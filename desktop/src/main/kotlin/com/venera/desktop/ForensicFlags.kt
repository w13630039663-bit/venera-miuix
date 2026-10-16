package com.venera.desktop

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GalleryRankings
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.gallery.ui.DesktopArtistTarget
import com.venera.desktop.gallery.ui.DesktopComicReading
import com.venera.desktop.gallery.ui.DesktopComicTarget
import com.venera.desktop.gallery.ui.DesktopDetailTier
import com.venera.desktop.gallery.ui.DesktopDomain
import com.venera.desktop.gallery.ui.DesktopGalleryHomeSectionKey
import com.venera.desktop.gallery.ui.DesktopRankingSeed
import com.venera.desktop.gallery.ui.desktopDetailTierLabel
import com.venera.desktop.gallery.ui.desktopDomainRows
import java.time.LocalDate

/**
 * `--start-domain=` 的三种读数。**"没给"与"给了但认不出"必须是不同的读数**，
 * 而后者绝不能塌回默认域 —— 判读理由见 [ForensicFlags.startDomain]。
 */
internal sealed class StartDomainReadout {
    /** 参数没给：按默认域起，不打读数 */
    object NotGiven : StartDomainReadout()

    /** 认出来了。[raw] 是用户原样打进来的那串（读数里要能对上他打的是什么） */
    data class Resolved(val domain: DesktopDomain, val raw: String) : StartDomainReadout()

    /** 给了但认不出（含只给名字不带值）：调用方必须中止本次启动 */
    data class Unresolved(val raw: String) : StartDomainReadout()
}

/**
 * `--artist=` 的四种读数（第二十批）。
 *
 * 与 [StartDomainReadout] 的三档差别是**多一档"给了也认得出，但它与 `--start-domain=` 不是一屏"**：
 * 单人页住在「画师」那一域的第二层里，所以这两枚参数会同给，而同给时赢的是 [ArtistReadout.Resolved]
 * —— 那是一次**覆盖**而不是故障，[ForensicFlags.artistNote] 负责把它念出来（不吭声就是
 * "参数互相打架时悄悄赢了一边"那一族）。
 */
internal sealed class ArtistReadout {
    /** 没给：本轮不进单人页，域也不跟着动 */
    object NotGiven : ArtistReadout()

    /** 认得出。[raw] 是原样打进来的那串（读数里要能对上用户打了什么） */
    data class Resolved(val target: DesktopArtistTarget, val raw: String) : ArtistReadout()

    /**
     * 给了但读不出。[why] 必须说清是**哪一种**形状：缺站点段、站点认不出、名字段是空的
     * —— 三档的修法各不一样，合成一句"格式不对"等于什么都没告诉打参数的人。
     */
    data class Unresolved(val raw: String, val why: String) : ArtistReadout()
}

/**
 * `--ranking=` 的三档读数（第三十批）。
 *
 * 形状逐字照 [ArtistReadout]：没给 / 认得出 / 读不出，而 [RankingReadout.Unresolved.why]
 * 必须说清差的是**哪一段**（档段认不出、期段写法不对、这一档不给回看、那一早于地板、那一还没到）——
 * 五档的修法各不一样，合成一句"格式不对"等于什么都没告诉打参数的人。
 *
 * 与那颗的**不同**处：这一枚认得出之后还可能有两种落空，都在 [ForensicFlags.rankingNote] 里出声 ——
 * 域与行会被它接管（那一屏在「图库」域的「排行榜」那一行里），而 `--artist` 同给时赢的是那一枚。
 */
internal sealed class RankingReadout {
    /** 没给：本轮不动档位，那一屏今天怎么进来就怎么摆（档 = 站方默认序，期 = 本期） */
    object NotGiven : RankingReadout()

    /** 认得出。[raw] 是原样打进来的那串（读数里要能对上用户打了什么） */
    data class Resolved(val seed: DesktopRankingSeed, val raw: String) : RankingReadout()

    /** 给了但读不出；[why] 点名差的是哪一段。 */
    data class Unresolved(val raw: String, val why: String) : RankingReadout()
}

/**
 * 取证参数的**诚实形状**（Task 6 第 4 件）。
 *
 * 改前的两颗毛病都是"看起来像开关但其实不是"：
 * - `args.contains("--autofav")` 只看给没给，`-Pautofav=0` 一样点亮 ⇒ 关掉它得靠"不给参数"，
 *   而"给了 0"却是开 —— 同一个参数两种口径。
 * - 两参同给时 `--favcheck` 先 `exitProcess`，`--autofav` 被**静默吃掉**，日志里看不见这回事。
 *
 * 现在：**值可判**（`1/true/on` 开、`0/false/off` 关，读不懂的值按关处理并如实说读不懂）、
 * **同给如实说一句**、**拼错的一律报未识别**。判读逻辑单独一颗对象是为了让它**可测**
 * （`main()` 要建窗口、要数据目录，测试碰不到；这里每条都能直接断返回值）。
 */
internal object ForensicFlags {

    /** 起始域那枚参数的名字。登记在 [KNOWN] 里，漏登记就会被 [unknown] 报成拼错 */
    private const val START_DOMAIN = "--start-domain"

    /** 搜索框预填什么。同样登记在 [KNOWN] 里 */
    private const val QUERY = "--query"

    /** 域内 pane 里哪一行先选中（第十批 ⑤）。同样登记在 [KNOWN] 里 */
    private const val START_SECTION = "--start-section"

    /**
     * 起窗后**自己把详情浮层摆出来**（第十一批 P0/P1）。
     *
     * 为什么需要它：那一层要**点一张卡**才出来，而 agent 侧不许动鼠标 ——
     * 于是 P0 与 P1 两批的观感都拍不到图（手册 §27.5 记的就是这个洞）。
     * 只给名字不带值 = 开，`=0` = 关（与 `--autofav` 同一把判据，不是 `args.contains`）。
     */
    private const val DETAIL = "--detail"

    /**
     * 起窗后**直接进那一位画师的单人页**（第二十批）。
     *
     * 为什么需要它：那一屏是"点一下才出来的那一层"（要点画师列表上的「看他的图」），
     * 而鼠标自验是禁的（手册 §8）—— 批 19 的判据全绿，图证那一格只能写"没拍成"。
     * 值两截：`<站点>/<标签>`，理由见 [artist]。
     */
    private const val ARTIST = "--artist"

    /**
     * 起窗后**把排行榜拨到那一档、那一期**（第三十批）。
     *
     * 为什么需要它：期次那一排的三枚按钮只在档切到月/年时才出现，而鼠标自验是禁的（手册 §8）——
     * 批 29 六条判据全绿而那三枚结构性地拍不到，"那一层验过了"缺的就是这一枚。
     * 值两截 `<档>[/<期>]`，理由见 [ranking]。
     */
    private const val RANKING = "--ranking"

    /**
     * 起窗后**直接进哪一本漫画的详情那一屏**（第八十批）。
     *
     * 为什么需要它：详情那一屏的入口是封面，而 agent 侧禁鼠标（手册 §8）⇒ 不带它时这一屏的图证
     * 只能记成"没拍成"。值两截 `<漫画号>[/<题名>]`：题名是"卡上那个题名"（站方还没回来时屏上得有字），
     * 不给题名时用号充（拿漫画号当题名是另一种谎，见 `DesktopComicTarget` 那颗注释）。
     */
    private const val COMIC = "--comic"

    /** `--disable=<源键>`（批 83.1）：把某一颗随包源当成本台**停用着**起来，只为把"停用真折进装载"拍到帧。 */
    private const val DISABLE = "--disable"

    /**
     * `--cookie=<host>:<名>=<值>`（批 88·下半）：把一枚凭据当成本次装载**手填着**起来。
     *
     * 为什么这一族最后那一跳需要它：手填那一形要人真粘一次，而鼠标键盘自验是禁的（手册 §8），
     * 「罐吃不吃得下这枚凭据、随请求发不发得出」那一格否则只能记"没验"。
     * 值为什么按**请求 host** 而不是源键：那颗罐分桶的轴就是 host（`DesktopCookieJar` 三条口径之一），
     * 种子递的得是被量那一层的形状。
     */
    private const val COOKIE = "--cookie"

    /** 起窗后直接进**阅读那一屏**（第八十一批）。同样登记在 [KNOWN] 里。 */
    private const val READ = "--read"

    /**
     * 进阅读那一屏时**先落在第几页**（第八十二批半，`--read-page=<索引>`）。
     *
     * 为什么单开一枚而不是 `--read` 的第四截：章名里允许有斜杠（见 [read]），
     * 再多切一截就会把章名劈掉一截，而劈剩的那半在屏上看着像"章名就这么短"。
     *
     * 这一枚是**补量具**不是新功能：批 82 收尾时「滚到哪一页就记到哪一页」那一格只能记"没验"——
     * 离屏语义树里的 `LazyListState` 量不到滚动（`scrollToItem(3)` 之后 `firstVisibleItemIndex`
     * 还是 0）。真窗口里让那一屏一开局就落在指定那一页，页号流进写的那一笔就有了一行 stdout
     * 与库里那一行，而不是靠推断。
     */
    private const val READ_PAGE = "--read-page"

    /**
     * 起窗后把阅读那一屏**顶到哪一档模式**（第八十七批，`--read-mode=<键或枚举名>`）。
     *
     * 为什么需要它：横向那一档要"点模式那一排的一枚"才出来，而取证这一路不许动鼠标（手册 §8）
     * —— 没有它，那一面墙在帧上永远不存在，本批那几条翻页的尺子就只有纸面一半有对证。
     *
     * ⚠️ 种子只作**本次初值**，不写那枚键：无人值守那一趟不该把用户选的档位挪走（批 83.1 同一条口径），
     * 而"这一档是被顶上的"那一句要上屏（`desktopReaderModeSeedNote`）。
     */
    private const val READ_MODE = "--read-mode"

