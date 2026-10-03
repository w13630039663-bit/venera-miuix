package com.venera.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.venera.engineprobe.DesktopJsHost
import com.venera.engineprobe.EngineAssets
import com.venera.engineprobe.EngineSession
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.LocalFavoriteDatabase
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.desktop.platform.DesktopDatabasePorts
import com.venera.desktop.platform.DesktopPaths
import com.venera.desktop.gallery.data.DesktopGalleryPorts
import com.venera.desktop.gallery.ui.DesktopGalleryHome
import com.venera.desktop.gallery.ui.DesktopTheme
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * R1-F 的**端到端最小闭环**：真源脚本 → GraalJS 宿主 → 真网络取图 → skia 解码 → Fluent 窗口渲染。
 *
 * 跑法：
 * ```
 * ./gradlew :desktop:app -PargsKey=jm -Pproxy=127.0.0.1:7890
 * ```
 * 带 `--shot=路径` 时它自己截图后退出（无人值守取证用），否则就是个能点的窗口。
 *
 * 这一件的目的是**把"路线走不走得通"从一堆单点读数变成一个可运行的东西**：
 * 任何一环（引擎、桥协议、Cookie、gzip、WebP 解码、UI 组合）断了，窗口里就直接看得见。
 */
/**
 * 标题带 PID：上一轮被 `timeout` 杀掉的 gradle 客户端会**留下还开着的探针窗**，
 * 标题一样时 `FindWindowW` 匹配到的是哪一扇全凭运气（本轮的"截图截到壁纸"就是这么来的）。
 */
private val APP_TITLE = "Venera Desktop R1-F 最小闭环 #${ProcessHandle.current().pid()}"

/**
 * 桌面侧收藏管理器的唯一引用：第一次用到才取（取用即建两棵库，时机与 Android 侧
 * `getInstance(context)` 一致），留着它只为关窗时能按正确次序关（见 `closePersistenceInOrder`）。
 */
private var favoritesManagerRef: LocalFavoritesManager? = null

private fun favorites(): LocalFavoritesManager =
    favoritesManagerRef
        ?: LocalFavoritesManager.getInstance(DesktopDatabasePorts.PLATFORM).also { favoritesManagerRef = it }

/**
 * 加入收藏 —— 与 Android 侧 `ComicCardContextMenu`「加入收藏」同源的调用形状：
 * 默认收藏夹 + [FavoriteItem] 逐字段对应（title→name、cover→coverPath、递下来的源 key→sourceKey）。
 * 桌面 [EngineSession.ComicCard] 这颗模型没带 subTitle/tags，author/tags 就按 FavoriteItem
 * 自身的空默认值落库，不编字段。addComic **抛**=收藏夹不存在、回 **false**=这本已经在里面，
 * 三种结果分开说，否则"收藏失败"看起来像"点了没反应"。
 */
private suspend fun addFavorite(
    manager: LocalFavoritesManager,
    sourceKey: String,
    card: EngineSession.ComicCard,
): String {
    val folder = LocalFavoriteDatabase.DEFAULT_FOLDER
    val added = runCatching {
        manager.addComic(
            folder,
            FavoriteItem(
                id = card.id,
                name = card.title,
                sourceKey = sourceKey,
                coverPath = card.cover,
            ),
        )
    }
    val message = when {
        added.getOrDefault(false) -> "已收藏到「$folder」"
        added.isSuccess -> "这本已经在「$folder」里了"
        else -> "收藏失败：${added.exceptionOrNull()?.let {
            it.message?.takeIf(String::isNotBlank) ?: it.javaClass.simpleName
        } ?: "未知原因"}"
    }
    println("D_收藏 点击 源=$sourceKey id=${card.id} -> $message")
    return message
}

/**
 * 关持久层的次序（5a 复审点名）：**先 manager.close()（有界等待在飞刷新，15s 上界），
 * 再 DesktopDatabasePorts.close()**——反序会让在飞的收藏刷新在下一次取用时撞上"已退役"，
 * 抛出落进管理器自己的协程 scope，变成 R25 那类 `database connection closed` 噪音。
 * 两步各自的失败原样打痕，不咽。
 */
private fun closePersistenceInOrder() {
    favoritesManagerRef?.let { manager ->
        runCatching { manager.close() }.onFailure {
            println("D_关闭 收藏管理器未关净：${it::class.java.simpleName}: ${it.message?.take(200)}")
        }
    }
    runCatching { DesktopDatabasePorts.close() }.onFailure {
        println("D_关闭 桌面接线关闭失败：${it::class.java.simpleName}: ${it.message?.take(200)}")
    }
}

