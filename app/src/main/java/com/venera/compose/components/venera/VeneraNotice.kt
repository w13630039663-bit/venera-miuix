package com.venera.compose.components.venera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * 提示的**严重度**。三档，不是三种样式 —— 它同时决定底色、图标与"这条该不该有个动作"。
 *
 * - [Error]：**这一笔取数失败了**（超时、401、解析不出来）。用户能做的事是重试。
 * - [Warning]：**能用，但缺一块**（某一站没配账号所以整站发不出车）。用户能做的事是去配。
 * - [Info]：**本来就没什么可做的**（这一站这轮没给内容、没有可用的推荐标签）。
 *   摆一个动作在这档上就是本仓明令禁止的假开关。
 *
 * ⚠️ 这一枚举**刻意与 `GalleryNoticeTone` 之类不并存**：它就是全仓唯一那枚"提示有多严重"。
 * 加第二枚同义枚举的代价是两处各判一次，而判据分叉不会报错，只会让同一条原因在两张屏上
 * 长成两个颜色。
 */
enum class VeneraNoticeTone { Info, Warning, Error }

/**
 * Venera 通用**提示块**（基础层）。
 *
 * ── 它替换的是什么 ──
 *
 * 从前全仓的"屏上少了一块"全靠一行 `Text(fontSize = caption, color = textSecondary)` 交代：
 * 画廊首页那一站没配账号的长句、搜索卡里那条腿超时、日榜二级页的缺席读数，**都是同一种灰**。
 * 于是三类完全不同的处境在屏上长得一模一样，只能靠把字读完才分得清 —— 而其中一类
 * （"这一站用不了，去设置里填两个字段"）本来就带着一个用户**现在就该做**的动作，
 * 那行灰字里却没有任何可以按的东西。
 *
 * 这个组件把三件事分开承担：
 * 1. **层级**：[title] 一行说"是什么事"，[message] 一段说"为什么 / 要怎么办"。
 *    字号与字重同时拉开（body/semibold vs caption/regular），不靠调透明度 ——
 *    alpha 拉出来的层级在深色档下会把正文压到 4.5:1 以下（WCAG AA 正文门槛）。
 * 2. **严重度**：[tone] 决定底色与图标，三档在屏上一眼可辨。
 * 3. **出口**：[actionLabel] + [onAction] 同时非空才画那枚动作。**这是硬约束**：
 *    画了按不动、或按下去什么都不发生的按钮，比不画更坏（它同时在暗示一个不存在的出路）。
 *
 * ── 为什么底色用"容器色对"而不是自己调 alpha ──
 *
 * 块底与块上的字必须是一对**由色板保证过对比度**的颜色。此前要在别处凑一个色块，
 * 只能写 `accent.copy(alpha = 0.12f)` —— 那个值在动态取色下与文字色的对比度没有任何保证，
 * 换一张壁纸就可能掉到读不出来。所以这一层直接吃 MD3 的四对容器槽位
 * （`error/errorContainer`、`tertiary/tertiaryContainer`、`surfaceContainerHigh`），
 * 见 `VeneraColorTokens` 那两处新增字段的头注。
 *
 * ⚠️ 它**不含任何业务语义**（不认识站点、搜索、画师）：把"哪条原因算哪一档"留给调用方。
 * 画廊那一侧的判定在 `gallery/ui/GalleryNotices.kt`，那里是纯函数、有单测。
 *
 * @param message 正文。**必填** —— 一句只有标题的提示说不清任何事。
 * @param title 标题（可空）。为空时退化成"图标 + 正文"一行，适合本来就只有一句话的提示。
 * @param tone 严重度；底色、图标、动作色全由它定。
 * @param actionLabel 动作文案；与 [onAction] **同时**非空才渲染。
 * @param onAction 动作回调。
 * @param icon 显式指定图标。一般不要传 —— 语义交给 [tone]；传了则以它为准。
 * @param compact **一行档**：只摆「图标 + 标题 + 出口 + 展开把手」一行，[message] 收到行下，
 *   点这一行才就地展开。给**内容区第一屏**用（画廊首页那面墙），因为：
 *
 *   ⚠️ 2026-10-14 手机档截图上读到的原样是 —— 两块提示各占 3~4 行，把
 *   「正在关注的画师 / 每日热门」整块推出首屏。文档式长段落直接铺在内容区，
 *   用户第一眼看到的是故障说明书而不是内容。
 *
 *   ⚠️ 但它**不是把正文删掉**：正文一个字都在，只是改成"点了才展开"。
 *   收起来的那一行同时给了展开把手（一枚箭头 + 读屏标签），所以这条路是可发现的；
 *   没有正文可展开时（`title` 为空）**不摆**那枚把手，也不挂点击 ——
 *   摆一枚按下去什么都不发生的把手就是本仓明令禁止的假开关。
 */