    /**
     * 起窗后把某一话**整话写进本机**（第九十六批，`--download=<漫画号>/<话号>`）。
     *
     * 为什么需要它：那一枚动作挂在**长按**上（桌面没有右键，第二枚动作只能走长按 —— 见批 96 rounds §2），
     * 而取证这一路不许动鼠标与光标（手册 §8.1）⇒ 没有它，"真源站取一整话落盘"这一格在交付物里
     * 永远量不到（10 颗纯判据 + 3 颗语义树都证不了网络与盘那一段）。
     *
     * ⚠️ 它走的是**与长按同一条代码路**（同一颗 runner、同一套目录名、同一行 `D_下载` 读数），
     * 不是另开一条"只为取证"的路 —— 否则量到的是种子那条路而不是用户那一条（本仓那一族第六次）。
     */
    private const val DOWNLOAD = "--download"

    /**
     * 起窗后那一趟整话下载**写下 N 页就自己停手**（第一百批，`--download-cancel-after=<N>`）。
     *
     * 为什么需要它：取消那一路跑在 IO 线程的一圈页循环里，而"人在恰好那一秒按下去"复现不了
     * （89 页那一趟还要等真网络）⇒ 没有这一枚，交付物里量不到"停在第 N 页而盘上那 N 页还在、
     * 清单说没齐"这一整档，而那正是这一批唯一的新降级路径。
     *
     * ⚠️ 它扳的是**与屏上那枚「取消」同一颗闸**（`DesktopComicDetail` 里那一格 `shouldCancel`），
     * 不是另开一条"只为取证"的取消路（批 98 那条纪律的同一形）。
     * ⚠️ `N<=0` 当**读不出**处理（念一句实话）而不是"立刻停手"：那一档与"没给这枚参数"在屏上同形，
     * 收下来就会让下一次取证以为量过了。
     */
    private const val DOWNLOAD_CANCEL_AFTER = "--download-cancel-after"

    /** 起窗后**把某一话从本机删掉**（第九十八批，`--delete=<漫画号>/<话号>`）：那一屏的把手要按两次才真删，
     *  而鼠标自验那一条路量不到"回读盘"那一档 ⇒ 走同一颗 runner。判读与纪律见 [delete]。 */
    private const val DELETE = "--delete"

    /** 起窗后**把某一话排回下载队列**（第一百一十一批，`--repair=<漫画号>/<话号>`）：
     *  「本机已下载」那一屏对缺页那一行新添的把手，鼠标自验量不到"按下之后表里多几笔、盘上一颗没动"
     *  那一格 ⇒ 走同一颗种子（与 [DELETE] 同一族，两枚号之间那一截斜杠照旧要 `%2F`）。 */
    private const val REPAIR = "--repair"

    /**
     * 起窗后**自动按一次「找同一张图」**（第三十六批）。
     *
     * 为什么需要它：结果那一格是"按一下才出来的那一层"，而鼠标自验是禁的（手册 §8）——
     * 批 35 十二道判据全绿，图证那一格只能拍到那枚按钮本身。
     * 值只判开不开（与 `--detail` 同一把 `state` 判据），但**它会顺带把浮层摆出来**，
     * 那一格在浮层里 ⇒ 接管那一步与那句实话各是一颗跑得动的函数（[detailPanel] / [reverseNote]）。
     */
    private const val REVERSE = "--reverse"

    /**
     * 起窗后**替用户挑好那一张本机的图**（第七十七批 批 77 下半，`--reverse-file=<路径>`）。
     *
     * 为什么需要它：那一格的入口是**系统文件框**，而鼠标自验是禁的（手册 §8）⇒ 不带这枚参数时，
     * 路径之后的三跳（闸口判那张 → 读字节发车 → 结果那一格）结构性地拍不到。
     *
     * ⚠️ 它与 [REVERSE] 不是一件事，两处文档不许互相冒充：`--reverse` 是"自动按一次屏上已有的按钮"，
     * 这一枚是"**跳过**文件框那一步"。文件框那一步今天仍然没有读数（只有代码在场那一条），
     * 所以这一枚验到的所有东西都不算"挑图那一步验过了"。
     */
    private const val REVERSE_FILE = "--reverse-file"

    /**
     * 点卡之后**进不进大图那一层**（第五十批）。判开不开用 [state]（与 `--detail` 同一把）。
     *
     * ⚠️ 默认行为是**进**：用户原话是"点卡就跳到大图，现在都没这个功能" ——
     * 这一层是产品的默认行为，不是取证专用开关。`-Pviewer=0` 只服务取证时"只想拍浮层那一屏"。
     * 所以"没给参数"（null）与"给了 0"（false）在屏上是同一个行为，
     * 但在 `D_落点` 的 `种子=` 那一格里必须是两句话。
     */
    private const val VIEWER = "--viewer"

    /**
     * 起窗后**放大到几倍**（取证用，`--viewer-zoom=`）。
     *
     * 为什么单独一枚而不是复用 `--viewer`：前者是"进不进那一层"（开不开），
     * 这一枚是"进了之后放大多少"（一个数）。两件事的读数形状不同，三档也不同。
     */
    private const val VIEWER_ZOOM = "--viewer-zoom"

    /**
     * 起窗后**落在哪一档**（批 92，`--detail-tier=预览|大图|原图|PREVIEW|SAMPLE|ORIGINAL`）。
     *
     * 为什么需要它：三档里只有「大图」是默认落点，而**分块解码那一层只在放大之后才跑** ——
     * "原图档 × 2.5 倍"那一格从前要靠手点两下（先换档再放大），而鼠标自验是禁的（手册 §8）
     * ⇒ 不带这枚参数时那一格结构性拍不到，批 89 落的那条「原图档在轨」只能停在"结构上在轨"。
     *
     * ⚠️ 它与 [VIEWER_ZOOM] 是两枚而不是复用一枚：前者定"看哪一份字节"，后者定"那一屏放大多少"，
     * 两件事的读数形状（档位名 vs 一个数）与三档都不同。
     * ⚠️ 落点不住在初值而住在**点卡那一跳**：`tier` 那颗状态的初值会在同一次点卡里被升档那一步盖掉
     * （取证那颗 `--detail` 走的就是同一枚 lambda），写成初值就是"念了而屏上没动"那一族第五次。
     */
    private const val DETAIL_TIER = "--detail-tier"

    /**
     * 起窗后**自动按一次「复制图片」**（第五十四批）。
     *
     * 为什么需要它：「剪贴板里到底进去了一张什么」只有按下去才量得出，而那一枚按钮在浮层里
     * 且鼠标自验是禁的（手册 §8）⇒ 与 [REVERSE] 同一族缺口，同一把 `state` 判据，
     * 顺带把浮层摆出来那一步也走同一颗 [detailPanel]。
     */
    private const val COPY = "--copy"

    /**
     * 起窗后**自动按一次「存到本机」**（批 85）。
     *
     * 为什么需要它：这一格唯一的证据是**盘上多出来的那颗文件**，而它只有按下去才存在 ——
     * 光起个窗什么都不写。禁鼠标那一侧不带种子就只剩那枚按钮本身可拍（与 [COPY] 同一族缺口，
     * 同一把 [state] 判据，接管浮层那一步也走同一颗 [detailPanel]）。
     */
    private const val SAVE = "--save"

    /**
     * 故意崩一笔（批 53）：`--crash=thread|edt`。
     *
     * ⚠️ 这一条是**补登记**：批 53 写了判读与消费点，却没把它列进这张表，
     * 于是带 `--crash` 起窗时 [unknown] 会额外念一句"不认识的参数"（读数自相矛盾）。
     * 那一族的尺子（"新增参数必须登记"）在这里第一次抓到自己人。
     */
    private const val CRASH = "--crash"

    /** 认识的参数名（带值也认：`--autofav=1`） */
    val KNOWN = listOf(
        "--proxy", "--shot", "--autofav", "--favcheck",
        START_DOMAIN, QUERY, START_SECTION, DETAIL, ARTIST, RANKING, REVERSE, REVERSE_FILE,
        VIEWER, VIEWER_ZOOM, COPY, SAVE, CRASH, COMIC, READ, READ_PAGE, READ_MODE, DISABLE, COOKIE, DETAIL_TIER, DOWNLOAD, DELETE, REPAIR,
    )

    private val ON = setOf("1", "true", "on")
    private val OFF = setOf("0", "false", "off")

    /**
     * 某个开关的判读。
     * - `null` = 这个参数**没给**（本轮与它无关，不打读数）
     * - 只给名字不带值（`--autofav`）= 开
     * - 带值：`1/true/on` 开、`0/false/off` **关**（关照样打一条"参数给了但值为关"，不装成没给）
     * - 值读不懂：按**关**处理并打"值无法判读"（不猜成开）
     */
    fun state(args: List<String>, name: String): Boolean? {
        val given = args.firstOrNull { it == name || it.startsWith("$name=") } ?: return null
        val raw = given.removePrefix("$name=").takeIf { it != given }
        if (raw == null) {
            println("D_取证参数 $name 已给（不带值），按开处理")
            return true
        }
        val value = raw.trim().lowercase()
        return when {
            value in ON -> {
                println("D_取证参数 $name 开（值=$raw）")
                true
            }

            value in OFF -> {
                println("D_取证参数 $name 参数给了但值为关（值=$raw），本轮不执行")
                false
            }

            else -> {
                println("D_取证参数 $name 值无法判读（值=$raw），按关处理")
                false
            }
        }
    }

