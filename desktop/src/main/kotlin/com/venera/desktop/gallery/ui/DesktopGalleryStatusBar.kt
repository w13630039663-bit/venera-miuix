package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.component.Text
import kotlinx.coroutines.delay

/**
 * 页面底部状态栏（稿 `.status`：CSS `:243-245`，HTML `:498-501`）—— 高 24、灰底、顶边一条描边。
 *
 * ## 位置上的一处差量，先说清楚
 *
 * 稿把 `.status` 放在 `.page` **之外**、`.main` 的底部（`.page` 自己有 `padding:0 24px`，
 * 而状态栏是另一套 `padding:0 12px` 的**全宽条**）。桌面今天没有 `.main` / `.page` 那层结构 ——
 * 内容区整块就是一颗 pane 件。所以这里把它画在 pane 列的**最底部**：上面那张墙吃 `weight(1f)`，
 * 状态栏随之贴在窗底、不参与滚动 —— **视觉位置与稿等价**（都是"内容区底部那条全宽状态条"）。
 *
 * ## 三条读数各有出处，**没有一条是装饰**
 *
 * | 读数 | 出处 | 为什么值得占一条状态栏 |
 * |---|---|---|
 * | 几站给内容 / 几站缺席 | `Daily.pools.size` 与 `Daily.failures.size` | 缺席站由墙上方那条缺席行**逐站点名 + 原因**，这里给**计数** —— 一眼看出"是不是只剩两站在撑" |
 * | 本次取数多久之前 | 取数完成那一刻的 `currentTimeMillis()` | 告诉用户这屏不是一个实时的东西；没有它就默认"刚刚"，而它是会变的 |
 * | 每站预算多少 | `GalleryDailyFeed.PER_SITE_TIMEOUT_MS` 换算 | 解释"为什么某一站会缺席"：是预算淘汰，不是坏了 |
 *
 * ⚠️ 「每站 12s 预算」那句不写死 `12`：由 [budgetLabel] 从 `PER_SITE_TIMEOUT_MS` 现算。
 * 将来调预算，状态栏自己跟上 —— 写死就是"界面念的和代码跑的不是同一个数"。
 *
 * ## 「X 前」为什么挂了一个 30s 的心跳
 *
 * 只看组合期的快照是**会说谎的**：取数之后如果不重组，"刚刚"会一直挂着，
 * 半小时之后它还在说"刚刚" —— 那不是旧读数，是**假读数**。所以这里按 [STATUS_BAR_TICK_MS]
 * 起一支心跳把这句话重算。**要撤掉心跳之前，先想清楚你打算退回到哪一种错。**
 *
 * ## 稿上被裁掉的一样：右侧那枚 `Ctrl K` 键帽
 *
 * 与标题栏同一个理由（见 `DesktopGalleryTopBar`）：桌面没有全局搜索快捷键链，
 * 画一枚按下去什么都不会发生的键帽，就是一枚假开关。
 */
@Composable
internal fun DesktopGalleryStatusBar(
    siteTotal: Int,
    missingCount: Int,
    fetchedAtMillis: Long,
    budgetMs: Long,
    droppedLabel: String,
    modifier: Modifier = Modifier,
) {
    // 心跳：把「本次取数 X 前」这句话重算。理由见类注释那一节 —— 没有它这句话会停在过去某一刻。
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(fetchedAtMillis) {
        while (true) {
            delay(STATUS_BAR_TICK_MS)
            tick += 1
        }
    }
    val elapsed = remember(fetchedAtMillis, tick) { elapsedLabel(fetchedAtMillis, System.currentTimeMillis()) }

    val metrics = DesktopGalleryMetrics
    Column(modifier.fillMaxWidth()) {
        // 顶边那条 1dp 分隔线（稿 `.status{border-top:1px solid var(--stroke)}` `:243`）。
        // ⚠️ 稿 `* { box-sizing:border-box }` 那枚 24px 是**含这条线**的，所以下面那行要减 1 ——
        //    不减就是 25，多出来那 1dp 会把内容区挤掉一格。
        Box(
            Modifier
                .fillMaxWidth()
                .height(STATUS_BAR_DIVIDER_HEIGHT)
                .background(DesktopTheme.SurfaceRaised),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(metrics.statusBarHeight - STATUS_BAR_DIVIDER_HEIGHT)
                .background(DesktopTheme.SidebarBackground)
                .padding(horizontal = metrics.statusBarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.statusBarGap),
        ) {
            StatusText(statusTally(siteTotal, missingCount))
            StatusText("本次取数 $elapsed")
            if (droppedLabel.isNotBlank()) {
                StatusText("已滤掉：$droppedLabel")
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                StatusText("每站 ${budgetLabel(budgetMs)} 预算 · 超时那一站这一轮缺席（会明说出来）")
            }
        }
    }
}

