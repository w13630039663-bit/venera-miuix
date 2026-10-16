package com.venera.desktop.gallery.ui

import com.venera.desktop.gallery.data.DesktopComicDeleteOutcome
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.data.desktopComicDeleteDownloadedChapter
import com.venera.desktop.gallery.data.desktopComicDeleteReading
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 批 98 · 「本机已下载」那一屏的**删除那两步**（屏上那一格的全部判读）。
 *
 * ## 为什么是"同屏两步"而不是弹层，也不是长按
 *
 * 桌面今天没有 `ContentDialogHost`（全仓只有 `SpikeWindow` 那枚探针用过 `FluentDialog`），为这一格去应用根
 * 新铺一层宿主是把两件事捆成一件 —— 批 77 收藏清空那一格已经把这条路走过一遍并留下现成形状：
 * **武装 → 再按才真删**，且两件事都做成纯函数（[desktopComicDeleteShouldFire] / [desktopComicDeleteNextKey]），
 * 因为"第二按才是真删"这条纪律不该靠人眼点界面来确认。
 *
 * 把手做成**可见的一颗**而不是长按：详情那一屏挂长按是因为整行已被「打开阅读」占了（桌面 `combinedClickable`
 * 没有 `onRightClick`，第二枚动作只能挤长按），而这一屏的行本来就没有主动作 —— 有主动作可让的位置就让，
 * 没得让才用长按。判据 ⑨ 钉这一颗文件里不出现 `onLongClick`。
 *
 * ## 与对岸不同轨的那两档（故意的，见数据层那颗件的 KDoc）
 *
 * 对岸 `LocalComicManager.kt:302-304` 丢掉 `deleteRecursively()` 的返回值再恒 `true`，于是"没删掉"也能说"已删除"。
 * 这一屏的四档各说一句，其中**三档不许出现「已删除」那三个字**（判据 ⑫ 钉着），
 * 而"到底删没删掉"由数据层删完**回读盘**定档，不吃任何返回值。
 */

/** 行里那一格的身份串（与 `LazyColumn` 的 key 同一颗算式，武装对准的就是这一行）。 */
internal fun desktopComicDeleteRowKey(chapter: DesktopComicDownloadedChapter): String =
    "${chapter.sourceKey}/${chapter.comicId}/${chapter.order}"

/** 没武装那一档的把手文案：点名删的是第几话（不点名的确认等于确认一个未知量）。 */
internal fun desktopComicDeleteIdleLabel(chapterNumber: Int): String =
    "删掉第 ${chapterNumber} 话的本机文件"

/** 武装那一档的把手文案：点名删的是第几话（不点名的确认等于确认一个未知量）。 */
internal fun desktopComicDeleteArmedLabel(chapterNumber: Int): String =
    "确认删掉第 $chapterNumber 话？再按一次就删，站方还在但要重下一遍"

/** 这一按该不该真删：只有"武装着的就是这一行"才删。 */
internal fun desktopComicDeleteShouldFire(armedKey: String?, rowKey: String): Boolean = armedKey == rowKey

/** 按一次之后武装那一格记谁：换了行就重新对准当下这一行。 */
internal fun desktopComicDeleteNextKey(armedKey: String?, rowKey: String): String = rowKey

/**
 * 上屏那一句（给用户看的，与 `D_删除` 那行读数是两句话）。
 *
 * ⚠️ 除 `Deleted` 之外三档**都不许说"删了"**：`Absent` 是没做过，`Rejected` 是没让做，`Failed` 是没做成 ——
 * 把这三档 any 一档说成"已删除"，就是本仓最忌的那一类：把没生效说成生效。
 */
internal fun desktopComicDeleteScreenLine(
    chapter: DesktopComicDownloadedChapter,
    outcome: DesktopComicDeleteOutcome,
): String {
    val which = "第 ${chapter.order + 1} 话"
    return when (outcome) {
        is DesktopComicDeleteOutcome.Deleted ->
            "删掉了${which}的本机文件（${outcome.pages} 页 · ${desktopByteLabel(outcome.freedBytes)}）"
        DesktopComicDeleteOutcome.Absent -> "${which}本来就不在本机里，没动任何东西"
        is DesktopComicDeleteOutcome.Rejected -> "${which}没让删：${outcome.reason}"
        is DesktopComicDeleteOutcome.Failed ->
            "${which}删不动：盘上还剩 ${outcome.leftFiles} 颗文件（${desktopByteLabel(outcome.leftBytes)}）没带走"
    }
}

/** 种子**命中**那一话时念的（与屏上那颗把手第二次按下同一档，只是归属换成种子）。 */
internal fun desktopComicDeleteSeedHitLine(comicId: String, chapterId: String): String =
    "D_删除种子 号=$comicId 话=$chapterId 落在=本机那一话（走屏上那颗把手第二次按下那一条路）"

/** 种子**落空**时念的：本机没有那一话。静默会把「我话号打错了」读成「删除坏了」（与下载那三档同一纪律）。 */
internal fun desktopComicDeleteSeedMissLine(comicId: String, chapterId: String): String =
    "D_删除种子 号=$comicId 话=$chapterId 落在=本机没有那一话（不删）"

/**
 * 真去删那一笔：走数据层那颗（回核 + 回读盘），读数念进 stdout。
 *
 * ⚠️ 这颗件住在**这一颗文件**而不是那一屏里：那一屏的判据 ④ 钉着"整颗文件只一处 `withContext(Dispatchers.IO)`"
 * （组合期碰盘就是这一族的老形状），删除这一跳再挤进同一颗文件就会把那条尺子顶成两句假话之一。
 */
internal suspend fun desktopComicDeleteChapter(
    root: File,
    chapter: DesktopComicDownloadedChapter,
): DesktopComicDeleteOutcome {
    val outcome = withContext(Dispatchers.IO) { desktopComicDeleteDownloadedChapter(root, chapter) }
    println(desktopComicDeleteReading(chapter, outcome))
    return outcome
}
