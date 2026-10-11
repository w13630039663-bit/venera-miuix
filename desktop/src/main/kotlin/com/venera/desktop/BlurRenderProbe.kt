package com.venera.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/**
 * S0-7 §六 欠的那一步：**应用内模糊在桌面上到底画不画得出来**（第六十二批，纯探针）。
 *
 * S0-7 量到的是 gate（`BlurEffect.isSupported() = true`、两枚 `isRuntimeShaderSupported()` 都真），
 * 而它自己写着「gate 说 true 不等于画面对」。这一颗把那句话补完：同一扇窗里摆**两枚同样的棋盘**，
 * 右边那一枚吃 `Modifier.blur` —— 高对比的 8×8 棋盘是最不留余地的图样（糊没糊一眼就读得出来，
 * 糊成一整片灰就是"生效"，两枚一模一样就是"没生效"）。
 *
 * ⚠️ 为什么走窗口而不是数值回读：`Modifier.blur` 是**渲染层**的事（graphicsLayer 上的 RenderEffect），
 * skia 层的 `ImageFilter.makeBlur` 数值探针量的是另一条路（原生库会不会算高斯），
 * 拿后者的"能"去答前者的"画不画"就是错引读数 —— 批 61 刚为这件事改判过一次（坑 ㊝）。
 *
 * 跑法：`sh gradlew :desktop:blurprobe`（开着不退，靠 `_qa/close_venera_windows.ps1` 收）。
 * 取证走 WGC 逐窗帧（`occlusionSafe=true`，锁屏也拍得到）。
 */
private const val RADIUS_DP = 24
private const val CELLS = 8

fun main() {
    val pid = ProcessHandle.current().pid()
    println("S07B_窗口 已开 pid=$pid 半径=${RADIUS_DP}dp 棋盘=${CELLS}x$CELLS 左=不糊 右=糊")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Venera 模糊渲染探针 #$pid",
            state = rememberWindowState(width = 760.dp, height = 420.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText("不糊（对照）", style = TextStyle(Color.White, 15.sp))
                    Board()
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText("糊（半径 ${RADIUS_DP}dp）", style = TextStyle(Color.White, 15.sp))
                    Board(Modifier.blur(radius = RADIUS_DP.dp))
                }
            }
        }
    }
}

/** 黑白棋盘：8×8 格、每格 20dp。画在两枚 `Board` 上的是同一颗函数，差别只有外面那层 modifier。 */
@androidx.compose.runtime.Composable
private fun Board(modifier: Modifier = Modifier) {
    Canvas(modifier.size((CELLS * 20).dp)) {
        val step = size.width / CELLS
        for (y in 0 until CELLS) {
            for (x in 0 until CELLS) {
                drawRect(
                    color = if ((x + y) % 2 == 0) Color.Black else Color.White,
                    topLeft = Offset(x * step, y * step),
                    size = Size(step, step),
                )
            }
        }
    }
}
