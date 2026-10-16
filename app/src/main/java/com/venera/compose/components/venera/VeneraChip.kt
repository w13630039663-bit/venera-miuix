package com.venera.compose.components.venera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraGlassRole
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * Chip 视觉变体。
 *
 * - [Assist]   : 未选中的动作型（描边 + 透明底）
 * - [Selected] : 选中态（强调容器色）
 * - [Tag]      : 标签语义（比 Assist 更轻，用于卡片上的 tag）
 * - [Filter]   : MD3 filter chip —— 未选中只有描边，选中填 **secondaryContainer**
 *   （不是 primaryContainer：选中一枚筛选条件不该有"主行动"那么响，规范里两者分档）
 * - [Neutral]  : MD3 tonal chip —— **无描边**、填 surfaceContainerHigh，用于"最近搜索"这类次要项
 * - [Tonal]    : 与 [Neutral] 同形（无描边、无前置圆点），但填 **surfaceVariant**（不透明）。
 *   为什么还要一档：`Neutral` 填的 `surfaceContainerHigh` 与画廊搜索卡的卡底**是同一颗色**
 *   （真机逐像素量过：`#EAE7EE / #EAE7EE` = 1.000:1），住在实色卡里就完全看不见。
 *   `Tonal` 用比卡底低一档的 `surfaceVariant`，层次靠**底色**表达而不是靠描边 —— 参考图那套
 *   读起来"有台阶又不抢眼"的就是这一档（实测卡底/控件底 1.055:1 与 1.115:1 两级）。
 */
enum class VeneraChipVariant { Assist, Selected, Tag, Filter, Neutral, Tonal }

