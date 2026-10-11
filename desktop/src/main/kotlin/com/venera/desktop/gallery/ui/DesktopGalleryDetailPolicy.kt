package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GallerySettingsModel
import com.venera.compose.gallery.domain.galleryArtistNames
import com.venera.desktop.gallery.data.DesktopDictWhy

/**
 * 详情浮层那张信息表的**行**（桌面第十一批 P0）。
 *
 * ## 为什么提成一颗纯函数
 *
 * 这张表的每一条"什么时候不摆"都不是审美问题，而是**上一轮用真缺陷换来的读数口径**：
 * Gelbooru 的 post JSON 根本没有 `file_size`（解析处一律填 0），摆出来就是每张图都写「0 MB」，
 * 看着像"这文件是空的"；而即使有值，整数除法也会把 yande.re 上不到 1.5 MB 的真文件压成「0 MB」。
 * 这两条在 Android 那颗 `GalleryInfoSheet.GalleryFactGrid` 里是写在 `buildList` 中间的，
 * 桌面这边若照抄一份 UI 代码，就等于把"能真跑的两条判据"降级成"两段注释"。
 * ⇒ 提成函数，两种输入各跑一遍（`DesktopGalleryDetailTest` ④）。
 *
 * ## 键名一律与 Android 那侧同词
 *
 * 「来源」「分辨率」「文件大小」「上传者」「本地状态」五颗词是从那颗件里逐字取的，中间没有空格的
 * `${width}×${height}` 也是。两端各写一份词，用户切设备时读到的是两张不同的表——
 * 而"同一张图在两端的读数不一样"这种话没人会去查，只会读成数据错了。
 */
internal fun desktopDetailInfoRows(post: GalleryPost, favorite: Boolean): List<Pair<String, String>> =
    buildList {
        add("来源" to post.site.displayName)
        // 尺寸取不到的条目（站方变体缺项）**整格不摆**：摆一个 `0×0` 就是编读数。
        if (post.width > 0 && post.height > 0) add("分辨率" to "${post.width}×${post.height}")
        if (post.fileExt.isNotBlank()) add("格式" to post.fileExt.uppercase())
        if (post.fileSize > 0) add("文件大小" to "%.1f MB".format(post.fileSize / 1024.0 / 1024.0))
        // 「上传者」不是「画师」。实测那句在 `GalleryInfoSheet.kt:444-447`：yande.re 的 `author`
        // 与 Gelbooru 的 `owner` 都是**上传者的登录名**（真帖 1269641 的 `author=Arsy`，而画师是标签
        // `gijang`），只有 Safebooru 的 `tag_string_artist` 才是画师档。桌面浮层从前把这一枚摆在稿的
        // `.artist .nm`（署名）那一格上 —— 那是**用位置说出来的假读数**，判据 ③ 现在两端逐字比那颗词。
        // 真画师走下面的 [desktopDetailArtists]（站方标好的档位），两条路各说各的。
        if (post.author.isNotBlank()) add("上传者" to post.author)
        // 「本地状态」那一格今天只答得出"收藏没收藏"：已下载两端都没有判据
        // （`GallerySaver` 只在重名时 exists()，无对外查询口，且那颗件在桌面排除清单里），
        // 所以这里**不写**"未下载" —— 那是把"我们没查"说成"这台机器上没有"。
        add("本地状态" to if (favorite) "已收藏" else "未收藏")
    }

/**
 * 面板顶部那一行"身份"：站名 + 作品号。
 *
 * ⚠️ 稿上这里写的是**作品标题**（`海渊图书馆 · 第 4 页`），而标题这一格是**结构性没有**：
 * `GalleryPost` 没有 `title` 字段（三站 API 都不给），`GalleryCardTitle.titleOf` 在
 * `labels.isEmpty()` 时直接回 null。批 40 之后桌面的 `titleTags` **拿得到真数据了**，
 * 而这一格仍然摆"今天真有的那个身份"：桌面**没有一个消费它的格子**（Android 那一位住在
 * `gallery/ui/GalleryCardCaption.kt`，不在桌面编译面上）⇒ 接它之前先量"卡上那一行今天摆的是什么"，
 * 不许照对岸的形状凭空摆一格（手册 §0 那一族：钉了生产者而屏上没有那一格）。
 * 拍板是改稿 ⇒ 这里摆"今天真有的那个身份"，而不是留一格空的或编一个名字。
 */
