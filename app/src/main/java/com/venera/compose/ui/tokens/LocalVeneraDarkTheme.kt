package com.venera.compose.ui.tokens

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * "当前是否深色"的唯一读口 —— 从 `feature/VeneraTheme.kt:45` 搬来 `ui/tokens`（2026-10-20）。
 *
 * ## 为什么搬
 *
 * `components/` 那 48 颗里有 5 颗吃它（`VeneraAmbientBackground` / `VeneraFaceSheen` /
 * `VeneraGlass` / `VeneraShimmer` / `VeneraTopAppBar`），而 `components/` 是桌面复用大屏 UI
 * 的核心层。它原先声明在 `feature/VeneraTheme.kt` —— 那颗文件带 `android.app.Activity`、
 * `LocalView`、`WindowCompat`、`BusinessPorts` 等一堆 Android 绑定，于是 `components/` 因为
 * **一行 CompositionLocal 的 import** 被连坐，整层上不了桌面。
 *
 * 搬来 `ui/tokens` 之后：那颗目录零 Android 依赖（现量 7 颗、`import android.` 0 处），
 * 两端都能读，而 `feature/VeneraTheme.kt` 只是不再声明它、改成 import —— 语义一字未改
 * （provider 仍在 `VeneraTheme` 里，位置没动）。
 *
 * ## 为什么默认值是 `false`
 *
 * 沿用原值，不改成"跟随系统"：`VeneraTheme` 是唯一的 provider，它一定会在真机上把这个值
 * provide 出来；默认 `false` 只服务没挂 provider 的预览与离屏测试。
 * 改成跟随系统会让预览图在深色系统上突然变色 —— 那是"顺手改进"带来的观感漂移，不要。
 */
val LocalVeneraDarkTheme = staticCompositionLocalOf { false }
