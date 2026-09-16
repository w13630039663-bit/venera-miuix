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
 * 阅读模式
 */
enum class ReaderReadingMode(val label: String, val icon: String) {
    VERTICAL_CONTINUOUS("条漫·连续滚动", "📜"),
    HORIZONTAL_PAGE("日漫·单页翻页", "📖")
}

/**
 * 章节数据载体
 */
data class ReaderChapter(
    val id: String,
    val title: String,
    val pages: List<ComicPageSource>
)

/**
 * 活跃阅读会话
 */
data class ReaderSession(
    val comicId: String,
    val comicTitle: String,
    val coverUrl: String,
    val chapters: List<ReaderChapter>,
    val initialChapterIndex: Int = 0,
    val initialPageIndex: Int = 0
)

/**
 * 生成演示/测试章节页面数据
 */
object SampleReaderData {
    private val sampleUrls = listOf(
        "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1080&q=80",
        "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1080&q=80",
        "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=1080&q=80",
        "https://images.unsplash.com/photo-1563089145-599997674d42?w=1080&q=80",
        "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1080&q=80",
        "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=1080&q=80",
        "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=1080&q=80",
        "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=1080&q=80"
    )

    fun createSampleSession(
        comicId: String,
        comicTitle: String,
        coverUrl: String,
        chapterNames: List<String>,
        initialIndex: Int = 0,
        initialPageIndex: Int = 0
    ): ReaderSession {
        val chapters = chapterNames.mapIndexed { cIndex, cTitle ->
            val pageCount = 12 + (cIndex % 5) * 4
            val pages = (0 until pageCount).map { pIndex ->
                val url = sampleUrls[(cIndex * 3 + pIndex) % sampleUrls.size]
                ComicPageSource.Network(
                    url = url,
                    pageIndex = pIndex,
                    width = 1080,
                    height = if (pIndex % 4 == 0) 3200 else 1600
                )
            }
            ReaderChapter(
                id = "ch_${cIndex + 1}",
                title = cTitle,
                pages = pages
            )
        }

        return ReaderSession(
            comicId = comicId,
            comicTitle = comicTitle,
            coverUrl = coverUrl,
            chapters = chapters,
            initialChapterIndex = initialIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)),
            initialPageIndex = initialPageIndex
        )
    }

    fun createLiveSession(
        comicId: String,
        comicTitle: String,
        coverUrl: String,
        chapterId: String,
        chapterTitle: String,
        pages: List<String>,
        initialPageIndex: Int = 0
    ): ReaderSession {
        val chapter = ReaderChapter(
            id = chapterId,
            title = chapterTitle,
            pages = pages.mapIndexed { idx, url ->
                ComicPageSource.Network(
                    url = url,
                    pageIndex = idx,
                    width = 1080,
                    height = 1600
                )
            }
        )
        return ReaderSession(
            comicId = comicId,
            comicTitle = comicTitle,
            coverUrl = coverUrl,
            chapters = listOf(chapter),
            initialChapterIndex = 0,
            initialPageIndex = initialPageIndex
        )
    }
}