package com.venera.compose.data.api

import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.download.DownloadTask
import com.venera.compose.download.LocalChapter
import com.venera.compose.download.LocalComic
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * 本地收藏库（贴在 `data/db/LocalFavoritesManager` 上）。
 *
 * **宽度按消费面实数**：那颗实现类公开面 68 枚，UI 八颗文件一共只摸到 **20** 枚
 * （复算 `_qa/b5-surface.mjs`；单是 `feature/FavoritesViewModel.kt` 就吃 19 枚，其余七颗加起来只多出
 * `folders` / `isExist` / `getAllComics` / `version` 这几枚）。没一枚是为「看起来完整」加的。
 *
 * ## 为什么这批不必等 W1
 *
 * 返回类型逐颗查过声明地：[FavoriteItem] 在 `data/db/FavoriteModels.kt:14`，
 * `DownloadTask` / `LocalComic` / `LocalChapter` 在 `download/DownloadModels.kt` —— 全在下层，
 * 所以这颗文件的签名不需要 import 任何 `feature.*`。这与 §七.6 那颗 `FavoriteImageItem`
 * （声明在 `feature/favoriteimages/FavoriteImagesManager.kt:92`，必须等搬家）不是一回事。
 *
 * ## 签名照抄的三枚默认值不许改
 *
 * `addComic` 的 `order` / `updateTime` 与 `createFolder` 的 `renameWhenInvalidName` 都有默认值，
 * 调用点靠**不传**它们取默认行为。适配器少抄一枚默认值不是编译错，是**行为错**
 * （变成必须显式传参 ⇒ 有人图省事直接传 `null`/`false` 之外的值）。
 */
interface FavoriteLibrary {

    val folders: StateFlow<List<String>>
    val counts: StateFlow<Map<String, Int>>

    /** 库版本号：收藏页靠它判断要不要重读一遍列表。 */
    val version: StateFlow<Int>

    suspend fun getAllComics(): List<FavoriteItem>

    suspend fun getFolderComics(folder: String): List<FavoriteItem>

    suspend fun search(keyword: String): List<FavoriteItem>

    suspend fun searchInFolder(folder: String, keyword: String): List<FavoriteItem>

    /** 这条漫画被哪些夹收了；没收给空表。 */
    suspend fun find(id: String, sourceKey: String): List<String>

    suspend fun isExist(id: String, sourceKey: String): Boolean

    suspend fun addComic(
        folder: String,
        comic: FavoriteItem,
        order: Int? = null,
        updateTime: String? = null,
    ): Boolean

    suspend fun deleteComicWithId(folder: String, id: String, sourceKey: String)

    suspend fun batchDeleteComics(folder: String, comics: List<FavoriteItem>)

    suspend fun batchDeleteComicsInAllFolders(comics: List<FavoriteItem>)

    suspend fun batchMoveFavorites(sourceFolder: String, targetFolder: String, comics: List<FavoriteItem>)

    suspend fun batchCopyFavorites(sourceFolder: String, targetFolder: String, comics: List<FavoriteItem>)

    suspend fun createFolder(name: String, renameWhenInvalidName: Boolean = false): String

    suspend fun deleteFolder(name: String)

    suspend fun rename(before: String, after: String)

    suspend fun updateOrder(folders: List<String>)

    suspend fun reorder(newFolder: List<FavoriteItem>, folder: String)

    /**
     * 把一个夹登记为追更夹并按需清表（`clearData` 默认 `true` 是调用点依赖的行为）。
     *
     * 这枚差点漏收：调用点写的是**跨行内联链**（`…getInstance(app)` 换行再 `.prepareTableForFollowUpdates(…)`），
     * 单行判式扫不到 —— 是编译器报 `Unresolved reference` 才抓回来的。
     */
    suspend fun prepareTableForFollowUpdates(table: String, clearData: Boolean = true)
}

/**
 * 下载队列（贴在 `download/DownloadManager` 上，公开面 42 枚 → UI 吃到 11 枚）。
 *
 * ⚠️ **不收 `chapterOffline`**：它返回的 `ChapterOffline` 是 `download/ChapterCompleteness.kt:4` 的
 * `internal enum` —— 公开签名不许暴露 internal 类型，要么把枚举提成 public、要么把契约降级成 internal，
 * 两个方向都是「为了缩名单而放宽封装」。因此 `feature/ComicDetailScreen.kt` 那一行**整颗留在白名单**
 * （它三枚都用得到，不在同一颗文件里混两种形状），解锁条件是那条三档判据本身下沉成 public 判据。
 */
interface DownloadQueue {

    val tasks: StateFlow<List<DownloadTask>>

    fun enqueue(
        sourceKey: String,
        comicId: String,
        comicTitle: String,
        comicCover: String,
        chapters: List<com.venera.compose.source.model.ComicChapter>,
    )

    fun pause(taskId: String)

    fun resume(taskId: String)

    fun pauseAll()

    fun resumeAll()

    fun clearCompleted()

    /** `deleteFiles` 的默认值 `true` 是调用点依赖的行为，不许漏抄。 */
    fun delete(taskId: String, deleteFiles: Boolean = true)

    fun isChapterDownloaded(sourceKey: String, comicId: String, chapterId: String): Boolean

    fun getDownloadedChapterFiles(sourceKey: String, comicId: String, chapterId: String): List<File>?

    /** 换存储根时把已有任务搬到新根；给搬动的条数。 */
    fun relocateTasks(oldRoot: File, newRoot: File): Int
}

/**
 * 本地漫画库（贴在 `download/LocalComicManager` 上，公开面 7 枚 → UI 吃到 5 枚）。
 *
 * 收口只换「从哪拿这颗对象」，不换任何调度：那颗类体里的 `withContext(Dispatchers.IO)` 留在实现侧，
 * 与改造前逐点相同。
 */
interface LocalComicLibrary {

    suspend fun getLocalComics(): List<LocalComic>

    suspend fun getLocalChapters(comic: LocalComic): List<LocalChapter>

    suspend fun deleteLocalComic(comic: LocalComic): Boolean

    suspend fun exportToCbz(
        comic: LocalComic,
        targetFile: File,
        onProgress: (Float) -> Unit = {},
    ): Result<File>

    suspend fun importCbz(archiveFile: File, customTitle: String? = null): Result<LocalComic>
}
