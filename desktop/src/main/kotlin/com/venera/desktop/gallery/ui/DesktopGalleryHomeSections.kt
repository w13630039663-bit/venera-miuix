package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPorts
import io.github.composefluent.component.Text

/**
 * 桌面图库首页**节的唯一事实源** —— key 常量与次序照 Android 侧那一颗
 * （`app/src/main/java/com/venera/compose/gallery/ui/GalleryHomeSections.kt:73-97` 的 key 常量 + `ORDER`，
 * `:530-552` 的 `when(key)` 渲染），改动有三处，都在下面逐条写明。
 *
 * ## 六项，次序钉死
 *
 * `每日热门 → 正在关注的画师 → 历史 → 下载 → 最新 → 排行榜`。
 *
 * ## 项数恒 6，**绝不跟数据变**
 *
 * 某一项今天没内容 ⇒ 那一行**还在**，只是走"未实现"形状。绝不做成"数据到货才插项"——
 * 那是 Android 侧锚定漂移的桌面版：懒列表在第 0 项之后插一项，视口锚点漂到下一项，
 * 内容被画进 `contentPadding` 里（现象读起来是"顶栏重叠 / 返回被打回默认值 / 落地闪一下"，
 * 三条其实是同一根因，见记忆「导航条目会重建组合」）。
 *
 * ## `when` 不留 else —— 但穷尽检查得落在编译器管得住的地方
 *
 * ⚠️ 这里与 Android 那颗**有一处实质差别**，不是抄漏：`when` 只在**表达式位置**才被 Kotlin 做穷尽检查，
 * 而 `String` 的 key 永远"不够穷尽"（编译器不知道六枚常量就是全部），所以照抄 `when(key)` 只有两种结局 ——
 * 要么写 `else`（就是本节点名禁止的那个），要么把它写成语句而**根本没有编译期保护**
 * （Android 那颗文件里"忘了登记就编译不过"那句注释其实是说过头的，那边真正的尺子是用例）。
 * 所以桌面这一颗把内容件做成 sealed（[DesktopGallerySectionContent]），`when` switch 在它上面：
 * **新增一种内容件忘了出形状 ⇒ 编译不过**，这条才是真 teeth；
 * 而"新增一节忘了登记进 `ORDER`"由 [DesktopGalleryHomeSectionsTest] ①② 两条钉住。
 *
 * ## 每一项的形状：内容件与原因**恒等二选一**
 *
 * `content == null` 与 `reason != null` 同真同假（XOR），不许有中间态。用例 ③ 逐 key 断言，
 * 出现"两者都非空 / 两者都空"即红 —— 前者是"既能渲染又有理由"的自相矛盾，
 * 后者就是本仓最忌的"看起来正常但实际少了一半"。
 */
internal object DesktopGalleryHomeSectionKey {
    const val DAILY = "section-daily"
    const val FOLLOWED_ARTISTS = "section-followed-artists"
    const val HISTORY = "section-history"
    const val DOWNLOADS = "section-downloads"
    const val LATEST = "section-latest"
    const val RANKING = "section-ranking"
}

/** 六项的**次序**，就是 pane 里自上而下的次序（用户口径：项数与次序都不许跟着数据变）。 */
internal val DESKTOP_GALLERY_HOME_SECTION_ORDER: List<String> = listOf(
    DesktopGalleryHomeSectionKey.DAILY,
    DesktopGalleryHomeSectionKey.FOLLOWED_ARTISTS,
    DesktopGalleryHomeSectionKey.HISTORY,
    DesktopGalleryHomeSectionKey.DOWNLOADS,
    DesktopGalleryHomeSectionKey.LATEST,
    DesktopGalleryHomeSectionKey.RANKING,
)

/**
 * 「有内容件」的三种说法中的两种 —— 只有这一颗 sealed 才是 `when` 穷尽检查的载体
 * （理由见类注释里那一节）。今天两档，S3 接上「最新」之类时加一档，忘了在两颗 `when` 里出形状的代价是编译不过。
 */
internal sealed interface DesktopGallerySectionContent {
    data object Daily : DesktopGallerySectionContent
    data object FollowedArtists : DesktopGallerySectionContent
}

/**
 * pane 里的一行。
 *
 * ⚠️ 这里**没有** `onClick` 字段：四行未实现的行没有任何可执行动作，而把一颗对四行恒为 `null`、
 * 对两行没人读的字段放进模型里，就是本仓记过的那种"漏传不报错的参数"。
 * 「未实现的行不许点」改由两处管住：这颗文件的两颗 `when` 里 `null` 分支只出 [DesktopUnimplementedRow]
 * （那颗件连 `clickable` 都没 import），以及用例对源码文本的负向断言。
 */
