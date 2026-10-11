package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 批 86 · 桌面自持的**图库浏览历史**（首页「历史」那一行的那一份档）。
 *
 * ## 为什么这一颗在桌面而不在对岸
 *
 * 首页那一行原来的判据点名了两种存储形态（`gallery/data/GalleryHistoryStore.kt` 或 `data/db` 里一张
 * gallery 历史表），那两条都是**对岸**的候选 —— 而对岸今天根本没有 gallery 浏览历史，
 * `app/` 又是受保域（为桌面的一屏去给对岸加一颗 store 不在授权面里）。
 * 批 85 是同一条处理：对岸那颗 `GallerySaver` 因 MediaStore 整颗在排除清单里 ⇒ 桌面自持一颗，
 * 判据跟着改写并把理由留在原处。⚠️ 别把这一颗与 `comic_history` 记成同一件事：那张表是
 * **漫画阅读进度**（批 82 起桌面真的在写它，首页「阅读历史」那一行读的就是它），本仓为这句话红过一次。
 *
 * ## 三条不显然的口径
 *
 * - **键是「站 + 号」两枚**：三站各有自己的编号空间，`#123` 在两站是两张图。本仓记过一枚同族的债 ——
 *   复合键两端不同轨（一头恒传空串），档位在而永远命中不了，单测全绿。判据 ② 专门拿同号异站喂它。
 * - **只存"看过"这一件事**：站、号、预览档、时刻，四样。存原图地址与宽高就等于开始欠"它过期了"那笔账，
 *   而这一屏要的只是"一眼认得那是哪张"。也因此那一屏**摆不出**"点开回原卡"，本批就不摆那一枚按钮
 *   —— 摆一枚只能带回半屏的按钮，正是本仓最忌的假开关。
 * - **没有条数上限**：对岸那两颗同类件（`GalleryFavoritesStore` / `GalleryArtistFollowsStore`）
 *   都没有 cap（现量：三颗文件里 `take(` / `MAX_` / `limit` 零处），这里不造一个新数字。
 *   代价写在这：一条 ≈ 90 字节，一万条形如 900 KB，构造时同步读一次（与那两颗同一形状：
 *   第一帧就是对的，不许先空一下再蹦出来）。
 *
 * ## 缺档与坏档是两句话
 *
 * 缺 = 还没点开过（空名单，**不报坏**）；坏 = 读不开（先把原文件改名留档，再交回空档，并说一句）。
 * 直接覆盖等于把用户攒的记录删了 —— 照对岸那两颗 store 的定案，不改进。
 */
internal class DesktopGalleryBrowseHistory internal constructor(private val file: File) {

    private val _rows = MutableStateFlow(emptyList<DesktopBrowseView>())