    /**
     * `--crash=thread|edt` 的判读（批 53）：**故意崩一笔**，用来验"崩了念不念得出一行归属"。
     *
     * 为什么要有这一枚而不是"等它哪天自己崩"：那一格的证据面只在 stdout 与那枚 Error 框上，
     * 而框是空白的、鼠标是禁的 ⇒ 没有这枚种子，"崩溃读数"这一件东西只能靠真事故去验（那就是事后验尸）。
     * 两档各走一条线程路：`thread` = 非 EDT（`Thread.setDefaultUncaughtExceptionHandler` 天然管得到），
     * `edt` = 事件派发线程（AWT 那一族有自己的异常路，正是这一枚要量的东西）。
     * 认不出值只出声不中止 —— 中止等于让一枚取证参数把应用关了。
     */
    fun crash(args: List<String>): String? {
        val given = args.firstOrNull { it.startsWith("--crash=") } ?: return null
        val raw = given.removePrefix("--crash=").trim().lowercase()
        return when (raw) {
            "thread", "edt" -> {
                println("D_取证参数 --crash 崩一笔（值=$raw）")
                raw
            }

            else -> {
                println("D_取证参数 --crash 值无法判读（值=$raw），只认 thread 与 edt，本轮不崩")
                null
            }
        }
    }

    /**
     * `--viewer-zoom=` 的判读：起窗后放大到几倍（第五十批）。
     *
     * 三档与 [state] 同一族：没给 / 认得出 / 给的不是个数。
     *
     * ⚠️ 最后一档**不猜一个倍数**：猜了就等于"我给你设了个你没说过的放大倍数"，
     * 而那一帧会当成真读数被当成"参数生效了"—— 与 `StartDomainReadout.Unresolved`
     * 那一族同一条纪律：认不出就说不认。
     */
    fun viewerZoom(args: List<String>): Float? {
        val given = args.firstOrNull { it == VIEWER_ZOOM || it.startsWith("$VIEWER_ZOOM=") } ?: return null
        val raw = given.removePrefix("$VIEWER_ZOOM=")
        val value = raw.toFloatOrNull()
        if (value == null || value.isNaN()) {
            println("D_取证参数 $VIEWER_ZOOM=$raw 不是个数，本轮不放大（不替你猜一个倍数）")
            return null
        }
        println("D_取证参数 $VIEWER_ZOOM 起窗后放大到 $value")
        return value
    }

    /**
     * `--start-domain=` 的判读：无人值守取证要落在**哪一域**起步。
     * 认 [DesktopDomain] 的**枚举名**（大小写不敏感，`SETTINGS`）与 rail 上的**中文题名**
     * （`设置`）两种写法。
     *
     * ⚠️ 认不出时**不塌回默认域**，交回一个 [StartDomainReadout.Unresolved] 由调用方中止进程。
     * 理由不是"严谨"，是这种产物的形状：无人值守取证产出的是**一张按目标域命名的截图**，
     * 而回落会产出一张图库域的图 —— 文件名替它作了伪证，读的人不会知道参数没生效。
     * 这与 [state] 那条"读不懂的值按关处理"是**不同**的口径：那颗读不懂顶多半次取证白跑，
     * 这颗读不懂会产出一条假证据。
     *
     * 首选枚举名：中文值要穿过 gradle `-Pargs=` 与 Windows 控制台那一层编码，那一段不在本仓的保证里。
     */
    fun startDomain(args: List<String>): StartDomainReadout {
        val given = args.firstOrNull { it == START_DOMAIN || it.startsWith("$START_DOMAIN=") }
            ?: return StartDomainReadout.NotGiven
        val raw = given.removePrefix("$START_DOMAIN=").let { if (it == given) "" else it }
        val hit = DesktopDomain.entries.firstOrNull {
            it.name.equals(raw, ignoreCase = true) || it.title == raw
        }
        return hit?.let { StartDomainReadout.Resolved(it, raw) } ?: StartDomainReadout.Unresolved(raw)
    }

    /**
     * `--query=` 的判读：搜索那一屏的输入框**预填什么**。
     *
     * 这一枚补的是一条**取证缺口**：搜索域是七枚域里唯一"不给输入就拍不到东西"的，
     * 而键盘与鼠标自验是禁的（手册 §8）—— 于是"AI 角标在真数据上长什么样"这类账
     * 结构性地拍不到。把它变成命令行上的读数之后，那一屏才进得了无人值守取证。
     *
     * 三档分开：`null` = 没给（这一轮不预填）；`""` = 给了但是空的（那是用户**要**空态那一屏，
     * 空态与建议词那一档）；非空 = 预填它。塌掉前两档就会把"我要拍空态"读成"没这一回事"。
     *
     * ⚠️ 与 [startDomain] 不同，这一枚**认不出没有什么可中止的**：它的值是一条自由文本，
     * 任何字符串都是合法查询。所以它不像那颗一样产不出假证据 —— 落在别的域时由 [queryNote] 出声。
     */
    fun query(args: List<String>): String? {
        val given = args.firstOrNull { it == QUERY || it.startsWith("$QUERY=") } ?: return null
        val raw = given.removePrefix("$QUERY=").takeIf { it != given }
        return if (raw == null) {
            println("D_取证参数 --query 已给（不带值），按空查询起（那是空态那一屏，不是没给）")
            ""
        } else {
            println("D_取证参数 --query 预填搜索框（值=$raw）")
            raw
        }
    }

    /**
     * 查询串落在**没有搜索框的那一域**时的那句实话。返回 `null` = 不必说。
     *
     * 不中止：域名由 `--start-domain=` 自己念，这一枚不会替它作伪证；
     * 但绝不静默 —— 静默就是"我给了它却没做任何事"，那是假开关的口径。
     */
    fun queryNote(query: String?, domain: DesktopDomain): String? =
        if (query == null || domain == DesktopDomain.SEARCH) {
            null
        } else {
            "D_取证参数 --query=$query 落在「${domain.title}」域，那一屏没有搜索框，" +
                "本轮不预填任何东西（要它生效请配 --start-domain=SEARCH）"
        }

    /**
     * `--reverse-file=` 的判读：起窗后**替用户挑好**那一张本机的图。
     *
     * 与 [query] 那三档**故意不一样**：查询串是自由文本，空串是用户真想要的那一屏；
     * 而一条路径没有"空"这一形 —— `--reverse-file=` 是打错了，不是"挑了一张空文件"。
     * 所以那一档交回 `null` 并由 [reverseFileNote] 出声，绝不静默当成"没给"。
     *
     * 路径**原样交**：不补分隔符、不做规范化、不猜相对目录 —— 猜出来的那一条不是用户打的那一条，
     * 而那一串最后由 `desktopLocalReverseGate` 逐字回答"本机找不找得到"。
     */
    fun reverseFile(args: List<String>): String? {
        val given = args.firstOrNull { it == REVERSE_FILE || it.startsWith("$REVERSE_FILE=") } ?: return null
        val raw = given.removePrefix("$REVERSE_FILE=").takeIf { it != given }
        return if (raw.isNullOrBlank()) {
            println("D_取证参数 --reverse-file 没带上那一条路径（本轮不挑图，看下面那一句）")
            null
        } else {
            println("D_取证参数 --reverse-file 替用户挑好那一张（值=$raw）")
            raw
        }
    }

    /**
     * 那一枚种子落空时的两句实话（各念各的，两枚同给时是两句而不是一句）。返回 `null` = 不必说。
     *
     * - 给了参数而**没带路径**：那就是打错了，说出来而不是当成"没给"；
     * - 带了路径而起始域不是「搜索」：那一格根本不在组合里 ⇒ 与 [queryNote] 同一条纪律，
     *   绝不静默（静默就是"我给了它却没做任何事"，那是假开关的口径）。
     */
    fun reverseFileNote(raw: String?, resolved: String?, domain: DesktopDomain): String? = when {
        raw == null -> null
        resolved == null -> "D_取证参数 --reverse-file 没有那一串路径（给了空值）⇒ 本轮不挑图，" +
            "那一格照旧等用户自己按"
        domain != DesktopDomain.SEARCH -> "D_取证参数 --reverse-file=$resolved 落在「${domain.title}」域，" +
            "那一格住在「搜索」那一屏 ⇒ 本轮不挑图（要它生效请配 --start-domain=SEARCH）"
        else -> null
    }

    /** 拼错的 / 不认识的 `--xxx`（调用方逐条打读数，不许静默忽略） */
    fun unknown(args: List<String>): List<String> =
        args.filter { it.startsWith("--") && KNOWN.none { name -> it == name || it.startsWith("$name=") } }

    /**
     * `--start-section=` 的判读：域内 pane 里**哪一行**先被选中。
     *
     * 这一枚补的是同一族取证缺口，只是深一层：`--start-domain=` 只到**域**，到不了域内那一行。
     * 于是图库域今天有两屏结构性地拍不到 —— 「最新」（第十批 ⑤ 刚点亮）与另两行未实现档的读数。
     * 键盘与鼠标自验是禁的（手册 §8），所以"落到那一行"只能从命令行进来。
     *
     * 三档分开：`null` = 没给（不预选，屏上就是这一域的第一行）；`""` = 给了不带值
     * （那是一条**读得出形状**的输入，对不上任何行 ⇒ 由 [sectionNote] 出声，不许当成"没给"）；
     * 非空 = 拿它去核行表（键与中文题名两种都认，与 `--start-domain=` 同一口径）。
     */
    fun section(args: List<String>): String? {
        val given = args.firstOrNull { it == START_SECTION || it.startsWith("$START_SECTION=") } ?: return null
        val raw = given.removePrefix("$START_SECTION=").takeIf { it != given }
        return if (raw == null) {
            println("D_取证参数 --start-section 已给（不带值），那是对不上任何行的一枚，屏上不会预选任何东西")
            ""
        } else {
            println("D_取证参数 --start-section 要去到那一行（值=$raw）")
            raw
        }
    }