internal data class DesktopGalleryHomeRow(
    val key: String,
    val title: String,
    val glyph: String,
    val content: DesktopGallerySectionContent?,
    val reason: String?,
)

/**
 * 六行。**唯一的输入是"这一行被点了要做什么"** —— 没有任何数据输入，所以项数在结构上就不可能跟着数据变。
 *
 * ## 四行「未实现」各自**变可点**的判据（原文抄自 S2-B 计划 §2.2，将来点亮时要同时满足，别只留结论）
 *
 * - **历史**：存在 `gallery/data/GalleryHistoryStore.kt`（或 `data/db` 里一张 gallery 历史表）
 *   **且**注册表有 `HISTORY` **且**有内容件 —— 三者同时。
 * - **下载**：`GallerySaver` 落点从 MediaStore 改为 `PathProvider` 目录 **且**存在一颗枚举读侧。
 * - **最新**：`BoardSource.searchPosts` 有可用排序参数**且**三站中 ≥2 站真返回。
 * - **排行榜**：`GalleryBoards` 上有跨站统一取榜成员 **且**「yande.re 固定 40 条、忽略分页」这条天花板
 *   在 UI 上被说出来（否则它点亮那一刻就是"看起来还有更多"的假读数）。
 *
 * 四句 caption 各自点名的缺席件（写进界面上的句子必须短到两行念完，见 [DesktopUnimplementedRow] 的预算说明）：
 * 历史 = 本地历史存储、下载 = 存图落点、最新 = 按时间的排序参数、排行榜 = 跨站取榜口 + 40 条天花板。
 */
internal fun desktopGalleryHomeRows(): List<DesktopGalleryHomeRow> = listOf(
    DesktopGalleryHomeRow(
        key = DesktopGalleryHomeSectionKey.DAILY,
        title = "每日热门",
        glyph = "日",
        content = DesktopGallerySectionContent.Daily,
        reason = null,
    ),
    DesktopGalleryHomeRow(
        key = DesktopGalleryHomeSectionKey.FOLLOWED_ARTISTS,
        title = "正在关注的画师",
        glyph = "绘",
        content = DesktopGallerySectionContent.FollowedArtists,
        reason = null,
    ),
    DesktopGalleryHomeRow(
        key = DesktopGalleryHomeSectionKey.HISTORY,
        title = "历史",
        glyph = "历",
        content = null,
        reason = "还没有本地历史存储，读不到浏览记录",
    ),
    DesktopGalleryHomeRow(
        key = DesktopGalleryHomeSectionKey.DOWNLOADS,
        title = "下载",
        glyph = "存",
        content = null,
        reason = "存图落点还没接桌面，没有可枚举的目录",
    ),
    DesktopGalleryHomeRow(
        key = DesktopGalleryHomeSectionKey.LATEST,
        title = "最新",
        glyph = "新",
        content = null,
        reason = "三站列表都没有按时间的排序参数",
    ),
    DesktopGalleryHomeRow(
        key = DesktopGalleryHomeSectionKey.RANKING,
        title = "排行榜",
        glyph = "榜",
        content = null,
        reason = "没有跨站取榜的口，yande.re 也只固定给 40 条",
    ),
)

/** 六行按 key 取一行；次序与成员由 [DESKTOP_GALLERY_HOME_SECTION_ORDER] 一处定死。 */
internal fun List<DesktopGalleryHomeRow>.byKey(key: String): DesktopGalleryHomeRow =
    firstOrNull { it.key == key } ?: error("注册表里没有 key「$key」—— 它不在 ORDER 六项里")

/**
 * pane 里的一行怎么**摆**：内容件决定形状，`null` 那一档只有 [DesktopUnimplementedRow] 一条路。
 *
 * `when` 挂在 `val rendered: Unit =` 这个**表达式位置**上（而不是写成一条语句）：
 * Kotlin 只对表达式位置的 `when` 做穷尽检查，那颗 `val` 就是这根钩子，别把它当冗余删掉。
 */
