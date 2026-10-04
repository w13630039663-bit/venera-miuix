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
 * ## 这一颗是**三面墙共用**的，所以有三档读数被显式关掉
 *
 * 2026-10-04 之前它只服务日榜墙（唯一调用点）。收藏墙接进来后发现**有三句在不存在的语境里**：
 *
 * - **「本次取数 X 前」**：收藏读的是本机 `gallery_favorites.json`，**一次网都不发**。
 *   摆"取数"是编造。⇒ 收藏那一档传 [LOCAL_READ_LABEL]，那句话改成"本机存档读于 X 前"。
 * - **「每站 N 预算 · 超时那一站这一轮缺席」**：同上，那一站不参与这次读数。
 *   ⇒ 收藏那一档传 `budgetMs = -1`（[STATUS_NO_BUDGET_MS]），整条不念。
 * - **「N 站 · 都给内容」**（[statusTally]）：这一句的主语是"**站出勤**" ——
 *   日榜那边真的是几个站并发去取、谁给了内容谁被预算淘汰。收藏屏**没有"出勤"这回事**：
 *   存档里按站分了组，但没有任何一站"出了勤"。
 *   而空收藏那一档更荒唐：`perSite` 是空的，于是屏上会念「**0 站 · 都给内容**」——
 *   一句话同时宣称"零个站"和"全都给了内容"。⇒ 收藏那一档传 `siteTotal = -1`
 *   （[STATUS_NO_SITE_TALLY]），整条不念；站分布改由段头那句「本机存档 N 条 · 摆上屏 M 张 · 某某站 k 张」承担，
 *   那一句的主语是"存档"，在本屏是真读数。
 *
 * ⚠️ 三档都由**参数**表达而不是新造一件状态栏：那一行要改形状时（比如加一条"缓存命中"），
 * 三面墙各改一次就会分叉 —— 同一个毛病本仓已经吃过两回（页脚与 Hero 侧卡讲同一件事、
 * 弹层容器两处各养一套）。宁可加一个语义明确的可空参数。
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
 *
 * ## 位置上的一处差量，先说清楚
 *
 * 稿把 `.status` 放在 `.page` **之外**、`.main` 的底部（`.page` 自己有 `padding:0 24px`，
 * 而状态栏是另一套 `padding:0 12px` 的**全宽条**）。桌面今天没有 `.main` / `.page` 那层结构 ——
 * 内容区整块就是一颗 pane 件。所以调用方把它画在内容列的**最底部**：上面那张墙吃 `weight(1f)`，
 * 状态栏随之贴在窗底、不参与滚动 —— **视觉位置与稿等价**（都是"内容区底部那条全宽状态条"）。
 */
@Composable
internal fun DesktopGalleryStatusBar(
    siteTotal: Int,
    missingCount: Int,
    fetchedAtMillis: Long,
    budgetMs: Long,
    droppedLabel: String,
    modifier: Modifier = Modifier,
    /** 这一条读数的前缀。日榜给默认那句「本次取数」；收藏屏给 [LOCAL_READ_LABEL]。 */
    elapsedLabelPrefix: String = DEFAULT_ELAPSED_LABEL,
) {    // 心跳：把「本次取数 X 前」这句话重算。理由见类注释那一节 —— 没有它这句话会停在过去某一刻。
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
            // ⚠️ `siteTotal < 0` 是「这一屏没有站出勤这回事」，**整条不念**。
            // 收藏屏走的就是这一档：存档里按站分了组，但没有一站"出了勤"，
            // 而空收藏时 `perSite` 是空的 —— 不设闸的话屏上会念「0 站 · 都给内容」，
            // 一句话同时宣称零个站和全都给了内容。站分布由段头那句承担（主语是"存档"）。
            if (siteTotal >= 0) {
                StatusText(statusTally(siteTotal, missingCount))
            }
            StatusText("$elapsedLabelPrefix $elapsed")
            if (droppedLabel.isNotBlank()) {
                StatusText("已滤掉：$droppedLabel")
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                // ⚠️ `budgetMs < 0` 是「这一屏没有每站预算这回事」，**整条不念**而不是念成 "-1s"。
                // 收藏屏走的就是这一档：它读本机 JSON、不发出网，
                // 而末句那句「超时那一站这一轮缺席」摆在那儿是凭空指控一个不参与的站。
                // 宁可这一条读数缺席，也不要一句指向不存在的事故。
                if (budgetMs >= 0) {
                    StatusText("每站 ${budgetLabel(budgetMs)} 预算 · 超时那一站这一轮缺席（会明说出来）")
                }
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

/** 出网那几屏的默认前缀。日榜墙用这一句（它真的发了几笔请求）。 */
internal const val DEFAULT_ELAPSED_LABEL = "本次取数"

/**
 * 本机存档那几屏的前缀 —— 收藏屏用这一句。
 *
 * ⚠️ 单独一颗常量而不是让调用方自己拼字符串：这句话的**主语**（取数 / 读存档）
 * 是判据的一部分，谁在屏上念什么由这里一处定；散出去拼，下一处就会忘了改主语，
 * 而"读本机"被念成"取数"正是本仓最忌的那类**方向性**假读数（它让人以为数据会自己变）。
 */
internal const val LOCAL_READ_LABEL = "本机存档读于"

/**
 * 「没有站出勤这回事」的哨兵值。传它给 [DesktopGalleryStatusBar] 的 `siteTotal` ⇒ 首句整条不念。
 *
 * 与 [DesktopGalleryFavoritesPane.STATUS_NO_BUDGET_MS] 同一个理由：这个数在桌面侧
 * **只有一个含义**（"这一屏不涉及站"），而 `siteTotal` 是 `Int`，
 * 写裸 `-1` 会被下一个人读成"站数是负一"。
 *
 * 为什么收藏屏要关掉它：这一句的主语是"谁出勤了"，而收藏读的是**已经躺在本机**的存档 ——
 * 没有谁去取过。空收藏那一档尤其荒唐（`perSite` 为空 ⇒ 屏上念「0 站 · 都给内容」）。
 * 站分布不是没讲，是挪到段头那句「本机存档 N 条 · 摆上屏 M 张 · 某某站 k 张」去讲了。
 */
internal const val STATUS_NO_SITE_TALLY = -1

/** 顶边那条线的厚。稿 `.status{border-top:1px …}`（`:243`），因 box-sizing 要从 24 里扣出来。 */
private val STATUS_BAR_DIVIDER_HEIGHT = 1.dp