    /**
     * 起始行**对不上这一域任何一行**时的那句实话。返回 `null` = 不必说。
     *
     * 与 [queryNote] 同一条纪律：不中止（这一枚没有"开错窗"的代价），但绝不静默 ——
     * 静默就是"我给了它却没做任何事"，而屏上看到的仍是第一行，那张图会顶着目标行的文件名。
     */
    fun sectionNote(section: String?, matched: Boolean, domain: DesktopDomain): String? =
        if (section == null || matched) {
            null
        } else {
            "D_取证参数 --start-section=$section 在「${domain.title}」域对不上任何一行（键与题名都不认），" +
                "本轮没预选任何东西 ⇒ 屏上是这一域的第一行，不是那一行"
        }

    /**
     * `--artist=` 的判读：起窗后**直接进那一位画师的单人页**（第二十批）。
     *
     * 值两截 `<站点>/<标签>`，因为那一屏是**单腿的**（一次只走一站，见 `DesktopArtistTarget`
     * 那颗目的地），而同一个标签在不同站上不是同一批图 —— 只给名字就得替用户猜一站，
     * 猜错的那张截图会顶着对的名字。站点段三种写法都认（键 / 枚举名 / 站方自己的写法），
     * 与 `--start-domain=` 同一口径。
     *
     * ⚠️ 斜杠按**第一个**分而不是最后一个：站方标签字符集没禁斜杠，切错方向会把一枚标签劈成两枚，
     * 而劈开的两半在屏上看着都像"这一位没什么图"。名字段里的空格原样留着（同 [query] 那条）。
     *
     * 读不出时**不中止启动**（与 [startDomain] 不同）：这一枚落空只是"没进去那一屏"，
     * 域还是用户自己念的那一域，代价是半轮取证白跑；由 [artistNote] 把这一句念出来。
     */
    fun artist(args: List<String>): ArtistReadout {
        val given = args.firstOrNull { it == ARTIST || it.startsWith("$ARTIST=") } ?: return ArtistReadout.NotGiven
        val raw = given.removePrefix("$ARTIST=").let { if (it == given) "" else it }
        val slash = raw.indexOf('/')
        if (slash < 0) {
            return ArtistReadout.Unresolved(raw, "没有站点段（要写成 <站点>/<标签>，例如 ${GallerySite.entries.first().routeKey}/某标签）")
        }
        val siteKey = raw.substring(0, slash)
        val site = GallerySite.entries.firstOrNull {
            it.routeKey.equals(siteKey, ignoreCase = true) ||
                it.name.equals(siteKey, ignoreCase = true) ||
                it.displayName == siteKey
        } ?: return ArtistReadout.Unresolved(raw, "站点认不出（站点段=$siteKey，可选：" + siteKeys() + "）")
        val name = raw.substring(slash + 1)
        if (name.isBlank()) return ArtistReadout.Unresolved(raw, "标签段是空的")
        return ArtistReadout.Resolved(DesktopArtistTarget(site, name), raw)
    }

    /**
     * `--comic=` 的判读（第八十批）：起窗后**直接进哪一本漫画的详情那一屏**。
     *
     * 值两截 `<漫画号>[/<题名>]`，斜杠按**第一个**分（同 [artist]：题名里允许有斜杠，切错方向会劈断一本的名字）。
     * 题名段可省（省了用号充题名，理由见 `DesktopComicTarget`）；`号` 段空就是打错了，
     * 说出来而不是当成"没给"。
     *
     * 与 [artist] 的**不同**处：这一枚不认站点（详情那一屏用 `DesktopComicEngineConfig.sourceKey` 那一颗
     * 当前源，漫画种子管不到"换源"），所以只读一个 `漫画号` 加上可选的 `题名`。
     *
     * 读不出时**不中止启动**（同 [artist]）：这一枚落空只是"没进详情屏"，漫画域照常停在列表，
     * 代价是半轮取证白跑；由下面那句 stdout 念出来（绝不静默）。
     */
    /**
     * 号那一段的百分号解码：`%2F` → `/`，而 `%25` **最后**才解。
     *
     * 为什么只有号这一段：切分吃的是**裸**斜杠，而 goda / nhentai 那一族的漫画号自己就以 `/manga/` 开头
     * ⇒ 号段不解码就恒被切成空串（批 106 包内那一趟念出的 `号=C:` 就是这一格）。题名、话号、章名那几段
     * 装的是显示文字与站方 id，本来不含斜杠，多解一道只会把"字面的 %2F"读成斜杠。
     * ⚠️ 次序就是语义：先解 `%25` 会把 `%252F` 连解两次，变成一道真斜杠。
     */
    private fun desktopForensicSegment(raw: String): String = raw.replace("%2F", "/").replace("%2f", "/").replace("%25", "%")

    fun comic(args: List<String>): DesktopComicTarget? {
        val given = args.firstOrNull { it == COMIC || it.startsWith("$COMIC=") } ?: return null
        val raw = given.removePrefix("$COMIC=").let { if (it == given) "" else it }
        if (raw.isBlank()) {
            println("D_取证参数 --comic 没带上那一串（本轮不进详情屏，参数打错了）")
            return null
        }
        val slash = raw.indexOf('/')
        val comicId = desktopForensicSegment(if (slash < 0) raw else raw.substring(0, slash))
        if (comicId.isBlank()) {
            println("D_取证参数 --comic=$raw 漫画号段是空的（要写成 <漫画号>[/<题名>]，例如 12345；号里要放斜杠就写 %2F）")
            return null
        }
        val title = if (slash < 0) comicId else raw.substring(slash + 1).ifBlank { comicId }
        println("D_取证参数 --comic 起窗后直接进详情屏（号=$comicId 题名=${if (slash < 0) "<未给，用号充题名>" else "已给"}）")
        // 种子这一趟没有入口卡：那枚封面留空是实话，而不是猜一张（详情若回了封面仍会填上）。
        return DesktopComicTarget(comicId, title, "")
    }

    /**
     * `--download=` 的判读（第九十六批）：起窗后把**哪一话整话写进本机**。
     *
     * 值是两截 `<漫画号>/<话号>`（话号用源站那一枚 id 而不是行号 —— 行号会漂，见批 94 那条欠账；
     * 拿行号当种子就会种一次对、下一次错）。读不出时**不中止启动**（同 [comic]）：代价只是半轮取证白跑，
     * 而那一格由 stdout 念出一句实话（绝不静默）。
     */
    fun download(args: List<String>): Pair<String, String>? {
        val given = args.firstOrNull { it == DOWNLOAD || it.startsWith("$DOWNLOAD=") } ?: return null
        val raw = given.removePrefix("$DOWNLOAD=").let { if (it == given) "" else it }
        val slash = raw.indexOf('/')
        if (raw.isBlank() || slash <= 0 || slash == raw.length - 1) {
            println("D_取证参数 $DOWNLOAD=$raw 不是两截（要写成 <漫画号>/<话号>，例如 1461316/1461318；号里要放斜杠就写 %2F；本轮不下载）")
            return null
        }
        val comicId = desktopForensicSegment(raw.substring(0, slash))
        val episodeId = raw.substring(slash + 1)
        println("D_取证参数 $DOWNLOAD 起窗后下载整话（号=$comicId 话=$episodeId 走的是长按那一条路）")
        return comicId to episodeId
    }

    /**
     * `--download=` 那枚种子**落空**时的实话（配着 [download] 用）。返回 `null` = 不必说。
     *
     * 只念一种情形：给了它而详情那一屏根本没开（没有配套的 `--comic`，或那一枚被别的种子接管走了）。
     * 下载这一格住在详情那一屏的章表里，那一屏没组合就没有任何消费点 —— 静默会把"我参数打错了"
     * 读成"整话下载坏了"（与 [detailTierNote] / [readPageNote] 同一条纪律）。
     * 种子自己读不出时 [download] 已经念过一句，这里不重复。
     */
    fun downloadNote(seed: Pair<String, String>?, detailOpens: Boolean): String? =
        if (seed != null && !detailOpens) {
            "D_取证参数 $DOWNLOAD=${seed.first}/${seed.second} 没有配套的 --comic ⇒ 本轮不开详情那一屏，那一话没种进去（不下载）"
        } else {
            null
        }

    /**
     * `--download-cancel-after=` 的判读（第一百批）：这一趟写下几页之后停手。交回 `null` = 没给或读不出。
     *
     * 读不出的三档各念各的一句（空值 / 不是数 / 非正数）—— 静默会把"我参数打错了"读成"取消坏了"。
     * ⚠️ 它**单独给也没有消费点**（要配 `--download=` 才有那一趟可停），所以 [cancelAfterNote] 负责念那一格。
     */
    fun downloadCancelAfter(args: List<String>): Int? {
        val given = args.firstOrNull { it == DOWNLOAD_CANCEL_AFTER || it.startsWith("$DOWNLOAD_CANCEL_AFTER=") } ?: return null
        val raw = given.removePrefix("$DOWNLOAD_CANCEL_AFTER=").let { if (it == given) "" else it }
        val n = raw.trim().toIntOrNull()
        if (n == null) {
            println("D_取证参数 $DOWNLOAD_CANCEL_AFTER=$raw 不是一个数（要写成 --download-cancel-after=3；本轮不停手）")
            return null
        }
        if (n <= 0) {
            println("D_取证参数 $DOWNLOAD_CANCEL_AFTER=$n 不是正数（0 与负数当没给，本轮不停手 —— 与「没给这枚参数」同形的那一档不收）")
            return null
        }
        println("D_取证参数 $DOWNLOAD_CANCEL_AFTER 这一趟写下 $n 页后停手（扳的是屏上那枚「取消」同一颗闸）")
        return n
    }

