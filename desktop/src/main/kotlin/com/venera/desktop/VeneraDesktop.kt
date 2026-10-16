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
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.data.platform.CacheDirs
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.domain.GalleryRankings
import com.venera.desktop.platform.DesktopDatabasePorts
import com.venera.desktop.platform.DesktopPaths
import com.venera.desktop.gallery.data.DesktopGalleryPorts
import com.venera.desktop.gallery.data.DesktopImagePipeline
import com.venera.desktop.gallery.data.DesktopSourceState
import com.venera.desktop.gallery.data.DesktopTagDictionaryAsset
import com.venera.desktop.gallery.data.DesktopComicReaderPreferences
import com.venera.desktop.gallery.data.DesktopGalleryBrowseHistory
import com.venera.desktop.gallery.data.desktopGallerySaveDir
import com.venera.desktop.gallery.ui.DesktopComicEngineConfig
import com.venera.desktop.gallery.ui.DesktopComicFavoriteOutcome
import com.venera.desktop.gallery.ui.DesktopDomain
import com.venera.desktop.gallery.ui.DesktopGalleryHomeSectionKey
import com.venera.desktop.gallery.ui.DesktopGalleryMetrics
import com.venera.desktop.gallery.ui.DesktopTheme
import com.venera.desktop.gallery.ui.VeneraDesktopApp
import com.venera.desktop.gallery.ui.desktopAddComicFavorite
import com.venera.desktop.gallery.ui.desktopCanCopyImage
import com.venera.desktop.gallery.ui.desktopDomainRows
import io.github.composefluent.FluentTheme
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
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
 * ./gradlew :desktop:app -Pkey=jm -Pproxy=127.0.0.1:7890
 * ./gradlew :desktop:app -Pshot=_qa/desktop-search.png -PstartDomain=SEARCH
 * ./gradlew :desktop:app -Pshot=_qa/desktop-artist.png -Partist=yandere/某标签
 * ./gradlew :desktop:app -Pshot=_qa/desktop-ranking-month.png -Pranking=MONTH/2024-03
 * ```
 * 带 `--shot=路径` 时它自己截图后退出（无人值守取证用），否则就是个能点的窗口。
 * 带 `--start-domain=` 时取证落在指定那一域（七枚都认，见 ForensicFlags.startDomain）；
 * 认不出就直接退出码 1，**不退到默认域** —— 默认域那张图会顶着目标域的文件名成为证据。
 * 带 `--artist=` 时直接进那一位的单人页（见 ForensicFlags.artist）：那一屏本来要**点一下**才出来。
 * 带 `--ranking=` 时把排行榜拨到那一档、那一期（见 ForensicFlags.ranking）：
 * 「上一期 / 下一期 / 回到本期」那一排本来要把档拨到月/年之后才出现，而鼠标自验是禁的。
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
 * 阅读历史那颗件（`comic_history`）。与 [favorites] 同一口径**第一次用到才取**（两者都只经
 * `DesktopDatabasePorts.PLATFORM` 这一颗端口要库，别的来源一概不认）。
 *
 * 这里**不留引用**：`HistoryDao.getInstance` 自己缓存（companion 里那枚 `INSTANCE`），
 * 而关库那一串是按端口走的（`closePersistenceInOrder`），不是按这颗件走的。
 */
private fun history(): HistoryDao = HistoryDao.getInstance(DesktopDatabasePorts.PLATFORM)

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
    seedCookies: List<String>,
): Int {
    val outcome = runBlocking {
        withContext(Dispatchers.IO) {
            runCatching {
                val host = DesktopJsHost(assetDir, dataDir, proxy, seedCookies)
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

    // 按的是屏上那颗「收藏」同一枚动作（`gallery/ui/DesktopComicFavorite.kt`）：
    // 两处各写一遍的话，种子跑通的那条路就不是用户按的那条路（判据 ④ 数出现次数）。
    val favoriteOutcome = runBlocking { desktopAddComicFavorite(::favorites, sourceKey, first) }
    val total = runCatching { runBlocking { favorites().getAllComics().size } }
    total.fold(
        onSuccess = { println("D_收藏 命中=$it") },
        onFailure = {
            println("D_收藏 读回失败 ${it::class.java.simpleName}: ${it.message?.take(200)}")
        },
    )
    // 退出码判据：读回成功，且这一笔不是"没成"（"这本已经在里面了"也算写链完好）。
    return if (total.isSuccess && favoriteOutcome !is DesktopComicFavoriteOutcome.Failed) 0 else 1
}

/** 无人值守截图前给 UI 的落地时间。取封面是异步的，太早截会拿到半屏的占位块。 */
private const val SELF_SHOT_SETTLE_MS = 2500L

/** 抬窗之后**再等一拍**才截：topmost 落到系统那边、合成器把这扇窗真压上来，都需要一点时间 */
private const val RAISE_SETTLE_MS = 250L

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
    // 崩溃归属那一行（批 53）：**第一件事**，早于任何取数与开窗 ——
    // 批 47 那次崩在启动阶段（Coil 的 ImageLoader 缺 swing dispatcher），装晚了就正好管不到那一段。
    installDesktopCrashHandler()
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
    // 起始域：**只服务无人值守取证**。`--shot=` 原来只能落在写死的那一域（图库），
    // 于是"搜索域的开窗形态""收藏域那两档观感"这类要看屏的账就全都做不了 ——
    // 而替代路是让用户点鼠标，那条已被明令禁掉。这一枚把"要看哪一域"变成命令行上的读数。
    // 认不出的一律中止启动（不退到默认域），判读与理由都在 ForensicFlags.startDomain。
    val startDomainReadout = ForensicFlags.startDomain(args.asList())
    val flagDomain = when (startDomainReadout) {
        is StartDomainReadout.NotGiven -> DesktopDomain.GALLERY

        is StartDomainReadout.Resolved -> {
            println("D_取证参数 --start-domain 认成「${startDomainReadout.domain.title}」（值=${startDomainReadout.raw} 枚举=${startDomainReadout.domain.name}）")
            startDomainReadout.domain
        }

        is StartDomainReadout.Unresolved -> {
            println(
                "D_取证参数 --start-domain 认不出（值=${startDomainReadout.raw.ifBlank { "<没带值>" }}）" +
                    "—— 不退到默认域：默认域那张图会顶着目标域的文件名成为证据"
            )
            println("D_取证参数 可选值：" + DesktopDomain.entries.joinToString(" ") { "${it.name}／${it.title}" })
            kotlin.system.exitProcess(1)
        }
    }
    // 画师单人页那颗种子（第二十批）：那一屏是"点一下才出来的那一层"（要点列表上的「看他的图」），
    // 而鼠标自验是禁的（手册 §8）—— 批 19 判据全绿而图证那格只能写"没拍成"，缺的就是这一枚。
    val artistSeed = ForensicFlags.artist(args.asList())
    // 排行榜那颗种子（第三十批）：期次那一排是"把档拨到月/年才出现的那一层"，而鼠标自验是禁的
    // （手册 §8）—— 批 29 六条判据全绿而那三枚拍不到，缺的就是这一枚。判读吃 UTC 的今天，因为
    // "早于地板"与"那一还没到"两档都是相对本期判的。
    val rankingSeed = ForensicFlags.ranking(args.asList(), LocalDate.now(ZoneOffset.UTC))
    // 两枚种子同给时赢的是 `--artist`（它先把起始域定成「画师」，而那一屏与排行榜那一行不在同一域里）。
    // 这不是故障而是一次落空，所以另一枚必须自己说清"我这枚没种进去" —— 静默抢跑就是
    // `--autofav` 与 `--favcheck` 原来那个形状（见 ForensicFlags.bothGivenNote）。
    val artistWins = artistSeed is ArtistReadout.Resolved
    // 起始域**在这一层**跟着种子走，不交给应用根去自己改：那一层的注释写明"起始域是参数，
    // 不是那一层的决定"。只把目的地种进槽而域留在别处，那一屏根本不在组合里 ——
    // 屏上什么也不会出现，而那正是"参数给了不报错、屏上是另一屏"最省事的一种做法。
    // 种子读不出时域一字未动（它只是没进去，不是把用户的域改掉了）。
    // 排行榜那一枚的接管走 ForensicFlags.rankingLanding（同一件事抽成跑得的那一颗，理由在那里的注释）。
    val domainAfterArtist = if (artistWins) DesktopDomain.ARTISTS else flagDomain
    val artistTarget = (artistSeed as? ArtistReadout.Resolved)?.target
    if (artistSeed is ArtistReadout.Resolved) {
        val hit = artistSeed.target
        println(
            "D_取证参数 --artist=${artistSeed.raw} 认成 站点=${hit.site.displayName} " +
                "标签=${hit.name}（起始域跟着落到「${domainAfterArtist.title}」）"
        )
    }
    // 没落到那一屏的两档实话（读不出 / 顶掉了用户点名的那一域）：不中止，但绝不静默。
    ForensicFlags.artistNote(artistSeed, flagDomain = (startDomainReadout as? StartDomainReadout.Resolved)?.domain)
        ?.let(::println)
    val rankingTarget = (rankingSeed as? RankingReadout.Resolved)?.seed
    // 起窗后直接进哪一本漫画的详情屏（第八十批）：那一屏入口是封面，而鼠标自验是禁的（手册 §8）⇒
    // 不带这枚种子时详情那一屏的图证只能记成"没拍成"。`null` = 不进，停在漫画列表。
    val comicSeed = ForensicFlags.comic(args.asList())
    // 把某一颗随包源当成本台**停用着**起来（批 83.1）：那一格今天只有离屏语义树量过，"列表标灰而装载照旧"
    // 这种缺陷在帧上看不见。种子只活在这一趟（不写盘上那份），认不认得那串由消费点那句读数念。
    val seedDisabled = listOfNotNull(ForensicFlags.disable(args.asList())).toSet()
    // 批 88·下半：`--cookie=<host>:<名>=<值>` 那几枚凭据种子。只并本次装载的罐，不写盘上那份
    //（盘上那份是用户的登录态）。解析在宿主那一层做，理由见 ForensicFlags.cookies 的注释。
    val seedCookies = ForensicFlags.cookies(args.asList())
    // 起始查询串（第五批）：搜索那一屏是七枚域里唯一"不给输入就拍不到东西"的，而键盘鼠标自验是禁的
    // （手册 §8）。这一枚把查询串变成命令行上的读数，AI 角标那类只在真数据上才出现的形状才拍得到。
    val startQuery = ForensicFlags.query(args.asList())
    // 起始**行**（第十批 ⑤）：`--start-domain=` 只到域，到不了域内 pane 的那一行 ——
    // 于是图库域里新点亮的「最新」这类页今天拍不到。键与中文题名两种都认（与那颗同一口径）；
    // 对不上任何行不中止（这一枚没有"开错窗"的代价），但绝不静默。
    val rawSection = ForensicFlags.section(args.asList())
    val flagSection = rawSection?.let { wanted ->
        desktopDomainRows(domainAfterArtist).firstOrNull { it.key == wanted || it.title == wanted }?.key
    }
    // 排行榜那颗种子把**起始域与起始行**一起接管（那一屏在域内的第二层：不在「排行榜」那一行上，
    // 档与期都没处种）。`--artist` 赢时一字不改 —— 两枚同给由 rankingNote 念"这一枚没种进去"，
    // 不能再偷偷改用户的落点。接管这件事走纯函数，理由写在 ForensicFlags.rankingLanding 那里。
    val landing = ForensicFlags.rankingLanding(
        rankingSeed,
        flagDomain = domainAfterArtist,
        flagSection = flagSection,
        artistWins = artistWins,
    )
    // 漫画详情那颗种子接管**起始域**（第八十批）。次序在 ranking 之后：那两枚会把域与行一起定死在
    // 同一屏里，这一枚只改域。为什么必须有这一步 —— 第一次起窗拍到的是这样一帧：
    // `D_落点 域=图库(启动) 行=每日热门 … 详情=开(启动)`，而屏上根本没有详情（那一屏只在漫画域里
    // 才组合），`D_详情` 一个字都没念。读数与屏上不一致，就是谎。
    val takenOverByOther = artistWins || rankingSeed is RankingReadout.Resolved
    val comicLanding = ForensicFlags.comicLanding(comicSeed, landing, takenOverByOther)
    ForensicFlags.comicNote(comicSeed, comicLanding, takenOverByOther)?.let(::println)
    // 起窗后直接进哪一话的阅读那一屏（第八十一批）。它排在详情**之后**：`--comic` 与 `--read` 同给时
    // 详情在底下留着（「← 返回详情」回得到），而屏上那一层是阅读。两枚都没给时不改用户的落点。
    // 起点页先判（第八十二批半）：那一枚是这一枚的一半，次序反了就得让 `read` 自己去摸 args。
    val readPageSeed = ForensicFlags.readPage(args.asList())
    val readSeed = ForensicFlags.read(args.asList(), readPageSeed)
    ForensicFlags.readPageNote(readPageSeed, readSeed)?.let(::println)
    val readLanding = ForensicFlags.readLanding(readSeed, comicLanding, takenOverByOther)
    ForensicFlags.readNote(readSeed, readLanding, takenOverByOther)?.let(::println)
    // 第八十七批：把阅读那一屏顶到某一档模式（`--read-mode=`）。它只作本次初值、不写那枚键 ——
    // 顶过的那一句实话由那一屏自己念（`desktopReaderModeSeedNote`）。
    val readerModeSeed = ForensicFlags.readMode(args.asList())
    // 第九十六批：起窗后把哪一话整话写进本机（`--download=<漫画号>/<话号>`）。
    // 它走的是详情那一屏**长按那一条路**（同一颗 runner、同一套目录名、同一行 `D_下载`），不是另开一条取证专用路。
    val downloadSeed = ForensicFlags.download(args.asList())
    ForensicFlags.downloadNote(downloadSeed, detailOpens = comicSeed != null && comicLanding.domain == DesktopDomain.COMIC)?.let(::println)
    // 第一百批：那一趟写下几页之后停手（`--download-cancel-after=<N>`，扳的是屏上那枚「取消」同一颗闸）。
    val downloadCancelAfter = ForensicFlags.downloadCancelAfter(args.asList())
    ForensicFlags.downloadCancelAfterNote(
        downloadCancelAfter, downloadSeed, detailOpens = comicSeed != null && comicLanding.domain == DesktopDomain.COMIC,
    )?.let(::println)
    // 第九十八批：起窗后把哪一话从本机删掉（`--delete=<漫画号>/<话号>`，与上面那枚同一把写法）。
    // 它走的是屏上那颗把手**第二次按下**那一条路（同一颗 runner、同一行 `D_删除`），不是另开一条取证专用路。
    val deleteSeed = ForensicFlags.delete(args.asList())
    ForensicFlags.deleteNote(deleteSeed, comicDomainOpens = comicLanding.domain == DesktopDomain.COMIC)?.let(::println)
    val repairSeed = ForensicFlags.repair(args.asList())
    ForensicFlags.repairNote(repairSeed, comicDomainOpens = comicLanding.domain == DesktopDomain.COMIC)?.let(::println)
    // 档位那颗种子（批 92，`--detail-tier=预览|大图|原图`，屏名与枚举名都认）。
    // ⚠️ 它的落点**不在** `tier` 的初值那一格，而在应用根的点卡那一跳 —— 写成初值会被同一次点卡里
    // 的"升档"盖掉（取证那颗 `--detail` 走的正是同一枚 lambda），那是"stdout 念了而屏上没动"那一族。
    val detailTierSeed = ForensicFlags.detailTier(args.asList())
    val startDomain = readLanding.domain
    val startSection = readLanding.section
    // 正面读数只在**真落到那一格**时念：两枚种子同给而赢的是 `--artist` 时，这一枚没接管落点，
    // 这里若照样念一句"起始域跟着落到「图库」"就与下一句（"这一枚没种进去"）互相打脸。
    if (rankingSeed is RankingReadout.Resolved &&
        landing.domain == DesktopDomain.GALLERY &&
        landing.section == DesktopGalleryHomeSectionKey.RANKING
    ) {
        val hit = rankingSeed.seed
        // 行名不写死：那一项的题名从行表里现取，行被改名或被撤下时这句读数会跟着变，
        // 而不会替一屏已经不存在的行作伪证（屏上每句断言都得答得出"哪次量的"）。
        val row = desktopDomainRows(DesktopDomain.GALLERY).firstOrNull { it.key == DesktopGalleryHomeSectionKey.RANKING }
        println(
            "D_取证参数 --ranking=${rankingSeed.raw} 认成 档=${hit.ranking.label} " +
                "期=${GalleryRankings.periodShortLabel(hit.ranking, hit.anchor) ?: "本期"}" +
                "（起始域跟着落到「${DesktopDomain.GALLERY.title}」" +
                (row?.let { "、起始行跟着落到「${it.title}」" } ?: "，而那一域的行表里没有「排行榜」那一行") + "）"
        )
    }
    // --autofav / --favcheck **只为取证存在**（无人值守把"点心收藏 → 重开进程还在"这条链
    // 量成两条 D_收藏 读数）：一个写（收藏第一张卡后读回条数并退出），一个读（启动时不点
    // 任何按钮直接读回条数并退出）。都不是默认行为，不带参数时窗口交互如常。
    val autofav = ForensicFlags.state(args.asList(), "--autofav")
    val favcheck = ForensicFlags.state(args.asList(), "--favcheck")
    // 起窗后自己把详情浮层摆出来（第十一批 P0/P1）：那一层要**点一张卡**才出来，
    // 而 agent 侧不许动鼠标 ⇒ 不带这枚参数时它永远拍不到图。默认行为一字未动。
    val detail = ForensicFlags.state(args.asList(), "--detail")
    // 起窗后自动按一次「找同一张图」（第三十六批）：结果那一格在浮层里，要按一下才出来，
    // 而鼠标自验是禁的 ⇒ 不带这枚参数时批 35 只能拍到那枚按钮。默认行为一字未动。
    val reverse = ForensicFlags.state(args.asList(), "--reverse")
    // 起窗后自动按一次「复制图片」（第五十四批）：那一格只有按下去才量得出剪贴板里进了什么。
    val copy = ForensicFlags.state(args.asList(), "--copy")
    // 起窗后自动按一次「存到本机」（第八十五批）：这一格唯一的证据是**盘上多出来那颗文件**，
    // 不按一次就量不到。默认行为一字未动。
    val save = ForensicFlags.state(args.asList(), "--save")
    // 起窗后替用户挑好那一张本机的图（第七十七批 批 77 下半）：那一格的入口是**系统文件框**，
    // 而鼠标自验是禁的（手册 §8）⇒ 不带这枚参数时路径之后的三跳结构性地拍不到。默认行为一字未动。
    // ⚠️ 它跳过的正是文件框那一步 ⇒ 那一跳今天没有读数，别把这枚种子的通过当成"挑图验过了"。
    val reverseFile = ForensicFlags.reverseFile(args.asList())
    // 点卡之后进不进大图那一层（第五十批）：`null` = 没给，而"没给"在应用根里是**进**（默认行为），
    // `=0` 才是不进。所以三态原样往下递，不在这里塌成二态 —— `D_落点` 的 `种子=` 那一格要念得出差别。
    val viewer = ForensicFlags.state(args.asList(), "--viewer")
    // 起窗后放大到几倍（第五十批）：`null` = 本轮不放大。给的不是个数时那一颗自己念一句并给 null。
    val viewerZoom = ForensicFlags.viewerZoom(args.asList())
    // 不认识的参数原样报出来，别让用户以为打进去了
    ForensicFlags.unknown(args.asList())
        .forEach { println("D_参数 未识别 $it（已忽略，不影响其余参数）") }
    // 两参同给原来会"静默抢跑"：favcheck 先 exitProcess，autofav 被吃掉且不吭声。现在如实说一句。
    ForensicFlags.bothGivenNote(autofav, favcheck)?.let(::println)
    // 查询串落在没有搜索框的那一域：不中止（域名由 `--start-domain=` 自己念），但必须出声。
    ForensicFlags.queryNote(startQuery, startDomain)?.let(::println)
    // 起始行对不上这一域的任何一行：不中止，但那一句必须念出来（静默 = 假开关的口径）。
    ForensicFlags.sectionNote(rawSection, matched = startSection != null, domain = startDomain)?.let(::println)
    // 排行榜那颗种子没落到那一格的四档实话（读不出 / 被 --artist 抢走 / 域被接管 / 行被接管）。
    // `flagSection ?: rawSection`：认得出就交键，认不出就把用户原样打的那一串递过去 ——
    // 那一枚被种子接管时读数得点名**他打的是什么**，而不是只说"行变了"。
    ForensicFlags.rankingNote(
        rankingSeed,
        flagDomain = (startDomainReadout as? StartDomainReadout.Resolved)?.domain,
        flagSection = flagSection ?: rawSection,
        artistWins = artistWins,
    )?.let(::println)
    // 反搜那颗种子替用户多开了一扇浮层时的那句实话（浮层本来就开着时不吭声）。
    ForensicFlags.reverseNote(detail, reverse)?.let(::println)
    // 「复制图片」与「存到本机」这两枚同给时要念清"为谁多开了这一扇"（第八十五批把两句并成一句：
    // 它们按的是同一颗浮层里相邻的两枚按钮，屏上却只多开了一扇窗 —— 两句各提一枚就是两份口径判同一件事）。
    ForensicFlags.overlaySeedNote(detail, copy, save)?.let(::println)
    // 那一枚种子落空时的两句实话（各念各的）：给了参数而没带路径 = 打错了；
    // 带了路径而起始域不是「搜索」= 那一格根本不在组合里。静默是假开关的口径。
    ForensicFlags.reverseFileNote(
        raw = args.asList().firstOrNull { it == "--reverse-file" || it.startsWith("--reverse-file=") },
        resolved = reverseFile,
        domain = startDomain,
    )?.let(::println)
    // 浮层开不开由那颗纯函数判（`--detail` 开，或被 `--reverse` / `--copy` / `--save` 接管）—— 这一格不许写在 main 里：
    // main 要建窗口，测试碰不到，留在里面就只剩文本锚点而锚点看不见死分支（批 30 那条规矩的第三次应用）。
    val panelOpen = ForensicFlags.detailPanel(detail, reverse, copy, save)
    // 给了档位而浮层根本不开 ⇒ 那一档哪儿也没去，必须出声（`readPageNote` 同一族纪律）。
    // 吃的是上面那一枚 `panelOpen` 而不是 `--detail` 自己：`--reverse` / `--copy` / `--save` 那三枚
    // 顺带接管浮层时，档位种子是真落得下去的，那时念这一句就是假读数。
    ForensicFlags.detailTierNote(detailTierSeed, panelOpen)?.let(::println)

    val sources = EngineAssets.listSourceKeys(assetDir)
    println("D_启动 sources=${sources.size} 起始=$startKey proxy=${proxy ?: "直连"} 仓库兜底=${assetDir.path}")
    // jpackage.app-path 只有从打包 exe 进来才有值 —— 用它区分"gradle 起的"和"真产物"，
    // 两者的坑不通用（本轮 ImageIO 缓存那条就只在后者复现）。
    // 批 90 把那句从"推测"变成"读数"：交付包里 `ImageIO.createImageInputStream` 实测抛
    // `IIOException: Can't create cache file!`（`_qa/b90_pkgC.log`），也就是**包内进程不能在
    // `java.io.tmpdir` 下建那颗 scratch**（成因仍未定论，与"包内取不到 jar 里的原生库"同一族）。
    // 正解不是去修那条成因，是**别依赖它**：我们喂给 ImageIO 的全是内存里的 `ByteArray`，
    // 落盘 scratch 本来就是纯开销 ⇒ `setUseCache(false)` 一次关掉，两条路（开发跑与包内跑）同轨。
    javax.imageio.ImageIO.setUseCache(false)
    println(
        "D_环境 jdk=${System.getProperty("java.version")} " +
            "tmp=${System.getProperty("java.io.tmpdir")} " +
            "打包=${System.getProperty("jpackage.app-path") != null} imageio=内存"
    )
    println("D_数据目录 ${paths.dataRoot.path}（写入回读已验）")
    // Task 5a：把 data/db 接到桌面（只装 factory lambda，不碰 SQLite —— 建目录/建库推迟到第一次取用）。
    // 未接线就取用会由 DatabasePorts.of 直接抛，这里不包 try/catch：抛就是要崩在启动读数里。
    DesktopDatabasePorts.install(paths)
    // Gallery 接线：必须在 DatabasePorts 之后 —— 守卫那一层（对岸的 `ContentGuardManager`，桌面包在
    // [com.venera.desktop.gallery.data.DesktopGalleryContentGuard] 里）在**构造那一刻**就建 `GuardRuleStore`，
    // 而那颗建起来要经 [com.venera.compose.data.db.DatabasePorts.of] 取库，未接线就抛。
    // （从前这里写的是"首问规则时"才取，那句读起来像可以推迟 —— 实际是构造期，早得多。）
    val galleryHandle = com.venera.desktop.gallery.data.DesktopGalleryHandle.of(paths)
    DesktopGalleryPorts.install(galleryHandle)
    // 取图那一跳的防盗链头（第七十九批）：装在**任何开窗与取图之前**，而 `install` 全应用只这一处 ——
    // 装两处是后一处静默盖掉前一处（判据 ④ 数的就是这一句的出现次数）。
    // 批 90：盘缓存那颗目录一起递进去（从前没配 ⇒ Coil 把它放在**系统 Temp**，与数据目录不同轨；
    // 落点与 http 那颗同一条路：`CacheDirs.inCache(paths.cacheRoot, …)`）。
    DesktopImagePipeline.install(
        galleryHandle.engine,
        CacheDirs.inCache(paths.cacheRoot, DesktopImagePipeline.IMAGE_CACHE_DIR_NAME),
    )
    // 设置页要写侧（共享接口只有读面），所以在集成方拿一次那颗具体件，往下**以形参传**。
    // 不在界面里 cast：那等于把"取静态口"这件事教给每一颗要用的件。
    val gallerySettings = DesktopGalleryPorts.preferences(galleryHandle)
    // 同一形状的第二例（第十二批 批 6）：分级档与屏蔽规则的写侧也在桌面那颗具体件上。
    val guard = DesktopGalleryPorts.contentGuard(galleryHandle)
    // 同一形状的第三例（第三十四批 批 34）：设置那一屏要写 SauceNao 的 key，而契约面只有读流。
    // 拿的是 `createPorts` 里那颗（"凭据只建一颗"），不是再 new 一颗 —— 否则用户填的与请求带的不是同一个事实。
    val galleryCredentials = DesktopGalleryPorts.credentials(galleryHandle)
    // coreDbFile/favoritesDbFile 是纯计算读数：这行自己不建目录，"已建"照实反映磁盘现状。
    val coreDb = DesktopDatabasePorts.coreDbFile()
    val favDb = DesktopDatabasePorts.favoritesDbFile()
    println(
        "D_接线 平台=desktop core=${coreDb.path} 已建=${coreDb.exists()} " +
            "fav=${favDb.path} 已建=${favDb.exists()}",
    )
    // 这台机器给不给写图片、这个包里有没有词典：两句都在启动里念一次，不等到屏上少了什么才回头找
    // （措辞住在读数件里，判据 ⑤ 钉着"只此一处"）。
    println(desktopCapabilityReading(desktopCanCopyImage(), DesktopTagDictionaryAsset.presence()))

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
        val code = runAutofavEvidence(assetDir, paths.dataRoot, proxy, startKey, seedCookies)
        closePersistenceInOrder()
        kotlin.system.exitProcess(code)
        return
    }

    // 崩一枚那枚种子（批 53）。两档各走一条线程路，为的是把"非 EDT"与"EDT"**分别**量一遍：
    // AWT 的事件派发线程有自己那一族异常路，`Thread.setDefaultUncaughtExceptionHandler` 管不管得到它
    // 正是这一枚要答的问题 —— 猜"应该管得到"就等于把那一半留在没量的状态。
    when (ForensicFlags.crash(args.asList())) {
        "thread" -> Thread({ throw IllegalStateException("取证种子：非 EDT 那一笔") }, "取证-非EDT").start()
        "edt" -> javax.swing.SwingUtilities.invokeLater { throw IllegalStateException("取证种子：EDT 那一笔") }
        else -> Unit
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
                    // 必填而不是带默认值的新参数：带默认值时漏传不报错，而"漏传"的形状正好是
                    // 取证时最坏的那种 —— 截图照样出，域照样是默认那一域。
                    startDomain = startDomain,
                    // 同上：查询串漏传就是"搜索那一屏永远拍不到内容"，而它不会报错。
                    startQuery = startQuery,
                    // 同上：起始行漏传就是"那一行永远拍不到"，而它不会报错。
                    startSection = startSection,
                    // 同上：漏传就是"浮层永远拍不到"，而它同样不会报错。
                    // 开不开由 `ForensicFlags.detailPanel` 判（`--detail` 开，或被 `--reverse` / `--copy` 接管）。
                    // `== true` 而不是 `?: false`：没给参数与给了 `=0` 都是关，但"没登记进 KNOWN"
                    // 那种形状会被上面那条 `unknown` 报出来，不在这里替它兜。
                    startDetail = panelOpen,
                    // 批 92：那一屏起在哪一档。`null` = 本轮不改（用「预览清晰度」偏好推出来的那一档）。
                    // 必填 —— 漏传的形状是"参数打进来了而屏上照旧"，判读层念过而落点没有那一格。
                    startDetailTier = detailTierSeed,
                    // 同上：漏传就是"结果那一格永远拍不到"（那一格在浮层里，且要按一下才出来，第三十六批）。
                    startReverse = reverse == true,
                    startReverseFile = reverseFile,
                    // 同上：漏传就是"那一枚按钮从没被按过"，而剪贴板这件事只有按过才量得出（第五十四批）。
                    startCopy = copy == true,
                    // 同上：漏传就是"那一枚按钮从没被按过"，而存图这件事只有按过才量得出盘上多没多那颗文件。
                    startSave = save == true,
                    // 存图落点：这一颗是**算出来的路径**（不建目录 —— 建目录那一笔在真往下写的那一刻），
                    // 所以起窗这一步不会因为"看一眼下载那一屏"而在盘上造出东西。
                    gallerySaveDir = desktopGallerySaveDir(paths),
                    // 同一族口径的第五枚（第八十六批）：那颗历史件在**这里**建，浮层与首页那一屏共用这一颗。
                    // 「看一眼不许在盘上造出东西」那条纪律管的是路径，而这一颗是**要往下写的**那一份内存，
                    // 建它的时机因此与建落点路径不同：它读自己那份档是在构造里同步发生的一次。
                    galleryHistory = DesktopGalleryBrowseHistory.getInstance(paths),
                    // 同上：漏传就是"单人页永远拍不到"（那一屏在第二层里），而它同样不会报错（第二十批）。
                    startArtist = artistTarget,
                    // 同上：漏传就是"排行榜永远停在默认档、期次那一排永远不出现"（第三十批），
                    // 而它同样不会报错 —— 批 29 欠的那张图就缺这一跳。
                    startRanking = rankingTarget,
                    // 同上：漏传就是"漫画详情那一屏永远停在列表"，而它不会报错（第八十批）。
                    // 只在**域真跟过去**时才递：`--artist` 或 `--ranking` 赢而这一枚没接管时，
                    // 递下去会得到一句假读数（`详情=开` 而那一屏根本没组合）。那一格由 comicNote 念实话。
                    startComic = comicSeed.takeIf { comicLanding.domain == DesktopDomain.COMIC },
                    // 同上（第八十一批）：漏传就是"阅读那一屏永远停在详情"，而它不会报错。
                    // 也只在域真跟过去时才递。这一枚**不顺手把详情也种上**：那样会得到
                    // `详情=开 阅读=开` 两格都"开"而底下那一屏从没装载过，是另一种假读数。
                    startRead = readSeed.takeIf { readLanding.domain == DesktopDomain.COMIC },
                    // 第八十七批：顶上哪一档阅读模式。这一枚**不跟着域走** —— 它不改落点，只在阅读那一屏
                    // 真的出现时决定那一屏摆哪一面墙；跟着域走反而会把"没进阅读屏"读成"参数没生效"。
                    startReaderMode = readerModeSeed,
                    // 大图那一层（第五十批）：`null` = 没给参数，而应用根把"没给"读成**进**（默认行为）。
                    // 必填三态原样递 —— 在这里塌成二态就分不出"没给"与"给了 0"，
                    // 而那两个在 `D_落点` 的 `种子=` 那一格里必须是两句话。
                    startViewer = viewer,
                    startViewerZoom = viewerZoom,
                    // 设置页的写侧。必填不给默认值 —— 漏传的形状是"那一屏只能看不能改"，
                    // 而那与"桌面今天没有设置页"是同一种用户观感，却少了一句实话。
                    settings = gallerySettings,
                    // 分级档与屏蔽规则的写侧，同上：必填不给默认值。
                    guard = guard,
                    credentials = galleryCredentials,
                    comic = DesktopComicEngineConfig(
                        assetDir = assetDir,
                        dataDir = paths.dataRoot,
                        proxy = proxy,
                        sourceKey = startKey,
                        // 懒装载：见 DesktopComicEngineConfig.favorites 的注释。
                        favorites = ::favorites,
                        // 阅读那一屏写历史要用它（批 82）。同一颗懒装载，同一理由。
                        history = ::history,
                        // 批 83：源管理那一屏读写「哪几颗被停用」。同一口径的懒装载 —— 建它要数据目录，
                        // 而"今天切到漫画域"不等于"要看源"。那份状态只影响这台机器（详见 DesktopSourceState）。
                        sourceState = { DesktopSourceState(paths) },
                        // 批 83.1：`--disable=` 那枚取证种子。必填、不给默认值 —— 漏在这一跳上就是
                        // "`-Pdisable=jm` 递上而屏上照旧"，与本批要抓的缺陷同形（同一颗纪律第十次）。
                        seedDisabled = seedDisabled,
                        // 批 88·下半：`--cookie=` 那几枚凭据种子。同样必填不给默认值 —— 漏在这一跳上
                        // 就是"参数递上而请求照旧不带凭据"，与本批要抓的缺陷同形。
                        seedCookies = seedCookies,
                        // 第九十六批：`--download=` 那一话的种子。同样必填不给默认值 —— 漏在这一跳上
                        // 就是"参数递上而盘上一颗文件都没有"，与本批要抓的缺陷同形（同一颗纪律第十一次）。
                        downloadSeed = downloadSeed,
                        // 第一百批：`--download-cancel-after=` 那一档。同样必填不给默认值 ——
                        // 漏在这一跳上就是"参数递上而那一趟照旧跑到尾"，与本批要抓的缺陷同形。
                        downloadCancelAfter = downloadCancelAfter,
                        // 第九十八批：`--delete=` 那一话的种子。同样必填不给默认值（同一颗纪律第十二次）。
                        deleteSeed = deleteSeed,
                        repairSeed = repairSeed,
                        // 第八十七批：阅读那一屏的模式偏好。与 `history` / `favorites` 同一条处理 ——
                        // 装配参数在集成方手里，界面只吃形参。同一数据根只建一颗（两颗各自缓存同一份 JSON）。
                        readerPrefs = DesktopComicReaderPreferences.getInstance(paths),
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
    // 抬窗只在这一颗函数里发生，而这一颗只在 `-Pshot` 分支被调用 ⇒ 正式用户永远不会被抢前台；
    // 无论截得到截不到，收尾都要落回 topmost（见下面的 try/finally）。
    //
    // 先拿 Win32 那颗句柄再抬窗：`setTopmost` 要它。拿不到也照样往下走，由 setTopmost 自己在
    // 读数里说"hwnd 取不到，未抬" —— 不在这里静默跳过。
    // ⚠️ 只认 Win32 那条：AWT 的 `Frame` 对象在窗口被关掉后仍可能报 `visible=true` 与旧尺寸，
    //   拿它的 bounds 去截 = 截到壁纸（`desktop-{nhentai3,jm-reader4,reader6}.png` 三张废图都是这么来的）。
    val hwnd = findHwnd(APP_TITLE)
    try {
        println("D_截图 抬窗 ${raiseOwnWindowForShot(frame, hwnd)}")
        // 合成器要一拍才真的把这扇窗压到普通窗之上；这一段**不阻塞正式路径**（正式用户走不到这里）。
        // 不用协程的 delay 是因为 selfShot 不是 suspend，而取证这一跑截完就退出进程。
        Thread.sleep(RAISE_SETTLE_MS)
        runCatching {
            if (hwnd == null) {
                println("D_截图 无效：Win32 已查不到标题窗口（多半已被关掉），AWT 的 bounds 不可信")
                return
            }
            val rect = screenRectOf(hwnd) ?: run {
                println("D_截图 无效：GetWindowRect 失败")
                return
            }
            // 锁屏/切虚拟桌面时窗口矩形照样存在，截出来是纯壁纸 —— 先确认屏幕中心那点真的落在本窗上
            val cx = rect.x + rect.width / 2
            val cy = rect.y + rect.height / 2
            if (!isPointOnWindow(hwnd, cx, cy)) {
                // 报**谁**占着那点，不报"多半已锁屏"那种猜法：取证的人拿这句就能决定下一步
                val at = windowAtPoint(cx, cy)
                println(
                    "D_截图 无效：中心点 ($cx,$cy) 落在 " +
                        "${if (at == null) "无窗" else "hwnd=$at " + windowDescribe(at)}" +
                        "，本窗 hwnd=$hwnd ${windowDescribe(hwnd)} —— 不产出假证据"
                )
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
    } finally {
        // 两手都要落回：AWT 那层的开关，与 Win32 那颗 WS_EX_TOPMOST 位。
        // 只解一半的话，这扇窗在本进程剩下的时间里仍压着用户的屏幕 —— 而它本来只是为了一张图。
        runCatching {
            frame.isAlwaysOnTop = false
            setTopmost(hwnd, false)
        }.onFailure { println("D_截图 落回 topmost 失败 ${it::class.java.simpleName}") }
    }
}

/**
 * 取证专用：把**自己这扇窗**抬到普通窗之上，让 `WindowFromPoint` 真认得它
 * （2026-10-04 第三批，用户拍板走"抬自己这扇窗"那条）。
 *
 * 真干活的是 `setTopmost`（Win32 的 `SetWindowPos(HWND_TOPMOST)` + 回读 `WS_EX_TOPMOST` 位），
 * AWT 那层的 `setAlwaysOnTop` 只是**顺带**：实测它报 `isAlwaysOnTop=true 支持=true visible=true`
 * 而中心点仍然被用户的 Chrome 窗占着 —— 那一层样式没落到 Win32，所以不能拿它当证据。
 * 至于 `SetForegroundWindow`：它受系统的 foreground lock 管，非前台进程调它常常只换来任务栏闪一下，
 * 要绕过那把锁得 `AttachThreadInput` 附到前台线程 —— 比"抢前台"更脏的一手，不做。
 *
 * ⚠️ **不调 `requestFocus()`**：取证要的只是 z-order，不是输入焦点。
 * 把键盘焦点从用户正在打的字上抢走，与"截一张图"完全不成比例。
 *
 * 返回的是**一句读数**，不是布尔：抬没抬起来、位上没上、抛了什么，都得留在 stdout 里 ——
 * 只回 true/false 的话，读图的人分不清"窗口真在上面"与"根本抬不动"。
 */
private fun raiseOwnWindowForShot(frame: java.awt.Frame, hwnd: Long?): String {
    val awt = runCatching {
        if (!frame.isAlwaysOnTopSupported) {
            "AWT 不支持 topmost"
        } else {
            frame.isAlwaysOnTop = true
            frame.toFront()
            "AWT 报了 isAlwaysOnTop=${frame.isAlwaysOnTop}"
        }
    }.getOrElse { "AWT 那层抛了 ${it::class.java.simpleName}" }
    return "$awt ｜ Win32 ${setTopmost(hwnd, true)}"
}
