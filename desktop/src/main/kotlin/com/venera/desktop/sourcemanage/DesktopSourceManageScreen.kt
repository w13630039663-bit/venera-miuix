package com.venera.desktop.sourcemanage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.contentMaxWidthFor
import com.venera.compose.components.venera.VeneraButton
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 桌面源管理屏 —— 对应安卓 `ComicSourceScreen`(1629) + `SourceSectionScreen`(604) + `SettingsScreen`
 * 里那几条与源/守卫相关的设置。
 *
 * ## 它顺手补了一个安全缺口
 *
 * 安全评审 H4 指出：`DesktopGalleryContentGuard.setNsfwMaskMode` **零调用点**
 * ⇒ 「即使 provider 挂对了，用户不改键值文件，遮罩也永远不开」。
 * 也就是说桌面上此前**没有任何地方能改分级遮罩档** —— 那条保护等于只能靠手改 json。
 *
 * 这一屏把那颗开关放出来。这是本轮"补界面"里少数**直接改善安全姿态**的一项，
 * 不是纯装饰。
 *
 * ## 与安卓的差别
 *
 * 安卓那两屏有源的增删、图标、排序拖拽、config 表单、导入导出。
 * 桌面这一批做的是**只读清单 + 启停**：
 * 源脚本是随包的 34 颗 js（不是用户装的），所以"删除"和"安装"在桌面上不成立 ——
 * 说清楚这一点，比画一个按不动的删除按钮好。
 */
@Composable
internal fun DesktopSourceManageScreen(
    mode: WideScreenLayoutMode,
    /** 源清单：key + 显示名 + 是否启用。 */
    rows: List<DesktopSourceRowUi>,
    /** 当前分级遮罩档（`OFF` / `BLUR` / `HIDE`）。 */
    maskMode: String,
    /** 当前是否屏蔽 AI 图。 */
    blockAi: Boolean,
    /** 读/写失败成因。非空照原话上屏。 */
    note: String?,
    onToggleSource: (key: String, enabled: Boolean) -> Unit,
    onMaskModeChange: (String) -> Unit,
    onBlockAiChange: (Boolean) -> Unit,
    /**
     * 回底栏那一格。
     *
     * 2026-10-21 补：这一屏是**二级页**（从探索顶栏进来的），而它此前没有 `onBack` ——
     * 顶栏只有「源管理」四个字，进去就出不来。入口接上之后这条是必须的，
     * 不然「有入口」只是「有一条单行道」。
     */
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val maxWidth = contentMaxWidthFor(mode)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
                .fillMaxSize()
                .padding(horizontal = tokens.spacing.screenHorizontal),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
            ) {
                // 与详情屏同一形状：返回是顶栏第一枚，不靠"用户猜 Esc / 猜侧栏"。
                VeneraButton(onClick = onBack) { Text("← 返回") }
                Text(
                    text = "源管理",
                    fontSize = tokens.type.screenTitle,
                    color = tokens.color.textPrimary,
                )
            }

            if (note != null) {
                Text(
                    text = note,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(bottom = tokens.spacing.space2),
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = tokens.spacing.space6),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            ) {
                // ── 内容守卫那一段 ──
                item {
                    SectionTitle("内容守卫")
                }
                item {
                    VeneraCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(tokens.spacing.space3)) {
                            Text(
                                text = "分级遮罩",
                                fontSize = tokens.type.body,
                                color = tokens.color.textPrimary,
                            )
                            Text(
                                // 这一句是安全评审要求的交代：OFF 是照安卓抄的默认值，
                                // 不是"桌面忽略了这条保护"。
                                text = "默认 OFF（与安卓同一默认值）。OFF 时命中项不打码 —— " +
                                    "只要这个档是 OFF，下面那两类内容就会直接铺出来。",
                                fontSize = tokens.type.caption,
                                color = tokens.color.textTertiary,
                            )
                            Spacer(Modifier.height(tokens.spacing.space2))
                            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2)) {
                                MaskChoice("OFF", "不遮", maskMode, onMaskModeChange)
                                MaskChoice("BLUR", "模糊", maskMode, onMaskModeChange)
                                MaskChoice("HIDE", "隐藏", maskMode, onMaskModeChange)
                            }
                        }
                    }
                }
                item {
                    VeneraCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space3),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "屏蔽 AI 图",
                                    fontSize = tokens.type.body,
                                    color = tokens.color.textPrimary,
                                )
                                Text(
                                    text = if (blockAi) "已开：AI 标记的图会被挡住" else "已关",
                                    fontSize = tokens.type.caption,
                                    color = tokens.color.textTertiary,
                                )
                            }
                            VeneraButton(onClick = { onBlockAiChange(!blockAi) }) {
                                Text(if (blockAi) "关掉" else "打开")
                            }
                        }
                    }
                }

                // ── 源清单那一段 ──
                item {
                    Spacer(Modifier.height(tokens.spacing.space3))
                    SectionTitle("源（${rows.count { it.enabled }}/${rows.size} 启用）")
                }
                item {
                    Text(
                        // 说清为什么没有"删除/安装"：这不是遗漏，是桌面的源不是用户装的。
                        text = "这 34 个源随包分发（`assets/sources/*.js`），不是用户安装的，" +
                            "所以这里只能启停、不能删除或新增。",
                        fontSize = tokens.type.caption,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.padding(bottom = tokens.spacing.space2),
                    )
                }
                items(rows) { row ->
                    SourceRow(row = row, onToggle = { onToggleSource(row.key, !row.enabled) })
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val tokens = VeneraTokens
    Text(
        text = text,
        fontSize = tokens.type.sectionTitle,
        color = tokens.color.textPrimary,
        modifier = Modifier.padding(top = tokens.spacing.space2),
    )
}

@Composable
private fun MaskChoice(
    value: String,
    label: String,
    current: String,
    onChange: (String) -> Unit,
) {
    val tokens = VeneraTokens
    VeneraButton(
        onClick = { if (current != value) onChange(value) },
        enabled = true,
    ) {
        // 当前档用括号标出来，而不是靠颜色 —— 颜色在不同主题下不可靠。
        Text(if (current == value) "· $label ·" else label, fontSize = tokens.type.caption)
    }
}

@Composable
private fun SourceRow(
    row: DesktopSourceRowUi,
    onToggle: () -> Unit,
) {
    val tokens = VeneraTokens
    VeneraCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = tokens.spacing.space3,
                vertical = tokens.spacing.space2,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    fontSize = tokens.type.body,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.key,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                )
            }
            Spacer(Modifier.width(tokens.spacing.space2))
            VeneraButton(onClick = onToggle) {
                Text(if (row.enabled) "停用" else "启用", fontSize = tokens.type.caption)
            }
        }
    }
}

/**
 * 源清单的一行（UI 模型）。
 *
 * 单独定义而不直接用 `DesktopSourceRow`（data 层那颗）：UI 不该依赖数据层的形状，
 * 否则数据层加一列，UI 会跟着红。
 *
 * ⚠️ `public` 是**被签名逼的**，不是随便挑的：壳 `VeneraDesktopLargeScreen` 是 public，
 * 它有一个 `readSources: () -> List<DesktopSourceRowUi>` 形参 ——
 * Kotlin 不许 public 函数暴露 internal 类型参数。
 */
data class DesktopSourceRowUi(
    val key: String,
    val title: String,
    val enabled: Boolean,
)
