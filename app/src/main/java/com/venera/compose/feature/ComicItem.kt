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

/**
 * 列表 / 搜索 / 收藏流中流转的漫画条目。
 *
 * ⚠️ 所有字段的默认值都必须是「空」：这里曾是硬编码的假数据
 * （`latestChapter = "第 128 话"`、`updateTime = "10分钟前"`、
 * `chapters = (1..60).map { "第 $it 话" }`），详情页在真实章节为空时会回退渲染它，
 * 结果任何无章节概念的源（EH 图库等）都会显示一份**凭空捏造**的 60 话目录。
 * 真实数据一律由源解析后显式传入，取不到就留空、由 UI 如实呈现。
 */
@Serializable
data class ComicItem(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val tags: List<String> = emptyList(),
    val rating: String = "",
    val description: String = "",
    val sourceName: String = "拷贝漫画",
    /** 最新章节标题；源未提供时为空串（UI 不显示占位符）。 */
    val latestChapter: String = "",
    /** 更新时间；源未提供时为空串。 */
    val updateTime: String = "",
    val hasUpdate: Boolean = false,
    /**
     * 列表接口给出的章节标题（**仅供列表展示**，不是可跳转的章节 id）。
     * 详情页的章节目录一律用源详情的 `chapterGroups` / `chapters`，绝不用它兜底。
     */
    val chapters: List<String> = emptyList(),
    /** Actual source-provided likes; never inferred from rating. */
    val likesCount: Int? = null
)

