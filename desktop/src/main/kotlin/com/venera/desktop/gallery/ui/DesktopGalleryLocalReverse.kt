package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryReverseSearch
import com.venera.desktop.desktopScrubSecrets
import com.venera.desktop.desktopFailureFrame
import com.venera.desktop.desktopFailureType
import com.venera.desktop.desktopOneLineMessage
import kotlinx.coroutines.CancellationException
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.io.File

/**
 * 桌面第七十七批 批 77 · 下半 · 「用本机的一张图找同一张」的**判定面**。
 *
 * ## 这一族从前缺的是哪一层
 *
 * 批 35 把 `searchByFile` 真接了（转交共享那颗 `SauceNaoClient`），而它自己在类注释里写得很清楚：
 * 差的是"用户从本机挑一张图"那一层 ⇒ 契约上有、界面上没有。本批补的就是那一层，
 * 而这一颗文件只管**判定**（发不发、发的是什么、回来算答案还是算故障），
 * 界面那一段住在 `DesktopGalleryLocalReverseSection.kt`。
 *
 * ## 落点复用批 35 那颗 [DesktopReverseOutcome]，不新造第二份
 *
 * 输入端两路（站方那一张的地址 / 本机的一张图）在"发出去之后"是**同一件事**：
 * 对岸也把它们并成一颗 `submit()`（`GalleryReverseViewModel` 里 `if (file != null) … else …`）。
 * 所以这里只新造输入那一颗 gate，四档落点、等结果那句、失败那句、命中那三句
 * 全部转交 `DesktopReverseSearchPolicy` —— 抄一份句子就等于埋一份会漂的口径
 * （判据 `DesktopGalleryLocalReverseTest` ⑩ 把这件事钉成机械尺子：那两句字面量在全树只许出现一次）。
 *
 * ## 输入端那一颗 gate 有六档，逐档都有来处
 *
 * | 档 | 那句说什么 | 来处 |
 * |---|---|---|
 * | 没挑 | 先挑一张，这一笔才有东西交 | 对岸 `submit()` 第一句「先贴一个图片链接，或者从本机挑一张图」的桌面形（这里没有链接那一栏） |
 * | 没配键 | 与浮层那一格**同一句**（[DESKTOP_REVERSE_NO_KEY_REASON]） | 对岸同一条实测裁决：匿名过盾能成功而重试仍 403，发出去就是白等 |
 * | 找不到 | 本机没有那一条路径指的文件 | **桌面多出来的一档**：Android 由 ContentResolver 代挑，不存在那种东西；桌面吃的是文件框给的一串路径 |
 * | 那是目录 | 路径指的是一个目录 | 同上，桌面自己才有的形状 |
 * | 不是图 | 认不出那是哪种图，并列出接哪几种 | 同上 —— 这一档不靠文件框过滤（过滤只挡住"框里看得见的"，路径那一串还是进得来） |
 * | 0 字节 | 那张是空的，没什么可比对 | 同上：空文件能过扩展名那一关而站方会当成一张图去比 |
 * | 超上限 | 那张多大、上限多少、不发 | 对岸那道闸（同值，见下面那颗常量与判据 ④） |
 *
 * ⚠️ 读不到（权限、被别的程序占着）**不在这里判**：那一档要到真读字节那一刻才成立，
 * 而它已经有归宿 —— [desktopLocalReverseRun] 把读那一笔的异常翻成
 * [DesktopReverseOutcome.Failed]（判据 ⑨），界面上念的是失败那一句而不是凭空猜一个原因。
 *
 * ## 上限那道闸为什么必须留
 *
 * 整张图要**原样**进内存再上传：降采样会改变特征，相似度就不是刚才那个相似度
 * （那一句逐字来自对岸 `readBytes` 的注释）。没有这道闸时，挑到一张几十 MB 的原图
 * 会把那些字节塞进 heap 并在一笔要等几秒的请求里等它。
 *
 * ⚠️ 那句人话这里**往上取整**而对岸往下取整：`6,291,457` 字节在对岸会念成
 * 「这张图 6 MB，超过 6 MB 不发」—— 那是一句自相矛盾的话（6 并不大于 6）。
 * 同一件事只许一把尺子钉措辞，所以本批不复刻那个缺陷，并把差异写在这里而不是悄悄改。
 */

/** 那一枚入口的名字。判据与界面共用这一颗常量，不留第二份字面量。 */
internal const val DESKTOP_LOCAL_REVERSE_ENTRY_LABEL: String = "用本机的一张图找"

