package com.venera.compose.gallery.data

import com.venera.compose.data.platform.PathProvider
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
 * 这份名单里刻意**不**掺 pixiv 用户编号 —— 实测拿旧编号去要头像，站方回 `error=true`，
 * 编号是会失效的。头像**地址**从 2026-10-01 起单独落盘了（见 [GalleryArtistAvatarStore]，
 * 那里存的是不会失效的静态路径而不是编号），但仍不写进这一份名单：
 * 名单是用户攒下的关系，地址档是可随时重取的结果，两者混在一份文件里谁也清不掉谁。
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
class GalleryArtistFollowsStore internal constructor(private val paths: PathProvider) {

    private val file = File(paths.dataRoot, FILE_NAME)

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

    /**
     * 合并一份名单进来（备份恢复），返回**真正新增**的条数。
     *
     * 恢复必须走这里、不能直接把 JSON 写回那个文件：屏上读的是 [_follows] 这份内存，
     * 绕过 store 会让"导入报了成功"和"首页那排入口还是空的"同时成立 —— 那只是把
     * 缺失往后推一格，跟当年收藏直接写 SQL 那张没人读的表是同一个根因。
     *
     * 去重与定序沿用 [GalleryArtistFollows] 既有判据（同一条留**较早**那次关注，不重掷时间戳），
     * 所以本机已经关注过的人不会因为导入被顶到名单最前。
     */
    suspend fun restore(incoming: List<GalleryArtistFollow>): Int {
        if (incoming.isEmpty()) return 0
        val known = _follows.value.mapTo(HashSet()) { it.uid }
        val merged = GalleryArtistFollows.sorted(GalleryArtistFollows.dedupe(_follows.value + incoming))
        val added = merged.count { it.uid !in known }
        if (added == 0) return 0
        // 先落盘、后换内存：盘没写成就整笔退回，不留"屏上已经有了、重启就没了"那种半截状态。
        // 恢复这条路要比普通关注更硬 —— 一次导入可能几十位画师，全丢了是实打实的损失。
        if (!persist(merged)) throw java.io.IOException("关注名单没能写进磁盘（${file.absolutePath}），本次恢复未生效")
        _follows.value = merged
        return added
    }

    /** 落盘是否成功。普通关注/取关沿用旧行为（失败只在下次冷启动显现），恢复那条路会把它当错误抛出。 */
    private suspend fun persist(list: List<GalleryArtistFollow>): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(list.map { it.toEntry() }))
            if (!tmp.renameTo(file)) {
                tmp.delete()
                throw java.io.IOException("关注名单档改名失败：${file.absolutePath}")
            }
        }.isSuccess
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

        fun getInstance(paths: PathProvider): GalleryArtistFollowsStore = instance ?: synchronized(this) {
            instance ?: GalleryArtistFollowsStore(paths).also { instance = it }
        }
    }
}

private fun GalleryArtistFollow.toEntry() = ArtistFollowEntry(site.routeKey, name, addedAt)
