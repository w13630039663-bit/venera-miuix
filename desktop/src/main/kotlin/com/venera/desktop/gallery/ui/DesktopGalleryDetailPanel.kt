package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.preferredExternalUrl
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.desktop.gallery.data.DesktopDictRead
import com.venera.desktop.gallery.data.DesktopDictWhy
import com.venera.desktop.gallery.data.DesktopGalleryTagLexicon
import com.venera.desktop.gallery.data.DesktopGalleryBrowseHistory
import com.venera.desktop.gallery.data.desktopSaveImageRun
import com.venera.desktop.gallery.data.desktopSaveImageScreenReading
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import kotlinx.coroutines.launch

/**
 * 「谁接住这一次点卡」—— 由应用根 provide，五面墙各自 `current` 之后**显式传给墙**。
 *
 * ## 为什么走 CompositionLocal 而不是一路加参数
 *
 * 这条回调要穿过四段派发：应用根 → [DesktopDomainBody] → `desktopGalleryDomainPane(section,…)`
 * → pane → 墙。走参数就是这四段各加一枚必填参数，而那四段里有一段是**并行会话正在改的文件**。
 *
 * ## 默认值**抛错**而不是 no-op
 *
 * 漏 provide 的形状不是"崩了"，而是"卡画成能点的样子、点下去什么都不发生"—— 本仓最忌的那枚假开关，
 * 而且它**静默**：没有红字、没有日志，只有用户反复点一张卡。抛错换一次说清来处的红屏，值得。
 * 这条与本仓"降级路径宁可错慢不可静默交错"是同一条。
 */
internal val LocalDesktopWallPick = compositionLocalOf<(DesktopWallSelection) -> Unit> {
    { error("这一屏的墙没被 provide 过点卡回调：选中态要从应用根（VeneraDesktopApp）传下来") }
}

/**
 * 「起窗后要不要自己点开第一张卡」—— 只服务取证参数 `--detail`（判读见 `ForensicFlags`）。
 *
 * ⚠️ 默认值**必须是 `false`**：这一枚与上面那颗不同，漏 provide 的形状不是假开关，
 * 而是"每次启动都无缘无故弹一层浮层在屏上"—— 把取证专用的行为变成默认行为。
 * 那条由 `DesktopGalleryDetailP1Test` ⑨ 钉着。
 */
internal val LocalDesktopWallSeedFirst = compositionLocalOf<Boolean> { false }

