package com.venera.compose.sync

import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryArtistFollow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 备份归档里画廊那一侧的编解码（纯 JVM，不碰磁盘、不碰 Context）。
 *
 * 抽出来的必要性和 [FavoriteBackupRows] 同一条：这一层出错**不抛异常**，字段名对不上只是
 * 恢复出来少一块信息，屏上看着就是一条"不知道为什么没封面"的收藏。能测的部分全放这里。
 *
 * ## 为什么直接复用 `@Serializable` 类，而不是像收藏那样手写一张列名映射表
 *
 * 画廊那两份数据的磁盘格式**本来就是** [GalleryFavorite] 这个类（`filesDir/gallery_favorites.json`
 * 由 kotlinx.serialization 直接落盘）。归档再造一套 22 字段的映射表，唯一结果是两处口径可以各自漂移，
 * 而漂移的征兆没有任何一条错误会指向它。所以这里用的就是那一份定义，字段名与磁盘档逐字一致。
 *
 * ## 缺档与坏档是两件事
 *
 * - member 不存在（v4 及更早的归档）⇒ 交回空列表，那本来就没有这一栏；
 * - member 存在但解不开 ⇒ **抛出去**，让整笔导入在动库之前失败。
 *   静默当成"这份包里没有画廊数据"，用户看到的就是"导入成功，但一条也没多"。
 */
internal object GalleryBackupRows {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
    }

    /**
     * 归档里的一行关注记录。字段名与 `gallery_artist_follows.json` 磁盘档一致
     * （那位 `ArtistFollowEntry` 是文件私有的，跨包复用它要把它对外开放，不值得）。
     */
    @Serializable
    private data class FollowRecord(
        @SerialName("site") val siteKey: String,
        @SerialName("name") val name: String,
        @SerialName("added_at") val addedAt: Long = 0L,
    )

    fun encodeFollows(follows: List<GalleryArtistFollow>): String =
        json.encodeToString(follows.map { FollowRecord(it.site.routeKey, it.name, it.addedAt) })

    /** 站点键认不出、名字为空的行为摘掉：留着就是一格永远点不开也删不掉的位置。 */
    fun decodeFollows(raw: String?): List<GalleryArtistFollow> {
        if (raw.isNullOrBlank()) return emptyList()
        val records = json.decodeFromString<List<FollowRecord>>(raw)
        return records.mapNotNull { record ->
            GallerySite.fromRouteKey(record.siteKey)
                ?.takeIf { record.name.isNotBlank() }
                ?.let { GalleryArtistFollow(it, record.name, record.addedAt) }
        }
    }

    fun encodeFavorites(favorites: List<GalleryFavorite>): String = json.encodeToString(favorites)

    fun decodeFavorites(raw: String?): List<GalleryFavorite> {
        if (raw.isNullOrBlank()) return emptyList()
        // 与 GalleryFavoritesStore.readFromDisk 同一条判据：认不出站点键的行当场摘掉。
        return json.decodeFromString<List<GalleryFavorite>>(raw).filter { it.site != null && it.id != 0L }
    }
}
