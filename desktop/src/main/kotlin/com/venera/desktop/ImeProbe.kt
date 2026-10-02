package com.venera.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField

/**
 * S0-5 判据探针：中文 IME 在 Compose Desktop 上能不能用。
 *
 * 库层已核实（`ui-desktop:1.12.1`）：`InputMethodSession` 实现了 AWT 的
 * `java.awt.im.InputMethodRequests`，并给出 `getTextLocation(TextHitInfo)`、
 * `getInsertPositionOffset()`、`getCommittedText(...)`、`getImeComposingText()`，
 * 且带一个 Windows 专用的 `InputMethodEndCompositionWorkaround`。
 * 剩下四条只能由真 IME 会话给出：候选窗位置、可翻页、可上屏、composition 结束不吞字。
 * 每次文本变化都往 stdout 打一行 `IME_TEXT=`，便于事后对账。
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Venera R1-F / S0-5 中文输入法探针",
        state = rememberWindowState(width = 820.dp, height = 340.dp),
    ) {
        FluentTheme {
            ImeProbeContent()
        }
    }
}

@Composable
private fun ImeProbeContent() {
    var text by remember { mutableStateOf("前置文本让光标跑到中线附近：一二三四五六七八九") }
    LaunchedEffect(text) { println("IME_TEXT=$text") }

    Column {
        Text("Win+Space 切到微软拼音，在下面这行末尾接着打 jinman / 一长串拼音，看四件事：")
        Text("1 候选窗是否落在光标正下方 2 能否翻页 3 上屏后文字是否进框 4 空格确认后有没有吞字")
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("当前框内：$text")
    }
}
