@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    io.github.composefluent.ExperimentalFluentApi::class,
)

package com.venera.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import io.github.composefluent.component.*

/**
 * S0-1 + S0-2 判据探针：compose-fluent v0.1.0（拿 Compose 1.8.2 编的）在
 * CMP 1.12.1 / Kotlin 2.4.10 上，方案文档点名的 8 个组件能否**运行期**组合成功。
 *
 * 编译过不算数 —— 判据是 stdout 打出 `S02_COMPOSED_OK` 且日志里没有 `NoSuchMethodError`。
 * 真 Mica（S0-6）已量出 window-styler 0.3.2 静默失效，故本探针不挂它，保持日志干净。
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Venera R1-F / S0-2 组件探针",
        state = rememberWindowState(width = 720.dp, height = 560.dp),
    ) {
        FluentTheme {
            ComponentProbe()
        }
    }
}

@Composable
private fun ComponentProbe() {
    var query by remember { mutableStateOf("禁漫") }
    var checked by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(true) }
    val tooltipState = rememberTooltipState()

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("1 Text：compose-fluent 在 Compose Desktop 上")
        ProgressBar(0.4f)
        ProgressBar()
        Switcher(checked = checked, onCheckStateChange = { checked = it }, text = "2 Switcher")
        TextField(value = query, onValueChange = { query = it }, singleLine = true)
        TooltipBox({ Text("3 TooltipBox 触发器") }, tooltipState) { Text("悬停提示") }
        FlyoutContainer(flyout = { Text("4 Flyout 内容") }, initialVisible = true) {
            Text("4 Flyout 宿主")
        }
        MenuFlyout(visible = showMenu, onDismissRequest = { showMenu = false }) {
            MenuFlyoutItem(onClick = { showMenu = false }, text = { Text("5 菜单项") })
        }
        FluentDialog(visible = showDialog, content = { Text("6 Dialog") })
        NavigationView(
            menuItems = {
                menuItem(selected = true, onClick = {}, text = { Text("7 首页") }, icon = { Text("H") })
                menuItem(selected = false, onClick = {}, text = { Text("探索") }, icon = { Text("E") })
            },
        ) {
            Text("8 NavigationView 的 pane 内容区")
            LaunchedEffect(Unit) {
                println("S02_COMPOSED_OK")
            }
        }
    }
}
