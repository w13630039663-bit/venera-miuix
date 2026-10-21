package com.venera.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.venera.sharedprobe.Hello
import org.junit.Test

/**
 * A0 探针判据 3 的**进程内**那一半（零窗口，按手册的自验口径：不动鼠标、不抢前台）。
 *
 * 两条各盖一半，都要：`:desktop:sharedprobe` 证"这扇窗起得来、运行期不崩"，
 * 而"屏上真有 `probe` 那一行"在这里读语义树 —— 窗口捕获要人配合且不可复跑，这一颗不用。
 *
 * ⚠️ 断言里写的是**字面量** `"probe"`，不是 `PROBE_TEXT` 那颗常量：引用常量的话常量和界面一起漂
 * 也照样绿（本仓"两枚 chip 同一读数 = 假开关"那一族）。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopSharedProbeTest {

    @Test
    fun `共享模块 commonMain 那颗 Hello 在桌面 jvm 上组合出 probe 那一行`() = runComposeUiTest {
        setContent { Hello() }
        onNodeWithText("probe").assertIsDisplayed()
    }
}
