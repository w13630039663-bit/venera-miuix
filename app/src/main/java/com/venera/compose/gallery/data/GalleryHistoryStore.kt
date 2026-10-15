package com.venera.compose.gallery.data

import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.domain.GalleryBrowseRow
import com.venera.compose.gallery.domain.GalleryBrowseRows
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 磁盘上的一行浏览记录。
 *
 * 与 domain 那个 [GalleryBrowseRow] 分开是有意的（理由与关注名单那颗同一条）：磁盘格式要能
 * 向后兼容（加字段给默认值就行），领域形状不该被磁盘绑住。字段名与桌面那颗件逐字相同 ——
 * 两份存储格式分开维护，但口径漂了就会出现"桌面看到的历史和手机不是一回事"。
 */
@Serializable
private data class BrowseEntry(
    @SerialName("site") val siteKey: String,
    @SerialName("id") val postId: Long,
    @SerialName("thumb") val thumbUrl: String,
    @SerialName("viewed_at") val viewedAt: Long = 0L,
    // 2026-10-15 加的一格：那一格摆哪档比例。给默认值 0 是有意的 ——
    // 昨天写下的档里没有这个键，读进来必须是"没量到"而不是解析失败（整档留档重来代价太大）。
    @SerialName("ratio") val ratio: Float = 0f,
)

/**
 * 画廊的**浏览历史**（看过的图）。2026-10-14 新增，入口在画廊首页顶栏（取代原来那枚「换一批」）。
 *
 * 存储照 [GalleryArtistFollowsStore] 同一份规格：
 * - 构造时**同步**读一次（名单几百行封顶，收益是第一帧那面墙就是对的，不会先空一下再蹦出来）；
 * - 写盘先写 `*.tmp` 再改名（写到一半被杀不会把名单截成半个 JSON）；
 * - 解不开时**先把原文件改名留档**（`*.corrupt-<时间戳>`）再开空档，并通过 [consumeNotice] 说一句
 *   —— 直接覆盖等于把用户攒的名单删了。
 *
 * ## 为什么不设行数上限
 *
 * 桌面那颗件量过同样的问题并明确不设（现量：`take(` / `MAX_` / `limit` 零处，理由写在那儿）——
 * 全仓没有第二份历史类存储带帽，这里不造第一个数字。体量自己算过：一行约 120 字节
 * （站键 + 图号 + 缩略图地址 + 时刻），看 2000 张图 ≈ 240KB，而重复看同一张只刷新时刻不涨行数，
 * 真要清就是顶栏「…」一键全清。
 *
 * ## 为什么存缩略图地址而不在记录时存图
 *
 * 地址一个串，位图几十 KB 起步 —— 而地址过期之后的代价只是瓦片显示"图没加载出来"那一态
 * （缩略图 CDN 长期有效，实测 `max-age` 十年档）。存图等于给每个看过的帖子在本机留一份拷贝，
 * 那是相册才干的事，不是历史该干的。
 */
class GalleryHistoryStore internal constructor(private val paths: PathProvider) : GalleryBrowseHistory {

    private val file = File(paths.dataRoot, FILE_NAME)

    private val _rows = MutableStateFlow<List<GalleryBrowseRow>>(emptyList())

    /** 已按「最近看过在前」排好的名单（判据在 [GalleryBrowseRows]）。 */
    override val rows: StateFlow<List<GalleryBrowseRow>> = _rows.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)

    init {
        readFromDisk()
    }

    override fun consumeNotice(): String? = _notice.value?.also { _notice.value = null }

    /**
     * 记一笔「看过」。同一条重复看只留一行、刷新时刻并置顶（判据在 [GalleryBrowseRows.withViewed]）。
     *
     * 先换内存再落盘（与关注名单同一顺序）：落盘失败只丢最新一行，而屏上那份已经是新的 ——
     * 下一笔成功时它会被一起写回去。历史丢一行不是损失，关注名单丢一条才是。
     */
    override suspend fun recordView(post: GalleryPost, at: Long) {
        // 比例取 `cardRatio` 而不是 width/height 两个数：那一格属性自己就在三档尺寸里挑
        // （large → preview → 原片），而历史只服务"摆得出来"这一件事。
        val row = GalleryBrowseRow(post.site, post.id, post.previewUrl, at, post.cardRatio)
        val merged = GalleryBrowseRows.withViewed(_rows.value, row)
        _rows.value = merged
        persist(merged)
    }

    /**
     * 批量移除（历史墙多选那条路）。
     *
     * 刻意不写成 `uids.forEach { remove(it) }`：那是 N 次全量序列化 + N 次写盘，
     * 选 50 张就是 50 趟 IO。与 `GalleryFavoritesStore.removeAll` 同一条理由、同一形状。
     */
    override suspend fun removeAll(uids: Collection<String>) {
        if (uids.isEmpty()) return
        val doomed = uids.toSet()
        val next = _rows.value.filterNot { it.uid in doomed }
        if (next.size == _rows.value.size) return
        _rows.value = next
        persist(next)
    }

    /** 整份清空。屏上那份与盘上那份一起清，不留"屏上没了、重启又回来"。 */
    override suspend fun clear() {
        _rows.value = emptyList()
        persist(emptyList())
    }

    /** 落盘是否成功。读不出来的坏档处理见 [readFromDisk]。 */
    private suspend fun persist(list: List<GalleryBrowseRow>): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(list.map { it.toEntry() }))
            // 从前是 `tmp.renameTo(file)` —— Windows 上目标已存在时它**返回 false 而不替换**，
            // 于是第二次记录就写不进盘（成因与选型逐字写在 `GalleryArtistAvatarStore.persist` 那里）。
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.isSuccess
    }

    private fun readFromDisk() {
        if (!file.exists()) return
        val parsed = runCatching { json.decodeFromString<List<BrowseEntry>>(file.readText()) }.getOrNull()
        if (parsed != null) {
            // 站点键认不出的行当场摘掉（老档写过别的键、或手工改坏）：留着它，那一格永远点不开。
            val usable = parsed.mapNotNull { entry ->
                GallerySite.fromRouteKey(entry.siteKey)?.let {
                    GalleryBrowseRow(it, entry.postId, entry.thumbUrl, entry.viewedAt, entry.ratio)
                }
            }
            _rows.value = GalleryBrowseRows.sorted(GalleryBrowseRows.dedupe(usable))
            return
        }
        val backup = File(file.parentFile, "$FILE_NAME.corrupt-${System.currentTimeMillis()}")
        val moved = runCatching { file.renameTo(backup) }.getOrDefault(false)
        _notice.value = if (moved) {
            "浏览历史档读不出来，已另存为 ${backup.name} 并重新开始"
        } else {
            "浏览历史档读不出来，本次改动可能覆盖它（${file.absolutePath}）"
        }
    }

    companion object {
        private const val FILE_NAME = "gallery_browse_history.json"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            prettyPrint = false
        }

        @Volatile
        private var instance: GalleryHistoryStore? = null

        fun getInstance(paths: PathProvider): GalleryHistoryStore = instance ?: synchronized(this) {
            instance ?: GalleryHistoryStore(paths).also { instance = it }
        }
    }
}

private fun GalleryBrowseRow.toEntry() = BrowseEntry(site.routeKey, postId, thumbUrl, viewedAt, ratio)
