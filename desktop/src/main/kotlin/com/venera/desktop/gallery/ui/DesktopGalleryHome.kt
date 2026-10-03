package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.venera.compose.gallery.data.GalleryPorts

/**
 * 桌面图库首页的 **pane 根** —— 集成方需要认识的就这一颗：在 `VeneraDesktop.kt` 的 `NavigationView`
 * 内容槽里一行 `DesktopGalleryHome(ports)` 就接得上。
 *
 * 形状 = 两级导航的**第二级**（pane 224 的六项 + 选中项的内容区）：
 * 第一级那条 rail 48 由集成方的 `NavigationView` 画（域切换：图库 / 漫画 / 设置），
 * 本文件只出 rail 的**数**（[DesktopGalleryMetrics.railWidth]，列数推导要减它），不画它 ——
 * 「首页 100% 图库、rail 保留漫画域入口但不占内容位」是设计稿定的（`:636`），
 * 把漫画与设置画成两行未实现会占掉内容位，那是越界。
 *
 * ⚠️ **pane 不涂底色**。稿上 `.pane{background:var(--layer)}` 的 `#272727` 是深色档的数；
 * 把它涂上去，浅色主题下 fluent 自己取色的主题文字（有内容那两行）就变成"深底深字"，
 * 正是本仓记过的那类"浅色主题下等于隐形"的反向事故。两档的正式底色映射归设计稿回改（S5），
 * 本轮靠 224 的宽度与行的形状分栏。
 *
 * 行的内衬与间距同样取自稿：`.pane{padding:6px 4px}`（`:70`）与 `.nav{margin:1px 0}`（`:73`，相邻两行合计 2dp）。
 * 六项**恒在**、项数不跟数据变（理由写在 [desktopGalleryHomeRows] 的类注释里，那是锚定漂移的桌面版防线）。
 */
@Composable
fun DesktopGalleryHome(ports: GalleryPorts, modifier: Modifier = Modifier) {
    val rows = desktopGalleryHomeRows()
    var selectedKey by remember { mutableStateOf(DESKTOP_GALLERY_HOME_SECTION_ORDER.first()) }
    val selectedRow = rows.byKey(selectedKey)

    Row(modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(DesktopGalleryMetrics.paneWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            rows.forEach { row ->
                DesktopGalleryHomeNavRow(row, selected = row.key == selectedKey, onSelect = { selectedKey = it })
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            DesktopGalleryHomeBody(selectedRow, ports)
        }
    }
}
