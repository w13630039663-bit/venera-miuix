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
    val updatedAt: String = "",
    val group: String = "默认",
    val isRead: Boolean = false
)

/**
 * 章节分组（多卷、连载中、单行本、番外篇等）
 */
data class ChapterGroup(
    val name: String,
    val chapters: List<ComicChapter>
)

/**
 * 漫画详情页评论实体（对齐 venera-init.js 中的 Comment 构造函数）
 */
data class Comment(
    val id: String = "",
    val userName: String,
    val avatar: String? = null,
    val content: String,
    val time: String? = null,
    val replyCount: Int = 0,
    val isLiked: Boolean = false,
    val score: Int = 0,
    val voteStatus: Int = 0 // 1: upvote, -1: downvote, 0: none
)

/**
 * 漫画完整详情数据（29 字段全量覆盖对齐原版）
 */
data class ComicDetails(
    val comic: Comic,
    val author: String = "",
    val status: String = "连载中",
    val rating: Float = 0f,
    val chapters: List<ComicChapter> = emptyList(),
    val chapterGroups: List<ChapterGroup> = emptyList(),
    val thumbnails: List<String> = emptyList(),
    val recommend: List<Comic> = emptyList(),
    val tagMap: Map<String, List<String>> = emptyMap(), // 带有命名空间的标签（如 "作者" -> ["..."], "题材" -> ["..."]）
    val uploader: String = "",
    val uploadTime: String = "",
    val updateTime: String = comic.updateTime,
    val url: String = "",
    val stars: Float = rating,
    val maxPage: Int = 1,
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val isFavorite: Boolean = comic.isFavorite,
    val commentCount: Int = 0,
    val comments: List<Comment> = emptyList(),
    val subId: String? = null,
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