/**
 * 点一张卡之后从右侧盖上来的那 340 宽浮层（第十一批 P0，稿画板 B `.detail`）。
 *
 * ## 它是**覆盖层**，不是主区的一列
 *
 * 稿 `:660` 量过：并排会把每列压到 148、违反"每列 200"那条预算，所以它是 `align(TopEnd)` 叠上去的，
 * **不参与宽度分配**（判据 ⑥ 扫着这一颗件里不许出现 `weight(` —— 正文那一栏靠"剩余高度"就够，
 * 它不需要、也不该要权重）。左缘那道 46 宽渐隐（稿 `.detail::before`）同样不在这颗件里画，
 * 而在骨架那一层：它要压在**别人的**内容上，画在自己框内就只是自己边上的一条脏带。
 *
 * ## 挡住的那一档**照挡**
 *
 * [masked] 是从墙上那张卡一路传进来的（墙往下交的是整张卡 [DesktopWallCard] 而不是裸 post，
 * 就是为了这一档不丢）。面板里不重新判分级，也不"点开就放行"：墙上不给看的东西，点开也不给 ——
 * 反过来就是给第四批那层守卫开一个绕出口。
 *
 * ## 稿上有六格四钮，这里只摆今天填得出的
 *
 * 标题、相关标签、画师"作品 N"、上传时间**整格不摆**（前三样结构性没有：三站 API 都不给标题、
 * 没有 related_tags 的来源端点、Android 那侧也没有作品数；上传时间是三颗 post DTO 都没解 `created_at`）。
 * 稿上那四钮今天接三钮（收藏、复制链接、在浏览器打开原图）：下载要另造桌面落盘件（P2），而稿上那枚
 * "分享"在桌面能做的其实只有"复制链接" ⇒ 按拍板改稿叫**复制链接**，不留一枚语义含糊的字。
 * 第四钮是批 35 加的「找同一张图」，稿上原来没有它 —— 它带的是一整格结果，所以那一格排在
 * 动作之后、信息表之前（与标签墙那条同一个理由：按完看不见的东西等于没做）。
 * 第五钮是批 54 加的「复制图片」，稿上也没有：链接那一条交出去的是地址，而对面要图时地址粘不出东西。
 * 这一条由判据 ③ 钉住：撤掉的东西一旦被谁顺手加回来，屏上就会长出一格常年空白的表。
 *
 * ## 尺寸一个都不写死
 *
 * 稿上那批值（340 / 46 / 200 / 30 / 16 / 12 / 76 / 10 / 7 / 1px / 6px / 8px）全在
 * [DesktopGalleryMetrics] 的"详情浮层"那一段，判据 ⑧ 扫这一颗件里**一个写死的 dp 都不许有**。
 * 底色取 [DesktopTheme.SidebarBackground] —— 稿的 `--layer`（`:10`）与侧栏是**同一颗** `#272727`，
 * 所以这里复用它的名字，不新增第二颗同值色。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopGalleryDetailPanel(
    ports: GalleryPorts,
    selection: DesktopWallSelection,
    /**
     * 当前取哪一档（第五十批上提）。**必填、不给默认值**。
     *
     * 为什么从"自己 remember 一份"改成必填形参：这一层与新增的大图那一屏必须读**同一颗**档位，
     * 而档位上提之后它还能被用户手动换（那一排分段控件）。带默认值时漏传不报错，
     * 而漏传的形状正是批 47 修掉的那族"两条轨"—— 屏上取一档、预取按另一档取，
     * 缓存键不同，"已经预取过了"就是假话（`DesktopGalleryPreloadTest` ⑧ 钉着）。
     */
    tier: DesktopDetailTier,
    /**
     * 换档写口。**必填**：这一层只读不写的话，用户在那一排分段控件上按下去会毫无反应，
     * 而那正是本仓最忌的"假开关"。
     */
    onTierChange: (DesktopDetailTier) -> Unit,
    onStep: (Int) -> Unit,
    onClose: () -> Unit,
    /**
     * 点标签 / 点画师名 → 去搜索那一屏（批 2 ⑤）。**必填、不给默认值**，而且不走那颗局部槽：
     * 这一层是覆盖层，挂在应用根那圈 `CompositionLocalProvider` **外面**，
     * 读到的是"没人接"的默认值 —— 2026-10-05 用户手上一按就当场炸在那里（读数只在 stdout，
     * 屏上不动）。漏传不报错的形状不许有，而这处连"传了但传不到"都不报错。
     */
    onSearch: (List<GallerySite>, String) -> Unit,
    /**
     * 起窗后要不要**自动按一次「找同一张图」**（取证参数 `--reverse`，第三十六批）。
     *
     * 为什么是必填参数而不是又一枚 compositionLocal：这一层是**覆盖层**，挂在应用根那圈
     * `CompositionLocalProvider` 外面 —— 2026-10-08 那一帧拍到过：走 local 时它永远读到默认值 `false`，
     * 于是 `--reverse=1` 是"stdout 念了两句而屏上什么都没发生"（同一颗坑第三次，前两次是 `onSearch`
     * 与那颗点卡回调，见上面那枚形参的注释）。漏传不报错的形状不许有。
     */
    seedReverse: Boolean,
    /**
     * 起窗后要不要**自动按一次「复制图片」**（取证参数 `--copy`，第五十四批）。
     *
     * 必填、不给默认值，与上面那枚同一条理由（这一层是覆盖层，provider 在它外面），而且这一格
     * 与「剪贴板里到底进去了一张什么」这件事**只有按下去才量得出** —— 禁鼠标的那一侧
     * 不带种子就永远只能拍到那枚按钮本身。
     */
    autoCopyImage: Boolean,
    /**
     * 存图的落点（批 85，`数据根/downloads/图库`）。
     *
     * 必填、不给默认值，而且给的是**算好的 `File`** 而不是 `PathProvider`：这一层是覆盖层，
     * provider 在它外面（那三枚种子栽过的同一颗坑），而把 provider 递进来就等于递进一条能建目录的手
     * —— 「去看一眼」那一屏的同一族纪律。
     */
    saveDir: File,
    /**
     * 起窗后要不要**自动按一次「存到本机」**（取证参数 `--save`，第八十五批）。
     *
     * 必填、不给默认值，理由与 [autoCopyImage] 同一条：这一格唯一的证据是**盘上多出来的那颗文件**，
     * 不带种子就只能拍到那枚按钮本身，而"按钮在"与"按下去真写得进盘"是两件事。
     */
    autoSaveImage: Boolean,
    /**
     * 图库浏览历史那颗件（第八十六批）。**必填、不给默认值**，而且给的是集成方**建好的那一颗**。
     *
     * 三个理由叠在这一枚上：
     *  - 这一层是覆盖层，provider 在它外面（那三枚种子栽过的同一颗坑），所以不能由它自己 `getInstance`；
     *    而 UI 里也不许去摸静态口（`DesktopUiIsolationTest` ③ 管的就是这一条）；
     *  - 浮层写的那颗与首页「历史」那一屏读的那颗必须是**同一个对象**，两颗各记一份内存的形状是
     *    "刚点开的那张在屏上看不见"（判据 ⑰）；
     *  - 给默认值的形状是"链子挂上了但永远不落盘"，而它不报错。
     */
    history: DesktopGalleryBrowseHistory,
    modifier: Modifier = Modifier,
) {
    // 序号由 [desktopDetailStep] 钳在两端，而整批是点那一刻的**快照**（不是活引用），
    // 所以落不到 null。真落到了就是哪里把序号写飞了 —— 说清，而不是摆一张空卡。
    val card = requireNotNull(selection.card) {
        "浮层拿到的序号 ${selection.index} 不在这一批 ${selection.size} 张里 —— 步进那一处把界写飞了"
    }
    val post = card.post
    val masked = card.masked
    val favorites by ports.favorites.favorites.collectAsState()
    val favorite = favorites.any { it.uid == post.uid }
    // 关注那两枚字读的**只有这份名单**（判据 ② 钉的就是"不许界面上自己记一颗 bool"）：
    // 点下去立刻翻，是因为写口写的是这颗流，而不是因为界面猜自己写成功了。
    val follows by ports.follows.follows.collectAsState()
    // 屏蔽规则那份表（批 10）：长按一枚标签要先判"列表里已经有这一枚了吗"，
    // 而这颗流就是那一面墙过关卡时读的同一份 —— 加完之后墙上下一帧认，这里也下一帧读到。
    val guardRules by ports.contentGuard.rules.collectAsState()
    // 「找同一张图」那一族要的两枚读数（批 35）：有没有键决定发不发得出去，
    // 分级那一把判据**与那面墙同一颗**（[desktopAllowNsfw] 逐字回核对岸那一行），这里不新造第二个读取点。
    val hasSauceNaoKey by ports.credentials.sauceNaoHasKey.collectAsState()
    val maskMode by ports.contentGuard.nsfwMaskMode.collectAsState()
    val scope = rememberCoroutineScope()
    // 反搜那一格的两种态：正在等（一笔要几秒级，屏上必须有个东西在动）与已经落地的那一份判定。
    var reverseWaiting by remember(post.uid) { mutableStateOf(false) }
    var reverseOutcome by remember(post.uid) { mutableStateOf<DesktopReverseOutcome?>(null) }
    // 打开某一条来源那一下的结果句，**单独一格**：它说的是这一格里的一行，
    // 与上面那两句（动作排的回执、长按标签的回执）共用一格就会把三件事念成一件。
    var reverseNotice by remember(post.uid) { mutableStateOf<String?>(null) }
    // 两笔动作各自的结果句：复制链接失败与"这台机器开不了浏览器"都必须说得出为什么，
    // 而不是按下去没反应（那是一枚假开关）。
    var notice by remember(post.uid) { mutableStateOf<String?>(null) }
    // 长按一枚标签的结果句，**单独一格**（批 10）：它要说的是"这一枚屏蔽上了吗"，
    // 摆在那排标签的正下方才挨着它说的那件事；与上面那一句共用一格会把两件事念成一件。
    var tagNotice by remember(post.uid) { mutableStateOf<String?>(null) }

    // 标签与画师名 → 搜索那条动线（批 2 ⑤）。接收者是**必填参数**而不是那颗局部槽：
    // 理由逐字写在函数签名那里（覆盖层在 provider 外面，2026-10-05 那一次当场炸）。
    // 画师那一栏有**两条来路**，各取各的，最后由那颗纯函数合档：
    //  ① 站方那一页自己标的档位 —— 只在面板打开时才发这一笔（三站 markup 不是一套，理由见 GalleryTagCategories）；
    //  ② 离线词典的画师档（`category = 1` 的那几枚）—— 本地查一趟，一分请求都不花，**不等** ①。
    // 两条的作用域互斥写在共享那颗 `buildGalleryTagBuckets` 里（① 非空 ⇒ 完全按它分而不看 ②），
    // 这里只把两份结果递进去并记住"① 到底答没答上"（`asked`）—— 没答上才是 NotAnswered，
    // 而取回 `null` 是"那一页没答上"，**不是**"这张没有画师"。
    var siteCategories by remember(post.uid) { mutableStateOf<Map<String, Int>?>(null) }
    var siteCategoriesAsked by remember(post.uid) { mutableStateOf(false) }
    var artistFallback by remember(post.uid) { mutableStateOf<Set<String>>(emptySet()) }
    // 词典那一问**是哪一态**（批 78）：null = 读到了而这批名字里没有。那句"离线词典里也没有这一串标签"
    // 只在读过时才成立，所以那一态要一路递到屏上那一句。
    var artistDictWhy by remember(post.uid) { mutableStateOf<DesktopDictWhy?>(null) }
    LaunchedEffect(post.uid, post.pageUrl) {
        siteCategories = runCatching { ports.lexicon.fetchTagCategories(post.pageUrl) }.getOrNull()
        siteCategoriesAsked = true
    }
    val artists = desktopDetailArtists(
        post.tagList,
        siteCategories,
        fallbackArtistNames = artistFallback,
        asked = siteCategoriesAsked,
    )

    // 离线词典那两问（译名 + 画师档）：**取不到就当没有**（那一排标签照原名摆、那一栏退回"没取到"），
    // 而不是把文字留空。与对岸同一档（`GalleryPostScreen.kt:363-364` 也是打开那张卡一次问两样），
    // 钥匙是这一张卡与它那一批标签。
    var tagTranslations by remember(post.uid) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(post.uid, post.tagList) {
        // 画师档那一问问的是**完整三态**（桌面那颗词典口才给）：那一句兜底要说得出是"读过而没有"、
        // "包里没带"还是"没读出来"。非桌面那颗（对岸/夹具）给不出态，按"读过而没有"念 —— 那是它自己的口径。
        val desktopLexicon = ports.lexicon as? DesktopGalleryTagLexicon
        if (desktopLexicon != null) {
            val read = desktopLexicon.artistNamesRead(post.tagList)
            artistFallback = if (read is DesktopDictRead.Hit) read.value else emptySet()
            artistDictWhy = if (read is DesktopDictRead.Miss) read.why else null
        } else {
            artistFallback = runCatching { ports.lexicon.artistNames(post.tagList) }.getOrDefault(emptySet())
            artistDictWhy = null
        }
        tagTranslations = runCatching { ports.lexicon.translations(post.tagList) }.getOrDefault(emptyMap())
    }

    // 档由**应用根持有**（第五十批）：这一层只读那一颗、自己不再 remember 一份，
    // 而大图那一屏与预取读的是同一颗（`DesktopGalleryViewerStateTest` ① 钉着）。
    val tiers = desktopDetailTiers(post, masked)
    // 换到一张"当前这档不该摆"的卡（从有原图的一条换到视频的一条）时落回这一张的第一档，
    // 而不是让面板去取一枚站方没给的地址、再把灰块说成"站方没给"。
    val shownTier = if (tiers.contains(tier)) tier else tiers.firstOrNull() ?: DesktopDetailTier.PREVIEW
    val url = if (masked) null else desktopDetailTierUrl(post, shownTier)

    // 按一次「找同一张图」—— 按钮与取证种子（`--reverse`，第三十六批）**共用这一枚**，
    // 种子自己再写一份发车就是第二份口径（那份永远不会被点到，也永远不会被改到）。
    val fireReverse: () -> Unit = {
        if (!reverseWaiting) {
            reverseNotice = null
            reverseOutcome = null
            reverseWaiting = true
            // 这笔挂在浮层的组合协程上：关掉浮层或换一张卡就取消它，
            // 那一取消**不会**被翻成"这一张没搜成"（判据 ⑦ 钉的是那颗件里的重抛）。
            scope.launch {
                reverseOutcome = desktopReverseRun(
                    reverse = ports.reverse,
                    post = post,
                    masked = masked,
                    hasKey = hasSauceNaoKey,
                    allowNsfw = desktopAllowNsfw(maskMode),
                )
                reverseWaiting = false
            }
        }
    }
    // 种子那一档：键是 `post.uid`，所以**换一张卡会再按一次**（这一枚只服务无人值守取证，
    // 真实用户永远走不到这里 —— 应用根递进来的是 `--reverse` 的判读结果，默认关）。
    if (seedReverse) {
        LaunchedEffect(post.uid) { fireReverse() }
    }

    // 「复制图片」那一枚（批 54）：按钮与取证种子（`--copy`）**共用这一枚 lambda** ——
    // 两处各写一遍的话，种子跑通的那条路就不是用户按的那条路（与上面那枚同一条理由）。
    // 取字节走的是播放器同一颗件，所以复制到的就是刚在屏幕上看的那一份，不是另发一趟请求的另一张。
    val platformContext = LocalPlatformContext.current
    var copyWaiting by remember(post.uid) { mutableStateOf(false) }
    val fireCopyImage: () -> Unit = {
        if (!copyWaiting) {
            copyWaiting = true
            notice = null
            scope.launch {
                notice = desktopCopyImageScreenReading(desktopCopyImageRun(platformContext, url))
                copyWaiting = false
            }
        }
    }
    // 种子那一档：这一格只有按下去才量得出剪贴板里进了什么，而鼠标是禁的（手册 §8）。
    if (autoCopyImage) {
        LaunchedEffect(post.uid) { fireCopyImage() }
    }

    // 「存到本机」那一枚（批 85）：形状照上面那枚 —— 按钮与取证种子（`--save`）共用同一颗 lambda，
    // 取字节也走同一颗共用件（冷缓存先取一趟那笔教训只许住在一处）。
    // **唯一的不同是取哪一档**：复制吃"屏上正在摆的这一张"，存吃"原图那一档"（对岸那句口径）——
    // 两处若共用同一枚地址，用户切了预览档就会把降档的那一张当成原图存下来（判据 ⑦）。
    var saveWaiting by remember(post.uid) { mutableStateOf(false) }
    val fireSaveImage: () -> Unit = {
        if (!saveWaiting) {
            saveWaiting = true
            notice = null
            scope.launch {
                notice = desktopSaveImageScreenReading(desktopSaveImageRun(platformContext, saveDir, post))
                saveWaiting = false
            }
        }
    }
    // 种子那一档：这一格唯一的证据是**盘上多出来的那颗文件**，而鼠标是禁的（手册 §8）⇒ 不按一次就拍不到。
    if (autoSaveImage) {
        LaunchedEffect(post.uid) { fireSaveImage() }
    }

    // 「看过」那一笔（第八十六批）：与上面两枚不同，它**不等按钮** —— "看过"这件事的发生地
    // 就是这一层浮层起来那一刻，所以记它的那一笔也住在这里（判据 ⑤ 把锚点钉在这一颗文件而不是发起方）。
    // 挡住的那一档不记：用户没看见画，记一条"看过"就是假历史（判据 ⑥ 钉那一圈守卫）。
    // 键挂在 post.uid 上：换一张卡记一笔，同一张卡反复重组不重复记（首页那一行只留一行，时刻跟着刷新）。
    if (!masked) {
        LaunchedEffect(post.uid) { history.recordView(post, System.currentTimeMillis()) }
    }

    Column(
        modifier
            .fillMaxHeight()
            .width(DesktopGalleryMetrics.detailWidth)
            .background(DesktopTheme.SidebarBackground)
            // 稿 `.detail{border-left:1px}`（`:215`）只画左边那道；这里四面同值，
            // 因为浮层就贴在窗口的上/右/下三条边上，另外三边画了也看不见。
            .border(DesktopGalleryMetrics.hairline, DesktopTheme.SurfaceRaised),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(DesktopGalleryMetrics.detailHeroHeight),
        ) {
            // 取哪一档**不在这里判**：转交 [desktopDetailTierUrl]，而它前两档逐字转交共享那颗
            // `GallerySettingsModel.wallUrl`（视频只吃静帧、大图取不到退预览都在那个里面）。
            when {
                // 挡住的那一档连发起取图都不该有（与墙上同一条）。
                url == null -> PanelFallback("分级遮挡")
                url.isBlank() -> PanelFallback("站方没给这一档的图")
                else -> AsyncImage(
                    model = url,
                    contentDescription = "${post.site.displayName} 的作品",
                    modifier = Modifier.fillMaxSize(),
                    // 「原图」档要看**整幅** ⇒ Fit（裁掉一半还谈什么原图），
                    // 预览与大图两档是缩略观感 ⇒ Crop 填满那一格。
                    contentScale = if (shownTier == DesktopDetailTier.ORIGINAL) ContentScale.Fit else ContentScale.Crop,
                )
            }
            // 关闭钮（稿 `.dclose{right:10px;top:10px;width:30px;height:30px}` `:222`）。
            // 用 `padding` 而不是 `offset` 把它从角上让开：让开的是**贴边的那一圈**，
            // 而 offset 只挪绘制不改布局，点起来会连旁边那张图一起吃掉。
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = DesktopGalleryMetrics.detailCloseInset,
                        end = DesktopGalleryMetrics.detailCloseInset,
                    )
                    .size(DesktopGalleryMetrics.detailCloseSize)
                    .clip(CircleShape)
                    .background(DesktopTheme.CardOverlayEnd)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(DesktopGalleryMetrics.detailBodyPadding),
            verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.detailBodyGap),
        ) {
            // ── P1 那两排：换档与翻页。都贴在图下面，因为它们说的都是"这一大位现在是什么"。
            //
            // 档排复用段头那一颗 `DesktopSegmented`（同一排单选控件的口径：容器底、描边、
            // 选中那枚换底色、13px）—— 桌面今天已有两处各画了一份，这里**不画第三份**。
            // 被挡住的那一张一档都不摆（`desktopDetailTiers` 回空表），因为三档换出来的
            // 都是同一句「分级遮挡」，那一排就是三枚假开关。
            if (tiers.isNotEmpty()) {
                val tierItems = tiers.map { DesktopSegment(it.panelLabel(), null) }
                DesktopSegmented(
                    items = tierItems,
                    selectedIndex = tiers.indexOf(shownTier),
                    onSelect = { segment ->
                        tiers.getOrNull(tierItems.indexOf(segment))?.let { picked -> onTierChange(picked) }
                    },
                )
            }

            // 页码与两枚步进。⚠️ 只有一张时两枚都不摆（`desktopDetailCanStep` 给 false×false）：
            // "下一张"按下去原地不动，与假开关同形。页码读数**仍然摆** —— 那是实话。
            run {
                val (canPrev, canNext) = desktopDetailCanStep(selection.index, selection.size)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canPrev) {
                        Button(onClick = { onStep(-1) }) { Text("上一张", fontSize = 13.sp) }
                    }
                    Text(
                        "第 ${selection.index + 1} / ${selection.size} 张",
                        color = DesktopTheme.TextSecondary,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(horizontal = DesktopGalleryMetrics.tightGap),
                    )
                    if (canNext) {
                        Button(onClick = { onStep(1) }) { Text("下一张", fontSize = 13.sp) }
                    }
                }
            }

            // 顶部那两行：身份行用稿 `.by`（12.5px / t3，`:227`）—— 稿上"Pixiv · ID 12345678"
            // 这一串就住在这一档里；画师名用稿 `.artist .nm`（13.5px / 600，`:241`）。
            // 稿的 `h5` 那一格是**作品标题**，而标题这一格是拍板撤掉的（见类注释）。
            // 顶部那一行是**身份行**（站名 + 作品号，稿 `.by` 12.5px / t3，`:227`），下面那一行才是画师
            // （稿 `.artist .nm` 13.5px / 600，`:241`）。稿的 `h5` 那一格是**作品标题**，标题这一格
            // 是拍板撤掉的（见类注释）。
            // ⚠️ 这里**从前**摆的是 `post.author` —— 那是**上传者的登录名**（实测那句在
            // `GalleryInfoSheet.kt:444-447`：真帖 1269641 的 `author=Arsy`，画师是标签 `gijang`），
            // 摆在署名位上就是"用位置说出来的假读数"。它现在住在下面那张信息表里，
            // 键名与 Android 那一格逐字相同（判据 ③）；真画师走 [desktopDetailArtists]。
            Column(verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap)) {
                Text(desktopDetailHeadline(post), color = DesktopTheme.TextTertiary, fontSize = 12.5.sp)
                when (val line = artists) {
                    is DesktopDetailArtists.Names -> Column(verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap)) {
                        // 名字来自离线词典那一档时，先念一句来源（判据 ④）：屏上摆着一串名字而说不出是谁给的，
                        // 就是把「我们猜的」摆成「站方说的」。站方那一档这句是空串 ⇒ 不占一格。
                        val artistSourceNotice = desktopArtistLineNotice(line, post.site, artistDictWhy)
                        if (artistSourceNotice.isNotBlank()) {
                            Text(artistSourceNotice, color = DesktopTheme.TextTertiary, fontSize = 12.sp)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap),
                            verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap),
                        ) {
                        // 每一枚都可点 ⇒ 去搜这一位。⚠️ 带的是**这张卡所属的那个站**而不是让搜索屏猜：
                        // 两站词表不通，同一串标签换个站就是另一个结果（Android 交接载荷那句原文）。
                        // 名字后面那两枚字才是关注入口（批 2 ④）：写口两端早就实现好了，
                        // 缺的只是界面这一下 —— 那句"桌面还没有这一步"的旧说明随上架一起下架，
                        // 并由 `DesktopStaleClaimsTest` ② 钉着它别以注释的形态回来。
                        line.names.forEach { name ->
                            val following = follows.any { it.site == post.site && it.name == name }
                            Row(horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap)) {
                                Text(
                                    name,
                                    color = DesktopTheme.AccentFuji,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onSearch(listOf(post.site), name) },
                                )
                                Text(
                                    desktopFollowLabel(following),
                                    // 「已关注」压暗：主色给"还没做的那一步"，而"这件事已经做了"
                                    // 要读得出是**状态**而不是另一个动作。
                                    color = if (following) DesktopTheme.TextTertiary else DesktopTheme.AccentFuji,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    modifier = Modifier.clickable {
                                        scope.launch { ports.follows.toggle(post.site, name) }
                                    },
                                )
                            }
                        }
                        }
                    }
                    // 其余各档说一句（正在问 / 没标 / 两处都没答上），不许合成一句「没有画师」。
                    else -> Text(desktopArtistLineNotice(line, post.site, artistDictWhy), color = DesktopTheme.TextTertiary, fontSize = 12.sp)
                }
            }

            // 这一排今天六枚（批 85 起；批 54 是五枚）。稿 `.acts4` 是四枚等宽格（`:228`），这里**不等宽** ——
            // 因为桌面走 compose-fluent 的 `Button`（自带内衬与最小宽），把它压成等宽格
            // 会把"在浏览器打开原图"那枚切掉。用 FlowRow 让装不下的自己换行，
            // 而不是让第三枚被裁成"在浏览器打开原"。
            // 开浏览器这一枚与下面结果那一格共用同一个判定（本机给不给这条路由两处各问一遍会漂）。
            val canBrowse = desktopCanBrowseExternally()
            // 复制图片这一枚也**先问支持不支持**（判据 ③ 钉的就是这一行只看能力位）：
            // 无头那一档 `systemClipboard` 直接抛，按下去什么都不发生的一枚按钮比没有按钮更糟。
            val canCopyImage = desktopCanCopyImage()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap),
                verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap),
            ) {
                Button(onClick = { scope.launch { ports.favorites.toggle(post) } }) {
                    Text(if (favorite) "已收藏" else "收藏", fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        notice = if (post.fileUrl.isBlank()) {
                            "这一张站方没给原图地址，没什么可复制的"
                        } else {
                            runCatching {
                                Toolkit.getDefaultToolkit().systemClipboard
                                    .setContents(StringSelection(post.fileUrl), null)
                            }.fold(
                                onSuccess = { "链接已复制" },
                                onFailure = { "复制没成：${it::class.java.simpleName}" },
                            )
                        }
                    },
                ) {
                    Text("复制链接", fontSize = 13.sp)
                }
                // 第五枚（批 54）：把**屏上正在摆的这一档**交给剪贴板。「复制链接」交出去的是地址，
                // 而对面要图时地址粘不出东西 —— 这一枚补的就是那一格。
                if (canCopyImage) {
                    Button(onClick = fireCopyImage) {
                        Text("复制图片", fontSize = 13.sp)
                    }
                } else {
                    Text("这台机器没给出写图片到剪贴板的路，复制图片那一枚不摆", color = DesktopTheme.TextTertiary, fontSize = 11.5.sp)
                }
                // 第六枚（批 85）：把**原图那一档**写到本机一个数得出的目录里。
                // 这一枚不问能力位：写字节是纯 JDK，而这台机器给不给写数据目录，启动那一刻的
                // `DesktopPaths.create()` 已经当场炸过一回了 —— 再问一遍就是第二把尺子。
                // 真写不进去时那一档由 `DesktopSaveImageCause.DISK` 那一句说清，按钮照样按得动。
                Button(onClick = fireSaveImage) {
                    Text("存到本机", fontSize = 13.sp)
                }
                // 开浏览器这件事**先问支持不支持**：不支持时不摆那枚按钮，
                // 而是把这句话摆出来 —— 按下去什么都不发生的一枚按钮，比没有按钮更糟。
                if (canBrowse) {
                    Button(onClick = { scope.launch { notice = openInBrowser(post.fileUrl) } }) {
                        Text("在浏览器打开原图", fontSize = 13.sp)
                    }
                } else {
                    Text("这台机器没给出浏览器的路，原图那一枚不摆", color = DesktopTheme.TextTertiary, fontSize = 11.5.sp)
                }
                // 第四钮（批 35）：把这一张交给 SauceNAO 比对。正在等的时候**不置灰、只不响应** ——
                // 置灰那枚淡主色按钮在这套主题里读起来像坏了，而这一格下面那句「正在等 SauceNAO 回话」
                // 才是它还在动的凭据（口径照对岸那颗反搜按钮的裁决，不另定一档）。
                Button(onClick = fireReverse) {
                    Text(DESKTOP_REVERSE_ENTRY_LABEL, fontSize = 13.sp)
                }
            }

            // 正在等的那一句（批 54）：冷缓存时那一笔要先取一趟图，几秒级是常态 ——
            // 与反搜那一格同一条口径：不置灰那枚按钮，而是把"它还在动"摆出来。
            if (copyWaiting) {
                Text("正在把这一张交给剪贴板", color = DesktopTheme.TextTertiary, fontSize = 12.sp)
            }
            // 存的那一枚更慢：它取的是**原图那一档**（屏上摆的可能是预览档，那一档在缓存里而原档不一定）。
            if (saveWaiting) {
                Text("正在取原图那一档并写到本机，原图比屏上这一张大", color = DesktopTheme.TextTertiary, fontSize = 12.sp)
            }

            notice?.let { message ->
                Text(message, color = DesktopTheme.TextSecondary, fontSize = 12.5.sp)
            }

            // ── 结果那一格（批 35）。排在动作之后、信息表之前：判据 ⑩ 钉着这个次序，
            // 理由与标签墙那一条同源 —— 按完看不见东西，与没做同一件事。
            // 四档各念各的（没发车 / 摆不出 / 没搜成 / 摆得出来），一句都不许并。
            if (reverseWaiting) {
                Text(desktopReverseWaitingReading(), color = DesktopTheme.TextTertiary, fontSize = 12.sp)
            }
            when (val outcome = reverseOutcome) {
                is DesktopReverseOutcome.NotSent -> Text(outcome.why, color = DesktopTheme.AccentBeni, fontSize = 12.sp)
                is DesktopReverseOutcome.Failed ->
                    Text(desktopReverseFailedReading(outcome.reason), color = DesktopTheme.AccentBeni, fontSize = 12.sp)

                is DesktopReverseOutcome.NoSimilar ->
                    Text(
                        desktopReverseNoSimilarReading(outcome.message, outcome.returned),
                        color = DesktopTheme.TextSecondary,
                        fontSize = 12.sp,
                    )

                is DesktopReverseOutcome.Answered -> Column(
                    verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap),
                ) {
                    Text(
                        "找同一张图的结果",
                        color = DesktopTheme.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // 退档那句**只在退过的时候念**：发出去的是哪一枚地址是可对核的事实，
                    // 而"这样相似度更低"没有量过，屏上不许替站方决定。
                    outcome.note?.let { message ->
                        Text(message, color = DesktopTheme.TextTertiary, fontSize = 11.5.sp)
                    }
                    // ⚠️ 这一格**只摆文字**：对岸那句写着"这一屏真正要读的是相似度与作者，
                    // 那是两行文字，不是一个缩略图"，而浮层只有 340 宽。
                    // 站方那批命中大半不是我们那两站的图（pixiv / Twitter / 截图），
                    // 塞进缩略图只会得到一排等高方块，还要多开一条不经守卫的取图路。
                    outcome.hits.forEach { hit ->
                        Column(verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap)) {
                            Text(desktopReverseHitReading(hit), color = DesktopTheme.TextPrimary, fontSize = 12.5.sp)
                            hit.title?.let { title ->
                                Text(title, color = DesktopTheme.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text(desktopReverseArtistReading(hit), color = DesktopTheme.TextSecondary, fontSize = 12.sp)
                            desktopReverseSiteRefReading(hit)?.let { ref ->
                                Text(ref, color = DesktopTheme.TextTertiary, fontSize = 11.5.sp)
                            }
                            // 「打开来源」走 `preferredExternalUrl` 而不是第一条：站方给的 `source`
                            // 常是一张裸图直链，那是 2026-09-27 那条真机反馈，口径在共享那颗件里。
                            val source = preferredExternalUrl(hit.extUrls)
                            if (source != null && canBrowse) {
                                Text(
                                    "打开来源",
                                    color = DesktopTheme.AccentFuji,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable {
                                        scope.launch { reverseNotice = openInBrowser(source) }
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        desktopReverseCapReading(cap = outcome.cap, taken = outcome.hits.size),
                        color = DesktopTheme.TextTertiary,
                        fontSize = 11.5.sp,
                    )
                }

                null -> Unit
            }
            reverseNotice?.let { message ->
                Text(message, color = DesktopTheme.TextSecondary, fontSize = 12.sp)
            }

            // 信息表（稿 `.infotbl` `:233-237`：外框 1px + r-card 圆角，行内衬 7/10，键列 76）。
            // 行与行之间那道分隔稿用的是 `#2E2E2E`，桌面没有这一档色 ⇒ **不加**：
            // 为一行分隔新增第五档深灰，是"色越加越多、没人说得清哪档该用"的开头。
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DesktopGalleryMetrics.cardRadius))
                    .border(DesktopGalleryMetrics.hairline, DesktopTheme.SurfaceRaised),
            ) {
                Text(
                    "作品信息",
                    color = DesktopTheme.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        start = DesktopGalleryMetrics.detailInfoRowHorizontalPadding,
                        end = DesktopGalleryMetrics.detailInfoRowHorizontalPadding,
                        top = DesktopGalleryMetrics.detailInfoRowVerticalPadding,
                        bottom = DesktopGalleryMetrics.detailInfoRowVerticalPadding,
                    ),
                )
                desktopDetailInfoRows(post, favorite).forEach { (key, value) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = DesktopGalleryMetrics.detailInfoRowHorizontalPadding,
                                vertical = DesktopGalleryMetrics.detailInfoRowVerticalPadding,
                            ),
                    ) {
                        Text(
                            key,
                            color = DesktopTheme.TextTertiary,
                            fontSize = 12.5.sp,
                            modifier = Modifier.width(DesktopGalleryMetrics.detailInfoKeyWidth),
                        )
                        Text(value, color = DesktopTheme.TextPrimary, fontSize = 12.5.sp)
                    }
                }
            }

            // 标签墙排在**最后**（图证读出来的缺陷，判据 ⑩）。
            // 稿把它放在动作之前，是因为稿上那一条只有 4 枚 + 一枚「+3」；
            // 而 2026-10-05 那张 `--detail` 帧上 Safebooru 一条有几十枚标签，
            // 三个动作与整张信息表被整排推到折叠线以下 —— 打开面板要看的东西全看不见的。
            // ⚠️ 不靠"截断标签"解决：页脚那句明写了这一排**不做二次归类**、括号里是站方那串，
            //    截了就不该再说那句话。挪顺序不动内容。
            if (post.tagList.isNotEmpty()) {
                // 稿 `.chipwrap{gap:6px}` `:181` + `.chip{height:26px;padding:0 11px}` `:137`。
                // 取的是 `post.tagList` 而不是自己再切一遍串：那颗 getter 走的是共享的
                // `splitGalleryTags`，两端切分口径必须一致（本仓为这条返过工）。
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap),
                    verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap),
                ) {
                    post.tagList.forEach { tag ->
                        Box(
                            Modifier
                                .height(DesktopGalleryMetrics.chipHeight)
                                .clip(RoundedCornerShape(DesktopGalleryMetrics.chipHeight / 2))
                                .background(DesktopTheme.SurfaceRaised)
                                // 点一枚标签 = 去搜索这一枚（批 2 ⑤）。挂在 `padding` **之前**，
                                // 于是内衬那一圈也在触达面里 —— 挂在文字上就成了"字能点、边不能点"。
                                // 长按 = 屏蔽这一枚（批 10，对岸那一路在长按菜单里）。两枚动作各一个槽位，
                                // 谁也没把谁换掉：加了长按而把点击改成屏蔽，就是"想搜一下却把标签封了"。
                                // 这件事同时写在页脚那句里 —— 看不见的动作等于没做。
                                .combinedClickable(
                                    onLongClickLabel = "屏蔽这一枚标签",
                                    onLongClick = {
                                        scope.launch {
                                            tagNotice = desktopBlockTag(ports.contentGuard, guardRules, tag)
                                        }
                                    },
                                    onClick = { onSearch(listOf(post.site), tag) },
                                )
                                .padding(horizontal = DesktopGalleryMetrics.chipHorizontalPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                // 只改显示：上面那两枚动作（点击去搜、长按屏蔽）交的还是 `tag` 原文。
                                desktopTagDisplayLabel(tag, tagTranslations),
                                color = DesktopTheme.TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // 长按那一枚的结果句摆在这排标签**正下方**：它说的是这一排里的一枚，挨着才读得对。
            // 三档（已经在列表里 / 加上了 / 没加上）各一句，不合成「已保存」那种话。
            tagNotice?.let { message ->
                Text(message, color = DesktopTheme.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = DesktopGalleryMetrics.tightGap))
            }

            // 页脚那句说四件事：这一排不归类、带括号那一形是谁给的、点它是干什么、按住它是干什么。
            // ⚠️ 「括号里才是站方那串原文」这半句在批 39 被撤过一次，理由是那时桌面那颗词典件恒交空表
            //    （屏上永远不会出带括号的那一形，而**没可能出现的形状不许写成说明**，手册 §0 坑 ⑯）。
            //    批 40 重新量的读数把它推回去了：词典在包内、只读打开成功、79,415 行里 71,423 行带中文，
            //    随手 40 枚屏上真会读的标签命中 33 枚 ⇒ 那一形现在是常态而不是稀客，说明得写回来。
            // ⚠️ 「本地不做二次归类」这半句仍是真话：这一排按站方返回的顺序摆，词典只出**显示名**
            //    （`desktopTagDisplayLabel` 那一颗），档位另取的是**站方自己标的**（`fetchTagCategories`）。
            // ⚠️ 后两半都不许漏：点与按在桌面上**都没有可见的提示**，这一句就是那两枚动作唯一的说明书
            //    —— 说了却没有那枚动作，或做了动作却不写这一句，判据 ⑧ 都会红。
            //    改回去的那一格用那颗常量点名，不留第二份字面量（两处各写一份，将来改名会有一处指错地方）。
            Text(
                "标签这一排不做二次归类，有译名的摆成「译名 (原词)」，括号里才是站方那串原文；" +
                    "点标签或画师名就去搜索那一屏搜它，按住一枚标签 = 屏蔽这一枚（要改回去，去「$DESKTOP_GUARD_SECTION_TITLE」那一区）",
                color = DesktopTheme.TextTertiary,
                fontSize = 11.5.sp,
            )
        }
    }
}

/**
 * 三档在屏上的名字。
 *
 * ⚠️ 「大图」而不是「清晰预览」或「样本」：用户在这排上要选的是"看多大"，
 * 而站方那两枚名字（`sample` / `large`）在两端指的不是同一档 ——
 * 枚举里那枚 `LARGE` 是"墙上摆 sample 档"，照搬上屏就成了"大图 = 原图"的误读。
 * 「原图」是站方 `file_url` 那一枚，也是稿 `.acts4` 里那枚"原图"的同一颗词。
 */
private fun DesktopDetailTier.panelLabel(): String = when (this) {
    DesktopDetailTier.PREVIEW -> "预览"
    DesktopDetailTier.SAMPLE -> "大图"
    DesktopDetailTier.ORIGINAL -> "原图"
}

/** 图位取不到时的底：灰块 + 一句"是哪一档取不到"。与墙上同一族，但这里只有一句（没有站名，站名在头顶那行已经念过）。 */
@Composable
private fun PanelFallback(label: String) {
    Box(Modifier.fillMaxSize().background(DesktopTheme.ImagePlaceholder), contentAlignment = Alignment.Center) {
        Text(label, color = DesktopTheme.OnImagePlaceholder, fontSize = 12.5.sp)
    }
}
