package com.venera.compose.data.db

/**
 * `favorite_images`（插图收藏）的读写层。
 *
 * 与 [ReadingStatsStore] 同一个理由：这张表的列名与可空性只有 `SchemaSql` 一份事实源，
 * 而调用方 `feature/favoriteimages/FavoriteImagesManager` 那边还有 `Bitmap` 落盘与
 * `filesDir` 这些 Android 才有的事。拆开后这一层不碰 Android 类型，
 * `:desktop:test` 能拿真库跑「写入 → 读回 → 备份导出行 → 恢复去重」。
 *
 * 可空性是逐列对过建表语句的：除 `local_path` 外全 `NOT NULL` ⇒ 文本走 [requiredString]；
 * `local_path` 本来可空（只存地址、没落盘的条目就是 null），读法保持改造前的 `?: ""`。
 */
class FavoriteImagesStore(private val source: SqlDatabaseSource) {

    /**
     * 新增一条收藏，回报新行 rowid。
     *
     * 改造前用的是 `SQLiteDatabase.insert`：失败不抛、只回 -1 并打一条日志，调用方
     * （`reader/VeneraReaderScreen.kt`）拿 `rowId < 0` 如实提示"没写进去"。
     * 这里换成立即抛，调用方的判据不用改 —— 捕获后仍回 -1，但库里少了一行"假装成功"的可能。
     */
    fun insert(
        comicId: String,
        comicTitle: String,
        sourceName: String,
        chapterTitle: String,
        pageIndex: Int,
        imageUrl: String,
        localPath: String,
        createdAt: Long,
    ): Long = source.writer().insert(
        INSERT_FAVORITE_IMAGE,
        comicId, comicTitle, sourceName, chapterTitle, pageIndex, imageUrl, localPath, createdAt
    )

    /** 这条地址是否已在收藏里（去重键，与恢复备份用的是同一个）。 */
    fun isFavorited(imageUrl: String): Boolean =
        source.reader().query(
            "SELECT id FROM favorite_images WHERE image_url = ? LIMIT 1", imageUrl
        ).isNotEmpty()

    /** 收藏墙要的全部行（含 `local_path` 与自增 id，两者都是本机状态），按创建时间倒序。 */
    fun allRows(): List<FavoriteImageRow> =
        source.reader().query("SELECT * FROM favorite_images ORDER BY created_at DESC")
            .map { row ->
                FavoriteImageRow(
                    id = row.long("id"),
                    comicId = row.requiredString("comic_id"),
                    comicTitle = row.requiredString("comic_title"),
                    sourceName = row.requiredString("source_name"),
                    chapterTitle = row.requiredString("chapter_title"),
                    pageIndex = row.long("page_index").toInt(),
                    imageUrl = row.requiredString("image_url"),
                    // 建表语句里这一列没带 NOT NULL，只存地址的条目为 null 是正常状态
                    localPath = row.string("local_path") ?: "",
                    createdAt = row.long("created_at"),
                )
            }

    /** 备份那一栏的七列（刻意不含 `id` 与 `local_path`，理由见调用方的类注释）。 */
    fun backupFields(): List<FavoriteImageBackupFields> =
        source.reader().query(BACKUP_SELECT).map { row ->
            FavoriteImageBackupFields(
                comicId = row.requiredString("comic_id"),
                comicTitle = row.requiredString("comic_title"),
                sourceName = row.requiredString("source_name"),
                chapterTitle = row.requiredString("chapter_title"),
                pageIndex = row.long("page_index").toInt(),
                imageUrl = row.requiredString("image_url"),
                createdAt = row.long("created_at"),
            )
        }

    /** 某一行的 `local_path`；行不存在给 null。 */
    fun localPathOf(id: Long): String? =
        source.writer().query("SELECT local_path FROM favorite_images WHERE id = ?", id)
            .firstOrNull()?.string("local_path")