/** 状态栏里的读数一律这一档：稿 `.status{font-size:11.5px;color:var(--t3)}`（`:243`）。 */
@Composable
private fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = DesktopTheme.TextTertiary,
        fontSize = 11.5.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 「多久之前」这句话；<1 分钟念"刚刚"，再往上按 分钟 / 小时 / 天 三级。纯函数，便于单测。 */
internal fun elapsedLabel(fromMillis: Long, nowMillis: Long): String {
    val seconds = ((nowMillis - fromMillis) / 1_000L).coerceAtLeast(0L)
    return when {
        seconds < 60 -> "刚刚"
        seconds < 3_600 -> "${seconds / 60} 分钟前"
        seconds < 86_400 -> "${seconds / 3_600} 小时前"
        else -> "${seconds / 86_400} 天前"
    }
}

/**
 * 状态栏左侧那句站点读数。
 *
 * ⚠️ 缺席为 0 时**不念**"0 缺席" —— 「三站 · 3 给内容 · 0 缺席」读起来像还有第三个维度要说清，
 * 而"没有缺席"本来就该用一个词说完。缺席非空时两项都念，因为那时"几分几落差"才是重点。
 *
 * ⚠️ [siteTotal] 写的是**阿拉伯数字**而不是稿上的汉字「三」：站点数是从 `Daily.pools.size`
 * 得来的，要写成「三」就得先写一张 数字→汉字 的表（且将来加第四站时那张表还得有人记得补）。
 * 为了让 UI 念的字必定等于实际跑了几个站，这里取派生值；稿那枚「三站」是展示稿的手写文案。
 */
internal fun statusTally(siteTotal: Int, missingCount: Int): String =
    if (missingCount == 0) {
        "$siteTotal 站 · 都给内容"
    } else {
        "$siteTotal 站 · ${siteTotal - missingCount} 给内容 · $missingCount 缺席"
    }

/**
 * 预算的那个数：由 `GalleryDailyFeed.PER_SITE_TIMEOUT_MS` 现算，不写死。
 *
 * ⚠️ 走整除秒。今天是 12_000ms ⇒ 正好 12s；若哪天改成非整秒（比如 12_500），
 * 这里会**向下截断**成 12s —— 少说 0.5s 看着无害，但它属于"界面念的数比实际跑的小"。
 * 所以判据里钉了一条「预算必须是整秒」：改预算的那一次就会红，
 * 逼着这里要么改成带小数，要么承认那一端不该改。
 */
internal fun budgetLabel(budgetMs: Long): String = "${budgetMs / 1_000L}s"

/** 「X 前」的重算间隔。改大只会让这句话更旧，改小就是白费重组 —— 详见类注释。 */
private const val STATUS_BAR_TICK_MS = 30_000L

/** 顶边那条线的厚。稿 `.status{border-top:1px …}`（`:243`），因 box-sizing 要从 24 里扣出来。 */
private val STATUS_BAR_DIVIDER_HEIGHT = 1.dp