/**
 * 本机的图最大多少字节 —— **与对岸同值**（`GalleryReverseViewModel.MAX_UPLOAD_BYTES`）。
 *
 * 那枚数是对岸自己的闸而不是站方公布的数（依据只有两条：整张图原样进内存、这一笔是 multipart），
 * 所以这里照抄那个值；判据 ④ 每次跑都回读对岸那一颗，改了一边忘了另一边会当场红。
 */
internal const val DESKTOP_LOCAL_REVERSE_MAX_UPLOAD_BYTES = 6L * 1024 * 1024

/** 接哪几种图：扩展名 → multipart 那一段要写的媒体类型。认不出来就是"不是图"那一档。 */
private val LOCAL_REVERSE_MIMES: Map<String, String> = mapOf(
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "png" to "image/png",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "bmp" to "image/bmp",
)

/** 那一串文件名是哪种图。认不出来交回 `null` = 不是图那一档，由 gate 说成人话。 */
internal fun desktopLocalReverseMimeType(fileName: String): String? {
    val dot = fileName.lastIndexOf('.')
    if (dot < 0 || dot == fileName.length - 1) return null
    return LOCAL_REVERSE_MIMES[fileName.substring(dot + 1).lowercase()]
}

/**
 * 本机这一路的输入端闸门。**发出去之前**能判的事全在这里判完，
 * 所以挡住的那几档一个字节都不读（判据 ③ 数的是读那一笔的次数）。
 *
 * 次序就是判据：没挑时不去问键（那一枚键救不了"什么都没有"），
 * 没键时不去碰那张文件（根本没发车，读它做什么）—— 与对岸 `submit()` 里
 * "输入空 → 配键 → 才发"那一条序完全一致。
 */
internal fun desktopLocalReverseGate(path: String?, hasKey: Boolean): DesktopLocalReverseGate {
    if (path.isNullOrBlank()) {
        return DesktopLocalReverseGate.Blocked("先挑一张本机的图，这一笔才有东西交给站方比对")
    }
    if (!hasKey) return DesktopLocalReverseGate.Blocked(DESKTOP_REVERSE_NO_KEY_REASON)
    val file = File(path)
    val leaf = file.name
    when {
        !file.exists() -> return DesktopLocalReverseGate.Blocked("本机找不到那一张：$leaf")
        file.isDirectory -> return DesktopLocalReverseGate.Blocked("那一条路径指的是一个目录，不是一张图：$leaf")
        else -> {
            val mime = desktopLocalReverseMimeType(leaf)
                ?: return DesktopLocalReverseGate.Blocked(
                    "认不出那一张是哪种图：这一路只接 ${LOCAL_REVERSE_MIMES.keys.joinToString("、")}"
                )
            val size = file.length()
            if (size == 0L) {
                return DesktopLocalReverseGate.Blocked("那一张是 0 字节的空文件，没什么可交给站方比对的")
            }
            if (size > DESKTOP_LOCAL_REVERSE_MAX_UPLOAD_BYTES) {
                return DesktopLocalReverseGate.Blocked(
                    "这张图 ${desktopMebiBytes(size)} MB，超过 ${DESKTOP_LOCAL_REVERSE_MAX_UPLOAD_BYTES / (1024 * 1024)} MB 不发"
                )
            }
            return DesktopLocalReverseGate.Proceed(path, leaf, mime, size)
        }
    }
}

/** 该不该发、发的是哪一张。挡住的那一档从不带着半个字节发车。 */
internal sealed interface DesktopLocalReverseGate {
    /** 可以发。[sizeBytes] 是闸口量到的那个长度，屏上要念的就是它。 */
    data class Proceed(val path: String, val fileName: String, val mimeType: String, val sizeBytes: Long) :
        DesktopLocalReverseGate

    data class Blocked(val why: String) : DesktopLocalReverseGate
}

/**
 * 读那张并交出去，落点复用批 35 那四档。
 *
 * @param readFile 读字节那一步。**留成一枚注入点**不是为了好看：
 *   判据要能验"挡住的那几档一次都没读"（那是"不发就不该把整张图塞进内存"这条裁决的形状），
 *   而真界面吃的是 [File.readBytes]。
 *
 * ⚠️ 取消**原样抛出去**，与 [desktopReverseRun] 同一条：这一笔挂在屏的协程上，
 *   换域、关掉那一格都会取消它，把它翻成"这一张没搜成"就是替用户的一次打断编出一个结果。
 */
