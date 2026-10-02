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
import com.venera.engineprobe.EngineSession
import com.venera.desktop.platform.DesktopDatabasePorts
import com.venera.desktop.platform.DesktopPaths
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.NavigationView
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import io.github.composefluent.component.menuItem
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
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

fun main(args: Array<String>) {
    val root = File(System.getProperty("user.dir"))
    val assetDir = File(root, "app/src/main/assets")
    // 阶段 1：数据目录收进 DesktopPaths —— 语义与旧的 localDataDir() 一致，
    // 取不到 %LOCALAPPDATA% 就启动即抛，绝不退回 java.io.tmpdir。
    val paths = DesktopPaths.create()
    val proxy = args.firstOrNull { it.startsWith("--proxy=") }?.removePrefix("--proxy=")
    val shot = args.firstOrNull { it.startsWith("--shot=") }?.removePrefix("--shot=")
    val startKey = args.firstOrNull { !it.startsWith("--") } ?: "jm"

    val sources = File(assetDir, "sources").listFiles { f -> f.extension == "js" }
        ?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()
    println("D_启动 sources=${sources.size} 起始=$startKey proxy=${proxy ?: "直连"} assets=${assetDir.path}")
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
    // coreDbFile/favoritesDbFile 是纯计算读数：这行自己不建目录，"已建"照实反映磁盘现状。
    val coreDb = DesktopDatabasePorts.coreDbFile()
    val favDb = DesktopDatabasePorts.favoritesDbFile()
    println(
        "D_接线 平台=desktop core=${coreDb.path} 已建=${coreDb.exists()} " +
            "fav=${favDb.path} 已建=${favDb.exists()}",
    )

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = APP_TITLE,
            state = rememberWindowState(placement = WindowPlacement.Floating, width = 1080.dp, height = 760.dp),
        ) {
            FluentTheme {
                VeneraDesktop(
                    assetDir = assetDir,
                    dataDir = paths.dataRoot,
                    proxy = proxy,
                    sources = sources,
                    startKey = startKey,
                    shotPath = shot,
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
    }

    NavigationView(
        menuItems = {
            sources.forEach { key ->
                menuItem(
                    selected = key == selected,
                    onClick = { selected = key },
                    text = { Text(key) },
                    icon = { Text(key.take(1).uppercase()) },
                )
            }
        },
    ) {
        val card = open
        if (card != null) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("← 返回列表", modifier = Modifier.clickable { open = null })
                    Text("  ｜ ${card.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(pageStatus ?: "取页中…", modifier = Modifier.padding(horizontal = 12.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    // 用 items(count) 而不是 items(list)：`items` 在本文件里已被网格占用，
                    // 两个同名扩展同时 import 会撞
                    items(pages.size) { i ->
                        Image(pages[i], contentDescription = null, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        } else {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            // 状态行走 fluent 的 Text（跟着主题取色）：上一版硬用白字，浅色主题下等于隐形
            Text("源：$selected ｜ $status" + (error?.let { " ｜ $it" } ?: ""))
            if (busy) {
                ProgressBar()
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(top = 12.dp),
            ) {
                cards.forEach { section ->
                    if (section.title.isNotBlank()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(section.title, modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                    items(section.comics, key = { "${section.title}-${it.id}" }) { comic ->
                        Column(
                            Modifier
                                .width(180.dp)
                                .clickable { open = comic },
                        ) {
                            Box(
                                Modifier.width(180.dp).height(230.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                val bmp = covers[comic.id]
                                if (bmp != null) {
                                    Image(bmp, contentDescription = comic.title, modifier = Modifier.fillMaxSize())
                                } else {
                                    Text("无图")
                                }
                            }
                            Text(
                                comic.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
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