    /** 已按「看过时刻倒序、同刻按站键再按号」排好的名单。 */
    val rows: StateFlow<List<DesktopBrowseView>> = _rows.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)

    /**
     * 那一份档读不开时的一句话，屏上由「历史」那一屏自己念。
     *
     * ⚠️ **不做读走即清**：`consumeNotice` 那套挂在屏里，会随那一屏离开组合而消失
     * （批 77 记过的那一课）。这一句说的是"盘上那份坏了"，它得一直在那里直到应用重启。
     */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        runCatching { desktopBrowseReadRows(file) }
            .onSuccess { _rows.value = it }
            .onFailure { failure ->
                val kept = keepCorruptArchive(file)
                _rows.value = emptyList()
                _notice.value = "本机那份浏览记录读不开（${failure::class.java.simpleName}），" +
                    "已经留档到 ${kept ?: "同目录的一份 .corrupt 档"}，这一份从空档重新开始"
            }
    }

    /**
     * 记一次"看过"：进行集、落盘，然后念一句 stdout。
     *
     * 顺序是**先写盘再改内存** —— 反过来的话，写不下去时内存里就留下一条屏上看得见而盘上没有的记录。
     * 写不下去**让它抛**（本地一次追加，失败就是磁盘或权限问题，那不是能"降级"过去的事）：
     * 桌面那颗崩溃读数会把来处念出来，比静默留下一份半档好。
     */
    suspend fun recordView(post: GalleryPost, at: Long): DesktopBrowseView = withContext(Dispatchers.IO) {
        val row = desktopBrowseRowOf(post, at)
        val merged = desktopBrowseRowsViewed(_rows.value, row)
        desktopBrowseWriteRows(file, merged)
        _rows.value = merged
        println(desktopBrowseStdoutReading(row, merged.size.toLong()))
        row
    }

    companion object {

        @Volatile
        private var instance: DesktopGalleryBrowseHistory? = null

        @Volatile
        private var instanceRoot: String? = null

        /**
         * 同一个数据根**只建一颗**。
         *
         * 浮层写的那颗与「历史」那一屏读的那颗必须是同一个对象：两颗各记各的内存，
         * 表现就是"刚点开的那张在屏上看不见"，而单看每一颗都自洽（判据 ⑰ 钉这一条）。
         */
        fun getInstance(paths: PathProvider): DesktopGalleryBrowseHistory {
            val root = paths.dataRoot.absolutePath
            instance?.takeIf { instanceRoot == root }?.let { return it }
            synchronized(this) {
                instance?.takeIf { instanceRoot == root }?.let { return it }
                val created = DesktopGalleryBrowseHistory(desktopBrowseHistoryFile(paths))
                instance = created
                instanceRoot = root
                return created
            }
        }
    }
}

/** 磁盘那一份的名字。与对岸那两颗同类件同一个落点层级（数据根这一层，不藏进子目录）。 */
internal const val DESKTOP_GALLERY_HISTORY_FILE_NAME = "gallery_browse_history.json"

/**
 * 磁盘上的一行。字段名走蛇形 + `@SerialName`，与 `GalleryArtistFollowsStore` 里那颗
 * `ArtistFollowEntry`（`site` / `name` / `added_at`）同一口径，判据 ⑯ 扫对岸那颗文件钉着。
 */
@Serializable
private data class BrowseRecord(
    @SerialName("site") val siteKey: String,
    @SerialName("id") val postId: Long,
    @SerialName("thumb") val thumbUrl: String,
    @SerialName("viewed_at") val viewedAt: Long,
)

/** 内存里的一行：站点键已经翻成枚举，所以翻不出来的那几行根本进不到这里。 */
internal data class DesktopBrowseView(
    val site: GallerySite,
    val postId: Long,
    val thumbUrl: String,
    val viewedAt: Long,
)

/** 只算落点、**不建目录**（与批 85 那颗存图件同一条：算一下不许在盘上多出东西）。 */
internal fun desktopBrowseHistoryFile(paths: PathProvider): File =
    File(paths.dataRoot, DESKTOP_GALLERY_HISTORY_FILE_NAME)

/** 一张卡 → 磁盘那一行：地址只取**预览档**那一档，原图地址不进这条链。 */
internal fun desktopBrowseRowOf(post: GalleryPost, at: Long): DesktopBrowseView = DesktopBrowseView(
    site = post.site,
    postId = post.id,
    thumbUrl = post.previewUrl,
    viewedAt = at,
)

/** 倒序；同刻按站点键再按号（连点两张会撞在同一毫秒，那时得有个定序）。 */
internal fun desktopBrowseRowsSorted(rows: List<DesktopBrowseView>): List<DesktopBrowseView> = rows.sortedWith(
    compareByDescending<DesktopBrowseView> { it.viewedAt }
        .thenBy { it.site.routeKey }
        .thenBy { it.postId },
)

/** 把一次"看过"进行集：同一「站 + 号」只留一行（时刻换成这一次），然后重新定序。 */
internal fun desktopBrowseRowsViewed(rows: List<DesktopBrowseView>, row: DesktopBrowseView): List<DesktopBrowseView> =
    desktopBrowseRowsSorted(
        rows.filterNot { it.site == row.site && it.postId == row.postId } + row,
    )

