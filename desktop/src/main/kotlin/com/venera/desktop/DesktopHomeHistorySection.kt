package com.venera.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.feature.HistoryCardHome
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 首页「历史记录」分区 —— 与安卓 `HomeScreen.kt:335-357` 对应。
 *
 * ## 为什么分区**恒定存在**（这是桌面侧的决定，不是抄安卓）
 *
 * ⚠️ **2026-10-21 更正**：本文件早先在这里引 `HomeScreen.kt:171-174` 说"安卓踩过这个坑"。
 * 那条引证是**错的**（架构评审 R5 指出，本喵复核成立）—— 那段注释在
 * `item(key = "stats")` **正上方**，讲的是**第 0 项**的锚位跳动；而历史在列表中部，
 * LazyList 锚的是首个可见项、不是第 0 项，同一机制不成立。
 * 更要紧：**安卓对历史就是条件渲染的**（`if (historyList.isNotEmpty()) { item { … } }`），
 * 它在这件事上没有"正解"可抄。留着那条引证会误导下一个人去改安卓。
 *
 * 真实的理由是**桌面自己的竞态**：
 * 历史数据由 `produceState(initialValue = DesktopHomeHistorySnapshot.Loading)` 起步，
 * 一次异步读之后才到值 ⇒ 这一格会经历「Loading → 有值 / 空」两态。若用 `if` 包 item：
 *   第一帧 item 不存在 → 第二帧 item 出现并插在列表中部 → **它下面的分区整片下移一次**。
 *
 * 所以桌面这里刻意**偏离安卓**：item 恒定存在，只在**内容**上判空。
 * 这是桌面的选择，安卓侧**一个字都不动**。
 * 空历史显示一行说明，而不是让整格消失。
 *
 * ## 数据怎么来
 *
 * `HistoryDao`（`data/db`，已在桌面编译面）。它是 `getHistory(comicId)` 单条 +
 * `historyFlow: StateFlow` 两条口 —— 首屏要列表，只能读 `historyFlow`，
 * 而它的初值是 `emptyList()`、由 `init { refresh() }` 异步填。
 * 这正说明「空」与「还没读到」同形，必须靠"格子恒定存在"来化解。
 */
@Composable
internal fun DesktopHomeHistorySection(
    history: List<HistoryRecord>,
    /** 读到没有（`false` = 还没读回来，与"读了但确实没有记录"要分开说）。 */
    loaded: Boolean,
    /**
     * 读失败时的成因。
     *
     * **必须有这一条形参**：`DesktopHomeHistorySource` 在读失败与 2s 超时两条路径都会
     * 填 `note`，但如果这里不接，屏上就落到 `else -> "还没有阅读记录…"` ——
     * 把「库打不开」谎报成「你没读过书」。那是本文件 KDoc 自己禁止的事，
     * 也是架构与安全两位独立点到的同一处（2026-10-21 评审 R3）。
     */
    note: String? = null,
    onOpen: (HistoryRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    // 与安卓同一口径：最多 4 张，横滑。
    val strip = remember(history) { history.take(4) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "历史记录",
            fontSize = tokens.type.sectionTitle,
            color = tokens.color.textPrimary,
        )
        Spacer(Modifier.height(tokens.spacing.space2))

        when {
            strip.isNotEmpty() -> Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
            ) {
                strip.forEach { record ->
                    HistoryCardHome(
                        record = record,
                        // 共享零件要 `ux`：内容守卫那条链在它上面。桌面没有 provider 时
                        // 会是 NoOp（遮罩恒 VISIBLE）—— 那个缺口由
                        // `DesktopUxServicesProvidedTest` 钉住，这里只管要。
                        ux = LocalUxPlatformServices.current,
                        onClick = { onOpen(record) },
                    )
                }
            }

            // 三档，顺序有讲究：
            //   ① note 非空 ⇒ 读**失败**了，念成因（不能说"正在读…"，那会停在假加载态）
            //   ② !loaded    ⇒ 还没读回来
            //   ③ 其余       ⇒ 读过了，确实没有记录
            note != null -> Text(
                text = note,
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
            )

            // 「还没读回来」与「读了，确实没有」是两件事，屏上要分开说。
            // 合成一句话就等于把"库还没开"说成"你没读过书"。
            !loaded -> Text(
                text = "正在读本地历史…",
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
            )

            else -> Text(
                text = "还没有阅读记录。读一本之后，这里会出现最近 4 部。",
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
            )
        }
    }
}
