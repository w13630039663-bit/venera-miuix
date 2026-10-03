package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.venera.compose.gallery.data.GalleryPorts

/**
 * 桌面应用的**根**。集成方需要认识的就这一颗：在 `FluentTheme` 的内容槽里一行
 * `VeneraDesktopApp(ports, comic)` 就接得上。
 *
 * ## 形状
 *
 * ```
 * Column {
 *   DesktopGalleryTopBar          ← 全宽一条，横跨下面三栏（稿 `.tb` 在 `.body` **之外**）
 *   Row {
 *     rail   48   ← 一级导航：七个域
 *     pane  224   ← 二级导航：当前域的页条
 *     主区         ← 当前域 / 当前页的内容
 *   }
 * }
 * ```
 *
 * ## 这一层为什么存在（它原先不存在）
 *
 * 上一版最后由 `DesktopGalleryHome` 自己兼这三件事：画标题栏、画 rail（selected 写死
 * `GALLERY`）、画图库那六项的 pane。**结果是整个桌面只有一个画面** —— 没有路由，
 * 没有第二个目的地，rail 上点另外两枚只会往主区底部吐一句缺席说明。
 *
 * 那形状最坏的一处不是"没做"，是**看起来做了**：用户看得见七个格子、看得见 hover 反馈，
 * 却在点了之后发现当前域从来不变。本仓对这种东西有一贯的判法 —— 那是"假开关"，
 * 比"没有这个按钮"更坏。
 *
 * 现在 **rail 七枚全都是真导航**：点了就切过去，目的地可能是一屏把理由念全的坦白页
 * （[DesktopDomain.absence]），但一定是一个能站在那里、看得懂为什么的地方。
 *
 * ## 每个域记住自己上次看的那一页
 *
 * [paneKeys] 是一张 `域 → key` 的表，而不是一颗共享的 `selectedKey`。
 * 共用一颗的代价是：在图库里翻到「历史」，去漫画域转一圈回来，pane 的高亮停在
 * 「每日热门」上而 body 显示的是 —— 取决于哪个 Write 先到。两个域各自记自己的，
 * 回程才能落在走之前那一页。
 */
@Composable
fun VeneraDesktopApp(
    ports: GalleryPorts,
    comic: DesktopComicEngineConfig,
    modifier: Modifier = Modifier,
) {
    var domain by remember { mutableStateOf(DesktopDomain.GALLERY) }
    val paneKeys = remember { mutableStateMapOf<DesktopDomain, String>() }

    val rows = desktopDomainRows(domain)
    val selectedKey = paneKeys[domain] ?: rows.first().key
    val selectedRow = rows.firstOrNull { it.key == selectedKey } ?: rows.first()

    Column(modifier.fillMaxSize().background(DesktopTheme.WindowBackground)) {
        // ⓪ 标题栏：**全宽一条，横跨 rail / pane / 主区**。
        //    稿把 `.tb` 放在 `.body` **之外**（`:340` 的 `.tb` 与 `:352` 的 `.body` 是兄弟节点），
        //    所以它不该缩进任何一栏里 —— 这是它唯一说得通的位置。
        DesktopGalleryTopBar(
            onSearch = {
                // 搜索位是同一个判法的正反两面：上一版它弹一条提示就完事
                // （`DESKTOP_GALLERY_SEARCH_NOTICE`，本批删掉），于是那枚**看起来能输入**的框
                // 其实比 rail 上的缺席更糟 —— 它连"我没地方可去"都没说清，只说"还没做"。
                // 现在它真的把你带到搜索域，那里的坦白页把"三站 searchPosts 已经通了、
                // 缺的是哪一层"逐颗点名。导航到坦白页 ≠ 假装那件事存在。
                domain = DesktopDomain.SEARCH
            },
        )

        Row(Modifier.weight(1f).fillMaxWidth()) {
            // ① Rail：一级导航（设计稿 `.rail`，`:59`）。宽 48，**七个域**。
            //    底色 [DesktopTheme.RailBackground]（`#1C1C1C`），**比 pane 更暗一档**：
            //    两级导航的层级差必须由明度差说清 —— 只靠一条 1px 分隔线在深色下看不见。
            Column(
                Modifier
                    .width(DesktopGalleryMetrics.railWidth)
                    .fillMaxHeight()
                    .background(DesktopTheme.RailBackground),
            ) {
                DesktopGalleryRail(selected = domain, onSelect = { domain = it })
            }

            // ② Pane：当前域的页条。宽恒定 224 —— **切域时这一栏不许宽窄跳变**，
            //    变了就等于承认每个域是自己的一套布局（本仓判过负的"第二套布局系统"）。
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
                    DesktopGalleryHomeNavRow(
                        row,
                        selected = row.key == selectedKey,
                        onSelect = { paneKeys[domain] = it },
                    )
                }
            }

            // ③ 主区：当前域当前页的内容。
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(DesktopTheme.WindowBackground),
            ) {
                DesktopDomainBody(domain, selectedRow, ports, comic)
            }
        }
    }
}

/**
 * 当前域的那份**页条**。
 *
 * 三档返回一个形状：[DesktopGalleryHomeRow] —— 这是刻意的。缺席域也因此有一行（内容位是
 * `null` ⇒ pane 里落成一颗**不可点**的 [DesktopUnimplementedRow]，与图库域那四行同一枚件），
 * 而不是让下面的 Column 空着。空 pane 会让"这个域到底有没有东西"变成一次猜测。
 *
 * ⚠️ 这颗是**纯数据**（不 require @Composable）：没有 `@Composable` 注解恰恰是为了让
 * `DesktopDomainNavigationTest` 能直接把七个域各跑一遍，而不是对着源码文本做正则。
 * 能真跑的判据永远比能扫的判据可靠 —— 后者改写一行注释就能绕过。
 */
internal fun desktopDomainRows(domain: DesktopDomain): List<DesktopGalleryHomeRow> = when (domain) {
    DesktopDomain.GALLERY -> desktopGalleryHomeRows()
    DesktopDomain.COMIC -> desktopComicRows()
    else -> {
        val absence = requireNotNull(domain.absence) { "「${domain.title}」既有内容又说缺席" }
        listOf(
            DesktopGalleryHomeRow(
                key = "${domain.name}-absent",
                title = domain.title,
                glyph = domain.glyph,
                content = null,
                reason = absence.reason,
            ),
        )
    }
}

/**
 * 主区的内容按域分发。
 *
 * ⚠️ 缺席域**不走** [DesktopGalleryHomeBody] 那条路：[DesktopAbsentDomainBody] 比那条多念一张
 * [DesktopAbsence.wired]，也就是"已经接好了但因为没界面而看不见"的那几颗件。
 * 少了那张清单，"这一域没做"和"这台机器上没有这个域"就会念成同一句话 ——
 * 那正是 [DesktopDomain] 类注释里写的、不许发生的混淆。
 */
@Composable
private fun DesktopDomainBody(
    domain: DesktopDomain,
    row: DesktopGalleryHomeRow,
    ports: GalleryPorts,
    comic: DesktopComicEngineConfig,
) {
    val rendered: Unit = when (domain) {
        DesktopDomain.GALLERY -> DesktopGalleryHomeBody(row, ports)
        DesktopDomain.COMIC -> DesktopComicBody(row, comic)
        else -> DesktopAbsentDomainBody(domain)
    }
}
