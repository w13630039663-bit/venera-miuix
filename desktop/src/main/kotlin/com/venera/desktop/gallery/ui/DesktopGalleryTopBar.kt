package com.venera.desktop.gallery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.component.Text

/**
 * 标题栏（稿 `.tb`：CSS `:40`，HTML `:340-351`）—— 品牌 + 搜索位 + 右侧读数。
 *
 * ## 照稿的形状，按本仓纪律裁掉四样
 *
 * 稿 `.tb` 里一共六样东西，本批落三样。裁掉的四样各有各的理由，**没有一样是"忘了"**：
 *
 * 1. `.cap` ×3（最小化 / 最大化 / 关闭，稿 `:347-349`）—— 那是**设计稿在浏览器里模拟
 *    Windows 窗口按钮**（稿画的那个"窗口"是网页里的一只框，它没有系统 chrome）。
 *    真实窗口已经有系统标题栏上那三枚，照画就是**两排窗口按钮**，而且自绘那排按不动。
 * 2. `.kbd`「Ctrl K」（稿 `:342`、`:46`）—— 桌面**没有**全局搜索快捷键这条链。
 *    画一枚快捷键徽标，等于宣称存在一个按下去什么都不会发生的键。
 * 3. `.dot`（绿点 + "网络正常"，稿 `:344`、`:51`）—— 桌面今天**没有网络状态源**：
 *    失败信息是跟着单次取数来的，不是常驻探活。画一枚恒绿的灯就是一枚会撒谎的灯。
 * 4. `.ava`（28×28 渐变圆，`title="本机数据目录"`，稿 `:346`、`:52`）—— 它是个**入口**，
 *    而桌面还没有"本机数据目录"那一页。按 rail 上漫画/设置的同一条办法，入口可以
 *    "点进去给一句说法"，但那句话今天没有立足处 —— 所以这一枚先不画，
 *    而不是画成一个点了没反应的圆。
 *
 * ## 搜索位为什么**能点**
 *
 * 稿上 `.search` 是个输入框。桌面**没有**搜索页（`GallerySearchViewModel` 那条链只在
 * Android 侧）—— 但也不能把它画成灰的：本仓"未实现"那一档的形状是"带缺席原因的灰行"，
 * 那是**列表里的一行**，塞进标题栏会更像坏掉。所以照稿画成可点的样子，
 * 点下去把原因说出来 —— 与 rail 上"漫画 / 设置"两枚同一个办法
 * （[DesktopGalleryDomain.notWiredReason]）。
 */
@Composable
internal fun DesktopGalleryTopBar(
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(DesktopGalleryMetrics.topBarHeight)
            .background(DesktopTheme.SidebarBackground)
            // 稿只给了左内衬（`padding:0 0 0 12px`）—— 它右侧紧跟着的是自绘的窗口按钮。
            // 本仓右边是系统标题栏，所以右侧也留同样的 12，免得读数胶囊贴着窗边。
            .padding(
                start = DesktopGalleryMetrics.topBarLeadingPadding,
                end = DesktopGalleryMetrics.topBarLeadingPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.topBarGap),
    ) {
        ToriiBrand()
        SearchField(onClick = onSearch)
        Spacer(Modifier.weight(1f))
        StatusLamp("三站 · 本地无任务队列")
    }
}

/** 品牌位。稿 `.brand`（`:41-42`）：20dp 的鸟居 glyph + 一行 12.5px 的 `--t2` 文字。 */
@Composable
private fun ToriiBrand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToriiMark(Modifier.size(18.dp))
        Text(
            "Venera",
            color = DesktopTheme.TextSecondary,
            fontSize = 12.5.sp,
            maxLines = 1,
        )
    }
}

/**
 * 鸟居标记 —— 稿 `#t-torii` 那枚 SVG symbol 的极简版：笠木、贯、两根柱子。
 *
 * 为什么自己画四笔、而不是塞一个 `⛩` 字符：那个字符在 Windows 上走彩色 emoji 字体，
 * 会渲染成一块**彩色**图形，落在这套冷调深色阶梯里就是个花斑。
 * 而稿上那枚 symbol 本来就是单色线稿（靠 `color:var(--beni-lite)` 上色）。
 *
 * ⚠️ 这里读的 [DesktopTheme.AccentBeni] 是普通 `val`，不是 @Composable getter ——
 * 所以能在绘制期（`Canvas` 的 lambda 里）直接读。
 */
@Composable
private fun ToriiMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bar = h * 0.13f
        val color = DesktopTheme.AccentBeni
        // 笠木（顶梁）：两端外挑，所以铺满整宽
        drawRect(color, Offset(0f, h * 0.08f), Size(w, bar))
        // 贯（第二道横梁）：比顶梁短一截
        drawRect(color, Offset(w * 0.15f, h * 0.36f), Size(w * 0.70f, bar))
        // 两根柱子：自贯而下
        drawRect(color, Offset(w * 0.24f, h * 0.36f), Size(bar * 1.2f, h * 0.56f))
        drawRect(color, Offset(w * 0.70f, h * 0.36f), Size(bar * 1.2f, h * 0.56f))
    }
}

/**
 * 搜索位。形状照稿 `.search`（`:43-44`）：固定 400×32、圆角 4、13px 的 `--t3` 文案。
 *
 * ⚠️ 底色与描边**没有**照抄稿的 `#2D2D2D` 与 `--stroke2:#3D3D3D`：那两个值比五档阶梯里的
 * `--card` `#2C2C2C` / `--stroke` `#353535` 各差 1~8 个灰阶，而"随手加一颗差不多的中间灰"
 * 恰恰是这套阶梯明令不许的事（理由写在 [DesktopTheme] 的类注释里）。
 * 这里按**最近的档**收：底 = [DesktopTheme.CardBackground]、边 = [DesktopTheme.SurfaceRaised]。
 * 观感上差不到一个色阶，阶梯却少了两颗孤值。
 *
 * 图标用汉字「搜」而不是稿上的 `#i-search` SVG —— 与 rail / pane 的降级同一套语言
 * （桌面这轮没有图标集，`DesktopGalleryMetrics.navGlyphSlotWidth` 那条注释里写着）。
 */
@Composable
private fun SearchField(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .padding(start = DesktopGalleryMetrics.searchFieldLeadingMargin)
            .width(DesktopGalleryMetrics.searchFieldWidth)
            .height(DesktopGalleryMetrics.searchFieldHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(if (hovered) DesktopTheme.SurfaceRaised else DesktopTheme.CardBackground)
            .border(1.dp, DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("搜", color = DesktopTheme.TextTertiary, fontSize = 13.sp, maxLines = 1)
        Text(
            "搜索作品、画师、标签",
            color = DesktopTheme.TextTertiary,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

/**
 * 右侧读数胶囊。形状照稿 `.lamp`（`:48`）：高 32、圆角 4、13px。
 *
 * 稿上有两枚（"网络正常" + "三站 · 本地无任务队列"），本批只落后者 ——
 * 前者要一枚常驻的网络状态源，桌面今天没有（类注释第 3 条）。
 * 而"本地无任务队列"这句是**真的**：桌面取数是"拉一次画一次"，没有队列这个东西。
 */
@Composable
private fun StatusLamp(text: String) {
    Box(
        Modifier
            .height(DesktopGalleryMetrics.statusLampHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(DesktopTheme.CardBackground)
            .border(1.dp, DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
            .padding(horizontal = DesktopGalleryMetrics.statusLampHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = DesktopTheme.TextSecondary, fontSize = 13.sp, maxLines = 1)
    }
}
