package com.venera.compose.data.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ComicSourceRecord(
    val sourceId: String,
    val name: String,
    val version: String,
    val iconUrl: String = "",
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
    val configJson: String = "{}"
)

/**
 * 漫画源元数据（`comic_source`）的读写层。
 *
 * 本轮改造点同 `FavoriteDao`：连接从 [SqlDatabaseSource] 现取，`ContentValues` 换成
 * 参数绑定的显式语句。首次运行播种五条默认源的口径（条数、顺序、启用状态）一字未动。
 */
class ComicSourceDao(private val source: SqlDatabaseSource) {

    private val _sourcesFlow = MutableStateFlow<List<ComicSourceRecord>>(emptyList())
    val sourcesFlow: StateFlow<List<ComicSourceRecord>> = _sourcesFlow.asStateFlow()

    init {
        checkAndSeedDefaults()
        refresh()
    }

    private fun checkAndSeedDefaults() {
        val db = source.writer()
        // 原来的写法是 `getInt(0)` 按位置读；这里加了 AS 别名，因为 SqlRow 只按列名取值，
        // 计数结果本身没有变化。
        val count = db.query("SELECT COUNT(*) AS total FROM comic_source").firstOrNull()?.long("total") ?: 0L
        if (count == 0L) {
            val defaults = listOf(
                ComicSourceRecord("copymanga", "拷贝漫画", "1.0.0", "", true, 0),
                ComicSourceRecord("picacg", "哔咔漫画", "1.0.0", "", true, 1),
                ComicSourceRecord("mangadex", "MangaDex", "1.0.0", "", true, 2),
                ComicSourceRecord("ehentai", "E-Hentai", "1.0.0", "", false, 3),
                ComicSourceRecord("nhentai", "NHentai", "1.0.0", "", false, 4)
            )
            defaults.forEach { src ->
                db.exec(
                    INSERT,
                    src.sourceId, src.name, src.version, src.iconUrl,
                    if (src.isEnabled) 1 else 0, src.sortOrder, src.configJson
                )
            }
        }
    }

    fun refresh() {
        _sourcesFlow.value = source.reader().query(
            "SELECT * FROM comic_source ORDER BY sort_order ASC"
        ).map { row ->
            ComicSourceRecord(
                sourceId = row.requiredString("source_id"),
                name = row.requiredString("name"),
                version = row.requiredString("version"),
                iconUrl = row.string("icon_url") ?: "",
                isEnabled = row.long("is_enabled") == 1L,
                sortOrder = row.long("sort_order").toInt(),
                configJson = row.string("config_json") ?: "{}"
            )
        }
    }

    suspend fun toggleSource(sourceId: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        source.writer().exec(
            "UPDATE comic_source SET is_enabled = ? WHERE source_id = ?",
            if (isEnabled) 1 else 0, sourceId
        )
        refresh()
    }

    companion object {
        /** 原 `db.insert(...)`（冲突即失败）的等价语句。 */
        private const val INSERT =
            "INSERT INTO comic_source " +
                "(source_id, name, version, icon_url, is_enabled, sort_order, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)"

        @Volatile
        private var INSTANCE: ComicSourceDao? = null

        /**
         * 兼容既有调用点的取用写法（递进来的是 Android 的 Context）。
         * 参数只能是 `Any`、句柄由平台 factory 解释，理由见 `FavoriteDao.getInstance`。
         */
        fun getInstance(context: Any): ComicSourceDao {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ComicSourceDao(DatabasePorts.of(context).core).also { INSTANCE = it }
            }
        }
    }
}
