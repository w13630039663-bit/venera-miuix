package com.venera.compose.source.model

/**
 * 通用漫画精简元数据（用于列表展示、搜索结果、收藏条目等）
 */
data class Comic(
    val id: String,
    val title: String,
    val subTitle: String = "",
    val cover: String = "",
    val sourceKey: String = "",
    val tags: List<String> = emptyList(),
    val description: String = "",
    val updateTime: String = "",
    val isFavorite: Boolean = false
)

/**
 * 漫画单话/单卷元数据
 */
data class ComicChapter(
    val id: String,
    val title: String,
    val order: Int = 0,
    val updatedAt: String = ""
)

/**
 * 漫画完整详情数据（用于漫画详情页）
 */
data class ComicDetails(
    val comic: Comic,
    val author: String = "",
    val status: String = "连载中",
    val rating: Float = 0f,
    val chapters: List<ComicChapter> = emptyList(),
    val thumbnails: List<String> = emptyList(),
    val sourceKey: String = comic.sourceKey
)

/**
 * 章节全量漫画图片列表（用于阅读器）
 */
data class ChapterPages(
    val comicId: String,
    val chapterId: String,
    val chapterTitle: String = "",
    val pages: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap()
)
