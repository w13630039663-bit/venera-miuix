package com.venera.compose.gallery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.venera.compose.components.venera.PILL_TINT_BOTTOM
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.openGalleryArtistProfile
import com.venera.compose.components.venera.PILL_TINT_TOP
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraPressFeedback
import com.venera.compose.components.venera.hScrollEndContentPadding
import com.venera.compose.components.venera.hScrollEndFade
import com.venera.compose.components.venera.hScrollStartFade
import com.venera.compose.components.venera.veneraAvatarRing
import com.venera.compose.components.venera.veneraFaceSheen
import com.venera.compose.components.venera.veneraGlassSurface
import com.venera.compose.components.venera.veneraSurfaceShadowElevation
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.PixivRankingMode
import com.venera.compose.gallery.data.PixivUser
import com.venera.compose.gallery.data.PixivWork
import com.venera.compose.gallery.domain.GalleryAi
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.ui.tokens.VeneraGlassRole
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.ui.tokens.veneraGlassEnabled
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * 文本行高倍数（`em`，相对字号）—— 2026-10-17 第二十一轮。
 *
 * **唯一出处是 miuix 的 `paragraph`**（`ui/tokens/MiuixTextStyles.kt:33` 写着
 * `TextStyle(fontSize = t.itemTitle, lineHeight = 1.2f.em)`，那颗注自己说明
 * "lineHeight 这一条库里是 1.2f.em，是唯一带行高的角色"）。这里照抄同一档，
 * 不另立新数：横滑一排要的是"行高可预测"，不是"排版更好看"。
 *
 * 为什么非要有它：`lineHeight` 不指定时，行高取**这一行实际用到的字形**的
 * ascent + descent —— 同一行字，纯拉丁 / CJK / 带 emoji 会差 1~2px。竖排文本
 * 差这点没人看得出来，但横滑一排的**报高**是"已测项的最大值"，于是"视口里换了谁"
 * 就成了报高，外面那张白卡跟着上下跳。详见 [PixivArtistRail] 头注那条链条。
 *
 * ⚠️ 2026-10-17 第三十轮起**两站共用**：图库站的关注画师行也进了白卡
 * （用户「关注画师和每日热门 做成相同风格的ui」），同一个病灶在同一种容器里，
 * 倍数再各抄一档就会漂。所以它是 `internal` 而不是文件私有。
 */
internal const val TEXT_LINE_HEIGHT = 1.2f

/**
 * 画师一排左缘渐隐的淡入/淡出时长（毫秒）。
 *
 * 只做一件事：让"开始往右滑"那一瞬间不要凭空冒出一道 16dp 的实色带。
 * 取短值是因为它**不是入场动画** —— 屏上同时在滚，过渡比秒数更难感知，
 * 120ms 足够把"突然出现"抹平成"跟着手势出来"。
 */
private const val RAIL_START_FADE_MILLIS = 120

/**
 * Pixiv 域首页与"内容域那一排"专用的几件控件（2026-10-17，用户那张排行榜稿）。
 *
 * 单独一颗文件而不是塞进 `GalleryHomeSections.kt`：那两把尺子
 * （`GalleryHomeChromeTest` 数 `VeneraSegmentedButton(` 的**所在文件**、
 * `UiAuditG3Test` 数 `wallAdjacent` 的**所在文件**）都是按文件算的，
 * 新件住在这儿就不与它们抢口径 —— 而档名那一排本来也**不该**用 `VeneraSegmentedButton`：
 * 那颗是等宽分段器（每格 `weight(1f)`），稿上是五枚跟着字长走的药丸。
 */
internal object GalleryPixivHome {

    /** 排行榜一排摆几张。用户那张稿是三张。 */
    const val RANKING_ROW_ITEMS = 3

    /**
     * 排行榜**收起态**横滑时摆几张（2026-10-17 第十九轮，用户「只能显示3张，左滑应该有更多，限制10张」）。
     *
     * 与 [RANKING_ROW_ITEMS] 拆开是因为它俩本来是同一颗常量：`3` 既当"收起态张数"又当
     * "展开态列数"用（`PixivRankedWorkGrid` 的 `chunked` / `until` 都读它）。直接改成 10
     * 会让展开态的网格变成每行 10 列 —— 两个语义必须分家。
     */
    const val RANKING_PREVIEW_ITEMS = 10

    /**
     * 收起态**一行露几张**（2026-10-17 第二十二轮，用户「缩到一行只同时显示两个半吧」）。
     *
     * 2.5 = 两张整 + 第三张探出半张。不是"每屏 2.5 张"的取整误差，而是**故意的**：
     * 半张露在渐隐里，"右边还有"这件事在屏上就看得见（与 `horizontalRailFades` 那条纪律同源）。
     *
     * ⚠️ 它只管**宽度**，张数仍由 [RANKING_PREVIEW_ITEMS] 管 —— 两者别再合成一个数。
     */
    const val RANKING_PREVIEW_SLOTS = 2.5f

    /**
     * 排行榜卡的图区宽高比（设计稿 389×369 ⇒ ≈1.05，**近方形**）。
     *
     * 为什么不再用定值 `historyCoverHeight`(170dp)：那 170 是给历史那一排 124dp 宽的卡定的，
     * 搬到三张均分的排行榜上卡宽只有 102dp ⇒ 图区成 102×172 的**竖条**，作品被裁得只剩中间一条
     * （现量见 `_probe/ui-diff-2026-10-17/`：卡宽 102dp / 图高 172dp ⇒ 0.59）。
     */
    const val RANKING_COVER_ASPECT = 1.05f

    /** 「查看全部」展开之后摆几张（榜单每页 50，这里只多摆几屏之内的）。 */
    const val RANKING_EXPANDED_ITEMS = 21

    /** 每枚种子取几张进墙（2026-10-17：推荐改成「猜你喜欢」那种墙之后，六张铺不满一屏）。 */
    const val RECOMMEND_PER_SEED = 8
}

/**
 * pixiv 首页那两池的**进程级存档**（2026-10-17 第七轮）。
 *
 * ## 为什么需要它
 *
 * 用户原话：「不要每次切页面都刷新，手动下拉才刷新」。
 *
 * 页内的域切换（图库站 ↔ Pixiv）只改一个 `rememberSaveable`，屏上那些 `remember` 都还在 ——
 * 那一路靠"取数前先看缓存"就够了。**切主 Tab 不一样**：`gotoTab` 用
 * `popUpTo(startDestination){saveState=true}` 把这条目的地 pop 掉，`remember` 全没
 * （`NavBackStackEntry` 类头：ViewModels will be cleared；saveState 只救 `rememberSaveable`）。
 * 于是从"收藏"切回"画廊"，pixiv 这一池就得从头再取一遍 —— 而图库站那边有同日快照挡着，
 * 不会重取。同一条反馈里用户读到的正是这种不对称。
 *
 * 所以把"已经取回来的东西"放进进程级存档：切回来直接摆，重新取只发生在
 * **下拉刷新 / 换档 / 换登录态 / 换种子表**这几处。
 *
 * ## 什么时候必须清
 *
 * - 登录态变化（`LaunchedEffect(pixivAuth)`）：登录前那一池是匿名来源，登录后该是站方那份；
 * - 用户下拉刷新：那是"我要新的"，见 `refreshWholePage`。
 *
 * 刻意不存磁盘：它跟"今天的热门"不是一类东西（后者有按天的快照口径），
 * 存下来第二天还在屏上就成了假的新鲜度。
 */
