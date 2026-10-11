package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.domain.GalleryAi
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryGuard
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GallerySettingsModel

/**
 * 桌面三面墙**共用**的分级判据层 —— 把 Android 侧 `buildGalleryWall` 的那一套待遇搬到桌面。
 *
 * ## 为什么这一颗必须存在（它补的是一个安全相关的缺口，不是"顺手对齐"）
 *
 * 桌面从 S2-A 起就在读 [GalleryContentGuard.nsfwMaskMode] 与 `.rules`，
 * 而**没有任何一处消费它们**：Android 侧那把过滤判据 `buildGalleryWall` 住在 `gallery/ui/`，
 * 那颗目录**不在桌面编译面内**（桌面只共享 `gallery/data` 与 `gallery/domain`）。
 * 于是同一份存档、同一条屏蔽规则，在手机上被挡着、在桌面上直接摆出来 ——
 * 用户设置的「成人内容处理 = 隐藏」与「屏蔽 AI 生成」在桌面**形同虚设**。
 *
 * ## 判据不重抄：调的是共享那颗 `GalleryGuard.maskStateFor`
 *
 * 这一层只做"把三档待遇落到桌面上"，**不自己判**：
 * 每一张卡走的是与 Android 逐字同一颗 [GalleryGuard.maskStateFor]（同一个 `maskMode` 字符串、
 * 同一个 `blockedByUser` 形状）。理由很硬：判据抄第二份就会漂，而漂的那一侧永远是桌面
 * （桌面没人拿它跟漫画侧对表）。用例 `DesktopWallGuardPolicyTest` 钉的就是"这里只许调它、不许自己写条件"。
 *
 * ## 与 Android 唯一不同的地方：遮挡档**不发起取图**
 *
 * Android 的 `BLURRED` 档是"图取回来再糊掉"。桌面改成"根本不去取"：
 * 一张注定要盖住的内容，没必要先花一次流量和一次解码再盖住它。
 * 这不是省 —— 是**少一次外泄**：遮挡的语义本来就是"这台机器的这个人不该看到它"。
 * 这一条差异写在卡上的角标里（「分级遮挡」），不藏在代码注释里。
 *
 * ⚠️ 只有 `HIDDEN` 与 `BLURRED` 两档的**判定**与 Android 同源；
 * `OFF` 档（不遮挡）桌面与 Android 一样原样摆。
 */
internal object DesktopGalleryWallPolicy {

    /**
     * "AI 生成"这条屏蔽项在读数里显示成的规则原文。
     *
     * ⚠️ 这是 Android 侧 `gallery/ui/GalleryScreen.kt` 里那枚 `AI_BLOCKED_RULE` 的**文本重述**
     * （那颗常量在桌面编译面外，拿不到）。重述就是会漂的那种重复，
     * 所以 `DesktopWallGuardPolicyTest` 拿文本回核两头是不是同一串 —— 与
     * `DesktopGalleryPreferencesTest` 对键名做的那件事同一形状。
     */
    const val AI_BLOCKED_RULE = "AI 生成"

    /**
     * 算出一面墙。纯函数（不 require @Composable），所以能被单测直接取值 ——
     * 与 `desktopDomainRows` 同一条理由：能真跑的判据永远比能扫的判据可靠。
     *
     * @param maskMode 直接取 [GalleryContentGuard.nsfwMaskMode] 的**原值**，不在这里翻译
     * @param blockAi 取 `prefs.galleryBlockAi`
     * @param guard 命中用户规则的那一问走它，与 Android 调用点同一颗方法、同一对入参
     */
    fun of(
        posts: List<GalleryPost>,
        maskMode: String,
        blockAi: Boolean,
        guard: GalleryContentGuard,
    ): DesktopWall {
        val cards = ArrayList<DesktopWallCard>(posts.size)
        val blockedRules = LinkedHashSet<String>()
        var blockedCount = 0
        var hiddenByRating = 0
        posts.forEach { post ->
            val blocked = guard.blockedGalleryRule(author = post.author, tags = post.tagList)?.pattern
                ?: if (blockAi && GalleryAi.isAiMarked(post.tagList)) AI_BLOCKED_RULE else null
            when (GalleryGuard.maskStateFor(post, maskMode, blockedByUser = blocked != null)) {
                GalleryGuard.HIDDEN -> if (blocked != null) {
                    blockedCount++
                    blockedRules += blocked
                } else {
                    hiddenByRating++
                }

                GalleryGuard.BLURRED -> cards += DesktopWallCard(post, masked = true)
                else -> cards += DesktopWallCard(post, masked = false)
            }
        }
        return DesktopWall(cards, blockedCount, blockedRules.toList(), hiddenByRating)
    }
}

/** 一张卡 + 它要不要被盖住。`masked` 为真时**不去取图**（见 [DesktopGalleryWallPolicy] 类注释）。 */
internal data class DesktopWallCard(val post: GalleryPost, val masked: Boolean)

