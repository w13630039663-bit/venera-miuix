package com.venera.compose.feature

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.venera.compose.components.ComicCardLayout
import com.venera.compose.components.ComicMetrics
import com.venera.compose.data.api.BusinessPorts
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 追更条目卡：追更页与收藏页「更新」段共用同一套渲染。
 *
 * 两处各写一份的代价不是重复代码，而是"同一个条目在两个地方显示的信息不一样"：
 * 追更页有封面 + 评分，收藏页那段如果只写标题就会让人怀疑是不是两套数据。
 * 因此这里把行卡本体收成一颗，参数是条目字段而不是条目对象 —— 调用方原本就在传字段。
 */
@Composable
internal fun FollowUpdateRow(
    detailed: Boolean,
    rating: Double?,
    likesCount: Int?,
    title: String,
    cover: String,
    sourceKey: String,
    tags: List<String>,
    author: String,
    comicId: String,
    updateTime: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasNewUpdate: Boolean = true,
    /**
     * 紧凑档：手机/折叠屏的收藏页「更新」段用它（54×72 封面 + 两行信息）。
     * 追更页仍走 [detailed] 那套，因为那边没有"上下还有三段"的压迫感，卡可以铺开。
     */
    compact: Boolean = false,
) {
    // 内容守卫：BLUR 命中打码（sourceKey 直查源级预设），HIDE 由调用方列表过滤兜底。
    val guard = BusinessPorts.of(LocalContext.current).contentGuard
    val maskState = guard.maskStateFor(
        sourceKey = sourceKey,
        title = title,
        author = author,
        tags = tags,
        comicId = comicId,
    )
    val mask = if (maskState == "VISIBLE") VeneraCoverMask.Visible else VeneraCoverMask.Masked
    val subtitle = listOfNotNull(sourceKey, updateTime?.takeIf { it.isNotBlank() }).joinToString(" · ")

    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        if (compact) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                VeneraCover(
                    url = cover,
                    contentDescription = title,
                    shimmerWhileLoading = false,
                    mask = mask,
                    modifier = Modifier.width(54.dp).height(72.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = VeneraTokens.type.body,
                        fontWeight = VeneraTokens.type.weightSemibold,
                        maxLines = 2,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = VeneraTokens.type.caption,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 1,
                    )
                    AnimatedVisibility(
                        visible = hasNewUpdate,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Text(
                            text = "NEW",
                            fontSize = VeneraTokens.type.badge,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
        } else {
            ComicCardLayout(detailed = detailed, modifier = Modifier.padding(10.dp), cover = {
                VeneraCover(
                    url = cover,
                    contentDescription = title,
                    shimmerWhileLoading = false,
                    mask = mask,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            }) {
                Text(
                    text = title,
                    fontSize = VeneraTokens.type.body,
                    fontWeight = VeneraTokens.type.weightSemibold,
                    maxLines = 2,
                )
                ComicMetrics(rating, likesCount)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = VeneraTokens.type.caption,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 2,
                )
                // NEW 标记跟着数据走：检查完把 has_new_update 清掉后，它会收缩消失而不是硬跳
                AnimatedVisibility(
                    visible = hasNewUpdate,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Text(
                        text = "NEW",
                        fontSize = VeneraTokens.type.badge,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
