package com.venera.compose.gallery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.venera.compose.components.venera.VeneraIconButtonTouchSize
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySaver
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivWorkDetail
import com.venera.compose.gallery.domain.GalleryArtistFollows
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch

/**
 * pixiv「更多信息」那一层的**装配**与**面板入口**（2026-10-18 从 `PixivWorkScreen` 抽出来）。
 *
 * 为什么单独一颗：那一层现在有**两个宿主** —— 作品页自己（屏底摘要条点开的那一层）与大图播放器
 * （页轴时经 [GalleryPageAxis] 交进来的那一版）。装配留在作品页那颗文件里的话，播放器这一侧只有
 * 两种下场：抄一份（两处迟早分叉，本仓为这件事写过不止一条尺），或者把 `PixivWork*` 递进旧看图器
 * （那正是"两屏互不吃对方"那条红线）。抽出来之后：渲染与写口各只有一份，两个宿主各自只交回调。
 *
 * 三枚装配件都是 `@Composable` 而不是普通 lambda：它们要读**打开这一刻的**存档（关注表、收藏表、
 * 屏蔽规则）。交接件里绝不放从组合中现攒的 lambda —— 那会把点图那一刻的读数冻在播放器里，
 * 表现就是"在播放器里点了关注，屏上说不算"。
 */

/** 站方那一作的地址：评论与"在浏览器里打开"都交回它。 */
internal fun pixivWorkUrl(id: Long) = "https://www.pixiv.net/artworks/$id"

/** 交外部浏览器：站方页面有的东西（评论、原作页）不在应用内冒充一份。 */
internal fun openWorkInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "这台设备打不开浏览器", Toast.LENGTH_SHORT).show() }
}

/**
 * 「更多信息」那一层顶部的四枚动作（收藏 / 评论 / 下载 / 分享）。
 *
 * 从前它们常驻在屏底那一排 dock 上，而 dock 里的「详情」与顶栏那枚「更多信息」开的是**同一层** ——
 * 用户 2026-10-18 拍板收进那一层：屏上只留摘要条，图才是主角，重复入口撤掉。
 *
 * 为什么拿一颗小载体往下传而不是四枚形参（同 [PixivArtistActions] 那条理由）：详情段在**三个地方**
 * 各画一次（滚动流末尾 + 作品页的抽屉 + 播放器那一版面板），每多一枚形参就要在三处各对齐一次，
 * 漏一处就是"那一枚按下去没反应"。
 */
internal class PixivWorkActions(
    val isFavorite: Boolean,
    /** 站方 `commentOff=1` 时这一枚**整枚不摆** —— 作者关了评论还摆一枚"看评论"就是假入口。 */
    val showComments: Boolean,
    /**
     * 两枚计数（用户 2026-10-18 第四批：「按照图三那样改」—— 图三那排是 ♥ 带数、💬 带数、
     * 下载与分享不带）。**站方没给就交 null，那一格只剩图标**，不补 0（"0 收藏"看着像读数，
     * 与本仓 `PixivWorkPage.facts` 那条同一族）。
     */
    val favoriteCount: Int? = null,
    val commentCount: Int? = null,
    val onFavorite: () -> Unit,
    val onDownload: () -> Unit,
    val onShare: () -> Unit,
    val onOpenComments: () -> Unit,
)

/**
 * 画师那一行的两枚把手（关注 / 屏蔽）。
 *
 * 屏蔽存的必须是 `AUTHOR` 而不是画师卡那条 `TAG` —— 那是量出来的差异，不是口味：
 * yande.re / Gelbooru 的画师名**就在** `post.tagList` 里（那边 tags 是平铺标签串），
 * 而 pixiv 的 tags 是**作品**标签、里面没有画师名（逐字段读数记在 `PixivFeedParse` 那颗的头注，
 * 不抄进这一颗：抄进来就把某一天的读数冻成了规格，见 `GalleryPixivWorkPageWiringTest` 那把假数尺），
 * 照抄 TAG 会存下一条永远挡不住的规则、而屏上那句 Toast 说的是"已屏蔽"。
 * 墙侧判定 `blockedGalleryRule(author = post.author, tags = post.tagList)` 的 AUTHOR 那一支
 * 吃的正是 `post.author` ⇒ 这一条加下去在墙上真生效。
 */
