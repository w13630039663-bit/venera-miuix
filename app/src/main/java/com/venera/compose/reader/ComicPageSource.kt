package com.venera.compose.reader

import java.io.File

/**
 * 漫画单页图片数据源抽象
 * 统一网络在线图片、本地文件、ZIP/CBZ 压缩包内图片流
 */
sealed class ComicPageSource {
    abstract val pageIndex: Int

    data class Network(
        val url: String,
        override val pageIndex: Int,
        val width: Int = 1080,
        val height: Int = 1600
    ) : ComicPageSource()

    /**
     * 动态解析页 —— 源声明了 `comic.onImageLoad` 时，`pages` 里的每一项是「图片键」，
     * 展示前须经 JS `onImageLoad(imageKey, comicId, epId)` 解析成真实地址
     * （对齐官方 `GetImageLoadingConfigFunc` 语义；EH 图库整本即此形态，
     * jm/nhentai 等源则借此做 URL 重写与防盗链加签）。
     */
    data class DynamicNetwork(
        val imageKey: String,
        override val pageIndex: Int,
        val sourceKey: String,
        val comicId: String,
        val epId: String,
        val width: Int = 1080,
        val height: Int = 1600
    ) : ComicPageSource() {
        /**
         * 图片缓存键 —— 与官方 `network/images.dart` 的
         * `cacheKey = "$imageKey@$sourceKey@$cid@$eid"` 完全同构。
         *
         * 关键：源每次解析出的真实 URL 可能带临时签名/token（EH 尤甚），
         * 若拿 URL 当缓存键，同一页每次翻回来都会被判定为「新图」而重新下载。
         * 用 imageKey 作键，翻回旧页才能命中 Coil 的磁盘缓存。
         */
        val cacheKey: String get() = "$sourceKey@$comicId@$epId@$imageKey"
    }

    data class LocalFile(
        val file: File,
        override val pageIndex: Int
    ) : ComicPageSource()

    data class ZipEntry(
        val zipFile: File,
        val entryName: String,
        override val pageIndex: Int
    ) : ComicPageSource()
}

/**
 * 阅读模式（对齐 Flutter 原版 5 种排版）
 */
enum class ReaderReadingMode(
    val key: String,
    val label: String,
    val icon: String,
    val description: String
) {
    VERTICAL_CONTINUOUS("VERTICAL_CONTINUOUS", "条漫·连续", "📜", "竖向无缝流式滚动，适合韩漫/条漫"),
    HORIZONTAL_RTL("HORIZONTAL_RTL", "日漫·右至左", "📖", "从右往左翻页，传统日漫习惯"),
    HORIZONTAL_LTR("HORIZONTAL_LTR", "美漫·左至右", "📑", "从左往右翻页，国漫/美漫习惯"),
    HORIZONTAL_CONTINUOUS("HORIZONTAL_CONTINUOUS", "横向·连续", "↔️", "横向画卷无缝滚动"),
    DOUBLE_PAGE("DOUBLE_PAGE", "对开·双页", "📰", "双页拼合对开展示，还原实体书阅读");

    companion object {
        val HORIZONTAL_PAGE = HORIZONTAL_RTL

        fun fromKey(key: String?): ReaderReadingMode {
            return when (key?.uppercase()) {
                "VERTICAL_CONTINUOUS", "VERTICAL" -> VERTICAL_CONTINUOUS
                "HORIZONTAL_RTL", "RTL", "HORIZONTAL", "HORIZONTAL_PAGE" -> HORIZONTAL_RTL
                "HORIZONTAL_LTR", "LTR" -> HORIZONTAL_LTR
                "HORIZONTAL_CONTINUOUS", "H_CONTINUOUS" -> HORIZONTAL_CONTINUOUS
                "DOUBLE_PAGE", "DOUBLE" -> DOUBLE_PAGE
                else -> VERTICAL_CONTINUOUS
            }
        }
    }
}

/**
 * 章节数据载体
 */
data class ReaderChapter(
    val id: String,
    val title: String,
    val pages: List<ComicPageSource> = emptyList(),
    val isLoaded: Boolean = pages.isNotEmpty()
)

/**
 * 活跃阅读会话
 */
data class ReaderSession(
    val comicId: String,
    val comicTitle: String,
    val coverUrl: String,
    val sourceName: String = "",
    val sourceKey: String = "",
    val chapters: List<ReaderChapter>,
    val initialChapterIndex: Int = 0,
    val initialPageIndex: Int = 0
)

/**
 * 阅读会话构造器 —— **只从真实数据源构造**。
 *
 * 页面地址一律来自源脚本对章节的解析结果（[createLiveSession] 的 `pages`）。
 *
 * ⚠️ 历史遗留已清除：这里曾有一个 `createSampleSession()`，用 8 张硬编码的
 * Unsplash 通用图伪造整章页面，并在「章节图片解析失败」时被**静默回退**使用
 * ——结果用户会读到与作品完全无关的图片，且不报错、难以察觉。
 * 现在解析失败一律如实上报错误，**绝不伪造内容**。
 */
object ReaderSessionFactory {

    fun createLiveSession(
        comicId: String,
        comicTitle: String,
        coverUrl: String,
        chapterId: String,
        chapterTitle: String,
        pages: List<String>,
        initialPageIndex: Int = 0,
        sourceName: String = "",
        sourceKey: String = "",
        allChapters: List<Pair<String, String>>? = null,
        useOnImageLoad: Boolean = false
    ): ReaderSession {
        val mappedPages = pages.mapIndexed { idx, urlOrKey ->
            if (useOnImageLoad) {
                // 图片键模式：真实地址由阅读器逐页经源 JS onImageLoad 解析
                ComicPageSource.DynamicNetwork(
                    imageKey = urlOrKey,
                    pageIndex = idx,
                    sourceKey = sourceKey,
                    comicId = comicId,
                    epId = chapterId
                )
            } else {
                ComicPageSource.Network(
                    url = urlOrKey,
                    pageIndex = idx,
                    width = 1080,
                    height = 1600
                )
            }
        }

        val chaptersList = if (!allChapters.isNullOrEmpty()) {
            allChapters.map { (cId, cTitle) ->
                if (cId == chapterId) {
                    ReaderChapter(id = cId, title = cTitle, pages = mappedPages, isLoaded = true)
                } else {
                    ReaderChapter(id = cId, title = cTitle, pages = emptyList(), isLoaded = false)
                }
            }
        } else {
            listOf(ReaderChapter(id = chapterId, title = chapterTitle, pages = mappedPages, isLoaded = true))
        }

        val curIdx = chaptersList.indexOfFirst { it.id == chapterId }.coerceAtLeast(0)

        return ReaderSession(
            comicId = comicId,
            comicTitle = comicTitle,
            coverUrl = coverUrl,
            sourceName = sourceName,
            sourceKey = sourceKey,
            chapters = chaptersList,
            initialChapterIndex = curIdx,
            initialPageIndex = initialPageIndex
        )
    }
}
