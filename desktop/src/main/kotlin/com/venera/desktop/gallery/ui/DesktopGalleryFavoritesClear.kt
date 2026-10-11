package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryFavorites
import com.venera.desktop.desktopFailureFrame
import com.venera.desktop.desktopFailureType
import com.venera.desktop.desktopOneLineMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 画廊收藏那一屏的「清空」（批 77 · 上半）。
 *
 * ## 这一格是什么
 *
 * 共享面 `GalleryFavorites` 的三枚写动作 `toggle` / `removeAll` / `clear` 全在，桌面那份也逐枚转交了
 * （`gallery/data/DesktopGalleryFavorites.kt:24-25`），而屏上只接了 `toggle` —— 那一屏自己写着"没有清空入口"。
 * 收藏多了清不掉，而那份档是用户一张张攒起来的：**不可重建**。
 *
 * ## 为什么是"同一屏上两步"而不是弹层
 *
 * 桌面今天没有 `ContentDialogHost`（全仓只有 `SpikeWindow` 那枚探针用过 `FluentDialog`），
 * 为这一格去应用根新铺一层宿主是把两件事捆成一件。本仓已有的两处现成形状各说一件事：
 * 设置那一屏删屏蔽规则**明写"没有确认那一步"**（代价低、敲回来一遍就行），搜索历史那枚「清空」直接可点
 * （只是历史）。收藏不属于那两类 ⇒ 这一格自己实现"武装 → 再按才真清"那两步，
 * 而两件事都做成**纯函数**（[desktopFavoritesClearNext] / [desktopFavoritesClearLabel]）：
 * "第二按才是真清"这条纪律不该靠人眼点界面来确认。
 *
 * ## 三条口径，各由一颗判据钉着
 *
 * 1. **两档各一句且都点名条数**：不念条数的确认等于"确认一个未知量"。
 * 2. **条数变了就重新武装**（[desktopFavoritesClearNext]）：武装着去别处翻了一页、或别的进程往档里加了条目，
 *    再按下去删的就不是屏上刚才那批 —— 那一按只许把确认对准**当下**这个数。
 * 3. **清完读回存档还剩几条**（[desktopClearGalleryFavorites] 的 `left`）：只念"已清空"而读回非 0，
 *    就是把没生效说成生效；那一句由 [desktopFavoritesClearReading] 分成两句说。
 *
 * 成因那一行复用批 53 那三颗原子（类型、一行且脱敏、栈顶），不重写第二份。
 */

/** 「清空」那一枚走到哪一档。[FIRE] 不是状态，是这一按**该做什么**的决断。 */
internal enum class DesktopFavoritesClearStage { IDLE, ARMED, FIRE }

/** 按一次那颗之后该走哪一档：没武装只武装；武装着且条数没变才真清；条数变了就重新对准当下这个数。 */
internal fun desktopFavoritesClearNext(
    stage: DesktopFavoritesClearStage,
    countAtArm: Int,
    countNow: Int,
): DesktopFavoritesClearStage = when (stage) {
    DesktopFavoritesClearStage.IDLE -> DesktopFavoritesClearStage.ARMED
    DesktopFavoritesClearStage.ARMED,
    DesktopFavoritesClearStage.FIRE,
    -> if (countNow == countAtArm) DesktopFavoritesClearStage.FIRE else DesktopFavoritesClearStage.ARMED
}

/** 两档各一句，都点名条数（判据 ① 钉两两不许相同）。 */
internal fun desktopFavoritesClearLabel(stage: DesktopFavoritesClearStage, count: Int): String = when (stage) {
    DesktopFavoritesClearStage.IDLE -> "清空全部 $count 条"
    DesktopFavoritesClearStage.ARMED -> "确认清空这 $count 条？再按一次就删，删不了能找回来的只有源站"
    DesktopFavoritesClearStage.FIRE -> "正在清空这 $count 条…"
}

/** 摆不摆只看**当下这一屏的条数**：空档上摆一枚「清空全部 0 条」是最像真的假动作。 */
internal fun desktopFavoritesClearVisible(count: Int): Boolean = count > 0

/** 清成了带得出"说清了几条、读回还剩几条"；没成带得出成因与那一笔真的抛了的东西。 */
internal sealed interface DesktopClearOutcome {
    data class Cleared(val cleared: Int, val left: Int) : DesktopClearOutcome
    data class Failed(val reason: String, val error: Throwable) : DesktopClearOutcome
}

/**
 * 上屏那一句：三档各一句。
 *
 * ⚠️ `left != 0` 那一档**绝不出现「已清空」**三个字 —— 写口返回 `Unit`，"清没清干净"只有读回知道；
 * 读回非 0 而那句念"已清空"，就是本仓最忌的那类：把没生效说成生效。
 */
internal fun desktopFavoritesClearReading(outcome: DesktopClearOutcome): String = when (outcome) {
    is DesktopClearOutcome.Cleared -> if (outcome.left == 0) {
        "已清空 ${outcome.cleared} 条收藏"
    } else {
        "按下去说清了 ${outcome.cleared} 条，而存档里还剩 ${outcome.left} 条 —— 这一屏别信，去数据目录看一眼"
    }
    is DesktopClearOutcome.Failed -> "清空没成：${outcome.reason}"
}

/** 没成那一格的成因：一行且先过一遍凭据剔除；消息是空的就退回类型名。 */
internal fun desktopFavoritesClearFailureReason(error: Throwable): String =
    desktopOneLineMessage(error).ifBlank { desktopFailureType(error) }

/** 点击那一行读数：成功与没成都念得出"清了几条、读回几条"（还剩几条时不许念成皆大欢喜）。 */
internal fun desktopFavoritesClearReadout(outcome: DesktopClearOutcome): String = when (outcome) {
    is DesktopClearOutcome.Cleared -> "D_收藏清空 清=${outcome.cleared} 读回=${outcome.left}"
    is DesktopClearOutcome.Failed -> "D_收藏清空 清=0 读回=没读到 因=${outcome.reason}"
}

/** 没成**再多一行**：成因、异常类型、栈顶（拿不到栈就明说"没有"）。 */
internal fun desktopFavoritesClearRefusalReading(failed: DesktopClearOutcome.Failed): String =
    "D_收藏清空 未成 因=${failed.reason} 型=${desktopFailureType(failed.error)} " +
        "栈=${desktopFailureFrame(failed.error) ?: "没有"}"

/**
 * 真去清那一笔：走共享面那颗 `clear`，然后**读回**收藏流还剩几条。
 *
 * @param clearedHint 按下去那一刻屏上念着的条数 —— 读数里"清了几条"就是用户刚才看见的那个数，
 *   不是事后从档里现取的（事后取的话，中间有别处写过档，那一句就把两件事混成一件事）。
 *
 * 写与读回都在 IO 上：清档是写盘，而读回必须发生在写**之后**（同一线程顺序里）才作得准。
 */
internal suspend fun desktopClearGalleryFavorites(
    favorites: GalleryFavorites,
    clearedHint: Int,
): DesktopClearOutcome {
    val outcome = withContext(Dispatchers.IO) {
        val attempt = runCatching { favorites.clear() }
        val error = attempt.exceptionOrNull()
        if (error != null) {
            DesktopClearOutcome.Failed(desktopFavoritesClearFailureReason(error), error)
        } else {
            DesktopClearOutcome.Cleared(cleared = clearedHint, left = favorites.favorites.value.size)
        }
    }
    println(desktopFavoritesClearReadout(outcome))
    if (outcome is DesktopClearOutcome.Failed) println(desktopFavoritesClearRefusalReading(outcome))
    return outcome
}
