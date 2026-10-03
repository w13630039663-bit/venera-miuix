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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.sp
import io.github.composefluent.component.Text

/**
 * 桌面图库首页的 **pane 根** —— 集成方需要认识的就这一颗：在 `VeneraDesktop.kt` 的
 * `FluentTheme` 内容槽里一行 `DesktopGalleryHome(ports)` 就接得上。
 *
 * 形状 = 两级导航的**第二级**（pane 224 的六项 + 选中项的内容区）：
 * 第一级那条 rail 48 由本文件自己画（域切换：图库 / 漫画 / 设置），**集成方不再有 `NavigationView`**。
 * 「首页 100% 图库、rail 保留漫画域入口但不占内容位」是设计稿定的（`:664`），
 * 把漫画与设置画成两行未实现会占掉内容位，那是越界。
 *
 * ⚠️ 2026-10-04：集成方原先在外面又套了一层 compose-fluent 的 `NavigationView`，
 * 而它自带一个 180dp 的侧栏（实测 `SideNavKt` 的宽度常量 `180.0f`）⇒ 窗口里挤了三层竖栏。
 * 判负记录见 `docs/rounds/large-screen-adaptation-stage2-plan-2026-10-02.md:20`
 * 「两边都建侧栏就是两套 —— 即『第二套布局系统』」，由 `DesktopShellLayoutTest` ① 钉住不再接回来。
 *
 * ## 两级导航的底色：**rail 比 pane 更暗一档**（2026-10-04 照稿执行）
 *
 * 稿 `.rail{background:#1C1C1C}`（`:59`）确实比 `.pane{background:var(--layer)}` = `#272727`（`:70`）更暗。
 * 本文件原先让两者共用 `SidebarBackground`，却在注释里声称"rail 刻意更深一档" ——
 * 注释与代码互相矛盾，结果深色下两级糊成一片。这枚明度差就是层级差本身，删不得。
 * 现在两处各引一颗 token（[DesktopTheme.RailBackground] / [DesktopTheme.SidebarBackground]），
 * 由 `DesktopCardOverlayTest` 的「rail 与 pane 必须不同档」断言机械看管。
 *
 * 行的内衬与间距同样取自稿：`.pane{padding:6px 4px}`（`:70`）与 `.nav{…}`（`:79`，相邻两行合计 2dp）。
 * 六项**恒在**、项数不跟数据变（理由写在 [desktopGalleryHomeRows] 的类注释里，那是锚定漂移的桌面版防线）。
 */
@Composable
fun DesktopGalleryHome(ports: GalleryPorts, modifier: Modifier = Modifier) {
    val rows = desktopGalleryHomeRows()
    var selectedKey by remember { mutableStateOf(DESKTOP_GALLERY_HOME_SECTION_ORDER.first()) }
    var railNotice by remember { mutableStateOf<String?>(null) }
    val selectedRow = rows.byKey(selectedKey)

    Column(modifier.fillMaxSize().background(DesktopTheme.WindowBackground)) {
        // ⓪ 标题栏：**全宽一条，横跨 rail / pane / 主区**。
        //    稿把 `.tb` 放在 `.body` **之外**（`:340` 的 `.tb` 与 `:352` 的 `.body` 是兄弟节点），
        //    所以它不该缩进任何一栏里 —— 这是它唯一说得通的位置。
        DesktopGalleryTopBar(
            onSearch = {
                // 与 rail 上「漫画 / 设置」两枚同一办法：把"这一档没接"说出来，
                // 而不是摆一枚按下去什么都不会发生的假输入框。
                railNotice = DESKTOP_GALLERY_SEARCH_NOTICE
            },
        )

        // 标题栏之外的高度全给下面三栏。
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // ① Rail：域切换（设计稿 `.rail`，`:59`）。**桌面自持的一颗 48dp 那一层** ——
            //    Android 端没有这一层（那边是底栏 + 侧栏），所以这 48 没有对岸可漂。
            //    底色取 [DesktopTheme.RailBackground]（`#1C1C1C`），**比 pane 更暗一档**：
            //    两级导航的层级差必须由明度差说清 —— 只靠一条 1px 分隔线在深色下看不见。
            //    2026-10-04 之前这里与 pane 同色（`SidebarBackground`），层级差只存在于注释里。
            Column(
                Modifier
                    .width(DesktopGalleryMetrics.railWidth)
                    .fillMaxHeight()
                    .background(DesktopTheme.RailBackground),
            ) {
                DesktopGalleryRail(
                    selected = DesktopGalleryDomain.GALLERY,
                    onSelect = { domain ->
                        // 漫画域今天在桌面没有入口页（S0 那条最小闭环链是探针，不算界面）。
                        // 所以这一档**不装一个能点但什么都不发生的按钮** —— 本仓最忌的假开关。
                        // 说清现状，胜过给一枚点了没反应的图标。
                        if (domain != DesktopGalleryDomain.GALLERY) {
                            railNotice = domain.notWiredReason
                        }
                    },
                )
            }

            // ② Pane：域内页条。底色 [DesktopTheme.SidebarBackground]（稿 `--layer` #272727，`:70`）。
            //    与 rail 的分界靠**明度差**说话（rail 是 `#1C1C1C`，比这一档更暗）。
            //    ⚠️ `verticalScroll` 是必需的、不是装饰：四行「未实现」各带一句 caption，
            //    六行实测 6 × 74dp + 两条实排 40dp ≈ 524dp，在较矮的窗上放不下。
            //    少了它，最后一行会被静默裁掉 —— 而"某一行看不见"正是本仓最忌的那种静默交错。
            //    （默认窗 2026-10-04 起是 1440×900，但窗口可缩放，这颗滚动件不能撤。）
            Column(
                Modifier
                    .width(DesktopGalleryMetrics.paneWidth)
                    .fillMaxHeight()
                    .background(DesktopTheme.SidebarBackground)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                rows.forEach { row ->
                    DesktopGalleryHomeNavRow(row, selected = row.key == selectedKey, onSelect = { selectedKey = it })
                }
            }

            // ③ 主区：窗底 + 一条竖分隔线，让三级（rail / pane / 内容）在明度上分得开。
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(DesktopTheme.WindowBackground),
            ) {
                DesktopGalleryHomeBody(selectedRow, ports)
                // 缺席说法贴在主区**底部**：它是"这一档没接"，不是"这一档坏了"，不该挡住内容。
                //
                // ⚠️ 它原先是 `Row {...}` 的兄弟节点、且没写 alignment，落在任何 Box 容器里都会被摆到
                // top-start ⇒ 盖住 rail 顶部。原先外面套着 `NavigationView` 的内容槽，不易被看见；
                // 2026-10-04 拆掉那层之后就会显形，所以随那一次改动一起挪进来（计划第 1 批）。
                railNotice?.let { reason ->
                    Text(
                        reason,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        color = DesktopTheme.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * 点标题栏搜索位时给出的说法 —— 与 rail 上那两枚同一个办法（"没接"要有地方落脚）。
 *
 * ⚠️ 不许把它删成空串：搜索位在稿上是**能输入**的，本仓把它降级成"点了给说法"，
 * 靠的就是这句话。没有它，那枚可点的框就退化成假开关。
 */
private const val DESKTOP_GALLERY_SEARCH_NOTICE =
    "桌面还没有搜索页：这条链在 Android 侧是 GallerySearchViewModel + 结果墙，桌面只接了取数与浏览。"