    /** 给了 `--download-cancel-after=` 而这一趟根本没开下载：念一句实话。返回 `null` = 不必说。 */
    fun downloadCancelAfterNote(after: Int?, downloadSeed: Pair<String, String>?, detailOpens: Boolean): String? =
        if (after != null && (downloadSeed == null || !detailOpens)) {
            "D_取证参数 $DOWNLOAD_CANCEL_AFTER=$after 没有配套的 $DOWNLOAD（或详情那一屏没开）⇒ 本轮没有可停的那一趟"
        } else {
            null
        }

    /**
     * `--delete=` 的判读（第九十八批）：起窗后把**哪一话从本机删掉**。
     *
     * 值与 [download] **同一把写法** `<漫画号>/<话号>` —— 两枚种子指的是同一格盘上的东西，
     * 一枚收话号而另一枚收行号就是本仓那一族"复合键两端不同轨"的现形（屏上那行永远命中不了）。
     * 读不出时不中止启动，只念一句实话。
     *
     * ⚠️ 它走的是**与那一行右边那颗把手第二次按下同一条代码路**（同一颗 `desktopComicDeleteChapter`），
     * 不是另开一条"只为取证"的删除路 —— 否则量到的是种子那条路而用户那一条可以仍是坏的（本仓那一族第六次）。
     */
    fun delete(args: List<String>): Pair<String, String>? {
        val given = args.firstOrNull { it == DELETE || it.startsWith("$DELETE=") } ?: return null
        val raw = given.removePrefix("$DELETE=").let { if (it == given) "" else it }
        val slash = raw.indexOf('/')
        if (raw.isBlank() || slash <= 0 || slash == raw.length - 1) {
            println("D_取证参数 $DELETE=$raw 不是两截（要写成 <漫画号>/<话号>，例如 1461316/1461318；号里要放斜杠就写 %2F；本轮不删）")
            return null
        }
        val comicId = desktopForensicSegment(raw.substring(0, slash))
        val chapterId = raw.substring(slash + 1)
        println("D_取证参数 $DELETE 起窗后删掉那一话（号=$comicId 话=$chapterId 走的是屏上那颗把手第二次按下那一条路）")
        return comicId to chapterId
    }

    /**
     * `--delete=` 那枚种子**落空**时的实话（配着 [delete] 用）。返回 `null` = 不必说。
     *
     * 只念一种情形：给了它而**起始域根本不是漫画** —— 那一屏住在漫画域里，域都不对就没有任何消费点。
     * （落在漫画域而没落在那一行时不必这里重复：`--start-section=` 认不认得那一句 [sectionNote] 已经念过了。）
     * 静默会把"我参数打错了"读成"删除坏了"（与 [downloadNote] 同一条纪律）。
     */
    fun deleteNote(seed: Pair<String, String>?, comicDomainOpens: Boolean): String? =
        if (seed != null && !comicDomainOpens) {
            "D_取证参数 $DELETE=${seed.first}/${seed.second} 起始域不是漫画 ⇒ 那一屏根本没开，本轮不动盘（不删）"
        } else {
            null
        }

    /**
     * `--repair=<漫画号>/<话号>` 的判读（第一百一十一批）：起窗后把那一行**排回下载队列**。
     *
     * 与 [delete] 同一族、同一条路（屏上那枚把手办的就是这一步，种子只是替鼠标按一次），
     * 所以两枚号之间那一截斜杠仍要写 `%2F`（批 107 那颗口径），且号段先过 [desktopForensicSegment] 解码。
     */
    fun repair(args: List<String>): Pair<String, String>? {
        val given = args.firstOrNull { it == REPAIR || it.startsWith("$REPAIR=") } ?: return null
        val raw = given.removePrefix("$REPAIR=").let { if (it == given) "" else it }
        val slash = raw.indexOf('/')
        if (raw.isBlank() || slash <= 0 || slash == raw.length - 1) {
            println("D_取证参数 $REPAIR=$raw 不是两截（要写成 <漫画号>/<话号>，例如 1461316/1461318；号里要放斜杠就写 %2F；本轮不补齐）")
            return null
        }
        val comicId = desktopForensicSegment(raw.substring(0, slash))
        val chapterId = raw.substring(slash + 1)
        println("D_取证参数 $REPAIR 起窗后把那一话排回队列（号=$comicId 话=$chapterId 走的是屏上那颗「补齐」把手同一条路）")
        return comicId to chapterId
    }

    /** `--repair=` 落空那一档的实话（与 [deleteNote] 同一判据：域都不对就没有消费点，不许静默）。 */
    fun repairNote(seed: Pair<String, String>?, comicDomainOpens: Boolean): String? =
        if (seed != null && !comicDomainOpens) {
            "D_取证参数 $REPAIR=${seed.first}/${seed.second} 起始域不是漫画 ⇒ 那一屏根本没开，本轮不排队（不补齐）"
        } else {
            null
        }

    /**
     * `--disable=<源键>` 的判读（批 83.1）：起窗后把那一颗源当成本台**停用着**，让"停用真折进装载"
     * 那一格第一次在帧上看得见（源管理那一屏的枚数与灰行 + 漫画域真装的那一颗同时进画）。
     *
     * 这里只管"读没读到一串字"：认不认得那串要拿清单比，而清单住在消费点（列资产目录那一问在
     * `rememberDesktopSources`），所以认不认得那句由 [disableNote] 在那里念。
     *
     * 只取**第一颗**：同一个名字给两遍时并两颗，会让屏上"停用了 N 颗"念出用户没打的数。
     * 空值与只给名字不带值都算**没给**并出声（把空串并进去等于凭空多一颗不存在的停用源）。
     */
    fun disable(args: List<String>): String? {
        val given = args.firstOrNull { it == DISABLE || it.startsWith("$DISABLE=") } ?: return null
        val raw = given.removePrefix("$DISABLE=").let { if (it == given) "" else it }
        if (raw.isBlank()) {
            println("D_取证参数 --disable 没带上那一串（本轮不折任何停用，参数打错了）")
            return null
        }
        return raw
    }

    /**
     * 那一句读数，三档两两不同（同 [readPageNote] 那颗纪律：落空也要说得出一句实话）。
     *
     * - 没给 ⇒ **null，不打**（打了就是把上一轮的读数当本轮的）。
     * - 清单里有 ⇒ 说清"并进的是本次装载"，因为这份状态**不写盘**（盘上那份是用户的，动它得用户在屏上点）。
     * - 清单里没有 ⇒ 原样点名那串，并说"一颗也没多停用"。静默吞掉取证参数是本仓那一族
     *   "量具看起来在工作而其实什么都没做"，绝不做。
     */
    fun disableNote(seed: String?, keys: List<String>): String? = when {
        seed == null -> null
        seed in keys -> "D_取证参数 --disable=$seed 清单里有这一颗 ⇒ 已并进本次装载（只这一趟；要动盘上那份得在源管理那一屏点一下）"
        else -> "D_取证参数 --disable=$seed 清单里没有这一颗 ⇒ 本次装载一颗也没多停用"
    }

    /**
     * `--cookie=<host>:<名>=<值>` 的判读（批 88·下半）：**同一枚参数给多遍**是这一族的常态
     * （ehentai 那一份源要四枚字段），与 [disable] 只取第一颗相反 —— 那里"给两遍"是两棵源，
     * 会把屏上"停用了 N 颗"念成用户没打的数；这里"给多遍"本来就是一棵源的几枚凭据。
     *
     * 这里只管"读到几串"，**不解析**：解析要懂罐那一形（按请求 host 分桶、脏域摘掉），
     * 而那一形住在 `:engine-probe` 的 `DesktopJsHost`；桌面重述一遍就是两处各认一种写法。
     *
     * 空值那一串算**没给**并出声（并一枚空凭据等于凭空给那一站添一枚"看着填了而值是空"的字段）。
     */
    fun cookies(args: List<String>): List<String> {
        val given = args.filter { it == COOKIE || it.startsWith("$COOKIE=") }
        if (given.isEmpty()) return emptyList()
        val kept = given.mapNotNull { raw ->
            // 只给名字不带值与空值都算"给了但读不出"（同 [disable]：并一枚空凭据等于凭空给那一站
            // 添一枚"看着填了而值是空"的字段，而把它当成没给就等于把打错的参数静默吞掉）。
            val value = raw.removePrefix("$COOKIE=").let { if (it == raw) "" else it }
            if (value.isBlank()) {
                println("D_取证参数 --cookie 那一串是空的（这一枚不并进去）")
                null
            } else value
        }
        println("D_取证参数 --cookie 收到=${given.size} 可用=${kept.size}")
        return kept
    }

