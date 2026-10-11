package com.venera.desktop.gallery.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.Key
import com.venera.desktop.gallery.data.DesktopReaderMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 第八十七批：阅读那一屏「翻页那一档」的判据面。
 *
 * ## 这一族的档位与算式为什么在桌面留了一份
 *
 * 对岸那两件（`reader/ComicPageSource.kt` 的档位表与 `reader/ReaderInteractionPolicy.kt` 的翻页算式）
 * 零 Android 依赖，本该直接进桌面编译面；挡住它的是受保域里那行 UI 禁串（逐字说明在
 * `gallery/data/DesktopReaderMode.kt` 与 `desktop/build.gradle.kts` 的注释里，本批不动 `app/`）。
 * 于是桌面自持一份同轨件，而**三处口径由尺子从对岸源码文本现取再比**：
 * 键值表、归一别名表、跨页与钳位那两枚数（见 `DesktopReaderPagedSeedTest`）。
 *
 * 那份算式管的是三件事，每一件都对应一次已经量过的病：一次翻页跨几页（双页那一档一组两页）、
 * 目标页先对齐到组首、**翻不动交回 null 而不是原页**。最后那一条不是风格问题：
 * 交回原页时调用方会以为翻成了，页码、进度、落库那一笔跟着动一格假进度。
 *
 * ## 今天只有三档，档位表整颗留着
 *
 * `HORIZONTAL_CONTINUOUS`（横向连续）与 `DOUBLE_PAGE`（对开双页）**本批不做**，所以那一排里不摆它们 ——
 * 摆一枚按不动的档位就是本仓最忌的假开关，缺口记在 rounds 那份的末节。
 * 屏上念的名字取自 `DesktopReaderMode.label`（逐字照对岸那张表，由回核尺子钉着），桌面不另造一套词。
 */
internal val DESKTOP_READER_MODE_CHOICES: List<DesktopReaderMode> = listOf(
    DesktopReaderMode.VERTICAL_CONTINUOUS,
    DesktopReaderMode.HORIZONTAL_RTL,
    DesktopReaderMode.HORIZONTAL_LTR,
)

/** 这一档是不是"一屏一张"的那一类（整页翻），还是纵向连续流。 */
internal fun desktopReaderIsPaged(mode: DesktopReaderMode): Boolean =
    mode == DesktopReaderMode.HORIZONTAL_RTL || mode == DesktopReaderMode.HORIZONTAL_LTR

/** 页序反不反：只有日漫那一档从右往左。 */
internal fun desktopReaderReverses(mode: DesktopReaderMode): Boolean = mode == DesktopReaderMode.HORIZONTAL_RTL

/** 一次翻页跨几页：双页那一档一组两页（这一档本批没上屏，算式照对岸整颗留着）。 */
internal fun desktopReaderPageStep(mode: DesktopReaderMode): Int =
    if (mode == DesktopReaderMode.DOUBLE_PAGE) 2 else 1

/** 「往前一步」落到哪一页；`null` = 已经到头（调用方据此决定停不停、要不要念"到底了"）。 */
internal fun desktopNextPageIndex(current: Int, mode: DesktopReaderMode, lastIndex: Int): Int? {
    if (lastIndex < 0) return null
    val step = desktopReaderPageStep(mode)
    val target = desktopPairStart(current, step) + step
    return target.takeIf { it <= lastIndex }
}

/** 「往后退一步」落到哪一页；`null` = 已经在第一组。判据是组首而不是当前页码（对岸同一条）。 */
internal fun desktopPreviousPageIndex(current: Int, mode: DesktopReaderMode): Int? {
    val step = desktopReaderPageStep(mode)
    val target = desktopPairStart(current, step) - step
    return target.takeIf { it >= 0 }
}

/** 这一页所在组的组首：单页档就是它自己，双页档收回到偶数页。 */
private fun desktopPairStart(page: Int, step: Int): Int = if (step == 1) page else page - page % step

/**
 * 「往前/往后一步」落到哪一页；`null` = 翻不动了。
 *
 * `delta` 只取正负：对岸一次跨一组，**步数不是这一颗的参数** —— 让它能吃 3，
 * 调用点就能悄悄多翻两页而屏上没人对账。
 */
internal fun desktopReaderStepTarget(current: Int, delta: Int, mode: DesktopReaderMode, lastIndex: Int): Int? =
    if (delta >= 0) desktopNextPageIndex(current, mode, lastIndex) else desktopPreviousPageIndex(current, mode)