internal object GalleryPixivHomeStore {

    /**
     * 排行榜按档缓存（`"${mode}#${app|www}"` → 那一档那一次的结果）。
     *
     * 键里那半截登录态（`app`/`www`）不是装饰：同一档在登录前后是**两条腿、两份内容**。
     */
    val ranking = mutableStateMapOf<String, PixivRankingSnapshot>()

    var recommendWorks by mutableStateOf<List<PixivWork>>(emptyList())

    /** 「这一池取过没有」的凭证（见 `GalleryScreen` 那条 effect）。null = 没取过。 */
    var recommendLoadedKey: String? = null

    var recommendCaption: String? = null

    var recommendStation: Boolean = false

    /** 站方推荐下一页地址（`next_url` 原样）；null = 到底。 */
    var recommendNext: String? = null

    var recommendSeedPage: Int = 1

    var recommendExhausted: Boolean = false

    var recommendIdle: Boolean = true

    // ── 关注画师 / 推荐用户（2026-10-17 第八轮）──
    //
    // 与上面那几枚同一个理由：切主 Tab 时这条目的地被 pop、`remember` 全丢，
    // 而这两条腿都要票据（比匿名那几条更贵），每次回来重打一遍是纯浪费。
    var followingUsers by mutableStateOf<List<PixivUser>>(emptyList())
    var recommendedUsers by mutableStateOf<List<PixivUser>>(emptyList())
    /** 「这两栏取过没有」的凭证（键 = 自己的 uid）。 */
    var usersLoadedKey: String? = null
    var followingCaption: String? = null
    var usersCaption: String? = null

    /** 登录态一变就整批作废（理由见上面那段）。 */
    fun clear() {
        ranking.clear()
        recommendWorks = emptyList()
        recommendLoadedKey = null
        recommendCaption = null
        recommendStation = false
        recommendNext = null
        recommendSeedPage = 1
        recommendExhausted = false
        recommendIdle = true
        followingUsers = emptyList()
        recommendedUsers = emptyList()
        usersLoadedKey = null
        followingCaption = null
        usersCaption = null
    }
}

/**
 * 内容域切换：**分段器**。
 *
 * ## 为什么是这一版（第九轮改下拉 ⇒ 本轮改回）
 *
 * 下拉那一版把左侧收到约 88dp（"图库站"那一档最宽），腾出来的宽度全给了搜索条 —— 宽度账是对的，
 * 屏上读起来却不对：一颗只写当前源的胶囊**看不出"还有另一个源"**，要戳一下才知道能换；
 * 而这一行左边的任务恰恰是"我在看哪一站、还能切到哪一站"。分段器把两档同时摆出来，一眼扫到。
 *
 * 用户原话：「站源选择器还是改回前面版本的分段切换器吧，这样好看点」。
 * 所以本体是第九轮之前那一版**原样**：`surfaceVariant` 轨道 + `primaryContainer` 滑动胶囊
 * （弹簧缓动，不是硬切），文字选中时换 `onPrimaryContainer` + semibold。
 *
 * ## 材质：跟着**全局材质轴**走，不是写死玻璃
 *
 * 用户原话：「搜索框和源切换器都要做成我们已经有的液态玻璃材质，但是要在全局切换材质保留
 * 切换原来的 md3 样式」。这一条不因形态改回分段器而失效 ——
 * - `veneraGlassEnabled()` 真 ⇒ 轨道透底 + `veneraGlassSurface(CHIP, …)` 采氛围层做真模糊；
 * - 假（= `SurfaceMaterial.SOLID`，也是默认档） ⇒ `surfaceVariant` 实色轨道，就是前面那一版的样子。
 *
 * ⚠️ 这里没有 miuix `Surface`，因此不存在"玻璃档投影透出来"那一条（它只挂在 `Surface` 上）。
 *
 * ## 宽度
 *
 * 外框**不铺满、不居中**：它住在搜索条左边那一格里，整排按字长收（每格吃 `space6` 横向内衬）。
 * 高度仍吃 `segmentedHeight`（48dp），与搜索条同档 —— 用户 2026-10-17 那句
 * 「搜索框缩成和切换源一样大小」是这一行唯一的对齐契约。
 *
 * ## 胶囊的位置：**量宽 + 前缀和**，不走父子坐标换算
 *
 * ⚠️ 2026-10-17 第二十七轮（用户「点到 pixiv 里面的胶囊不会过去」）：从前每格报的是
 * `coords.parentCoordinates?.localBoundingBoxOf(coords)` —— 把子的框换算到**父坐标**再画。
 * 那条路在真机上失效过一次：选中态已经切到右格（字的颜色换了档），底色却停在左格
 * （判据 KDoc 记在 `UiAuditG3Test` 那一笔，2026-10-17 真机截图）。
 * 换算链上任何一环拿不到真值（`parentCoordinates` 为空、父节点不是你以为的那一层），
 * 落地的都是**上一帧的矩形**，而胶囊的四个动画值只认当期目标 ⇒ 屏上读作"胶囊不跟手"。
 *
 * 现在改成：每格只报**自己的尺寸**（`onSizeChanged`，量的是节点自身，不牵涉任何人），
 * 胶囊左缘 = **前面各格宽度之和**。格子是 `Row` 里依次摆的，这条恒等式在 Row 自己的
 * 坐标系里成立，整条路上没有"换算"这一步 —— 少一环就少一个能错的地方。
 * 纵坐标恒 0：这一排每格都吃同一档 `.height(segmentedHeight - space1 * 2)`，
 * `Row` 的交叉轴报高就是它，胶囊贴着 Row 顶边画即是贴着格子画。
 */
