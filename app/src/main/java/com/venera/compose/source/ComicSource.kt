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
     * 源声明的更新地址（JS 顶层 `url` 字段，对应官方 `this['temp'].url`）。
     * 为空表示该源不支持「按 URL 更新」。
     */
    val url: String
        get() = ""

    /**
     * 测试漫画源网络连通性与延迟（毫秒），失败返回 -1L
     */
    suspend fun ping(): Long

    /**
     * 关键字搜索漫画
     *
     * @param options 源声明的搜索筛选值（对齐官方 search.optionList / SearchOptions）；
     *                null 表示使用源默认值
     */
    suspend fun search(keyword: String, page: Int = 1, options: List<String>? = null): Result<List<Comic>>

    /**
     * 获取源声明的搜索筛选组（对齐官方 SearchPageData.searchOptions）。
     * 每组含 label 与 LinkedHashMap<key, 显示名>。空列表 = 该源无筛选。
     */
    suspend fun getSearchOptions(): List<com.venera.compose.source.model.SearchOptionGroup> = emptyList()

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
     * 获取源定义的探索页面列表
     */
    suspend fun getExplorePages(): Result<List<com.venera.compose.source.model.ExplorePageData>> =
        Result.success(emptyList())

    /**
     * 加载指定探索页面内容
     */
    suspend fun loadExplorePage(pageIndex: Int, page: Int = 1): Result<List<com.venera.compose.source.model.ExplorePagePart>> =
        Result.success(emptyList())

    /**
     * 获取源定义的分类页面元数据
     */
    suspend fun getCategoryData(): Result<com.venera.compose.source.model.CategoryData?> =
        Result.success(null)

    /**
     * 获取指定分类下的筛选选项
     */
    suspend fun getCategoryComicsOptions(category: String, param: String?): Result<List<com.venera.compose.source.model.CategoryComicsOption>> =
        Result.success(emptyList())

    /**
     * 加载指定分类下的真实漫画列表
     */
    suspend fun loadCategoryComics(
        category: String,
        param: String?,
        options: List<String>,
        page: Int = 1
    ): Result<com.venera.compose.source.model.CategoryComicsResult> =
        Result.success(com.venera.compose.source.model.CategoryComicsResult(emptyList()))

    /**
     * 加载排行榜真实漫画列表
     */
    suspend fun loadCategoryRanking(option: String, page: Int = 1): Result<List<Comic>> =
        Result.success(emptyList())

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

    /* ------------------------------------------------------------------ *
     * 网页登录（官方 account.loginWithWebview）
     * ------------------------------------------------------------------ */

    /**
     * 内嵌 WebView 当前 url/title 是否表示已登录成功。
     * 对应官方 `account.loginWithWebview.checkStatus(url, title)`。
     *
     * 官方在 WebView 的每次导航/标题变化时都会调用它。
     */
    suspend fun checkLoginStatus(url: String, title: String): Boolean = false

    /**
     * 网页登录成功后的源侧回调。
     * 对应官方 `account.loginWithWebview.onLoginSuccess()`。
     *
     * 源常在此处二次加工：把 cookie 搬到别的域（如 ehentai 把 forums 的 cookie
     * 改成 .exhentai.org），或从 `_localStorage` 里取 token 落库（如 ccc.js）。
     * **必须在 cookie/localStorage 已写入之后再调用。**
     */
    suspend fun onWebLoginSuccess(): Result<Unit> = Result.success(Unit)

    /* ------------------------------------------------------------------ *
     * Cookie 直填登录（官方 account.loginWithCookies）
     * ------------------------------------------------------------------ */

    /**
     * 需要用户手填的 cookie 字段名列表。
     * 对应官方 `account.loginWithCookies.fields`，为空表示该源不走此方式。
     */
    fun getCookieFields(): List<String> = emptyList()

    /**
     * 校验用户手填的 cookie 是否有效。
     * 对应官方 `account.loginWithCookies.validate(cookies)`。
     */
    suspend fun validateCookies(cookies: List<String>): Boolean = false

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

    /**
     * 网络收藏能力（需账号登录）。源在 JS 中声明 `favorites` 时由 JsComicSource 提供，
     * 否则为 null（该源不支持网络收藏）。对齐官方 ComicSource.favoriteData。
     */
    val favoriteData: FavoriteData?
        get() = null
}
