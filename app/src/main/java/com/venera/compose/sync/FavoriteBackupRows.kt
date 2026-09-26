package com.venera.compose.sync

import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.LocalFavoriteDatabase

/**
 * 备份归档里「收藏」那一栏的**编解码规则**（纯 JVM，不碰 SQLite、不碰 org.json）。
 *
 * 为什么单独抽出来：[BackupManager] 那一半要 Context + SQLite 才跑得动，而本项目单元测试
 * 没有 Robolectric（`app/build.gradle.kts` 里 test 依赖只有 junit），SQL 路径在 JVM 测试里
 * 根本执行不了。真正会**静默出错**的恰好是这一层 —— 字段名对不上不会抛异常，只会恢复出一批
 * 打开是空白条目的收藏。所以把判据放这里，能测的那部分全放这里。
 *
 * 两种行格式都要认，因为磁盘上的旧文件还在流通：
 * - **v4（现在导出的）**：每夹一表结构，字段名与 [FavoriteItem] 一致，外加 `folder` 标记这行属于哪个收藏夹；
 * - **v3 及更早**：已废弃单表 `comic_favorite` 的列名（`comic_id` / `title` / `cover_url` /
 *   `source_name` / `folder_name` / `created_at` 毫秒整数）。判据用「有 `comic_id` 且没有 `id`」，
 *   因为新旧两套列名互不重叠。不认旧格式的话，老备份文件在新版上导入直接失败
 *   （`id` 为空 → 整行被跳掉），用户看到的是"导入成功 0 条收藏"。
 */
internal object FavoriteBackupRows {

    /** 备份归档格式版本，写进 `meta.json`。 */
    const val CURRENT_VERSION = 4

    /** 一条收藏 → 备份行。 */
    fun encode(item: FavoriteItem, folder: String): Map<String, Any?> = mapOf(
        "folder" to folder,
        "id" to item.id,
        "name" to item.name,
        "author" to item.author,
        "sourceKey" to item.sourceKey,
        "tags" to item.tags,
        "coverPath" to item.coverPath,
        "time" to item.time,
        "displayOrder" to item.displayOrder,
    )

    /** 一条收藏在备份里的落点：夹名、条目本体、以及**有没有**显式排序值。 */
    data class Restored(val folder: String, val item: FavoriteItem, val order: Int?)

    /**
     * 备份行 → 落点。
     *
     * `order` 只在行里真带了 `displayOrder` 时才给值。老单表没有这一列，这时必须交 null 给
     * `addComic`，由它按「新收藏加到开头/末尾」的偏好顺序递增；若填 0，一次导入的所有条目
     * 会拿到同一个排序值，夹子里的顺序就变成读取顺序的副产品了。
     */
    fun decode(row: Map<String, Any?>): Restored =
        if (isLegacy(row)) decodeLegacy(row) else decodeCurrent(row)

    fun isLegacy(row: Map<String, Any?>): Boolean = row.containsKey("comic_id") && !row.containsKey("id")

    private fun decodeCurrent(row: Map<String, Any?>): Restored = Restored(
        folder = row.string("folder").ifBlank { LocalFavoriteDatabase.DEFAULT_FOLDER },
        item = FavoriteItem(
            id = row.string("id"),
            name = row.string("name"),
            author = row.string("author"),
            sourceKey = row.string("sourceKey"),
            tags = row.strings("tags"),
            coverPath = row.string("coverPath"),
            time = row.string("time").ifBlank { FavoriteItem.currentTimeString() },
            displayOrder = row.int("displayOrder"),
        ),
        order = if (row.containsKey("displayOrder")) row.int("displayOrder") else null,
    )

    private fun decodeLegacy(row: Map<String, Any?>): Restored {
        val createdAt = row.long("created_at")
        return Restored(
            folder = row.string("folder_name").ifBlank { LocalFavoriteDatabase.DEFAULT_FOLDER },
            item = FavoriteItem(
                id = row.string("comic_id"),
                name = row.string("title"),
                author = row.string("author"),
                // 主键的一半 type 就是 sourceKey.hashCode()，见 FavoriteItem.type
                sourceKey = row.string("source_name"),
                tags = row.strings("tags"),
                coverPath = row.string("cover_url"),
                time = if (createdAt > 0) FavoriteItem.currentTimeString(createdAt)
                else FavoriteItem.currentTimeString(),
            ),
            order = null,
        )
    }

    private fun Map<String, Any?>.string(key: String): String = this[key] as? String ?: ""

    /**
     * 标签串。JSON 里是数组；v3 的单表把它存成了一个字符串（`,` 或换行分隔）。
     * 两种都得拆，否则旧备份恢复出来的收藏在详情页显示不出标签。
     */
    private fun Map<String, Any?>.strings(key: String): List<String> {
        return when (val value = this[key]) {
            is List<*> -> value.mapNotNull { it?.toString() }.filter { it.isNotBlank() }
            is String -> value.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }

    /**
     * 整数读数。JSON 的数字反序列化回来是 `Long`（android org.json），
     * 手工写的备份也可能是 `Int`/`Double`/`String`，全部收拢到 `Int`。
     */
    private fun Map<String, Any?>.int(key: String): Int = long(key).toInt()

    private fun Map<String, Any?>.long(key: String): Long = when (val value = this[key]) {
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull() ?: 0L
        else -> 0L
    }
}