/**
 * 屏上那一句页号。
 *
 * - 空表不念（`""`）：源站回了 0 页时"第 1 / 0 页"是自相矛盾的读数，那一格由「源站这一话回了 0 页」那句单独说。
 * - 页号越界**钳到真实那一头**：拖动与落起点都可能递进一个此刻还没页表的数，念"第 0 页"或"第 400 / 89 页"都是假读数。
 */
internal fun desktopReaderPageLabel(page: Int, total: Int): String =
    if (total <= 0) "" else "第 ${page.coerceIn(0, total - 1) + 1} / $total 页"

/**
 * 这一屏**本次**用哪一档：取证种子顶过就用种子，否则用盘上那档；两种情形里
 * 落到的那一档若是本批没做的那两档之一，**照默认那一档摆**（不照它演一遍）。
 *
 * 抽成一颗件而不是留在 `main()` 里，是因为"接管落点"这类判断留在集成方就没有尺子（批 30 那一课）。
 * 种子**不写盘**（与批 83.1 同一口径）：无人值守那一趟不该把用户的档位挪走。
 * 会折回默认档的两条路都由 [desktopReaderModeSeedNote] 念出来 —— 折了而不说就是静默换档。
 */
internal fun desktopReaderModeOf(seed: DesktopReaderMode?, stored: DesktopReaderMode): DesktopReaderMode {
    val wanted = if (seed != null && seed in DESKTOP_READER_MODE_CHOICES) seed else stored
    return if (wanted in DESKTOP_READER_MODE_CHOICES) wanted else DesktopReaderMode.VERTICAL_CONTINUOUS
}

/**
 * 档位这一格对用户的实话；返回 `null` = 没什么要交代（没种子、盘上那档又做得了）。
 *
 * 三种情形各说各的，合成一句就说不清该改哪一边：
 * - 种子顶过而且做得了：说明这是本次装载顶的档，**盘上没改**。
 * - 种子顶的是本批没做的那档：说明它没生效、这一屏实际照哪一档摆。
 * - 没种子而盘上那档本批没做：说明屏上摆的与盘上存的不是同一档。
 */
internal fun desktopReaderModeSeedNote(seed: DesktopReaderMode?, stored: DesktopReaderMode): String? {
    val actual = desktopReaderModeOf(seed, stored)
    return when {
        seed == null ->
            if (stored == actual) null
            else "盘上那一档是「${stored.label}」，本批还没做 ⇒ 这一屏照「${actual.label}」摆（盘上没改动）"
        seed == actual ->
            "这一档是取证参数顶上的（盘上那档还是「${stored.label}」，没有改动）"
        else ->
            "取证参数顶的是「${seed.label}」，那一档本批还没做 ⇒ 这一屏照「${actual.label}」摆（盘上没改动）"
    }
}

/**
 * `D_按键` 那一句：把「这一枚键到没到这一层、落到了哪儿」念成一行可回读的读数。
 *
 * ## 为什么这一批交的是量具而不是音量键那条分支
 *
 * 音量键在桌面上只剩一条可能的路：AWT 的原始码（`Key.VolumeUp` 是一颗合成哨兵，真按键永远对不上它）。
 * 而"真按一次 Windows 会不会把 VK 175 送进这一层"本机一次都没量过（无 OS 级输入，手册 §8 那条硬边界），
 * 对岸那一枚偏好又**默认关**、桌面设置页也没有那一枚开关 —— 两头都缺的形状接上就是假开关。
 * 所以这一批只交读数：每一枚到得了这一屏的 `KeyDown` 都念一行，且把 `原始=` 一起念出来。
 *
 * ## 只有落到的键才念一句的话，会糊掉哪两种
 *
 * 「没落到」与「根本没送到这一层」会压成同一种哑巴，而这两件事的下一步完全不同。
 * `原始=` 那一格同理：有数 = AWT 那一层真送到了；念 `无` = 这一枚来自离屏合成注入（判据 ⑤ 那三条用例），
 * 两者混在一起就会把"我这儿的测试过了"读成"用户那儿能按"。
 *
 * 页号那一格不在这里重复念：落到了谁，`D_翻页` 那一行自己会说（两行念同一件事就是两本账）。
 */
internal fun desktopReaderKeyReading(key: Key, rawKeyCode: Int?, delta: Int?): String {
    val landing = when (delta) {
        null -> "不接"
        1 -> "后一页"
        -1 -> "前一页"
        else -> "步长 $delta"
    }
    return "D_按键 键=${key.keyCode} 原始=${rawKeyCode ?: "无"} 落点=$landing"
}

