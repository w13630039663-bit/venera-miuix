package com.venera.compose.source

import com.venera.compose.source.model.ChapterPages
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ComicDetails

/**
 * 漫画源核心抽象接口
 */
interface ComicSource {
    val key: String
    val name: String
    val version: String
    val iconUrl: String?
        get() = null

    /**
     * 测试漫画源网络连通性与延迟（毫秒），失败返回 -1L
     */
    suspend fun ping(): Long

    /**
     * 关键字搜索漫画
     */
    suspend fun search(keyword: String, page: Int = 1): Result<List<Comic>>

    /**
     * 获取漫画完整详情及章节列表
     */
    suspend fun getComicDetails(comicId: String): Result<ComicDetails>

    /**
     * 获取指定章节全量高清图片链接
     */
    suspend fun getChapterPages(comicId: String, chapterId: String): Result<ChapterPages>

    /**
     * 获取探索/热门推荐漫画流
     */
    suspend fun getExploreComics(page: Int = 1): Result<List<Comic>>

    /**
     * 加载漫画详情评论列表
     */
    suspend fun loadComments(comicId: String, subId: String? = null, page: Int = 1): Result<List<com.venera.compose.source.model.Comment>> =
        Result.success(emptyList())

    /**
     * 发送漫画详情评论
     */
    suspend fun sendComment(comicId: String, subId: String? = null, content: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("当前源暂不支持发送评论"))

    /**
     * 加载章节单话评论列表
     */
    suspend fun loadChapterComments(comicId: String, chapterId: String, page: Int = 1): Result<List<com.venera.compose.source.model.Comment>> =
        Result.success(emptyList())

    /**
     * 发送章节单话评论
     */
    suspend fun sendChapterComment(comicId: String, chapterId: String, content: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("当前源暂不支持发送章节评论"))

    /**
     * 给评论点赞
     */
    suspend fun likeComment(commentId: String, comicId: String): Result<Boolean> =
        Result.success(true)

    /**
     * 给评论投票（顶/踩）
     */
    suspend fun voteComment(commentId: String, isUpvote: Boolean, comicId: String): Result<Boolean> =
        Result.success(true)

    /**
     * 漫画打星评分 (0.0 - 5.0)
     */
    suspend fun starRating(comicId: String, rating: Float): Result<Boolean> =
        Result.success(true)

    /**
     * 漫画作品点赞
     */
    suspend fun likeComic(comicId: String): Result<Boolean> =
        Result.success(true)
}