internal class PixivArtistActions(
    val isFollowed: Boolean,
    val busy: Boolean = false,
    val notice: String? = null,
    val onToggleFollow: () -> Unit,
    val onBlock: () -> Unit,
)

/**
 * 四枚动作的装配。[onDownload] 由宿主给：作品页与播放器各持有自己的进度与 toast，
 * 不共享那一枚布尔（两处各存一份就会出现"底部条在转圈、底栏那颗还能点"）。
 */
@Composable
internal fun pixivWorkActions(detail: PixivWorkDetail?, onDownload: () -> Unit): PixivWorkActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ports = remember { GalleryPorts.of(context) }
    var siteBookmarked by remember(detail?.post?.id, detail?.isBookmarked) {
        mutableStateOf(detail?.isBookmarked)
    }
    // pixiv 收藏只进站方、只认站方状态，不进本地收藏
    val effectiveBookmarked = siteBookmarked ?: false
    return PixivWorkActions(
        isFavorite = effectiveBookmarked,
        showComments = detail != null && !detail.commentsOff && detail.commentCount != null,
        favoriteCount = detail?.post?.favCount,
        commentCount = detail?.commentCount,
        onFavorite = {
            detail?.let { one ->
                scope.launch {
                    val bearer = runCatching { ports.pixivAccount?.bearerOrNull() }.getOrNull()
                    if (bearer.isNullOrBlank()) {
                        android.widget.Toast.makeText(context, "请先登录 pixiv 账号", android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val target = !effectiveBookmarked
                    val res = if (target) {
                        ports.pixiv?.bookmarkAdd(bearer, one.post.id, private = false)
                    } else {
                        ports.pixiv?.bookmarkRemove(bearer, one.post.id)
                    }
                    if (res?.isSuccess == true) {
                        siteBookmarked = target
                        ports.favorites.removeAll(listOf(one.post.uid))
                    } else {
                        val msg = res?.exceptionOrNull()?.message ?: "网络未响应"
                        android.widget.Toast.makeText(context, "站方书签操作失败：$msg", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        onDownload = onDownload,
        onShare = { detail?.let { one -> scope.launch { shareGalleryPost(context, one.post) } } },
        onOpenComments = { detail?.let { one -> openWorkInBrowser(context, pixivWorkUrl(one.post.id)) } },
    )
}

/** 画师那一行两枚把手的装配（与「画廊 → 关注画师」读同一份存档，点完那一栏当场多一枚）。 */
@Composable
internal fun pixivArtistActions(author: String, authorId: Long? = null, initialFollowed: Boolean? = null): PixivArtistActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ports = remember { GalleryPorts.of(context) }
    val guard = ports.contentGuard
    val follows by ports.follows.follows.collectAsState()
    val isLocalFollowed = GalleryArtistFollows.isFollowing(follows, GallerySite.PIXIV, author)
    var siteFollowed by remember(author, initialFollowed) { mutableStateOf(initialFollowed) }
    // pixiv 关注只认站方状态，不写入本地图库；但若本地有历史残留，允许显示并清理
    val effectiveFollowed = siteFollowed ?: isLocalFollowed
    var notice by remember(author) { mutableStateOf<String?>(null) }
    var busy by remember(author) { mutableStateOf(false) }
    return PixivArtistActions(
        isFollowed = effectiveFollowed,
        busy = busy,
        notice = notice,
        onToggleFollow = {
            scope.launch {
                val target = !effectiveFollowed
                togglePixivFollow(
                    ports = ports,
                    userId = authorId,
                    artistName = author,
                    target = target,
                    setBusy = { busy = it },
                    setNotice = { notice = it },
                    alignSite = { siteFollowed = it },
                )
            }
        },
        onBlock = {
            scope.launch {
                val exists = guard.rules.value.any {
                    it.type == "AUTHOR" && it.pattern.equals(author, ignoreCase = true)
                }
                when {
                    exists -> Toast.makeText(context, "「$author」已在屏蔽列表", Toast.LENGTH_SHORT).show()
                    guard.addRule("AUTHOR", author) >= 0 -> Toast.makeText(
                        context, "已屏蔽「$author」，可在设置 → 内容屏蔽管理", Toast.LENGTH_SHORT,
                    ).show()
                    else -> Toast.makeText(context, "屏蔽失败，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        },
    )
}

/**
 * 画师头像按**作者用户号**现取（用户 2026-10-18 真机：「只显示占位符」）。
 *
 * 详情腿本身不带头像地址，但它带了号 —— 现成那条腿就是按号取的（`/ajax/user/{id}`，
 * 地址按 2026-10-01 那条改判落盘，键与画师卡同一份）。取不到（这一条没号 / 那一跳失败）交 null，
 * 由 `GalleryArtistAvatar` 自己退回首字母座 —— 那是**明说的降级**，不是"头像坏了"。
 */
@Composable
internal fun pixivAvatarUrl(detail: PixivWorkDetail?): String? {
    val context = LocalContext.current
    val ports = remember { GalleryPorts.of(context) }
    val userId = detail?.authorId
    val author = detail?.post?.author.orEmpty()
    val source = detail?.post?.source.orEmpty()
    val resolved = remember { mutableStateMapOf<Long, String?>() }
    LaunchedEffect(userId, author) {
        if (userId == null || resolved.containsKey(userId)) return@LaunchedEffect
        val cached = ports.avatars.peek(GallerySite.PIXIV, author, source)
        if (cached != null) {
            resolved[userId] = cached
            return@LaunchedEffect
        }
        ports.artists.pixivAvatarUrl(userId).onSuccess { url ->
            resolved[userId] = url
            if (!url.isNullOrBlank()) {
                ports.avatars.remember(GallerySite.PIXIV, author, source, url)
            }
        }
    }
    return userId?.let { resolved[it] }
}

/**
 * 那一排四枚动作（用户 2026-10-18 第四批「优化下面的按钮和 tag 展示 ui」＋
 * 2026-10-19 那张规格照：四枚住在**同一颗圆角条**里，♥ 带收藏数、💬 带评论数、下载与分享只带图标）。
 *
 * 两条口径同时成立：
 * - **外壳是一整条**（`surfaceContainerHigh` + `shape.card` 圆角），四枚在里面均分 ——
 *   从前四枚各浮在背景上，读起来是"四个散件"而不是"这一作的动作条"；
 * - **每一枚自己按内容取宽**（见 [WorkAction]），所以带计数时那串数字不再被挤进固定方格。
 *
 * 评论那一枚**就是**"去浏览器看评论"那条出口（点它 = 打开站方页面），所以详情段里**不再**
 * 另摆一行"评论 N 条 · 在浏览器里看"：同一件事在同一屏说两遍，正是本批撤屏底摘要条时
 * 立案的那一族（重复读数）。
 */
@Composable
internal fun PixivWorkActionRow(actions: PixivWorkActions) {
    val tokens = VeneraTokens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.screenHorizontal)
            .clip(RoundedCornerShape(tokens.shape.card))
            .background(tokens.color.surfaceContainerHigh)
            .padding(vertical = tokens.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        WorkAction(
            if (actions.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            if (actions.isFavorite) "取消收藏" else "收藏",
            tinted = actions.isFavorite,
            count = actions.favoriteCount,
        ) { actions.onFavorite() }
        if (actions.showComments) {
            WorkAction(
                Icons.Outlined.ChatBubbleOutline,
                "在浏览器里看评论",
                count = actions.commentCount,
            ) { actions.onOpenComments() }
        }
        WorkAction(Icons.Outlined.Download, "下载") { actions.onDownload() }
        WorkAction(Icons.Outlined.Share, "分享") { actions.onShare() }
    }
}

/**
 * 动作行里的一枚：**自己按内容长出来的胶囊**，不是固定尺寸的图标按钮。
 *
 * 2026-10-18 真机读数（用户「按钮还是没改反而 ui 有问题」）：从前这枚套在 `VeneraIconButton` 里，
 * 那颗件把 content 钉在一个**固定方格**内 —— 带计数时那串数字挤不进方格，换行成"41 / 77"两截再被裁掉。
 * 图三要的就是"图标 + 数"平摊在一排里，所以这里不借那颗固定格子，让胶囊按内容取宽；
 * 顶栏那两枚仍然走全站同一颗玻璃座（`VeneraTopBarPill`，两件事不是一颗）。
 */
@Composable
private fun WorkAction(
    icon: ImageVector,
    label: String,
    tinted: Boolean = false,
    count: Int? = null,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            // 宽度随内容长，**触达高度仍吃全站那一档 48dp**（胶囊只有 18dp 图标 + 6dp×2 内衬 = 30dp，
            // 不钉下限就成了本仓反复立案的"点不着的小把手"）。
            .heightIn(min = VeneraIconButtonTouchSize)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            // 读屏念的是"这一枚做什么 + 站方给的数"，与屏上那一串数字同一份出处。
            .semantics { contentDescription = if (count == null) label else "$label $count" }
            .padding(
                horizontal = tokens.spacing.space4,
                vertical = tokens.spacing.space3,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (tinted) tokens.color.primary else tokens.color.textPrimary,
                modifier = Modifier.size(tokens.spacing.badgeIconSize),
            )
            // 计数只在站方给了那一格时才摆；语义串里带上同一个数，读屏不必再点一次才知道是多少。
            if (count != null) {
                Text(
                    count.toString(),
                    fontSize = tokens.type.caption,
                    color = if (tinted) tokens.color.primary else tokens.color.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 播放器那一层的**信息面板**（页轴时由 [GalleryPageAxis] 把这一枚**顶层函数引用**交进去）。
 *
 * 交函数引用而不是从组合里现攒的 lambda，是为了让面板**自己**读存档；而 [data] 是 `Any?` ——
 * 播放器不解释它，所以旧看图器那一侧不需要认识 pixiv 的任何模型（那条隔离红线钉的就是这个）。
 *
 * 「相关图片」那一排在播放器这一版里**不摆**（`related = emptyList()`）：那一排每一次点击都是
 * "离开播放器、换一条作品"，而播放器这一层没有那条出口（它有 `onOpenArtist`，没有 `onOpenWork`）。
 * 宁可少一排，也不摆一枚点了没反应的把手 —— 那一排在作品页里原样都在。
 */
@Composable
internal fun pixivWorkInfoPanel(data: Any?, params: GalleryPageAxis.PanelParams) {
    val detail = data as? PixivWorkDetail ?: return
    val context = LocalContext.current
    val ports = remember { GalleryPorts.of(context) }
    val aiBadgeOn by ports.prefs.galleryAiBadge.collectAsState()
    var tagsExpanded by remember(detail.post.id) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        PixivWorkDetails(
            detail = detail,
            related = emptyList(),
            tagsExpanded = tagsExpanded,
            onExpandTags = { tagsExpanded = true },
            onOpenArtist = { _, name, source -> params.onOpenArtist(name, source) },
            artist = pixivArtistActions(detail.post.author, detail.authorId, detail.isAuthorFollowed),
            actions = pixivWorkActions(
                detail = detail,
                onDownload = {
                    // 播放器那一层持有进度与 toast（`saving`），面板只显示 —— 两处各存一份就会
                    // 出现"底部条在转圈、面板这颗还能再点一次"。
                    params.onDownload()
                },
            ),
            avatarUrl = pixivAvatarUrl(detail),
            imageLoader = params.imageLoader,
            onOpenWork = { params.onDismiss() },
            aiBadgeOn = aiBadgeOn,
            // 面板里的标签也接上搜索（用户 2026-10-19 第 3 条）。顺序是先收面板再交标签：
            // 留在原地的面板会盖住"已经跳去搜索"这件事；而 `params.onSearchTag` 为 null 时
            // 这里传 null，标签保持纯展示（不摆点了没反应的那一枚）。
            onSearchTag = params.onSearchTag,
        )
    }
}