internal fun desktopDetailHeadline(post: GalleryPost): String =
    "${post.site.displayName} · ID ${post.id}"

// ── P1：面板那一大位的三档，与翻页 ────────────────────────────────────────

/**
 * 面板顶部那一大位取哪一档。
 *
 * ⚠️ 名字与共享那颗 [GalleryPreviewQuality] **故意不同名**（`SAMPLE` 而不是 `LARGE`）：
 * 那枚枚举里 `LARGE` 指的是"墙上摆 sample 档"，而桌面这一侧三档是
 * 预览 / 大图 / 原图 —— 沿用旧名会让"大图"读起来像"原图"。映射只有一处（下面那颗函数）。
 */
internal enum class DesktopDetailTier { PREVIEW, SAMPLE, ORIGINAL }

/**
 * 这一档取哪个地址。
 *
 * **前两档逐字转交**共享那颗 [GallerySettingsModel.wallUrl]，桌面不重述任何判据：
 * 视频只吃静帧、"大图取不到退预览"、"不给空地址"三条都在它里面（Android 的墙上那一格
 * 也在吃同一颗，所以两端不会各自漂）。第三档是一颗字段（站方的 `file_url`），
 * 它没有判据可漂 —— 这也是方案 §6 第 3 条"把三档算式搬进共享面"最终**没做**的原因，
 * 详见 `docs/rounds/desktop-detail-panel-plan-2026-10-04.md` §8。
 */
internal fun desktopDetailTierUrl(post: GalleryPost, tier: DesktopDetailTier): String = when (tier) {
    DesktopDetailTier.PREVIEW -> GallerySettingsModel.wallUrl(post, GalleryPreviewQuality.PREVIEW)
    DesktopDetailTier.SAMPLE -> GallerySettingsModel.wallUrl(post, GalleryPreviewQuality.LARGE)
    DesktopDetailTier.ORIGINAL -> post.fileUrl
}

/**
 * 这一张**该摆**哪几档。摆不摆按"按下去拿不拿得到东西"定，三处各是一档：
 *
 * - 被分级挡住 ⇒ **一档都不摆**：三档换出来的都是同一句「分级遮挡」，那是一排假开关；
 * - 视频 ⇒ 撤「原图」：那一档的地址是原片 mp4，而站方没有更小的动图/视频转码档，
 *   解码器解不出来（Android 侧为这条在真机上闪退过一回）。
 *   ⚠️ 这里用 [GalleryPost.isVideo] **不是**在重述"视频取哪一枚地址" ——
 *   那族算式（`videoPosterUrl`）留在共享件里，判据 `DesktopGalleryDetailP1Test` ⑥ 钉着；
 * - 站方没给 `file_url` ⇒ 同样撤「原图」。
 *
 * 档位序稳定（预览 → 大图 → 原图）：换序会让那排分段控件自己跳。
 */
internal fun desktopDetailTiers(post: GalleryPost, masked: Boolean): List<DesktopDetailTier> {
    if (masked) return emptyList()
    return buildList {
        add(DesktopDetailTier.PREVIEW)
        add(DesktopDetailTier.SAMPLE)
        if (!post.isVideo && post.fileUrl.isNotBlank()) add(DesktopDetailTier.ORIGINAL)
    }
}

/**
 * 步进**钳在两端而不回绕**。
 *
 * 回绕（最后一张再往前跳回第一张）会被读成"这面墙自己重排了"，而它没有 ——
 * 那是把一件没发生的事演给用户看，与"宁可少摆不可摆错"对立。
 */
internal fun desktopDetailStep(index: Int, delta: Int, size: Int): Int =
    (index + delta).coerceIn(0, (size - 1).coerceAtLeast(0))

