package com.venera.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.venera.sharedprobe.Hello
import kotlinx.coroutines.delay

/**
 * A0 探针的桌面入口（方案文档 §6.2 第 4 条 + §6.3 判据 3）。
 *
 * `:desktop:run` 今天指向的是真 App，所以这扇探针窗按 S0-2 的既有手法单开一个任务
 * （`spike` / `ime` / `mica` 都是同一族）：**跑的是同一颗 runtimeClasspath**，
 * 换的只是 main class —— 判据 3 要量的"桌面侧解析/运行得了吗"在这一颗上等价成立。
 *
 * 三行 stdout 读数各管一格：
 *  - `A0_SHARED_PROBE_START` —— 进程活着、依赖解析过了
 *  - `A0_SHARED_PROBE_CLASS_FROM=` —— `Hello()` 那颗粒是从哪个 jar 装载的
 *  - `A0_SHARED_PROBE_MATERIAL3_FROM=` —— 运行期 `androidx.compose.material3` 的类实际来自哪颗
 *    （这条是判据 4 在桌面侧的那一半：编译过了不代表运行期拿到的是同一颗 material3）
 *  - `A0_DESKTOP_COMPOSED_OK` —— 真组合成功，`NoSuchMethodError` 那一族不会走到这一行
 */
fun main(args: Array<String>) = application {
    println("A0_SHARED_PROBE_START jvm=${System.getProperty("java.version")}")
    println("A0_SHARED_PROBE_CLASS_FROM=${codeSourceOf("com.venera.sharedprobe.HelloKt")}")
    println("A0_SHARED_PROBE_MATERIAL3_FROM=${codeSourceOf("androidx.compose.material3.ColorScheme")}")
    val hold = args.firstOrNull { it.startsWith("hold=") }?.substringAfter("hold=")?.toLongOrNull() ?: 4_000L
    Window(
        onCloseRequest = ::exitApplication,
        title = "Venera A0 shared-probe",
        state = rememberWindowState(width = 420.dp, height = 260.dp),
    ) {
        Hello()
        LaunchedEffect(Unit) {
            println("A0_DESKTOP_COMPOSED_OK")
            delay(hold)
            exitApplication()
        }
    }
}

private fun codeSourceOf(fqcn: String): String =
    runCatching { Class.forName(fqcn) }
        .fold(
            { it.protectionDomain.codeSource?.location?.path ?: "no-code-source" },
            { "CLASS_NOT_FOUND(${it::class.java.simpleName}: ${it.message})" },
        )