internal suspend fun desktopLocalReverseRun(
    reverse: GalleryReverseSearch,
    gate: DesktopLocalReverseGate,
    allowNsfw: Boolean,
    /**
     * 读字节那一步。**必填**（带默认值的回调参数是 `DesktopCallbackParamLedgerTest` 那一条禁令：
     * 漏传不报错，而这一枚漏传的形状正是"挡住的那几档也去读了整张图"那种没人知道的错）。
     * 留成一枚注入点不是为了好看：判据要能数"读那一步被走了一次都没有"。
     */
    readFile: (String) -> ByteArray,
    /**
     * 那一笔真抛过的异常交给这里。落点里只剩一句被压平的原话，
     * 而"没成"那第二行读数要的是**类型与栈顶那一帧** —— 从字符串里猜不出那两枚，
     * 所以让抛的那一处当场把它交出去（界面那颗 holder 存下来，判据 ⑬ 钉的是这条用法）。
     */
    onThrowable: (Throwable) -> Unit,
): DesktopReverseOutcome {
    if (gate is DesktopLocalReverseGate.Blocked) return DesktopReverseOutcome.NotSent(gate.why)
    val proceed = gate as DesktopLocalReverseGate.Proceed
    val bytes = try {
        readFile(proceed.path)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        onThrowable(failure)
        return DesktopReverseOutcome.Failed(failure.message ?: desktopFailureType(failure))
    }
    val page = try {
        reverse.searchByFile(bytes, proceed.fileName, proceed.mimeType, allowNsfw)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        onThrowable(failure)
        return DesktopReverseOutcome.Failed(failure.message ?: desktopFailureType(failure))
    }
    if (page.hits.isEmpty()) return DesktopReverseOutcome.NoSimilar(page.message, page.resultsReturned)
    return DesktopReverseOutcome.Answered(page.hits, reverse.maxResults, null)
}

/**
 * 挑中的那一句：是哪一张、多大、以及**整张原样交出去**这件事。
 *
 * 后半句不是装饰：用户会问"为什么这么慢"与"它会不会把我这张压小"，
 * 而"不降采样"正是那两问的答案（来源见文件头那条注释）。
 */
internal fun desktopLocalReversePickedReading(fileName: String, sizeBytes: Long): String =
    "挑中的是 $fileName（${desktopHumanBytes(sizeBytes)}）· 整张原样交给站方，不降采样"

/**
 * 一轮的 stdout 读数。**那一串请求参数与键值一个字都不进这一行**（只点名"挑的是哪一个文件名"，
 * 那一句的来处见下面 [path] 那条注释；整条路径也不复述 —— 用户本机有哪些目录不是这台机器要念的事）。
 *
 * 三档落点各念各的：没发车（`发=否`）、摆得出（带条数）、没搜成（带原话）。
 * "没量过的相似度高低"这一族屏上与读数都不说，与批 35 同一条口径。
 *
 * ⚠️ [path] 与 [gate] **两枚都要递**：挡住的那一档从闸口里取不到文件名（它根本没放行那张），
 * 只从闸口取就会把"用户明明给了一条路径"念成「挑=没挑」—— 2026-10-09 帧 A 的 stdout 当场抓到这一句，
 * 而那一屏上的红字说的是另一件事。读数与行为不一致时，只有日志与像素那一眼抓得到（判据 ⑫ 钉住）。
 */
internal fun desktopLocalReverseReadout(
    path: String?,
    gate: DesktopLocalReverseGate,
    outcome: DesktopReverseOutcome,
): String {
    val picked = (gate as? DesktopLocalReverseGate.Proceed)?.fileName
        ?: path?.takeIf { it.isNotBlank() }?.let { File(it).name }
        ?: "没挑"
    val sent = if (gate is DesktopLocalReverseGate.Blocked) "否" else "是"
    val (stage, hits) = when (outcome) {
        is DesktopReverseOutcome.Answered -> "对上" to outcome.hits.size
        is DesktopReverseOutcome.NoSimilar -> "站方说没有相似图（回 ${outcome.returned} 条）" to 0
        is DesktopReverseOutcome.Failed -> "没搜成 ${desktopScrubSecrets(outcome.reason)}" to -1
        is DesktopReverseOutcome.NotSent -> "没发车 ${desktopScrubSecrets(outcome.why)}" to -1
    }
    return "D_反搜本地 挑=$picked 发=$sent 落点=$stage 命中=${if (hits < 0) "-" else hits}"
}