/**
 * 点卡这件事的**完整载荷**：哪一批卡 + 当前第几张。
 *
 * ## 为什么不止交一张卡
 *
 * P0 交的是单张（`DesktopWallCard`），那时浮层只能"看这一张"。P1 要翻页（← / → 与页码），
 * 而"第 3 / 40 张"里的 40 **只有墙自己知道** —— 它是这一屏当下真上屏的那一批
 * （守卫挡掉的不算、续页追加的要算）。把它交给应用根去自己拼，等于让另一处去猜"这批有多大"，
 * 而那一猜一旦与墙不一致，页码就成了假读数。
 *
 * ⚠️ 与第十批 ③ 那颗 [DesktopGalleryBatch] 不是一颗件，也不该合成一颗：
 * 那颗管"取回来的这一批"（含被挡下的、含去重键与到底名单），这颗管"屏上这一批的第几张"。
 * 合成一颗的后果是页码开始把被挡下的也数进去。
 */
internal data class DesktopWallSelection(val cards: List<DesktopWallCard>, val index: Int) {
    val size: Int get() = cards.size
    val card: DesktopWallCard? get() = cards.getOrNull(index)
}

/**
 * 一面墙的产物。四个字段各自是一句不同的话，别合并：
 * `blockedCount` 是"你的规则挡下的"，`hiddenByRating` 是"分级设置收起的"，
 * 让前者替后者说话就是把"你屏蔽的"念成"你不想看的"。
 */
internal data class DesktopWall(
    val cards: List<DesktopWallCard> = emptyList(),
    val blockedCount: Int = 0,
    val blockedRules: List<String> = emptyList(),
    val hiddenByRating: Int = 0,
)

/**
 * hero 那一大位要不要取图。遮挡档给 `null` = **根本不去取**。
 *
 * 为什么单独一颗函数：hero 原来直接拿 `daily.merged.posts.firstOrNull()`，
 * **绕过了分级这一整关**，而且它取的是 `backdropUrl`（中档大图，比墙上缩略档更清晰）——
 * 也就是"该挡的内容在最大最清楚的那一格上摆着"。判据走同一颗，只是把"要不要发起这一次取图"
 * 提成可单测的形状，而不是让 `masked` 那一步埋在 composable 的 `if` 里。
 */
internal fun heroBackdropOf(card: DesktopWallCard?): String? =
    card?.takeIf { !it.masked }?.post?.backdropUrl

/**
 * 卡上**发起哪一次取图**。`null` = 根本不发起。
 *
 * 遮挡档排在清晰度档前面：用户选了"清楚一点"那一档，也不该让一张注定要盖住的图先下下来。
 * 取哪一档本身不在这里判 —— 那是共享那颗 [GallerySettingsModel.wallUrl]（它管着
 * "LARGE 取不到就退回 preview，不给空地址"与"视频条目永远只吃静帧"两条，桌面抄第二份就会漂）。
 */
internal fun tileImageRequest(post: GalleryPost, masked: Boolean, quality: GalleryPreviewQuality): String? =
    if (masked) null else GallerySettingsModel.wallUrl(post, quality)

/**
 * 墙上摆几列。
 *
 * 两半颗各有各的主人：**AUTO** 那一档要吃"内容区实测宽"，那只有 composable 侧量得出来，
 * 所以自适应那半颗归桌面（[DesktopGalleryMetrics.imageWallColumnCount]）；
 * **TWO / THREE** 是用户的显式覆盖，语义归共享那颗 [GallerySettingsModel.gridColumns]
 * （"窗口再宽也不许跟着动"），桌面这边只许转交、不许自己写 `-> 2`。
 */
internal fun desktopWallColumnCount(contentWidthDp: Float, mode: GalleryColumnMode): Int =
    GallerySettingsModel.gridColumns(mode, DesktopGalleryMetrics.imageWallColumnCount(contentWidthDp))

/** 角标那两个字。与 Android 侧 `GalleryScreen.kt` 那颗 `GalleryAiPill` 同串，由用例逐字回核。 */
const val AI_BADGE_TEXT = "AI"

/**
 * 这一张摆不摆「AI」角标。
 *
 * 两枚"没有信号就不摆"叠在一起：开关关着不摆，站方没打 AI 标签也不摆 ——
 * 判据只用 [GalleryAi.isAiMarked]（站方标签），**不靠画风推断**。
 * 它与 `blockAi` 是两枚独立开关：屏蔽关着时总得有个读数，所以默认档是"摆"。
 */
internal fun aiBadgeFor(post: GalleryPost, badgeOn: Boolean): Boolean =
    badgeOn && GalleryAi.isAiMarked(post.tagList)

/**
 * 墙上那两笔缺席读数。返回空表 = 这一墙没有任何东西被挡，调用方一句都不该念。
 *
 * 两句的文案是**逐字照 Android**：`gallery/ui/GalleryFeedReadout.kt:40` 的
 * `ratingHiddenFragment`（「N 张按「成人内容处理」收起」）与 `:36` 的
 * `blockedByRulesFragment`（「N 张命中屏蔽规则 x、y」）。
 * 同一件事在两头念成两个词，用户就会以为桌面多了一套规则 —— 屏蔽那句里的**笔数**
 * 曾经被这里漏掉（写成"命中屏蔽规则 x、y"），漏掉的就是"一条规则挡下三张"与
 * "三条规则各挡一张"这两种情形的差别。
 */
internal fun DesktopWall.guardFragments(): List<String> = buildList {
    if (hiddenByRating > 0) {
        add("$hiddenByRating 张按「成人内容处理」收起")
    }
    if (blockedRules.isNotEmpty()) {
        add("$blockedCount 张命中屏蔽规则 ${blockedRules.joinToString("、")}")
    }
}