/** 取证参数的判读口径与读数都在 [ForensicFlags]（那颗是可测的；这里只是接线） */

fun main(args: Array<String>) {
    val root = File(System.getProperty("user.dir"))
    // 仓库目录从今天起只是**兜底**：包内 `/sources/*.js` 与 `/venera-init.js`、`/venera-shim.js`
    // 命中时这条路径压根不会被读（每次取用都会打一条"来源=包内/仓库"的读数，见 EngineAssets）
    val assetDir = File(root, "app/src/main/assets")
    // 阶段 1：数据目录收进 DesktopPaths —— 语义与旧的 localDataDir() 一致，
    // 取不到 %LOCALAPPDATA% 就启动即抛，绝不退回 java.io.tmpdir。
    val paths = DesktopPaths.create()
    val proxy = args.firstOrNull { it.startsWith("--proxy=") }?.removePrefix("--proxy=")
    val shot = args.firstOrNull { it.startsWith("--shot=") }?.removePrefix("--shot=")
    val startKey = args.firstOrNull { !it.startsWith("--") } ?: "jm"
    // --autofav / --favcheck **只为取证存在**（无人值守把"点心收藏 → 重开进程还在"这条链
    // 量成两条 D_收藏 读数）：一个写（收藏第一张卡后读回条数并退出），一个读（启动时不点
    // 任何按钮直接读回条数并退出）。都不是默认行为，不带参数时窗口交互如常。
    val autofav = ForensicFlags.state(args.asList(), "--autofav")
    val favcheck = ForensicFlags.state(args.asList(), "--favcheck")
    // 不认识的参数原样报出来，别让用户以为打进去了
    ForensicFlags.unknown(args.asList())
        .forEach { println("D_参数 未识别 $it（已忽略，不影响其余参数）") }
    // 两参同给原来会"静默抢跑"：favcheck 先 exitProcess，autofav 被吃掉且不吭声。现在如实说一句。
    ForensicFlags.bothGivenNote(autofav, favcheck)?.let(::println)

    val sources = EngineAssets.listSourceKeys(assetDir)
    println("D_启动 sources=${sources.size} 起始=$startKey proxy=${proxy ?: "直连"} 仓库兜底=${assetDir.path}")
    // jpackage.app-path 只有从打包 exe 进来才有值 —— 用它区分"gradle 起的"和"真产物"，
    // 两者的坑不通用（本轮 ImageIO 缓存那条就只在后者复现）。
    println(
        "D_环境 jdk=${System.getProperty("java.version")} " +
            "tmp=${System.getProperty("java.io.tmpdir")} " +
            "打包=${System.getProperty("jpackage.app-path") != null}"
    )
    println("D_数据目录 ${paths.dataRoot.path}（写入回读已验）")
    // Task 5a：把 data/db 接到桌面（只装 factory lambda，不碰 SQLite —— 建目录/建库推迟到第一次取用）。
    // 未接线就取用会由 DatabasePorts.of 直接抛，这里不包 try/catch：抛就是要崩在启动读数里。
    DesktopDatabasePorts.install(paths)
    // Gallery 接线：必须在 DatabasePorts 之后，因为 [DesktopGalleryContentGuard]
    // 的 [com.venera.compose.gallery.data.GalleryRules.askFirst] 首问规则时会经
    // [com.venera.compose.data.db.DatabasePorts.of] 取库，未接线就抛。
    DesktopGalleryPorts.install(com.venera.desktop.gallery.data.DesktopGalleryHandle.of(paths))
    // coreDbFile/favoritesDbFile 是纯计算读数：这行自己不建目录，"已建"照实反映磁盘现状。
    val coreDb = DesktopDatabasePorts.coreDbFile()
    val favDb = DesktopDatabasePorts.favoritesDbFile()
    println(
        "D_接线 平台=desktop core=${coreDb.path} 已建=${coreDb.exists()} " +
            "fav=${favDb.path} 已建=${favDb.exists()}",
    )

    if (favcheck == true) {
        // 取证第二跑：开窗前直接读回收藏条数（数据源仍是 LocalFavoritesManager，不自己开 SQL）
        val outcome = runCatching { runBlocking { favorites().getAllComics() } }
        outcome.fold(
            onSuccess = { items ->
                println("D_收藏 命中=${items.size} 明细=${items.take(10).map { it.id to it.name }}")
            },
            onFailure = {
                println("D_收藏 读回失败 ${it::class.java.simpleName}: ${it.message?.take(200)}")
            },
        )
        closePersistenceInOrder()
        kotlin.system.exitProcess(if (outcome.isSuccess) 0 else 1)
    }

    application {
        Window(
            onCloseRequest = {
                closePersistenceInOrder()
                exitApplication()
            },
            title = APP_TITLE,
            // 1440×900 = 设计稿 `.frame` 的画板尺寸（`docs/designs/windows-gallery-home-touhou-2026-10-03.html:39`）。
            // ⚠️ 这个尺寸不只是"好看"：它决定图片墙的列数。`1440 − rail 48 − pane 224 = 1168`，
            // 正是稿上标注的「内容净宽 1168」，再扣横向内边距 24 ⇒ `ceil(1144/200) = 6` 列。
            // 稿 `:338` 与 `:657` 两处都把这档写成 6 列，由 `DesktopWidthCaliberDriftTest` 钉住。
            //
            // 旧档 1080×760 仍然有效（窗口可缩放），它按同一条公式得 4 列。
            // ⚠️ 已知局限：`WindowPlacement.Floating` 不做尺寸自适应，1366 宽的屏上这扇窗会超出可视区
            //（尺寸钳制不在本批范围，见计划 R-g）。
            state = rememberWindowState(placement = WindowPlacement.Floating, width = 1440.dp, height = 900.dp),
        ) {
            // 深色主题**钉死**（2026-10-04 视觉重构）：`colors` 走 Fluent 的 darkColors（accent=藤紫），
            // 其余 surface 走 DesktopTheme 的五档阶梯。理由写在 `DesktopTheme` 的类注释里 ——
            // 之前让 Fluent 自己跟系统，于是浅底上那几枚深色档色值（#8B8B8B 灰、#CBB6FF 藤紫、
            // #3A2A55 渐变）全成了"看着发脏的错色"。
            // ⚠️ 这一行是**临时钉死**而不是"不支持浅色"：桌面端今天没有主题轴
            // （DesktopGalleryPreferences 那 12 枚读成员在桌面给的都是常量），
            // 将来接主题轴时这里要变成跟着偏好走，而不是删掉深色这套。
            //
            // ⚠️ 拆掉 `NavigationView` **不等于**拆掉 `FluentTheme`：后者给全窗提供深色档，
            // 且 `gallery/ui/` 多处在用 fluent 的 `Text`（需要它 provide 的 CompositionLocal scope）。
            // 由 `DesktopShellLayoutTest` ② 钉住这一条，防止连它一起被拆掉。
            FluentTheme(colors = DesktopTheme.colors()) {
                VeneraDesktop(
                    assetDir = assetDir,
                    dataDir = paths.dataRoot,
                    proxy = proxy,
                    sources = sources,
                    startKey = startKey,
                    shotPath = shot,
                    autofav = autofav == true,
                )
            }
        }
    }
}

