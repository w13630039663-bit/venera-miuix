package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryForYouMerge
import com.venera.compose.gallery.domain.GalleryLegGuard
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface DesktopForYouState {
    /** 正在取第一轮。 */
    data object Loading : DesktopForYouState

    /** 一条都发不出去（没收藏 / 标签全被滤掉），那句由计划那颗给。 */
    data class Blank(val reason: String) : DesktopForYouState

    /** 判据或合成出了事 —— 与"某一站没答上"是两件事，不许互相替。 */
    data class Failed(val message: String) : DesktopForYouState

    /**
     * 取到了。[batch] 里是**累计到这一轮第 page 页**的那一批，
     * 页尾那几句读数（发出去的串、没答上的站、剔掉了几张）全部从它念。
     */
    data class Ready(val batch: DesktopGalleryBatch) : DesktopForYouState
}

// 「下一页」那一笔的相位住在 DesktopGalleryMorePhase（批 23 收成一颗：它不进 DesktopGalleryBatch，
// 那是 UI 的等待形状，不是这一批的数据 —— 发现、单人页、最新三屏吃同一颗）。

internal const val DESKTOP_FOR_YOU_SNAPSHOT_LINE: String =
    "离开这一屏再回来还是这一批 —— 只有关掉程序再开才会重新抽，那一份快照桌面没有。"

/**
 * 页尾那几句读数。次序是刻意的：**先说抽到了什么串**（这一轮的身份），再说少了谁，最后才是计数。
 *
 * ⚠️ 每一句都是"有内容才说"，但**不许因为没内容就不留判据**：
 * 一站没答上时必须点名是哪一站、为什么 —— 双源混摆静默退化成单源是这一屏最难发现的一种错。
 */
