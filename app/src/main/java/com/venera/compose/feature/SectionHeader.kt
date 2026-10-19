/**
 * 分区标题（首页六分区与画廊首页共用）。
 *
 * 本轮 Token 化：字号 / 颜色 / 圆角 / 间距全部走 Token。
 * 「›」原为 20sp（明显过大，与 17sp 标题打架），已收敛到 type.chevron。
 */
package com.venera.compose.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun MiuixSectionHeader(
    title: String,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /**
     * 行尾那一段（画廊首页用它摆「换一批 / 查看全部」）。
     *
     * 给了 [trailing] 就**不画那枚「›」，整行也不再可点**：那一颗箭头表达的是"整行是一个入口"，
     * 而行里已经有按钮了 —— 两个动作打架，点标题与点按钮做得不是同一件事，
     * 用户却没法从外观上知道哪个能点、点了会去哪。
     */
    trailing: (@Composable () -> Unit)? = null,
    /**
     * 节头左边那枚**小图标**（2026-10-17 第十八轮，用户稿每个节头都带一枚）。
     *
     * 加性参数、默认 `null` ⇒ 其余调用点（漫画侧首页那几个分区）逐像素不变。
     * 尺寸与颜色**刻意写死在组件里**：图标是"这一节讲什么"的记号，不是按钮 ——
     * 各调用点自己挑大小的话，同一屏三节会长出三种粗细。
     */
    icon: ImageVector? = null,
    /**
     * 没有 [trailing] 时要不要画那枚「›」。给"这一节没有全量页"的地方关掉它：
     * 一枚画出来却点不动的箭头就是假把手（本仓反复修过的那一族）。
     */
    chevron: Boolean = true,
    /**
     * 标题**右侧**那一格（2026-10-17 第十九轮，用户「已关注和发现选择器放在画师标题的右侧」）。
     *
     * 加性参数、默认 `null` ⇒ 其余调用点逐像素不变。
     * 与 [trailing] 的分界：这一格是"**这一节看哪一半**"（属于标题自己），
     * [trailing] 是"**这一节能做什么**"（入口／动作）。两者贴在一起时，动作仍然贴右缘。
     */
    titleTrailing: (@Composable () -> Unit)? = null,
) {
    val tokens = VeneraTokens
    val clickable = onTap != null && trailing == null
    /*
     * 两种行尾 = 两种排法，这一格从前是**一档通吃**（恒 `SpaceBetween`）：
     * 「›」那一支要的是箭头贴屏幕右缘（它表达"整行是一个入口"，右缘那枚就是入口的把手），
     * 而 `trailing` 那一支摆的是**真按钮**（画廊首页的「查看全部」「换一批」与「另有 N 位」读数），
     * 贴到屏幕右缘就把"标题 + 动作"这一组拉到屏幕两头。
     *
     * 现量（`_qa/ui-verify-2026-10-15/section_header_gap.txt`，探针 `_qa/section_header_gap_probe.js`，
     * 同行右邻与标题右缘的间隙）：
     *  - 平板 1920px：「正在关注的画师」→「另有 2 位」**1642px ≈ 621dp**、
     *    「每日热门」→ 动作那枚 **1676px ≈ 634dp**；
     *  - 手机 1080px：同一排 **577 / 639 / 670px ≈ 220 / 243 / 255dp**（三节各一条）。
     * 两档都成立 ⇒ 这一格不是"平板专属适配"，改的是共用件本身（手机一起修，见 rounds §七那条口径声明）。
     * 动作贴标题才读得出"这两个东西是一组的"。
     */
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.shape.small))
            .then(if (clickable) Modifier.clickable { onTap?.invoke() } else Modifier)
            .padding(
                horizontal = tokens.spacing.space2,
                // 2026-10-17 第十九轮：纵向 6dp → 4dp。
                // 卡内衬抬到 12dp 之后，标题离卡顶 12+6=18dp、离卡左 12+4=16dp，上下比左右松；
                // 收到 4dp 后两个方向都是 16dp，标题才落在卡面的视觉中轴上。
                vertical = tokens.spacing.space2,
            ),
            // 行尾排法（2026-10-17 第十九轮，用户拍板「查看全部 贴右缘 + 箭头，两站都改」）：
            // `trailing` 那一支从前是 `spacedBy`（动作紧跟标题），本轮改成 `SpaceBetween` 贴右缘。
            // ⚠️ 旧注释里那条"动作贴标题才读得出这两个是一组的"（2026-10-15 探针量过
            // 1642px / 577px 两组读数）是**上一版的口径**，已被用户推翻 —— 稿上就是贴右缘的。
            // ⚠️ 这个件同时被漫画侧首页三个分区使用，改这里会连带它们，回归要一起跑。
            horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tokens.color.textSecondary,
                    modifier = Modifier.size(tokens.spacing.sectionHeaderIcon),
                )
                Spacer(modifier = Modifier.width(tokens.spacing.space2))
            }
            Text(
                text = title,
                fontSize = tokens.type.itemTitle,
                fontWeight = tokens.type.weightSemibold,
                color = tokens.color.textPrimary,
            )
            if (titleTrailing != null) {
                Spacer(modifier = Modifier.width(tokens.spacing.space4))
                titleTrailing()
            }
        }
        if (trailing != null) {
            trailing()
        } else if (chevron) {
            Text(
                text = "›",
                fontSize = tokens.type.chevron,
                color = tokens.color.textTertiary,
            )
        }
    }
}