    /**
     * `--read=` 的判读（第八十一批）：起窗后**直接进哪一话的阅读那一屏**。
     *
     * 值一到三截 `<漫画号>[/<话号>[/<章名>]]`，按**第一个**斜杠分（同 [comic]：章名里允许有斜杠）。
     * - 只给号 ⇒ 当作**占位章**（整本就一章那一类）：话号空、`placeholder=true`。
     *   这一档不能省：拿空串当话号去问 `loadEp` 会取到错的东西，而"取页时要把话号还原成 null"
     *   这件事只有 placeholder 那一枚知道。
     * - 给了话号而未给章名 ⇒ 章名留空串；那一格在屏上念的是**话号**（不是"整部…"那句 —— 那一句
     *   只有真占位章才许念，图证抓到过一次把有 15 话的本子说成"没有分话"）。
     *
     * 读不出时**不中止启动**（同 [comic]）：代价只是半轮取证白跑，由 stdout 念出来（绝不静默）。
     *
     * [startPage] 由调用方从 [readPage] 递进来（必填、不给默认值）：那一屏从第几页起是**这两枚参数
     * 合起来**决定的，藏在 `read()` 里自己去摸 args 就变成"两个入口各读一遍同一枚参数"。
     */
    fun read(args: List<String>, startPage: Int?): DesktopComicReading? {
        val given = args.firstOrNull { it == READ || it.startsWith("$READ=") } ?: return null
        val raw = given.removePrefix("$READ=").let { if (it == given) "" else it }
        if (raw.isBlank()) {
            println("D_取证参数 --read 没带上那一串（本轮不进阅读屏，参数打错了）")
            return null
        }
        val parts = raw.split('/')
        val comicId = desktopForensicSegment(parts[0])
        if (comicId.isBlank()) {
            println("D_取证参数 --read=$raw 漫画号段是空的（要写成 <漫画号>[/<话号>[/<章名>]]，例如 12345/g；号里要放斜杠就写 %2F）")
            return null
        }
        val episodeId = parts.getOrNull(1)?.trim().orEmpty()
        val episodeName = parts.drop(2).joinToString("/").trim()
        println(
            "D_取证参数 --read 起窗后直接进阅读屏（号=$comicId 话=${if (episodeId.isEmpty()) "<未给，按占位章>" else episodeId}" +
                " 章名=${if (episodeName.isEmpty()) "<未给>" else "已给"}）",
        )
        return DesktopComicReading(
            comicId = comicId,
            title = comicId,
            episodeId = episodeId,
            episodeName = episodeName,
            placeholder = episodeId.isEmpty(),
            // 种子只点名"哪一话"，不知道它在章表里第几行 ⇒ 这里只能是 0。
            // 这一档只影响无人值守取证那一趟（跑在隔离的数据目录里），真人那条路从详情那一屏进来，行号是真的。
            episodeIndex = 0,
            // 页号没给 / 读不出时就是第一页：0 是一个**真页号**（从第一页起），不是"起点未知"。
            startPage = startPage ?: 0,
            // 种子直接落在阅读那一屏，不经过详情那一行 ⇒ 手里没有封面地址。空串是实话：
            // 那一趟只跑在隔离的数据目录里，而猜一张会把没量过的东西写进表。
            coverUrl = "",
        )
    }

    /**
     * `--read-page=` 的判读（第八十二批半）：进阅读那一屏时先落在第几页（**0 起的索引**）。
     *
     * 三档与 [viewerZoom] 同一族：没给 / 认得出 / 给的不是个数。最后一档**不猜一个页号**——
     * 猜了就成了"我给你滚到了一个你没说过的地方"，而那一帧会被当成"种子生效了"。
     * 负数单独念一句（与"不是个数"分开）：那一串看着像数，修法却是"索引从 0 起"，
     * 合成一句就说不清该改哪一边。
     *
     * ⚠️ 这一枚**只管起点**，量的是"页号流进写的那一笔"这一格；真人在那一屏里往下翻走的还是
     * 滚动那一条路（批 82 的写口），两者不互相冒充。
     */
    fun readPage(args: List<String>): Int? {
        val given = args.firstOrNull { it == READ_PAGE || it.startsWith("$READ_PAGE=") } ?: return null
        val raw = given.removePrefix("$READ_PAGE=").takeIf { it != given }
        if (raw == null) {
            println("D_取证参数 $READ_PAGE 没带上那一串（本轮不种起点页，从第一页起）")
            return null
        }
        val value = raw.trim().toIntOrNull()
        if (value == null) {
            println("D_取证参数 $READ_PAGE=$raw 不是个数，本轮不种起点页（不替你猜一个页号）")
            return null
        }
        if (value < 0) {
            println("D_取证参数 $READ_PAGE=$value 是负数：页号从 0 起，本轮不种起点页")
            return null
        }
        println("D_取证参数 $READ_PAGE 起窗后先落到第 ${value + 1} 页（索引=$value）")
        return value
    }