@Composable
private fun VeneraDesktop(
    assetDir: File,
    dataDir: File,
    proxy: String?,
    sources: List<String>,
    startKey: String,
    shotPath: String?,
    autofav: Boolean,
) {
    var selected by remember { mutableStateOf(startKey) }
    var status by remember { mutableStateOf("待装载") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var cards by remember { mutableStateOf<List<EngineSession.Section>>(emptyList()) }
    var covers by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    var session by remember { mutableStateOf<EngineSession?>(null) }
    // 阅读链路：点卡片 → loadInfo → 取第一章 → loadEp → 前 8 页（含去混淆）
    var open by remember { mutableStateOf<EngineSession.ComicCard?>(null) }
    var pages by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var pageStatus by remember { mutableStateOf<String?>(null) }
    // 收藏这条链：顶栏「收藏」分段 + 卡片上的收藏入口，数据源一律 LocalFavoritesManager
    val scope = rememberCoroutineScope()
    var showFavorites by remember { mutableStateOf(false) }
    var favStatus by remember { mutableStateOf<String?>(null) }
    var favTree by remember { mutableStateOf<List<Pair<String, List<FavoriteItem>>>>(emptyList()) }

    LaunchedEffect(showFavorites) {
        if (!showFavorites) return@LaunchedEffect
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val manager = favorites()
                manager.currentFolders().map { it to manager.getFolderComics(it) }
            }
        }
        outcome.fold(
            onSuccess = { favTree = it; favStatus = null },
            onFailure = {
                favStatus = "收藏列表读取失败：${it::class.java.simpleName}: ${it.message?.take(200)}"
                println("D_收藏列表 读取失败 ${it::class.java.simpleName}: ${it.message?.take(200)}")
            },
        )
    }

    LaunchedEffect(open) {
        val s = session
        val card = open
        pages = emptyList()
        pageStatus = null
        if (s == null || card == null) return@LaunchedEffect
        pageStatus = "取详情…"
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val (title, eps) = s.details(card.id)
                val ep = eps.first()
                println("D_详情 $title 章数=${eps.size} 前3=${eps.take(3)}")
                val keys = s.pages(card.id, ep)
                if (keys.isEmpty()) error("loadEp 回了 0 页")
                pageStatus = "$title · ${eps.size} 章 · 共 ${keys.size} 页 · 取前 8 页"
                keys.take(8).mapIndexed { i, k ->
                    pageStatus = "$title · 解码第 ${i + 1}/8 页"
                    org.jetbrains.skia.Image.makeFromEncoded(s.pageImage(k, card.id, ep.id))
                        .toComposeImageBitmap()
                }
            }
        }
        outcome.fold(
            onSuccess = { pages = it },
            onFailure = {
                pageStatus = "阅读链路失败：${it::class.java.simpleName}: ${it.message?.take(240)}"
                println("D_阅读失败 ${card.id} -> ${it.message?.take(420)}")
            },
        )
        println("D_阅读 ${card.id} 页数=${pages.size}")
    }

    LaunchedEffect(selected) {
        session?.close()
        busy = true
        error = null
        cards = emptyList()
        covers = emptyMap()
        status = "装载源 $selected…"
        val s = EngineSession(DesktopJsHost(assetDir, dataDir, proxy), assetDir)
        session = s
        // 引擎与网络都是阻塞式，一笔都不许留在 UI 线程上
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val name = s.load(selected)
                status = "$name · 取探索页…"
                val page = s.explore(1)
                // 分区数组（jm 等）与单列表（search）两种形状在这里汇流，每分区先取 12 张
                val shown = page.sections.map { it.copy(comics = it.comics.take(12)) }
                val all = shown.flatMap { it.comics }
                if (all.isEmpty()) {
                    // 空手而归必须说清楚是哪种空：没有 explore / 信封失败 / 源回了 0 条
                    error = page.error ?: "源返回 0 条（分区 ${page.sections.size} 个）"
                }
                val map = LinkedHashMap<String, ImageBitmap>()
                // 取图先设总量闸：分区多的源一屏也放不下，逐张串行拉会先把探针拖成爬
                all.take(30).forEachIndexed { i, c ->
                    status = "$name · 取图 ${i + 1}/${all.take(30).size}"
                    if (c.cover.isNotBlank()) {
                        runCatching {
                            org.jetbrains.skia.Image.makeFromEncoded(s.coverBytes(c.cover))
                                .toComposeImageBitmap()
                        }.getOrNull()?.let { map[c.id] = it }
                    }
                }
                Triple(name, shown, map)
            }
        }
        outcome.fold(
            onSuccess = { (name, shown, map) ->
                cards = shown
                covers = map
                busy = false
                val total = shown.sumOf { it.comics.size }
                status = "$name · ${shown.size} 个分区 / $total 条 · 出图 ${map.size}/$total"
                println("D_结果 源=$name 分区=${shown.map { it.title to it.comics.size }} 出图=${map.size}/$total")
            },
            onFailure = {
                busy = false
                error = "${it::class.java.simpleName}: ${it.message?.take(300)}"
                status = "失败"
                println("D_失败 $selected -> $error")
            },
        )
        if (shotPath != null) {
            // 无人值守取证时顺手把阅读链路也走一遍（点第一条 → 取详情/页表/去混淆取图）
            val first = cards.firstOrNull()?.comics?.firstOrNull()
            if (open == null && first != null) {
                open = first
                kotlinx.coroutines.delay(20_000)
            } else {
                kotlinx.coroutines.delay(1500)
            }
            selfShot(shotPath)
            kotlin.system.exitProcess(0)
        }
        if (autofav) {
            // 取证第一跑：等效"点第一张卡的收藏"（同一颗 addFavorite），读回条数后按次序收尾退出
            val first = cards.firstOrNull()?.comics?.firstOrNull()
            if (first == null) {
                println("D_收藏 失败：源没回任何卡片，没东西可收（error=${error ?: "无网络层错误"}）")
                closePersistenceInOrder()
                kotlin.system.exitProcess(1)
            }
            val message = addFavorite(favorites(), selected, first)
            val total = runCatching { favorites().getAllComics().size }
            total.fold(
                onSuccess = { println("D_收藏 命中=$it") },
                onFailure = {
                    println("D_收藏 读回失败 ${it::class.java.simpleName}: ${it.message?.take(200)}")
                },
            )
            closePersistenceInOrder()
            // 退出码判据：读回成功且这条点击不是"收藏失败"（"已在里面"也算写链完好）
            kotlin.system.exitProcess(if (total.isSuccess && !message.startsWith("收藏失败")) 0 else 1)
        }
    }

    // ⚠️ 这里**直接**是 `DesktopGalleryHome`，外面不再套 compose-fluent 的 `NavigationView`。
    //
    // 判负记录：`docs/rounds/large-screen-adaptation-stage2-plan-2026-10-02.md:20` 已明确
    // 「`NavigationView` 有它自己的默认宽度，不是官方的 72/224。两边都建侧栏就是两套宽度 ——
    // 即『第二套布局系统』」，并据此把桌面化冻结在阶段 1。S2 接线时漏判了这条，
    // 于是窗口里挤了三层竖栏：NavigationView 自带的 pane（实测 `SideNavKt` 宽度常量 `180.0f`）
    // + 我们自绘的 rail 48 + 自绘的 pane 224 ⇒ 452dp 被吃掉，内容区被压掉一大截。
    //
    // 那层 pane 的底色还另有一次事故：它走 fluent 的 `MaterialContainer` + `acrylicDefault`，
    // 而 acrylic 在 skiko 桌面端解析不出系统底色 ⇒ 回退成**浅色**面板（深色主题下也一样）。
    // 自绘 rail/pane 不依赖 fluent Material 体系，这条路才彻底断掉。
    //
    // 判据：`DesktopShellLayoutTest` ① 断言根布局里不再出现 `NavigationView(` / `menuItems =`。
    DesktopGalleryHome(GalleryPorts.of(null))
}

