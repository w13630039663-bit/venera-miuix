package com.venera.compose.gallery.data

/**
 * 画廊的**归一**图片条目：两站官方 JSON 的字段名几乎全不同（对照表见
 * `gallery-dual-source-hot-pool-plan-2026-09.md` §三），所以这里只留"用途"，
 * 站方的字段名各自的 DTO 里去翻译，UI 层不再出现任何单站专属字段。
 *
 * **四档地址按用途各取一档，不要混用**：
 * [previewUrl] 给网格、[fastUrl] 给大图页**开门那一档**、[largeUrl] 给大图页中档、
 * [fileUrl] 原图只给 HD 钮。两站实测（2026-09-26，yande.re #600000 与 Danbooru #6000000，
 * 每档逐个 HEAD 取 `Content-Length`）：
 *
 * | 站 | 开门档 [fastUrl] | 中档 [largeUrl] | 原档 [fileUrl] |
 * |---|---|---|---|
 * | yande.re | `preview_url` 300×212 / **13.7 KB** | `sample_url` 1500×1060 / **209 KB** | 原图 6.6 MB（PNG） |
 * | Danbooru | `360x360` 242×360 / **28.8 KB** | `sample` 850×1265 / **164 KB** | 原图 360 KB |
 *
 * ⚠️ **yande.re 的中档曾经错挂 `jpeg_url`**（旧对照表里那句"中档 = jpeg_url"是站方语义读错了）：
 * 实测 30/30 条 `jpeg_width/height` **恒等于** `width/height` —— 它不是降档，
 * 而是**原分辨率的 JPEG 重编码**（同一份池子里见过 9600×5400、10240×5760，单条 3 MB 级）。
 * 拿它当中档，打开一张图要先等一个原图大小的文件，观感就是"骨架图停在那儿半天"，
 * 而且每次翻回来都重来一次。站方真正的中档是 `sample_url`：长边 ≤1500，
 * 原图本来就比 sample 小的时候它回落成 `/image/` 原图 —— **实测 70/70 条非空、无 404 风险**。
 */
