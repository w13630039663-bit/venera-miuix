package com.venera.desktop.gallery.ui

import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.LocalFavoriteDatabase
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.desktop.desktopFailureFrame
import com.venera.desktop.desktopFailureType
import com.venera.desktop.desktopOneLineMessage
import com.venera.engineprobe.EngineSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把一本漫画交给本地收藏夹（批 76）。
 *
 * ## 这一格为什么单独抽出来
 *
 * 写链早就整条在：共享面的 `LocalFavoritesManager.addComic` 会**抛**（那一夹不存在）或返回
 * `false`（这本已经在里面），三句结果说明也逐字照 Android 的 `ComicCardContextMenu` 抄好了 ——
 * 唯一调用它的却是 `--autofav` 那段取证路径。于是屏上收藏夹进得去、看得到，**存不进东西**：
 * "实现了而界面没有入口"那一族里最刺眼的一格，因为用户点不到的那颗钮不会自己报错。
 *
 * 现在按钮与种子吃的是同一枚 [desktopAddComicFavorite]（判据 ④ 数出现次数）。两处各写一遍的话，
 * 种子跑通的那条路就不是用户按的那条路 —— 与批 36 的 `fireReverse`、批 54 的 `fireCopyImage` 同一形状。
 *
 * ## 三条口径，各由一颗判据钉着
 *
 * 1. **三态各一句**（[desktopComicFavoriteScreenReading]）："已在里面"与"没成"撞成一句，读起来就是
 *    "点了没反应"，而这正是本批要修的那个观感。
 * 2. **两端同词**：跨端 import UI 是禁的，所以句子在两处各存一份，由判据 ② 逐字回核句核 ——
 *    一侧改了另一侧会红，而不是各自漂。
 * 3. **读数只走批 53 那三颗原子**：成因可能带着站方地址（query 里常躺 `api_key`），
 *    压成一行与剔凭据都复用现成的，不重写第二份（[desktopComicFavoriteFailureReason]）。
 *
 * ⚠️ [desktopAddComicFavorite] 不收一颗现成的 manager 而收**懒装载那颗**：`getInstance` 会建两棵库，
 * 桌面今天可能一整天都不点漫画域 —— 开机装配等于白建（判据 ⑧ 钉的就是这一条）。
 * 同理，那颗懒取走 `Dispatchers.IO`：第一次按下会连带开库，落在主线程上就是整屏一顿。
 */

/** 按一次「收藏」之后的三种结果。 */
internal sealed interface DesktopComicFavoriteOutcome {

    /** 真写进去了。 */
    data class Added(val folder: String) : DesktopComicFavoriteOutcome

    /** `addComic` 返回 false —— 这本已经在里面。它与"没成"不是一件事，那句实话必须分开说。 */
    data class Already(val folder: String) : DesktopComicFavoriteOutcome

    /** `addComic` 抛了。今天已知的一因是那一夹不存在；[error] 原样带着，读数才念得出类型与栈顶。 */
    data class Failed(val reason: String, val error: Throwable) : DesktopComicFavoriteOutcome
}

/** 三句上屏的话（判据 ① 钉两两不许相同，判据 ② 钉这三句与 Android 那侧同词）。 */
internal fun desktopComicFavoriteScreenReading(outcome: DesktopComicFavoriteOutcome): String = when (outcome) {
    is DesktopComicFavoriteOutcome.Added -> "已收藏到「${outcome.folder}」"
    is DesktopComicFavoriteOutcome.Already -> "这本已经在「${outcome.folder}」里了"
    is DesktopComicFavoriteOutcome.Failed -> "收藏失败：${outcome.reason}"
}

/**
 * 没成那一格说得出为什么：**一行、且先过一遍凭据剔除**（复用批 53 那颗脱敏原子）。
 *
 * 消息是空的（`throw NullPointerException()` 那种）就退回类型名 —— 空着会被读成"读数件坏了"。
 */
internal fun desktopComicFavoriteFailureReason(error: Throwable): String =
    desktopOneLineMessage(error).ifBlank { desktopFailureType(error) }

/** 点击那一行读数：三态都念得出（"没跑到"与"跑到了而失败"在 stdout 上不许同一个样）。 */
internal fun desktopComicFavoriteReadout(
    sourceKey: String,
    cardId: String,
    outcome: DesktopComicFavoriteOutcome,
): String = "D_收藏 点击 源=$sourceKey id=$cardId -> ${desktopComicFavoriteScreenReading(outcome)}"

/** 没成**再多一行**可 grep 的东西：成因、异常类型、栈顶（拿不到栈就明说"没有"）。 */
internal fun desktopComicFavoriteRefusalReading(failed: DesktopComicFavoriteOutcome.Failed): String =
    "D_收藏 未成 因=${failed.reason} 型=${desktopFailureType(failed.error)} " +
        "栈=${desktopFailureFrame(failed.error) ?: "没有"}"

/**
 * 按一次「收藏这一本」：懒取到 manager → 写进默认那一夹 → 三态各回一句，并把两行读数念在 stdout。
 *
 * 字段逐条对应 Android 那侧同一枚 `FavoriteItem`（`title→name`、`cover→coverPath`、递下来的源 key→`sourceKey`）。
 * 桌面这颗 [EngineSession.ComicCard] 模型没带 `subTitle`/`tags`，那两枚就按 `FavoriteItem` 自身的
 * 空默认值落库，**不编**（判据 ⑤ 钉着这一条）——编出来的作者或标签将来会看着像真数据。
 *
 * 读数在协程外念：`println` 不是那棵库的一部分，放在 IO 块里只是把 stdout 也拖到别的线程上，
 * 取证那一路等的是这一行。
 */
internal suspend fun desktopAddComicFavorite(
    favorites: () -> LocalFavoritesManager,
    sourceKey: String,
    card: EngineSession.ComicCard,
): DesktopComicFavoriteOutcome {
    val outcome = withContext(Dispatchers.IO) {
        val folder = LocalFavoriteDatabase.DEFAULT_FOLDER
        val added = runCatching {
            favorites().addComic(
                folder,
                FavoriteItem(
                    id = card.id,
                    name = card.title,
                    sourceKey = sourceKey,
                    coverPath = card.cover,
                ),
            )
        }
        val error = added.exceptionOrNull()
        when {
            error != null -> DesktopComicFavoriteOutcome.Failed(desktopComicFavoriteFailureReason(error), error)
            added.getOrDefault(false) -> DesktopComicFavoriteOutcome.Added(folder)
            else -> DesktopComicFavoriteOutcome.Already(folder)
        }
    }
    println(desktopComicFavoriteReadout(sourceKey, card.id, outcome))
    if (outcome is DesktopComicFavoriteOutcome.Failed) println(desktopComicFavoriteRefusalReading(outcome))
    return outcome
}