/**
 * 两枚步进各自可不可点。`size <= 1` ⇒ 两枚都不摆：
 * 只有一张时"下一张"按下去原地不动，与假开关同形（页码读数仍然摆，那是实话）。
 */
internal fun desktopDetailCanStep(index: Int, size: Int): Pair<Boolean, Boolean> {
    if (size <= 1) return false to false
    return (index > 0) to (index < size - 1)
}

// ── 批 2 ⑤：画师那一栏（站方档位），三档 ──────────────────────────────────

/**
 * 面板里"画师"那一栏今天处于哪一档。
 *
 * 四档里两档是**定论**（[Names] / [Unlabeled]），两档不是（[Fetching] / [NotAnswered]）。
 * 把它们分开的理由与批 1 那颗 [DesktopFaceProbe] 一字不差：把"没问到"记成"没有"，
 * 屏上就是替站方宣布了一句话 —— 而这一句恰好是用户最在意的那类（这位到底是谁画的）。
 *
 * ⚠️ [Names] 现在带**来路**（[DesktopArtistSource]）：站方那一页答不上时走的是离线词典的画师档，
 * 而那是"我们猜的"。Android 侧的注释早就写着"一桶'我们猜它是画师'不该长得跟'站方说它是画师'一模一样"
 * —— 它靠"不给 category ⇒ 退中性色"来说这件事，桌面这一排本来就不按档位上色，
 * 所以这里换成**一句读数**来说（批 41）。
 */
internal sealed interface DesktopDetailArtists {
    /** 面板刚打开、那一页还没答上（初始态，不是结论）。 */
    data object Fetching : DesktopDetailArtists

    /** 站方答上了，而这一张确实没标画师档 —— **这是一句定论**。 */
    data object Unlabeled : DesktopDetailArtists

    /** 那一页没答上（取失败、或站方没给那张页）**而词典里也没有这些名字**。**这不是"这张没有画师"**。 */
    data object NotAnswered : DesktopDetailArtists

    /** 摆出来的那几位，按站方自己的次序；[from] 说的是这一串名字**从哪来**。 */
    data class Names(val names: List<String>, val from: DesktopArtistSource) : DesktopDetailArtists
}

/** 画师那一栏那串名字的来路（批 41）：站方标的档位，还是离线词典的画师档。 */
internal enum class DesktopArtistSource { SITE, DICTIONARY }

/**
 * 站方档位 + 离线词典那份 → 画师那一栏。
 *
 * `categories` 的 `null` 与空表是两件事，这里**不许塌回同一档**：
 * `null` 是那颗词典件"读不出来"的交法（它自己的判据，见 `DesktopGalleryTagLexicon` 的类注释），
 * 而空表是"读出来了而这张一个标签都没分类"。后者可以定论说"没标"，前者只能说"没取到"。
 *
 * 两条来路的**作用域互斥**不写在这里，写在共享那颗 [galleryArtistNames] 里
 * （`buildGalleryTagBuckets`：站方那份非空 ⇒ 完全按它分而**不看** [fallbackArtistNames]；
 * 空 ⇒ 只兜画师这一栏，其余留在「标签」桶）。这里只按同一条件给结果贴来路，
 * 免得第二处再判一遍而两处漂。
 */
internal fun desktopDetailArtists(
    tagList: List<String>,
    categories: Map<String, Int>?,
    fallbackArtistNames: Set<String>,
    asked: Boolean,
): DesktopDetailArtists {
    if (!asked) return DesktopDetailArtists.Fetching
    val names = galleryArtistNames(tagList, categories, fallbackArtistNames)
    if (names.isNotEmpty()) {
        val from = if (categories.isNullOrEmpty()) DesktopArtistSource.DICTIONARY else DesktopArtistSource.SITE
        return DesktopDetailArtists.Names(names, from)
    }
    return if (categories == null) DesktopDetailArtists.NotAnswered else DesktopDetailArtists.Unlabeled
}

