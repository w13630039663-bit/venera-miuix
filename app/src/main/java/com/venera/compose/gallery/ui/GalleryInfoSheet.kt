package com.venera.compose.gallery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.components.venera.VeneraGallerySourceMark
import com.venera.compose.components.venera.VeneraIconButton
import com.venera.compose.feature.LocalVeneraDarkTheme
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryTagGroup
import com.venera.compose.gallery.data.galleryTagCategoryLabel
import com.venera.compose.gallery.domain.GalleryArtistLinks
import com.venera.compose.gallery.domain.GalleryRatingTone
import com.venera.compose.gallery.domain.GallerySheetScroll
import com.venera.compose.gallery.domain.GalleryTagCategory
import com.venera.compose.gallery.domain.GalleryTagCollapse
import com.venera.compose.gallery.domain.buildGalleryTagBuckets
import com.venera.compose.gallery.domain.galleryArtistNames
import com.venera.compose.gallery.domain.ratingLabel
import com.venera.compose.gallery.domain.ratingToneOf
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.ui.tokens.GalleryRatingColors
import com.venera.compose.ui.tokens.GallerySheetSection
import com.venera.compose.ui.tokens.GalleryTagCategoryColors
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

/**
 * 「关于这张图」半模态（照 Breadboard 的 `InfoSheet`，用户 2026-09-25 点名复刻）。
 * 它**取代**二级页原来那两张往下滚的卡 —— 二级现在是满屏图 + 底部工具条，信息收在这里。
 *
 * 用 M3 的 `ModalBottomSheet`，不用 miuix 那个 `OverlayBottomSheet`：
 * 后者把内容渲染在独立窗口里，读不到 `CompositionLocal`，会崩
 * （同一个坑记在 `feature/SearchScreen.kt:1500` 与 `:1645` 的注释里）。
 *
 * **只摆站方真给的字段**：参考截图里那行 `Pixiv URL` 与底部 `Sources | Imageboard` 分段，
 * 是它把"上游出处"和"原站作品页"分开存，而我们只有 `source` 一个串
 * （实测 yande.re 40 条里 3 条为空 → 空就整块不摆）。没有的字段一律不编。
 * 同理，图二参考稿里那行 `12 张作品 · 关注 8.3k` 与 `@handle` **都没有落地** ——
 * 两站的画师端点都不给这两个读数（yande.re 的 `artist.json` 只有 `id/name/alias_id/group_id/urls`，
 * Gelbooru 侧借的 danbooru 记录同理），编一行出来就是全仓最贵的那种错。
 *
 * ## 版面：五块（2026-10-01 **第三**次重排，用户报"分区的色块不明显"）
 *
 * 前两轮都在改**分组方式**（6 张卡 → 3 张 → 按内容重排），这一轮改的是**分组有没有可见载体**。
 * 实测（真机截图逐像素，1080×2376）：整屏内容区自始至终是同一个 `rgb(245,242,249)` ——
 * 上一版靠 `VeneraCard` 的 `surfaceContainerHigh` 与半模态的 `surfaceContainerLow` 拉开的那一档，
 * 在动态取色下只剩一个色调步，乘上玻璃的容器 alpha 之后归零，**一条边界都量不出来**。
 *
 * 所以这一版把"分区"从色调阶梯上挪下来，改由两件不随主题塌掉的东西承担
 * （判据与色值见 [GallerySheetSectionColors]）：
 * - **块的边界** = 一层极淡的色相洗底 + 一圈 `outlineVariant` 发丝描边（见 [SheetBlock]）；
 * - **块的归属** = 节标题前那枚 28dp 色相徽标（见 [SheetSectionBadge]），四块四色。
 *
 * 四块**同一种画法**（都有徽标 + 标题）：上一版只有「来源链接」「标签」有标题，
 * 前两块是"没有名字的那一块"，读起来像漏了 —— 而"数得出来这是几块"正是这一轮要的。
 *
 * | 位置 | 形状 | 色相 |
 * |---|---|---|
 * | 标题行右端 | 分级徽标 | 唯一影响"你能看到什么"的字段，该第一个被看到 |
 * | ① 基本信息 | 3 列图标网格 | 靛 |
 * | ② 画师 | 头像 + 名字 + 平台入口 + 关注 | 青 |
 * | ③ 来源链接 | 徽标 + 抬头 + 地址 + 动作 | 粉 |
 * | ④ 标签 | 每桶一行（分类名 + 胶囊），**默认收起** | 橙 |
 * | ⑤ 底部动作条 | 三枚并列动作，钉在屏底 | 不参与色相（它是页脚不是分区） |
 *
 * 三处**刻意不合并**的：
 * - 「出处」与「本站地址」是**两个不同的地址**（一个在 pixiv、一个在本站），各占一行。
 * - 每一行都留着**复制**钮：底部条那枚「复制链接」复制的是本站地址，带不出出处那一串。
 * - 事实网格里**没有值的格子直接不摆**（`width/height` 为 0、`md5` 为空、站方没给 `fav_count`），
 *   不摆 `0×0` 那种"看着像读数其实是没有"。这一条与上一版同一口径，只是从"少一行"变成"少一格"。
 *
 * ## 标签墙为什么要收起（判据在 [GalleryTagCollapse]）
 *
 * 一张 yande.re 的 `general` 桶实测 16 枚、整面墙 20 枚出头，安静地占掉四五行滚屏，
 * 而它在这一页里的分量最低。收起态每桶留 6 枚 + 行尾一枚 `+N`，展开归节标题右端那枚
 * 「展开全部」——**一枚都不删**，只是不默认铺开。
 *
 * @param onSearchTag 用户点了某一枚标签：把这枚标签交回一级画廊去搜（页面那边负责关 sheet、
 *   关本页，见 [GallerySearchHandoff]）。**这里不自己发请求** —— 搜索结果归画廊那一屏的
 *   ViewModel 持有，在这一页里另起一份就会出现在两处各存一半结果的分叉。
 * @param saving 底部那条「下载」正忙（状态由页面持有，与底栏那枚下载是同一份 ——
 *   两处各存一个布尔就会出现在底部条转圈、底栏那颗还能点的分叉）。
 * @param categories 站方对**这张帖**给的「标签 → 档位」（来自那张帖的 HTML，见
 *   [com.venera.compose.gallery.data.GalleryTagCategories]）。null = 这一笔没取到。
 * @param fallbackArtistNames 只在 [categories] 为 null 时参与：离线词典里判为画师的那几枚。
 *   两者**不会同时生效**（页面那边只在站方没给时才查词典），所以这里不需要仲裁规则。
 * @param artistCredits 这一张的画师署名（外链 + 头像）。**页面在打开这一张图时就取好**
 *   （用户 2026-10-01 第 2 条），面板打开时它通常已经到位 —— 这一块因此不再有自己的
 *   加载态，缺哪一位就摆哪一位的首字母座。取数那一份判据在 [loadArtistCredits]。
 * @param tagTranslations 标签 → 中文译名（离线词典）。**只改显示**：菜单里那三项与
 *   [onSearchTag] 回给画廊的仍是原文 —— 译文会随词典更新而变，屏蔽词表与检索词都存原值。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryInfoSheet(
    post: GalleryPost,
    categories: Map<String, Int>?,
    fallbackArtistNames: Set<String>,
    /**
     * 这一张的画师署名（`画师名 → 外链/头像`）—— 由大图页在**打开这一张图**时就取好
     * （用户 2026-10-01 第 2 条），这里只渲染。表里查不到的名字 = 还没回来，摆首字母座。
     */
    artistCredits: Map<String, ArtistCredits>,
    tagTranslations: Map<String, String>,
    imageLoader: ImageLoader,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSearchTag: (String) -> Unit,
    /** 画师那一块整块可点 → 介绍页（与"点标签 = 回一级搜这一枚"是两条路，批次 Q+R · 批量 4）。 */
    onOpenArtist: (String) -> Unit,
    /** 底部那条「下载」：与底栏那枚是**同一个**动作，带它的进度与 toast（页面持有）。 */
    onDownload: () -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    // 屏蔽规则要落库（suspend），动作归本面板持有；与漫画详情页那三个标签动作同一份分工。
    val guard = remember { GalleryPorts.of(context).contentGuard }
    val tagScope = rememberCoroutineScope()
    // 收起 / 展开只属于"这一次打开"：换一张图要回到收起态（`post.uid` 当 key）。
    // 不落盘也不跨页 —— 它是个阅读动作，不是设置。
    var tagsExpanded by remember(post.uid) { mutableStateOf(false) }

    /*
     * 内容区吃不完的那一份位移，**拖动放行、惯性全扣** —— 判据在 [GallerySheetScroll]，
     * 那边逐条写了"为什么手套那一份不能扣、甩那一份不能给"，以及返回值的方向为什么反直觉。
     * 少了这一层，面板盖满屏之后会一直抖、还拉不下来（用户 2026-10-01 报的第 3 条）。
     */
    // `NestedScrollSource.Drag` / `Fling` 在新版 compose 里已改名（UserInput / SideEffect），
    // 但这两个旧名在 1.7 与 1.12 上都在 —— 本仓的 compose 版本由 Miuix 在运行期顶，写新名会编不过。
    @Suppress("DEPRECATION")
    val sheetScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val eaten = if (source == NestedScrollSource.Fling) {
                    GallerySheetScroll.consumedFromFling(available.y)
                } else {
                    GallerySheetScroll.consumedFromDrag(available.y)
                }
                // 横向一分不动：这一页没有横滑的内容，横向位移归标签墙外面那些手势（如果有）。
                return Offset(0f, eaten)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(0f, GallerySheetScroll.consumedFromFling(available.y))
        }
    }

    // 分桶（判据与降级全在 buildGalleryTagBuckets，那边可单测）。
    val groups = remember(post.tagList, categories, fallbackArtistNames) {
        buildGalleryTagBuckets(post.tagList, categories, fallbackArtistNames)
    }
    /*
     * **署名那一组与标签墙分家**（用户 2026-09-29 点名）：画师不留在任何一份列表里，
     * 它换成②那块「头像 + 名字 + 平台入口 + 关注」—— 一行要摆三样，塞进定义列表三样都会挤没。
     * 角色 / 作品则**回到**标签墙（这一版改判）：它们没有任何附属动作，本来就是"标签"，
     * 与通用标签排在一起按档位分色，比夹在尺寸评分中间更好找。
     *
     * "哪几个是画师"走 domain 的 [galleryArtistNames]：大图页在**打开这一页时**就要按同一份
     * 名字去预取外链与头像（用户 2026-10-01 第 2 条），两处各写一遍迟早会对不上。
     */
    val artistNames = remember(post.tagList, categories, fallbackArtistNames) {
        galleryArtistNames(post.tagList, categories, fallbackArtistNames)
    }
    val artistLabel = galleryTagCategoryLabel(GalleryTagCategory.ARTIST)
    val wallGroups = groups.filterNot { it.label == artistLabel }

    val copyTag: (String) -> Unit = { tag -> copyToClipboard(context, "标签「$tag」", tag) }
    val blockTag: (String) -> Unit = { tag ->
        tagScope.launch {
            // 一律存成 TAG 规则，且存**站点原值**（译文会随字典更新失效）。选 TAG 而不是 KEYWORD
            // 是量出来的：画廊那面墙的屏蔽判据只喂 `post.tagList`，而两站的 `tags`
            // 就是那一串平铺标签（Gelbooru 实测没有 `tag_string_*` 那种分桶串）——
            // 所以这里每一枚 chip 的原串都必然在 tagList 里，规则加下去一定挡得住
            // （用 KEYWORD 会连作者名一起误伤）。
            val exists = guard.rules.value
                .any { it.type == "TAG" && it.pattern.equals(tag, ignoreCase = true) }
            when {
                exists -> Toast.makeText(context, "「$tag」已在屏蔽列表", Toast.LENGTH_SHORT).show()
                guard.addRule("TAG", tag) >= 0 -> Toast.makeText(
                    context, "已屏蔽「$tag」，可在设置 → 内容屏蔽管理", Toast.LENGTH_SHORT,
                ).show()
                else -> Toast.makeText(context, "屏蔽失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /*
     * 滚动区的高度上限按**屏幕**算，不用 `weight(1f, fill = false)`。
     *
     * 后者的上限是"父高 − 动作条"，而父高就是这张 sheet 自己的高度、sheet 的高度又由内容量出来
     * —— 一个自我引用的环。用户在面板上拖一下就会让两边互相推着走：面板动 → 可用高度变 →
     * 量出的内容高变 → 面板的锚点变 → 面板再动。表现是面板盖满屏之后**下拉不动、原地高频抖动**
     * （用户 2026-10-01 报的第 3 条）。上限一旦是"屏高减一个常量"，内容高度就与面板当前姿态无关。
     * 另一半修法（扣掉惯性）在 [sheetScrollConnection]，两份参数化成一个事实：
     * **内容区的高度与位移都不许反过来影响面板的姿态。**
     */
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val bodyMaxHeight = (screenHeight - tokens.spacing.sheetBodyHeightReserve)
        .coerceAtLeast(tokens.spacing.sheetBodyHeightReserve)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = bodyMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .nestedScroll(sheetScrollConnection)
                    .padding(horizontal = tokens.spacing.screenHorizontal),
            ) {
                // ── 标题行：标题在左、分级徽标在右 ──
                //
                // 标题吃 `screenTitle`（22/24sp）而不是 itemTitle：四块的**节标题**都是 itemTitle，
                // 面板标题再停在那一档，屏上就有五个一样大的标题在抢 —— 层级全平正是"乱"的来源之一。
                // 徽标不吃 weight（Row 先按自然宽量它，剩下的才给标题），否则
                // `存疑 (questionable)` 这种长标会被压成省略号。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "关于这张图",
                        fontSize = tokens.type.screenTitle,
                        fontWeight = tokens.type.weightSemibold,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    RatingBadge(rating = post.rating)
                }
                Spacer(modifier = Modifier.height(tokens.spacing.sheetGroupGap))

                // ── ① 基本信息：这张图的客观属性，3 列图标网格 ──
                GalleryFactGrid(post = post)

                // ── ② 画师 ──
                if (artistNames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(tokens.spacing.sheetGroupGap))
                    GalleryArtistCreditRows(
                        site = post.site,
                        artists = artistNames,
                        translations = tagTranslations,
                        // 取数**不在这里**：页面在打开这一张图时就取好了（用户 2026-10-01 第 2 条），
                        // 这里只渲染传进来的那一份，表里没有的名字摆首字母座。
                        credits = artistCredits,
                        imageLoader = imageLoader,
                        onOpenArtist = onOpenArtist,
                    )
                }

                // ── ③ 来源链接 ──
                if (post.source.isNotBlank() || post.pageUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(tokens.spacing.sheetGroupGap))
                    GallerySourceLinks(
                        post = post,
                        onCopySource = { copyToClipboard(context, "出处", post.source) },
                        onOpenSource = { openInBrowser(context, sourceHeadline(post.source), post.source) },
                        onCopyPage = { copyToClipboard(context, "本站地址", post.pageUrl) },
                        onOpenPage = { openInBrowser(context, post.site.displayName, post.pageUrl) },
                    )
                }

                // ── ④ 标签墙 ──
                if (wallGroups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(tokens.spacing.sheetGroupGap))
                    GalleryTagWall(
                        groups = wallGroups,
                        expanded = tagsExpanded,
                        onToggleExpand = { tagsExpanded = !tagsExpanded },
                        tagLabel = { tag -> tagDisplayLabel(tag, tagTranslations) },
                        onSearchTag = onSearchTag,
                        onCopyTag = copyTag,
                        onBlockTag = blockTag,
                    )
                }
                // 收尾留白：动作条已经把它与屏底隔开了，这里只需一点呼吸。
                Spacer(modifier = Modifier.height(tokens.spacing.sheetGroupGap))
            }

            // ── ⑤ 底部动作条 ──
            GallerySheetActionBar(
                isVideo = post.isVideo,
                saving = saving,
                onCopyLink = { copyToClipboard(context, "本站地址", post.pageUrl) },
                onDownload = onDownload,
                onOpenInBrowser = { openInBrowser(context, post.site.displayName, post.pageUrl) },
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// ① 事实网格
// ────────────────────────────────────────────────────────────────────────────

/** 三列：一屏宽下每格约 110dp，够摆一枚图标 + 三字标签，值那一行放得下 `3636×2607`。 */
private const val FACT_GRID_COLUMNS = 3

/** 网格里的一格。 */
private class FactCell(val icon: ImageVector, val label: String, val value: String)

/**
 * 基本信息：**一块 [SheetBlock] + 一块 3 列图标网格**。
 *
 * 为什么不是一列"字段名在左、值在右"的定义列表：那七行同形的读数里，
 * 人真正要找的通常只有一两样（多大、什么分级），而逐行读的代价是**要扫七行**。
 * 网格把每一样压成一个"图标 + 标签 + 值"的小块，眼睛按位置跳，不用按顺序读。
 *
 * 每一格**没有自己的底色**：块面本身就是那个"容器"，再加一层底色会变成
 * 六个小卡片钻进一张大卡片（上一版六张同形白卡的老毛病换个尺寸复现）。
 */
@Composable
private fun GalleryFactGrid(post: GalleryPost) {
    val tokens = VeneraTokens
    val cells = buildList {
        add(FactCell(Icons.Outlined.Language, "来源", post.site.displayName))
        // 尺寸取不到的条目（站方变体缺项）**整格不摆**：摆一个 `0×0` 就是编读数。
        if (post.width > 0 && post.height > 0) {
            add(FactCell(Icons.Outlined.AspectRatio, "分辨率", "${post.width}×${post.height}"))
        }
        add(FactCell(Icons.Outlined.Star, "评分", post.score.toString()))
        // 原图那一档的体积。**读数没有就不摆**：Gelbooru 的 post JSON 根本没有 file_size
        // （`GelbooruClient.kt:62` 记着这条天花板），解析处一律填 0 —— 摆出来每张
        // Gelbooru 图都写着「0 MB」，看着像"这文件是空的"。
        // 有值也必须走一位小数：整数除法会把 yande.re 上不到 1.5 MB 的真文件也压成「0 MB」。
        if (post.fileSize > 0) {
            add(FactCell(Icons.Outlined.Storage, "文件大小", "%.1f MB".format(post.fileSize / 1024.0 / 1024.0)))
        }
        // 指纹只摆前 8 位：这一格的用处是"两条看着一样的帖子是不是同一份文件"，
        // 8 位（32 bit）足够分辨，而全 32 位在这三列的格子里必然折成两行。
        post.md5.takeIf { it.isNotBlank() }?.let { add(FactCell(Icons.Outlined.Fingerprint, "MD5", it.take(8))) }
        // 上传者与画师**是两件事**，别互相顶替（实测：yande.re 的 `author` 与 Gelbooru 的
        // `owner` 都是**上传者的登录名**；真帖 1269641 的 `author=Arsy`，而画师是标签 `gijang`，
        // 那一位在②那块里）。画师不在这里，所以这里没有"重复显示一个人"的问题。
        post.author.takeIf { it.isNotBlank() }?.let { add(FactCell(Icons.Outlined.Person, "上传者", it)) }
        // yande.re 实测没有 `fav_count` → null 就整格不摆：摆 0 是"这张零收藏"，
        // 与"站方没给"是两回事。
        post.favCount?.let { add(FactCell(Icons.Outlined.FavoriteBorder, "收藏", it.toString())) }
        // 两站都没有时长字段，一律 null；真给到 0 也不是一段能看的视频，同样不摆。
        post.durationSeconds?.takeIf { it > 0 }?.let { add(FactCell(Icons.Outlined.Schedule, "时长", "${it.toInt()} 秒")) }
    }

    SheetBlock(tint = sheetSectionTint(GallerySheetSection.Facts)) {
        SheetSectionHeader(
            icon = Icons.Outlined.Info,
            title = "基本信息",
            tint = sheetSectionTint(GallerySheetSection.Facts),
        )
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4)) {
            cells.chunked(FACT_GRID_COLUMNS).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                ) {
                    row.forEach { cell -> GalleryFactCell(cell) }
                    // 末行不满时补空位：不补的话最后一格会按 weight 被拉宽，
                    // 与上面几行的列宽对不上，网格就散了。
                    repeat(FACT_GRID_COLUMNS - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * 网格里的一格：**上一行是图标 + 标签（次级）、下一行是值（主要）**。
 *
 * 值比标签大一号是刻意的：标签那三四个字（「分辨率」「上传者」）是**索引**，
 * 值的那个数才是要读的东西。反过来（标签大字、值小字）就是上一版定义列表的观感，
 * 也正是"信息卡读起来像参数表"的来源。
 *
 * ⚠️ 写成 `RowScope` 扩展：`Modifier.weight` 只在 `RowScope` 里解析得到，
 * 先在外面算成变量再传进来是编译不过的（本仓踩过一次，记在
 * `gallery-artist-alias-2026-09.md`）。
 */
@Composable
private fun RowScope.GalleryFactCell(cell: FactCell) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = cell.icon,
                contentDescription = null,
                tint = tokens.color.textTertiary,
                modifier = Modifier.size(tokens.spacing.chipIconSize),
            )
            Text(
                text = cell.label,
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        // 值只给一行：三列的一格约 96dp 净宽，`3636×2607` 那一档正好一行，
        // 放不下的是上传者那种长名 —— 那种情况省略号比折成两行好（折行会把整行的高度拉开，
        // 同一行里另两格的底边就对不齐了）。
        Text(
            text = cell.value,
            fontSize = tokens.type.body,
            color = tokens.color.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// ③ 来源链接
// ────────────────────────────────────────────────────────────────────────────

/**
 * 来源链接：**一块 [SheetBlock]，两行"徽标 + 抬头 + 完整地址 + [复制][打开]"**。
 *
 * 抬头那行（`pixiv.net · 147310074`）是**从地址本身推出来的**（域名 + 作品号），
 * 不是另存的一份元数据 —— 推不出来的那一档只摆域名（`GalleryArtistLinks.hostLabel` 的现有口径），
 * 绝不硬凑一个编号。
 *
 * ⚠️ 别拿 `GalleryArtistLinks.platformOf` 来出这个抬头：它回答的是"这条地址是不是**这个人**的主页"，
 * 对 `/artworks/…` 会**故意**答 `OTHER`（防的是"把作品页当画师主页"，见那个函数头注）。
 * 这里要的是"这是哪一家"，所以走域名。
 */
@Composable
private fun GallerySourceLinks(
    post: GalleryPost,
    onCopySource: () -> Unit,
    onOpenSource: () -> Unit,
    onCopyPage: () -> Unit,
    onOpenPage: () -> Unit,
) {
    val tokens = VeneraTokens
    val tint = sheetSectionTint(GallerySheetSection.Source)
    SheetBlock(tint = tint) {
        SheetSectionHeader(icon = Icons.Outlined.Link, title = "来源链接", tint = tint)
        Column(modifier = Modifier.fillMaxWidth()) {
            if (post.source.isNotBlank()) {
                GallerySourceLinkRow(
                    headline = sourceHeadline(post.source),
                    url = post.source,
                    // 认不出平台时 PlatformBadge 退中性底 + 通用链接字形（见它自己的头注）——
                    // 那是对的：给一个未知域名安一枚品牌色等于宣称它属于某个已知服务。
                    badge = { PlatformBadge(GalleryArtistLinks.platformOf(post.source)) },
                    onCopy = onCopySource,
                    onOpen = onOpenSource,
                )
                SheetDivider()
            }
            GallerySourceLinkRow(
                headline = "${post.site.displayName} · #${post.id}",
                url = post.pageUrl,
                // 站点标识取与 PlatformBadge 同一档边长：两行的徽标必须一样大，否则这两行
                // 读起来像两种东西（一行是"哪儿来的"，一行是"在这儿"）。
                badge = { VeneraGallerySourceMark(site = post.site, size = tokens.spacing.platformBadgeSize) },
                onCopy = onCopyPage,
                onOpen = onOpenPage,
            )
        }
    }
}

/**
 * 「出处」那一行的抬头：**域名 + 作品号**（推得出作品号时）。
 *
 * 编号只认 pixiv 那一条链（`GalleryArtistLinks.pixivArtworkId`，它认得 `/artworks/N`、
 * `member_illust.php?illust_id=N` 与图床上 `<号>_p<页>.<扩展名>` 三种形态）。
 * 别家的地址里也可能有一串数字，但那不是我们能断言的编号 —— 不摆。
 */
private fun sourceHeadline(url: String): String {
    val host = GalleryArtistLinks.hostLabel(url).removePrefix("www.")
    val artworkId = GalleryArtistLinks.pixivArtworkId(url) ?: return host
    return "$host · $artworkId"
}

@Composable
private fun GallerySourceLinkRow(
    headline: String,
    url: String,
    badge: @Composable () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badge()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                fontSize = tokens.type.body,
                color = tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 完整地址还是要摆出来：复制钮给的是"能粘走"，而这一行得让人**当场读得出来**
            // 这一串到底是哪个页面（pixiv 的作品号与 yande.re 的 #1265977 不是同一个东西）。
            Text(
                text = url,
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VeneraIconButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "复制$headline",
                tint = tokens.color.textPrimary,
            )
        }
        VeneraIconButton(onClick = onOpen) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = "打开$headline",
                tint = tokens.color.textPrimary,
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// ④ 标签墙
// ────────────────────────────────────────────────────────────────────────────

/**
 * 标签墙，**按站方给的分类分桶**（截图里 `Character` / `Copyright` 那种分组），默认收起。
 *
 * 判据与降级都不在这一层：分桶见 [buildGalleryTagBuckets] 与
 * [com.venera.compose.gallery.data.GalleryTagCategories]，收起见 [GalleryTagCollapse]。
 * 两站的 post JSON 确实不给分类（实测 yande.re 44 个键里没有、Gelbooru 只有一串平铺 `tags`），
 * 但那张贴的 **HTML 条目页**每枚标签都带 `tag-type-*` 类名 —— 那是站方对这张画的判定，
 * 所以这里摆得出的桶名**全部有出处**，仍然没有一处是按标签名猜的。
 * 站方判定取不到时只兜「画师」一栏（离线词典对两站的画师档误报实测 0 例），
 * 且那一栏**不上档位色**（见 `GalleryTagBuckets` 那段注释），其它桶宁可不摆。
 *
 * chips 是**可点的**（用户 2026-09-25 点名，语义与漫画详情页那枚药丸一模一样）：
 * 点击=用这枚标签去画廊搜索，长按=「搜索 / 复制 / 屏蔽」三项菜单。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalleryTagWall(
    groups: List<GalleryTagGroup>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    tagLabel: (String) -> String,
    onSearchTag: (String) -> Unit,
    onCopyTag: (String) -> Unit,
    onBlockTag: (String) -> Unit,
) {
    val tokens = VeneraTokens
    val tint = sheetSectionTint(GallerySheetSection.Tags)
    val total = groups.sumOf { it.tags.size }
    // 「展开全部」摆不摆，读的是**整面墙有没有一处会截** —— 一桶都不截时摆出来就是一枚假开关。
    val collapsible = groups.any { GalleryTagCollapse.overflows(it.tags) }
    SheetBlock(tint = tint) {
        SheetSectionHeader(
            icon = Icons.Outlined.LocalOffer,
            title = "标签 · $total",
            tint = tint,
            // 标题行**整行可点**：那枚「展开全部 ›」按 12sp 只有 48dp 宽，
            // 单给它挂点击就得靠猜位置；整行可点之后触达位是整屏宽，也不必再造一枚小钮。
            action = if (collapsible) (if (expanded) "收起" else "展开全部") else null,
            onAction = if (collapsible) onToggleExpand else null,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            groups.forEachIndexed { index, group ->
                if (index > 0) Spacer(modifier = Modifier.height(tokens.spacing.space4))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                ) {
                    GalleryTagGroupLabel(group)
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    ) {
                        val slice = GalleryTagCollapse.slice(group.tags, expanded)
                        slice.visible.forEach { tag ->
                            GalleryTagChip(
                                tag = tag,
                                text = tagLabel(tag),
                                onClick = { onSearchTag(tag) },
                                onCopy = { onCopyTag(tag) },
                                onBlock = { onBlockTag(tag) },
                            )
                        }
                        // 行尾那枚 `+N`：**它自己就是展开入口**（点它 = 点节标题那枚「展开全部」）。
                        // 只写一个数字不给动作的话，这枚胶囊会读成"还有 6 个标签"这样的读数，
                        // 而按下去没反应的胶囊比不摆更糟。
                        if (slice.hasMore) {
                            VeneraChip(
                                text = "+${slice.hiddenCount}",
                                variant = VeneraChipVariant.Neutral,
                                onClick = onToggleExpand,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 每一桶左侧那一列**分类名**（`角色` / `作品` / `通用`…）。
 *
 * 色取自 [GalleryTagCategoryColors]（索引就是站方那个数字档），与搜索补全行右侧那枚
 * 是同一份色表 —— 同一个档位在两个页面上必须同色，否则"颜色=归属"这条编码就断了。
 * 判据吃的是 [GalleryTagGroup.category]：**兜底那两路没有档位**（离线词典猜的、
 * 站方没判定的），一律退中性文字色，不硬安一个色相。
 *
 * 定宽 [VeneraTokens.spacing.sheetTagLabelWidth] 的理由见那个 token 的注释。
 */
@Composable
private fun GalleryTagGroupLabel(group: GalleryTagGroup) {
    val tokens = VeneraTokens
    val color = group.category
        ?.let { GalleryTagCategoryColors.of(it, LocalVeneraDarkTheme.current) }
        ?: tokens.color.textTertiary
    Row(
        modifier = Modifier
            .width(tokens.spacing.sheetTagLabelWidth)
            // 往下垫一档：这一列顶端要与右边第一行胶囊**视觉齐平**，
            // 而胶囊自己有纵向内衬（`chipVerticalPadding`），不垫就会顶高半行。
            .padding(top = tokens.spacing.space2),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = tagCategoryIcon(group.category),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(tokens.spacing.chipIconSize),
        )
        Text(
            text = group.label,
            fontSize = tokens.type.caption,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 档位 → 那一列的小图标。
 *
 * 色已经编码了归属，图标是**第二重**：色盲档下颜色不成立，图标还在。
 * 认不出档位（`null`）时给一枚通用标签字形 —— 不给它编一个具体归属的图形，
 * 与 [GalleryTagCategoryColors.of] 取不到就回 null 是同一条口径。
 */
private fun tagCategoryIcon(category: Int?): ImageVector = when (category) {
    GalleryTagCategory.ARTIST -> Icons.Outlined.Person
    GalleryTagCategory.CHARACTER -> Icons.Outlined.Face
    GalleryTagCategory.COPYRIGHT -> Icons.Outlined.Bookmark
    GalleryTagCategory.GENERAL -> Icons.Outlined.GridView
    GalleryTagCategory.METADATA -> Icons.Outlined.Info
    else -> Icons.Outlined.LocalOffer
}

// ────────────────────────────────────────────────────────────────────────────
// ⑤ 底部动作条
// ────────────────────────────────────────────────────────────────────────────

/**
 * 底部动作条：**复制链接 / 下载原图 / 在浏览器中打开**。
 *
 * 为什么把它钉在屏底而不是跟在内容后面：这一页的长度由标签数量决定，标签多的时候
 * "在浏览器中打开"会被推到几屏之外 —— 而它是这一页**用完就想走**的那一枚。
 *
 * 上面一条发丝线是它与内容的唯一分界：包一层底色没必要（两者本来就在同一张 sheet 面上），
 * 但**不画线**时内容滚到底会与动作条糊在一起，分不清哪一行是内容、哪一行是行为。
 */
@Composable
private fun GallerySheetActionBar(
    isVideo: Boolean,
    saving: Boolean,
    onCopyLink: () -> Unit,
    onDownload: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    val tokens = VeneraTokens
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(tokens.spacing.hairline)
                .background(tokens.color.outlineVariant),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = tokens.spacing.screenHorizontal,
                    end = tokens.spacing.screenHorizontal,
                    top = tokens.spacing.space4,
                    bottom = tokens.spacing.space4,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GallerySheetAction(
                icon = Icons.Outlined.ContentCopy,
                label = "复制链接",
                onClick = onCopyLink,
            )
            GallerySheetActionDivider()
            // 标签跟着内容走：视频条目的原始档是一段 mp4，写「下载原图」就成了另一个东西。
            GallerySheetAction(
                icon = Icons.Outlined.Download,
                label = if (isVideo) "下载视频" else "下载原图",
                onClick = onDownload,
                // 进行中**吃掉点击并换成波浪环**：视频原片实测 16~26 MB 那几秒里，
                // 一个没有任何反馈、还能再按一次的钮就是"点了没反应"。
                // 与底栏那枚下载是同一个 `saving`（页面持有），所以两处不会各转各的。
                enabled = !saving,
                busy = saving,
            )
            GallerySheetActionDivider()
            GallerySheetAction(
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                label = "在浏览器中打开",
                onClick = onOpenInBrowser,
            )
        }
    }
}

/** 动作条里的一项：图标 + 文案，均分整条宽度。 */
@Composable
private fun RowScope.GallerySheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val tokens = VeneraTokens
    val tint = if (enabled) tokens.color.textPrimary else tokens.color.textDisabled
    Row(
        modifier = Modifier
            .weight(1f)
            .height(tokens.spacing.sheetActionHeight)
            .clip(RoundedCornerShape(tokens.shape.small))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(tokens.spacing.chipIconSize),
                color = tokens.color.primary,
                trackColor = tokens.color.surfaceVariant,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(tokens.spacing.chipIconSize),
            )
        }
        Spacer(modifier = Modifier.width(tokens.spacing.space2))
        Text(
            text = label,
            fontSize = tokens.type.caption,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 三枚动作之间的竖发丝线。高度取半档，免得读成"把这一条切成三格"。 */
@Composable
private fun GallerySheetActionDivider() {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .width(tokens.spacing.hairline)
            .height(tokens.spacing.space11)
            .background(tokens.color.outlineVariant),
    )
}

// ────────────────────────────────────────────────────────────────────────────
// 共用件
// ────────────────────────────────────────────────────────────────────────────

/**
 * 标题行右端那枚**分级徽标**。
 *
 * 它取代了原来那张「分级 / 站点」小卡里的**分级**一半。为什么值得单独挪出来：
 * 分级是这一页唯一影响"你能看到什么"的字段（它还连着内容守卫那一档），
 * 而此前它跟「站点」同形同宽地并排，读起来只是又一个字段。
 * 挪到标题行右端之后，它成了这一页第一个被看到的东西 —— 而"站点"退进①事实网格第一格，
 * 那才是它该有的分量。
 *
 * 走 [VeneraChip] 而不是自造一枚 Badge：那个组件的头注明写"这是**唯一**的 Chip 实现，
 * 禁止再复制一套 UI"，所以底色与字色是从它新开的两个覆盖口子进去的。
 *
 * **不给 `onClick`** —— 它是个读数，不是按钮。给了会有一份按得动的假反馈。
 * 色与档位的对应（以及为什么只有四档色）见 `gallery/domain/GalleryRating.kt` 的头注。
 */
@Composable
private fun RatingBadge(rating: String) {
    VeneraChip(
        text = ratingLabel(rating),
        variant = VeneraChipVariant.Neutral,
        containerColorOverride = when (ratingToneOf(rating)) {
            GalleryRatingTone.Safe -> GalleryRatingColors.Safe
            GalleryRatingTone.Caution -> GalleryRatingColors.Caution
            GalleryRatingTone.Explicit -> GalleryRatingColors.Explicit
            GalleryRatingTone.Unknown -> GalleryRatingColors.Unknown
        },
        contentColorOverride = GalleryRatingColors.OnRatingBadge,
    )
}

/**
 * 卡片**内部**的分隔发丝线。
 *
 * 存在的理由是这一页的病根：一屏堆同形同距的 `VeneraCard`，每一块都在喊
 * "我和旁边那块一样重要"。同一张卡里要分两组信息时，应该是一条发丝线，而不是再开一张卡。
 */
@Composable
private fun SheetDivider() {
    val tokens = VeneraTokens
    Spacer(modifier = Modifier.height(tokens.spacing.space4))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(tokens.spacing.hairline)
            .background(tokens.color.outlineVariant),
    )
    Spacer(modifier = Modifier.height(tokens.spacing.space4))
}

/**
 * 一枚药丸上的显示文本：**只改显示**。
 *
 * `译文 (原文)` 这个双显形状与漫画侧 `rememberTagDisplayLabel` 同一口径，
 * 原文常驻括号里是刻意的 —— 词典覆盖不到的标签会退回原文，若同时隐藏原文，
 * 用户就无从判断这枚药丸对应站方那个词。
 *
 * ⚠️ 点它发出去的、复制的、屏蔽落库的都得是**原串**（调用方一直传 `tag`，不传这个返回值）。
 */
internal fun tagDisplayLabel(tag: String, translations: Map<String, String>): String {
    val label = translations[tag.lowercase()]?.takeIf { it.isNotBlank() && it != tag }
    return if (label == null) tag else "$label ($tag)"
}

/**
 * 一枚可交互的标签药丸 —— **画法照 `feature/ComicDetailScreen.kt` 的 `DetailTagChip`**：
 * 点击直达该标签搜索，长按起菜单。按压反馈由 `VeneraChip` 统一持有，这里只给回调。
 *
 * 菜单**逐项各挂一个**：FlowRow 排布下共用一个锚会让菜单永远从第一枚的位置弹出来
 * （详情页已经踩过，`FavoriteImagesScreen.kt:601` 那条注释记着同一件事）。
 *
 * 三项里"搜索"与点击重复，是**刻意**的：长按菜单是"这块区域能做什么"的目录，
 * 少了这一项，用户只能靠猜知道点击也可以。
 *
 * [onCopy]/[onBlock] 给 null 就是"这一枚不带菜单"（画师那一行用它）。
 */
@Composable
private fun GalleryTagChip(
    tag: String,
    text: String,
    onClick: () -> Unit,
    onCopy: (() -> Unit)?,
    onBlock: (() -> Unit)?,
) {
    val tokens = VeneraTokens
    val view = LocalView.current
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        VeneraChip(
            text = text,
            variant = VeneraChipVariant.Tag,
            onClick = onClick,
            // 条件与下面那块菜单**各写一次**，不用一个布尔变量接着：
            // 那样 Kotlin 认不出 `onCopy` 已经不是 null，只能写成 `?.invoke()` 反而更绕。
            onLongClick = if (onCopy != null && onBlock != null) {
                {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    menuExpanded = true
                }
            } else null,
        )
        if (onCopy != null && onBlock != null) DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text("搜索「$tag」", fontSize = tokens.type.caption, color = tokens.color.textPrimary)
                },
                onClick = {
                    menuExpanded = false
                    onClick()
                },
            )
            DropdownMenuItem(
                text = {
                    Text("复制「$tag」", fontSize = tokens.type.caption, color = tokens.color.textPrimary)
                },
                onClick = {
                    menuExpanded = false
                    onCopy()
                },
            )
            DropdownMenuItem(
                text = {
                    Text("屏蔽该标签", fontSize = tokens.type.caption, color = StatusColors.Failing)
                },
                onClick = {
                    menuExpanded = false
                    onBlock()
                },
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
}

/** 「打开」：跳系统浏览器。失败必须说话 —— 静默失败读起来就是"按了没反应"。 */
private fun openInBrowser(context: Context, siteName: String, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(context, "打不开${siteName}：${it.message}", Toast.LENGTH_SHORT).show()
    }
}
