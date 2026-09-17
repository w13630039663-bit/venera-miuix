package com.venera.compose.data.db

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地收藏条目（对齐原版 `foundation/favorites.dart` 的 `FavoriteItem`）。
 *
 * 官方字段：`id / name / author / type / tags / coverPath / time`。
 * 我们额外保留 [sourceKey] 文本（见 `LocalFavoriteDatabase` 头部说明：
 * 官方靠 `ComicType` 反查源，而 JVM 与 Dart 的哈希算法不同，无法复刻）。
 */
data class FavoriteItem(
    val id: String,
    val name: String,
    val author: String = "",
    val sourceKey: String = "",
    val tags: List<String> = emptyList(),
    val coverPath: String = "",
    /** 收藏时间，格式 `yyyy-MM-dd HH:mm:ss`（对应官方 `_getTimeString`）。 */
    val time: String = currentTimeString(),
    /** 手动排序值，对应官方 `display_order`。默认 0，加入时由 Manager 计算。 */
    val displayOrder: Int = 0,
) {
    /** 对应官方 `FavoriteItem.type`（`ComicType.value` = 源 key 的哈希）。 */
    val type: Int get() = sourceKey.hashCode()

    /** 官方 `FavoriteItem.description`：来源名 + 收藏日期（前 10 位）。 */
    val description: String
        get() = "$sourceKey | ${time.take(10)}"

    fun toJson(): Map<String, Any?> = mapOf(
        "name" to name,
        "author" to author,
        "sourceKey" to sourceKey,
        "tags" to tags,
        "id" to id,
        "coverPath" to coverPath,
    )

    companion object {
        private val TIME_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        fun currentTimeString(time: Long = System.currentTimeMillis()): String =
            TIME_FMT.format(Date(time))

        fun parseTime(text: String?): Long = try {
            if (text.isNullOrBlank()) 0L else TIME_FMT.parse(text)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }

        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: Map<String, Any?>): FavoriteItem = FavoriteItem(
            id = json["id"]?.toString() ?: json["target"]?.toString() ?: "",
            name = json["name"]?.toString() ?: "",
            author = json["author"]?.toString() ?: "",
            sourceKey = json["sourceKey"]?.toString() ?: "",
            tags = (json["tags"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            coverPath = json["coverPath"]?.toString() ?: "",
        )
    }
}

/**
 * 带追更信息的收藏条目（对齐官方 `FavoriteItemWithUpdateInfo`）。
 *
 * @param updateTime 最近一次**服务端**给出的更新时间（官方 `last_update_time`）
 * @param hasNewUpdate 是否有新章节未读
 * @param lastCheckTime 上次检查时间戳（毫秒），用于节流
 */
data class FavoriteItemWithUpdateInfo(
    val item: FavoriteItem,
    val updateTime: String? = null,
    val hasNewUpdate: Boolean = false,
    val lastCheckTime: Long? = null,
) {
    /** 官方 `FavoriteItemWithUpdateInfo.description`：`更新时间 | 来源`。 */
    val description: String
        get() = "${updateTime ?: "Unknown"} | ${item.sourceKey}"
}