@Composable
internal fun GalleryHomeDomainChips(
    selected: GalleryHomeDomain,
    onPick: (GalleryHomeDomain) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    // 条目表取**伴生里那一份实例**（页面上这一排只有这一处消费，别每次组合新建一个 List）。
    val entries = GalleryHomeDomain.HOME_OPTIONS
    val pill = RoundedCornerShape(percent = 50)
    // 颜色在组合期取（`tokens.color` 那串 getter 是 @Composable 的），`drawBehind` 里只能读现成的值。
    //
    // ⚠️ 方案 B（2026-10-17，用户拍板「先按方案b」）：粉块**两端都往 primary 那侧插值**，
    // 而从前只有一个平涂的 `primaryContainer` —— 评审的原话是「选中态现在太淡」。
    // 插值而不是换槽位：换槽位会牵动全仓所有"选中态"的读数，而这颗胶囊只需要"厚一点"。
    val pillFill = tokens.color.primaryContainer
    val pillTop = lerp(pillFill, tokens.color.primary, PILL_TINT_TOP)
    val pillBottom = lerp(pillFill, tokens.color.primary, PILL_TINT_BOTTOM)
    // 每格**自己的尺寸**（px）。两格 ⇒ 两个槽，不建 Map：
    // 越界只会拿到 null 走"先什么都不画"那一支，而 Map 的读法会把"还没量"与"这格宽 0"混成一谈。
    //
    // ⚠️ 量的是**节点自己**（`onSizeChanged`），不是 `parentCoordinates.localBoundingBoxOf(coords)`
    // 那一套 —— 理由见头注「胶囊的位置」：换算链上任何一环失真，落地都是上一帧的矩形，
    // 而胶囊只认当期目标，屏上就读作"点到 pixiv 胶囊不过去"。
    val sizes = remember(entries) { mutableStateListOf(*Array(entries.size) { IntSize.Zero }) }
    val selectedIndex = entries.indexOf(selected)
    // 选中格的左缘 = 前面各格宽之和（Row 里依次摆 ⇒ 恒等式）；宽与高 = 选中格自己那一格。
    // 纵坐标恒 0（每格同一档定高，Row 的交叉轴报高就是它）。
    val pillLeftTarget = sizes.take(selectedIndex).fold(0f) { acc, size -> acc + size.width }
    val pillWidthTarget = sizes.getOrNull(selectedIndex)?.width?.toFloat() ?: 0f
    val pillHeightTarget = sizes.getOrNull(selectedIndex)?.height?.toFloat() ?: 0f
    val spec = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    // 三个名都带 `pill` 前缀：裸名 `height` 会把 `Modifier.height` 遮掉（编译器直接报
    // "Function invocation 'height(...)' expected"），而这三个值只在绘制期读。
    val pillLeft by animateFloatAsState(pillLeftTarget, spec, label = "domainPillLeft")
    val pillWidth by animateFloatAsState(pillWidthTarget, spec, label = "domainPillWidth")
    val pillHeight by animateFloatAsState(pillHeightTarget, spec, label = "domainPillHeight")

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(pill)
                // 玻璃档轨道透底（模糊层由下一枚铺），实色档就是前面那一版的 `surfaceVariant`。
                //
                // 方案 B：轨道这一层也铺一遍面材质（顶亮底暗 + 顶部高光）。分段器是这一行里
                // **沉**的那一头，材质给得比搜索条弱一档（highlightAlpha 显式传低值）——
                // 一浮一沉是这一刀的全部意图，两头同强度就等于没分主次。
                .background(
                    if (veneraGlassEnabled()) Color.Transparent else tokens.color.surfaceVariant,
                )
                .veneraGlassSurface(VeneraGlassRole.CHIP, tokens.spacing.segmentedHeight / 2)
                .veneraFaceSheen(
                    topColor = lerp(tokens.color.surfaceVariant, tokens.color.surface, TRACK_SHEEN_LIFT),
                    bottomColor = tokens.color.surfaceVariant,
                    // 比搜索条低一档：轨道是背景层，它的高光只负责"这里也是一块面"，
                    // 不负责"这里是我要看的东西"。两个数字挨在一起写死会让"改一个忘了另一个"。
                    highlightAlpha = 0.55f,
                )
                .padding(tokens.spacing.space1),
        ) {
            // `drawBehind` 挂在**这一层**（Row）：每格报上来的宽度与底色共用同一套坐标
            // （都在 Row 自己的空间里，差一圈内衬就会整体偏掉）。
            Row(
                modifier = Modifier
                    .clip(pill)
                    .drawBehind {
                        // 第一帧还没量 ⇒ 宽 0 ⇒ 什么都不画（画一枚贴在左上角的 0 宽圆角矩形是噪声）。
                        if (pillWidth > 0f) {
                            drawRoundRect(
                                // 方案 B：粉块也是渐变（顶亮底深），与轨道/搜索条同一套材质语言。
                                // ⚠️ `verticalGradient` 的 `endY` 默认是 `size.height`，
                                // 而这里是**弹簧动画中的中间态** ⇒ 尺寸每帧都在变，
                                // 但 brush 的端点跟着 size 走，所以不会错位（只有硬写 px 端点才会）。
                                brush = Brush.verticalGradient(listOf(pillTop, pillBottom)),
                                // 纵坐标恒 0：每格同一档定高，Row 的交叉轴报高就是它。
                                topLeft = Offset(pillLeft, 0f),
                                size = Size(pillWidth, pillHeight),
                                cornerRadius = CornerRadius(pillHeight / 2f),
                            )
                        }
                    },
            ) {
                entries.forEachIndexed { index, item ->
                    val isOn = item == selected
                    Box(
                        modifier = Modifier
                            .height(tokens.spacing.segmentedHeight - tokens.spacing.space1 * 2)
                            // 只报**自己的尺寸**（不进任何父子坐标换算，见头注「胶囊的位置」）
                            .onSizeChanged { sizes[index] = it }
                            .clip(pill)
                            .clickable { onPick(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.label,
                            fontSize = tokens.type.itemTitle,
                            fontWeight = if (isOn) tokens.type.weightSemibold else tokens.type.weightRegular,
                            // ⚠️ 方案 B 修掉一处**反编码**（评审第四节）：
                            // 未选中那格此前吃 `textPrimary`（浅色实测 #67262A，9.0:1）——
                            // 比选中格的 `onPrimaryContainer` 还艳，读起来像"当前站是 Pixiv"。
                            // 站源切换器里两格的差别是"选没选中"，不是"重不重要"，
                            // 所以未选中就该退一档到 `textSecondary`。
                            color = if (isOn) tokens.color.onPrimaryContainer else tokens.color.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                            modifier = Modifier.padding(horizontal = tokens.spacing.space6),
                        )
                    }
                }
            }
        }
    }
}

/** 轨道顶面往上抬多少（往 surface 插值）。比粉块轻 —— 它是背景层。 */
private const val TRACK_SHEEN_LIFT = 0.35f

/**
 * 「画师」那一格的两种读法（2026-10-17 第九轮）。
 *
 * 用户原话：「推荐画师合并到发现和已关注供切换」。从前它们是**两节**（关注画师 / 推荐画师），
 * 各自一排、各自一个节头，一屏里占掉两段高度；合并成一节之后两排共用同一块地方，切换即可看到另一半。
 *
 * ⚠️ **切换不改取数**：两条腿（`/v1/user/following` 与 `/v1/user/recommended`）在
 * `GalleryScreen` 里是**同时**取的、各存各的。切 Tab 只是换"摆哪一份"，
 * 若做成"切过去才取"，切回来会先空一帧 —— 而那一帧在屏上读起来像"没有关注的人"。
 */
internal enum class PixivArtistTab(val label: String) {
    FOLLOWING("已关注"),
    DISCOVER("发现"),
}

