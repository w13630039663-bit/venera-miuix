package com.venera.desktop.gallery.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 批 96 · 漫画「把一整话存到本机」那一格的命名与落盘口径（**装载层不吃引擎句柄**，与阅读那一屏同一分家规矩）。
 *
 * ## 为什么这一颗件在桌面侧是新的
 *
 * 桌面今天会"存一张图"（`desktopSaveImageRun`，只吃画廊那一侧的 `GalleryPost`），也会"读一页"
 * （`DesktopComicPageFetcher` → `engine.pageImage(...)`），而**把一整话的页序列写进盘**那一格没有：
 * 那一问页序列的调用今天只 inline 住在阅读那一屏的 `LaunchedEffect` 里（`gallery/ui/DesktopComicReader.kt:291-292`）。
 *
 * ## 命名口径全部逐字回核对岸（`app/…/download/DownloadManager.kt`）
 *
 * - 净化：`replace(Regex("[/\\\\:*?\"<>|]"), "_")` —— 对岸三处（`sourceKey` / `comicId` / 章节标题）。
 *   ⚠️ 那三处**不一致**：只有章节标题那一行后面多一个 `.trim()`。本件**照抄这条不一致**而不"顺手统一" ——
 *   统一了就会与对岸已经写在盘上的目录名不同轨（同一本漫画在盘上出现两个目录就是这种漂移的用户可见形状）。
 * - 章节目录：`"${order.toString().padStart(4, '0')}_$safeTitle"`；页名：`"${(idx + 1).toString().padStart(4, '0')}.jpg"`
 *   （**库里 0 基数而名从 1 数起**；扩展名对岸是写死 `.jpg` 的，本件不自己判断真实格式 —— 判了就和两端认不出的那一族同形）。
 * - 清单：`chapter.json`，字段 `chapterId` / `title` / `order` / `expectedPages` / `complete`（对岸同一份）。
 *
 * ## 两条降级口径（本仓那条「宁可错慢不可静默交错」）
 *
 * - 有一页取不到 ⇒ **抛**（带得上页号与站方原话），先把清单写成 `complete=false` 再抛：已写的页留在盘上（不清用户的东西），
 *   而"齐不齐"那一判从此说false ⇒ 下一趟会补那一页而不是当成下全了。
 * - 已经齐的那一趟**一页都不取**直接交回 0（对岸那句注释：不判就会"点一下就直接重下一遍"）。
 *
 * 清单那一笔走 `.tmp` + `Files.move(REPLACE_EXISTING[, ATOMIC_MOVE])` —— 不用 `File.renameTo`
 * （Windows 上目标已存在时它只返回 false 而不覆盖，本仓有一条全目录扫描 `SharedStoreAtomicWriteTest` ⑤ 钉着这一族）。
 */

/** 对岸那三处净化共用的那张字符表（`/ \ : * ? " < > |`）。 */
private val DESKTOP_COMIC_UNSAFE_CHARS = Regex("[/\\\\:*?\"<>|]")

/** 清单那份文件的名字（沿用对岸那一串，不另起 `manifest.json`）。 */
private const val DESKTOP_COMIC_CHAPTER_MANIFEST = "chapter.json"

/**
 * 章节下载那一格的根：`数据根/downloads`（对岸 `ComicStorageRoot` 的默认根就是 `<filesDir>/downloads`，
 * 目录名逐字沿用 —— 两端各起一个名字就会在同一台机器上长出两棵树）。
 *
 * ⚠️ 它与画廊那格的 `数据根/downloads/图库`（`desktopGallerySaveDir`）是**父子**关系而不是并列：
 * 画廊存的是散图，漫画存的是 `<站名键>/<号>/<话目录>/NNNN.jpg`，两条路各数各的目录，别互相扫。
 */
internal fun desktopComicDownloadsRoot(dataRoot: File): File = File(dataRoot, "downloads")

/** `sourceKey` / `comicId` 那一档：只替字符，**不去首尾空白**（对岸那两行没有 `.trim()`）。 */
internal fun desktopComicSafePathKey(raw: String): String = raw.replace(DESKTOP_COMIC_UNSAFE_CHARS, "_")

