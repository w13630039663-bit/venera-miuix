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

    /**
     * 获取源的动态设置项列表（用于源管理页面渲染与配置）
     */
    fun getSettings(): List<com.venera.compose.feature.sourcemanage.SourceSettingItem> = emptyList()

    /**
     * 保存源的单项配置
     */
    fun saveSetting(key: String, value: Any) {}

    /**
     * 执行 callback 类型的源设置操作（例如刷新域名列表）
     */
    suspend fun executeSettingCallback(key: String): Result<Unit> = Result.success(Unit)

    /**
     * 获取账号管理状态
     */
    fun getAccountInfo(): com.venera.compose.feature.sourcemanage.SourceAccountInfo =
        com.venera.compose.feature.sourcemanage.SourceAccountInfo()

    /**
     * 账号登录
     */
    suspend fun login(username: String, password: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("当前源暂不支持密码登录"))

    /**
     * 重新登录（若凭证已保存）
     */
    suspend fun relogin(): Result<Boolean> =
        Result.failure(UnsupportedOperationException("暂无保存登录凭据"))

    /**
     * 退出登录
     */
    suspend fun logout(): Result<Boolean> = Result.success(true)
}