/**
 * 各档那一句人话；站方给的名字那一档留空 —— 名字本身就是内容，
 * 在它前面再写一句"画师如下"是给界面添噪声。
 *
 * ⚠️ [DesktopDetailArtists.NotAnswered] 那句**不许**出现"没有画师"（判据 ②′ 钉的就是这一条）。
 * ⚠️ 词典兜底那一档**必须**点名"离线词典"（批 41 判据 ④）：屏上摆着一串名字而说不出是谁给的，
 * 就是把"我们猜的"摆成"站方说的"。
 * ⚠️ "这次没取到"那一句按**词典那一态**分三种说法（批 78）：那句从前不管词典有没有被读过，
 * 都写"离线词典里也没有这一串标签"—— 库里没带词典、或那颗读不出时，那是一句我们没根据的声称。
 */
internal fun desktopArtistLineNotice(
    artists: DesktopDetailArtists,
    site: GallerySite,
    dictionary: DesktopDictWhy?,
): String = when (artists) {
    DesktopDetailArtists.Fetching -> "画师这一栏正在问站方"
    DesktopDetailArtists.Unlabeled -> "这一张站方没标画师"
    DesktopDetailArtists.NotAnswered -> when (dictionary) {
        null -> "画师这一栏这次没取到：${site.displayName} 那一页没答上标签分类，离线词典里也没有这一串标签"
        DesktopDictWhy.Absent -> "画师这一栏这次没取到：${site.displayName} 那一页没答上标签分类，这个包里也没带离线词典"
        is DesktopDictWhy.Unreadable -> "画师这一栏这次没取到：${site.displayName} 那一页没答上标签分类，离线词典这次没读出来"
    }

    is DesktopDetailArtists.Names -> when (artists.from) {
        DesktopArtistSource.SITE -> ""
        DesktopArtistSource.DICTIONARY -> "画师这一栏来自离线词典的画师档（${site.displayName} 那一页没答上标签分类）"
    }
}

/**
 * 标签那一枚**摆出来的字** —— 有中文译名时念「译名 (原名)」，没有就原样交回。
 *
 * ⚠️ **只改显示**（对岸 `gallery/ui/GalleryInfoSheet.kt:184` 那条口径逐字照办）：搜索与屏蔽那两路交的
 * 都还是站方那串原文。把「风景 (landscape)」写进屏蔽规则是一条**永远命中不了**的规则，
 * 把译名递进搜索则是一条空结果 —— 显示串与动作串混成一件事的那一格，两端都返过工。
 *
 * 三条形状都照对岸那颗 `tagDisplayLabel`（`GalleryInfoSheet.kt:1160`）：键按 `tag.lowercase()` 查
 * （词典按小写存而站方标签常带大写）、译名空白**或译名与原名同一串**都不摆（否则屏上出现
 * 「landscape (landscape)」这种复读）、命中时括号里留原名（用户要复制的仍是原名）。
 */
internal fun desktopTagDisplayLabel(tag: String, translations: Map<String, String>): String {
    val label = translations[tag.lowercase()]?.takeIf { it.isNotBlank() && it != tag }
    return if (label == null) tag else "$label ($tag)"
}

/**
 * 「正在关注的画师」那一栏与整页那一屏的名字：**有中文就整枚替换**（不带括号、不并列原名）。
 *
 * ⚠️ 这一格与 [desktopTagDisplayLabel] **不是一颗件**，因为对岸那两格自己就不是同一形：
 * `GalleryHomeSections.kt:317` 是 `translations[artist.name] ?: artist.name`（整枚替换、键不 lower），
 * 而候选与标签那两格是 `"$译名 (原名)"`（并列、键 lower）。合成一颗的后果是括号摆进 44dp 头像栏那一行
 * （名字那行 `maxLines = 1`，括一号就截断），或标签那格的原名被挤掉 —— 用户要复制的仍是站方那串。
 *
 * 与那颗件同一条纪律：**只改显示**，点那枚名字走的 `handoff(artist.sites, artist.name)` 交的还是原名。
 */
internal fun desktopArtistDisplayName(name: String, translations: Map<String, String>): String =
    translations[name]?.takeIf { it.isNotBlank() } ?: name

