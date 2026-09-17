package com.venera.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 漫画列表「双列网格 / 单列大卡」布局系统（S8，对齐原版 ComicTile 三模式中的
 * brief(双列) / detailed(单列) 与 ComicLayoutToggleButton）。
 *
 * 单列 detailed 对齐原版 _buildDetailedMode + _ComicDescription：
 * 左封面（高约 186dp，宽 = 高×0.68）+ 右侧标题(2行)/副标题(1行)/标签
 * Wrap 徽章/评分星/描述(2行)/语言徽章。双列 brief 保持封面在上标题在下。
 */

/** 单列大卡：对齐原版 ComicTile._buildDetailedMode。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComicTileDetailed(
    title: String,
    coverUrl: String,
    subtitle: String = "",
    description: String = "",
    tags: List<String> = emptyList(),
    rating: Double? = null,
    badge: String = "",
    onClick: () -> Unit,
    coverContent: (@Composable BoxScope.() -> Unit)? = null,
    /** S7 分级遮罩：外部传入 "VISIBLE"/"BLURRED"/"HIDDEN"（调用方用 ContentGuard 判定） */
    coverMaskState: String = "VISIBLE",
    likesCount: Int? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面：高 186dp，宽 = 高×0.68 ≈ 127dp（对齐原版 height * 0.68）
            Box(
                modifier = Modifier
                    .width(122.dp)
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.1f))
                    .then(if (coverMaskState == "BLURRED") Modifier.blur(18.dp) else Modifier)
            ) {
                if (coverContent != null) {
                    coverContent()
                } else {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 右侧信息区：对齐原版 _ComicDescription
            Column(
                modifier = Modifier.weight(1f).heightIn(min = 180.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = title.replace("\n", ""),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 10.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    ComicMetrics(rating = rating, likesCount = likesCount)

                    // 描述（有标签时 2 行，无标签 3 行，对齐原版 maxLines 规则）
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            maxLines = if (tags.isEmpty()) 3 else 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 15.sp
                        )
                    }

                    // 语言/类型徽章（对齐原版 badge Container）
                    if (badge.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .align(Alignment.End)
                        ) {
                            Text(text = badge, fontSize = 10.sp, color = MiuixTheme.colorScheme.primary)
                        }
                    }
                }

                // 标签占用右侧底部空间；内容较多时允许卡片增高，避免挤压或遮挡。
                if (tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        maxLines = 3
                    ) {
                        tags.take(10).forEach { tag ->
                            Text(
                                text = tag.substringAfter(':'),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 列表页 AppBar 的「单列/双列」切换按钮。
 * 对齐原版 ComicLayoutToggleButton：brief 显示 ViewAgenda（点击切单列），
 * detailed 显示 GridView（点击切双列）。
 */
@Composable
fun ComicLayoutToggleButton(
    displayMode: String,
    onToggle: (String) -> Unit
) {
    IconButton(onClick = { onToggle(if (displayMode == "detailed") "brief" else "detailed") }) {
        Icon(
            imageVector = if (displayMode == "brief") Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
            contentDescription = if (displayMode == "brief") "切换单列" else "切换双列",
            tint = MiuixTheme.colorScheme.onSurface
        )
    }
}