/** 章节标题那一档：替字符之后**再去首尾空白**（对岸那一行多这一步）。 */
internal fun desktopComicSafeChapterTitle(raw: String): String =
    raw.replace(DESKTOP_COMIC_UNSAFE_CHARS, "_").trim()

/** 章节目录名：话号补零四位 + `_` + 净化后的标题（`padStart` 只补不截）。 */
internal fun desktopComicChapterDirName(order: Int, title: String): String =
    order.toString().padStart(4, '0') + "_" + desktopComicSafeChapterTitle(title)

/** 页文件名：库里 0 基数 ⇒ 名从 1 数起、补零四位，扩展名恒 `.jpg`（对岸那句写死的）。 */
internal fun desktopComicChapterPageFileName(index: Int): String =
    (index + 1).toString().padStart(4, '0') + ".jpg"

/** `根/<净化 sourceKey>/<净化 comicId>/<章节目录名>` —— 这颗**只算不建**（建目录留给写那一侧）。 */
internal fun desktopComicChapterDir(
    root: File, sourceKey: String, comicId: String, order: Int, title: String,
): File = File(
    File(File(root, desktopComicSafePathKey(sourceKey)), desktopComicSafePathKey(comicId)),
    desktopComicChapterDirName(order, title),
)

/** 清单那一行的读数：五格齐全，失败那一档多带一行化原因。 */
internal fun desktopComicChapterDownloadLine(
    comicId: String, order: Int, written: Int, expected: Int, complete: Boolean,
): String = "D_下载 号=$comicId 话=$order 页=$written/$expected 全=${if (complete) "是" else "否"}"

internal fun desktopComicChapterDownloadLine(
    comicId: String, order: Int, written: Int, expected: Int, cause: Throwable,
): String = "D_下载 号=$comicId 话=$order 页=$written/$expected 全=否 因=${oneLine(cause.message ?: cause::class.java.simpleName)}"

private fun oneLine(raw: String): String = raw.replace('\n', ' ').replace('\r', ' ').trim()

/**
 * 写清单：`chapter.json`。走 `.tmp` + 替换改名，临时档不留盘（`finally` 里删）。
 *
 * ⚠️ 字段表与对岸同一份 —— 对岸改了字段名，`DesktopComicChapterDownloadTest` ⑥ 会红在这里。
 * ⚠️ **字段名同轨不等于语义同轨**：`chapterId` 那一格装的是**话号**（对岸 `DownloadManager.kt:505` `put("chapterId", task.chapterId)`），
 * 装成本子号就两端互认不出同一话（对岸拿它与话号比：`:309`），而那一站的表观是"没下过"⇒ 批量下载会**重下一遍**。
 * 第九十七批改判：从前这里写的是 `comicId`，见 rounds `desktop-comic-downloads-screen-2026-10-12.md` §6。
 */
internal fun desktopComicWriteChapterManifest(
    dir: File, chapterId: String, order: Int, title: String, expectedPages: Int, complete: Boolean,
) {
    val body = buildString {
        append('{')
        append("\"chapterId\":\"").append(chapterId).append('"').append(',')
        append("\"title\":\"").append(oneLine(title)).append('"').append(',')
        append("\"order\":").append(order).append(',')
        append("\"expectedPages\":").append(expectedPages).append(',')
        append("\"complete\":").append(complete)
        append('}')
    }
    val target = File(dir, DESKTOP_COMIC_CHAPTER_MANIFEST)
    desktopComicAtomicWriteTo(target, body.toByteArray())
}

/**
 * 把一串字节交到 `target` 上：同目录临时档 + **带覆盖语义**的那一颗改名。
 *
 * 两个坑都是本仓栽过的：`File.renameTo` 在 Windows 上目标已存在时**只返回 `false` 而不抛**（于是第二笔静默丢掉、
 * 盘上留的还是旧的那一颗），而没有覆盖那枚参数的 `Files.move` 同样不替换。所以这里沿用本仓共享档那一形
 * （`DesktopGalleryBrowseHistory`、`JsonKeyValueStore`、`DesktopTagDictionaryAsset` 三处同一形）：
 * 先写同目录 `*.tmp`，再带 `REPLACE_EXISTING` 改名过去；这台文件系统不许原子改名时退回非原子替换，
 * 而临时档一律在 `finally` 里删 —— 留在盘上的 `*.tmp` **不算一页**（那两个判吃的是精确页名）。
 */
