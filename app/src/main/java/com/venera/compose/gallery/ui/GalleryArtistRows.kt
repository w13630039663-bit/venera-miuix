package com.venera.compose.gallery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.ImageLoader
import com.venera.compose.R
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.gallery.data.DanbooruArtistClient
import com.venera.compose.gallery.data.GalleryArtistFollowsStore
import com.venera.compose.gallery.data.GalleryArtistProbeClient
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivClient
import com.venera.compose.gallery.data.YandeReClient
import com.venera.compose.gallery.domain.GalleryArtistAvatarProbe
import com.venera.compose.gallery.domain.GalleryArtistFollow
import com.venera.compose.gallery.domain.GalleryArtistFollows
import com.venera.compose.gallery.domain.GalleryArtistLink
import com.venera.compose.gallery.domain.GalleryArtistLinkPlatform
import com.venera.compose.gallery.domain.GalleryArtistLinks
import com.venera.compose.gallery.domain.GalleryArtistProfile
import com.venera.compose.ui.tokens.GalleryPlatformColors
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

/**
 * 详情面板里的「这位画师」一行（批次 L · L5）：头像 + 平台入口 + 关注。
 *
 * 形态按用户 2026-09-30 拍板的那一档（「每位画师一行：头像 + 图标 + 关注」）。三条不是顺手就做的设计：
 *
 * ## 面板打开才取数
 *
 * 一位画师最贵要三笔请求（站方画师记录 → pixiv 用户档 → 头像图本身）。挂在墙上是每张卡都要预热，
 * 挂在这里只有真点开的那位才花钱。所以这一份状态**归本 composable 持有**，面板一关就跟着组合一起没。
 *
 * ## 平台入口已经带上品牌图标了（2026-10-01 收口）
 *
 * 用户 2026-10-01（批次 Q+R 批量 6）拍板"社交链接胶囊要带平台图标"。这一行当时**没做**，
 * 理由是"资产没落地之前这里仍是文字，不留半截实现" —— 那条前提已经作废：
 * 图标资产走 `scripts/build_platform_icons.mjs`（与两枚站标同一条管线，Simple Icons CC0-1.0）
 * 已经落地为 `res/drawable/ic_platform_*.xml` 五枚，所以这一行现在与画师介绍页
 * **共用同一枚 [PlatformBadge]**（它从介绍页那一份迁到这里，改成 `internal` 两处共用）。
 *
 * ⚠️ 这一条**与来源标识那枚不是同一条**，别互相套用（2026-09-30 L12 之后容易读混）：
 * 卡片角与首页画师行上的两枚**已经是站方真图标**（`VeneraGallerySourceMark`），因为那两站
 * 各有一枚现成原档、且只塞两个资产。平台入口这一排是 pixiv / X / FANBOX / Instagram 若干家，
 * 每家都要往 APK 里塞一份第三方品牌资产、还得跟着人家改版走 —— 体积与授权记在批次 Q+R 方案文档里。
 *
 * ## 取不到 = 什么都不摆，且不说"没有"
 *
 * 外链为空、头像为空、整笔请求失败，这三种在屏上**长成同一个样子**（不摆那一枚），
 * 但语义完全不同，所以失败那一路必须在日志里留下一句话。用户在屏上永远读不到
 * "这位没有 pixiv"这种我们没资格断言的话 —— 只有真的站方答上了"没有这条记录"才是空。
 *
 * ## 入口与头像还有一条备用的路：这条帖子自带的出处
 *
 * Gelbooru 侧站方给不出画师外链（要凭据、且供体 Danbooru 卡盾），而**出处那一栏实测覆盖 18/20**
 * （pixiv / baraag / twitter / fanbox…），它是站方逐条记在帖子上的。所以 [source] 会作为**第二顺位**
 * 参与两件事：入口缺档时由 [com.venera.compose.gallery.domain.GalleryArtistLinks.fromSource] 补一枚
 * （认得出"这个人"的形状才补，作品页一律不认），头像缺档时由
 * [com.venera.compose.gallery.domain.GalleryArtistAvatarProbe] 派生 fanbox / Mastodon 的公开端点。
 * 站方登记的记录始终排在前面 —— 画师页自己声明的链接比"这张图发布在哪儿"更像本人。
 */

