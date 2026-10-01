package com.venera.compose.gallery.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 猜你喜欢那一屏里"按站一份"的读数（快照用的行式形状）。
 *
 * 为什么存成 `List` 而不是直接存 `Map<GallerySite, String>`：`GalleryFeedSnapshot` 那批
 * 已有形状就是"每行自带站点键"（`GalleryFavorite.site`），跟着它走有两个好处 ——
 * 不用押 kotlinx 对枚举键 Map 的编码口径，而且站点认不出来时可以**逐行摘掉**而不是整档作废
 * （与 [GalleryFeedCache] 那头 `mapNotNull { fav.site?.let {...} }` 同一条口径）。
 */
@Serializable
data class GalleryForYouSiteRow(
    @SerialName("site") val site: GallerySite? = null,
    @SerialName("value") val value: String = "",
)

/**
 * 猜你喜欢那一屏的**离线快照**（批次 O）。
 *
 * ## 为什么需要它
 *
 * 切 Tab 会把画廊那条目的地 pop 掉，条目作用域的 VM 随之清空
 * （依据见 [com.venera.compose.gallery.domain.GalleryForYouRefreshPolicy] 类头注）。
 * 日榜那头 2026-09-29 就为这件事存过快照，这一屏当时漏了 —— 于是"每次进画廊整页重取"。
 *
 * ## 为什么存这么多字段
 *
 * 只存 `posts` 会造出一屏**看着是旧的、其实是残的**内容：
 * - 不存 [queries] → 滑到底取下一页时不知道照哪串标签发，只能重抽（= 换了一屏不相干的图）；
 * - 不存 [page] / [sitesDone] → 下一页从第 1 页重发，已看过的整批被去重剔空，
 *   读起来是"到底了却还在转"；
 * - 不存 [failures] → 双源混摆静默退化成单源时那句**可见**提示会消失，
 *   而那正是这一屏最难被发现的一种错；
 * - 不存页尾那三个累计数 → 重进一次，"累计 X 张 / 已排除 N 张"就归零，像数据丢了。
 *
 * [seed] 是这一屏的"哪一次抽样"身份：它必须与 [posts] 成对在场，判据见
 * [com.venera.compose.gallery.domain.GalleryForYouRefreshPolicy.canHydrate]。
 */
@Serializable
data class GalleryForYouSnapshot(
    @SerialName("saved_epoch_day") val savedEpochDay: Long = 0L,
    @SerialName("seed") val seed: Long = 0L,
    @SerialName("posts") val posts: List<GalleryFavorite> = emptyList(),
    @SerialName("queries") val queries: List<GalleryForYouSiteRow> = emptyList(),
    @SerialName("page") val page: Int = 0,
    @SerialName("per_site") val perSite: List<GalleryForYouSiteRow> = emptyList(),
    @SerialName("excluded_favourite") val excludedFavourite: Int = 0,
    @SerialName("videos") val videos: Int = 0,
    @SerialName("failures") val failures: List<GalleryForYouSiteRow> = emptyList(),
    @SerialName("sites_done") val sitesDone: List<GallerySite> = emptyList(),
)

/** 快照里那几列"按站一份"的读数还原成 Map；站点认不出的行**逐行摘掉**，不整档作废。 */
fun List<GalleryForYouSiteRow>.bySite(): Map<GallerySite, String> =
    mapNotNull { row -> row.site?.let { it to row.value } }.toMap()

/** 反向：Map → 行式（写快照用）。 */
fun Map<GallerySite, String>.toSiteRows(): List<GalleryForYouSiteRow> =
    entries.map { GalleryForYouSiteRow(site = it.key, value = it.value) }

/** 页尾那个"各站累计张数"专用：值是个位数，塞进字符串列再转回来，不另开一套形状。 */
fun List<GalleryForYouSiteRow>.countBySite(): Map<GallerySite, Int> =
    bySite().mapNotNull { (site, value) -> value.toIntOrNull()?.let { site to it } }.toMap()

fun Map<GallerySite, Int>.toCountRows(): List<GalleryForYouSiteRow> =
    entries.map { GalleryForYouSiteRow(site = it.key, value = it.value.toString()) }

/**
 * 猜你喜欢快照的落盘。写法照 [GalleryFeedCache]（构造侧同步读 / 先写 `*.tmp` 再改名 /
 * `ignoreUnknownKeys`），两边踩过的坑一样：写到一半被杀不能截断已有内容，
 * 新字段被旧版本读到不该当场炸。
 */
object GalleryForYouCache {

    private const val FILE_NAME = "gallery_for_you_cache.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
    }

    /**
     * **同步**读：调用点在 VM 的 `init`，而"这一轮要不要联网"就取决于这次读的结果 ——
     * 异步读抢不过那次判断，切回 Tab 照样发一笔，快照就白存了。
     *
     * 读不出来就当作没有：最坏是回到本轮之前那次联网加载，用户没丢任何他自己的数据
     * （这一档存的是站方的一屏，不是他产生的东西）。
     */
    fun read(context: Context): GalleryForYouSnapshot? {
        val file = file(context)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<GalleryForYouSnapshot>(file.readText()) }.getOrNull()
            ?.takeIf { it.posts.isNotEmpty() }
    }

    suspend fun write(context: Context, snapshot: GalleryForYouSnapshot) = withContext(Dispatchers.IO) {
        runCatching {
            val file = file(context)
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(snapshot))
            if (!tmp.renameTo(file)) {
                tmp.delete()
                throw java.io.IOException("猜你喜欢快照档改名失败：${file.absolutePath}")
            }
        }
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)
}
