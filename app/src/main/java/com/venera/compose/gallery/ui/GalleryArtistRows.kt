package com.venera.compose.gallery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.ImageLoader
import com.venera.compose.R
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryArtistAvatarProbe
import com.venera.compose.gallery.domain.GalleryArtistCreditsKey
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
 * 详情面板里的「这位画师」（批次 L · L5）：头像 + 平台入口 + 关注。
 *
 * ## 形态：一位画师 = **一张卡**（2026-10-01 信息卡第二次重排）
 *
 * 用户 2026-09-30 拍板的那一档是「每位画师一行：头像 + 图标 + 关注」，落到屏上是**裸摆**的
 * 两行（上行头像+名字、下行外链+关注）。2026-10-01 用户拿图二那稿报"还是有点乱"，
 * 这一块的问题不在行数，而在**它没有边界**：上方是白色的事实卡、这一块是灰底上散着的四样东西，
 * 读起来像"卡下面还掉着些零碎"。现在整块收进一张 `VeneraCard`：
 *
 * | 行 | 摆什么 |
 * |---|---|
 * | 行1 | 头像（56dp）· 显示名 / 站方原串 · 关注胶囊 |
 * | 行2 | 外链胶囊排（pixiv / X / FANBOX…，各带品牌色小方块） |
 *
 * 三处不是顺手就做的设计：
 * - **整卡可点 = 进档案**，走 `VeneraCard(onClick=)` 那个官方重载。上一版是自己往行上加
 *   `clickable` + 手工按压缩放，还得在名字尾部摆一枚「›」来提示"这行能点"；
 *   卡自带按压缩放之后那枚提示标就不必了（它同时也不再跟右侧的关注钮抢位置 —— 那条
 *   "两个不同语义的可点物挤在一起"的老问题在结构上消失）。卡里的关注胶囊自己有 `onClick`，
 *   子级先拿到事件，所以点关注不会连带跳进档案页。
 * - **名字与站方原串分两行**（有译文时）：上行摆译文、下行摆站方那串标识。
 *   与 `tagDisplayLabel` 的 `译文 (原文)` 是同一份判据，只是在这儿改成两行 ——
 *   挤在一个括号里会把整行撑到省略号。没有译文时两行是同一个串，只摆上行。
 * - **外链一枚都没有时行2整行不摆**：不摆"没有外链"那种我们没资格说的话，也不留一行空白。
 *
 * ## 取数不在这里 —— 面板打开时它**早该是现成的**
 *
 * 一位画师最贵要三笔请求（站方画师记录 → pixiv 用户档 → 头像图本身）。
 * 最初这一份状态归本 composable 持有，`LaunchedEffect` 在**面板打开那一刻**才开跑
 * —— 于是每次点「关于这张图」，外链与头像都要当着用户的面等一两百毫秒（首字母座先顶着）。
 * 用户 2026-10-01 第 2 条点掉的就是这一段：「画师信息提前在点击图片后加载图片的时候就加载，
 * 不要点击信息才加载」。
 *
 * 现在的分工：**大图页在打开这一张图时就取**（[com.venera.compose.gallery.ui.GalleryPostScreen]
 * 里那个 `LaunchedEffect`），取到的东西按 `(站点, 画师名, 出处)` 记进
 * [GalleryArtistCreditsCache]；这一块只**渲染传进来的那一份**
 * （`credits` 由页面透传，与 `saving` 那份状态同一条分工）。
 *
 * 把加载留在组件里会更"自洽"，但那就是两处取数：面板开得比预取快时两边同时发同一笔请求
 * ——而真正要的那件事（**打开面板时它已经是现成的**）恰恰靠"只有一个取数方"保证。
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
 * （pixiv / baraag / twitter / fanbox…），它是站方逐条记在帖子上的。所以那张图的**出处**
 * 会作为**第二顺位**参与两件事：入口缺档时由 [com.venera.compose.gallery.domain.GalleryArtistLinks.fromSource] 补一枚
 * （认得出"这个人"的形状才补，作品页一律不认），头像缺档时由
 * [com.venera.compose.gallery.domain.GalleryArtistAvatarProbe] 派生 fanbox / Mastodon 的公开端点。
 * 站方登记的记录始终排在前面 —— 画师页自己声明的链接比"这张图发布在哪儿"更像本人。
 */

