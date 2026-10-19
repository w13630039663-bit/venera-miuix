package com.venera.compose.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.venera.compose.components.venera.VeneraGallerySourceMark
import com.venera.compose.components.venera.veneraAvatarRing
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.domain.GalleryCardTitle
import com.venera.compose.gallery.domain.GalleryTitleTag
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 卡片封面下方那一块：**来源标识 + 标题**（对齐参考图的形态）。
 *
 * 来源摆**这一行**（不再压在封面角上）：参考图里那枚站标与标题同处封面下方的一行，
 * 而压角方案在两站混排时要用户从图的四个角里找，反而更难读。
 *
 * 标题为 `null` 时**整行不画**（不是画一行空文字）：回退链四档（作品·角色 → 单档 → 画师名 →
 * 通用标签译名，见 `GalleryCardTitle`）都没命中的图就不该有那一行，
 * 摆一行空的会在横滑行里把封面高度参差拉开。
 *
 * ## 两档形状，按「传没传头像地址」分（2026-10-17 第三十二轮）
 *
 * - **`authorAvatar != null`**：头像 + 两行（标题 / 画师名）—— pixiv 推荐墙与云端书签墙
 *   走这一档（第二十四轮用户「和排行榜那种一样的有头像显示」）。
 * - **否则**：原来那一行（站标 + 标题），其余五处调用点逐像素不变。
 *
 * ⚠️ 判据是**传没传头像地址**、不是「有没有画师名」：画师名五面墙都非空，拿它判就会把
 * 主墙 / 搜索 / 收藏那几卡的形状一起改掉（那几处不传头像表，`authorAvatars[uid]` 是 null）。
 *
 * ## 浮层那一档已退役（第三十二轮）
 *
 * 2026-10-17 第二十四轮起，pixiv 推荐墙的文字区曾**浮在封面下沿**（半透明毛玻璃压在图上）。
 * 用户第三十二轮原话：「我之前说的做这个浮层，改成图片能在外面预览全部显示那种不要浮层，
 * 这样飞行动画也会好很多」—— 浮层把封面底沿压掉一条，图在网格里看不全；且飞行起点
 * （PixelCopy 截的封面那一块）带着那条 UI，要额外做「两段画 + 随进度淡出」才接得上画面框。
 * 现在文字区回到封面**下方**：图全部显示，起点与落点同构（都是纯图），飞行动画不再需要
 * 那道减退机理（`GalleryFlyIn.noteCaptionHeight` 那一套随之退役）。
 *
 * @param title 已经算好的标题（见 [rememberGalleryCardTitles]）；null = 这一张没有可显示的标题。
 * @param authorAvatar 作者头像地址。非 null = 走「头像 + 两行」那一档（见上）；
 *   null = 不画头像（其余调用点逐像素不变）。
 * @param authorName 作者名。`null`/空串 = 不画第二行。
 * @param onOpenArtist 点**署名行那颗头像**（用户 2026-10-19：「改成点击画师头像进入画师页」）。
 *   null = 头像不可点（图库三站的墙不传这张表，天然不受影响）。
 *
 * ⚠️ 只有**头像那颗圆**吃掉点击，整行不吃：这一行的右边紧跟着卡片的点击区，
 * 把整行做成可点就会与"点卡进大图"抢手势 —— 而"点脸去看这个人"是用户点名的那一下。
 */