internal fun desktopComicAtomicWriteTo(target: File, bytes: ByteArray) {
    val temp = File(target.parentFile, target.name + ".tmp")
    try {
        temp.writeBytes(bytes)
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (unsupported: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        temp.delete()
    }
}

/**
 * 「这一话齐不齐」：清单说齐 **且** 页名从 `0001` 连号到 `expectedPages` 一颗不缺，才算齐。
 *
 * 为什么两样都要看：半途而废的目录（有页没清单）与下全的目录（有页有清单）在盘上同形，只看页数会把前者认成后者，
 * 而那一档在屏上的表现是"显示已下载而翻开缺页"。
 */
internal fun desktopComicChapterIsComplete(dir: File, expectedPages: Int): Boolean {
    if (expectedPages <= 0) return false
    val manifest = File(dir, DESKTOP_COMIC_CHAPTER_MANIFEST)
    if (!manifest.isFile) return false
    val text = runCatching { manifest.readText() }.getOrNull() ?: return false
    if (!Regex("\"complete\"\\s*:\\s*true").containsMatchIn(text)) return false
    for (index in 0 until expectedPages) {
        // 第一百一十批：这一格从前数的是"名字在且非零长度"，于是 4096 字节的过盾页 HTML 也算一页 ⇒
        // 清单说齐它就齐，而下载那一趟因"看着齐了"根本不去重取（**屏上念「已下全」而翻开缺页**的那条路）。
        // 现在它与续跑/屏上读侧/队列那一行吃同一颗 [desktopComicPageLooksWhole]。
        if (!desktopComicPageLooksWhole(dir, index)) return false
    }
    return true
}

/**
 * 这一趟整话下载**自己说得出**它是跑完的还是被停的（批 100）。
 *
 * 为什么不让调用点"自己记"：对岸 `LocalComicManager.kt:302` 正是把 `deleteRecursively()` 的返回值丢掉、
 * 再无条件说"已删除"（批 98 逐行证实），于是"没做成"与"做成了"在屏上塌成一格。
 * 取消那一路同理 —— 只有 runner 自己知道它停在哪一页、有没有把清单落回 `complete=false`。
 */
internal sealed interface DesktopComicChapterRun {
    /** 这一趟把该补的页都补完了（`written` 是**这一趟**新写的页数，不是盘上总页数）。 */
    data class Completed(val written: Int) : DesktopComicChapterRun

    /** 这一趟被用户停在第 [written] 页之后：清单已落 `complete=false`，**页一颗都没删**。 */
    data class Cancelled(val written: Int) : DesktopComicChapterRun
}

/** 逐页进度那一句（批 100）：`页=` 念的是**盘上已有**的页数，所以续跑那一趟起手就不是 0。 */
internal fun desktopComicChapterProgressLine(comicId: String, order: Int, written: Int, expected: Int): String =
    "D_下载进度 号=$comicId 话=$order 页=$written/$expected"

/**
 * 取消那一句（批 100）：念得出"下次从第几页接着补"，而这一句里**不许**出现「已下载」。
 *
 * `下次=` 从 1 数起（人话），而 `written` 是页数也就是下一张页的 0 基数索引 —— 两者差一这一格正是
 * 批 96 那条「库里存索引、屏上从 1 数起」的同一口径。
 */
internal fun desktopComicChapterCancelledLine(comicId: String, order: Int, written: Int, expected: Int): String =
    "D_下载取消 号=$comicId 话=$order 页=$written/$expected 下次=第 ${written + 1} 页"

/**
 * 把一整话写进 `dir`：逐页取字节、按对岸那套页名落盘，交回这一趟**跑完了还是被停**（[DesktopComicChapterRun]）。
 *
 * - 已经齐的那一趟一页都不取（直接交回 `Completed(0)`，而进度那一格念的是满的）。
 * - 已经写过的页（非空文件）跳过 ⇒ 上一趟半途失败或被取消，这一趟只补缺的那几页。
 * - 有一页取不到 ⇒ 先把清单写成 `complete=false`，再抛 `IllegalStateException`（带页号与站方原话）—— 绝不静默少页。
 * - **每写一页**调一次 [progress]（分子是盘上已有的页数），而**每一页动手之前**查一次 [shouldCancel]：
 *   要停就先把清单落成 `complete=false`、**留下的页一颗都不删**，交回 `Cancelled(这一趟新写的页数)`。
 *
 * ⚠️ [shouldCancel] 是**协作式**的：它停在两页之间，正在飞的那一笔请求不会被掐掉（所以"按了没立刻停"不是缺陷，
 * 而这一格也不许在屏上写成"已停止"）。
 * ⚠️ 进度那一格只念"写到第几页"，**齐不齐仍只由 [desktopComicChapterIsComplete] 那一判断**（这条线上一共存着
 * 三把口径而桌面只许用最硬那一档 —— 批 97/98/99 同一课）。
 *
 * ⚠️ `comicId` 与 `chapterId` 是**两枚**形参而不是同一枚换名：前者只进报错那一句（"号=… 话=…"要念得给人看），
 * 后者进清单那一格（对岸按它互认整棵目录树）。漏掉后者或把前者递进去，屏上"已下载"那一档就会指到别的话。
 */
/**
 * 第一百零二批 · 那一串字节像不像一张图。
 *
 * 四档魔数与那道"少于 12 字节一律不算"的下界**逐字回核对岸** `download/DownloadManager.kt:580-592`
 * （判据里有一颗正则钉着那张表，对岸改了桌面会红）。
 * ⚠️ 白名单**不止 JPEG**：页名恒 `.jpg`（沿用对岸那一形）而站方常回 PNG/GIF/WebP ——
 * 只认 JPEG 头会把好页挡在门外，那比收下坏页更糟。
 */
internal fun desktopComicBytesLookLikeImage(bytes: ByteArray): Boolean {
    if (bytes.size < 12) return false
    fun at(index: Int): Int = bytes[index].toInt() and 0xFF
    if (at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF) return true
    if (at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47) return true
    if (at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x38) return true
    return at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 &&
        at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50
}

/** 那一页要长到多大才算"已经有了" —— **照抄对岸那把下界**（`DownloadManager.kt:454` 的 `length() > 1024`）。 */
private const val DESKTOP_COMIC_WHOLE_PAGE_MIN_BYTES = 1024L

/**
 * 第一百一十批 · 盘上第 [index] 页算不算**已经有了** —— 续跑跳过、屏上「已下全 / 缺 N 页」、
 * 队列那一行念的页数、那颗判齐件的逐页循环**四处共用这一颗**（同一屏同一个列名不许吃两把尺子）。
 *
 * ⚠️ 那条边界也记在这儿：`DesktopComicChapterDelete.kt` 里的 `pageCountOf` 同是"数页"而**故意不吃这一颗** ——
 * 它数的是"刚才在那儿、马上要没了的颗数"，换成这把尺子就会念「删掉了 2 页」而盘上少 3 颗（把释放说小）。
 *
 * 为什么不能只看"名字对 + 非零长度"：第一百零二批那道魔数闸只管**新写的字节**，管不到**已经在盘上的**——
 * 闸装好之前落盘的过盾页 HTML、错误信封 JSON、Brotli/GZIP 压缩包名字对、尺寸也够大，只有头部会说实话。
 * 只看长度就是"重试永远治不了"（对岸 `DownloadManager.kt:446-451` 那句原话）。
 *
 * ⚠️ 两处**故意不抄对岸**：① 对岸 `isValidImageBytes(targetFile.readBytes())` 整份读，
 * 这里只取**前 12 字节**（一次续跑要判 30×N 页，而那张魔数表全在前 12 字节内 —— 判式 ⑦ 钉着这一形）；
 * ② 对岸还有一条「JM 混淆图恒定不跳过」，桌面**不搬**：这一路在落盘之前就完成去混淆
 * （`EngineSession.pageImage` 交回的是还原后的图），那条成因在桌面不存在，搬过来只会让每页永远重下。
 * 读不到（文件正被换掉之类）一律回"不算已有"—— 它退化向**重取**而不是退化向"谎称齐了"。
 */
internal fun desktopComicPageLooksWhole(dir: File, index: Int): Boolean {
    val page = File(dir, desktopComicChapterPageFileName(index))
    if (!page.isFile || page.length() <= DESKTOP_COMIC_WHOLE_PAGE_MIN_BYTES) return false
    val head = try {
        Files.newInputStream(page.toPath()).use { it.readNBytes(12) }
    } catch (t: Throwable) {
        return false
    }
    return desktopComicBytesLookLikeImage(head)
}

/** 报错那句里带的头四字节（站方回了什么，光看"不是图像"查不动）。 */
private fun hexHeadOf(bytes: ByteArray): String =
    bytes.take(4).joinToString("") { ("%02X").format(it.toInt() and 0xFF) }

internal suspend fun desktopComicDownloadChapter(
    dir: File, comicId: String, chapterId: String, order: Int, title: String, expectedPages: Int,
    shouldCancel: () -> Boolean,
    progress: suspend (written: Int, expected: Int) -> Unit,
    fetch: suspend (index: Int) -> ByteArray,
): DesktopComicChapterRun {
    if (expectedPages <= 0) error("这一话一页都没有（号=$comicId 话=$order）")
    if (desktopComicChapterIsComplete(dir, expectedPages)) {
        progress(expectedPages, expectedPages)
        return DesktopComicChapterRun.Completed(written = 0)
    }
    if (!dir.mkdirs() && !dir.isDirectory) error("建不出目录：$dir")
    // 每一页在不在盘上，只在这一趟开头数一次：起手那格进度与"跳过哪几页"吃的是同一份，不会两处各数各的。
    // 第一百一十批：那一判换成共用那颗 [desktopComicPageLooksWhole]（名字 + 尺寸 + **头部是图**），
    // 于是闸装好之前落盘的过盾页会被重取覆盖，而不是被永远当成"这一页有了"。
    val present = (0 until expectedPages).map { index -> desktopComicPageLooksWhole(dir, index) }
    var onDisk = present.count { it }
    progress(onDisk, expectedPages)
    var written = 0
    for (index in 0 until expectedPages) {
        if (shouldCancel()) {
            desktopComicWriteChapterManifest(dir, chapterId, order, title, expectedPages, complete = false)
            return DesktopComicChapterRun.Cancelled(written = written)
        }
        if (present[index]) continue
        val bytes = try {
            fetch(index)
        } catch (cause: Throwable) {
            desktopComicWriteChapterManifest(dir, chapterId, order, title, expectedPages, complete = false)
            throw IllegalStateException("取页失败 页=$index 共=$expectedPages 因=${oneLine(cause.message ?: cause::class.java.simpleName)}", cause)
        }
        if (bytes.isEmpty()) {
            desktopComicWriteChapterManifest(dir, chapterId, order, title, expectedPages, complete = false)
            throw IllegalStateException("取回空字节 页=$index 共=$expectedPages 因=站方给了 0 字节")
        }
        if (!desktopComicBytesLookLikeImage(bytes)) {
            // 坏字节**一颗都不落盘**：落下就永远算一页，那一话"齐了"而其中几页永远打不开。
            desktopComicWriteChapterManifest(dir, chapterId, order, title, expectedPages, complete = false)
            throw IllegalStateException(
                "取回的不是图像 页=$index 共=$expectedPages 首四字节=${hexHeadOf(bytes)} 因=站方回的不是图像数据（过盾页或错误信封？）",
            )
        }
        desktopComicAtomicWriteTo(File(dir, desktopComicChapterPageFileName(index)), bytes)
        written += 1
        onDisk += 1
        progress(onDisk, expectedPages)
    }
    desktopComicWriteChapterManifest(dir, chapterId, order, title, expectedPages, complete = true)
    return DesktopComicChapterRun.Completed(written = written)
}