/**
 * Venera 基础 Chip（基础层）。
 *
 * 这是**唯一**的 Chip 实现：VeneraTagChip / 页面上的各种条件 Chip 都必须基于它，
 * 禁止再复制一套 UI。
 *
 * ── 交互状态（本组件自行统一处理，页面不得自行实现 Chip press effect）──
 *  | 状态 | 视觉响应 |
 *  |---|---|
 *  | pressed  | 轻微缩放（0.96）+ 底色加深 |
 *  | focused  | 描边转为主色（键盘/无障碍焦点可见） |
 *  | selected | 强调容器色（primaryContainer） |
 *  | disabled | 降透明度 + 不可点击 + 不响应按压 |
 *
 * 反馈刻意保持**轻量**：只有 alpha 与 scale 两条动画，都走 spring，
 * 不使用涟漪/模糊/复杂位移，避免 Chip 密集场景下的额外开销。
 *
 * 主题适配：容器用 miuix Surface；MD3 模式下 VeneraTheme 已桥接色板，
 * 因此两套风格自动呈现各自正确的颜色，组件内无需分支。
 *
 * @param text 显示文本（单行 + Ellipsis）。
 * @param selected 选中态。
 * @param onClick 可空；为 null 时不可点击（纯展示），此时也不产生按压反馈。
 * @param onLongClick 可空；长按（如弹出「复制 / 屏蔽」菜单）。传了它即使 [onClick] 为 null
 *   也算可交互 —— 按压反馈仍由本组件统一持有，页面不要自行叠 combinedClickable。
 * @param leadingIcon 前置图标（如「＋」）。
 * @param leading 前置**插槽**。与 [leadingIcon] 的分工：那个摆的是一枚**单色矢量**
 *   （跟着 [contentColor] 走），这个摆的是**自己带底、自己带色**的一小块 ——
 *   画师介绍页的平台入口就是它：品牌色小方块 + 白字形，两样都不是本组件能推出来的颜色。
 *   给了它就不再画 [leadingIcon]（两枚前置并排没有语义，只会让胶囊读起来像两个东西）。
 * @param trailingIcon 后置图标（如「×」移除）。
 * @param onRemoveClick 给 [trailingIcon] 单独一个动作（胶囊自带的 ×）。
 *   **为什么要有它**：整枚胶囊的点击常被别的语义占着（标签胶囊点一下=改成排除），
 *   此时"删掉这枚"必须挂在 × 那一小块上，不能靠整枚 —— 否则要么两个动作打架，
 *   要么画了个按不动的 ×（假开关）。只在 [trailingIcon] 非空时生效。
 * @param leadingText 前置**次要文字**（如历史 chip 里那枚站点名）：比 [text] 小一档、吃
 *   [VeneraColorTokens.textTertiary]，让主文字（关键词）赢过它。摆这个而不摆图标是为了
 *   "同一枚 chip 里两段文字两种层级"这种需求 —— 别再为它复制一套 chip UI。
 * @param enabled false 时降透明度且不可点击。
 * @param variant 视觉变体；[VeneraChipVariant.Tag] 用于标签语义。
 * @param containerColorOverride 底板色的**兜底覆盖**。给 null（默认）时完全走 [variant]
 *   与各状态分支，既有调用点一字不动。为什么要有这个口子：有些语义标签的底色
 *   本组件推不出来 —— 「关于这张图」右上那枚分级徽标就是（安全 / 留意 / 成人要吃
 *   [com.venera.compose.ui.tokens.GalleryRatingColors] 那四枚固定色）。
 *   ⚠️ 本组件的头注写着"这是**唯一**的 Chip 实现、禁止再复制一套 UI"，
 *   所以那条需求只能从这里开一个口子，**不许**为它另写一枚 Badge 组件。
 * @param contentColorOverride 文字色的兜底覆盖，与 [containerColorOverride] 配对使用。
 *   给了覆盖色之后**对比度由调用方负责**（本组件不再有立场替它挑色）。
 *   两者一起给时状态分支（selected / disabled）不再参与着色 —— 需要状态色的场景
 *   请继续走 [variant]，别用这一对。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VeneraChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    leadingText: String? = null,
    onRemoveClick: (() -> Unit)? = null,
    /**
     * 那枚 × 的**命中盒边长**（方形，图标在其中居中）。**给了 `onRemoveClick` 就必须给这一枚**
     * —— 尺子 `UiAuditChipTouchSplitTest` 数的是调用点，编译器不管（这颗组件有二十来处调用，
     * 全改成必填只会逼出二十行无意义的实参）。
     *
     * 为什么不让组件自己挑一档：2026-10-16 第一版就是"removable 一律给 40dp"，结果
     * **已选条件那排胶囊跟着一起长高**（用户：「连同搜索tag一起变大了，你分开」）。
     * 同一枚 × 住在两种容器里：历史那一条是要**按**的按钮（48dp 那一档），
     * 条件胶囊是**一排里的一个元素**（这一屏每一行都在抢卡片高度，见 `GallerySearchArea` 头注）。
     * 所以尺寸由住的人定，不由零件定。
     */
    removeTouchSize: Dp? = null,
    enabled: Boolean = true,
    variant: VeneraChipVariant = VeneraChipVariant.Assist,
    containerColorOverride: Color? = null,
    contentColorOverride: Color? = null,
    /**
     * 主文字的行数上限，默认 1 行 + 省略 —— 这是 Chip 的常态，**卡片里的标签必须留在这档**：
     * 整张卡的高度是由信息列与标签区驱动的，不是封面（`ComicTileLayout` 那处已烧过三轮往返），
     * 一枚 chip 多一行，一屏几十张卡就集体长高。
     *
     * 只有在**标签本身就是内容**的地方才值得放到 2（详情页标签、探索页分类/榜、画廊信息卡的标签墙、
     * 搜索页标签选择器）—— 那里的失败模式是"读不到完整的词"，而卡片那里的失败模式是"卡变胖"。
     */
    textMaxLines: Int = 1,
) {
    val tokens = VeneraTokens

    // 交互状态源。onClick == null 时仍创建，但 clickable 不会被挂上，
    // 因此 pressed/focused 恒为 false，纯展示 Chip 不会"假响应"。
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()

    val interactive = (onClick != null || onLongClick != null) && enabled
    val isPressed = interactive && pressed

    // ── 诊断包专用（2026-10-04）：撤除时连着 `gallery/ui/GallerySheetProbe.kt` 一起删 ──
    // 16:53 那一份读数是"盒子收到 9 条、到达 0 条"，所以要先把"这枚到底有没有挂上 clickable"
    // 钉成数。这里**不 import 那颗探针**：components 层 import gallery 会破 LayeringEdgeTest，
    // 所以直接写同一个 TAG，让 logcat 一条过滤就能看全。
    // `composeTick` 存在普通 IntArray 里而不是状态：探针不许自己引发重组（同 GallerySheetProbe 的头注），
    // 而下面那颗见证在按住前后各读一次它，就能分出"这一枚在按着的时候被重新组合了没有"。
    val composeTick = remember { IntArray(1) }
    if (variant == VeneraChipVariant.Tag) {
        android.util.Log.i(
            "GallerySheetProbe",
            "chip 装配 「$text」 interactive=$interactive enabled=$enabled " +
                "有点击=${onClick != null} 有长按=${onLongClick != null}",
        )
        composeTick[0]++
    }

    // 轻量动画：只有 scale 与 alpha，均走 spring，无涟漪/模糊。
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) tokens.current.chipPressedScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "VeneraChipPressScale",
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) tokens.current.chipPressedAlpha else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "VeneraChipPressAlpha",
    )

    // ── 颜色决策：覆盖 > disabled > selected > Neutral > Tag > Assist ──
    // 覆盖排在最前：调用方给色时它的语义是"这一枚的颜色不由本组件决定"（见参数说明），
    // 所以它连 disabled/selected 也不再参与 —— 那种需求走 variant，不走这两个口子。
    val container = containerColorOverride ?: when {
        !enabled -> tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha)
        // Filter 的选中态吃 secondaryContainer（MD3 给 filter chip 的就是这一档，比
        // primaryContainer 退一级）；其余变体的选中仍是主色容器。
        selected && variant == VeneraChipVariant.Filter -> tokens.color.secondaryContainer
        selected -> tokens.color.primaryContainer
        // Neutral：无描边的 tonal 面，靠底色本身与背景分层，不画轮廓。
        variant == VeneraChipVariant.Neutral -> tokens.color.surfaceContainerHigh
        // Tonal：同一条路，只是低一档的底 —— 住在"卡底就是 surfaceContainerHigh"的容器里时用它。
        variant == VeneraChipVariant.Tonal -> tokens.color.surfaceVariant
        // Tag：使用**专用**语义 Token，不再用两个 alpha 相加拼凑。
        // 这让 Tag 成为一个可辨识的「块」，同时仍是 surfaceVariant（非 primary），
        // 因而弱于 SourceBadge 的实底深色，满足 Title > SourceBadge > Tag > Metadata。
        variant == VeneraChipVariant.Tag ->
            tokens.color.surfaceVariant.copy(alpha = tokens.current.tagContainerAlpha)
        else -> Color.Transparent
    }
    val contentColor = contentColorOverride ?: when {
        !enabled -> tokens.color.textDisabled
        selected && variant == VeneraChipVariant.Filter -> tokens.color.onSecondaryContainer
        selected -> tokens.color.onPrimaryContainer
        // Tag 文字提到 textPrimary（原为 textSecondary 70%）——
        // 在 0.72f 的容器上，100% 文字才能保证 Light/Dark 均达到 WCAG AA。
        variant == VeneraChipVariant.Tag -> tokens.color.textPrimary
        else -> tokens.color.textPrimary
    }

    // ── 描边：Assist / Filter 未选中时用弱描边；focused 时转主色以提供焦点可见性 ──
    val border = when {
        !enabled -> null
        focused && interactive -> BorderStroke(tokens.spacing.space1, tokens.color.primary)
        // Tag：极弱 outlineVariant 描边，用于强化轮廓识别。
        // 单纯提高 alpha 后若 surfaceVariant 与卡片背景仍接近（尤其 Dark 模式），
        // 边框能保证 Tag 与背景分离；用 outlineVariant 而非 primary，
        // 避免与 selected Chip（实底 primaryContainer）混淆。
        variant == VeneraChipVariant.Tag && !selected ->
            BorderStroke(tokens.spacing.space1 / 2, tokens.color.outlineVariant)
        // Assist 与 Filter 未选中态同形：只有一层 outline 描边（MD3 filter chip 的默认档）。
        // Neutral 刻意不描边 —— 它靠 surfaceContainerHigh 的底色分层，再加轮廓就成了两层装饰。
        (variant == VeneraChipVariant.Assist || variant == VeneraChipVariant.Filter) && !selected ->
            BorderStroke(tokens.spacing.space1 / 2, tokens.color.outline)
        else -> null
    }

    val shape = RoundedCornerShape(tokens.shape.extraLarge)
    // 提前取值：graphicsLayer 的 lambda 不是 @Composable，不能在内部读 tokens.current
    val disabledAlpha = tokens.current.chipDisabledAlpha

    // ── 玻璃档位：只有"本来就是空底"的形态才值得换材质 ──
    // selected 刻意不上玻璃：选中态靠 primaryContainer / secondaryContainer 的**实底**表达
    // "这条已经生效"，玻璃的半透明容器会把这层语义洗成"看起来没选"（比不统一更糟）。
    // disabled 同理，Neutral 本来就靠 surfaceContainerHigh 的底色分层，也不换。
    // Tag 走**点缀档**（只染色、不建模糊）：卡片上一屏可能二十枚，逐个离屏采样就是层数炸弹。
    val glassRole = when {
        !enabled || selected -> null
        // 底色被调用方接管时不叠玻璃：玻璃是半透明的，压在固定语义色上会把那枚色洗淡
        // （分级徽标那四枚色是按"白字可读"逐色挑的，洗一层就不成立了）。
        containerColorOverride != null -> null
        variant == VeneraChipVariant.Tag -> VeneraGlassRole.BADGE
        variant == VeneraChipVariant.Neutral -> null
        // Tonal 与 Neutral 同理：它本来就靠实底分层，再叠半透明玻璃就把那一档洗平了。
        variant == VeneraChipVariant.Tonal -> null
        else -> VeneraGlassRole.CHIP
    }

    Surface(
        shape = shape,
        color = container,
        border = border,
        modifier = modifier
            // 挂在 graphicsLayer **之前**：按压缩放与 alpha 要一起作用在玻璃上，
            // 否则会出现"文字缩了、底板没缩"的脱层。
            .then(if (glassRole != null) Modifier.veneraGlass(glassRole, shape) else Modifier)
            // 按压/焦点反馈统一由 Chip 处理，页面不参与
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = if (enabled) pressAlpha else disabledAlpha
            }
            // ── 诊断包专用（2026-10-05）：撤除时连着 `gallery/ui/GallerySheetProbe.kt` 一起删 ──
            // 这一颗挂在 **combinedClickable 的外侧一格**（同一个 LayoutNode，Main 段）。
            // 同节点上 Main 段是"链上靠后的先收"，所以 clickable 比我先拿到事件 ——
            // 于是我报 `down已被消费=true` 而调用点没报「到达」，就是"clickable 收了 down 却没走完点击"；
            // 我报 false 而它又不响，就是"这一层根本没参与"，与几何无关。
            // 不 import 那颗探针：components 层 import gallery 会破 LayeringEdgeTest。
            .then(
                if (variant == VeneraChipVariant.Tag) {
                    Modifier.pointerInput(text) {
                        awaitEachGesture {
                            val d = awaitFirstDown(false, PointerEventPass.Main)
                            val consumedAtDown = d.isConsumed
                            val ticksAtDown = composeTick[0]
                            var seen = 0
                            var done = false
                            while (!done) {
                                val c = awaitPointerEvent(PointerEventPass.Main)
                                    .changes.firstOrNull { it.id == d.id } ?: break
                                seen++
                                done = !c.pressed
                            }
                            android.util.Log.i(
                                "GallerySheetProbe",
                                "内 $text 收到 down已被消费=$consumedAtDown 事件数=$seen " +
                                    "按下时scaleX=$pressScale alpha=$pressAlpha " +
                                    "组合=${ticksAtDown}→${composeTick[0]} 按着时重组=${composeTick[0] != ticksAtDown}",
                            )
                        }
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (interactive) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null, // 反馈由上面的 graphicsLayer 统一表达，避免涟漪叠加
                        enabled = true,
                        onClick = onClick ?: {},
                        onLongClick = onLongClick,
                    )
                } else {
                    // 仍然提供 interactionSource，使无障碍/键盘焦点可被观测
                    Modifier
                }
            ),
    ) {
        Row(
            modifier = Modifier
                .padding(
                    horizontal = tokens.spacing.chipHorizontalPadding,
                    vertical = tokens.spacing.chipVerticalPadding,
                )
                // ── 诊断包专用（2026-10-05）：撤除时连着 `gallery/ui/GallerySheetProbe.kt` 一起删 ──
                // 挂在**比 combinedClickable 更深一格**的内容行上：Main 段是"内先外后"，所以这一颗
                // 比 clickable 先跑，它报的 `主=` 就是**clickable 轮到之前 down 干不干净**。
                // 这一枚要分掉最后两种可能（foundation `Clickable.kt:1113` 写死了
                // `isChangedToDown(requireUnconsumed = true)`：down 被吃过 ⇒ clickable 根本不开始跟踪
                // ⇒ 不消费抬手、不回调 onClick、不发 Press —— 01:38 那份"事件全到了、一条到达都没有"
                // 只有这一条能造出来）：
                // - `主=false` ⇒ clickable 开始时 down 还是干净的，是它自己走完又判负（slop / 越界 / 长按），坏在本枚内部；
                // - `主=true` ⇒ 它还没轮到就被吃了，吃的人比这一枚更深或 z 序更高（FlowRow 里后画的相邻药丸、
                //   或面板里某个盖在内容上的浮层）。
                .then(
                    if (variant == VeneraChipVariant.Tag) {
                        Modifier.pointerInput(text) {
                            awaitEachGesture {
                                val d = awaitFirstDown(false, PointerEventPass.Initial)
                                val deepInit = d.isConsumed
                                val mainChange = awaitPointerEvent(PointerEventPass.Main)
                                    .changes.firstOrNull { it.id == d.id }
                                val deepMain = mainChange?.isConsumed
                                var seen = 0
                                var pressed = true
                                var consumed = false
                                while (true) {
                                    val c = awaitPointerEvent(PointerEventPass.Main)
                                        .changes.firstOrNull { it.id == d.id } ?: break
                                    seen++
                                    pressed = c.pressed
                                    consumed = c.isConsumed
                                    if (!pressed) break
                                }
                                android.util.Log.i(
                                    "GallerySheetProbe",
                                    "深 $text down=(${d.position.x.toInt()},${d.position.y.toInt()}) " +
                                        "初=$deepInit 主=$deepMain 事件=$seen 已抬手=${!pressed} " +
                                        "抬手被消费=$consumed",
                                )
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            // ⚠️ 这一档从前给"带次要前缀"那支**收到 space1（2dp）**，理由写的是"两段文字是一体的
            // （站点名 + 关键词），留出两个独立元素的间距会把一枚 chip 读成两枚"。真机量下来那一档过头了：
            // 前缀与查询词之间实测只剩字形自带的空隙 ⇒ 屏上是 `Y·G·Slong_hair censored`，
            // 读成一串乱码（2026-10-15 读数：`scr-b16-infosheet.png` 里那两段节点
            // [106,1120][189,1154] 与 [194,1119][419,1156]，间隙 5px ≈ 1.9dp）。
            // 归位到本颗 chip 无前置时就在用的那一档 `space2`，不另立数字；
            // "读成两枚"那条不成立 —— 两段文字同在一枚底色里，间距不是分隔物。
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 前置插槽优先：它与 leadingIcon 互斥（理由见参数说明），摆两块只会让胶囊变胖。
            if (leading != null) {
                leading()
            } else if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(tokens.spacing.chipIconSize),
                )
            }
            // 次要前缀：小一档 + 退到 tertiary，让主文字赢过它（历史 chip 里的站点名就是这个用法）。
            // 它跟着 contentColor 走会一起变响，所以这里**不**吃 contentColor。
            if (leadingText != null) {
                Text(
                    text = leadingText,
                    fontSize = tokens.type.overline,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = text,
                fontSize = tokens.type.caption,
                color = contentColor,
                maxLines = textMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingIcon != null) {
                // ── × 的触达位：2026-10-16 真机改判 ──
                // 从前这一档把 clickable 挂在**图标自己**那颗 16dp 的 `size(chipIconSize)` 上，
                // 外面那圈 `padding` 只是留白、**不吃点击** —— 所以屏上真正的命中区就是 16×16dp，
                // 整枚 chip 也才 ~29dp 高（用户原话「太小很难按到而且还容易删除」）。
                // 现在 clickable 移到那颗 40dp 的 `Box` 上（`iconButtonSize` 是现成档，不造新数字），
                // 图标仍是 16dp：**视觉一寸没变，命中区从 256dp² 变成 1600dp²**。
                // 顺带整枚 chip 被这一档顶到 ≥48dp（40 + 上下各 6 的内边距），
                // 与 MD3 单行列表项那一档同值 —— 也就是用户要的"大点"。
                // 没给 onRemoveClick 就保持原样，不加任何按不动的假触达位。
                val removable = onRemoveClick != null
                // 给了 `removeTouchSize` ⇒ 命中挂在外面那颗盒子上（图标仍是 16dp，视觉一寸不变）。
                // 没给 ⇒ 逐字退回 2026-10-16 之前的形状：clickable 在图标自己身上，
                // 外面那圈只是留白。两种形状分开写，是为了"历史那枚要大好按"不再连带把
                // 条件胶囊那一排顶高（用户点名的就是这一格）。
                val hit = removeTouchSize
                Box(
                    modifier = if (removable && hit != null) {
                        Modifier
                            .padding(start = tokens.spacing.space2)
                            .size(hit)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onRemoveClick,
                            )
                    } else if (removable) {
                        Modifier.padding(
                            start = tokens.spacing.space2,
                            end = if (onLongClick != null || onClick != null) tokens.spacing.space1 else 0.dp,
                        )
                    } else {
                        Modifier
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = if (removable) "移除" else null,
                        tint = contentColor,
                        modifier = Modifier
                            .size(tokens.spacing.chipIconSize)
                            .then(
                                if (removable && hit == null) {
                                    Modifier.combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onRemoveClick ?: {},
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                    )
                }
            }
        }
    }
}
