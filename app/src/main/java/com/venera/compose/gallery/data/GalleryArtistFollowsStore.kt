package com.venera.compose.gallery.data

import android.content.Context
import com.venera.compose.gallery.domain.GalleryArtistFollow
import com.venera.compose.gallery.domain.GalleryArtistFollows
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 磁盘上的一条关注记录。
 *
 * 与 domain 那个 [GalleryArtistFollow] 分开是有意的：磁盘格式要能向后兼容（加字段给默认值就行），
 * 而领域形状不该被磁盘绑住。这里存的也只有**站别 + 名字 + 关注时刻**三样。
 *
 * 刻意**不存** pixiv 用户编号和头像地址：那两个是会变的（实测拿旧编号去要头像，站方回
 * `error=true`；图片地址也是会过期的临时档）。存了就等于承诺"离线也能看到头像"，
 * 而那条承诺兑现不了 —— 所以每次面板打开现取。
 */
@Serializable
private data class ArtistFollowEntry(
    @SerialName("site") val siteKey: String,
    @SerialName("name") val name: String,
    @SerialName("added_at") val addedAt: Long = 0L,
)

/**
 * 画廊的**关注画师名单**。
 *
 * 语义按用户 2026-09-30 拍板的那一档：「名单 + 直达入口」—— 我们**不与站方发生任何关注关系**
 * （两站都没有匿名可写的关注端点），这份名单只在我们这一侧成立：它决定搜索卡上那一排入口，
 * 以及画师行上那颗胶囊的亮/灭。
 *
 * 存储照 [GalleryFavoritesStore] 同一份规格：
 * - 构造时**同步**读一次（名单只有几十条，收益是第一帧那排入口就是对的，不会先空一下再蹦出来）；
 * - 写盘先写 `*.tmp` 再改名（写到一半被杀不会把名单截成半个 JSON）；
 * - 解不开时**先把原文件改名留档**（`*.corrupt-<时间戳>`）再开空档，并通过 [notice] 说一句
 *   —— 直接覆盖等于把用户攒的名单删了。
 */
class GalleryArtistFollowsStore private constructor(private val context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    private val _follows = MutableStateFlow<List<GalleryArtistFollow>>(emptyList())

    /** 已按「关注时刻倒序、同刻按名字」排好的名单（排序口径在 [GalleryArtistFollows.sorted]）。 */
    val follows: StateFlow<List<GalleryArtistFollow>> = _follows.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        readFromDisk()
    }

    fun consumeNotice(): String? = _notice.value?.also { _notice.value = null }

    fun isFollowing(site: GallerySite, name: String): Boolean =
        GalleryArtistFollows.isFollowing(_follows.value, site, name)

    /** 关注/取消，返回**操作之后**的状态（true = 已关注），供 UI 决定说哪句话。 */
    suspend fun toggle(site: GallerySite, name: String): Boolean {
        val adding = !isFollowing(site, name)
        _follows.value = GalleryArtistFollows.sorted(
            if (adding) {
                GalleryArtistFollows.withAdded(
                    _follows.value,
                    GalleryArtistFollow(site, name, System.currentTimeMillis()),
                )
            } else {
                GalleryArtistFollows.removed(_follows.value, site, name)
            },
        )
        persist(_follows.value)
        return adding
    }

    private suspend fun persist(list: List<GalleryArtistFollow>) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(list.map { it.toEntry() }))
            if (!tmp.renameTo(file)) {
                tmp.delete()
                throw java.io.IOException("关注名单档改名失败：${file.absolutePath}")
            }
        }
    }

    private fun readFromDisk() {
        if (!file.exists()) return
        val parsed = runCatching { json.decodeFromString<List<ArtistFollowEntry>>(file.readText()) }.getOrNull()
        if (parsed != null) {
            // 站点键认不出的行当场摘掉（老档写过别的键、或手工改坏）：留着它，那一格永远点不开。
            val usable = parsed.mapNotNull { entry ->
                GallerySite.fromRouteKey(entry.siteKey)?.let { GalleryArtistFollow(it, entry.name, entry.addedAt) }
            }
            _follows.value = GalleryArtistFollows.sorted(GalleryArtistFollows.dedupe(usable))
            return
        }
        val backup = File(file.parentFile, "$FILE_NAME.corrupt-${System.currentTimeMillis()}")
        val moved = runCatching { file.renameTo(backup) }.getOrDefault(false)
        _notice.value = if (moved) {
            "关注名单档读不出来，已另存为 ${backup.name} 并重新开始"
        } else {
            "关注名单档读不出来，本次改动可能覆盖它（${file.absolutePath}）"
        }
    }

    companion object {
        private const val FILE_NAME = "gallery_artist_follows.json"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            prettyPrint = false
        }

        @Volatile
        private var instance: GalleryArtistFollowsStore? = null

        fun getInstance(context: Context): GalleryArtistFollowsStore = instance ?: synchronized(this) {
            instance ?: GalleryArtistFollowsStore(context.applicationContext).also { instance = it }
        }
    }
}

private fun GalleryArtistFollow.toEntry() = ArtistFollowEntry(site.routeKey, name, addedAt)