private val historyJson = Json {
    ignoreUnknownKeys = true
    // 四个键恒写：读侧要靠"这一栏在不在"判档的形态，与对岸那颗 encodeDefaults=false 的归档编码不同用途。
    encodeDefaults = true
}

internal fun desktopBrowseEncode(rows: List<DesktopBrowseView>): String =
    historyJson.encodeToString(rows.map { BrowseRecord(it.site.routeKey, it.postId, it.thumbUrl, it.viewedAt) })

/**
 * 解一份文本：认不出站点键、或号是 0 的那一行**当场摘掉**（对岸 `GalleryBackupRows.decodeFavorites`
 * 同一条判据）。留着它的代价是那一格在屏上念出半个站名和一个 `#0`，而那正是"读出来了但没读对"。
 * 整份解不开就从这里抛出去，由那颗件的 init 留档。
 */
internal fun desktopBrowseDecode(text: String): List<DesktopBrowseView> =
    historyJson.decodeFromString<List<BrowseRecord>>(text).mapNotNull { record ->
        GallerySite.fromRouteKey(record.siteKey)
            ?.takeIf { record.postId > 0L }
            ?.let { DesktopBrowseView(it, record.postId, record.thumbUrl, record.viewedAt) }
    }

/** 缺档交回空名单（那是"没有"，不是"读不开"）。 */
internal fun desktopBrowseReadRows(file: File): List<DesktopBrowseView> =
    if (!file.isFile) emptyList() else desktopBrowseDecode(file.readText())

/**
 * 先写同目录的 `*.tmp` 再改名过去，且**必须**是带覆盖语义的那一颗改名。
 *
 * 两条都是栽过的：`File.renameTo` 在 Windows 上目标已存在时只返回 `false` 而不抛
 * （5d74155 那一笔：三颗 store 的原子写从第二笔起全丢），而直接写目标文件则会在写到一半被打断时
 * 把整份档截成半个 JSON。判据 ⑤⑥ 一个钉行为、一个钉形状。
 */
internal fun desktopBrowseWriteRows(file: File, rows: List<DesktopBrowseView>) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, "${file.name}.tmp")
    tmp.writeText(desktopBrowseEncode(rows))
    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
}

/** 把读不开的那份挪走留档，交回留档的名字（挪不动就交 null，让屏上那句说实话）。 */
private fun keepCorruptArchive(file: File): String? {
    val kept = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
    return runCatching { Files.move(file.toPath(), kept.toPath()) }.getOrNull()?.let { kept.name }
}

private val VIEWED_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 表头那一句：没点开过 vs 看过 N 张，两句话说的是两件不同的事（判据 ⑫）。 */
internal fun desktopBrowseHeaderReading(rows: List<DesktopBrowseView>): String = if (rows.isEmpty()) {
    "本机还没点开过图 —— 点开一张卡就记在这里"
} else {
    "本机看过 ${rows.size} 张 · 最近 ${desktopBrowseTimeText(rows.first().viewedAt)}"
}

/** 行里那一句：站名（不是 routeKey）、号、那一刻。 */
internal fun desktopBrowseRowLine(row: DesktopBrowseView): String =
    "${row.site.displayName} · #${row.postId} · ${desktopBrowseTimeText(row.viewedAt)}"

private fun desktopBrowseTimeText(millis: Long): String =
    VIEWED_TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/**
 * stdout 那一行读数。**形参表里就没有地址**这一枚（站方那一路地址常带 `api_key`），
 * 所以这一句结构上带不出 URL —— 与批 85 那两枚读数函数同一条口径，判据 ⑮ 钉整串签名。
 */
internal fun desktopBrowseStdoutReading(row: DesktopBrowseView, totalRows: Long): String =
    "D_看过 #${row.postId} 站=${row.site.displayName} 共 $totalRows 行"
