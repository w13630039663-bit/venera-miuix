package com.venera.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.venera.desktop.gallery.ui.DesktopComicEngineConfig
import com.venera.desktop.gallery.ui.DesktopGalleryMetrics
import com.venera.desktop.gallery.ui.DesktopTheme
import com.venera.desktop.gallery.ui.VeneraDesktopApp
import io.github.composefluent.FluentTheme
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * `--autofav` 的取证链路：**不碰 UI**，自己在 IO 上走完"装载源 → 探索页 → 第一张卡"。
 *
 * 上一版这一跑依赖根组合里那颗 `cards` 状态 —— 而那颗状态的唯一用途是喂给从未存在过的布局。
 * 于是这个开关在做的其实是"为了让某个变量被写进去，先把 30 张封面拉下来"。
 * 取证自己会取它需要的那一张，`engine.close()` 也在同一份 `finally` 里，不会漏。
 *
 * @return 退出码：0 = 写链完好（含"这本已经在里面了"）；1 = 取卡失败或写失败。
 */
private fun runAutofavEvidence(
    assetDir: File,
    dataDir: File,
    proxy: String?,
    sourceKey: String,
): Int {
    val outcome = runBlocking {
        withContext(Dispatchers.IO) {
            runCatching {
                val host = DesktopJsHost(assetDir, dataDir, proxy)
                val engine = EngineSession(host, assetDir)
                try {
                    engine.load(sourceKey)
                    engine.explore(1).sections.firstOrNull()?.comics?.firstOrNull()
                } finally {
                    engine.close()
                }
            }
        }
    }
    val first = outcome.getOrNull()
    if (first == null) {
        println(
            "D_收藏 失败：源没回任何卡片，没东西可收" +
                "（${outcome.exceptionOrNull()?.message?.take(200) ?: "无网络层错误"}）",
        )
        return 1
    }

    val message = runBlocking { addFavorite(favorites(), sourceKey, first) }
    val total = runCatching { runBlocking { favorites().getAllComics().size } }
    total.fold(
        onSuccess = { println("D_收藏 命中=$it") },
        onFailure = {
            println("D_收藏 读回失败 ${it::class.java.simpleName}: ${it.message?.take(200)}")
        },
    )
    // 退出码判据：读回成功，且这次点击不是"收藏失败"（"已在里面"也算写链完好）。
    return if (total.isSuccess && !message.startsWith("收藏失败")) 0 else 1
}

/** 无人值守截图前给 UI 的落地时间。取封面是异步的，太早截会拿到半屏的占位块。 */
private const val SELF_SHOT_SETTLE_MS = 2500L

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

    if (autofav == true) {
        // 取证第一跑。**不再经过 UI**：上一版它挂在根组合的 LaunchedEffect 上，要靠
        // `cards` 里第一张卡 —— 而那张卡是给 UI 用的中间产物，UI 不画它它就白跑。
        // 现在自己在 IO 上走一遍"装载 → 探索 → 首卡"，这里既不需要窗口也不需要布局：
        // 于是 `-Pautofav=1` 在同一次启动里不再为了喂一张卡去串行拉 30 张封面。
        val code = runAutofavEvidence(assetDir, paths.dataRoot, proxy, startKey)
        closePersistenceInOrder()
        kotlin.system.exitProcess(code)
        return
    }

    application {
        // 默认窗尺寸**按屏钳制**（收掉计划里的 R-g）：`WindowPlacement.Floating` 不做尺寸自适应，
        // 1366 宽的屏上写死 1440 会让最右一列永远跑到屏幕外。钳制逻辑在
        // `DesktopGalleryMetrics.windowSizeFor` —— 那里是纯函数，所以"1366 屏得多少"有单测兜着；
        // 这里只负责把 AWT 的屏幕读数喂给它。
        val windowSize = remember {
            val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
            DesktopGalleryMetrics.windowSizeFor(screen.width, screen.height)
        }
        Window(
            onCloseRequest = {
                closePersistenceInOrder()
                exitApplication()
            },
            title = APP_TITLE,
            // 目标尺寸 1440×900 = 设计稿 `.frame` 的画板尺寸（`docs/designs/windows-gallery-home-touhou-2026-10-03.html:39`）。
            // ⚠️ 这个尺寸不只是"好看"：它决定图片墙的列数。`1440 − rail 48 − pane 224 = 1168`，
            // 正是稿上标注的「内容净宽 1168」，再扣横向内边距 24 ⇒ `ceil(1144/200) = 6` 列。
            // 稿 `:338` 与 `:657` 两处都把这档写成 6 列，由 `DesktopWidthCaliberDriftTest` 钉住。
            //
            // ⚠️ 实际开出来可能是**更窄**的一档（屏放不下 1440 时）：那不是"跑偏"，
            // 列数会按同一条公式跟着变（1280 档 5 列、1080 档 4 列），墙仍然是墙。
            state = rememberWindowState(
                placement = WindowPlacement.Floating,
                width = windowSize.width,
                height = windowSize.height,
            ),
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
                VeneraDesktopApp(
                    ports = GalleryPorts.of(null),
                    comic = DesktopComicEngineConfig(
                        assetDir = assetDir,
                        dataDir = paths.dataRoot,
                        proxy = proxy,
                        sourceKey = startKey,
                        // 懒装载：见 DesktopComicEngineConfig.favorites 的注释。
                        favorites = ::favorites,
                    ),
                )

                if (shot != null) {
                    // 无人值守取证等 UI 落地后再截。这段**留在集成方**（本文件）而不是搬进
                    // `VeneraDesktopApp`：截图与"起来就退出"都是取证的形状，不是应用的形状 ——
                    // 塞进应用根的话，每个正式用户每一次启动都要多走一个 `shot != null` 的判断。
                    LaunchedEffect(Unit) {
                        delay(SELF_SHOT_SETTLE_MS)
                        selfShot(shot)
                        closePersistenceInOrder()
                        kotlin.system.exitProcess(0)
                    }
                }
            }
        }
    }
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
