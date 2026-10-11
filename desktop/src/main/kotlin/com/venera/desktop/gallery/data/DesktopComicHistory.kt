package com.venera.desktop.gallery.data

import com.venera.compose.data.db.HistoryRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 批 82 · 阅读那一屏写进 `comic_history` 的那一笔。
 *
 * 这张表**两端共用**：同一行数据两端各写一种形状，表现是"手机上的进度在桌面丢了"或反过来，
 * 而它没有版本号也没有迁移，漂了不会红。所以下面每一个实参都是回核
 * `app/src/main/java/com/venera/compose/reader/VeneraReaderScreen.kt` 里那处 `saveHistory(` 得来的，
 * 判据 [DesktopComicHistoryTest] 逐字段钉着，改动前先回看那一段。
 */
internal data class DesktopReadingProgress(
    val comicId: String,
    val title: String,
    val sourceKey: String,
    val chapterTitle: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val totalPages: Int,
    /**
     * 这一本的封面地址（对岸那一段写的是 `coverUrl = session.coverUrl`，会话里那一枚由
     * [desktopSessionCover] 按对岸同一条优先级算出）。批 82.6 之前这一格恒空串，
     * 而 `saveHistory` 那条「空封面沿用旧值」只救**本来就有封面**的行 ——
     * 桌面第一次写的那一行从此是空白框且不会自愈。
     */
    val coverUrl: String,
)

/**
 * 会话封面 = **详情解析出来的那张优先，入口带的兜底**（两档都空才是空串）。
 *
 * 这条优先级不是桌面定的：对岸 `ComicDetailViewModel` 那一处就是这么算的，旁边还有一段成文的理由
 * （照抄入口值 = 给历史页留永久空白框），判据 `DesktopComicCoverChainTest` 回核那一句原式。
 * 用 `ifBlank` 而不是 `isEmpty`：站方给一枚空白串也算缺货。
 */
internal fun desktopSessionCover(detailCover: String, entryCover: String): String =
    detailCover.ifBlank { entryCover }

/** 时钟由调用方递进来：内部取时钟的件没法判等，而这一颗要的正是"同一输入同一行"。 */
internal fun desktopHistoryRecord(p: DesktopReadingProgress, nowMillis: Long): HistoryRecord = HistoryRecord(
    comicId = p.comicId,
    title = p.title,
    // 对岸那一段写的就是 `author = ""`（阅读器手里没有作者）。补一个猜出来的名字 = 把假数据写进共享表。
    author = "",
    coverUrl = p.coverUrl,
    sourceName = p.sourceKey.ifBlank { DESKTOP_HISTORY_SOURCE_FALLBACK },
    lastChapterTitle = p.chapterTitle,
    lastChapterIndex = p.chapterIndex,
    lastPageIndex = p.pageIndex,
    totalPages = p.totalPages,
    updatedAt = nowMillis,
)

/** 这一句在对岸那句里；判据不写死这里，改的是对岸那一处就会红。 */
private const val DESKTOP_HISTORY_SOURCE_FALLBACK: String = "拷贝漫画"

/** 一笔写下去的读数。`failed` 非空时念"没写成 + 成因"，绝不念成"写了"。 */
internal fun desktopComicHistoryReading(p: DesktopReadingProgress, failed: String?): String {
    val head = "D_历史 ${if (failed == null) "写" else "没写成"} 源=${p.sourceKey.ifBlank { DESKTOP_HISTORY_SOURCE_FALLBACK }}" +
        " 号=${p.comicId} 章=${p.chapterIndex} 页=${p.pageIndex}/${p.totalPages}"
    return if (failed == null) head else "$head 因=${failed.replace('\n', ' ').replace('\r', ' ')}"
}

/**
 * 把进度交出去，并把念哪一句**返回**给调用方（不在这颗里 println：与 `desktopComicReaderReading`
 * 同一族口径 —— 读数是纯函数，println 在消费点，这样离屏也能判等）。
 *
 * 两层上下文逐字照对岸：`NonCancellable` 管"退出时组合被收掉，最后这一笔还得写完"，
 * `Dispatchers.IO` 管"落库不占渲染那一根线"。
 *
 * 写失败只念一句、不抛：这一屏正在看图，历史是顺带的事，把库里的故障做成整屏崩是错的方向。
 * 唯独取消原样抛出去 —— 上游收了不等于"数据库炸了"，折成失败读数会念出一笔根本没干的活儿。
 */
internal suspend fun desktopWriteReadingHistory(
    progress: DesktopReadingProgress,
    nowMillis: Long,
    save: suspend (HistoryRecord) -> Unit,
): String = try {
    withContext(NonCancellable + Dispatchers.IO) { save(desktopHistoryRecord(progress, nowMillis)) }
    desktopComicHistoryReading(progress, null)
} catch (canceled: CancellationException) {
    throw canceled
} catch (failure: Throwable) {
    desktopComicHistoryReading(progress, "${failure::class.java.simpleName}: ${failure.message}")
}