    /**
     * `--read-mode=<键或枚举名>` → 顶上哪一档（第八十七批）。认不出**不猜**：
     * 那颗归一件 `DesktopReaderMode.fromKey` 会把认不出的串折回纵向那一档，那是"盘上读档"的规矩，
     * 取证参数借它就会导致「递了个错字而屏上静静回到默认档」—— 与本批要防的那一类一模一样。
     */
    fun readMode(args: List<String>): DesktopReaderMode? {
        val given = args.firstOrNull { it.startsWith("$READ_MODE=") } ?: return null
        val raw = given.removePrefix("$READ_MODE=").trim()
        if (raw.isEmpty()) {
            println("D_取证参数 $READ_MODE 没带上那一串（本轮不顶档，用盘上那一档）")
            return null
        }
        val hit = DesktopReaderMode.entries.firstOrNull { it.key.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
        if (hit == null) {
            println("D_取证参数 $READ_MODE=$raw 认不出是哪一档（认键与枚举名，逐字照对岸那张表）：本轮不顶档")
            return null
        }
        println("D_取证参数 $READ_MODE=$raw 认成 档=${hit.label}")
        return hit
    }

    /**
     * `--detail-tier=<屏名或枚举名>` → 浮层与大图那一屏起在哪一档（批 92）。
     *
     * 认**屏名**（`原图`）而不只认内部名：递参数的人看的是屏上那排分段控件摆的那三串字，
     * 而那一套词现在只有一颗生产者（`desktopDetailTierLabel`），读数与屏上不会漂。
     *
     * ⚠️ 认不出**不猜**（与 [readMode] 同一条纪律）：悄悄落回默认档会把"我参数打错了"读成
     * "这一档今天兑现不了"，而这两句的下一步动作完全不同（改参数 / 改判据）。
     * ⚠️ 这里只回答"参数要的是哪一档"，**不回答"这一张摆不摆得出这一档"** —— 后者住在
     * `desktopDetailTiers` 那张表里（视频与站方没给 `file_url` 那两条撤档规则），由落点那一行念。
     */
    fun detailTier(args: List<String>): DesktopDetailTier? {
        val given = args.firstOrNull { it == DETAIL_TIER || it.startsWith("$DETAIL_TIER=") } ?: return null
        val raw = given.removePrefix("$DETAIL_TIER=").takeIf { it != given }
        if (raw == null || raw.isBlank()) {
            println("D_取证参数 $DETAIL_TIER 没带上那一串（本轮不改档位，用设置里那一档）")
            return null
        }
        val value = raw.trim()
        val hit = DesktopDetailTier.entries.firstOrNull {
            it.name.equals(value, ignoreCase = true) || desktopDetailTierLabel(it) == value
        }
        if (hit == null) {
            println("D_取证参数 $DETAIL_TIER=$value 认不出是哪一档（认枚举名与屏名 预览/大图/原图），不替你猜一档")
            return null
        }
        println("D_取证参数 $DETAIL_TIER=$value 认成 档=${desktopDetailTierLabel(hit)}（${hit.name}）")
        return hit
    }

    /**
     * 档位那颗种子**落空**时的实话（配着 [detailTier] 用）。返回 `null` = 不必说。
     *
     * 只念一种情形：给了 `--detail-tier` 而**浮层根本没开**（没给 `--detail` 那一族，且没有
     * 任何一枚会顺带接管浮层的种子）⇒ 那一档哪儿也没去，而 stdout 上刚念过一句"认成 档=原图"。
     * 不补这一句就是假开关的口径（与 [readPageNote] 同一条纪律）。
     */
    fun detailTierNote(seed: DesktopDetailTier?, overlayOpens: Boolean): String? =
        if (seed != null && !overlayOpens) {
            "D_取证参数 $DETAIL_TIER=${desktopDetailTierLabel(seed)} 没有配套的 --detail ⇒ 本轮不开浮层，那一档没种进去"
        } else {
            null
        }

    /**
     * 页号那颗种子**落空**时的实话（配着 [read] 用）。返回 `null` = 不必说。
     *
     * 只念一种情形：给了 `--read-page` 而**那一屏根本没进**（没有 `--read`，或它自己读不出）。
     * 静默就是"我给了它却没做任何事"，那是假开关的口径（与 [queryNote] / [reverseFileNote] 同一条纪律）。
     * 页号自己读不出时 [readPage] 已经念过一句，这里不重复；两枚都合上时也没什么可念。
     */
    fun readPageNote(startPage: Int?, seed: DesktopComicReading?): String? =
        if (startPage != null && seed == null) {
            "D_取证参数 $READ_PAGE=$startPage 没有配套的 --read ⇒ 本轮不进阅读屏，那一页没种进去"
        } else {
            null
        }

    /** 可选的站点写法（读不出那一句与正面读数都要点名它，别让打参数的人去翻枚举）。 */
    private fun siteKeys(): String = GallerySite.entries.joinToString(" ") { it.routeKey }    /**
     * 单人页那颗种子**没落到那一屏**时的实话。返回 `null` = 不必说。
     *
     * 两档分得很开（合成一句就说不清该改哪一边）：
     * - 读不出：本轮不进单人页，屏上就是起始那一域 —— 那张图会顶着单人页的文件名，所以必须出声。
     * - 读得出、而 `--start-domain=` 指的是别处：这是一次**覆盖**不是故障，
     *   单人页在那一域的第二层里，域只能跟着种子走。不吭声就是"两枚参数打架时悄悄赢了一边"。
     *
     * [flagDomain] 只在用户**真给过** `--start-domain=` 时才是非空（`NotGiven` 交 `null`）：
     * 没给域名时种子换域是本分，没有什么可报告的。
     */
    fun artistNote(readout: ArtistReadout, flagDomain: DesktopDomain?): String? = when (readout) {
        ArtistReadout.NotGiven -> null

        is ArtistReadout.Unresolved ->
            "D_取证参数 --artist=${readout.raw.ifBlank { "<没带值>" }} 读不出：${readout.why}" +
                " —— 本轮没进单人页，屏上仍是起始那一域（可选站点：" + siteKeys() + "）"

        is ArtistReadout.Resolved ->
            if (flagDomain == null || flagDomain == DesktopDomain.ARTISTS) {
                null
            } else {
                "D_取证参数 --artist=${readout.raw} 要落在「${DesktopDomain.ARTISTS.title}」那一域的单人页，" +
                    "而 --start-domain 指的是「${flagDomain.title}」 ⇒ 本轮域名以 --artist 为准" +
                    "（单人页在那一域的第二层，域留在「${flagDomain.title}」时那一屏不在组合里）"
            }
    }

    /**
     * `--ranking=` 的判读：起窗后**排行榜停在哪一档、哪一期**（第三十批）。
     *
     * 值两截 `<档>[/<期>]`，期段可省（省了就是本期）。两种长度各有靶子：只给档就能拍到
     * 「上一期 / 下一期 / 回到本期」那一排本身，而带上期段才拍得到"这是回看的历史期"那一句
     * 与胶囊上那枚期次标记 —— 它们都只在锚点非空时出形状。
     *
     * 档段三种写法都认（枚举名 / 下拉里那一整行的题 / 胶囊上那一小截），与 `--start-domain=`
     * 同一口径；⚠️ 走命令行时**首选枚举名**，中文值要穿过 gradle 属性与 Windows 控制台那两层编码。
     * 斜杠按**第一个**分（同 [artist]）：月档的期段是 `2024-03`、年档是 `2019`，都不含斜杠，
     * 而多出来的一段必须报读不出而不是"当作没给"。
     *
     * 判读要吃 [todayUtc] 而且**必填、不给默认值**：地板那一档与"那一期还没到"那一档都相对本期判，
     * 带默认值就会静默按机器时钟判，用例也换不出两种时钟（手册 §0 那一族）。
     * 读不出时**不中止启动**（同 [artist]）：这一枚落空只是"没拨到那一格"，屏上仍是用户点名的那一屏，
     * 代价是半轮取证白跑；由 [rankingNote] 念出来。
     */
    fun ranking(args: List<String>, todayUtc: LocalDate): RankingReadout {
        val given = args.firstOrNull { it == RANKING || it.startsWith("$RANKING=") } ?: return RankingReadout.NotGiven
        val raw = given.removePrefix("$RANKING=").let { if (it == given) "" else it }
        val slash = raw.indexOf('/')
        val rankPart = if (slash < 0) raw else raw.substring(0, slash)
        val periodPart = if (slash < 0) null else raw.substring(slash + 1)
        val ranking = GalleryRanking.entries.firstOrNull {
            it.name.equals(rankPart, ignoreCase = true) || it.label == rankPart || it.shortLabel == rankPart
        } ?: return RankingReadout.Unresolved(
            raw,
            "档段认不出（档段=${rankPart.ifBlank { "<没给档段>" }}，可选：" + rankingKeys() + "）",
        )
        if (periodPart == null) return RankingReadout.Resolved(DesktopRankingSeed(ranking, null), raw)
        if (!GalleryRankings.supportsHistory(ranking)) {
            return RankingReadout.Unresolved(
                raw,
                "「${ranking.label}」这一档不给回看历史期，只有「${GalleryRanking.MONTH.label}」与" +
                    "「${GalleryRanking.YEAR.label}」两档给（期段=$periodPart）",
            )
        }
        val anchor = parsePeriod(ranking, periodPart) ?: return RankingReadout.Unresolved(
            raw,
            "期段「$periodPart」的写法不对（月档写 2024-03、年档写 2019）",
        )
        if (anchor.isBefore(GalleryRankings.EARLIEST_PERIOD)) {
            return RankingReadout.Unresolved(
                raw,
                "那一早于地板 ${GalleryRankings.EARLIEST_PERIOD}（站方存档再往前回空，拍出来是一张空墙）",
            )
        }
        if (anchor.isAfter(todayUtc)) {
            return RankingReadout.Unresolved(
                raw,
                "那一还没到（本期是 $todayUtc）—— 窗口终点恒被今天钉住，种进去只会拍到一处被夹回本期的读数",
            )
        }
        return RankingReadout.Resolved(DesktopRankingSeed(ranking, anchor), raw)
    }

    /**
     * 期段 → 那一期的起点。认不出格式交回 null（由调用方念"哪一段不对"）。
     *
     * 落点归一到月初 / 年初，与 `desktopRankingStep` 同一口径：区间只看锚点落在哪一期，
     * 而屏上那枚期次标记跟着锚点的年月走，不归一就会出现「2024-03 那一期的锚点是 03-12」这种读法。
     */
    private fun parsePeriod(ranking: GalleryRanking, period: String): LocalDate? {
        val trimmed = period.trim()
        return when (ranking) {
            GalleryRanking.MONTH -> Regex("""(\d{4})-(\d{1,2})""").matchEntire(trimmed)?.let { m ->
                val month = m.groupValues[2].toInt()
                if (month in 1..12) LocalDate.of(m.groupValues[1].toInt(), month, 1) else null
            }

            else -> Regex("""(\d{4})""").matchEntire(trimmed)?.let { LocalDate.of(it.groupValues[1].toInt(), 1, 1) }
        }
    }

    /** 可选的档名写法（读不出那一句与正面读数都要点名它，别让打参数的人去翻枚举）。 */
    private fun rankingKeys(): String = GalleryRanking.entries.joinToString(" ") { it.name }

    /**
     * 排行榜那颗种子**没拨到那一格**时的实话。返回 `null` = 不必说。
     *
     * 四档分得很开（合成一句就说不清该改哪一边）：
     * - 读不出：本轮那一屏停在默认档，那张图会顶着目标档的文件名；
     * - `--artist` 同给：起始域已被那一枚定成「画师」，这一枚**没种进去**（要点名是哪一枚落空）；
     * - 域被接管：那一屏在「图库」域里，而 `--start-domain=` 指的是别处；
     * - 行被接管：同理，`--start-section=` 给的那一枚（认得出就交键，认不出就原样）不是「排行榜」。
     * 后两档是**覆盖**而不是故障，不吭声就是"参数互相打架时悄悄赢了一边"那一族。
     */
    fun rankingNote(
        readout: RankingReadout,
        flagDomain: DesktopDomain?,
        flagSection: String?,
        artistWins: Boolean,
    ): String? = when (readout) {
        RankingReadout.NotGiven -> null

        is RankingReadout.Unresolved ->
            "D_取证参数 --ranking=${readout.raw.ifBlank { "<没带值>" }} 读不出：${readout.why}" +
                " —— 本轮没进排行榜那一屏，屏上仍是起始那一域（可选档：" + rankingKeys() + "）"

        is RankingReadout.Resolved -> {
            val clauses = buildList {
                if (artistWins) {
                    add(
                        "--artist 与 --ranking=${readout.raw} 同给，起始域已被 --artist 定成" +
                            "「${DesktopDomain.ARTISTS.title}」⇒ 本轮 --ranking 的档与期次没种进去",
                    )
                }
                if (flagDomain != null && flagDomain != DesktopDomain.GALLERY) {
                    add(
                        "--ranking=${readout.raw} 要落在「${DesktopDomain.GALLERY.title}」域的排行榜那一行，" +
                            "而 --start-domain 指的是「${flagDomain.title}」⇒ 本轮域以 --ranking 为准",
                    )
                }
                if (!artistWins && flagSection != null && flagSection != DesktopGalleryHomeSectionKey.RANKING) {
                    add(
                        "--ranking=${readout.raw} 要落在「排行榜」那一行，而 --start-section 给的是" +
                            "$flagSection ⇒ 本轮行以 --ranking 为准（那一行的键=${DesktopGalleryHomeSectionKey.RANKING}）",
                    )
                }
            }
            clauses.joinToString("；").takeIf { it.isNotEmpty() }?.let { "D_取证参数 $it" }
        }
    }

    /**
     * 取证参数**落在哪一域的哪一行**（域与行是一件事的两半，所以一起交回）。
     */
    internal data class ForensicLanding(val domain: DesktopDomain, val section: String?)

    /**
     * 排行榜那颗种子认得出时，起始域与起始行**跟着走**（第三十批）。
     *
     * 为什么把这一步做成纯函数而不写在 `main()` 里：这一格要钉的是"判式里带着种子认得出那一半"，
     * 而 `main()` 要建窗口、要数据目录，测试碰不到 —— 留在里面就只剩文本锚点，
     * 而文本锚点**看不见死分支**（手册 §0 坑 ㉔）：把 `rankingWins` 改成恒 false，屏上落回日榜，
     * 而"main() 里出现过 DesktopDomain.GALLERY"那一条照样绿。
     *
     * 两枚种子同给时赢的是 `--artist`（起始域已被它定成「画师」，那一屏里没有排行榜这一行），
     * 此时这一枚**一字不改用户的落点** —— 落空由 [rankingNote] 念，而不是偷偷改屏。
     */
    fun rankingLanding(
        readout: RankingReadout,
        flagDomain: DesktopDomain,
        flagSection: String?,
        artistWins: Boolean,
    ): ForensicLanding = when {
        readout !is RankingReadout.Resolved || artistWins -> ForensicLanding(flagDomain, flagSection)
        else -> ForensicLanding(DesktopDomain.GALLERY, DesktopGalleryHomeSectionKey.RANKING)
    }

    /**
     * 漫画详情那颗种子**接管起始域**（第八十批）：那一屏住在「漫画」域的第二层里，
     * 域没跟过去就是"状态置上了而那一屏根本没组合"—— 本批第一次起窗拍到的正是这一格：
     * `D_落点 … 详情=开` 而落点停在「图库·每日热门」，屏上什么都没有，读数是句谎话。
     *
     * 次序排在 `rankingLanding` **之后**且让着那两枚：`--artist` 与 `--ranking` 都会把域与行
     * 一起定死在同一屏里，而这一枚只改**域**（漫画域第一行就是「探索」，行不用种）。
     * 被抢走时**一字不改**用户的落点，那一格由 [comicNote] 念出来。
     */
    fun comicLanding(
        seed: DesktopComicTarget?,
        landing: ForensicLanding,
        takenOverByOther: Boolean,
    ): ForensicLanding = when {
        seed == null || takenOverByOther -> landing
        // 用户已经点名漫画域的某一行时**只补域、不动行**（批 82.7 的量具那一半）：要把
        // 「行=阅读历史 + 详情=开」同时种进去，才拍得到"载荷设上了而那一屏根本没换"那一格。
        // 旧写法一律把行交回 null（=第一行「探索」），于是唯一能拍出这一格的路径被量具自己堵死。
        landing.domain == DesktopDomain.COMIC -> landing
        // 从别的域接管时才交回 null：带着上一域的键去种漫画域那一行对不上任何东西
        //（根上 `?: rows.first()` 落回第一行，那一格由 `sectionNote` 照实念，不静默）。
        else -> ForensicLanding(DesktopDomain.COMIC, null)
    }

    /** 接管那一句里"行留在哪一格"那一截：行名从行表里**现取**（改名或撤行时这句跟着变，不替已经不存在的行作伪证）。 */
    private fun comicLandingRowNote(section: String?): String {
        val rows = desktopDomainRows(DesktopDomain.COMIC)
        val hit = rows.firstOrNull { it.key == section }
        return if (hit == null) "，行交回第一行「${rows.first().title}」" else "，行留在「${hit.title}」"
    }

    /**
     * 漫画那颗种子**没落到那一屏**时的实话。返回 `null` = 不必说。
     *
     * 三档各一句，因为修法各不一样：没给（什么都不必说）、被抢走（参数打架，用户要知道赢的是谁）、
     * 真接管（正面读数，念的是**结果**而不是请求）。
     */
    fun comicNote(seed: DesktopComicTarget?, landing: ForensicLanding, takenOverByOther: Boolean): String? = when {
        seed == null -> null
        takenOverByOther -> "--comic=${seed.comicId} 没接管落点（与 --artist / --ranking 同给，赢的是那一枚）" +
            "，而详情那一屏只在「${DesktopDomain.COMIC.title}」域里 ⇒ 本轮不进详情屏"
        landing.domain != DesktopDomain.COMIC -> "--comic=${seed.comicId} 认得出，而起始域没跟过去" +
            "（落在「${landing.domain.title}」）⇒ 本轮不进详情屏"
        else -> "D_取证参数 --comic=${seed.comicId} 起始域跟着落到「${DesktopDomain.COMIC.title}」" +
            comicLandingRowNote(landing.section)
    }

    /**
     * 阅读那颗种子**接管起始域**（第八十一批）：形状与 [comicLanding] 同一条 —— 那一屏住在「漫画」域
     * 的第二层里，域没跟过去就是"状态置上了而那一屏根本没组合"（批 80 第一次起窗拍到的正是这一格）。
     *
     * 它排在 [comicLanding] **之前**：`--read` 已经把"哪一话"都定死了，同给两枚时阅读那一屏就是终点，
     * 而详情那一屏在底下留着（返回即回得到）。两枚都没抢到时才让给用户的落点。
     */
    fun readLanding(
        seed: DesktopComicReading?,
        landing: ForensicLanding,
        takenOverByOther: Boolean,
    ): ForensicLanding =
        if (seed == null || takenOverByOther) landing else ForensicLanding(DesktopDomain.COMIC, null)

    /**
     * 阅读那颗种子**没落到那一屏**时的实话。返回 `null` = 不必说。三档各一句的理由同 [comicNote]。
     */
    fun readNote(seed: DesktopComicReading?, landing: ForensicLanding, takenOverByOther: Boolean): String? = when {
        seed == null -> null
        takenOverByOther -> "--read=${seed.comicId}/${seed.episodeId} 没接管落点（与 --artist / --ranking 同给，赢的是那一枚）" +
            "，而阅读那一屏只在「${DesktopDomain.COMIC.title}」域里 ⇒ 本轮不进阅读屏"
        landing.domain != DesktopDomain.COMIC -> "--read=${seed.comicId}/${seed.episodeId} 认得出，而起始域没跟过去" +
            "（落在「${landing.domain.title}」）⇒ 本轮不进阅读屏"
        else -> "D_取证参数 --read=${seed.comicId}/${seed.episodeId} 起始域跟着落到「${DesktopDomain.COMIC.title}」"
    }

    /**
     * 详情浮层这一轮**开不开**：`--detail` 开，或者 `--reverse` / `--copy` / `--save` 开 ——
     * 那几枚要按的按钮都在浮层里，浮层不在就没有按钮可按（第三十六批 / 第五十四批 / 第八十五批）。
     *
     * 四枚都是 `Boolean?`（`null` = 那个参数没给），因为"没给"与"给了而值为关"在这里**不等价**：
     * `--reverse` 而 `--detail=0` 时是这一枚替用户多开了一扇东西，那一句必须念出来（见 [reverseNote]），
     * 而塌成一个 `Boolean` 就会把"用户明确关掉了浮层"读成"他没提浮层"。
     *
     * ⚠️ 为什么四枚挤在一颗函数里而不是各自 `||` 一下：「哪枚种子会替用户开窗」是**同一件事**，
     * 分两处判就要在参数同给时漂出两种答案（`D_落点` 那一格读的是这一颗的结果）。
     */
    fun detailPanel(detail: Boolean?, reverse: Boolean?, copy: Boolean?, save: Boolean?): Boolean =
        reverse == true || copy == true || save == true || detail == true

    /**
     * 反搜那颗种子**替用户多开了一扇东西**时的那句实话。返回 `null` = 不必说。
     *
     * 只在 `--reverse` 真开而浮层本来不会开时念：不吭声就是"参数互相打架时悄悄赢了一边"那一族
     * （与 [artistNote] / [rankingNote] 同一条纪律）。值为关那一档 [state] 已经念过一句，不重复。
     */
    fun reverseNote(detail: Boolean?, reverse: Boolean?): String? =
        if (reverse == true && detail != true) {
            "D_取证参数 --reverse 要按的那一枚按钮在详情浮层里 ⇒ 本轮把浮层一起摆出来" +
                "（--detail ${if (detail == null) "没给" else "值为关"}）"
        } else {
            null
        }

    /**
     * 浮层里那几枚按钮的种子（`--copy` 批 54 / `--save` 批 85）**替用户多开了一扇东西**时的那句实话。
     * 返回 `null` = 不必说（没给、给了而值为关、或 `--detail` 本来就开着）。
     *
     * 这两枚**合在一颗**：它们要按的是同一颗浮层里相邻的两枚按钮，"为谁多开了窗"是同一件事，
     * 分两颗判就会在两枚同给时念出两句各自只提一枚的话，而屏上其实只多开了一扇。
     * ⚠️ `--reverse` 那一句今天仍住在 [reverseNote] 里（它的话术带着批 35 那格专属的一层），
     * 把它并进这一颗属于**另一批改判**，本批不动那句读数的形状 —— 这笔账记在
     * `docs/rounds/desktop-image-save-2026-10-10.md`。
     */
    fun overlaySeedNote(detail: Boolean?, copy: Boolean?, save: Boolean?): String? {
        if (detail == true) return null
        val seeds = listOfNotNull(
            "--copy（复制图片）".takeIf { copy == true },
            "--save（存到本机）".takeIf { save == true },
        )
        if (seeds.isEmpty()) return null
        return "D_取证参数 ${seeds.joinToString("、")} 要按的按钮在详情浮层里 ⇒ 本轮把浮层一起摆出来" +
            "（--detail ${if (detail == null) "没给" else "值为关"}）"
    }

    /**
     * 两参同给的实话。返回 null 表示没同给（不必说）。
     * favcheck 为开时它会先读回再退出进程，**autofav 那一步根本不会执行** —— 这句就是原来缺的。
     */
    fun bothGivenNote(autofav: Boolean?, favcheck: Boolean?): String? {
        if (autofav == null || favcheck == null) return null
        return if (favcheck) {
            "D_取证参数 --autofav 与 --favcheck 同给，本轮只跑 favcheck（它读回后就退出进程，autofav 的写动作不执行）"
        } else {
            "D_取证参数 --autofav 与 --favcheck 同给，favcheck 的值为关 ⇒ 不提前退出，本轮跑 autofav"
        }
    }
}