/**
 * 那一笔没成时的第二行：原话 + 类型 + 栈顶那一帧。
 *
 * 与批 53/54/76 同一族形状（[desktopOneLineMessage] 已经把 query 里的凭据打掉，
 * 所以这一行说得出"是哪一步炸的"而不会替用户漏一枚键）。
 */
internal fun desktopLocalReverseRefusalReading(reason: String, error: Throwable?): String {
    val oneLine = error?.let { desktopOneLineMessage(it) } ?: desktopScrubSecrets(reason)
    val type = error?.let { desktopFailureType(it) } ?: "没给出异常"
    return "D_反搜本地 未成 因=$oneLine 型=$type 栈=${error?.let { desktopFailureFrame(it) } ?: "没有"}"
}

/**
 * 这台机器给不给"挑一张本机的图"那条路。
 *
 * 与批 54 那颗 `desktopCanCopyImage()` 同一族形状：先问支持不支持，不支持时**不摆那枚按钮**，
 * 而是把这句实话摆出来 —— 按下去什么都不发生的一枚按钮比没有按钮更糟。
 * AWT 没有"文件框支持不支持"这一问，无头那一档是唯一能问出来的（`FileDialog` 在无头下直接抛）。
 */
internal fun desktopCanPickLocalImage(): Boolean = !GraphicsEnvironment.isHeadless()

/**
 * 开系统的文件框，等用户挑一张，交回那一条路径；没挑（取消）交回 `null`。
 *
 * ## 为什么它必须在 AWT 事件线程上跑
 *
 * `FileDialog` 是**模态**的：它会吃掉调用线程直到用户点完。组合那一侧的点击回调本来就在
 * AWT 事件线程上（Compose Desktop 的默认调度），所以直接开；从别的线程进来时走
 * [EventQueueInvokeAndWait] 挪过去 —— 反过来（在事件线程上调 `invokeAndWait`）会抛
 * `ErrorWrongThread`，那一颗坑就是本仓记过的"同一件事两种线程形状"。
 *
 * ## 这一跳的读数是**外部操作留下的**，不是本批自验
 *
 * 鼠标自验是禁的（手册 §8）⇒ 本批自己递不出去那一下。2026-10-09 09:02 用交付包 b77b 起窗那一跑，
 * stdout 里多出第二行 `D_反搜本地 挑=4a0f8a81-….png 发=是 落点=没搜成 …`，而屏上同一时刻念着
 * 「挑中的是 4a0f8a81-….png（2 MB）· 整张原样交给站方」—— 那一串 UUID 文件名不在本批任何命令里，
 * 而全树只有一处会把它递进发车（按钮那一枚，判据 ⑬ 钉的就是这一枚计数），
 * 所以那一眼能反推出"框真开了、真挑到了一张"。
 * ⚠️ 但没人可复核是谁点的、挑的是哪一档 ⇒ 这一条只记成"有读数"，**不许记成"验过了"**；
 * 种子（`--reverse-file=`）走的仍是"跳过文件框"那一条路，它验的是路径之后的三跳。
 * 判据 `DesktopGalleryLocalReverseTest` ⑰ 与 `DesktopReverseFileSeedTest` ③ 钉的是这两句不许互相冒充。
 */
internal fun desktopPickLocalImage(owner: Frame?): String? {
    if (!desktopCanPickLocalImage()) return null
    var chosen: String? = null
    val open: () -> Unit = {
        val dialog = FileDialog(owner, "挑一张本机的图", FileDialog.LOAD)
        dialog.isVisible = true
        val name = dialog.file
        if (name != null) chosen = File(dialog.directory, name).absolutePath
    }
    if (java.awt.EventQueue.isDispatchThread()) open() else desktopRunOnEventThread(open)
    return chosen
}

/** 把一段动作挪到 AWT 事件线程上等它跑完；挪不动（无头、线程已死）就当这一笔没成 —— 能力判据那一句已经先把按钮收起来了。 */
private fun desktopRunOnEventThread(action: () -> Unit) {
    runCatching { java.awt.EventQueue.invokeAndWait(action) }
}

/** 字节数 → 人话（B / KB / MB，往上取整）。 */
private fun desktopHumanBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${desktopMebiBytes(bytes)} MB"
    bytes >= 1024 -> "${desktopKibiBytes(bytes)} KB"
    else -> "$bytes B"
}

private fun desktopKibiBytes(bytes: Long): Long = (bytes + 1023) / 1024

private fun desktopMebiBytes(bytes: Long): Long = (bytes + 1024 * 1024 - 1) / (1024 * 1024)