/** 「画师」那一格的切换器：两枚胶囊，选中那枚换填充（与排行榜档位同一套，不另立一种按钮）。 */
@Composable
internal fun PixivArtistTabRow(
    selected: PixivArtistTab,
    onPick: (PixivArtistTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 2026-10-17 第十八轮（用户稿）：画师这一格换成**轨道 + 白色选中胶囊**。
    //
    // 稿上它与下面「日榜/周榜/…」不是同一种东西：档位那一排是"标签胶囊"（各自一枚浅底），
    // 而画师这里是 iOS 那种分段器 —— 一条浅灰轨道，选中项是压在轨道上的一枚白胶囊。
    // 两者混用会让"已关注/发现"读起来像两个可叠加的标签，而它们其实是二选一。
    PixivSegmentedRow(
        items = PixivArtistTab.entries.toList(),
        selected = selected,
        labelOf = { it.label },
        onPick = onPick,
        modifier = modifier,
    )
}

/**
 * 分段器（轨道 + 白胶囊）。宽度**跟着内容走**，不铺满整行 —— 稿上它只占左起一段，
 * 右边留白正是"这里还有别的东西吗"的负空间。
 */
@Composable
private fun <T> PixivSegmentedRow(
    items: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tokens.color.surfaceVariant)
            .padding(tokens.spacing.space1),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
    ) {
        items.forEach { item ->
            val isOn = item == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    // 选中那枚用 `surface`（卡面白）：轨道是 `surfaceVariant`，两者差一档
                    // ⇒ 不用投影也读得出"这枚被按住了"。
                    .background(if (isOn) tokens.color.surface else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { if (!isOn) onPick(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelOf(item),
                    fontSize = tokens.type.caption,
                    // 行高写死：这一枚切换时 `fontWeight` 在 regular/semibold 之间跳，
                    // 而它坐在**定高白卡**里 —— 行高若随字重动，切 Tab 时整张卡会跳一下
                    // （与 [PixivArtistRail] 那两排同源，见 `TEXT_LINE_HEIGHT` 的注）。
                    lineHeight = TEXT_LINE_HEIGHT.em,
                    fontWeight = if (isOn) tokens.type.weightSemibold else tokens.type.weightRegular,
                    color = if (isOn) tokens.color.textPrimary else tokens.color.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.padding(
                        horizontal = tokens.spacing.space4,
                        // 与 [PixivPillRow] 那一档**同一个数**（第二十三轮，用户「文字也小点，
                        // 同步画师那边选择器的大小就行」）—— 档名那排原本用 `itemTitle`、比这里大一号，
                        // 同一屏里两个分段器两种尺寸会被读成"不是一套东西"。
                        vertical = tokens.spacing.space1,
                    ),
                )
            }
        }
    }
}

/** 排行榜的档名那一排（日榜 / 周榜 / 月榜 / 新人 / 原创）。 */
@Composable
internal fun PixivRankingModeChips(
    selected: PixivRankingMode,
    onPick: (PixivRankingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 五档都是现量匿名 200 的那五档；R-18 两档匿名 403 ⇒ 一枚都不摆。
    PixivPillRow(
        items = PixivRankingMode.entries.toList(),
        selected = selected,
        labelOf = { it.label },
        onPick = onPick,
        modifier = modifier,
    )
}

/**
 * 「换一批」那枚单枚药丸（2026-10-17 第二十三轮，用户「推荐标题右边要多个换一批的胶囊」）。
 *
 * 视觉上就是 [PixivPillRow] 里的**一枚**：同样的 `percent = 50` 圆角、同样的底色与内衬档，
 * 所以它与同屏那两排分段器读起来是同一套控件 —— 一个尺寸不同的动作胶囊会被读成另一种东西。
 *
 * 取数里那一档才是"能不能摆"的前提，理由写在 `PIXIV_RECOMMEND` 那一节里。
 */
@Composable
internal fun PixivRemixChip(
    loading: Boolean,
    onRemix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tokens.color.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = VeneraPressFeedback.indication,
                enabled = !loading,
            ) { onRemix() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (loading) "正在换" else "换一批",
            // 字号与内衬都放大一档（第二十四轮，用户「换一批的按钮太小了」）。
            // 它是节头那一行的**唯一动作**，与同屏那两排分段器不是同一档东西 ——
            // 分段器是筛选（一排摆五档），这枚是单个把手，缩成筛选器那一档就找不到了。
            fontSize = tokens.type.itemTitle,
            // 行高同样钉住（与那两排同源：它坐在定高白卡旁的节头行里）。
            lineHeight = TEXT_LINE_HEIGHT.em,
            fontWeight = tokens.type.weightMedium,
            color = if (loading) tokens.color.textTertiary else tokens.color.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = tokens.spacing.space6,
                vertical = tokens.spacing.space3,
            ),
        )
    }
}

/** 两排药丸共用的一颗：选中那枚换填充，未选中那枚走 `surfaceVariant`（不画线，描边那版早被否）。 */
@Composable
private fun <T> PixivPillRow(
    items: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
    ) {
        items.forEach { item ->
            val isOn = item == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (isOn) tokens.color.primaryContainer else tokens.color.surfaceVariant)
                    .clickable { onPick(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelOf(item),
                    fontSize = tokens.type.caption,
                    // 行高写死：这一枚切换时字重跳，行高必须钉住（理由见 `TEXT_LINE_HEIGHT` 的注）。
                    lineHeight = TEXT_LINE_HEIGHT.em,
                    fontWeight = if (isOn) tokens.type.weightSemibold else tokens.type.weightRegular,
                    color = if (isOn) tokens.color.onPrimaryContainer else tokens.color.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.padding(
                        horizontal = tokens.spacing.space4,
                        // 4dp → 2dp（第二十三轮，用户「按钮可以再扁一点」）。两排标签共用这一档数值，
                        // 所以它们恒等高 —— 同一屏里摆两种尺寸的分段器会被读成"这两个不是一套东西"。
                        vertical = tokens.spacing.space1,
                    ),
                )
            }
        }
    }
}

/**
 * 「排行榜」节头右上的那一枚把手。
 *
 * 它**不是**二级页入口（这一批没有新路由），但行为是真的：点下去这一排从三张扩到
 * [GalleryPixivHome.RANKING_EXPANDED_ITEMS] 张，再点收回。稿上那句「查看全部」先按"展开"实现，
 * 等接了 pixiv 榜单二级页再换成跳页 —— 那时这一枚改一个字就行。
 */
@Composable
internal fun PixivRankingShowAll(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    // 「查看全部 ›」（2026-10-17 第十九轮）：节头 trailing 现在贴右缘（见 `MiuixSectionHeader`），
    // 配一枚 › 才读成"去另一个地方"，否则那段灰字像标题的注脚。
    Row(
        modifier = modifier.clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
    ) {
        Text(
            text = if (expanded) "收起" else "查看全部",
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
        )
        // 收起态不摆箭头：那一下是"原地收回"，不是"去某处"。
        if (!expanded) {
            Text(
                text = "›",
                fontSize = tokens.type.chevron,
                color = tokens.color.textTertiary,
            )
        }
    }
}

/**
 * 进 **pixiv 画师页**（`PixivArtistScreen`，2026-10-19）。
 *
 * 这是"点画师"的**唯一动作**（用户 2026-10-19：「改成点击画师头像进入画师页」）：
 * 首页横排、搜索「画师」分栏、卡片署名头像、作品页画师行 —— 全部落这一处，
 * 免得某一处忘了改、只在那面墙上退化成开网页。
 *
 * 传的是 `source` 的形状（`users/{id}`）而不是名字：画师页用
 * [GalleryArtistLinks.pixivUserId] 从它取号，**名字只是屏上的显示**。
 * 拿名字当身份会串人（名字可改、可重名）。
 *
 * 走 [com.venera.compose.openGalleryArtistProfile] 那一颗 —— 它与图库三站共用同一个宿主
 * （`GalleryArtistProfileActivity`），差别只在宿主按 `site` 分派到哪一屏。
 */
