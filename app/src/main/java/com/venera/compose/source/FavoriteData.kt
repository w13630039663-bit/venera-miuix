package com.venera.compose.source

import com.venera.compose.source.model.Comic

/**
 * 网络收藏「按页」结果。对齐官方 `favorites.loadComics` 的 `Res(comics, subData: maxPage)`。
 */
data class FavComicPage(
    val comics: List<Comic>,
    val maxPage: Int,
)

/**
 * 网络收藏「游标翻页」结果。对齐官方 `favorites.loadNext` 的 `Res(comics, subData: next)`。
 *
 * [next] 为 null 表示已到最后一页；首屏也由 `loadNext(null, folder)` 返回
 * （ehentai 等源没有 `loadComics`，只声明 `loadNext`）。
 */
data class FavComicNext(
    val comics: List<Comic>,
    val next: String?,
)

/**
 * 网络收藏文件夹列表结果。对齐官方 `Res(folders, subData: favorited)`。
 */
data class FavFolders(
    val folders: Map<String, String>,
    val favorited: List<String>,
)

/**
 * 单个漫画源的网络收藏能力。由源 JS 的 `favorites` 对象生成；
 * 源未声明 `favorites` 时该值为 null（该源不支持网络收藏）。
 *
 * 字段与官方 `FavoriteData` 逐项对应（见
 * `.reference/flutter-master/lib/foundation/comic_source/parser.dart:_loadFavoriteData`）：
 * - [loadComic] 来自 `favorites.loadComics(page, folder)`，返回 comics + maxPage
 * - [loadNext] 来自 `favorites.loadNext(next, folder)`，返回 comics + next 游标
 *   （两者由官方 `_checkExists` 分别探测，可单独存在，通常至少有一个）
 * - [loadFolders]/[addFolder]/[deleteFolder] 仅 `multiFolder` 源且源已声明时非空
 * - 所有调用内建 `retryZone`：未登录返回 Not login，遇 `Login expired` 自动重登重试一次
 */
data class FavoriteData(
    /** 源 key（如 "jm"），也是官方 favoriteData.key */
    val key: String,
    /** 展示名，来自源 name */
    val title: String,
    /** 是否多文件夹（决定是否先列出文件夹） */
    val multiFolder: Boolean,
    /** 单本漫画是否只能在一个文件夹（官方 singleFolderForSingleComic） */
    val singleFolderForSingleComic: Boolean = false,
    /** 旧→新排序（官方 isOldToNewSort） */
    val isOldToNewSort: Boolean = false,
    /** 全收藏统一 id（部分源有；本仓库源脚本未声明，恒为 null） */
    val allFavoritesId: String? = null,
    /** 加载第 page 页（来自 `favorites.loadComics`）；源未声明该函数时为 null */
    val loadComic: (suspend (page: Int, folderId: String?) -> Result<FavComicPage>)? = null,
    /** 游标翻页（来自 `favorites.loadNext`）；源未声明该函数时为 null。next=null 表示首屏 */
    val loadNext: (suspend (next: String?, folderId: String?) -> Result<FavComicNext>)? = null,
    /** 加载文件夹列表（仅 multiFolder）；comicId 非 null 时返回该漫画所在文件夹 */
    val loadFolders: (suspend (comicId: String?) -> Result<FavFolders>)? = null,
    /** 新建文件夹（仅 multiFolder 且源支持） */
    val addFolder: (suspend (name: String) -> Result<Unit>)? = null,
    /** 删除文件夹（仅 multiFolder 且源支持） */
    val deleteFolder: (suspend (folderId: String) -> Result<Unit>)? = null,
    /** 增删收藏；favoriteId 在删除时用于定位特定收藏项 */
    val addOrDelFavorite: suspend (comicId: String, folderId: String, isAdding: Boolean, favoriteId: String?) -> Result<Unit>,
)
