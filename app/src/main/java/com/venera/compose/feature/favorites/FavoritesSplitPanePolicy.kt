// 收藏页「列表-详情」双栏（List-Detail）的判据层。
//
// 为什么判据要单独一颗文件而不是写在 FavoritesScreen 里：那颗文件是**冻结页面**
// （FREEZE-STATEMENT 第三批），且已有 1300+ 行。把「什么时候进双栏」这件事抽出来，
// 一来能像 WideScreenPolicy 那样被单测钉住，二来冻结文件里只留一处分支（compact 短路）。
//
// 口径来源：`docs/收藏页双栏分屏方案_2026-09.md` §1 的目标行为表。
// ⚠️ 那张表用的是 **MD3 官方断点 600/840**，与本仓 WideScreenPolicy 的 600/1300
// **不是同一把尺** —— 这不是笔误：双栏是 MD3 Adaptive 库的能力，库的
// `ListDetailPaneScaffold` 内部按它自己的 WindowSizeClass（600/840）决定摆几栏，
// 我们去对齐库的断点才不会出现「我们说双栏、库说不摆」的错位。
package com.venera.compose.feature.favorites

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** MD3 官方宽度断点（`material3-window-size-class`）。 */
private val CompactMaxWidth = 600.dp
private val MediumMaxWidth = 840.dp

/**
 * 收藏页在给定窗口宽下要不要进「列表 + 详情」双栏。
 *
 * - `< 600dp`（compact）→ **false**：走现结构，逐像素不变。
 *   这是方案 §4 的「零回归线」—— 手机档编译期短路，不进任何分栏代码路径。
 * - `>= 600dp` → true：左栏列表、右栏详情。
 *
 * 比较符取 `>=`：与库侧 WindowSizeClass 一致（它把 600dp 判进 medium）。
 * 本仓 `WideScreenPolicy` 那边是 `>`（照 master 的 changePoint），两处**故意不同**，
 * 因为对齐的第一方不同：导航形态对齐 Flutter master，分栏形态对齐 MD3 库。
 */
fun favoritesUsesSplitPane(windowWidth: Dp): Boolean = windowWidth >= CompactMaxWidth

/**
 * 双栏两栏各占多少重量（`列表 to 详情`）。
 *
 * 两条读数拼成这一条规则：
 * - 右栏**打开**时 `1 to 2` —— 与换自排两栏之前库给的几何一致（用户 2026-10-13：
 *   「目前这样确实可以」说的就是这一态）；
 * - 右栏**没内容**时 `3 to 0` —— 收藏页三档里「图片收藏 › 画廊收藏」那一档压根不会打开右栏，
 *   而上一版两栏永远各占一份宽，那一屏被永久压在 1/3 宽里（用户读数：「其他页面就挤成一团了」）。
 *
 * 详情那一份给 **0 而不是"很窄"**：0 让宿主直接不组合那一格，占宽与"一块空白板顶着"一起消失。
 */
internal fun favoritesPaneWeights(detailOpen: Boolean): Pair<Float, Float> =
    if (detailOpen) 1f to 2f else 3f to 0f

/**
 * 双栏下是否再出第三栏（左=收藏夹导航 / 中=列表 / 右=详情）。
 *
 * 方案 §3.3 的裁决：**expanded 才出第三栏**，medium 档收藏夹入口仍留在列表头部
 * （跟随库的 ListDetail 两栏语义，改动最小）。
 */
fun favoritesUsesThreePanes(windowWidth: Dp): Boolean = windowWidth >= MediumMaxWidth

/**
 * 右栏导航载荷的键分隔符：ASCII 单元分隔符（与 `ReadingStatsManager.TAG_SEPARATOR` 同一条理由）。
 *
 * 不用竖线也不用斜杠：源本子号里真会出现这两种（goda 那一档的号是带斜杠的 slug 形状），
 * 拿它们当分隔符就会把一本切成两段，或者串到别人的号上。
 */
internal const val DETAIL_KEY_SEPARATOR = "\u001F"

/**
 * 右栏载荷的键 = 源名 + 分隔符 + 本子号。
 *
 * 为什么载荷不能是 ComicItem 本体（2026-10-13 真机崩溃栈钉的）：
 * `rememberListDetailPaneScaffoldNavigator` 那台导航器是 **rememberSaveable** 存的，
 * 而 Android 侧只认「装得进 Bundle」的那些类型。平板上点开一本、再切到别的页面时，
 * `SaveableStateHolder` 在 dispose 里 `performSave`，当场抛
 * `IllegalArgumentException: item at index 1 can't be saved: ComicItem(...)`
 * （MuMu 上四笔 dropbox 同栈：`ListSaver.kt:41` ← `ThreePaneScaffoldNavigator.kt:503`）。
 * 手机档从来不带对象（`DetailRoute(comicId, sourceName)` + 壳槽位 `shell.selectedComic`），
 * 这里照同一形状走 —— 两栏问「哪一本」用的是同一把尺子，不是各写一份。
 */
internal fun favoritesDetailKey(sourceName: String, comicId: String): String =
    "$sourceName$DETAIL_KEY_SEPARATOR$comicId"

/**
 * 解出（源名, 本子号）。形状不对返回 null —— 调用方按「右栏没有选中项」处理，
 * 不许拿半截的号去开一本对不上号的本子。
 */
internal fun parseFavoritesDetailKey(raw: String): Pair<String, String>? {
    val idx = raw.indexOf(DETAIL_KEY_SEPARATOR)
    if (idx <= 0 || idx == raw.lastIndex) return null
    val sourceName = raw.substring(0, idx)
    val comicId = raw.substring(idx + DETAIL_KEY_SEPARATOR.length)
    return if (sourceName.isEmpty() || comicId.isEmpty()) null else sourceName to comicId
}