/** 一位画师在面板里的取数结果。[loaded]=false 是"还没回来"，与 urls 为空是两件事。 */
private class ArtistCredits(
    val links: List<GalleryArtistLink> = emptyList(),
    val avatarUrl: String? = null,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryArtistCreditRows(
    site: GallerySite,
    artists: List<String>,
    translations: Map<String, String>,
    source: String,
    imageLoader: ImageLoader,
    /** 点头像 + 名字那一整块：进画师介绍页（批次 Q+R · 批量 4 改判，原来是"点 = 搜这一枚标签"）。 */
    onOpenArtist: (String) -> Unit,
) {
    if (artists.isEmpty()) return
    val context = LocalContext.current
    val tokens = VeneraTokens
    val scope = rememberCoroutineScope()
    val store = remember { GalleryArtistFollowsStore.getInstance(context) }
    val follows by store.follows.collectAsState()

    var credits by remember(site, artists, source) { mutableStateOf(mapOf<String, ArtistCredits>()) }
    LaunchedEffect(site, artists, source) {
        artists.forEach { name ->
            val links = when (site) {
                // 两站的取法不同不是随手分的：yande.re 的 `artist.json` 匿名直接给 `urls`；
                // Gelbooru 那边没有任何匿名可读的画师端点（`s=list&name=` 那个参数实测根本不生效），
                // 只能借 danbooru —— 两家共享画师名。
                GallerySite.YANDERE -> YandeReClient.getInstance(context).artistLinks(name)
                GallerySite.GELBOORU -> DanbooruArtistClient.getInstance(context).artistUrls(name)
            }.getOrElse { failure ->
                Log.w(TAG, "画师「$name」($site) 的外链没取到：${failure.message}")
                emptyList()
            }
            // 先按"站方记录 + 出处"排一次。只有这一次里**没有 pixiv 那一档**，才值得再花一笔请求
            // 去反查作品作者 —— 出处是作品页时那串数字不能当用户号用（见 GalleryArtistLinks.fromSource）。
            val preliminary = GalleryArtistLinks.iconPlan(links, source = source)
            val author = if (preliminary.any { it.platform == GalleryArtistLinkPlatform.PIXIV }) null
            else GalleryArtistLinks.pixivArtworkId(source)?.let { artworkId ->
                PixivClient.getInstance(context).artworkAuthor(artworkId).getOrElse { failure ->
                    // 实测 15 条出处里有 4 条站方直接 404（删了或锁了）：那是"没取到"，不是"这位没有 pixiv"。
                    Log.w(TAG, "画师「$name」的出处是作品 $artworkId，但 pixiv 没答上作者：${failure.message}")
                    null
                }
            }
            val plan = if (author == null) {
                preliminary
            } else {
                GalleryArtistLinks.iconPlan(
                    urls = links,
                    source = source,
                    artworkAuthorUrl = "https://www.pixiv.net/users/${author.userId}",
                    artworkAuthorAccount = author.account,
                )
            }
            val avatar = resolveArtistAvatar(context, name, plan, source)
            credits = credits + (name to ArtistCredits(plan, avatar, loaded = true))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        artists.forEach { name ->
            val data = credits[name]
            val following = GalleryArtistFollows.isFollowing(follows, site, name)
            val press = remember(name) { MutableInteractionSource() }
            val pressed by press.collectIsPressedAsState()
            val links = data?.links.orEmpty()
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            ) {
                /*
                 * ── 行1：整块可点 = 进档案。**这一行只有这一个语义** ──
                 *
                 * **头像 + 名字整块可点**（批次 Q+R · 批量 4，用户报"读不出这行能点"）。
                 * 成因不是"少了个按钮"而是**两枚东西的可见度反了**：右侧那枚关注是带按压缩放的
                 * `VeneraChip`，而真正会跳的那一枚名字此前只是一段 `Text` 挂了个零提示的 clickable ——
                 * 看起来更像按钮的恰好是不能跳的那个。所以可点面扩到整块，
                 * 并按仓内统一的那档按压（0.96 / 0.88，与 `VeneraChip`、`VeneraTopBarPill`
                 * 同一份数字，不另造）。名字尾部那枚「›」用节标题同一个走向标（`tokens.type.chevron`）。
                 *
                 * ⚠️ 2026-10-01 把这枚「关注」**从这一行挪走了**（挪到下面行2的右端）。
                 * 成因是位置：名字吃 `weight(1f)` 会把「›」顶到这一行的最右，而关注原本就贴在那儿，
                 * 两者只隔 `space4`(8dp) —— "进档案"与"改关注状态"两个不同语义的东西挤在一起，
                 * 误触面重叠。挪走之后这一行右侧再没有第二个可点物，`›` 顶在最右反而是对的。
                 */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.shape.small))
                        .graphicsLayer {
                            val p = if (pressed) 1f else 0f
                            scaleX = lerp(1f, 0.96f, p)
                            scaleY = lerp(1f, 0.96f, p)
                            alpha = lerp(1f, 0.88f, p)
                        }
                        .clickable(interactionSource = press) { onOpenArtist(name) },
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GalleryArtistAvatar(
                        avatarUrl = data?.avatarUrl,
                        name = name,
                        imageLoader = imageLoader,
                    )
                    Text(
                        text = translations[name] ?: name,
                        fontSize = tokens.type.body,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "›",
                        fontSize = tokens.type.chevron,
                        color = tokens.color.textTertiary,
                    )
                }
                /*
                 * ── 行2：外链在左、关注在右 ──
                 *
                 * 两样都不是"这张图"的属性，而是**关于这个人**的附属动作，摆在一起是一组。
                 * 一枚外链都没有是常态（站方没给、或全被认成别的站）：那时左边是空的，
                 * 关注自己靠右 —— 这里不摆"没有外链"那种我们没资格说的话。
                 * 关注**不跟着行1走**的理由见上面那段 ⚠️。
                 */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    ) {
                        links.forEach { link ->
                            VeneraChip(
                                text = GalleryArtistLinks.chipLabel(link),
                                variant = VeneraChipVariant.Assist,
                                // 品牌色小方块 + 白字形（见 [PlatformBadge]）。
                                leading = { PlatformBadge(link.platform) },
                                onClick = { openExternal(context, link.url) },
                            )
                        }
                    }
                    VeneraChip(
                        text = if (following) "已关注" else "关注",
                        selected = following,
                        variant = VeneraChipVariant.Assist,
                        onClick = { scope.launch { store.toggle(site, name) } },
                    )
                }
            }
        }
    }
}

