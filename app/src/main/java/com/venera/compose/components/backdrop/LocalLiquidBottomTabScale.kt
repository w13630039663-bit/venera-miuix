// SPDX-License-Identifier: Apache-2.0
//
// Ported from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass
// app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt
//
// 按下时图标一起放大：官方用 compositionLocalOf { { 1f } }（外层是 () -> Float），
// 让同一个比例值被三处渲染共享，避免把动画值提升成参数造成全量重组。
package com.venera.compose.components.backdrop

import androidx.compose.runtime.compositionLocalOf

val LocalLiquidBottomTabScale = compositionLocalOf { { 1f } }