/**
 * 自截图。**先验窗口真的在屏上再截**：上一版只按矩形抓，窗口被最小化时
 * 截到的是桌面壁纸（`_qa/desktop-{nhentai3,jm-reader4}.png` 就是这么废掉的），
 * 而日志照样打"截图成功"——那是伪证，不是证据。
 */
private fun selfShot(path: String) {
    val frame = java.awt.Frame.getFrames().firstOrNull { it.title == APP_TITLE }
    if (frame == null || !frame.isVisible || frame.state == java.awt.Frame.ICONIFIED) {
        println(
            "D_截图 无效：窗口不在屏上（frame=${if (frame == null) "null" else "visible=${frame.isVisible} iconified=${frame.state == java.awt.Frame.ICONIFIED}"}）" +
                "—— 本次没有可读的证据图，读数只认 stdout"
        )
        return
    }
    runCatching {
        // 只认 Win32 那条：AWT 的 `Frame` 对象在窗口被关掉后仍可能报 `visible=true` 与旧尺寸，
        // 拿它的 bounds 去截 = 截到壁纸（`desktop-{nhentai3,jm-reader4,reader6}.png` 三张废图都是这么来的）
        val hwnd = findHwnd(APP_TITLE)
        if (hwnd == null) {
            println("D_截图 无效：Win32 已查不到标题窗口（多半已被关掉），AWT 的 bounds 不可信")
            return
        }
        val rect = screenRectOf(hwnd) ?: run {
            println("D_截图 无效：GetWindowRect 失败")
            return
        }
        // 锁屏/切虚拟桌面时窗口矩形照样存在，截出来是纯壁纸 —— 先确认屏幕中心那点真的落在本窗上
        if (!isPointOnWindow(hwnd, rect.x + rect.width / 2, rect.y + rect.height / 2)) {
            println("D_截图 无效：WindowFromPoint 不认这扇窗（多半已锁屏/切到别的虚拟桌面），不产出假证据")
            return
        }
        val img = Robot().createScreenCapture(rect)
        ImageIO.write(img, "png", File(path))
        println("D_截图 $path rect=${rect.x},${rect.y} ${rect.width}x${rect.height} 字节=${File(path).length()}")
        // 同屏再抓一笔全屏当对照：窗口矩形取自 Win32，若截到的不是窗口，看全屏就知道差在哪
        val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
        val full = Robot().createScreenCapture(Rectangle(0, 0, screen.width, screen.height))
        ImageIO.write(full, "png", File("$path.full.png"))
        println("D_截图 对照全屏 ${screen.width}x${screen.height} 字节=${File("$path.full.png").length()}")
    }.onFailure { println("D_截图 FAIL ${it::class.java.simpleName}: ${it.message?.take(140)}") }
}
