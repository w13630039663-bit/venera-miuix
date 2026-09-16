/**
 * S0-3 机械拆分自 MainActivity.kt（代码正文逐行原样搬运，未作改写）。
 */
package com.venera.compose.feature

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import com.venera.compose.reader.*
import com.venera.compose.data.db.*
import com.venera.compose.data.prefs.*
import com.venera.compose.source.model.*
import kotlinx.serialization.Serializable

@Serializable
data class ComicItem(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val tags: List<String>,
    val rating: String,
    val description: String,
    val sourceName: String = "拷贝漫画",
    val latestChapter: String = "第 128 话",
    val updateTime: String = "10分钟前",
    val hasUpdate: Boolean = true,
    val chapters: List<String> = (1..60).map { "第 $it 话" }.reversed()
)

