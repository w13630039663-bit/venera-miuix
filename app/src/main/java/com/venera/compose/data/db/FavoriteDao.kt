package com.venera.compose.data.db

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FavoriteRecord(
    val comicId: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val sourceName: String,
    val folderName: String = "默认",
    val tags: String = "",
    val hasUpdate: Boolean = false,
    val latestChapter: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 旧 `comic_favorite` 单表的读写层（v2 起主键为 `(comic_id, source_name)`）。
 *
 * 本轮改造点：以前它吃 `VeneraDatabase`（`SQLiteOpenHelper`）+ `ContentValues` +
 * `db.query/insert/delete`；现在吃 [SqlDatabaseSource]，SQL 逐条写成显式语句，
 * 值一律参数绑定。列的读取顺序与建表语句逐列对齐，`has_update` 仍按「整数 1 才算真」判定。
 */
class FavoriteDao(private val source: SqlDatabaseSource) {

    private val _favoritesFlow = MutableStateFlow<List<FavoriteRecord>>(emptyList())
    val favoritesFlow: StateFlow<List<FavoriteRecord>> = _favoritesFlow.asStateFlow()

    /**
     * 全表读取一律放到 IO 线程（原实现是 init 里同步查全表，
     * 而 DAO 是在 Composable 里 remember 出来的 ⇒ 冷启动主线程扫库）。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        refresh()
    }

    fun refresh() {
        scope.launch { _favoritesFlow.value = getAllFavoritesSync() }
    }

    private fun getAllFavoritesSync(folderName: String? = null): List<FavoriteRecord> {
        val rows = if (folderName != null) {
            source.reader().query(
                "SELECT * FROM comic_favorite WHERE folder_name = ? ORDER BY created_at DESC",
                folderName
            )
        } else {
            source.reader().query("SELECT * FROM comic_favorite ORDER BY created_at DESC")
        }
        return rows.map {
            FavoriteRecord(
                comicId = it.requiredString("comic_id"),
                title = it.requiredString("title"),
                author = it.string("author") ?: "",
                coverUrl = it.requiredString("cover_url"),
                sourceName = it.requiredString("source_name"),
                folderName = it.requiredString("folder_name"),
                tags = it.string("tags") ?: "",
                hasUpdate = it.long("has_update") == 1L,
                latestChapter = it.string("latest_chapter") ?: "",
                createdAt = it.long("created_at")
            )
        }
    }

    suspend fun addFavorite(record: FavoriteRecord) = withContext(Dispatchers.IO) {
        source.writer().exec(INSERT_OR_REPLACE, *record.toBindArgs())
        refresh()
    }

    suspend fun removeFavorite(comicId: String) = withContext(Dispatchers.IO) {
        source.writer().exec("DELETE FROM comic_favorite WHERE comic_id = ?", comicId)
        refresh()
    }

    fun toggleFavorite(
        comicId: String,
        title: String,
        coverUrl: String,
        author: String = "",
        sourceName: String = "拷贝漫画",
        latestChapter: String = "",
        folderName: String = "默认"
    ) {
        val currentFavs = _favoritesFlow.value
        val exists = currentFavs.any { it.comicId == comicId }
        val db = source.writer()
        if (exists) {
            db.exec("DELETE FROM comic_favorite WHERE comic_id = ?", comicId)
        } else {
            db.exec(
                INSERT_OR_REPLACE,
                comicId, title, author, coverUrl, sourceName, folderName, "", 0, latestChapter,
                System.currentTimeMillis()
            )
        }
        refresh()
    }

    suspend fun isFavorite(comicId: String): Boolean = withContext(Dispatchers.IO) {
        source.reader().query(
            "SELECT 1 FROM comic_favorite WHERE comic_id = ? LIMIT 1",
            comicId
        ).isNotEmpty()
    }

    suspend fun getFolders(): List<String> = withContext(Dispatchers.IO) {
        val folders = mutableListOf("默认")
        source.reader().query(
            "SELECT DISTINCT folder_name FROM comic_favorite ORDER BY folder_name ASC"
        ).forEach {
            val f = it.string("folder_name")
            if (f != null && !folders.contains(f)) {
                folders.add(f)
            }
        }
        folders
    }

    /** 与原 `ContentValues` 的 put 顺序一致；`has_update` 仍旧写 1/0 整数。 */
    private fun FavoriteRecord.toBindArgs(): Array<Any?> = arrayOf(
        comicId, title, author, coverUrl, sourceName, folderName, tags,
        if (hasUpdate) 1 else 0, latestChapter, createdAt
    )

    companion object {
        /** 原 `insertWithOnConflict(..., CONFLICT_REPLACE)` 的等价语句。 */
        private const val INSERT_OR_REPLACE =
            "INSERT OR REPLACE INTO comic_favorite " +
                "(comic_id, title, author, cover_url, source_name, folder_name, tags, has_update, latest_chapter, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

        @Volatile
        private var INSTANCE: FavoriteDao? = null

        /**
         * 兼容既有调用点的取用写法（各 UI / ViewModel 递的是 Android 的 Context）。
         *
         * 参数类型只能是 `Any`：本层不许再出现 Android 的 Context 类型，
         * 句柄原样递给平台装的 factory，由它负责解释（Android 侧不是 Context 就抛）。
         * 调用点在 4b / Task 5 收口后可换成本 DAO + [DatabasePorts] 直接构造。
         */
        fun getInstance(context: Any): FavoriteDao {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FavoriteDao(DatabasePorts.of(context).core).also { INSTANCE = it }
            }
        }
    }
}