    /** 一批 id 对应的 `local_path`（顺序不保证，只用来删文件）。 */
    fun localPathsOf(ids: List<Long>): List<String?> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        return source.writer().query(
            "SELECT local_path FROM favorite_images WHERE id IN ($placeholders)",
            *ids.toTypedArray()
        ).map { it.string("local_path") }
    }

    /**
     * 删一行，回报**库里本来有没有这一行**。
     *
     * 为什么要先查一次：门面的 `exec` 不回报影响行数，而调用方要把"删掉了"和
     * "没这条记录"如实分给 UI 看（改造前用的是 `SQLiteDatabase.delete` 的返回值）。
     */
    fun deleteById(id: Long): Boolean {
        val db = source.writer()
        val existed = db.query(
            "SELECT id FROM favorite_images WHERE id = ? LIMIT 1", id
        ).isNotEmpty()
        db.exec("DELETE FROM favorite_images WHERE id = ?", id)
        return existed
    }

    /** 一批 id 删掉，回报真正删掉的行数（0 要如实报，不能报"已移除"）。 */
    fun deleteByIds(ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        val db = source.writer()
        val placeholders = ids.joinToString(",") { "?" }
        val args = ids.toTypedArray()
        val matched = db.query(
            "SELECT id FROM favorite_images WHERE id IN ($placeholders)", *args
        ).size
        db.exec("DELETE FROM favorite_images WHERE id IN ($placeholders)", *args)
        return matched
    }

    /**
     * 恢复插图收藏（备份导入），回报**真正新增**的条数。
     *
     * 三条与改造前逐字对齐的口径：
     *  - `local_path` 一律写空串（归档里没有那份位图，收藏墙按 `image_url` 现加载）；
     *  - `created_at` 用归档里的原值，否则导入的这批会整体插到收藏墙最前；
     *  - 去重键是 `image_url`（本机已有的与这批内部重复都不再写）。
     *
     * 整笔在一句事务里；任一行写不下去 ⇒ [SqlDatabase.exec] 抛 → 整笔回滚 → 异常传出。
     * 改造前靠 `insert` 返回 -1 再手工抛 `SQLException` 达到同一效果，判据一致。
     */
    fun restoreBackupFields(rows: List<FavoriteImageBackupFields>): Int {
        if (rows.isEmpty()) return 0
        val db = source.writer()
        var added = 0
        db.inTransaction {
            val known = HashSet<String>()
            db.query("SELECT image_url FROM favorite_images")
                .forEach { row -> row.string("image_url")?.let { known += it } }
            for (row in rows) {
                if (!known.add(row.imageUrl)) continue
                db.exec(
                    INSERT_FAVORITE_IMAGE,
                    row.comicId, row.comicTitle, row.sourceName, row.chapterTitle,
                    row.pageIndex, row.imageUrl, "", row.createdAt,
                )
                added++
            }
        }
        return added
    }

    companion object {
        /** 列序与改造前 `ContentValues` 的 put 顺序一致。 */
        internal const val INSERT_FAVORITE_IMAGE =
            "INSERT INTO favorite_images " +
                "(comic_id, comic_title, source_name, chapter_title, page_index, image_url, " +
                "local_path, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"

        /**
         * 备份读那一栏的列清单。收成常量与 [INSERT_FAVORITE_IMAGE] 同一个做法：
         * 「不带本机自增 `id` 与 `local_path`」是这张表最要紧的备份口径（换台机器前者没有意义、
         * 后者指向一个不存在的文件），一句写死的 SQL 才让用例能把这条口径逐列断言住。
         */
        internal const val BACKUP_SELECT =
            "SELECT comic_id, comic_title, source_name, chapter_title, page_index, image_url, created_at" +
                " FROM favorite_images ORDER BY created_at DESC"
    }
}

/** 插图收藏的一行（`author` 不在表里，由调用方从本地真相反查后补上）。 */
data class FavoriteImageRow(
    val id: Long,
    val comicId: String,
    val comicTitle: String,
    val sourceName: String,
    val chapterTitle: String,
    val pageIndex: Int,
    val imageUrl: String,
    val localPath: String,
    val createdAt: Long,
)

/** 备份归档里那一行的七个字段（没有本机才成立的 `id` 与 `local_path`）。 */
data class FavoriteImageBackupFields(
    val comicId: String,
    val comicTitle: String,
    val sourceName: String,
    val chapterTitle: String,
    val pageIndex: Int,
    val imageUrl: String,
    val createdAt: Long,
)
