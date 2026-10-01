package com.venera.compose.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.venera.compose.components.VeneraEmptyTone
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.isWideScreen
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.SauceNaoClient
import com.venera.compose.gallery.data.SauceNaoHit
import com.venera.compose.gallery.data.preferredExternalUrl
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text

/**
 * 反搜结果，摆在**墙那一片**（不是新造一屏）。
 *
 * 为什么是列表而不是复用图片瀑布流：SauceNAO 的命中大半不是我们那两站的图 ——
 * 而是 pixiv / Twitter / 番剧截图，它们没有宽高比、没有分级字段，塞进瀑布流
 * 只会得到一排等高的方块。而且这一屏真正要读的是**相似度**与**作者**，
 * 那是两行文字，不是一个缩略图。
 *
 * 但命中里带 `yandere_id` / `gelbooru_id` 的那几条，就是我们已经能打开的那张图 ——
 * 所以那一行右侧给的是「在画廊打开」（走与一级点卡片**同一条**路：跨 Activity 才有
 * 实时 blur-behind 与预测式返回），只有对不上站的才退化成外链。
 */
@Composable
fun GalleryReverseResults(
    rvm: GalleryReverseViewModel,
    topPadding: Dp,
    allowNsfw: Boolean,
    onOpenPost: (GallerySite, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val imageLoader = remember { GalleryImageLoader.get(context) }

    // 新一轮落地 → 滚回顶部。用 generation 而不是 hits.size 当信号（两轮条数相同就观察不到），
    // 理由与搜索那侧一模一样，见 GalleryScreen 里 searchGeneration 那段。
    LaunchedEffect(rvm.generation) {
        if (rvm.generation > 0) listState.scrollToItem(0)
    }

    when {
        rvm.isSearching && rvm.hits.isEmpty() -> Box(
            modifier = modifier
                .fillMaxSize()
                .padding(top = topPadding),
            contentAlignment = Alignment.Center,
        ) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(tokens.spacing.space11),
                color = tokens.color.primary,
            )
        }

        !rvm.isSearching && rvm.error != null -> Box(
            modifier = modifier
                .fillMaxSize()
                .padding(top = topPadding),
            contentAlignment = Alignment.Center,
        ) {
            VeneraEmptyView(
                title = "这一张没搜成",
                message = rvm.error.orEmpty(),
                tone = VeneraEmptyTone.Failed,
                actionText = "再试一次",
                onAction = { rvm.submit(allowNsfw) },
            )
        }

        !rvm.isSearching && rvm.noResults -> Box(
            modifier = modifier
                .fillMaxSize()
                .padding(top = topPadding),
            contentAlignment = Alignment.Center,
        ) {
            // 「没有相似图」与"搜失败了"是两件事，必须分开说 —— 合成一句就分不清该重试还是该换图。
            VeneraEmptyView(
                title = "SauceNAO 说没有相似图",
                message = "这不是出错。换一张更清晰、更少水印的图，或者用大图页那张原图再试。",
                // 显式写 [VeneraEmptyTone.Nothing] 而不是靠默认值：这一档与上面那档
                // 屏上长得必须**不一样**（"没搜到"不是"没搜成"），写明才不会下次被顺手改回 Failed。
                tone = VeneraEmptyTone.Nothing,
            )
        }

        else -> LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = tokens.spacing.screenHorizontal,
                end = tokens.spacing.screenHorizontal,
                top = topPadding,
                bottom = VeneraSpacing.bottomBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
        ) {
            // **不给 key**。这一屏的条目没有稳定身份：SauceNAO 会在不同库里返回同一张缩略图
            // （`i.saucenao.com/...` 那条 URL 对两条命中是同一条），相似度也常撞，
            // 拿 `indexName + thumbnail + similarity` 当 key 会撞成重复 key —— 那是一当场崩。
            // 而这一屏没有插入 / 重排（一轮结果整片替换），key 买不到任何东西。
            items(rvm.hits) { hit ->
                SauceNaoHitRow(
                    hit = hit,
                    imageLoader = imageLoader,
                    onOpenPost = onOpenPost,
                    onOpenExternal = { url -> openExternally(context, url) },
                )
            }

            // 一屏到底要说出来。SauceNAO 一次只回这么多条、且没有可靠的深翻页，
            // 不写这一句，用户滚到底会以为"相似图就这么点"，而事实是"我们只取了前 N 条"。
            item {
                SearchNoticeLine("SauceNAO 一次只回前 ${SauceNaoClient.SAUCE_NUM_RESULTS} 条，到底了")
            }
        }
    }
}

