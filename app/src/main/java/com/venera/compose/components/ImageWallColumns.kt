package com.venera.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * 图片墙的自适应列数 —— 四颗页共用的一次调用。
 *
 * 为什么要把这三行收进来（`LocalConfiguration` 取窗口宽 → `wideScreenLayoutMode` 定档 →
 * `imageWallColumnCount` 算列）：ba3e207 修的是「列数判据写死成两档」，
 * 但**判据的入参仍是四处各抄一遍** —— 画廊墙、每日推荐、画廊收藏、图片收藏各写一份同样的四行。
 * 后果不是难看：第三参 `VeneraSpacing.screenHorizontal * 2` 一旦某页改内边距、
 * 或那条 AUTO 覆盖链变动，四处要手工同步，而单测当时传的是**字面量 24.dp**
 * （今天恰好等于 12×2，所以改了 token 也不会红）⇒ 列数回归抓不到。
 * 现在入参由 [imageWallHorizontalPadding] 从 token 推导，四页共用一个 composable，
 * 测试与生产走同一个表达式。
 *
 * 不设参数是有意的：四处今天用的是同一个网格内边距。哪一页将来用别的内边距，
 * 让它直调 [imageWallColumnCount] 显式传，不要靠一个「漏传也不报错」的默认值悄悄改掉别页。
 *
 * ⚠️ 与漫画侧的关系：这一把尺住在 `components/`（中性基建），画廊与漫画两侧都可以调它 ——
 * 隔离口径禁的是 `feature ↔ gallery` 互引，不是禁共用判据。ba3e207 的病根恰恰是
 * 「两侧各写一份列数判据」，把它拆回两份才是重犯。
 */
@Composable
fun rememberImageWallColumnCount(): Int {
    val windowWidth = LocalConfiguration.current.screenWidthDp.dp
    return imageWallColumnCount(windowWidth, wideScreenLayoutMode(windowWidth), imageWallHorizontalPadding())
}
