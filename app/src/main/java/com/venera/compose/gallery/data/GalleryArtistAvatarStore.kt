package com.venera.compose.gallery.data

import android.content.Context
import android.util.Log
import com.venera.compose.gallery.domain.GalleryArtistAvatars
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 头像地址的**磁盘档**：一位画师在一站上解析出来的那张脸去哪儿取。
 *
 * ## 为什么这一份要落盘（2026-10-01 改判）
 *
 * 批次 L 原本拍板"刻意不落盘"，理由是"头像地址会过期，存了就等于承诺离线也能看到"。
 * 那条前提今天被实测掉了：
 * - pixiv 的头像地址是 `i.pximg.net/user-profile/img/2017-03-08/…/12240120_<md5>_170.jpg`
 *   —— 一条**不带任何过期参数**的静态路径，2017 年的头像今天仍回 200；
 * - 响应头 `Cache-Control: max-age=31536000`，画廊那份 Coil 磁盘缓存本来就存得住图字节。
 *
 * 不落盘付的代价是**每次冷启动**都要为每一位画师重发 2~4 笔 JSON（pixiv `/ajax/user` →
 * fanbox `creator.get` → Mastodon 查询），而这正是屏上"脸一张一张慢慢蹦"的成因。
 *
 * 那条"离线也能看到"的承诺由**图片缓存**兑现，不由这份地址档兑现：地址在、图不在时
 * 仍然是一次普通的首次加载。
 *
 * ## 存的是什么、不存的是什么
 *
 * 只存**解析结果**（站点 + 画师名 + 出处 → 头像地址）。刻意不存 pixiv 用户编号：
 * 实测拿旧编号去要头像，站方回 `error=true` —— 编号会失效，而地址不会。
 *
 * 读写规格照 [GalleryArtistFollowsStore]：构造时同步读一次（第一帧那排脸就是对的，
 * 不先空一下再蹦出来）、写盘先 `*.tmp` 再改名、解不开时**先把原文件改名留档**再开空档。
 * 判据（三档语义、上限怎么丢）都在 [GalleryArtistAvatars]，这一层只管字节与磁盘。
 */
internal class GalleryArtistAvatarStore private constructor(private val context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    /** 内存查找表；[GalleryArtistAvatars.lookup] 的三档语义由它的返回值承担。 */
    @Volatile
    private var byKey: Map<String, GalleryArtistAvatars.Entry> = emptyMap()

    /** 全量条目快照（写盘用）。与 [byKey] 同批换掉，不留"表里有、列表里没有"的中间态。 */
    @Volatile
    private var entries: List<GalleryArtistAvatars.Entry> = emptyList()

    init {
        readFromDisk()
    }

    /** null = 从没查过；空串 = 查过、站方确实没给脸；其余 = 可以直接画的地址。 */
    fun peek(site: GallerySite, name: String, source: String): String? =
        GalleryArtistAvatars.lookup(byKey, site, name, source)

    /** 记下一次解析的结果。[avatar] 传 null 也照样记（落成空串 = "查过了，没有"）。 */
    suspend fun remember(site: GallerySite, name: String, source: String, avatar: String?) {
        val snapshot = GalleryArtistAvatars.put(
            entries,
            GalleryArtistAvatars.Entry(
                site = site,
                name = name,
                source = source,
                avatar = avatar.orEmpty(),
                resolvedAt = System.currentTimeMillis(),
            ),
        )
        entries = snapshot
        byKey = GalleryArtistAvatars.index(snapshot)
        persist(snapshot)
    }

    private suspend fun persist(snapshot: List<GalleryArtistAvatars.Entry>) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(snapshot.map { it.toRecord() }))
            if (!tmp.renameTo(file)) {
                tmp.delete()
                throw java.io.IOException("头像地址档改名失败：${file.absolutePath}")
            }
        }.onFailure { failure ->
            // 落不进盘只是下次冷启动多问一遍，不值得打扰用户 —— 但日志里必须留话。
            Log.w(TAG, "头像地址档没写成：${failure.message}")
        }
    }

    private fun readFromDisk() {
        if (!file.exists()) return
        val records = runCatching {
            json.decodeFromString<List<AvatarRecord>>(file.readText())
        }.getOrNull()
        if (records != null) {
            // 站点键认不出的行当场摘掉（老档写过别的键、或手工改坏）：留着它也没法用。
            val usable = records.mapNotNull { record ->
                GallerySite.fromRouteKey(record.siteKey)?.let {
                    GalleryArtistAvatars.Entry(
                        site = it,
                        name = record.name,
                        source = record.source,
                        avatar = record.avatar,
                        resolvedAt = record.resolvedAt,
                    )
                }
            }
            entries = usable
            byKey = GalleryArtistAvatars.index(usable)
            return
        }
        val backup = File(file.parentFile, "$FILE_NAME.corrupt-${System.currentTimeMillis()}")
        val moved = runCatching { file.renameTo(backup) }.getOrDefault(false)
        Log.w(
            TAG,
            if (moved) "头像地址档读不出来，已另存为 ${backup.name} 并重新开始"
            else "头像地址档读不出来，本次改动可能覆盖它（${file.absolutePath}）",
        )
    }

    companion object {
        private const val FILE_NAME = "gallery_artist_avatars.json"
        private const val TAG = "GalleryArtistAvatar"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            prettyPrint = false
        }

        @Volatile
        private var instance: GalleryArtistAvatarStore? = null

        fun getInstance(context: Context): GalleryArtistAvatarStore = instance ?: synchronized(this) {
            instance ?: GalleryArtistAvatarStore(context.applicationContext).also { instance = it }
        }
    }
}

/** 盘上的一条。字段名短、且与内存形状分开，理由同 [GalleryArtistFollowsStore] 的那份。 */
@Serializable
private data class AvatarRecord(
    @SerialName("site") val siteKey: String,
    @SerialName("name") val name: String,
    @SerialName("source") val source: String = "",
    @SerialName("avatar") val avatar: String = "",
    @SerialName("resolved_at") val resolvedAt: Long = 0L,
)

private fun GalleryArtistAvatars.Entry.toRecord() =
    AvatarRecord(site.routeKey, name, source, avatar, resolvedAt)
