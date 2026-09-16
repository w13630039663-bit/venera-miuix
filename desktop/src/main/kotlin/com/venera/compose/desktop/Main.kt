package com.venera.compose.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.venera.compose.desktop.ui.screens.MainScreen
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun main() = application {
    val windowState = rememberWindowState(width = 440.dp, height = 880.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Venera Compose - Windows 桌面版",
        state = windowState
    ) {
        MiuixTheme {
            MainScreen()
        }
    }
}
