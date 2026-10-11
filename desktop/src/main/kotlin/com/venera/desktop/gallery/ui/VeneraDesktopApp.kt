package com.venera.desktop.gallery.ui

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

/**
 * 大图那一层的放大上限（第五十批）。取证种子 `-PviewerZoom=` 越界时夹到这个值，
 * 且夹取发生在我们自己这侧（[desktopViewerZoomScale]）—— 库那枚 `changeScale` 也夹，
 * 但它夹得不出声（见 `docs/rounds/desktop-image-viewer-design-2026-10-08.md` §2.1）。
 */
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
@Composable
internal fun VeneraDesktopApp(
    ports: GalleryPorts,
    startDomain: DesktopDomain,
    /**
     * 搜索那一屏的查询初值（取证用）。`null` = 不预填。
     *
     * 必填、不给默认值：漏传落下来的形状是"搜索那一屏永远拍不到内容"，而那**不会报错** ——
     * 正是这一族判据最忌的"看起来像开关但其实不是"。
     */
    startQuery: String?,
    /**
     * pane 里**先选中哪一行**（取证用）。`null` = 不预选，落在这一域的第一行。
     *
     * 必填、不给默认值：`--start-domain=` 只到域，到不了域内那一行，而"漏传"的形状是
     * 截图照样出、屏上却是日榜 —— 那张图会顶着目标那一行的文件名（判据 ⑰ 钉着这条链）。
     */
    startSection: String?,
    /**
     * 起窗后**自己把详情浮层摆出来**（取证用，`--detail`）。`false` = 不摆（默认行为）。
     *
     * 必填、不给默认值：漏传的形状是"参数打进去了但屏上没反应"，而它**不会报错** ——
     * 与 `startDomain` / `startQuery` / `startSection` 同一条理由。
     * 为什么需要这一枚：那一层要**点一张卡**才出来，而 agent 侧不许动鼠标，
     * 于是 P0/P1 两批的观感都拍不到图（手册 §27.5 那条"没有像素证据"就是它）。
     */
    startDetail: Boolean,
    /**
     * 起窗后**自动按一次「找同一张图」**（取证用，`--reverse`）。`false` = 不按（默认行为）。
     *
     * 必填、不给默认值：漏传的形状是"参数打进去了而那一格永远不出现"，它**不会报错** ——
     * 与上面那几枚种子同一条理由。为什么需要这一枚：结果那一格在浮层里、要按一下才出来，
     * 而 agent 侧不许动鼠标（手册 §8）⇒ 不带它时批 35 的图证只能拍到那枚按钮本身。
     *
     * ⚠️ 它**不自己开浮层**：那一枚按钮在浮层里，"顺带把浮层摆出来"这一步住在集成方
     * （`ForensicFlags.detailPanel`），与 `startArtist` / `startRanking` 那两枚同一族规矩。
     */
    startReverse: Boolean,
    /**
     * 起窗后**替用户挑好那一张本机的图**（取证用，`--reverse-file=`）。`null` = 不挑（默认行为）。
     *
     * 必填、不给默认值：漏传的形状是"参数打进去了而那一格永远不出现"，它**不会报错** ——
     * 与上面那几枚种子同一条理由。为什么需要这一枚：那一格的入口是**系统文件框**，
     * 而 agent 侧不许动鼠标（手册 §8）⇒ 不带它时路径之后的三跳结构性地拍不到。
     *
     * ⚠️ 它与 `--reverse` 有一处实差，两处文档都不许互相冒充：那一枚是"自动按一次屏上已有的按钮"，
     * 这一枚是"**跳过**文件框那一步"。文件框本身今天仍然没有读数（只有代码在场这一条）。
     */
    startReverseFile: String?,
    /**
     * 起窗后**自动按一次「复制图片」**（取证用，`--copy`）。`false` = 不按（默认行为）。
     *
     * 必填、不给默认值：漏传的形状是"参数打进去了而剪贴板里始终空空"，它**不会报错** ——
     * 与上面那几枚种子同一条理由。为什么需要这一枚：那一格只有按下去才量得出剪贴板里
     * 到底进去了一张什么，而 agent 侧不许动鼠标（手册 §8）⇒ 不带它时批 54 只能拍到按钮本身。
     *
     * ⚠️ 与 `--reverse` 同一处边界：那一枚按钮也在浮层里，"顺带把浮层摆出来"这一步住在集成方
     * （`ForensicFlags.detailPanel`），不在这里替用户开窗。
     */
    startCopy: Boolean,
    /**
     * 起窗后**自动按一次「存到本机」**（取证用，`--save`）。`false` = 不按（默认行为）。
     *
     * 必填、不给默认值：漏传的形状是"参数打进去了而盘上始终不多那颗文件"，它**不会报错** ——
     * 与上面那几枚种子同一条理由。为什么需要这一枚：这一格唯一的证据是**落盘之后回读到的字节数**，
     * 不起窗按一次就量不到（手册 §8.1 的②档：取证种子 + 起窗）。
     */
    startSave: Boolean,
    /**
     * 存图落点（批 85 的 `数据根/downloads/图库`）。必填、不给默认值。
     *
     * ⚠️ 这一枚**不是** `PathProvider`：界面件拿到 provider 就等于拿到一条能建目录的手，
     * 而首页「下载」那一屏的纪律是"去看一眼不许在盘上造出东西"。落点由集成方算好，这里只递一颗 `File`。
     */
    gallerySaveDir: File,
    /**
     * 图库浏览历史那颗件（第八十六批），两个消费点共用：详情浮层**写**它，首页「历史」那一屏**读**它。
     *
     * 为什么由集成方建好再递进来，而不是这两处各自 `getInstance`：同一颗件的两份内存，
     * 表现正是"刚点开的那张在屏上看不见"（判据 ⑰），而界面件里去摸静态口是
     * `DesktopUiIsolationTest` ③ 管住的那一条。
     */
    galleryHistory: DesktopGalleryBrowseHistory,
    /**
     * 起窗后**直接进那一位画师的单人页**（取证用，`--artist=`）。`null` = 不进（默认行为）。
     *
     * 必填、不给默认值：漏传的形状是"参数打进去了但屏上仍是画师列表"，而它**不会报错** ——
     * 与 `startDomain` / `startQuery` / `startSection` / `startDetail` 同一条理由。
     * 为什么需要这一枚：那一屏是"点一下才出来的那一层"（要点列表上的「看他的图」），
     * 而 agent 侧不许动鼠标 ⇒ 不带它时批 19 的图证只能记成"没拍成"。
     *
     * ⚠️ 域**不在这一层跟着改**：起始域是参数而不是这一层的决定（见上面的类注释），
     * 所以"种子认出来时落到「画师」那一域"这一步住在集成方（`VeneraDesktop.kt`）。
     * 传了目的地而 `startDomain` 仍是别处，那一屏就不在组合里 —— 那正是这颗参数**最该被念出来**的形状，
     * 由 `ForensicFlags.artistNote` 负责。
     */
    startArtist: DesktopArtistTarget?,
    /**
     * 起窗后**直接开到哪一本漫画的详情那一屏**（取证用，`--comic=`）。`null` = 不开。
     *
     * 必填、不给默认值：漏传的形状是"参数打进去了而那一屏永远停在列表"，它**不会报错** ——
     * 与 `startDomain` / `startArtist` 同一条理由。为什么需要它：详情那一屏的入口是封面，
     * 而 agent 侧禁鼠标（手册 §8）⇒ 不带它时这一屏的图证只能记成"没拍成"。
     */
    startComic: DesktopComicTarget?,
    /**
     * 起窗后**直接开到哪一话的阅读那一屏**（取证用，`--read=`）。`null` = 不开。
     *
     * 必填、不给默认值：漏传的形状是"参数打进去了而那一屏永远停在详情"，它**不会报错** ——
     * 与 [startComic] 同一条理由。为什么需要它：阅读那一屏的入口是章节表里的一行，
     * 而 agent 侧不许动鼠标（手册 §8）⇒ 不带它时这一屏的图证只能记成"没拍成"。
     */
    startRead: DesktopComicReading?,
    /**
     * 起窗后把阅读那一屏**顶到哪一档模式**（第八十七批，`--read-mode=`）。`null` = 不顶，用盘上那档。
     *
     * 必填、不给默认值：漏传的形状是「参数打进去了而那一屏永远停在纵向」，它不报错 ——
     * 与 [startRead] 同一条理由。种子只作**本次**初值、不写盘（批 83.1 那条口径）：
     * 无人值守那一趟不该把用户选的档位挪走，而那一句实话要上屏（`desktopReaderModeSeedNote`）。
     */
    startReaderMode: DesktopReaderMode?,
    /**
     * 起窗后**把排行榜拨到那一档、那一期**（取证用，`--ranking=`）。`null` = 不拨（默认行为）。
     *
     * 必填、不给默认值：漏传的形状是"参数打进去了而那一屏永远停在默认档"，它**不会报错** ——
     * 与 `startDomain` / `startQuery` / `startSection` / `startDetail` / `startArtist` 同一条理由。
     * 为什么需要这一枚：「上一期 / 下一期 / 回到本期」那一排是把档拨到月/年之后才出现的，
     * 而 agent 侧不许动鼠标（手册 §8）⇒ 不带它时批 29 的图证只能记成"没拍成"。
     *
     * ⚠️ 它**不改这一层的落点判据**：档在这一组腿里兑现不了就照原样落回默认档（那一句屏上会念），
     * "起始域与起始行跟着种子走"那一步住在集成方（`VeneraDesktop.kt`），与 `startArtist` 同一族。
     */
    startRanking: DesktopRankingSeed?,
    /**
     * 点卡之后**进不进大图那一层**（第五十批）。
     *
     * ⚠️ 三态而不是开不开：`null` = **没给参数**，而"没给"在这一屏是**进**（用户 2026-10-08 原话
     * 「点卡就跳到大图，现在都没这个功能」）。`-Pviewer=0` 只服务取证时"只想拍浮层那一屏"。
     * 必填、不给默认值 —— 漏传的形状是"点卡只出浮层、那一屏永远拍不到"，而它不会报错。
     */
    startViewer: Boolean?,
    /**
     * 起窗后**放大到几倍**（取证用，`--viewer-zoom=`）。`null` = 本轮不放大。
     *
     * 必填三态：给的不是个数时那一颗自己念一句并给 `null`（不猜一个倍数）。
     */
    startViewerZoom: Float?,
    /**
     * 设置页的写侧。类型是**桌面那颗具体件**而不是共享接口 ——
     * `GalleryPreferences` 只有读面（Android 侧的 setter 也在具体类上），
     * 而"UI 只吃形参"这条不许破，所以由集成方取一次再传下来（见 `DesktopGalleryPorts.preferences`）。
     */
    settings: DesktopGalleryPreferences,
    /**
     * 分级档与屏蔽规则的写侧（第十二批 批 6）。类型同样是**桌面那颗具体件**：
     * 共享接口 `GalleryContentGuard` 只有读面加一枚 `addRule`，而"改档 / 删一条"两道在那一颗类上。
     * 必填、不给默认值 —— 漏传的形状是"那一屏只读"，而它不会报错。
     */
    guard: DesktopGalleryContentGuard,
    /**
     * 凭据那一颗的**具体件**（第三十四批 批 34）：设置那一屏要往本机写 SauceNao 的 key，
     * 而共享契约 `GalleryCredentials` 只有两枚读流。必填、不给默认值 ——
     * 漏传的形状是"那一行摆着输入框而按下去什么都不落"，正是这一族最忌的假开关。
     */
    credentials: DesktopGalleryCredentials,
    comic: DesktopComicEngineConfig,
    modifier: Modifier = Modifier,
) {
    var domain by remember { mutableStateOf(startDomain) }
    // 落点是**谁**改走的（批 48 ②）：每一处写 `domain` 的地方必须同时写这一枚戳，读数把戳念出来 ——
    // 否则"落点被改走"只剩一句"改了"，定不了案（2026-10-08 那三行落点读数就是在没有鼠标的情况下改的）。
    var domainStamp by remember { mutableStateOf("启动") }
    // 浮层那一句同理：它是覆盖层，开与关各有四颗主人（墙上点卡 / 浮层内步进 / 关掉 / 换域换页自动收）。
    var pickStamp by remember { mutableStateOf("启动") }
    val paneKeys = remember {
        mutableStateMapOf<DesktopDomain, String>().apply {
            // 取证参数落在这一域时，先把它要的那一行种进去；`null` = 不种（屏上就是这一域的第一行）。
            // ⚠️ 只在 `remember` 里种一次：写进组合期每次重组都种，用户点别行会被弹回来
            //（"导航条目会重建组合"那一族，手册 §11）。
            if (startSection != null) put(startDomain, startSection)
        }
    }

    val rows = desktopDomainRows(domain)
    val selectedKey = paneKeys[domain] ?: rows.first().key
    val selectedRow = rows.firstOrNull { it.key == selectedKey } ?: rows.first()

    // 详情浮层的**选中态**：整桌面只有这一颗（判据 `DesktopGalleryDetailTest` ⑤ 数着声明处）。
    // 它不放进 pane、也不放进任何一面墙 —— 那些件每换一行就重建一次组合（手册 §11 那一族），
    // 浮层会跟着一起消失，表现为"点了卡，闪一下就没了"。
    // P1 起它装的是**整批 + 序号**（翻页要有数据源），而不是单张卡。
    var selected by remember { mutableStateOf<DesktopWallSelection?>(null) }
    // 面板顶部那张图**起步**取哪一档，跟着用户的「预览清晰度」偏好（当场可换档）。
    val previewQuality by ports.prefs.galleryPreviewQuality.collectAsState()

    // 档位与缩放态**上提到这一层**（第五十批）：浮层与大图那一屏读同一颗。
    // 两条轨的形状在批 47 刚踩过 —— 预取算的地址与屏上取的不是同一枚档，
    // 就是"预取白费、点下去重取一趟"，而屏上看不出任何异常。
    var tier by remember { mutableStateOf(desktopDetailInitialTier(previewQuality)) }
    // 缩放态住在根上而不是大图那一层里：连播那颗判据要读 `scale`，而它住在集成方这一层。
    val zoomState = rememberZoomState(maxScale = VIEWER_MAX_SCALE)
    // 大图那一层开没开（点卡 = 两层一起开；Esc 一层层退）
    var viewerOpen by remember { mutableStateOf(false) }
    // 「点卡进不进大图」这一屏的**默认行为是进**（用户原话）。`-Pviewer=0` 只服务取证时
    // 「只想拍浮层那一屏」那一档，所以"没给参数"与"给了 0"在行为上相同，
    // 而在 `D_落点` 的 `种子=` 那一格里必须是两句话（形参留三态，导出值才二态）。
    val viewerOpensOnPick = startViewer != false

    // 换域 / 换页时**关掉浮层**：留着它，用户看到的就是"上一屏那张卡"压在这一屏上面，
    // 而那句页码读数也跟着说谎（那一批已经不在这屏上了）。
    LaunchedEffect(domain, selectedKey) {
        selected = null
        viewerOpen = false
        pickStamp = "换域或换页自动收"
    }

    // 漫画详情那一屏开在哪一本（第八十批）。它住在根上而不是漫画那一屏里：换域/换页要能收掉它，
    // 而"谁把它改走的"那一枚戳与 `pickStamp` 同一族理由（没戳时四五种解释互相打不掉）。
    var comicTarget by remember { mutableStateOf(startComic) }
    var comicStamp by remember { mutableStateOf("启动") }
    // 阅读那一屏开在哪一话（第八十一批）。它比详情更高一层，但**与详情各持一枚槽**而不是复用一枚：
    // 「← 返回详情」要能回到同一本的那一屏（那里站着章节表），复用同一枚的话返回只能回到列表。
    var readTarget by remember { mutableStateOf(startRead) }
    var readStamp by remember { mutableStateOf("启动") }
    // 换域 / 换页时收掉详情那一屏。**首帧不收**：`--comic=` 那颗种子的语义就是"首帧该开着"，
    // 收了以后这一屏只能靠屏上那一次点击才到得了（而本环境禁鼠标）。
    var comicLandingSlot by remember { mutableStateOf(domain to selectedKey) }
    LaunchedEffect(domain, selectedKey) {
        val slot = domain to selectedKey
        if (comicLandingSlot == slot) return@LaunchedEffect
        comicLandingSlot = slot
        comicTarget = null
        comicStamp = "换域或换页自动收"
        readTarget = null
        readStamp = "换域或换页自动收"
    }

    // 落点读数（第四十八批）：**从状态现取，不从取证参数回读** —— 从请求回读等于拿请求当结果，
    // 而"屏上究竟落在哪一屏"这件事从前只有像素一条路（批 47 那三帧就卡在这里：stdout 只念了
    // 「认成「设置」」那一句参数判读，落点没有读数）。钥匙必须是那三枚状态本身：
    // 写 `Unit` 的话换域就不再念，念出来的永远停在第一帧那一句，那是另一种假读数。
    LaunchedEffect(domain, domainStamp, selectedRow.title, selected != null, pickStamp, viewerOpen, startViewer, startViewerZoom, comicTarget != null, comicStamp, readTarget != null, readStamp) {
        // ⚠️ 这一句必须**一行**：两行拼接会把证据送到判式看不见的地方（批 48 那条）。
        // 种子那几枚从参数**现取**：它们是"请求"，而域/行/浮层/查看器那四枚是"结果"。
        // 两者混在一行里正是"拿请求当结果"那个忌，所以这里给的是**并列**而不是混算。
        val seeds = listOfNotNull(
            "detail".takeIf { startDetail },
            // 这两枚也是"请求"那一侧的东西，与 detail 同一族：批 36 落地时漏登记了 reverse，
            // 于是带 `--reverse` 起窗时这一行念的是"种子=无"而 stdout 上一句"参数已判读"，两句互相打脸。
            "reverse".takeIf { startReverse },
            // 这一枚念的是"本轮替用户挑好了那一张"（路径本身不进这一行：那是用户本机的一条路径，
            // 而这一行要回答的只有"种子有没有接管那一格"，读数与屏上都不必复述一整串目录）。
            "reverse-file".takeIf { startReverseFile != null },
            "copy".takeIf { startCopy },
            "save".takeIf { startSave },
            "viewer".takeIf { startViewer == true },
            "viewer关".takeIf { startViewer == false },
            "viewerZoom".takeIf { startViewerZoom != null },
            "comic".takeIf { startComic != null },
            "read".takeIf { startRead != null },
        ).joinToString("+").ifBlank { "无" }
        println("D_落点 域=${domain.title}(${domainStamp}) 行=${selectedRow.title} 浮层=${if (selected != null) "开" else "关"}(${pickStamp}) 查看器=${if (viewerOpen) "开" else "关"} 详情=${if (comicTarget != null) "开" else "关"}($comicStamp) 阅读=${if (readTarget != null) "开" else "关"}($readStamp) 种子=$seeds")
    }

    // 搜索那一屏的**那颗种子**：初值吃 `--query=`（取证那条链，第六批），而批 2 ⑤ 之后
    // 它还会被"点标签 / 点画师名"改写。两件事共用一颗状态而不是两颗 ——
    // 两颗的话"谁覆盖谁"没人说清（判据 ⑦ 钉的就是这一条），而屏上只能有一个输入框内容。
    // `token` 让同一枚标签被点第二次也能重新落地，理由写在 [desktopSearchSeedFor] 那里。
    var searchSeed by remember {
        mutableStateOf(DesktopSearchSeed(startQuery ?: "", GallerySearchSource.ALL, token = 0))
    }
    // 两枚**坏档读数**在根上各消费一次（批 3′）。为什么主人不能是那一屏：`consumeNotice()` 是
    // 读走即清（`GalleryArtistFollowsStore.kt:65`），挂在屏里就会随那一屏离开组合而消失 ——
    // 用户切走再切回来，屏上仍是同一份空名单，而那句"档坏了、原档另存成了什么"没了。
    // 应用根整场只组合一次（它是 `Window` 的内容），所以只有这里记得住；两屏各吃一颗必填参数。
    val followsNotice = remember { ports.follows.consumeNotice() }
    val favoritesNotice = remember { ports.favorites.consumeNotice() }
    // 换域 / 换页再回来时，那一屏的账由这颗 holder 存着（批 5）。
    // 为什么不是"把状态提到根上那张表"，也不是"七域同时留在组合里"：
    // `SaveableStateProvider(key)` 的官方语义就是"subtree 离开组合前把里面所有 `rememberSaveable` 存下，
    // 用同一个 key 再进来时还原"（`runtime-saveable` `SaveableStateHolder.kt:38-50`）——
    // 前者要自己造一张表并自己管清理，后者会让冷启动七域同时发车。
    // ⚠️ 单用 `rememberSaveable` 没有这个效果：`SaveableHolder.onForgotten` 只是 unregister，
    // 没有任何一步把当前值写回 registry（`RememberSaveable.kt:91-100`、`:307`）—— 两者是一套。
    // 键取「域 + 那一行」：换页与换域是同一件事，那一屏的账本来就按"哪一屏"存。
    val domainStateMemory = rememberSaveableStateHolder()
    // 交接动作**只有这一个主人**（应用根）。两条交付形状各有理由：
    // - 五面墙与画师行在 `CompositionLocalProvider` 里面 ⇒ 走局部槽（不改一长排签名）；
    // - 详情浮层是**覆盖层**，它挂在 provider 外面（下面那个 `Box` 的第二格），
    //   所以它走**必填参数**。⚠️ 这一条不是设计偏好：2026-10-05 用户手上一按就炸在
    //   `LocalDesktopSearchHandoff` 的默认值上（浮层读到的是那颗"没人接"的默认），
    //   读数只落在 stdout 而屏上不动 —— 覆盖层依赖 provider 就是这种形状。
    val goToSearch: (List<GallerySite>, String) -> Unit = { sites, tag ->
        searchSeed = desktopSearchSeedFor(sites, tag, searchSeed.token + 1)
        domain = DesktopDomain.SEARCH
        domainStamp = "标签交接到搜索"
    }

    // 画师单人页的目的地槽（批 19）。它是**同一域里的第二层**，所以既不占 `domain` 那枚枚举的一位，
    // 也不进 `paneKeys` 那本账（那本账记的是"这一域的哪一行"，不是"这一行的哪个目的地"）。
    // 用 `remember` 而不是 `rememberSaveable` 的理由**不是**"那颗不是 Serializable 会炸"——
    // 桌面这侧 `canBeSaved` 恒 true（§32 量过：skiko 那颗 registry 的 lambda 编出来是 `iconst_1; ireturn`），
    // 存任意对象都不需要 `Saver`；那句是从 Android 的形状抄过来的（Bundle 那侧才会拒），批 21 改回。
    // 真正的理由只有一条：**槽住在应用根**，而应用根整场只组合一次、也不在任何 `SaveableStateProvider` 里
    // ⇒ 那里没有 registry 可存，`rememberSaveable` 与 `remember` 等价，写前者只会让人以为有跨进程恢复。
    // 初值吃 `--artist=` 那颗种子（批 20）：写死 `null` 的话那枚参数就只是装饰，
    // 而"装饰"的形状是**不报错**——屏上永远是画师列表，那张图却顶着单人页的文件名。
    var artistTarget by remember { mutableStateOf(startArtist) }
    val openArtist: (DesktopArtistTarget) -> Unit = { target ->
        // 与标签那一路同一颗纪律：**换域与写槽是一步**，拆开写会先切域、槽还是旧的，
        // 那一帧用户看到的是"跳到单人页而画的是上一位"。
        artistTarget = target
        domain = DesktopDomain.ARTISTS
        domainStamp = "打开画师单人页"
    }

    Column(modifier.fillMaxSize().background(DesktopTheme.WindowBackground)) {
        // ⓪ 标题栏：**全宽一条，横跨 rail / pane / 主区**。
        //    稿把 `.tb` 放在 `.body` **之外**（`:340` 的 `.tb` 与 `:352` 的 `.body` 是兄弟节点），
        //    所以它不该缩进任何一栏里 —— 这是它唯一说得通的位置。
        DesktopGalleryTopBar(
            onSearch = {
                // 搜索位是同一个判法的正反两面：上一版它弹一条提示就完事
                // （`DESKTOP_GALLERY_SEARCH_NOTICE`，本批删掉），于是那枚**看起来能输入**的框
                // 其实比 rail 上的缺席更糟 —— 它连"我没地方可去"都没说清，只说"还没做"。
                // 现在它真的把你带到搜索域。**（第六批起那一域是真页面，不再是坦白页 ——
                // 这句从前写的是"那里的坦白页把缺的层逐颗点名"，那句随转正一起过期了。）**
                domain = DesktopDomain.SEARCH
                domainStamp = "顶栏搜索框"
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
                DesktopGalleryRail(
                    selected = domain,
                    onSelect = {
                        domain = it
                        domainStamp = "侧栏点「${it.title}」"
                    },
                )
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
            //    ⚙ 这一层从 `Column` 换成了 `Box`（第十一批 P0）：详情浮层要**叠**在主区上，
            //    而 `Column` 里两排只能前后排、不能压在一起。原来的那三档几何（`weight(1f)`、
            //    `fillMaxHeight()`、窗口底色）整块搬进里面那一枚 `Column`，一档都没改 ——
            //    权重仍然给主区（浮层不吃权重，它是覆盖层），底色仍然在这一格上。
            //    判据见 `DesktopShellLayoutTest` 与 `DesktopGalleryDetailTest` ⑥。
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                // 那一层底吃的是屏上第八行那枚档（批 61）；而 `GLASS` 那一档糊的是**下面这棵墙**（批 63），
                // 半径与压暗两枚数照对岸、判据住在 `desktopWallBlurRadius`。钥匙必须是那两枚状态本身。
                val backdropTier by settings.galleryBackdrop.collectAsState()
                val wallBlur = desktopWallBlurRadius(backdropTier, viewerOpen = viewerOpen)
                Column(
                    Modifier
                        .fillMaxSize()
                        // 零档就不挂那一层：`Modifier.blur(0.dp)` 在桌面上会不会仍留一枚 graphicsLayer
                        // 没量过 ⇒ 不糊的档走空的那一枚，不把"零成本"交给库的善意。
                        .then(if (wallBlur > 0.dp) Modifier.blur(radius = wallBlur) else Modifier)
                        .background(DesktopTheme.WindowBackground),
                ) {
                    CompositionLocalProvider(
                        LocalDesktopWallPick provides { selection ->
                            selected = selection
                            pickStamp = "墙上点卡"
                            // 进那一屏时升档（设计 §4.1 A 案）。吃的是那张表而不是直接写 SAMPLE：
                            // 被挡那张表是空的、视频与原图那两条撤档规则都住在表里（判据 ⑥）。
                            val picked = selection.card
                            if (picked != null) {
                                desktopViewerEntryTier(desktopDetailTiers(picked.post, picked.masked))
                                    ?.let { entry -> tier = entry }
                            }
                            viewerOpen = viewerOpensOnPick
                        },
                        // 取证那颗种子也从这里 give —— 五面墙不用改签名就能知道自己该不该自开浮层。
                        LocalDesktopWallSeedFirst provides startDetail,
                        // 点标签 / 点画师名 → 切进搜索域并把那一枚装进输入框（批 2 ⑤）。
                        // **换域与写种子是一步**：拆开写会先切域、种子还是旧的，
                        // 那一帧用户看到的是"跳到搜索屏而搜的是上一轮"（Android 那边同一句警告）。
                        LocalDesktopSearchHandoff provides goToSearch,
                    ) {
                        domainStateMemory.SaveableStateProvider("$domain-$selectedKey") {
                            DesktopDomainBody(
                                domain,
                                selectedRow,
                                ports,
                                comic,
                                searchSeed = searchSeed,
                                seedLocalReverse = startReverseFile,
                                settings = settings,
                                guard = guard,
                                credentials = credentials,
                                // 两枚坏档读数由根上带来（消费一次、存得住），屏只负责念。
                                followsNotice = followsNotice,
                                favoritesNotice = favoritesNotice,
                                artistTarget = artistTarget,
                                onOpenArtist = openArtist,
                                onCloseArtist = { artistTarget = null },
                                startRanking = startRanking,
                                saveDir = gallerySaveDir,
                                history = galleryHistory,
                                startReaderMode = startReaderMode,
                                // 漫画详情那一屏开在哪一本 + 两枚改它的回调：与 artistTarget/openArtist 同一族，
                                // 由根上持有（换域/换页要能收掉它，戳同 pickStamp 那一条理由）。
                                comicTarget = comicTarget,
                                readTarget = readTarget,
                                onOpenComic = { target ->
                                    comicTarget = target
                                    comicStamp = "点封面"
                                    // 换一本时把阅读槽一起收掉：留着它的话屏上那一屏还停在**上一本**的某一话，
                                    // 而底下那本的章节表已经换了 —— 那是一句"读着对而画着错"的假读数。
                                    readTarget = null
                                    readStamp = "点了别本"
                                },
                                onBackComic = {
                                    comicTarget = null
                                    comicStamp = "退回列表"
                                },
                                onOpenEpisode = { reading ->
                                    readTarget = reading
                                    readStamp = "点某一话"
                                },
                                onBackReader = {
                                    readTarget = null
                                    readStamp = "退回详情"
                                },
                            )
                        }
                    }
                }

                val selection = selected
                if (selection != null) {
                    // 步进与关闭各只有**一个主人**：顶上那两枚按钮与 Esc / ← / → 吃同一对 lambda。
                    // 给键盘复制一份 `selected = selection.copy(...)` 的话，两份算式的第一处差别
                    // 就在"一边钳位一边不钳位"上（判据 ⑦ 数着这一行的出现次数）。
                    val step: (Int) -> Unit = { delta ->
                        selected = selection.copy(
                            index = desktopDetailStep(selection.index, delta, selection.size),
                        )
                        pickStamp = "浮层内步进"
                    }
                    val onClose: () -> Unit = {
                        selected = null
                        // ⚠️ 大图那一层**一起收**（第五十批图证量出来的）：`onClose` 是浮层唯一的关闭主人，
                        // 而浮层顶上那枚 × 走的就是它 —— Esc 那条路因为有 `viewerOpen` 分流不会经过这里，
                        // 从 × 按下去却只有 `selected` 变、没人收 `viewerOpen`，屏上就留下
                        // 「浮层没了而大图还开着」（那一帧的读数是 `浮层=关(关掉浮层) 查看器=开`）。
                        // 两层各有各的退路，但"浮层 × = 关掉这一片"必须两枚都动。
                        viewerOpen = false
                        pickStamp = "关掉浮层"
                    }
                    // 「到点自己翻下一张」（批 46）。判据整条**转交**共享那颗
                    // `GalleryAutoPlay.shouldAdvance`（`gallery/domain` 本来就在桌面编译面内）：
                    // 连播不许在弹层开着时翻、不许替人决定视频何时起播、不许在最后一张空转 ——
                    // 那三条都是对岸真机量出来的，桌面重述一份就会漂（漂的形状是"正看着的那张被翻走"）。
                    //
                    // 这一格为什么住在集成方而不是浮层里：浮层按规矩只吃形参（见上面那段注释），
                    // 而这里 `step` 与偏好**都已经在手** —— 加一枚形参要铺三处调用点，铺不出新事实。
                    // `infoOpen` / `zoomed` 交的是**这一屏的事实**（第五十批起 `zoomed` 换成了真读数）：
                    // 放大时连播不许抢页 —— 正在看细节的时候自己翻走是这一族最难受的一种。
                    // 判据整条**转交**共享那颗 `GalleryAutoPlay.shouldAdvance`。
                    val autoPlaySec by settings.galleryAutoPlaySec.collectAsState()
                    val autoPlayVideo = selection.card?.post?.isVideo == true
                    val viewerZoomed = zoomState.scale > 1f
                    LaunchedEffect(autoPlaySec, selection.index, selection.size, autoPlayVideo, viewerZoomed) {
                        if (!GalleryAutoPlay.shouldAdvance(
                                seconds = autoPlaySec,
                                infoOpen = false,
                                isVideo = autoPlayVideo,
                                zoomed = viewerZoomed,
                                currentPage = selection.index,
                                pageCount = selection.size,
                            )
                        ) {
                            return@LaunchedEffect
                        }
                        delay(autoPlaySec * 1000L)
                        // 翻完这一张，selection.index 变了 ⇒ 上面那把钥匙重开这一段（不是一条 while 长跑）。
                        step(1)
                    }

                    // 提前把前后那几张的图取回来（第四十七批）。"取哪几张"整条**转交**共享那颗
                    // `GalleryPreload.plan`（在桌面自有件 desktopPreloadUrls 里那一处），桌面不重述那四条。
                    //
                    // 取图器必须是 `AsyncImage` 背后**同一份**单例（coil3.SingletonImageLoader）：
                    // 另起一枚 ImageLoader 等于把预取写进另一份缓存，点下去照样重取一趟 —— 两条轨那一族。
                    // 初始档位也不在这里重算：两头共用 desktopDetailInitialTier，见那颗件的注释。
                    val preloadMode by settings.galleryPreload.collectAsState()
                    val preloadContext = LocalPlatformContext.current
                    // 取哪一档**吃共用那一枚**而不是设置里那枚（第五十批）：档位上提之后两者可以分岔，
                    // 按设置推会让"翻到的下一张命中缓存"成假（同批 ⑧ 判据）。
                    LaunchedEffect(preloadMode, selection.index, selection.size, tier) {
                        val plan = desktopPreloadPlan(preloadMode, selection.index, selection.cards, tier)
                        val urls = plan.urls
                        // 这一行排在"空不空"那一步**之前**：空批也要念得出（OFF 与"开了但没货"是两句话，
                        // 静默则把这两种压成同一种哑巴）。判据 ⑨ 钉的就是这个次序。
                        println(desktopPreloadReading(preloadMode, plan, tier))
                        if (urls.isEmpty()) {
                            return@LaunchedEffect
                        }
                        val loader = SingletonImageLoader.get(preloadContext)
                        val queued = urls.map { url ->
                            loader.enqueue(ImageRequest.Builder(preloadContext).data(url).build())
                        }
                        try {
                            // 这一批只在"效应还活着"的时候有意义：换页、换档、关浮层之后，前后那几张就换了一批。
                            // `enqueue` 不吃这一层的取消 ⇒ 必须自己收手（少了这一格，连播开着就是一路排几十张没人看的图）。
                            awaitCancellation()
                        } finally {
                            queued.forEach { one -> one.dispose() }
                            // 收手那一句排在 dispose **之后**：先念后停就是拿请求当结果（批 49 那枚顺序钉的另一半）。
                            println(desktopPreloadStopReading(queued.size))
                        }
                    }
                    // ⌨ 浮层必须**自己把焦点要过来**：没有焦点时整棵组合树收不到任何按键
                    // （`FocusOwnerImpl.dispatchKeyEvent` 无焦点那一路找的是 `rootFocusNode` 的祖先，
                    // 而那颗是焦点系统自己造的、上面没有东西 —— 理由全文在 [onDesktopOverlayKey] 的注释）。
                    // 要不到焦点不是"键盘没有"，是"键盘接了而屏上不认"，所以它必须念出一行读数（判据 ⑤）。
                    val overlayFocus = remember { FocusRequester() }
                    // 键上的缩放与那枚按钮一样是**挂起**动作（`changeScale` / `reset` 都是 suspend）
                    val zoomScope = rememberCoroutineScope()
                    LaunchedEffect(Unit) {
                        // 先让过一帧：浮层是这一帧才进组合的，`focusRequester` 的节点要等放置之后才挂上。
                        withFrameNanos { }
                        var granted = overlayFocus.requestFocus()
                        var retries = 0
                        while (!granted && retries < 2) {
                            retries++
                            withFrameNanos { }
                            granted = overlayFocus.requestFocus()
                        }
                        if (!granted) {
                            println("KEYNAV=浮层没拿到焦点：Esc、← / → 与 + / − / 0 这一轮都不生效，屏上只剩 × 与那几枚按钮")
                        }
                    }
                    // 浮层与它左缘那道渐隐是**一对**，所以一起挂在右上角。
                    // 渐隐不在面板里画：稿 `.detail::before`（`:217`）要压在**别人的**内容上，
                    // 画进面板自己框内就只是面板边上一条脏带。
                    //
                    // ⚠️ 第五十批：这三件修饰（focusRequester / focusTarget / onKeyEvent）**上提到下面
                    // 那颗 BoxWithConstraints** —— 它包着"大图 + 浮层"两层，所以一处链服务两层，
                    // 而「全模块只有一处 `.onKeyEvent {`」那条判据不用放宽（`DesktopKeyNavigationTest` ⑥）。
                    // 上提之后浮层那颗 Row 上**不许再留第二处**，否则两层各读一遍键、Esc 会一次退两层。
                    BoxWithConstraints(
                        Modifier
                            .matchParentSize()
                            .focusRequester(overlayFocus)
                            .focusTarget()
                            .onKeyEvent { event ->
                                val signal = event.asDesktopKeySignal()
                                // 缩放那一支排在前面：它只认那六枚键，其余原样交给下面那支
                                // （一处链服务两层 —— 第五十批那条"全模块只有一处 `.onKeyEvent {`"不放宽）。
                                when (onDesktopZoomKey(signal)) {
                                    DesktopZoomIntent.IN -> {
                                        zoomScope.launch {
                                            zoomState.changeScale(
                                                desktopViewerZoomStep(zoomState.scale, zoomState.maxScale, 1), Offset.Zero,
                                            )
                                        }
                                        true
                                    }
                                    DesktopZoomIntent.OUT -> {
                                        zoomScope.launch {
                                            zoomState.changeScale(
                                                desktopViewerZoomStep(zoomState.scale, zoomState.maxScale, -1), Offset.Zero,
                                            )
                                        }
                                        true
                                    }
                                    // 复位走库那颗 `reset()`，与屏上那枚「回到原大小」同一颗：
                                    // 它连位移一起带回中心，而 `changeScale(1f)` 只改倍数、拖偏过的图回不来。
                                    DesktopZoomIntent.RESET -> {
                                        zoomScope.launch { zoomState.reset() }
                                        true
                                    }
                                    null -> onDesktopOverlayKey(
                                        signal,
                                        viewerOpen = viewerOpen,
                                        onCloseViewer = { viewerOpen = false },
                                        onClose = onClose,
                                        onStep = step,
                                    )
                                }
                            },
                    ) {
                        // 宽度**只有这一条来源**：`maxWidth` 是这格真被约束到的宽度，不是整窗宽
                        // （整窗宽会把 rail 与 pane 算进去，那一段在大图底下）。算式在纯函数里。
                        // 该不该逐帧：判据住在共享面那颗 `GalleryMotion.animates`，桌面只喂两枚输入进去
                        // （批 51；全树只这一处调用，由 `DesktopGalleryViewerMotionTest` ⑥ 数着）。
                        // `unmetered = true` 与 `DesktopGalleryPreferences.kt:88-95` 同一口径 ——
                        // 桌面不做流量计费判断，所以那一档给的是 ALWAYS，第二枚参数在那条 when 里不参与。
                        val animatedMode by settings.galleryAnimated.collectAsState()
                        // 那一枚档在上面那格就收好了（批 63：墙要不要糊与这一层画什么共用同一枚档），
                        // 这里只把它递给大图那一屏。
                        if (viewerOpen) {
                            DesktopGalleryViewer(
                                selection = selection,
                                tier = tier,
                                zoomState = zoomState,
                                viewerZoomSeed = startViewerZoom,
                                contentWidth = desktopViewerContentWidth(maxWidth, overlayOpen = true),
                                animateGifs = GalleryMotion.animates(animatedMode, unmetered = true),
                                backdrop = backdropTier,
                            )
                        }
                        Row(Modifier.align(Alignment.TopEnd).fillMaxHeight()) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .width(DesktopGalleryMetrics.detailScrimWidth)
                                    .background(
                                        Brush.horizontalGradient(
                                            // 稿有三段停靠（透明 → .55 → .92，`:217`），这里只取两端：
                                            // 46 宽的一条带上读不出中间那一档，而中间那一档也不是现成口径。
                                            0.0f to Color.Transparent,
                                            1.0f to DesktopTheme.OverlayScrim,
                                        ),
                                    ),
                            )
                            DesktopGalleryDetailPanel(
                                ports = ports,
                                // 交**整批与序号**而不是单张卡：翻页要有数据源，而"共几张"只有墙知道。
                                selection = selection,
                                // 档位由根上那一颗持有（第五十批）：这一层只读它与写回它。
                                tier = tier,
                                onTierChange = { tier = it },
                                // 步进只改序号、不换那一份快照：批是点那一刻的批，
                                // 中途墙自己变了（续页到了）也不该把用户正翻着的这一批换掉。
                                onStep = step,
                                onClose = onClose,
                                // 覆盖层读不到上面那圈 provider ⇒ 交接必须从这里递进去（必填）。
                                onSearch = goToSearch,
                                // 同一颗理由的第二枚（第三十六批）：`--reverse` 那颗种子走 local 时
                                // 永远读到默认值，2026-10-08 那一帧拍到过"stdout 念了而屏上没动"。
                                seedReverse = startReverse,
                                // 同一颗理由的第三枚（第五十四批）：覆盖层读不到 provider，
                                // 而「复制图片」那一枚也只有按下去才量得出剪贴板里进了什么。
                                autoCopyImage = startCopy,
                                // 同一颗理由的第四枚（第八十五批）：「存到本机」也在覆盖层里，而它唯一的
                                // 证据是盘上多出来那颗文件 —— 递进去的是算好的落点，不是 provider。
                                autoSaveImage = startSave,
                                saveDir = gallerySaveDir,
                                // 同一颗理由的第五枚（第八十六批）：记「看过」那一笔也住在覆盖层里，
                                // 而浮层与首页那一屏必须拿**同一颗**件 —— 所以由根上递进来，不让它自己建。
                                history = galleryHistory,
                            )
                        }
                    }
                }
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
@Composable
private fun DesktopDomainBody(
    domain: DesktopDomain,
    row: DesktopGalleryHomeRow,
    ports: GalleryPorts,
    comic: DesktopComicEngineConfig,
    /**
     * 漫画详情那一屏开在哪一本（null = 列表）。由根上持有，与画廊的 `selected`、画师的 `artistTarget` 同一族。
     * 必填、不给默认值：漏传的形状是"卡上点了没反应"，而封面是**画出来了**的没反应 —— 比不摆更糟。
     */
    comicTarget: DesktopComicTarget?,
    /**
     * 阅读那一屏开在哪一话（null = 不在阅读那一层）。必填、不给默认值：漏传的形状是"某一话按下去只把
     * 戳改了一下而那一屏永远出不来"，而它**不会报错** —— 与 [comicTarget] 同一条理由。
     */
    readTarget: DesktopComicReading?,
    /** 封面点开（必填：漏传的形状是一枚按不动的封面）。 */
    onOpenComic: (DesktopComicTarget) -> Unit,
    /** 详情那一屏的「← 返回列表」（必填，同上）。 */
    onBackComic: () -> Unit,
    /** 某一话点开（必填：漏传的形状是一行摆着能点的样子而按不动）。 */
    onOpenEpisode: (DesktopComicReading) -> Unit,
    /** 阅读那一屏的「← 返回详情」（必填，同上）。 */
    onBackReader: () -> Unit,
    /**
     * 搜索那一屏的种子（初值 = `--query=`，之后被"点标签 / 点画师名"改写）。
     * 必填、不给默认值：漏传的形状是"点了标签而搜索屏仍显示上一轮"，而那一屏照样有内容 ——
     * 那种"看着像动了其实没接"的形状正是这一族判据最忌的。
     */
    searchSeed: DesktopSearchSeed,
    /** `--reverse-file=` 那一枚种子（批 77 下半）。必填、不给默认值，理由与形参 [searchSeed] 同一条。 */
    seedLocalReverse: String?,
    settings: DesktopGalleryPreferences,
    /** 设置域那一屏的分级档与屏蔽规则写侧，理由与形参 [settings] 同一条（同一批、同一形状）。 */
    guard: DesktopGalleryContentGuard,
    /** 设置域那一屏的凭据写侧（批 34 的 SauceNao 键），理由同形参 [guard] 那一条。 */
    credentials: DesktopGalleryCredentials,
    /**
     * 关注名单那份档**读不出来**时 store 留下的那一句（`null` = 档是好的）。
     * 必填、不给默认值：漏传的形状是"屏上摆着一份空名单而真相是它坏了"，那一屏不会报错。
     * 为什么由根上递下来而不是屏里自己取：见调用处那三行注释（读走即清 + 屏会离开组合）。
     */
    followsNotice: String?,
    /** 画廊收藏那份档的同一句读数，理由与 [followsNotice] 完全一致。 */
    favoritesNotice: String?,
    /**
     * 画师单人页的目的地槽（`null` = 今天看的是名单那一层）。三枚都必填、不给默认值：
     * 漏传的形状是"名单里那一枚按不动"，而它是**画出来了**的按不动 —— 比不摆更糟。
     */
    artistTarget: DesktopArtistTarget?,
    onOpenArtist: (DesktopArtistTarget) -> Unit,
    onCloseArtist: () -> Unit,
    /**
     * 排行榜那颗种子（批 30，`--ranking=`）。必填、不给默认值：漏传的形状是"那一屏永远停在默认档，
     * 而期次那一排因为档没拨过去所以根本不出现"，同时**一句报错都没有** —— 那正是批 29 欠的图证缺口。
     * 只有图库域那一行吃它，其余域没有档与期的概念。
     */
    startRanking: DesktopRankingSeed?,
    /**
     * 存图落点（批 85）。只有图库域「下载」那一行吃它，其余域没有这一层 ——
     * 所以它走**参数**而不是 provider：漏传就编译不过，而 provider 递进界面件会带进一条能建目录的手。
     */
    saveDir: File,
    /**
     * 图库浏览历史那颗件（第八十六批）。只有图库域「历史」那一行吃它，其余域没有这一层 ——
     * 与上面那枚 `saveDir` 同一条理由走**参数**：漏传就编译不过，而自己建就会建出两颗。
     */
    history: DesktopGalleryBrowseHistory,
    /** 取证种子顶上的那一档阅读模式（`null` = 用盘上那档）。往下递给阅读那一屏，不改盘。 */
    startReaderMode: DesktopReaderMode?,
) {
    val rendered: Unit = when (domain) {
        DesktopDomain.GALLERY -> DesktopGalleryHomeBody(row, ports, startRanking, saveDir, history)
        DesktopDomain.COMIC -> DesktopComicBody(
            row,
            comic,
            comicTarget,
            readTarget,
            onOpenComic = onOpenComic,
            onBackComic = onBackComic,
            onOpenEpisode = onOpenEpisode,
            onBackReader = onBackReader,
            startReaderMode = startReaderMode,
        )
        DesktopDomain.FAVORITES -> DesktopGalleryFavoritesBody(row, ports, favoritesNotice = favoritesNotice)
        DesktopDomain.SEARCH -> DesktopGallerySearchBody(row, ports, searchSeed, seedLocalReverse)
        // 第七批：设置域从坦白页转正。它吃的不是 `ports` 而是那颗具体件 ——
        // 共享接口只有读面，写侧只能在桌面这一侧拿。
        DesktopDomain.SETTINGS -> DesktopGallerySettingsBody(row, settings, guard, credentials)
        // 第八批名单 + 批 19 单人页：槽非空时那一层覆盖这一行，返回把槽清空即回到名单。
        // 桌面没有导航栈（那是 Android 侧的那一层），所以"返回"只能是一颗把槽清掉的按钮。
        DesktopDomain.ARTISTS -> artistTarget?.let { target ->
            DesktopArtistProfilePane(ports, target, onBack = onCloseArtist)
        } ?: DesktopGalleryArtistsBody(row, ports, followsNotice = followsNotice, onOpenArtist = onOpenArtist)
        DesktopDomain.DISCOVER -> DesktopGalleryDiscoverBody(row, ports)
        else -> DesktopAbsentDomainBody(domain)
    }
}
