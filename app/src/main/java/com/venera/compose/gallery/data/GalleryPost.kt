package com.venera.compose.gallery.data

/**
 * 画廊的**归一**图片条目：三站官方 API 的字段名几乎全不同（对照表见
 * `gallery-dual-source-hot-pool-plan-2026-09.md` §三与 [SafebooruClient] 文件头），
 * 所以这里只留"用途"，站方的字段名各自的 DTO 里去翻译，UI 层不再出现任何单站专属字段。
 *
 * **四档地址按用途各取一档，不要混用**：
 * [previewUrl] 给网格、[fastUrl] 给大图页**开门那一档**、[largeUrl] 给大图页中档、
 * [fileUrl] 原图只给 HD 钮。
 *
 * ⚠️ 各站**来自不同的字段、而且缺档的处理也不同**（都是实测）：
 *
 * | 站 | 开门档 [fastUrl] | 中档 [largeUrl] | 原档 [fileUrl] |
 * |---|---|---|---|
 * | yande.re | `preview_url` 300×212 | `sample_url` 1500×1060 | 原图（PNG 可达 6.6 MB） |
 * | Gelbooru | `preview_url` 350px 级 | `sample_url` 850×… ；**小图/视频时为空串** | `file_url` |
 * | Safebooru | `media_asset.variants` 的 **360 档**（180 档铺满屏会糊） | `large_file_url`（=sample；`has_large=false` 时就是原图）；**视频条目这里是原片 mp4，翻译时换成 720 档静帧** | `file_url` |
 *
 * **yande.re 的中档曾经错挂 `jpeg_url`**（旧对照表里那句"中档 = jpeg_url"是站方语义读错了）：
 * 实测 30/30 条 `jpeg_width/height` **恒等于** `width/height` —— 它不是降档，
 * 而是**原分辨率的 JPEG 重编码**（同一份池子里见过 9600×5400、10240×5760，单条 3 MB 级）。
 * 拿它当中档，打开一张图要先等一个原图大小的文件，观感就是"骨架图停在那儿半天"，
 * 而且每次翻回来都重来一次。站方真正的中档是 `sample_url`：长边 ≤1500，
 * 原图本来就比 sample 小的时候它回落成 `/image/` 原图 —— **实测 70/70 条非空、无 404 风险**。
 *
 * Gelbooru 的中档同理取 `sample_url`，但那边的空值形态是**空串**（不是缺键、也不是 404），
 * 所以翻译时兜底到 `file_url`，见 `GelbooruDto.toPost`。
 */