@Composable
fun VeneraNotice(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: VeneraNoticeTone = VeneraNoticeTone.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    val tokens = VeneraTokens
    val palette = noticePaletteOf(tone)

    // 只有标题或只有正文时那一行不留空位：`Row` 里摆一个空 Text 会让图标与动作之间凭空多一截。
    val hasTitle = !title.isNullOrBlank()
    val showAction = !actionLabel.isNullOrBlank() && onAction != null
    // 一行档里"有东西可展开"的全部条件：正文存在、且它不是那一行本身。
    val collapsible = compact && hasTitle && message.isNotBlank()
    var expanded by remember { mutableStateOf(false) }
    val showBody = hasTitle && message.isNotBlank() && (!compact || expanded)

    Surface(
        shape = RoundedCornerShape(tokens.shape.medium),
        color = palette.container,
        // Info 档的底色（surfaceContainerHigh）在浅色主题下与页面底色只差一档，
        // 描一道发丝线它才读得出"这是一块"。另两档的容器色本身已经分得开，再加描边就是两层装饰。
        border = palette.border?.let { BorderStroke(tokens.spacing.hairlineThin, it) },
        // 只有真的藏着东西时这一行才可按（见上面那条"假开关"约束）。
        modifier = if (collapsible) {
            modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "收起说明" else "展开说明") { expanded = !expanded }
        } else {
            modifier.fillMaxWidth()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = tokens.spacing.space6,
                    vertical = if (compact && !expanded) tokens.spacing.space4 else tokens.spacing.space5,
                ),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
            ) {
                Icon(
                    imageVector = icon ?: palette.glyph,
                    // 装饰性：这一档的意思由标题文字承担，读屏再念一遍图标等于同一件事说两次。
                    contentDescription = null,
                    tint = palette.onContainer,
                    modifier = Modifier.size(tokens.spacing.chipIconSize),
                )
                Text(
                    text = title?.takeIf { hasTitle } ?: message,
                    // 一行档把标题压在**一行**：多行标题 + 展开把手 = 这一行仍然吃掉整块高度。
                    fontSize = if (hasTitle) tokens.type.body else tokens.type.caption,
                    fontWeight = if (hasTitle) tokens.type.weightSemibold else tokens.type.weightRegular,
                    color = palette.onContainer,
                    maxLines = if (hasTitle) (if (compact) 1 else 2) else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (collapsible) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        // 语义由那一行的 `onClickLabel` 承担（读屏在那里念"展开说明/收起说明"），
                        // 这里再念一遍就是同一件事说两次。
                        contentDescription = null,
                        tint = palette.onContainer,
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                }
                if (showAction) {
                    // 动作走**实底胶囊**，而不是同色的文字：文字动作与标题同为 onContainer 时
                    // 读起来只是标题的续行，用户不会想到它能按。
                    //
                    // 配色由"容器色对"**反过来**用（底=onContainer、字=container），
                    // 所以对比度是色板那一对本身保证的，不依赖我在这里挑一个第三色。
                    //
                    // `Neutral` 变体是刻意的：它既不描边（Assist/Filter 会加一道 outline）
                    // 也不上玻璃（玻璃会把实底洗淡，而这枚底色的可读性正是它的全部意义）。
                    VeneraChip(
                        text = actionLabel,
                        onClick = onAction,
                        variant = VeneraChipVariant.Neutral,
                        containerColorOverride = palette.onContainer,
                        contentColorOverride = palette.container,
                    )
                }
            }
            if (showBody) {
                Text(
                    text = message,
                    fontSize = tokens.type.caption,
                    color = palette.onContainer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 一档严重度的四种取色。抽出来只为一件事：三档的取值在一处看全，别散进布局代码里。 */
private class NoticePalette(
    val container: Color,
    val onContainer: Color,
    val glyph: ImageVector,
    /** Info 档那道发丝描边；另两档为 null（理由见 [VeneraNotice] 里传 `border` 那处）。 */
    val border: Color?,
)

@Composable
private fun noticePaletteOf(tone: VeneraNoticeTone): NoticePalette {
    val colors = VeneraTokens.color
    return when (tone) {
        VeneraNoticeTone.Error -> NoticePalette(
            container = colors.errorContainer,
            onContainer = colors.onErrorContainer,
            glyph = Icons.Outlined.ErrorOutline,
            border = null,
        )

        VeneraNoticeTone.Warning -> NoticePalette(
            // 警告吃 tertiary 容器：MD3 里"需要留意但不是错误"的语义槽位就是它。
            // 刻意**不**为此新造一枚 warning 色 —— 那要么是一枚固定色（不跟壁纸取色，
            // 在动态主题下会与整屏脱节），要么是 primary 的变体（那就分不出"警告"与"主行动"）。
            container = colors.tertiaryContainer,
            onContainer = colors.onTertiaryContainer,
            glyph = Icons.Outlined.ReportProblem,
            border = null,
        )

        VeneraNoticeTone.Info -> NoticePalette(
            container = colors.surfaceContainerHigh,
            onContainer = colors.textPrimary,
            glyph = Icons.Outlined.Info,
            border = colors.outlineVariant,
        )
    }
}
