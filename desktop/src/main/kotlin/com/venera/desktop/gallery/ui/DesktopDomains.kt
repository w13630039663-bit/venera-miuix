package com.venera.desktop.gallery.ui

/**
 * rail 七个域的**定义**，以及每个域今天为什么进不去。
 *
 * ## 七枚的次序与分组逐条照稿（`docs/designs/windows-gallery-home-touhou-2026-10-03.html:353-364`）
 *
 * ```
 * 图库 发现 搜索 收藏 画师        ← `:354-358`，一组
 *   ── 8px 空隙 ──                ← `:359`
 * 漫画                            ← `:360`
 *   ── 弹性留白 ──                ← `:361` 的 `.spacer`，把「设置」推到底
 * 鸟居 / 博麗神社                 ← `:362-363`，装饰层，本批不做
 * 设置                            ← `:364`，贴底
 * ```
 *
 * ## 缺席分**两档**，界面不许把它们念成同一句话
 *
 * 「这一域进不去」有两种完全不同的成因：
 *
 * - [DesktopAbsenceKind.NO_CHAIN]：**本机压根没有这条链**。
 * - [DesktopAbsenceKind.NO_UI]：**取数链已经接线到桌面，只是没有界面**。
 *
 * 混为一谈的代价是把"接好了但没画"念成"这台机器上没有" —— 那会让后来接手的人去找一条
 * 根本不存在的断点。所以每一条缺席都带一张 [DesktopAbsence.wired]，把已经能跑的件点名列出来：
 * 谁要认领这一域，看那张清单就知道差的是哪一层。
 *
 * ## 为什么 rail 上的缺席域**仍然可点**
 *
 * 本仓不许"按了不发生任何事"的开关，也不许"画成灰让人以为点进去能看到说明"的形状。
 * 缺席域点了就切过去，目的地是一屏把理由念全的坦白页 —— 换言之 **rail 上七枚全都是真导航**，
 * 没有一枚是装饰。
 *
 * 这与图库域 pane 里那四行**不可点**的未实现行不是同一件事，别拿同一条理由去判两边：
 * 那四行是"同一个域内部的某一页还没做"，域本身是通的、把它们撤了也说得通；
 * rail 上七枚之间是**并列**关系，缺席域必须给自己一个立足点，否则用户只能观察到
 * "点了又弹回图库"，而看不到为什么。
 */
internal enum class DesktopDomain(
    val title: String,
    val glyph: String,
    /** 进不去的理由；`null` = 这一域今天真能进。 */
    val absence: DesktopAbsence?,
) {
    GALLERY("图库", "图", null),

    DISCOVER(
        "发现",
        "发",
        DesktopAbsence(
            DesktopAbsenceKind.NO_UI,
            "猜你喜欢那一屏没做：编排在、ViewModel 不在",
            listOf(
                "GalleryForYouMerge（跨页跨站去重、排掉已收藏，`.domain` 已整颗在桌面编译面）",
                "GalleryMerge.isDisplayable / dedupKeys（能不能摆上屏的判据，与日榜共用同一份）",
            ),
        ),
    ),

    SEARCH(
        "搜索",
        "索",
        DesktopAbsence(
            DesktopAbsenceKind.NO_UI,
            "三站 searchPosts 已通，缺那面结果墙",
            listOf(
                "BoardSource.searchPosts(tags, page, limit)（三站 board 都继承它）",
                "BoardSource.searchTags(term)（标签补全）",
                "GallerySearch.tagBudget(site) / fitsTagBudget（每站的标签预算）",
            ),
        ),
    ),

    FAVORITES(
        "收藏",
        "藏",
        DesktopAbsence(
            DesktopAbsenceKind.NO_UI,
            "名单在流已落盘，缺一面墙来读它",
            listOf(
                "GalleryFavorites.favorites（StateFlow<List<GalleryFavorite>>）",
                "GalleryFavoritesStore（落本机数据目录的 JSON，与 Android 那一侧同一份磁盘形状）",
                "toggle / removeAll / clear（三个写动作都在）",
            ),
        ),
    ),

    ARTISTS(
        "画师",
        "绘",
        DesktopAbsence(
            DesktopAbsenceKind.NO_UI,
            "关注名单在流，没有画师墙也没有单人页",
            listOf(
                "GalleryArtistFollows.follows（StateFlow<List<GalleryArtistFollow>>）",
                "GalleryArtistDirectory.danbooruArtistUrls / danbooruArtistCredits",
                "GalleryArtistDirectory.probeAvatarUrl（fallback 的 fanbox / Mastodon 两路）",
            ),
        ),
    ),

    // 本批回收：原先在 `VeneraDesktop.kt` 里那一批"写了从不读"的真链路
    // （EngineSession.explore / details / pages / pageImage + LocalFavoritesManager）
    // 搬到这里成了漫画域，所以这一域的 absence 是 null —— 它是**真能进**的第二个域。
    COMIC("漫画", "漫", null),

    SETTINGS(
        "设置",
        "设",
        DesktopAbsence(
            DesktopAbsenceKind.NO_UI,
            "读数都在，缺那颗能改的设置页",
            listOf(
                "GalleryPreferences 那 12 枚 StateFlow（桌面对应物 DesktopGalleryPreferences）",
                "GalleryContentGuard.rules / nsfwMaskMode（直连 GuardRuleStore，桌面真跑）",
                "⚠️ 桌面那一侧是 getter-only：构造时读一次，今天没有写侧，改不动",
            ),
        ),
    ),
    ;

    /** 这一域今天能不能点进去。[absence] 为空即能进，反过来亦然。 */
    val isWired: Boolean get() = absence == null

    /** 这一域在 rail 上是不是排在 8px 空隙之前那一组里（稿 `:354-358`）。 */
    val isInTopGroup: Boolean get() = ordinal <= ARTISTS.ordinal

    /**
     * 这一域在漫画之后那一段弹性留白的前面 —— 只有「漫画」为真，
     * 稿上 `.spacer`（`:361`）就插在它与「设置」之间，作用是把设置推到底。
     */
    val precedesSpacer: Boolean get() = this == COMIC
}

/** 缺席的两种成因。界面上必须念不同的话，见 [DesktopDomain] 的类注释。 */
internal enum class DesktopAbsenceKind {
    /** 本机压根没有这条链。 */
    NO_CHAIN,

    /** 取数链已经接线到桌面，缺的是把它画出来的那一屏。 */
    NO_UI,
}

/**
 * 一个域为什么进不去。
 *
 * @param reason **短句**，作 pane 里那行的 caption。字数有硬预算：pane 224 扣掉
 *   [DesktopGalleryMetrics.captionStart] 之后一行约 28 个汉字、`maxLines = 2`，
 *   写长了就被省略号吃掉 —— 那等于"缺席"没被说出来。句长由判据钉在 30 以内。
 * @param wired 已经接线、真能跑的件。**逐颗点名到成员**，不许写"底层已就绪"这类话。
 *   这一张清单同时承担了"缺席是哪种缺席"的判断：非空 = [DesktopAbsenceKind.NO_UI]
 *   （接好了但没画），空 = [DesktopAbsenceKind.NO_CHAIN]（本机真没有）。
 */
internal data class DesktopAbsence(
    val kind: DesktopAbsenceKind,
    val reason: String,
    val wired: List<String>,
)
