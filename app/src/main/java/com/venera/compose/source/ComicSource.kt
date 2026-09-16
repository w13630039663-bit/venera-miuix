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
}