/**
 * 落起点那一格**本次**用哪一页：这一屏已经摆过一页就用那一页，没摆过才用种子折过的那一页。
 *
 * 为什么需要它（rounds §2.4，本批自己造出来的一条缺陷）：装载那一层的 `LaunchedEffect` 的键里
 * **有 `mode`**，所以点一下档位会整颗重跑。重跑时若再落一次起点，人在第 40 页就被甩回第 1 页，
 * 而盯梢那一笔跟着把第 1 页写进**两端共用**的 `comic_history` —— 手机侧"下次从第 40 页读"就这么被挪走了。
 *
 * `shown` 交的是**这一屏自己记的那一页**（由盯梢那一笔现取），不是"另一面墙现在的页号"：
 * 换档那一刻那面墙已经离开组合，读它拿到的是 stale 值或 0（本仓记过"不在组合里的那枚 state
 * 动手就是空操作"同一族），而写它更会把落点与盯梢排成死循环。
 */
internal fun desktopReaderCarriedPage(seedPage: Int, shown: Int?): Int = shown ?: seedPage

/**
 * 落起点那一格的总尝试上限（到顶就照实念 `没落到`，不把这一屏挂死在等上）。
 * 批 82.5 起就在那一屏，第八十七批随那颗共用件一起搬到这里 —— 两档共用同一个循环，上限也该只有一份。
 */
internal const val DESKTOP_READER_START_PAGE_TRIES = 12

/** 每一次尝试之后等页号落定的上限。 */
internal const val DESKTOP_READER_START_PAGE_WAIT_MS = 400L

/**
 * 把那一屏落到起始那一页，并交回那行自证读数。两档共用这一颗，各滚各的那面墙。
 *
 * 起点先在这里折一次（[desktopReaderStartPageIndex]）：页表还没回来时"第 2 页"根本不存在，
 * 折成 0 之后与现值相等，于是**一次都不滚** —— 落在一面还没造的墙上就是批 82.5 量到的那第一趟。
 *
 * 三条读数定下来的形状（不是猜的，见 `desktop-reader-start-page` 那份）：滚一次就交卷会念
 * `请求=3 量到=0 没落到`；"等量出非零尺寸再滚"照样没落到；隔一会儿再滚同一枚才落定
 * ⇒ 那一刻没法用一扇闸刻画，只能**有界重试**；到顶照实念 `没落到`，**永不**把请求值当读数。
 */
internal suspend fun desktopApplyStartPage(
    startPage: Int,
    pageCount: Int,
    observed: () -> Int,
    scroll: suspend (Int) -> Unit,
    settled: (Int) -> Flow<Int>,
): String {
    val target = desktopReaderStartPageIndex(startPage, pageCount)
    var observedNow = observed()
    var tries = 0
    while (observedNow != target && tries < DESKTOP_READER_START_PAGE_TRIES) {
        tries++
        scroll(target)
        observedNow = withTimeoutOrNull(DESKTOP_READER_START_PAGE_WAIT_MS) {
            settled(target).firstOrNull { it == target }
        } ?: observedNow
    }
    return desktopReaderStartPageReading(target, observedNow, pageCount, tries)
}

/** 纵向那一档：滚的是列表那面墙。 */
internal suspend fun desktopApplyReaderStartPage(
    listState: LazyListState,
    startPage: Int,
    pageCount: Int,
): String = desktopApplyStartPage(
    startPage = startPage,
    pageCount = pageCount,
    observed = { listState.firstVisibleItemIndex },
    scroll = { target -> listState.scrollToItem(target) },
    settled = { snapshotFlow { listState.firstVisibleItemIndex } },
)

/**
 * 整页翻那一档：滚的是 pager 那面墙。
 *
 * 单列一枚而不是让调用方"看着模式递不同的参数"：本仓记过同族的一条 —— 撤掉 pager 之后
 * `scrollToItem` 打在另一面墙上就是**空操作**，而它不报错、屏上只是不动。
 * 落定的那一页取 `settledPage` 而不是 `currentPage`：后者在拖动的半路上就已经换值了，
 * 那一格落进历史就是"人还没翻过去，进度已经记上了"。
 */
internal suspend fun desktopApplyReaderStartPagePager(
    pagerState: PagerState,
    startPage: Int,
    pageCount: Int,
): String = desktopApplyStartPage(
    startPage = startPage,
    pageCount = pageCount,
    observed = { pagerState.settledPage },
    scroll = { target -> pagerState.scrollToPage(target) },
    settled = { snapshotFlow { pagerState.settledPage } },
)
