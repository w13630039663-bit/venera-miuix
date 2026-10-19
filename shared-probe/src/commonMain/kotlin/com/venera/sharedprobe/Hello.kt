package com.venera.sharedprobe

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A0 探针唯一的共享 UI：方案文档 §6.2 那一颗逐字最小的 `Hello()`。
 *
 * 它的价值不在画了什么，在于**同一份 `.class` 同时出现在两端**：安卓侧 `:app` 拿到的是
 * `-android` variant，桌面侧 `:desktop` 拿到的是 `-jvm`/`-desktop` variant，而两边渲染的是这一颗。
 */
const val PROBE_TEXT: String = "probe"

@Composable
fun Hello() {
    MiuixTheme {
        Text(PROBE_TEXT)
    }
}
