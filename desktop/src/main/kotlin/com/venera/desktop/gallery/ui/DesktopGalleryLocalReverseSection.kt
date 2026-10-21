package com.venera.desktop.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.preferredExternalUrl
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Frame
import java.io.File

internal class DesktopLocalReverseState {
    /** 挑中的那一句（闸口认下来才有；挡住时留 null 而把闸口那句交给落点那一档）。 */
    var pickedReading by mutableStateOf<String?>(null)

    var waiting by mutableStateOf(false)
    var outcome by mutableStateOf<DesktopReverseOutcome?>(null)

    /** 「打开来源」那句（成功时不摆，与浮层同一形状）。 */
    var notice by mutableStateOf<String?>(null)

    /**
     * 闸口那一颗：读数那一句要回答"这一笔到底发了没有、发的是哪一张"，
     * 所以它必须与发车用的**是同一颗**，不是事后凭 state 猜的。
     */
    var gate by mutableStateOf<DesktopLocalReverseGate?>(null)

    /**
     * 那一笔真抛过的异常。失败那第二行读数（型 + 栈顶帧）只能从**它**念，
     * 不能从被压成字符串的落点里猜 —— 猜出来的那一行等于什么都没念（判据 ⑬ 钉的是这一枚的用法）。
     */
    var pendingError by mutableStateOf<Throwable?>(null)
}

/**
 * 入口那一行：一枚按钮 + 挑中的那一句 + 种子那一条路。
 *
 * @param seedPath 取证种子（`--reverse-file=`）给的那一条路径。`null` = 本轮不自动发车（默认行为）。
 *   **必填、不给默认值**：漏传的形状是"参数打进去了而那一格永远不出现"，它不会报错 ——
 *   与批 36 那颗种子同一条理由（那一枚当年做成 compositionLocal 之后，判据全绿而屏上根本没动）。
 */