/**
 * 一位画师的取数结果。
 *
 * [loaded] 是「取过了」而不是「取到了」：`loaded = true` 且两边都空 = 这一位确实什么都没有
 * （屏上仍什么都不摆，口径见文件头注）。它与"还没回来"必须分得开，否则每一次重进都在重发同一笔。
 */
internal class ArtistCredits(
    val links: List<GalleryArtistLink> = emptyList(),
    val avatarUrl: String? = null,
    val loaded: Boolean = false,
)

/**
 * 画师署名的取数入口：**站方外链 → 出处补一枚 → 头像三档**。
 *
 * 两个读者同解（都由 [GalleryArtistCreditsCache] 兜住重复）：
 * - 大图页在**打开这一张图**时就调（用户 2026-10-01 第 2 条），
 * - 面板拿到的是它取好的结果，不再自己发请求。
 */
internal suspend fun loadArtistCredits(
    context: Context,
    site: GallerySite,
    name: String,
    source: String,
): ArtistCredits {
    val links = when (site) {
        // 三站的取法不同不是随手分的：yande.re 的 `artist.json` 匿名直接给 `urls`；
        // Gelbooru 那边没有任何匿名可读的画师端点（`s=list&name=` 那个参数实测根本不生效），
        // 只能借 danbooru —— 两家共享画师名。
        // Safebooru 与 Gelbooru 同属 Danbooru 系，画师端点也走 danbooru。
        GallerySite.YANDERE -> GalleryPorts.of(context).boards.yandere.artistLinks(name)
        GallerySite.GELBOORU -> GalleryPorts.of(context).artists.danbooruArtistUrls(name)
        GallerySite.SAFEBOORU -> GalleryPorts.of(context).artists.danbooruArtistUrls(name)
    }.getOrElse { failure ->
        Log.w(TAG, "画师「$name」($site) 的外链没取到：${failure.message}")
        emptyList()
    }
    // 先按"站方记录 + 出处"排一次。只有这一次里**没有 pixiv 那一档**，才值得再花一笔请求
    // 去反查作品作者 —— 出处是作品页时那串数字不能当用户号用（见 GalleryArtistLinks.fromSource）。
    val preliminary = GalleryArtistLinks.iconPlan(links, source = source)
    val author = if (preliminary.any { it.platform == GalleryArtistLinkPlatform.PIXIV }) null
    else GalleryArtistLinks.pixivArtworkId(source)?.let { artworkId ->
        GalleryPorts.of(context).artists.pixivArtworkAuthor(artworkId).getOrElse { failure ->
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
    return ArtistCredits(plan, resolveArtistAvatar(context, site, name, plan, source), loaded = true)
}

/**
 * 画师署名的**进程内缓存**。键是 `站点 | 画师名 | 出处`。
 *
 * **出处进键**不是洁癖：站方记录缺档时，那一枚 pixiv 入口是**从出处反查**出来的
 * （见 [loadArtistCredits] 里的 `artworkAuthor`）。同一位画师的两张图若挂着不同的出处，
 * 反查到的"作者"未必是同一个人（转投、代投、原作是别人），混用一份会给这一位挂上另一位的号。
 *
 * 这一份**只在进程内**：外链是站方那条记录的两个字段，一次只要一笔请求，而画师换链接、
 * 删号都会让它变味 —— 存了就等于把上一趟进程的排面长期钉住。
 * 头像那一档不同（要 2~4 笔才拿得到，而且实测地址是不带过期参数的静态路径），
 * 它另有磁盘档，见 [com.venera.compose.gallery.data.GalleryArtistAvatarStore]。
 * 这里只是免得同一趟进程里为同一位画师重发十几笔（用户翻十张图、张张同一个画师，是常态）。
 */
internal object GalleryArtistCreditsCache {

    private val byKey = java.util.concurrent.ConcurrentHashMap<String, ArtistCredits>()

    fun peek(site: GallerySite, name: String, source: String): ArtistCredits? =
        byKey[GalleryArtistCreditsKey.of(site, name, source)]

    fun remember(site: GallerySite, name: String, source: String, credits: ArtistCredits) {
        byKey[GalleryArtistCreditsKey.of(site, name, source)] = credits
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryArtistCreditRows(
    site: GallerySite,
    artists: List<String>,
    translations: Map<String, String>,
    /**
     * 这一批画师的取数结果（`画师名 → 结果`）—— **由页面持有并透传**，理由见文件头注。
     * 表里没有的名字 = 还没回来，那一格摆首字母座。
     */
    credits: Map<String, ArtistCredits>,
    imageLoader: ImageLoader,
    /** 点这张卡（关注钮与外链胶囊除外）：进画师介绍页（批次 Q+R · 批量 4 改判，原来是"点 = 搜这一枚标签"）。 */
    onOpenArtist: (String) -> Unit,
) {
    if (artists.isEmpty()) return
    val context = LocalContext.current
    val tokens = VeneraTokens
    val scope = rememberCoroutineScope()
    val store = remember { GalleryPorts.of(context).follows }
    val follows by store.follows.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        artists.forEach { name ->
            val data = credits[name]
            val following = GalleryArtistFollows.isFollowing(follows, site, name)
            val links = data?.links.orEmpty()
            // 显示名与站方标识分成两半：有译文时上行摆译文、下行摆站方原串（见下面那段注释），
            // 没有译文时两行是同一个串，就只摆上行。判据与 `tagDisplayLabel` 同一口径
            // （译文为空白、或译文就是原文，都不算"有译文"）。
            val translated = translations[name]?.takeIf { it.isNotBlank() && it != name }
            /*
             * 一位画师 = **一张卡**（2026-10-01 信息卡第二次重排，用户报"还是有点乱"）。
             *
             * 上一版这一块是"裸摆"的：头像、名字、外链、关注四样散在 sheet 面上，
             * 上一块（事实卡）是白的、这一块是灰的，读起来像"卡片下面还有一堆零碎"。
             * 换成卡之后这一块才有了自己的边界 —— 它说的是**关于这个人**的事，
             * 与上下两块（这张画的属性 / 这页的地址）本来就不是一回事。
             *
             * 整卡可点 = 进档案，走 `VeneraCard(onClick=)` 那个官方重载，**不往外层
             * modifier 上叠 combinedClickable** —— 那样会被 squircle 的裁剪层级吞掉长按
             * （`VeneraCard.kt` 头注记着这条真机实测）。卡里的关注胶囊自己有 onClick，
             * 子级先拿到事件，所以"点关注"不会连带跳进档案页。
             */
            VeneraCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenArtist(name) },
            ) {
                // ── 行1：头像 + 名字 + 关注 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GalleryArtistAvatar(
                        avatarUrl = data?.avatarUrl,
                        name = name,
                        imageLoader = imageLoader,
                        // 这一档就是首页「正在关注的画师」那一栏的脸径：同一件事（一个人的头像）
                        // 在两个页面上不该是两个尺寸。
                        modifier = Modifier.size(tokens.spacing.artistAvatarSize),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = translated ?: name,
                            fontSize = tokens.type.itemTitle,
                            fontWeight = tokens.type.weightSemibold,
                            color = tokens.color.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        /*
                         * 第二行只在**显示名与站方标识不是同一个串**时才摆（有译文时）。
                         * 它摆的是站方那串原始标识 —— 用户拿它回站上搜得到这个人，
                         * 而中文译名在站上是搜不到的。两者相同时摆两遍同一串字只是噪声。
                         */
                        if (translated != null) {
                            Text(
                                text = name,
                                fontSize = tokens.type.caption,
                                color = tokens.color.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    /*
                     * 关注**留在这一行右端**（2026-10-01 从行1挪到行2、本版又挪回来）。
                     *
                     * 上次挪走的理由是"名字吃 weight(1f) 会把「›」顶到最右，与关注只隔 8dp，
                     * 两个不同语义的可点物误触面重叠"。那条前提今天不成立了：这一版把
                     * 「›」**整枚撤掉**，行1右端只剩关注这一样可点物（整卡可点那一路由卡自己的
                     * 按压反馈承担），所以"挤"这件事在结构上不存在了。
                     */
                    VeneraChip(
                        text = if (following) "已关注" else "关注",
                        selected = following,
                        variant = VeneraChipVariant.Assist,
                        onClick = { scope.launch { store.toggle(site, name) } },
                    )
                }
                /*
                 * ── 行2：外链胶囊 ──
                 *
                 * 一枚外链都没有是常态（站方没给、或全被认成别的站）：那时这一行整体不摆
                 * —— 不摆"没有外链"那种我们没资格说的话，也不留一行空白撑高卡片。
                 */
                if (links.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(tokens.spacing.space4))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
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
 *
 * **结果落盘**（[GalleryArtistAvatarStore]，2026-10-01 改判）：这一层是三个读者
 * （详情面板 / 介绍页 / 首页那一栏）唯一的共同下游，所以"查没查过"记在这里，
 * 而不是让每个读者各自决定要不要问一遍 —— 介绍页此前正是**绕过了两份缓存**直调这一层，
 * 于是每进同一位都要重发 2~4 笔 JSON。键带出处，理由见 [GalleryArtistCreditsKey]。
 */
internal suspend fun resolveArtistAvatar(
    context: Context,
    site: GallerySite,
    name: String,
    plan: List<GalleryArtistLink>,
    source: String,
): String? {
    val store = GalleryPorts.of(context).avatars
    // 三档语义（没查过 / 查过没有 / 有地址）在 GalleryArtistAvatars 里钉着，这里只照它办。
    store.peek(site, name, source)?.let { return it.ifBlank { null } }
    val avatar = probeArtistAvatar(context, name, plan, source)
    store.remember(site, name, source, avatar)
    return avatar
}

/** [resolveArtistAvatar] 的取数那半 —— 不碰缓存，失败只在日志里留话。 */
private suspend fun probeArtistAvatar(
    context: Context,
    name: String,
    plan: List<GalleryArtistLink>,
    source: String,
): String? {
    plan.pixivUserIdOrNull()?.let { userId ->
        val url = GalleryPorts.of(context).artists.pixivAvatarUrl(userId).getOrElse { failure ->
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
    return GalleryPorts.of(context).artists.probeAvatarUrl(endpoint).getOrElse { failure ->
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
 *
 * 「查没查过」由 [resolveArtistAvatar] 里那份磁盘档负责，这里不再自备一份进程内的表。
 */
internal suspend fun artistAvatarOnSite(context: Context, site: GallerySite, name: String): String? {
    val links = when (site) {
        GallerySite.YANDERE -> GalleryPorts.of(context).boards.yandere.artistLinks(name)
        GallerySite.GELBOORU -> GalleryPorts.of(context).artists.danbooruArtistUrls(name)
        GallerySite.SAFEBOORU -> GalleryPorts.of(context).artists.danbooruArtistUrls(name)
    }.getOrElse { failure ->
        Log.w(TAG, "画师「$name」($site) 的外链没取到，头像这一路到此为止：${failure.message}")
        emptyList()
    }
    return resolveArtistAvatar(
        context,
        site,
        name,
        GalleryArtistProfile.profilePlan(links, source = ""),
        source = "",
    )
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
