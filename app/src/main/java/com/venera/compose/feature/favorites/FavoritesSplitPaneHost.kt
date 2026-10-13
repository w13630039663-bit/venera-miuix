package com.venera.compose.feature.favorites

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.venera.compose.feature.ComicItem
import com.venera.compose.feature.ReadTarget
import com.venera.compose.reader.ReaderSession

/**
 * 收藏页的「列表 + 详情」双栏宿主（2026-10-12，用户点名的 B 方案；2026-10-13 换成自排两栏）。
 *
 * ## 为什么单独一颗文件、而不是改 `FavoritesScreen.kt`
 *
 * `FavoritesScreen.kt` 是**冻结页面**（FREEZE-STATEMENT 第三批，1300+ 行）。
 * 把双栏外壳放在这里、由 `Navigation.kt` 的 `composable<FavoritesRoute>` 接入，
 * 换来的是**冻结文件零 diff** —— 列表那一屏原样复用，只是它的 `onSelect` 从
 * 「推壳 NavHost 的 DetailRoute」换成「打开右栏」。手机档走的是同一条原路，逐像素不变。
 *
 * ## 档位判据
 *
 * 收口在 [favoritesUsesSplitPane]（本包 policy 文件，有单测）。`< 600dp` 直接短路，
 * 不进任何分栏代码路径 —— 这是方案 §4 的「零回归线」。
 *
 * ## 为什么不用 `ListDetailPaneScaffold`（2026-10-13 改判）
 *
 * 上一版用的是 MD3 adaptive 的 `ListDetailPaneScaffold` + `rememberListDetailPaneScaffoldNavigator`。
 * 它有一条改不动的行为：**两栏永远各占一份宽**，右栏没内容时也是一块空白板 ——
 * 而收藏页有三档（网络 / 图片 / 本地），图片档里的「画廊收藏」根本不会打开右栏，
 * 于是那一屏被永久压在 1/3 宽里（用户读数：「其他页面就挤成一团了」）。
 *
 * 想让"右栏空着就不占宽"本来该改 directive，但 1.4.0-alpha03 这一版
 * `ListDetailPaneScaffoldDirective` 已经是 `PaneScaffoldDirective` 的别名，
 * 里面**只有分区数与首选宽，没有两栏比例**（javap 实证：`maxHorizontalPartitions` /
 * `horizontalPartitionSpacerSize` / `defaultPanePreferredWidth`，没有 `ratioOfPanes`）
 * —— 库给的比例拿不到，"列表:详情 = 1:2"这条定值就没法随状态改。所以这里自己排两栏：
 * 重量由 [favoritesPaneWeights] 给，右栏从 0 长到 2f 那一瞬用 `animateFloatAsState` 连着
 * 把列表推回去，观感与换宿主前一致（用户点名「目前这样确实可以」的是打开那一态）。
 *
 * ## 详情栏的作用域
 *
 * `AndroidComicDetailScreen` 的签名要一个 `AnimatedVisibilityScope`（封面共享元素用），
 * 而这一层不是 pane scope、不给它。里面那层**无转场的 `AnimatedVisibility`** 就是为造出这个
 * 作用域而存在 —— 转场由外层的重量动画负责，这一层不能自己再加动画。
 *
 * @param readTargetFor 插图收藏长按「从该页开始阅读」用的定位请求。走壳 ViewModel 的一次性槽位，
 *   与手机档同源。
 * @param onComicOpened 列表栏点开某本时**先把对象交回壳槽位**（`shell.selectedComic`），
 *   再让本宿主记住它的键。右栏要展示的那一本一律经 [detailComicFor] 现取 ——
 *   与手机档 `openComic` 同一纪律：**载荷里从不放对象**（原因见 [favoritesDetailKey] 上那段崩溃栈）。
 * @param detailComicFor 按 `(源名, 本子号)` 现取右栏那一本；槽位里没有同一条目时调用方给占位条目，
 *   详情页自己会去拉真数据（口径与 `resolveDetailComic` 逐字同）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.FavoritesSplitPaneHost(
    onStartLiveReading: (ReaderSession) -> Unit,
    onSearchTag: (tag: String, sourceName: String) -> Unit,
    onOpenCoverViewer: (url: String, title: String) -> Unit,
    readTargetFor: (comicId: String, sourceName: String) -> ReadTarget?,
    onComicOpened: (ComicItem) -> Unit,
    detailComicFor: (sourceName: String, comicId: String) -> ComicItem,
    /** 列表栏。参数是「打开某本漫画」——双栏档由本宿主接管为「填右栏」。 */
    listPane: @Composable (onOpen: (ComicItem) -> Unit) -> Unit,
    /** 详情栏。入参是选中的漫画、定位请求与它的 AnimatedVisibilityScope。 */
    detailPane: @Composable (
        comic: ComicItem,
        readTarget: ReadTarget?,
        detailVisibilityScope: AnimatedVisibilityScope,
        onBack: () -> Unit,
    ) -> Unit,
) {
    // 可保存的那一格**只装键串**：ComicItem 本体进不了 Bundle，Android 会在「点开一本再切页」
    // 的 dispose 里当场抛 `item at index 1 can't be saved`（真机四笔同栈，逐字理由在 favoritesDetailKey 上）。
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    // 收栏那一瞬右栏还在往 0 长回去，内容不许先空掉 —— 所以关掉时仍按上一本画。
    var lastDetailKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(detailKey) { if (detailKey != null) lastDetailKey = detailKey }
    val shownKey = detailKey ?: lastDetailKey

    val (listWeightTarget, detailWeightTarget) = favoritesPaneWeights(detailKey != null)
    val listWeight by animateFloatAsState(listWeightTarget, label = "收藏列表栏重量")
    val detailWeight by animateFloatAsState(detailWeightTarget, label = "收藏详情栏重量")

    // 返回语义（方案 §3）：右栏开着时先收右栏；收掉了才轮到外层壳（本页没有注册第二层）。
    BackHandler(enabled = detailKey != null) { detailKey = null }

    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(listWeight).fillMaxHeight()) {
            listPane { comic ->
                // 双栏档：不推壳 NavHost，改为填右栏（方案 §1「右栏原地滑出详情，列表不动」）。
                // 对象先交回壳槽位（与手机档 openComic 同一纪律），本宿主只记键。
                onComicOpened(comic)
                detailKey = favoritesDetailKey(comic.sourceName, comic.id)
            }
        }
        if (detailWeight > 0f) {
            Box(modifier = Modifier.weight(detailWeight).fillMaxHeight()) {
                val comic = shownKey?.let { parseFavoritesDetailKey(it) }
                    ?.let { (sourceName, comicId) -> detailComicFor(sourceName, comicId) }
                if (comic == null) {
                    FavoritesDetailPlaceholder()
                } else {
                    // 无转场：只为拿一个 AnimatedVisibilityScope 交给详情页的共享元素。
                    // ⚠️ 全部按位置传、不用尾随 `{}`：`detailPane` 的第 3 个形参正是
                    // `AnimatedVisibilityScope`，尾随 lambda 会被 Kotlin 当成它来吃，
                    // 于是最后一个形参缺参。Kotlin 又不许对 function type 参数用命名参数。
                    // ⚠️ 写全限定名：外层是 `Row { }`，`RowScope` 自己也带一枚同名的
                    // `AnimatedVisibility`（那是"整栏跟着显隐"的另一件事），隐式接收者会挑中它。
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = EnterTransition.None,
                        exit = ExitTransition.None,
                    ) {
                        detailPane(
                            comic,
                            readTargetFor(comic.id, comic.sourceName),
                            this,
                        ) {
                            detailKey = null
                        }
                    }
                }
            }
        }
    }
}

/**
 * 右栏**没被任何一本占住时**摆的那一块。
 *
 * 自排两栏之后这一格只在"收栏动画进行中的那一两帧"里露脸（重量已经 >0 而键已经清空）。
 * 它仍然是一块中性底：方案 §3.2 的「进页不默认打开第一条」现在由"右栏压根不占宽"实现，
 * 而不是靠一块空板顶着。
 */
@Composable
private fun FavoritesDetailPlaceholder() {
    Box(modifier = Modifier.fillMaxSize())
}