@Composable
internal fun GalleryCardCaption(
    site: com.venera.compose.gallery.data.GallerySite,
    title: String?,
    authorAvatar: String? = null,
    authorName: String? = null,
    imageLoader: coil3.ImageLoader? = null,
    onOpenArtist: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    // ── 「头像 + 两行」那一档（递了头像表的墙：pixiv 推荐墙 / 云端书签墙）──
    if (authorAvatar != null) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        ) {
            // 头像 24dp（`space10`）：2026-10-18 用户「作者的头像可以大点，画廊首页推荐的头像也大点吧」
            // —— 从 `space9`(20dp) 提到这一档。两面墙共用本组件（推荐墙 / 云端书签墙 / 搜索墙），
            // 所以一处改三处一起变，这正是当初把它收成一颗组件的目的。
            // 再大一档就是 `space11`(32dp)：那已经高过"标题 + 作者名"两行的合计（约 30dp），
            // 文字区会被头像压成一条，读起来像"头像卡着两行字"。
            Box(
                modifier = Modifier
                    .size(tokens.spacing.space10)
                    .veneraAvatarRing(RoundedCornerShape(tokens.shape.small))
                    .background(tokens.color.surfaceContainerHigh)
                    // 点脸 = 进这一位的画师页；不传回调就不挂点击（不给假把手）。
                    .then(
                        if (onOpenArtist != null) {
                            Modifier.clickable(onClick = onOpenArtist)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (authorAvatar.isNotBlank() && imageLoader != null) {
                    AsyncImage(
                        model = authorAvatar,
                        contentDescription = authorName,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // 表里有这一项但地址是空串（站方没给）⇒ 首字母座，
                    // **不拿别人的头像顶**。判据走 `authorAvatar != null`（墙级传了表）
                    // 而不是逐张判空，就是为了让这种卡与同墙其余卡同档，不在一面墙里
                    // 混两种形状（瀑布流会高矮不齐）。
                    Text(
                        text = authorName.orEmpty().take(1),
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
                        maxLines = 1,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    Text(
                        text = title,
                        fontSize = tokens.type.caption,
                        // 行高写死（`TEXT_LINE_HEIGHT` 那一档，1.2f 照抄 miuix paragraph）：
                        // 不指定时行高随字形走（拉丁/CJK/emoji 差 1~2px），网格里就是卡高参差。
                        lineHeight = TEXT_LINE_HEIGHT.em,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        // 一张画可能挂着好几个作品/角色档的标签，标题会很长 —— 省略号收口。
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!authorName.isNullOrBlank()) {
                    Text(
                        text = authorName,
                        fontSize = tokens.type.badge,
                        lineHeight = TEXT_LINE_HEIGHT.em,
                        color = tokens.color.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = tokens.spacing.space1),
                    )
                }
            }
        }
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        VeneraGallerySourceMark(
            site = site,
            size = tokens.spacing.sourceMarkSize,
        )
        if (title != null) {
            Text(
                text = title,
                // 回到 caption（批次 N · N4）。批次 M · M7 那一句原文是：
                // 「降一档：这一行的主体是封面，标题是署名级别的补充；与旁边那枚站标（18dp）同一量级才读成一行」——
                // 当时用户说的是"图标太大、字体也大"，我把两样一起降了，结果标题比参考图还小一档。
                // 图标那一半（18dp）是对的、留着；字号这一半今天回改。
                fontSize = tokens.type.caption,
                color = tokens.color.textPrimary,
                maxLines = 1,
                // 一张画可能挂着好几个作品/角色档的标签，标题会很长 —— 省略号收口，
                // 卡片宽度是定死的（横滑行里每一枚都同宽）。
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 这一批条目的**卡片标题**（按 uid 索引）。
 *
 * ── 为什么是"整批查一次"而不是"每张卡各查一次" ──
 *
 * 标题来自离线词典（`GalleryTagLexicon.titleTags`），而它是要开 SQLite 的：
 * 一屏几十张卡各查一次 = 几十趟查询 + 几十次游标开关。整批摊平去重后查一次，
 * 成本与"一屏多少张"基本无关（名字去重之后也就几百上千枚，那层还有分批）。
 *
 * ── 为什么 `null` 与空 map 要分得开 ──
 *
 * - **`null`（词典打不开）**：屏上表现为"所有卡都没有标题行"。这是**我们的故障**，
 *   与"这些图确实没有作品/角色标签"在屏幕上长得一模一样 —— 所以打一条 warn 日志，
 *   别让它静默（同 `GalleryTagDictionary.openDatabase` 那条口径）。
 * - **空 map**：正常的"这一批图都没有作品/角色标签"，不打日志。
 *
 * @return `uid → 标题`；没有标题的那些 uid **不会出现在表里**（调用方据此不画那一行）。
 */
@Composable
internal fun rememberGalleryCardTitles(posts: List<GalleryPost>): Map<String, String> {
    val context = LocalContext.current
    // 摊平这批图的所有标签去重：它是查询键，也是 remember 的键 ——
    // 键错了就会拿着上一批的名字画这一批的标题（"卡上写着 A 串、点进去是 B"那类返祖）。
    val names = remember(posts) { posts.flatMap { it.tagList }.map { it.lowercase() }.distinct() }
    var labels by remember(names) { mutableStateOf<Map<String, GalleryTitleTag>>(emptyMap()) }
    LaunchedEffect(names) {
        if (names.isEmpty()) {
            labels = emptyMap()
            return@LaunchedEffect
        }
        val found = GalleryPorts.of(context).lexicon.titleTags(names)
        if (found == null) {
            // 词典打不开：这一批卡都不会有标题行。这条必须响 —— 静默的话它和
            // "这些图没有作品标签"完全同形（理由见上面那段）。
            android.util.Log.w(TAG, "画廊标签词典打不开，卡片标题这一批全部不可用（${names.size} 个名字）")
            labels = emptyMap()
        } else {
            labels = found
        }
    }
    // 只有"这一批图 + 这一份词典"都定了才重算，避免每次重组重拼字符串。
    return remember(posts, labels) {
        if (labels.isEmpty()) {
            emptyMap()
        } else {
            posts.mapNotNull { post ->
                GalleryCardTitle.titleOf(post.tagList, labels)?.let { post.uid to it }
            }.toMap()
        }
    }
}

private const val TAG = "GalleryCardTitle"

// ── 卡片角上那三枚小胶囊（2026-10-18 从 GalleryScreen.kt 搬到这里）────────────
//
// 搬家的理由：它们原先挂在 GalleryScreen.kt 里而且是 private，于是 **pixiv 首页那一族卡
// （`GalleryPixivHome.PixivWorkCard`）用不上** —— 那一族卡自己画了名次角标与收藏 ♥，
// 却没有视频与 AI 两枚。用户 2026-10-18 报「开了 AI 角标，整个画廊一个都没见到」，
// pixiv 域那一半的原因就在这里。搬到本文件（六处墙共用的 caption 件）之后两边同包可用。
//
// 三枚现在都是 internal：同包可见，跨包仍是私有（漫画侧那 6 页不受影响）。

/**
 * 视频角标：一个「▶」，站方给了时长就带上秒数。
 *
 * 摆右下角。给视频打标不是装饰 ——
 * 站方那档 preview 是**静帧 jpg**，不打标它就是一张看着正常、点开什么也不会动的图，
 * 用户没法知道那张可以播。时长在 yande.re 那侧拿不到（实测字段表里没有），
 * 所以只在有的时候显示，不写"0s"（秒数单位的口径见 `GalleryPost.durationLabel`）。
 *
 * ⚠️ 2026-09-30 起**左下角那枚来源胶囊撤了**：来源搬到封面下方那一行
 * （与标题并排，见 [GalleryCardCaption]）。所以这里的"与来源分角不抢位"那条理由
 * 已经不存在，四个角只剩视频与 AI 两枚。
 */
@Composable
internal fun BoxScope.GalleryVideoPill(durationLabel: String) {
    GalleryCornerPill(if (durationLabel.isEmpty()) "▶" else "▶ $durationLabel", Alignment.BottomEnd)
}

/**
 * AI 角标：一枚「AI」。
 *
 * 摆**左上角**。判据与"没有信号就不摆"这条硬口径同一条：站方没打 AI 标签的条目一律不摆，
 * 不靠"画风像 AI"编一个读数出来（判据见 [com.venera.compose.gallery.domain.GalleryAi]）。
 *
 * ⚠️ 摆不摆由**画廊自己那枚开关**管（`prefs.galleryAiBadge`），与漫画守卫页的 block_ai 是两把。
 * 屏蔽开着时这条根本不会上屏，所以角标的真正用途是"我不屏蔽，但我要看得出来"。
 */
@Composable
internal fun BoxScope.GalleryAiPill() {
    GalleryCornerPill("AI", Alignment.TopStart)
}

/**
 * 多页角标：一枚「4P」（用户 2026-10-18「要能在图片卡片上另加看出这张有几页，而不是点进作品才知道」）。
 *
 * 摆**右下角**：左上是 AI，而 pixiv 首页那一族卡把右上给了名次或 AI —— 右下是两族卡都空着的那一格。
 * 与视频那一枚同角但**不会共存**：pixiv 的卡永不是视频（站方给的是 jpg/png 地址），
 * 而 booru 永不带页数（一张 = 一幅，字段表里没这一格）—— 判据在 [GalleryPost.pagesBadge]，
 * 「站方没给」与「只有一页」都不摆，所以这一枚永远只有一个出处。
 */
@Composable
internal fun BoxScope.GalleryPagesPill(badge: String) {
    GalleryCornerPill(badge, Alignment.BottomEnd)
}

/**
 * 卡片角上那枚小胶囊。
 *
 * 不复用 [com.venera.compose.components.venera.VeneraSourceBadge]：那枚是**左上角小圆角**、
 * 且被漫画侧 6 个页面共用，改它等于一起动别人。这里的口径是"胶囊 + 更小"，
 * 但**尺寸全部取现成 token**（badge 字号 / badge 内边距 / extraLarge 圆角），不新造数字。
 * 底板仍用固定深色：封面颜色不可控，只有固定底板能保证任何图上都可读。
 *
 * 刻意**不可点**：点下去要的是"只看这一站"，本仓还没有筛选态的出口，做了就是假按钮。
 *
 * 2026-09-30 第二轮：它只剩视频 / AI 两枚在用（来源那一枚换成了
 * [VeneraGallerySourceMark] 图形标识），所以这里只留形状，不再有"源名"这个用途。
 */
@Composable
internal fun BoxScope.GalleryCornerPill(text: String, alignment: Alignment) {
    val tokens = VeneraTokens
    Text(
        text = text,
        fontSize = tokens.type.badge,
        fontWeight = tokens.type.weightMedium,
        color = StatusColors.OnBadgeSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .align(alignment)
            .padding(tokens.spacing.badgeInset)
            .background(
                StatusColors.BadgeSurface.copy(alpha = 0.72f),
                RoundedCornerShape(tokens.shape.extraLarge),
            )
            .padding(
                horizontal = tokens.spacing.badgeHorizontalPadding,
                vertical = tokens.spacing.badgeVerticalPadding / 2,
            ),
    )
}
