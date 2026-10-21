package com.venera.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

/**
 * 跨端的**窗口宽**来源 —— 大屏 UI 上桌面的唯一一道闸门。
 *
 * ## 为什么需要这一层
 *
 * `LocalConfiguration` 是 AndroidX 的件，在桌面侧的 Compose Multiplatform 里**没有**
 * （2026-10-20 现量：`:desktop:compileKotlin` 报 `Unresolved reference 'LocalConfiguration'`）。
 * 于是此前 21 处 `LocalConfiguration.current.screenWidthDp.dp` 把整个大屏 UI 钉死在 Android 上：
 * 判据层（[wideScreenLayoutMode] 等）明明是纯函数、零 Android 依赖，
 * 可**取宽的那一句**在 Android 上，界面因此上不了桌面。
 *
 * 这一层把那句话收口成一处：**同一条调用式，两端各自供给**。
 *
 * ## 三个设计决定，每条都能被证伪
 *
 * **① 值是可空的 `Dp?`，`null` = "没显式供给"。**
 * 不是为了省事，是因为 `staticCompositionLocalOf` 的默认值 lambda **不是 `@Composable`**，
 * 里面读不了 `LocalWindowInfo` / `LocalDensity`（写了编译不过，已试过）。
 * 于是"默认值"这件事只能挪到读口 [LocalWindowWidthDp] 里做，Local 本身只存"有没有人供给"。
 *
 * **② 兜底读 `LocalWindowInfo.containerSize`，不读 `LocalConfiguration`。**
 * 这是写法 A（安卓零改、桌面真复用）能成立的关键：
 *  1. **两端都有** —— 已现量（`LocalWindowInfo` 过、`LocalConfiguration` 不过）。
 *     若兜底仍写 `LocalConfiguration`，这颗文件本身就进不了桌面编译面，收口等于没做。
 *  2. **语义正好是那句话要的** —— `WideScreenPolicy.kt:174` 的注释早就写着
 *     「宽度传**窗口宽**，不传物理屏宽：分屏与自由窗口下两者不等」。
 *     `containerSize` 就是窗口宽，比整块屏的 `screenWidthDp` 更贴合这条既有口径。
 *  3. **离屏测试里也成立** —— `runComposeUiTest` 的窗口有确定的容器尺寸。
 *
 * **③ Android 侧由 `provideLocalWindowWidth` 显式供给 `screenWidthDp`，值逐字不变。**
 * 所以手机档与平板档的现有观感一个像素都不动 —— 这是用户 2026-10-02
 * 「调的都是大屏版，别动手机版」那条硬约束的兑现方式。兜底分支在 Android 上一次都走不到。
 */
val LocalWindowWidth = staticCompositionLocalOf<Dp?> { null }

/**
 * 取窗口宽的**唯一读口** —— 大屏判据的入参一律从这里来。
 *
 * 存在的理由是"让它像 `LocalConfiguration` 一样好用"：调用点写
 * `wideScreenLayoutMode(LocalWindowWidthDp)` 与从前写
 * `wideScreenLayoutMode(LocalConfiguration.current.screenWidthDp.dp)` 一样短，
 * 于是"替换 21 处"这件事没有摩擦，不会有人因为嫌麻烦而就地读回 Android。
 *
 * 做成 `@Composable @ReadOnlyComposable` 的 val 而不是普通函数：
 * 与 `LocalConfiguration.current` 的用法形状完全一致，替换是纯文本级的。
 */
val LocalWindowWidthDp: Dp
    @Composable
    @ReadOnlyComposable
    get() = LocalWindowWidth.current ?: with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }

/**
 * Android 侧的供给口：把 `screenWidthDp` 挂上去，语义与替换前**逐值相同**。
 *
 * 为什么不直接在默认值里转发 `LocalConfiguration`：那颗件桌面没有，本文件就编不过
 * （理由见文件头 ②）。所以供给这件事必须发生在**平台侧**（Android 的 `MainActivity`），
 * 而共享面只留一个不依赖任何平台的兜底。
 *
 * @param windowWidth 本帧的窗口宽。Android 传 `LocalConfiguration.current.screenWidthDp.dp`。
 */
@Composable
fun ProvideLocalWindowWidth(
    windowWidth: Dp,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalWindowWidth provides windowWidth, content = content)
}