data class GalleryPost(
    val site: GallerySite,
    val id: Long,
    /** 空格分隔的标签串。yande.re 不带命名空间前缀，Danbooru 带（`artist:` / `copyright:` …）。 */
    val tags: String = "",
    /** 站方显式分级。yande.re 是 `s`/`q`/`e`，Danbooru 多一档 `g`（general）。 */
    val rating: String = "",
    val score: Int = 0,
    /**
     * 收藏数。**yande.re 的 JSON 里没有这个字段**（实测 44 个键不含 `fav_count`），
     * 所以是 nullable 而不是 0 —— 加权时把"不知道"当 0 会把整站系统性压到最低档。
     */
    val favCount: Int? = null,
    /** 画师。Danbooru 没有 `author` 字段，从 `tag_string_artist` 翻译过来。 */
    val author: String = "",
    /** 画师自己的出处链接（不是本站单页地址，那个见 [pageUrl]）。 */
    val source: String = "",
    val width: Int = 0,
    val height: Int = 0,
    /** 网格缩略档。 */
    val previewUrl: String = "",
    val previewWidth: Int = 0,
    val previewHeight: Int = 0,
    /**
     * 大图页的**第一档**：打开那一瞬间就把画面填上的那一张（预览的上一档）。
     *
     * 为什么不直接拿 [previewUrl] 顶：yande.re 的预览 300×212 铺满屏还行，
     * 而 Danbooru 的预览只有 121×180（实测 9.3 KB）—— 铺满一屏是 9 倍放大的一块糊。
     * 站方另给了 `360x360`（242×360 / 28.8 KB），同样是"秒出"的量级，却清楚一倍。
     * 所以两站各取自己那份"最便宜且铺满屏还认得出"的档：
     * yande.re = `preview_url`（顺带与网格卡片同址，磁盘/内存缓存直接命中），
     * Danbooru = `360x360`。
     *
     * 取不到时留空串（站方变体缺项），由 UI 降级成"直接等 [largeUrl]"。
     * **绝不留一个编出来的地址**。
     */
    val fastUrl: String = "",
    /** 大图页中档：两站都取站方的 `sample` 档。 */
    val largeUrl: String = "",
    val largeWidth: Int = 0,
    val largeHeight: Int = 0,
    /** 原图档（只有 HD 钮才碰）。 */
    val fileUrl: String = "",
    val fileSize: Long = 0,
    val md5: String = "",
    val fileExt: String = "",
    /**
     * 标签按**站方给的分类**分好桶，给「关于这张图」那面板用。
     *
     * 两站的能力差得很远，所以桶数不一样（实测）：
     * - Danbooru 直接给五串 `tag_string_general/character/copyright/artist/meta` → 五桶；
     * - yande.re 的 44 个键里**没有任何分类字段**，只有一个平铺 `tags` → 只能一桶「标签」。
     * 这是站方数据面的天花板，不是这里少写。空桶不进列表（摆一个空的"角色"区比不摆更糟）。
     */
    val tagGroups: List<GalleryTagGroup> = emptyList(),
    /**
     * 视频时长（秒）。站方只在 `media_asset.duration` 给（实测顶层**没有** `duration` 键），
     * yande.re 根本没这个字段、Danbooru 的图片条目也没有 → 一律 null，不当 0。
     */
    val durationSeconds: Double? = null,
) {
    val tagList: List<String> get() = splitGalleryTags(tags)

    /**
     * 瀑布流摆放前要的高度比例（摆放前就得知道，否则会"各列先等分再集体跳动"）。
     *
     * 优先 [largeWidth]/[largeHeight]：原图尺寸可能是 9000×6000 的巨幅，
     * 而 preview 档被站方裁过（实测一条 9151×6300 的原图，preview 只有 300×207）。
     * 三者比例一致时取哪个都行，拿不到任何尺寸时返回 0，由卡片退回占位比例。
     */
    val cardRatio: Float
        get() {
            val w = largeWidth.takeIf { it > 0 } ?: previewWidth.takeIf { it > 0 } ?: width
            val h = largeHeight.takeIf { it > 0 } ?: previewHeight.takeIf { it > 0 } ?: height
            return if (w > 0 && h > 0) w.toFloat() / h else 0f
        }

    /**
     * 站方把 `questionable` 也算成人内容：与漫画侧「源级预设 = nsfw」同一档处理。
     *
     * 判据写成「只有 `s` / `g` 才算安全」而不是「`e`/`q` 才算成人」——
     * 未知值或空串一旦放行，就是把内容**静默**放到已经开了打码的界面上。方向要反过来：宁可错打码。
     * `g`（general）是接 Danbooru 才出现的一档（实测最"新"的那批 20 条里 8 条是 g），
     * 沿用第一轮 `rating != "s"` 会把这档最干净的内容**误打码**。
     */
    val isAdultMarked: Boolean get() = rating != "s" && rating != "g"

    /** 本站单页地址。已删除的条目会 404，那是站方语义，不是我们的缺陷。 */
    val pageUrl: String get() = site.pageUrlPrefix + id

    /**
     * 视频条目。图片链路（Coil）拿它会解码失败 —— 两站的 `large_file_url` / `jpeg_url`
     * 已经翻译时就换成了静帧，所以这里只用来把 UI 分流到播放器。
     */
    val isVideo: Boolean get() = fileExt.lowercase() in VIDEO_EXTS

    /** 可播放的原片地址。实测两站都**没有**更小的视频转码档，所以只能直接吃 [fileUrl]。 */
    val videoUrl: String get() = fileUrl

    /** 时长角标。站方没给时长时返回空串而不是 "0″" —— 编一个看不出来的数比不写更糟。 */
    val durationLabel: String
        get() = durationSeconds?.takeIf { it > 0.0 }?.let { "${it.toInt()}″" } ?: ""

    /** 站内唯一身份。列表 key 与"这一屏已经出现过"的判据都用它。 */
    val uid: String get() = "${site.routeKey}:$id"

    /**
     * 出处归一键：跨站同一张画常常是同一个上游链接（pixiv / twitter）。
     * 实测本轮两站热池的交集为 0（见方案 §五），但这个键零成本，留着防将来。
     */
    val sourceKey: String
        get() = source.trim().lowercase()
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .trimEnd('/')

    companion object {
        /**
         * 要引进来的视频扩展名。实测 Danbooru 日榜只有 `mp4`，
         * yande.re 侧历史上是 `webm`（这两档之外的一律当"不能摆"滤掉，比如 `zip`）。
         */
        val VIDEO_EXTS: Set<String> = setOf("mp4", "webm")
    }
}

/** 一桶标签：[label] 是给人看的桶名（"通用"/"角色"…），[tags] 已经拆好。 */
data class GalleryTagGroup(val label: String, val tags: List<String>)

/** 站方那串空格分隔的 tag → 词表。切分口径全仓只有这一处（[GalleryPost.tagList] 也走它）。 */
internal fun splitGalleryTags(raw: String): List<String> =
    raw.trim().split(' ').map { it.trim() }.filter { it.isNotEmpty() }

/** 切成一桶，**空桶直接不产出** —— 摆一个空的"角色"区比不摆更糟。 */
internal fun galleryTagGroup(label: String, raw: String): GalleryTagGroup? =
    splitGalleryTags(raw).takeIf { it.isNotEmpty() }?.let { GalleryTagGroup(label, it) }