internal fun Context.openPixivArtistPage(userId: Long, artistName: String) {
    openGalleryArtistProfile(
        site = GallerySite.PIXIV,
        artistName = artistName,
        source = "https://www.pixiv.net/users/$userId",
    )
}

/**
 * 打开某位 pixiv 用户的**站方网页**。
 *
 * ⚠️ 2026-10-19 起这**不再是"点画师"的去处**（那一跳改成 [openPixivArtistPage]）。
 * 它退成"看站方原页"的正当出口：小说、约稿、私密收藏档这些只有网页有的东西。
 *
 * 与登录那一跳同一个优先级：先 Custom Tab（Chrome 里有他可能已登录的 pixiv 会话，
 * 打开就是登录态），起不来才退回裸 VIEW。顺序反了就等于没改。
 */
internal fun openPixivUserPage(context: Context, userId: Long) {
    val uri = Uri.parse("https://www.pixiv.net/users/$userId")
    val viaTab = runCatching {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .apply { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .launchUrl(context, uri)
    }
    if (viaTab.isSuccess) return
    // 没有支持 Custom Tabs 的浏览器：退回任意能开 https 的那一个。
    // 两次都起不来时不抛（崩在卡片里用户只看到"点了没反应"），由下一次点击再试。
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * 横滑一排的**两端渐隐**（2026-10-17 第十九/二十一轮）。
 *
 * ## 右端为什么恒亮
 *
 * 一排排到"刚好满 / 略微超出"那一档时，最后一枚被硬切，而屏上没有任何东西说"这一行还能往右滑"。
 * 渐隐是这件事唯一的视觉证据。它与尾部内衬是**同一件事的两半** —— 只加渐隐，
 * 滑到底时末枚仍压在渐隐底下（等于把"被裁"从中间挪到终点），所以调用方必须配
 * [hScrollEndContentPadding]。
 *
 * ## 左端为什么不能照抄
 *
 * 停在起点时左侧没有东西被裁，恒定画一道渐隐就是把第一枚的左缘糊在卡面内衬上。
 * 所以左端按「已离开起点」驱动：离开才淡入、滑回起点淡出。
 *
 * ⚠️ 左端的可见度以 **`State<Float>`** 交给 [hScrollStartFade]（那颗在 `drawWithContent`
 * 里读 `.value`）⇒ 淡入那 [RAIL_START_FADE_MILLIS] 只重绘、不重组。换成 `Float` 参数
 * 就是每帧把新 Modifier 递给 `LazyRow` 连带重组宿主，而 fling 那一秒要跑 60 次。
 *
 * ⚠️ `derivedStateOf` 包着「已离开起点」这个布尔：滑动过程中它**只在翻转时**通知，
 * 中间那些帧不触发重组。
 *
 * @param scroll 这一排自己的 `LazyListState`（调用方要把它同时传给 `LazyRow(state = …)`）。
 * @param container 这一排底下的那块颜色（坐在白卡里就传 `surface`，铺在页面底就传 `background`）——
 *   两端必须是同一个色，否则左右两道渐隐深浅不一。
 *
 * ⚠️ 2026-10-17 第三十轮起 `internal`：图库站关注画师行进了白卡之后与
 * [PixivArtistRail] 同档同形（用户「关注画师和每日热门 做成相同风格的ui」）——
 * 两端渐隐是**一颗**，两站各写一版就会漂（`GalleryHScrollRailHeightTest` 钉着这条）。
 */
@Composable
internal fun Modifier.horizontalRailFades(
    scroll: LazyListState,
    container: Color,
): Modifier {
    val leftTarget = remember(scroll) {
        derivedStateOf {
            scroll.firstVisibleItemIndex > 0 || scroll.firstVisibleItemScrollOffset > 0
        }
    }
    val startVisible = animateFloatAsState(
        targetValue = if (leftTarget.value) 1f else 0f,
        animationSpec = tween(durationMillis = RAIL_START_FADE_MILLIS),
        label = "railStartFade",
    )
    return this
        .hScrollStartFade(container = container, visible = startVisible)
        .hScrollEndFade(container = container)
}

/**
 * 一排 pixiv 画师（「已关注 / 发现」两半共用这一颗）。
 *
 * 数据两条腿见 [PixivUser]；这里只管"一排人怎么摆"。
 */
@Composable
internal fun PixivArtistRail(
    users: List<PixivUser>,
    onOpen: (PixivUser) -> Unit,
    /**
     * 画廊自己那份取图器（[com.venera.compose.gallery.data.GalleryImageLoader]）。
     *
     * ⚠️ 这里从前没传，`AsyncImage` 于是吃了 `LocalPlatformContext` 那枚**单例**
     * = `VeneraApp.newImageLoader` = **漫画那一份**。两处都是错的，而且都是这条链上已拍板口径的反面：
     * - 缓存预算：`GalleryImageLoader` 头注第 20~23 行那句"图站一批几百张 preview 灌进去会把
     *   漫画侧的封面挤掉"说的事，正好由这一排的 6~10 张头像在干 —— 它们落进了漫画那份
     *   Coil 默认值的 memoryCache / diskCache（默认目录，与漫画封面同一个）；
     * - 并发口径：画廊那份把 `maxRequestsPerHost` 从 OkHttp 默认的 5 抬到 16
     *   （见 `GalleryImageLoader.MAX_REQUESTS_PER_HOST` 那段读数），而漫画那份仍吃默认值 5 ——
     *   这一排的头像全打在同一个 `i.pximg.net` 上，第 6 位起就在队列里排队。
     *
     * 2026-10-17 第二十轮（用户报"快速滑动画师头像时整个框有奇怪的抖动"）一并收掉。
     */
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    // ── 高度必须由**几何**决定，不能由"视口里正好是谁"决定（2026-10-17 第二十一轮）──
    //
    // 用户报「快速滑动画师头像时整张白卡上下跳 1~2px」，并补一句「排行榜也在抖」。
    // 两处是**同一个形状**：一张 `surface` 白卡里装一条横向 `LazyRow`（画师一排 / 排行榜收起态）。
    //
    // 链条：
    // ① `LazyRow` 在**交叉轴**上报的是"当前已测量那几格里的最大高"，不是全部格子的最大高
    //    —— 懒布局只测视口内的项，这是横向量独有的语义（纵向 `LazyColumn` 至少一屏内稳定）；
    // ② 格子高度由文字那几行决定，而 `lineHeight` 未指定时行高取的是"这一行实际用到的字形"
    //    的 ascent+descent —— 纯拉丁名 / CJK / 带 emoji 的名字差 1~2px；
    // ③ 横滑 ⇒ 视口里换了谁 ⇒ 报高变 ⇒ 白卡高度变；
    // ④ 白卡本身是 `LazyStaggeredGrid` 的一个 item，item 一改高，网格把**后继全部项**重排
    //    ⇒ 屏上读起来是"整张白卡上下跳"。
    //
    // 所以这里两道一起上，缺一不可：
    // ① 名字 `Text` 写死 `lineHeight` ⇒ 行高不再由字形决定（治源头）；
    // ② 这一排的外框写死高度 ⇒ 报高从此是常量、与已测集合无关（治传播）。
    // 高度全部从 token 派生，不落魔法数字。
    val nameLineHeight = with(LocalDensity.current) {
        (tokens.type.caption * TEXT_LINE_HEIGHT).toDp()
    }
    val railHeight = tokens.spacing.artistAvatarSize + tokens.spacing.space1 + nameLineHeight
    val railScroll = rememberLazyListState()
    LazyRow(
        state = railScroll,
        modifier = modifier
            .fillMaxWidth()
            // 用 `heightIn(min = ...)` 而不是 `height(...)`：`LineHeightStyle` 默认 `Trim.None`，
            // 行高小于字体度量时不裁溢出、而是让文本更高 ⇒ 算出来的 `railHeight` 是**下限**而非精确值。
            // 钉成固定高会裁掉字尾；给下限则"内容更高时自己撑开"—— 而每格同字号、撑开的量相同，
            // 报高依然恒定，抖动照样不发生。
            .heightIn(min = railHeight)
            // 两端渐隐（右端恒亮 / 左端跟滚动位置，理由见 [horizontalRailFades]）。
            .horizontalRailFades(railScroll, tokens.color.surface),
        // 禁掉这一排的过度滚动（2026-10-17 第二十轮，用户报"快速滑动画师头像时整个框有奇怪的抖动"）。
        //
        // 这一排只有 5~6 位，一次用力甩动**必然**顶到尽头 ⇒ 平台默认的 stretch 会把整排内容
        // 一起拉伸再弹回；而这一排坐在一张 `clip(RoundedCornerShape)` 的白卡里，被拉伸的
        // 圆头像会往卡面圆角外挤 —— 屏上读起来就是"整个框在抖"。这与本仓既有的那条判据同源
        // （"有头图的墙禁过度滚动：stretch 会整张顶下去，观感很糟"），只是这一次张的是卡里的一排人。
        //
        // ⚠️ 恒传 `null` ⇒ 不需要 `rememberOverscrollEffect()`（那条"必须无条件先 remember"
        // 的规矩是针对"按开关取舍"的写法；这里没有开关，就没有条件调用）。
        overscrollEffect = null,
        // 间距 4dp → 12dp（2026-10-17 第十九轮，用户「拉大画师头像的间隔」+ 稿上特写）：
        // 裸圆头像之间没有卡面帮忙分隔，4dp 在 64dp 的球之间读起来是"挤在一起的一条"；
        // 稿上那个空气感是「间距 ≈ 头像直径的 1/4」，本仓现成档里 `space6`(12dp) 正好是 64 的 1/5。
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
        contentPadding = hScrollEndContentPadding(),
    ) {
        items(users, key = { it.id }) { user ->
            Column(
                modifier = Modifier
                    .width(tokens.spacing.artistAvatarSize)
                    // 可点面就是"头像 + 名字"这一整列：按压反馈走 `SinkFeedback`（与 miuix 卡片同一个原语）。
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = VeneraPressFeedback.indication,
                    ) { onOpen(user) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(tokens.spacing.artistAvatarSize)
                        // 淡描边（第二十三轮，用户「给页面所有的头像都加一层淡淡的描边」）：
                        // 裸圆压在白卡上时边界只有色差，滑动时那圈会被抗锯齿吃掉、读成"糊了一圈"。
                        .veneraAvatarRing(CircleShape)
                        .background(tokens.color.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (user.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = user.avatarUrl,
                            contentDescription = user.name,
                            // 取图器显式传画廊那一份（理由见函数参数那段注）。
                            imageLoader = imageLoader,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // 站方没给头像 ⇒ 首字母圆座，**不拿别人的头像顶**（同 [PixivUser] 那条注）。
                        Text(
                            text = user.name.take(1),
                            fontSize = tokens.type.itemTitle,
                            fontWeight = tokens.type.weightSemibold,
                            color = tokens.color.textSecondary,
                        )
                    }
                }
                Text(
                    text = user.name,
                    fontSize = tokens.type.caption,
                    // 行高写死（理由见 `PixivArtistRail` 头注那条链条 ②）：不写的话行高跟着
                    // 这一行用到的字形走，横滑时外面那张白卡会上下跳 1~2px。
                    lineHeight = TEXT_LINE_HEIGHT.em,
                    color = tokens.color.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = tokens.spacing.space1),
                )
            }
        }
    }
}

/**
 * 排行榜 / 推荐那一排：封面在上，下面两行是「01 · 作品标题」与画师名（用户稿的形状）。
 *
 * 序号取的是**站方给的 `rank`**（榜单那一腿直接带，1..500），不是"这一排里的第几枚" ——
 * 两者今天恰好相等，但展开、翻页与去重之后就不等了，拿位次冒充排名就是编读数。
 * `rank` 为 null（按标签那一腿）时那一行只剩标题，不补零序号。
 *
 * ## 收起态是**横滑**，不是三列网格（2026-10-17 第八轮，用户「排行榜这些卡片应该是可以左右滑动的」）
 *
 * 从前这里恒是 `chunked(3)` 的三列：第三张右边就是屏幕边，用户没有任何办法看到第 4 名 ——
 * 而"查看全部"那枚把手在节头右上角，读完三张再回顶上去点，与"继续往右划"是两种心智。
 * 现在收起态走 [LazyRow]、卡片定宽（[VeneraTokens.spacing.historyCardWidth]，与历史那一排同档），
 * 一屏露出第三张的一半 ⇒ "右边还有"这件事**在屏上看得出来**。
 *
 * 展开态仍是网格：那时用户已经明说要"看全部"，一列列铺开比横滑更好扫。
 *
 * 飞行：这一排的卡**参与**跨窗口飞行（2026-10-19 批 A 把半接线接上 —— 从前去程没截，
 * 点进去恒走"整页从下沿抬上来"那条退路）。去程截图与返回程落点都收在 `PixivWorkCard`
 * 一处（键 `viewerKey`，与作品页查询侧同串），不在这层网格里另接一遍。
 */
@Composable
internal fun PixivRankedWorkGrid(
    works: List<PixivWork>,
    maskedOf: (GalleryPost) -> Boolean,
    imageLoader: ImageLoader,
    onOpen: (GalleryPost) -> Unit,
    /** 展开态（节头已点「查看全部」）。收起态横滑、展开态三列网格。 */
    expanded: Boolean,
    /** 这一张在不在收藏里（走本地收藏档，与全站 ♥ 同一份）。 */
    favoritedOf: (GalleryPost) -> Boolean = { false },
    /** 点 ♥。不传就不画那颗心（不给假把手）。 */
    onToggleFavorite: ((GalleryPost) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    if (!expanded) {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            // 2026-10-17 第二十二轮（用户「缩到一行只同时显示两个半吧」）：一行露 2.5 张。
            //
            // 从前是三张均分（「(本行宽 − 两枚间距) / 3」），卡宽只剩 102dp —— 加上图区是 170dp 定高，
            // 每张读起来是 102×172 的**细长条**，三张挤在卡里，像被压扁了一排。
            //
            // 分母改 2.5f：**要减掉的间距仍是两枚**，别跟着一起除 ——
            // 第 2、第 3 张前面各有一枚；露出半张的那一枚不吃尾部间距（那截是渐隐带的宽度）。
            // 间距 6dp → 4dp（第二十三轮，用户「大卡片之间的间距再小点」）：封面通栏之后
            // 视觉间隙 = 4dp + 两张卡各自的圆角留白，再给 6dp 就读成"三张挤在一起"。
            val gap = tokens.spacing.space2
            val cardWidth = (maxWidth - gap * 2) / GalleryPixivHome.RANKING_PREVIEW_SLOTS
            val railScroll = rememberLazyListState()
            LazyRow(
                state = railScroll,
                modifier = Modifier
                    .fillMaxWidth()
                    // 两端渐隐（与画师那一排同一颗：右端恒亮 / 左端跟滚动位置）。
                    .horizontalRailFades(railScroll, tokens.color.surface),
                horizontalArrangement = Arrangement.spacedBy(gap),
                // 尾部内衬从 `screenHorizontal`(12dp) 换成 `space8`(16dp)：渐隐带宽就是 16dp，
                // 内衬比它窄 4dp 的话滑到底末张仍压在渐隐里 —— "渐隐与尾部内衬成对"那条纪律
                // （见 [horizontalRailFades]）。
                contentPadding = hScrollEndContentPadding(),
            ) {
                items(works, key = { it.post.uid }) { work ->
                    Box(modifier = Modifier.width(cardWidth)) {
                        PixivWorkCard(work, maskedOf(work.post), favoritedOf(work.post), imageLoader, onOpen, onToggleFavorite)
                    }
                }
            }
        }
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        works.chunked(GalleryPixivHome.RANKING_ROW_ITEMS).forEach { rowWorks ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
            ) {
                (0 until GalleryPixivHome.RANKING_ROW_ITEMS).forEach { slot ->
                    val work = rowWorks.getOrNull(slot)
                    // 凑不满一行时那两格留空槽而不是把现存的那张拉宽：拉宽会让同一批卡在
                    // 展开/收起两种形状下各长一个样，读起来像"卡片会变大"。
                    Box(modifier = Modifier.weight(1f)) {
                        if (work != null) {
                            PixivWorkCard(work, maskedOf(work.post), favoritedOf(work.post), imageLoader, onOpen, onToggleFavorite)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(tokens.spacing.space4))
        }
    }
}

@Composable
private fun PixivWorkCard(
    work: PixivWork,
    masked: Boolean,
    favorited: Boolean,
    imageLoader: ImageLoader,
    onOpen: (GalleryPost) -> Unit,
    onToggleFavorite: ((GalleryPost) -> Unit)?,
) {
    val tokens = VeneraTokens
    // 内容内衬归零 ⇒ **封面通栏**（2026-10-17 第二十三轮，用户「感觉还是太小了」）。
    //
    // 实测 p16：卡宽 140dp，封面只有 124 —— 两边各缩了 8dp `cardContentPadding`。
    // 而设计稿上封面是顶到卡片边缘的，四个圆角就是封面的四个角。
    // 那 8dp 让"卡面"整整小了一圈，也正是这一轮报"太小"里最直接的一笔。
    // 内衬归零的代价在下面：文字区自己补水平内衬。
    //
    // ── 飞行转场：登记**封面那一块**，去程截它，返回程落回它（2026-10-19 批 A 收口）──
    //
    // 从前登记挂在整张卡上（`VeneraCard`），去程压根没截 —— 排行榜那排点进去恒走
    // "整页从下沿抬上来"那条退路；回来即便有落点也是整卡（带着下面那条文字区），
    // 与作品页离场的"封面那一格"不同构，交棒那一刻就是一圈包裹凭空消失。
    // 批 A 把两半都接上：
    //
    // - 去程：点下那一刻把**封面那一块**的像素截下来（`GalleryFlyIn.capture`，与主墙
    //   `GalleryCardsGrid` 的 onOpen 同一个动作）—— 截的是封面，落点也是封面，两端同构；
    // - 返回程：作品页离场按同一把键（`GalleryFlyIn.cardBoundsOf(post.viewerKey)`）查
    //   "飞回哪儿"，矩形改挂封面（学 `GalleryPostCard` 的挂法）。
    //
    // 两端必须**同串**（`viewerKey`，不是 `uid`）—— `GalleryPostScreen` 的头注写着这条，
    // 不同串的表现是"静默不飞"，谁也不会报。
    //
    // ⚠️ 只在**真的变了**才写：布局回调每帧都来，无条件赋值等于每帧把这格重组一遍
    // （墙上那颗卡片栽过同一条，见 `GalleryFlyIn` 头注）。
    // ⚠️ 打码卡**不回写** —— 红线是"打码那张两边都不飞"，"表里压根没有它"是这条红线
    // 最不容易被绕过的一种落法（主墙 `GalleryPostCard` 同款）。
    val view = LocalView.current
    var coverRect by remember(work.post.uid) { mutableStateOf<Rect?>(null) }
    DisposableEffect(work.post.viewerKey) {
        onDispose { GalleryFlyIn.forgetCardBounds(work.post.viewerKey) }
    }
    VeneraCard(
        onClick = {
            coverRect?.let { GalleryFlyIn.capture(view, it) }
            onOpen(work.post)
        },
        edgeToEdge = true,
    ) {
        // ── 名次角标（2026-10-17 第九轮，设计稿左上角那枚数字）──
        //
        // 从前序号是拼在标题里的（`"01 · 标题"`），于是"名次"与"标题"在**同一行**抢宽度：
        // 标题长了先被截的是名次后面那几个字，而名次本身也不是一眼能扫到的东西。
        // 现在分开：角标只放**站方给的那个 `rank`**（不是"这一排里的第几枚"），标题回到纯标题。
        //
        // `rank == null`（按标签/推荐那两条腿）时**不画角标**：摆一枚"01"就等于编了一个站方没给的名次。
        //
        // ⚠️ 用户 2026-10-17 第二十二轮拍板：「角标本身 p 站是没有的」。
        // 也就是说这枚角标**不是站方语义**（那三枚 1/2/3 是设计稿自己加的），
        // 所以别再为它去改取数（`record["rank"]` 那个字段名的疑问到此为止，不追）——
        // 留这段只因为：哪天站方真给了 `rank`，摆出来是对的，不是编的。
        Box {
            VeneraCover(
                url = work.post.previewUrl,
                contentDescription = work.title.ifBlank { "pixiv #${work.post.id}" },
                preserveAspectRatio = false,
                mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                imageLoader = imageLoader,
                // 图区高度**跟着卡宽走**（2026-10-17 第二十二轮，用户「卡面太小了，就好像压扁了那样」）。
                //
                // 从前是定值 `historyCoverHeight`(170dp) —— 那是给历史那一排 124dp 宽的卡定的比例，
                // 搬到这里卡宽只有 102dp，图区就成了 102×172 的**竖条**（实测 0.59），
                // 竖构图的作品被裁得只剩中间一条，读起来正是"被压扁"。
                // 稿上图区是**近方形**（389×369 ⇒ 1.05），所以按卡宽算高：
                // 2.5 张制下卡宽 ≈126dp ⇒ 图区 ≈120dp，回到"方卡"。
                //
                // 不会引入抖动：收起态卡宽是我给的定值、展开态由 `weight(1f)` 定，
                // 两种情况下宽度都确定 ⇒ 高度确定（那条链条见下面 `heightIn` 的注）。
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(GalleryPixivHome.RANKING_COVER_ASPECT)
                    .onGloballyPositioned { coords ->
                        // 截像素那一路必须吃裁过的（PixelCopy 只读窗口内表面）：显式写出来。
                        val bounds = coords.boundsInWindow(clipBounds = true)
                        // 批 B §3 地基：登记落点吃没裁过的那一把（定位），截像素才吃裁过的。
                        val full = coords.boundsInWindow(clipBounds = false)
                        if (bounds != coverRect) {
                            coverRect = bounds
                            if (!masked) GalleryFlyIn.noteCardBounds(work.post.viewerKey, full, bounds)
                        }
                    },
            )
            work.rank?.let { rank ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(tokens.spacing.space2)
                        .clip(RoundedCornerShape(tokens.shape.small))
                        // 高对比：封面多半是花哨的图，半透明底压不住数字。
                        // 稿上三甲是同一枚实色（越靠前越重），这里不做第 1/2/3 三种颜色 ——
                        // 那三种色在任何一套主题下都要另立三条色板，而我们只有"主色"这一个立场。
                        .background(tokens.color.primary)
                        .padding(
                            horizontal = tokens.spacing.space2,
                            vertical = tokens.spacing.space1,
                        ),
                ) {
                    Text(
                        text = rank.toString(),
                        fontSize = tokens.type.caption,
                        fontWeight = tokens.type.weightSemibold,
                        color = tokens.color.onPrimary,
                        maxLines = 1,
                    )
                }
            }
            // ── AI 角标（2026-10-18，用户「开了 AI 角标，整个画廊一个都没见到」）──
            //
            // 这一族卡此前**一枚角标都没有**（只有名次与 ♥）：六处墙共用的那枚
            // `GalleryPostCard` 早就有 AI 角标，而 pixiv 首页的排行榜 / 推荐卡走的是
            // 另一个件（本函数），于是"打开开关"在 pixiv 域等于没开 —— 这正是那张截图里的情形
            // （截图是 pixiv 域首页）。胶囊件本身也从 GalleryScreen.kt 搬到了同包的
            // `GalleryCardCaption.kt`（见那边的搬家注），所以这里与主墙同源同形。
            //
            // 摆**右上角**：名次占左上、♥ 在左下，右上这一格本来空着。
            // 判据、开关与"没有信号就不摆"全部与主墙同一条（`GalleryAi` + 同一枚偏好）。
            val aiBadgeOn by GalleryPorts.of(LocalContext.current).prefs
                .galleryAiBadge.collectAsState()
            if (aiBadgeOn && GalleryAi.isAiMarked(work.post.siteAiFlag, work.post.tagList)) {
                GalleryAiPill()
            }
            // 多页角标（2026-10-18，用户「不用点进作品也能看出这张有几页」）：与主墙同一条判据
            // （`GalleryPost.pagesBadge`：站方没给或只有一页都不摆）、同一颗胶囊件，摆右下那一格。
            // 从前这一族卡只有名次与 ♥，页数只在详情腿里读得到 ⇒ 榜上那些多页作看不出是多页。
            work.post.pagesBadge?.let { GalleryPagesPill(it) }
        }
        // 文字区**定高**（理由见 `PixivArtistRail` 头注那条链条）：封面高度由卡宽经
        // `aspectRatio` 定（卡宽在收起态与展开态都确定），所以「封面 + 内衬 + 这一段」就是整卡高度。
        // 这一段若由字体度量决定，收起态那一排的报高就会随"视口里是哪几张"变
        // ⇒ 外面那张白卡上下跳 1~2px（用户 2026-10-17 报「排行榜也在抖」）。
        val titleLineHeight = with(LocalDensity.current) {
            (tokens.type.itemTitle * TEXT_LINE_HEIGHT).toDp()
        }
        val captionLineHeight = with(LocalDensity.current) {
            (tokens.type.caption * TEXT_LINE_HEIGHT).toDp()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 卡内衬归零了（封面要通栏）⇒ 文字区把水平内衬自己补回来。
                // 数值比原来的 `cardContentPadding`(8dp) 各边小一档：文字区一旦变胖，
                // "卡面就大"这件事在屏上就被吃回去了（用户 2026-10-17「感觉还是太小了」）。
                .padding(horizontal = tokens.spacing.space3, vertical = tokens.spacing.space2)
                // 下限而非固定高（理由见 `PixivArtistRail` 那处 `.heightIn` 的注）。
                .heightIn(min = titleLineHeight + tokens.spacing.space1 + captionLineHeight),
            verticalAlignment = Alignment.Top,
        ) {
            // 作者头像（2026-10-17 第二十二轮，用户「排行榜也没画师头像」）。
            //
            // 取的地址在 `PixivWork.authorAvatarUrl` —— 就在榜单那个响应的 `user.profile_image_urls`
            // 里，**不按 userId 再发一次请求**（一排 10 张就是 10 次额外往返）。
            // 空串 = 这条腿没给 ⇒ 画首字母座，**不拿别人的头像顶**（同 [PixivArtistRail] 那条纪律）。
            //
            // 16dp（`space8`）不是随手取的：设计稿上那颗占文字区高度约一半，
            // 而文字区高 ≈33dp ⇒ 16dp 正好复刻那个比例；再大就吃掉标题的宽度。
            Box(
                modifier = Modifier
                    .padding(end = tokens.spacing.space2)
                    .size(tokens.spacing.space8)
                    // 描边与画师那一排同一颗（先裁后描，见 [veneraAvatarRing] 的顺序注）。
                    .veneraAvatarRing(RoundedCornerShape(tokens.shape.small))
                    .background(tokens.color.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (work.authorAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = work.authorAvatarUrl,
                        contentDescription = work.post.author,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = work.post.author.take(1),
                        fontSize = tokens.type.caption,
                        lineHeight = TEXT_LINE_HEIGHT.em,
                        color = tokens.color.textSecondary,
                        maxLines = 1,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = work.title,
                    fontSize = tokens.type.itemTitle,
                    lineHeight = TEXT_LINE_HEIGHT.em,
                    fontWeight = tokens.type.weightSemibold,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = work.post.author,
                    fontSize = tokens.type.caption,
                    lineHeight = TEXT_LINE_HEIGHT.em,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = tokens.spacing.space1),
                )
            }
            // ♥ 走**本地收藏**（与全站收藏页同一份档）：只在调用方给了回调时才画，
            // 不画一枚点了没反应的装饰心。点它不打开大图（同一个 Box 里两个可点面，
            // ♥ 的面积够大，靠 `VeneraCard` 的 onClick 与这里的 clickable 各自命中）。
            if (onToggleFavorite != null) {
                val tint = if (favorited) tokens.color.primary else tokens.color.textTertiary
                Icon(
                    imageVector = if (favorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (favorited) "取消收藏" else "收藏",
                    tint = tint,
                    modifier = Modifier
                        .padding(top = tokens.spacing.space1, start = tokens.spacing.space2)
                        .size(tokens.spacing.badgeIconSize)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = VeneraPressFeedback.indication,
                        ) { onToggleFavorite.invoke(work.post) },
                )
            }
        }
    }
}