@Composable
private fun SauceNaoHitRow(
    hit: SauceNaoHit,
    imageLoader: coil3.ImageLoader,
    onOpenPost: (GallerySite, Long) -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val tokens = VeneraTokens
    val wide = isWideScreen(LocalConfiguration.current.screenWidthDp.dp)
    val thumbSize = if (wide) 96.dp else 72.dp
    val ref = hit.siteRef

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = ref != null) { ref?.let { onOpenPost(it.site, it.id) } }
                .padding(tokens.spacing.space6),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
        ) {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .clip(RoundedCornerShape(tokens.shape.medium))
                    .background(tokens.color.surfaceVariant),
            ) {
                AsyncImage(
                    model = hit.thumbnailUrl,
                    contentDescription = null,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 相似度是这一屏唯一比标题更该先看见的数字，放最左。
                    Text(
                        text = "${hit.similarity}%",
                        fontSize = tokens.type.itemTitle,
                        color = similarityColor(hit.similarity, tokens),
                    )
                    if (hit.indexName.isNotEmpty()) {
                        Spacer(Modifier.width(tokens.spacing.space3))
                        Text(
                            text = hit.indexName,
                            fontSize = tokens.type.caption,
                            color = tokens.color.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    hit.part?.let {
                        Spacer(Modifier.width(tokens.spacing.space3))
                        Text(text = it, fontSize = tokens.type.caption, color = tokens.color.textTertiary)
                    }
                }

                hit.title?.let {
                    Spacer(Modifier.height(tokens.spacing.space2))
                    Text(
                        text = it,
                        fontSize = tokens.type.body,
                        color = tokens.color.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(tokens.spacing.space2))
                Text(
                    // 作者真的没有时念"作者未知"，不留一行空白让人以为还在加载。
                    text = hit.artistName?.let { "作者：$it" } ?: "作者未知",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(tokens.spacing.space3))
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6)) {
                    if (ref != null) {
                        // 这一条能开我们自己的大图页：收藏、屏蔽规则、防盗链、混淆全都现成。
                        Text(
                            text = "在 ${ref.site.displayName} 打开",
                            fontSize = tokens.type.caption,
                            color = tokens.color.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(tokens.shape.small))
                                .clickable { onOpenPost(ref.site, ref.id) }
                                .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space2),
                        )
                    }
                    // ⚠️ 用 [preferredExternalUrl] 而不是 firstOrNull：
                    // 站方给的 `source` 常是图片直链（pixiv 库尤其如此），
                    // 直接取第一条会跳到一张裸图而不是作品页（2026-09-27 真机反馈）。
                    preferredExternalUrl(hit.extUrls)?.let { url ->
                        Text(
                            text = if (ref != null) "原页" else "打开来源",
                            fontSize = tokens.type.caption,
                            color = tokens.color.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(tokens.shape.small))
                                .clickable { onOpenExternal(url) }
                                .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space2),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 相似度分档取色。
 *
 * 阈值 80 / 50 不是拍的：SauceNAO 自己把 80 以上当"基本是同一张"，
 * 50 以下多半只是构图或配色像。把 42% 也涂成主色会让人照着它去收藏，那是错的读数。
 */
@Composable
private fun similarityColor(similarity: Int, tokens: VeneraTokens): Color = when {
    similarity >= 80 -> tokens.color.primary
    similarity >= 50 -> tokens.color.textPrimary
    else -> tokens.color.textTertiary
}