/**
 * 头像位。三种状态在屏上是**两种样子**：
 * 有地址 → 真图；没地址（含"还没回来"）→ 首字母圆座。
 *
 * 首字母不是装饰：这一行总得有个"这是个人"的形状，而空白圆读起来像图挂了。
 * 取数回来之后它会换成真图，那一跳比"先空白再蹦出来"好读。
 */
@Composable
internal fun GalleryArtistAvatar(
    avatarUrl: String?,
    name: String,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier.size(VeneraTokens.spacing.sourceAvatarSize),
) {
    val tokens = VeneraTokens
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(tokens.color.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            VeneraCover(
                url = avatarUrl,
                contentDescription = name,
                preserveAspectRatio = false,
                imageLoader = imageLoader,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = name.take(1).uppercase(),
                fontSize = tokens.type.body,
                color = tokens.color.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 平台入口胶囊上那枚**品牌色小方块**。
 *
 * 2026-10-01 从 `GalleryArtistProfileScreen` 迁到这里（改成 `internal`）—— 两处要摆的是
 * 同一样东西（介绍页头部那一排外链、信息卡里这一行），各留一份迟早会漂
 * （`GalleryArtistLinks.chipLabel` 那条注释记着同一件事的教训）。
 * 迁到 `gallery/ui` 而不是上提 `components/venera`：它依赖 [GalleryPlatformColors]
 * 与 domain 层的 [GalleryArtistLinkPlatform]，上提会让基础组件层挂上画廊语义，
 * 与 `VeneraCard` 头注那句"不含任何业务语义"冲突。
 *
 * 三条形状上的决定：
 * - **是一枚小方块，不是整枚实底胶囊**（参考图那一排是整枚品牌色 + 白字）。没照抄，
 *   因为白字压在那几个官方色上不够：pixiv `#0096fa` 3.1:1、Instagram `#ff0069` 3.7:1、
 *   YouTube `#ff0000` 4.0:1，都低于正文 4.5:1 的门槛（**图形**门槛是 3:1，所以色块上的
 *   字形没问题，文字不行）。色块保留品牌辨识，文字回到胶囊自己的文字色，深浅两档都不用重调。
 * - **发丝描边**：X 那枚是 `#1d9bf0`、Tumblr 是 `#36465d`，在深色档下与卡面对比很弱；
 *   FANBOX 那枚接近中性色，在浅色档下同理。一条 `outlineVariant` 让六枚在两种底色上都"有边界"。
 * - **认不出平台的一枚退中性色 + 通用链接字形**：不给未知域名安一个品牌色 ——
 *   那等于宣称它属于某个已知服务。
 */
@Composable
internal fun PlatformBadge(platform: GalleryArtistLinkPlatform) {
    val tokens = VeneraTokens
    val brand = when (platform) {
        GalleryArtistLinkPlatform.PIXIV -> GalleryPlatformColors.Pixiv
        GalleryArtistLinkPlatform.TWITTER -> GalleryPlatformColors.X
        GalleryArtistLinkPlatform.INSTAGRAM -> GalleryPlatformColors.Instagram
        GalleryArtistLinkPlatform.FANBOX -> GalleryPlatformColors.Fanbox
        GalleryArtistLinkPlatform.TUMBLR -> GalleryPlatformColors.Tumblr
        GalleryArtistLinkPlatform.YOUTUBE -> GalleryPlatformColors.YouTube
        GalleryArtistLinkPlatform.OTHER -> null
    }
    // `when` 不留 else：加一档平台时这里必须显式表态（是有形资产、还是字母标）。
    val glyphRes = when (platform) {
        GalleryArtistLinkPlatform.PIXIV -> R.drawable.ic_platform_pixiv
        GalleryArtistLinkPlatform.TWITTER -> R.drawable.ic_platform_x
        GalleryArtistLinkPlatform.INSTAGRAM -> R.drawable.ic_platform_instagram
        GalleryArtistLinkPlatform.TUMBLR -> R.drawable.ic_platform_tumblr
        GalleryArtistLinkPlatform.YOUTUBE -> R.drawable.ic_platform_youtube
        // FANBOX 无可用矢量档（站方只有彩色吉祥物与横版字标），退字母标 —— 见
        // `scripts/build_platform_icons.mjs` 头注里那三条实测。OTHER 认不出是哪一家，不给字形。
        GalleryArtistLinkPlatform.FANBOX, GalleryArtistLinkPlatform.OTHER -> null
    }
    val shape = RoundedCornerShape(tokens.shape.extraSmall)
    Box(
        modifier = Modifier
            .size(tokens.spacing.platformBadgeSize)
            .clip(shape)
            .background(brand ?: tokens.color.surfaceVariant)
            .border(BorderStroke(tokens.spacing.hairlineThin, tokens.color.outlineVariant), shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            glyphRes != null -> Icon(
                painter = painterResource(glyphRes),
                contentDescription = null,
                tint = GalleryPlatformColors.OnPlatformBrand,
                modifier = Modifier.size(tokens.spacing.platformGlyphSize),
            )
            // FANBOX：字母标。用平台名首字母而不是自造一个图形 —— 屏上那句 "FANBOX" 就在旁边，
            // 字母标读得出是对应的哪一家；自造图形读不出。
            platform == GalleryArtistLinkPlatform.FANBOX -> Text(
                text = "F",
                fontSize = tokens.type.badge,
                fontWeight = tokens.type.weightBold,
                color = GalleryPlatformColors.OnPlatformBrand,
                maxLines = 1,
            )
            else -> Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                tint = tokens.color.textTertiary,
                modifier = Modifier.size(tokens.spacing.platformGlyphSize),
            )
        }
    }
}

/**
 * 头像按 **pixiv 用户档 → fanbox iconUrl → Mastodon avatar → 首字母座** 依次试，拿到第一个真地址就停。
 *
 * 三条口径都不是顺手加的：
 * - **每一档只试一次**。这一层跑在面板打开的那一刻，重试只是把用户的等待换成同一份失败。
 * - **每一档失败都要在日志里留一句**。屏上三种失败长成同一个样子（首字母座），
 *   能分清"这位确实没头像"与"三条路都没走通"的只有日志。
 * - **站方登记的地址先于出处**（与入口那侧同一条优先次序）：画师页自己声明的链接比"这张图发布在哪儿"更像本人。
 *
 * 画师介绍页（[GalleryArtistProfileScreen]）**共用这一份**：那里给的是 `profilePlan` 出来的全平台排，
 * 而头像那三档试法与"失败只在日志里留话"的口径两边完全一致，留两份迟早会漂。
 */
internal suspend fun resolveArtistAvatar(
    context: Context,
    name: String,
    plan: List<GalleryArtistLink>,
    source: String,
): String? {
    plan.pixivUserIdOrNull()?.let { userId ->
        val url = PixivClient.getInstance(context).avatarUrl(userId).getOrElse { failure ->
            Log.w(TAG, "画师「$name」的 pixiv 头像没取到（$userId）：${failure.message}")
            null
        }
        if (url != null) return url
    }
    val endpoint = plan.firstNotNullOfOrNull { GalleryArtistAvatarProbe.fanboxCreator(it.url) }
        ?: GalleryArtistAvatarProbe.fanboxCreator(source)
        ?: GalleryArtistAvatarProbe.mastodonLookup(source)
        ?: return null
    // 日志里只到 `?` 之前：这几条端点本身不带凭据，但"URL 一律截掉 query"是这一层的统一口径。
    return GalleryArtistProbeClient.getInstance(context).avatarUrl(endpoint).getOrElse { failure ->
        Log.w(
            TAG,
            "画师「$name」的 ${endpoint.kind} 头像没取到（${endpoint.endpoint.substringBefore('?')}）：${failure.message}",
        )
        null
    }
}

/**
 * 一个名字在**一站**上的头像：站方外链 → 那一排里的 pixiv / fanbox / mastodon。
 * 首页「正在关注的画师」那一栏用（那里没有"这张图的出处"，所以 `source` 交空串）。
 *
 * 取法用 [GalleryArtistProfile.profilePlan] 而不是 [GalleryArtistLinks.iconPlan]：后者只留两枚**展示用**的
 * 胶囊，fanbox-only 的人会被截掉 —— 而头像正是从那一档取的。少一枚图标不要紧，少一张脸就是bug。
 */
internal suspend fun artistAvatarOnSite(context: Context, site: GallerySite, name: String): String? {
    val uid = GalleryArtistFollow.uidOf(site, name)
    GalleryArtistAvatarCache.peek(uid)?.let { return it.ifBlank { null } }
    val links = when (site) {
        GallerySite.YANDERE -> YandeReClient.getInstance(context).artistLinks(name)
        GallerySite.GELBOORU -> DanbooruArtistClient.getInstance(context).artistUrls(name)
    }.getOrElse { failure ->
        Log.w(TAG, "画师「$name」($site) 的外链没取到，头像这一路到此为止：${failure.message}")
        emptyList()
    }
    val avatar = resolveArtistAvatar(
        context,
        name,
        GalleryArtistProfile.profilePlan(links, source = ""),
        source = "",
    )
    GalleryArtistAvatarCache.remember(uid, avatar)
    return avatar
}

/**
 * 头像解析结果的**进程内**缓存。空串 = **查过了，没有** —— 与"没查过"必须分得开，
 * 否则每一位没头像的画师都会在每次重进画廊时再走一遍整条链。
 *
 * 刻意不落盘：批次 L 拍板过"头像地址会过期，存了就等于承诺离线也能看到"，那条理由今天不改。
 * 这里只是免得同一趟进程里为同一批人重发十几笔。
 */
private object GalleryArtistAvatarCache {

    private val byUid = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun peek(uid: String): String? = byUid[uid]

    fun remember(uid: String, url: String?) {
        byUid[uid] = url.orEmpty()
    }
}

/**
 * 胶囊上的平台名已收在 [GalleryArtistLinks.chipLabel]（批次 Q+R 起介绍页也要用同一份）。
 *
 * 这里**不再留一份私有的**：画师行只收 pixiv / 推 / fanbox 三家，而三家之外的写法
 * 一旦在两处各写各的，就会出现"同一枚链接在两个页面上叫不同名字"这种事。
 */

/** 一排里挑出那条 pixiv 的用户编号（头像要从它取）。 */
private fun List<GalleryArtistLink>.pixivUserIdOrNull(): Long? =
    firstOrNull { it.platform == GalleryArtistLinkPlatform.PIXIV }?.let { GalleryArtistLinks.pixivUserId(it.url) }

private fun openExternal(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Log.w(TAG, "外链打不开：$url（${it.message}）")
    }
}

private val TAG = "GalleryArtistRows"