data class GalleryPost(
    val site: GallerySite,
    val id: Long,
    /** 空格分隔的标签串。三站都**不带**命名空间前缀（都是平铺的下划线标识符）。
     *  ⚠️ 站方的键名不同：yande.re/Gelbooru 叫 `tags`，Safebooru 叫 `tag_string`
     *  （那边**没有** `tags` 键 —— 按 `tags` 声明会静默解成空串，见 [SafebooruPostDto]）。 */
    val tags: String = "",
    /**
     * 站方显式分级。⚠️ **各站不同形**（实测）：
     * - yande.re 单字母 `s`/`q`/`e`；
     * - Gelbooru 单词 `general`/`sensitive`/`questionable`/`explicit`；
     * - Safebooru 单字母 `g`/`s`/`q`/`e`，且**实测全站只有 `g` 一档**
     *   （`rating:s`/`rating:q`/`rating:e` 各回 0 条）—— 所以 `g` 漏进安全档的代价是整站被打码。
     * 所以这里存的是站方原样值，判据在 [isAdultMarked] 里按"只认安全的那几档"统一处理。
     */
    val rating: String = "",
    val score: Int = 0,
    /**
     * 收藏数。**只有 Safebooru 给**（实测 `fav_count`，如 170）：
     * yande.re 的 JSON 实测 44 个键不含它，Gelbooru 字段表里也没有 ——
     * 所以是 nullable 而不是 0，取不到的那两站交 null。
     * 加权时把"不知道"当 0 会把整站系统性压到最低档。
     */
    val favCount: Int? = null,
    /**
     * 画师。三站的取法不同，而且**语义不完全同宽**（都是实测）：
     * - yande.re：站方 `author` 字段；
     * - Gelbooru：它的 `owner`（**上传者**，与"画师"不是一回事）；
     * - Safebooru：`tag_string_artist`（站方标好的画师档标签，可能是一串空格分隔的多个名字）。
     */
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
     * 判据是"最便宜且铺满屏还认得出"，各站拿到的那一档不同（都是实测）：
     * - yande.re / Gelbooru：`preview_url`（300×212 / 350px 级），顺带与网格卡片同址，
     *   磁盘/内存缓存直接命中；
     * - Safebooru：`media_asset.variants` 的 **360 档**。它的最便宜档只有 180（实测 9.7 KB），
     *   铺满一屏糊到认不出画的是什么，而 360 档实测 32 KB、与另两站的 preview 级同口径 ——
     *   代价是开门多一笔下载，且**不再**与网格卡同址命中。
     *
     * 取不到时留空串（站方变体缺项），由 UI 降级成"直接等 [largeUrl]"。
     * **绝不留一个编出来的地址**。
     */
    val fastUrl: String = "",
    /**
     * 大图页中档：yande.re/Gelbooru 取站方的 `sample_url`（⚠️ Gelbooru 会给空串，翻译时已兜底）；
     * Safebooru 取 `large_file_url`（`has_large=false` 时它就是原图，见 [SafebooruPostDto]）。
     */
    val largeUrl: String = "",
    val largeWidth: Int = 0,
    val largeHeight: Int = 0,
    /** 原图档（只有 HD 钮才碰）。 */
    val fileUrl: String = "",
    val fileSize: Long = 0,
    val md5: String = "",
    val fileExt: String = "",
    /**
     * 分类归哪儿：`tagGroups` 这一项 2026-09-28 **从归一模型里去掉了**，而且是有意的。
     *
     * 两站的 post 端点确实都不给分类（实测：yande.re 的 44 个键里没有任何分类字段，
     * Gelbooru 只有一串平铺 tags，没有 `tag_string_*` 那五串）。
     * Safebooru 那五串**是给得出的**（实测有 `tag_string_artist` 等），但归一模型不为此多开一栏：
     * 留着这一项就只有两种写法 —— 要么在两站上硬造一个假分组，要么在三站上永远填一桶「标签」
     * 而让下一个人以为分类数据根本不存在，两种都是骗人。
     *
     * 分类的真出处在那张帖的 HTML 上（站方给每一枚标签标了 tag-type-* 类名）：
     * 由 [GalleryTagCategories] 在「关于这张图」打开时另取一笔，渲染期用
     * buildGalleryTagBuckets(post.tagList, …) 分桶；空桶不产出那条口径仍在那边守着。
     * 取不到时只兜「画师」一栏，判据与实测数字都写在那两个文件里。
     *
     * （补全行吃的是另一条链 —— tag 端点上的 type，与「这张画」无关，
     *  见 [GalleryTagSuggestion.category]。别把两者混起来。）
     */
    /**
     * 视频时长（秒）。**只有 Safebooru 给**（实测 `media_asset.duration`，如 9.055782，
     * 图片条目站方直接给 JSON `null`）；yande.re 与 Gelbooru 的字段表里都没有这一项 →
     * 那两站一律 null，**不当 0**（"0 秒"是被编出来的事实）。
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
     * 判据写成「只有安全的那两档才算安全」而不是「`e`/`q` 才算成人」——
     * 未知值或空串一旦放行，就是把内容**静默**放到已经开了打码的界面上。方向要反过来：宁可错打码。
     *
     * ⚠️ 各站的"安全档"**字形不同**（实测），所以这里几种都认：
     * - yande.re 单字母：`s`（safe）；
     * - Gelbooru 单词：`general` / `sensitive`；
     * - Safebooru 单字母：`g`（general，实测**全站只有这一档**）。
     *
     * 沿用第一轮 `rating != "s"` 会把 Gelbooru 的 `general` 与 Safebooru 的 `g` 那批
     * 最干净的内容**误打码**（后者是整站），而只认单词又会让 yande.re 整站都被打码 ——
     * 所以判据必须同时覆盖两套字形。
     */
    val isAdultMarked: Boolean
        get() = rating.lowercase() !in SAFE_RATINGS

    /** 本站单页地址。已删除的条目会 404，那是站方语义，不是我们的缺陷。 */
    val pageUrl: String get() = site.pageUrlPrefix + id

    /**
     * 视频条目。图片链路（Coil）拿它会解码失败 —— 两站的 `large_file_url` / `jpeg_url`
     * 已经翻译时就换成了静帧，所以这里只用来把 UI 分流到播放器。
     */
    val isVideo: Boolean get() = fileExt.lowercase() in VIDEO_EXTS

    /** 可播放的原片地址。实测两站都**没有**更小的视频转码档，所以只能直接吃 [fileUrl]。 */
    val videoUrl: String get() = fileUrl

    /**
     * 视频条目**当底图用**的那一档（播放器顶上那一屏画面）。
     *
     * 不能直接用 [largeUrl]：Gelbooru 对视频条目的 `sample_url` 给**空串**，
     * 翻译时按"小图/视频都要有地址"兜底成了 [fileUrl]（原片 mp4，实测 16~26 MB）。
     * 把 mp4 交给 Coil 的结果是**先白下几十 MB 再解码失败**，
     * 而失败之前那一屏什么都没有 —— 真机现象就是"视频条目黑屏"。
     *
     * 判据是「large 档与原片同址 = 站方没有静帧」，此时才退到 [previewUrl]
     * （实测这一档**恒非空**）。yande.re 那一路 large 档本来就是 jpg 静帧、
     * 与原片不同址，所以保留它 —— 它比 350px 缩略图清楚得多。
     * 两条各有一个用例锁住（`GalleryGelbooruParsingTest`）。
     */
    val videoPosterUrl: String
        get() = largeUrl.takeUnless { it.isBlank() || it == fileUrl } ?: previewUrl

    /**
     * 任何条目**当背景底图**用的那一档。
     *
     * 画师介绍页的 hero 底图用它。底图这一档的要求与 [previewUrl] 那档**不是**一件事：
     * 缩略档只保证"认得出一格是什么"，而底图要铺满整页宽度
     * （hero 实宽约 361dp，本机 ≈993 物理像素），拿 300×212 铺上去是放大 3.3 倍 ——
     * 再叠一层模糊就只剩色块，认不出画的是什么（2026-10-01 用户报「模糊过头」）。
     *
     * 所以图片条目直接用中档 [largeUrl]（两站都是站方的 `sample_url`，长边 ≤1500），
     * 缺档才退 [previewUrl]。**不给 [fileUrl]**：那是原图档，yande.re 侧实测见过
     * 9600×5400 / 单条 3 MB 级的条目，铺一块 196dp 高的底图不值当。
     *
     * 视频条目**不在这里重写判据**，整条转给 [videoPosterUrl] —— 那边多一道
     * "中档被兜底成原片 mp4 就退缩略图"的闸门，两处写两份迟早会分叉。
     */
    val backdropUrl: String
        get() = if (isVideo) videoPosterUrl
        else largeUrl.takeIf { it.isNotBlank() } ?: previewUrl

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
         * 归一成"安全"的分级值（**小写**，两站字形都收）。
         *
         * 这是全仓唯一一处写死分级字符串的地方 —— 加新站时只改这里。
         * 之所以用白名单（而不是列成人档）：未知值必须落进"成人"，
         * 放行一个没见过的分级 = 把内容静默放到已开打码的界面上。
         */
        /**
         * 归一成"安全"的分级值（**小写**，三站字形都收）。
         *
         * 这是全仓唯一一处写死分级字符串的地方 —— 加新站时只改这里。
         * 之所以用白名单（而不是列成人档）：未知值必须落进"成人"，
         * 放行一个没见过的分级 = 把内容静默放到已开打码的界面上。
         *
         * ⚠️ `g` 是 **Safebooru（Danbooru 系）**的"general"档字形。三站三套字形：
         * - yande.re：`s`（safe）
         * - Gelbooru：`general` / `sensitive`（单词）
         * - Safebooru：`g` / `s`（单字母，与 yande.re 同形但多一个 `g`）
         *
         * 2026-10-01 接入 Safebooru 时补 `g`：不加的话该站**最干净的那一批会被整站误打码**
         * （实测抽样里 `rating:"g"` 占绝大多数），而这正是上一轮为 Gelbooru 补
         * `general` 时踩过的同一个坑 —— 字形表不全 = 静默误判整站。
         */
        val SAFE_RATINGS: Set<String> = setOf(
            "s",          // yande.re：safe；Safebooru：sensitive（同字母，两站共用）
            "safe",       // 别名，防站方改写法
            "g",          // Safebooru：general（最干净的那一档）
            "general",    // Gelbooru：一般
            "sensitive",  // Gelbooru：敏感（不露骨，仍算安全档）
        )

        /**
         * 要引进来的视频扩展名。实测两站都有 `mp4`（yande.re 侧历史上是 `webm`，
         * Gelbooru 会把上传的 webm 转码成 mp4，见 `GelbooruClient` 的字段说明）——
         * 这两档之外的一律当"不能摆"滤掉（比如 `zip`）。
         */
        val VIDEO_EXTS: Set<String> = setOf("mp4", "webm")
    }
}

/**
 * 一桶标签：[label] 是给人看的桶名（"通用"/"角色"…），[tags] 已经拆好。
 *
 * [category] 是这一桶**来自站方的哪个数字档**（见 `GalleryTagCategory`）。两类桶没有它：
 * 「站方没判定过」那一桶（[com.venera.compose.gallery.domain.UnresolvedBucketLabel]）、
 * 以及 `categories` 整笔取不到时用离线词典兜出来的那一份（那边只兜画师，档位是推的不是站方给的）。
 *
 * 留着它只为一件事：让「关于这张图」按档位上色（`GalleryTagCategoryColors` 的索引**就是**
 * 站方那个数字档）。从 [label] 反查回数字会变成"按中文名猜档位" —— 那正是分桶那一层
 * 一直在避免的事（见 `GalleryTagBuckets.kt` 头注的"宁可漏，不可错"）。
 */
data class GalleryTagGroup(val label: String, val tags: List<String>, val category: Int? = null)

/** 站方那串空格分隔的 tag → 词表。切分口径全仓只有这一处（[GalleryPost.tagList] 也走它）。 */
internal fun splitGalleryTags(raw: String): List<String> =
    raw.trim().split(' ').map { it.trim() }.filter { it.isNotEmpty() }
