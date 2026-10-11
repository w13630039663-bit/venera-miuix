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

    // 2026-10-04 第九批转正：**最后一枚**坦白页至此全部转真。
    // 原先那张"已经接好、只是没界面去看"的清单（`GalleryForYouMerge` + `isDisplayable/dedupKeys`）
    // 随缺席一起消失 —— 那一屏现在真的在读它们：抽样走 `GalleryRecommendations.recommendBySite`、
    // 每腿预算走 `GalleryLegGuard`、混摆走 `GalleryForYouMerge.page`、墙上那一关走第四批那层守卫。
    //
    // ⚠️ 这一屏仍然缺一件事，且**写在屏上**而不是不提：
    // 沿用上次那一屏（`GalleryForYouCache` 吃 `Context`，在桌面编译面的排除清单里）。
    // 续页**不缺**了 —— 第十批 ③ 立了那颗 `DesktopGalleryBatch` 持有件、批 8 把页码改成逐腿一份账，
    // 页尾那枚「下一页」读的就是它的相位。
    DISCOVER("发现", "发", null),

    // 2026-10-04 第二批：转成真页面。取数链那一整套判据**整颗已在桌面编译面**
    // （`gallery/domain` 是 srcDir 之一），所以这一屏没有新造任何一条口径：
    // 分段用 `GallerySearchSource.options`、每腿一笔超时用 `GalleryLegGuard.guard`、
    // 合成用 `GallerySearchMerge.interleave`、到底判据用 `exhaustedSites` + `GallerySearch.isExhausted`。
    SEARCH("搜索", "索", null),

    // 2026-10-04 第二批：这一域从坦白页转成真页面。
    // 取数链是 2026-10-04 骨架那批就核过的（`GalleryFavorites.favorites` 是真 StateFlow、
    // `GalleryFavorite.toPost` 已在），当时判它"最便宜"；这一批真的把它画出来。
    //
    // ⚠️ 与**漫画域的收藏夹**不是同一份数据：这里是 `GalleryFavoritesStore`（`gallery_favorites.json`），
    // 那边是 `LocalFavoriteDatabase`（本机 SQLite）。两处条数不相等是正常的，
    // 把两个域的收藏数念成同一个数就是假读数。
    FAVORITES("收藏", "藏", null),

    // 2026-10-04 第八批：转成真页面。原先这张"已经接好、只是没界面去看"的清单随缺席一起消失 ——
    // 名单、头像档、画师目录三颗都在 `DesktopGalleryPorts` 里接好了，今天那一屏真的在读它们。
    //
    // ⚠️ 这一屏**只读**，而它有两层：名单那一行与「看他的图」进去的单人页（批 19，一站一次、段头点名是哪一站）。
    // 头像走的是**按视口现取**那一路（第十批 ②），取不到的落字母座 —— 这一域原先记着的头像那一层，随现取一起过期了。
    // 成因与判据见 `DesktopArtistsPage` 与 `DesktopArtistProfilePolicy` 的类注释。
    ARTISTS("画师", "绘", null),

    // 本批回收：原先在 `VeneraDesktop.kt` 里那一批"写了从不读"的真链路
    // （EngineSession.explore / details / pages / pageImage + LocalFavoritesManager）
    // 搬到这里成了漫画域，所以这一域的 absence 是 null —— 它是**真能进**的第二个域。
    COMIC("漫画", "漫", null),

    // 2026-10-04 第七批转正：原先这三行念的"桌面那一侧是 getter-only、改不动"已经不实 ——
    // 四枚有消费点的档现在能改（写侧在 DesktopGalleryPreferences，落盘 + 推流两步），
    // 剩下六枚为什么不在这一屏，由设置页**逐颗点名**（DesktopGallerySettingsSpec.notOnScreen），
    // 不再由域级坦白页代答。域级缺席清单随缺席一起消失，与收藏/搜索那两域同一条规矩。
    SETTINGS("设置", "设", null),
    ;

    /** 这一域今天能不能点进去。[absence] 为空即能进，反过来亦然。 */
    val isWired: Boolean get() = absence == null

    /** 这一域在 rail 上是不是排在 8px 空隙之前那一组里（稿 `:354-358`）。 */

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
