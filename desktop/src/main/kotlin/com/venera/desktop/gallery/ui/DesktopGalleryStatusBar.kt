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
