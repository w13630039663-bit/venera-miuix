package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

internal sealed interface DesktopRankingState {
    data object Loading : DesktopRankingState

    /** 整条链出事，与"某一站没答上"是两句话。 */
    data class Failed(val message: String) : DesktopRankingState

    data class Ready(
        val batch: DesktopGalleryBatch,
        /** 这一轮**真发出去**的那一档（可能与用户点的那一档不同 —— 不同就必须出声）。 */
        val landing: GalleryRanking,
        /**
         * 这一批是**哪一期**取的那一句（`本期 2026-10-01 ~ 2026-10-07` / `2024-03 …`；不设窗口的档为空串）。
         *
         * 存句子而不是存锚点：它与 `fetchedAtMillis` 同一刻算出，于是那句话回答的是
         * "墙上这批是哪一期"，而不是"我现在把下拉停在哪一期"。
         */
        val periodReading: String,
        /** 发车前就没发的腿（Gelbooru 没配凭据那一档）。 */
        val silent: Map<GallerySite, String>,
        val fetchedAtMillis: Long,
    ) : DesktopRankingState
}

