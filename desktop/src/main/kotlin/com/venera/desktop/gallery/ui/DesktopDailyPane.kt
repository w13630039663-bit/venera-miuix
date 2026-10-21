package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GelbooruCredentialState
import com.venera.compose.gallery.domain.GalleryDailyFeed
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import com.venera.desktop.gallery.data.DesktopDecoTier
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface DailyState {
    data object Loading : DailyState
    data class Failed(val reason: String) : DailyState

    /**
     * @param fetchedAtMillis **取数完成那一刻**的时刻戳，给状态栏那句「本次取数 X 前」。
     *   不是在渲染时现取的时刻 —— 那句话要回答的是"墙上这批图是哪一轮拿的"，
     *   而"渲染的这一刻"会随着每次重组往后挪，填进去就成了自指。
     */
    data class Ready(val daily: GalleryDailyFeed.Daily, val fetchedAtMillis: Long) : DailyState
}
