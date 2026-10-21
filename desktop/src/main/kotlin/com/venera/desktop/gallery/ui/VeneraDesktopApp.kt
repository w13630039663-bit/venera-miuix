package com.venera.desktop.gallery.ui

import androidx.compose.runtime.collectAsState
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.DesktopDecoTier
import com.venera.desktop.gallery.data.DesktopDecoTierPreferences
import com.venera.desktop.gallery.data.desktopDecoTierForScreen
import com.venera.desktop.gallery.data.desktopDecoTierOf
import com.venera.desktop.gallery.data.desktopComicQueueLampText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import com.venera.compose.gallery.domain.GalleryColumnMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryAutoPlay
import com.venera.compose.gallery.domain.GalleryMotion
import com.venera.compose.gallery.domain.GallerySearchSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import net.engawapg.lib.zoomable.rememberZoomState
import com.venera.desktop.gallery.data.DesktopGalleryBrowseHistory
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.gallery.data.DesktopGalleryContentGuard
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import java.io.File

private const val VIEWER_MAX_SCALE = 5f

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
 *
 * ## 起始域是**参数**，不是这一层的决定
 *
 * [startDomain] 没有默认值，而且是必填：带默认值的新参数漏传不报错，而"漏传"落下来的形状
 * 恰好是取证时最坏的那种 —— 窗口照样开、截图照样写、域却是默认那一枚，于是那张图顶着
 * 目标域的文件名。做成必填之后漏传是**编译错误**，不需要再加一条扫文本的判据。
 *
 * ⚠️ 这颗是 `internal`（原来是 public）：起始域的类型 [DesktopDomain] 是本模块内部的，
 * public 签名会把它暴露出去（连带 `DesktopAbsence` 那一族都要跟着变 public）。
 * "集成方需要认识的"那一项并没有变 —— 集成方就在同一个模块里（`VeneraDesktop.kt` 的 `main()`）。
 */
internal fun desktopDomainRows(domain: DesktopDomain): List<DesktopGalleryHomeRow> = when (domain) {
    DesktopDomain.GALLERY -> desktopGalleryHomeRows()
    DesktopDomain.COMIC -> desktopComicRows()
    // 2026-10-04 第二批：这两域从坦白页转成真页面，各有一行。
    DesktopDomain.FAVORITES -> desktopFavoritesRows()
    DesktopDomain.SEARCH -> desktopSearchRows()
    // 第七批：设置域转正。它今天只有一行（墙上那四枚档），不是"一堆设置分类"——
    // 多出来的每一行都得有真写侧，而那四枚之外今天没有第五枚能改。
    DesktopDomain.SETTINGS -> desktopSettingsRows()
    // 第八批：画师域转正。第一层是名单，第二层是批 19 的单人页（「看他的图」那枚入口进去），
    // 而域级的行清单仍然只有一行 —— 单人页不是一行"页"，它是同一行里的第二层目的地。
    DesktopDomain.ARTISTS -> desktopArtistsRows()
    // 第九批：发现域转正 —— 七枚域到此**全部**是真页面，`else` 那一条坦白页路今天走不到。
    // 留着它的理由与判据一起写在 `DesktopDomainNavigationTest` 那条"0 枚缺席域"的断言旁边。
    DesktopDomain.DISCOVER -> desktopDiscoverRows()
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
 *
 * ## 现在有**七**条真域路（第九批起一条坦白页路都不剩）
 *
 * ⚠️ `else` 那两条留着不是死代码，是**回滚的落点**：谁把某一域的 `absence` 挂回去，
 * 那一屏立刻又能念全理由，而不是编译不过 —— 而"今天七枚全真"这件事由
 * `DesktopDomainNavigationTest` 里那两枚计数断言钉着（改一域就红一次）。
 *
 * `when` 挂在那颗 `val rendered: Unit =` 上（表达式位置 ⇒ Kotlin 做穷尽检查），
 * 所以新增一枚域忘了在这里出形状会**编译不过**。而每一条真域路内部那层 `when` 也各自挂着
 * 同一根钩子 —— 两层都是"忘了登记就红"，这是本仓唯一一种能自动顶出漏登记的形状。
 */