@Composable
internal fun DesktopGalleryHomeNavRow(row: DesktopGalleryHomeRow, selected: Boolean, onSelect: (String) -> Unit) {
    val rendered: Unit = when (row.content) {
        DesktopGallerySectionContent.Daily -> DesktopGalleryHomeSelectableRow(row, selected) { onSelect(row.key) }
        DesktopGallerySectionContent.FollowedArtists -> DesktopGalleryHomeSelectableRow(row, selected) { onSelect(row.key) }
        null -> DesktopUnimplementedRow(row.title, row.glyph, requireNotNull(row.reason))
    }
}

/**
 * 选中行的**内容区**。这一颗是集成方唯一需要认识的形状：它按注册表把六项分到两颗 pane 与"缺席读数"三档。
 *
 * ⚠️ 与 Android 侧那条纪律一致：pane 只吃形参 [GalleryPorts]，**不在 UI 里调 `GalleryPorts.of(...)`** ——
 * 桌面的接线件（`DesktopGalleryPorts`）由 A 线在另一个 worktree 里落，形状不在本批的编译面上。
 */
@Composable
internal fun DesktopGalleryHomeBody(row: DesktopGalleryHomeRow, ports: GalleryPorts) {
    val rendered: Unit = when (row.content) {
        DesktopGallerySectionContent.Daily -> DesktopDailyPane(ports)
        DesktopGallerySectionContent.FollowedArtists -> DesktopFollowedArtistsPane(ports)
        null -> DesktopAbsentBody(row)
    }
}

/**
 * 没接的那一项在内容区里的说法：**把 caption 那句话原样再说一遍**，不画空墙。
 *
 * 宁可慢、宁可难看，也不许"内容区一片空白而 pane 上看不出行没接" —— 那是静默交错的另一种形状。
 */
@Composable
private fun DesktopAbsentBody(row: DesktopGalleryHomeRow) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${row.title} · ${UNIMPLEMENTED_LABEL}")
        Text(requireNotNull(row.reason), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * 有内容件的那一行。形状来自稿：`.nav`（`:73-78`）—— 高 40、内缩 12、图标位 16、
 * 选中态是**藤紫到 Layer2 的横向渐变 + 行首一枚 3×20 圆角指示条**，未选中悬停走 `--card-hov`。
 *
 * 选中态的文字加粗（稿 `font-weight:600`），图标换成 `--fuji-lite`（`:77`）。
 * 稿上的计数徽标 `.nav .cnt`（`1.2k` / `286` / `2` / `3,412`）**一颗都不带**：那些数要有 store 才念得出口。
 */
@Composable
private fun DesktopGalleryHomeSelectableRow(row: DesktopGalleryHomeRow, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(Modifier.fillMaxWidth().height(DesktopGalleryMetrics.navRowHeight)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DesktopGalleryMetrics.navRowHeight)
                .background(
                    brush = when {
                        selected -> SelectionBrush
                        hovered -> SolidColor(HoverBackground)
                        else -> SolidColor(Color.Transparent)
                    },
                    shape = RoundedCornerShape(4.dp),
                )
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = DesktopGalleryMetrics.navRowHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.navRowGap),
        ) {
            Box(Modifier.width(DesktopGalleryMetrics.navGlyphSlotWidth), contentAlignment = Alignment.Center) {
                Text(row.glyph, color = if (selected) SelectedGlyph else Glyph, fontSize = 14.sp, maxLines = 1)
            }
            Text(
                row.title,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            // 稿 `.nav.sel::before`：left 0、top 10、3×20、圆角 3。高 40 里 top 10 就是垂直居中。
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(20.dp)
                    .background(IndicatorColor, RoundedCornerShape(3.dp)),
            )
        }
    }
}

// 下面几颗色值逐条来自设计稿的深色档（`:10-17` 的变量表与 `:76-78` 的选中态），不是新造数：
// 浅色档的正式映射归设计稿回改（S5），本轮不自己发挥。
private val SelectionStart = Color(0xFF3A2A55)
private val SelectionEnd = Color(0xFF2C2C2C)

// 稿上 `.nav.sel` 是 `linear-gradient(90deg,#3A2A55,#2C2C2C 78%)`；Compose 的停靠点写法与 CSS 同形，
// 0f 与 0.78f 就是那两档，无需换算（方向差一条 90deg 与对角线的观感，S5 出图时一并校）。
private val SelectionBrush = Brush.linearGradient(0f to SelectionStart, 0.78f to SelectionEnd)
private val HoverBackground = Color(0xFF333333)
private val IndicatorColor = Color(0xFFCBB6FF)
private val SelectedGlyph = Color(0xFFCBB6FF)
private val Glyph = Color(0xFFC2C2C2)
