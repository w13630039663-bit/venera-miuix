package com.venera.compose.components

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraTagChip
import com.venera.compose.data.prefs.ComicListPreferences
import com.venera.compose.feature.searchVisibleTags
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared by all lists. Writes immediately; preference callbacks keep mounted screens in sync. */
@Composable
fun rememberComicListDisplayMode(): MutableState<String> {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { ComicListPreferences(context) }
    val current = remember(preferences) { mutableStateOf(preferences.displayMode) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            current.value = preferences.displayMode
        }
        preferences.registerListener(listener)
        current.value = preferences.displayMode
        onDispose { preferences.unregisterListener(listener) }
    }
    return remember(preferences, current) {
        object : MutableState<String> {
            override var value: String
                get() = current.value
                set(value) {
                    val normalized = normalizeComicDisplayMode(value)
                    current.value = normalized
                    preferences.displayMode = normalized
                }
            override fun component1(): String = value
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
}

/** Observe real source metrics without querying a source from a card or changing favorite schemas. */
@Composable
fun rememberCachedComicMetrics(sourceKey: String, comicId: String): State<com.venera.compose.data.prefs.CachedComicMetrics> {
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { com.venera.compose.data.prefs.ComicMetricsCache(context) }
    val state = remember(cache, sourceKey, comicId) { mutableStateOf(cache.get(sourceKey, comicId)) }
    DisposableEffect(cache, sourceKey, comicId) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state.value = cache.get(sourceKey, comicId) }
        cache.registerListener(listener)
        state.value = cache.get(sourceKey, comicId)
        onDispose { cache.unregisterListener(listener) }
    }
    return state
}

/** A neutral responsive body: the caller retains click, long-click, selection and menu actions. */
@Composable
fun ComicCardLayout(
    detailed: Boolean,
    modifier: Modifier = Modifier,
    cover: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (detailed) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(122.dp)) { cover() }
            Column(Modifier.weight(1f)) { content() }
        }
    } else {
        Column(modifier) {
            cover()
            content()
        }
    }
}

/** Numeric text only: the source did not supply a maximum, so do not invent a five-star scale. */
@Composable
fun ComicMetrics(rating: Double? = null, likesCount: Int? = null, modifier: Modifier = Modifier) {
    val text = comicMetricsText(rating, likesCount)
    if (text.isNotEmpty()) {
        Text(text = text, fontSize = 11.sp, color = MiuixTheme.colorScheme.primary, modifier = modifier)
    }
}

/**
 * 列表「单列行卡」的唯一形态：左封面 92dp（`listCoverWidth`）+ 右信息
 * （标题 2 行 / 副标题 / metadata 行 / 描述 2 行 / 标签 5 个最多 2 行）。
 *
 * 正文是从 `SearchScreen.SearchResultRowItem` **逐行搬来、未改任何尺寸与颜色**的，
 * 搜索页现在也调这个组件。之所以抽出来：收藏页两条路径（本地 / 网络）各自画了一套，
 * 同一页出现两三种卡片高度，改一处忘一处。收藏页要「和搜索页一致」，正解是共用一个
 * 组件而不是把配方抄一遍。
 *
 * @param coverModifier 封面共享元素转场用（`Modifier.sharedElement(...)`）；打码命中的卡不要挂。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComicRowCard(
    title: String,
    coverUrl: String,
    subtitle: String,
    description: String,
    tags: List<String>,
    mask: VeneraCoverMask,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    likesCount: Int? = null,
    rating: Float? = null,
    updateTime: String = "",
    tagLabel: (String) -> String = { it },
    coverModifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    VeneraCard(onClick = onClick, onLongClick = onLongClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6)) {
            Box(Modifier.width(tokens.spacing.listCoverWidth)) {
                VeneraCover(
                    url = coverUrl,
                    contentDescription = title,
                    mask = mask,
                    modifier = coverModifier,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = tokens.type.body,
                    fontWeight = tokens.type.weightMedium,
                    color = tokens.color.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ComicRowMetadata(likesCount = likesCount, rating = rating, updateTime = updateTime)
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        fontSize = tokens.type.overline,
                        color = tokens.color.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 标签放开到 5 个（网格小卡才是 2 个）：单列有整行宽度可用。
                // 承载用 FlowRow 而非 Row —— 5 个在 360dp 窄屏必超一行，Row 会直接裁掉；
                // overflow 留默认（Clip），省略号指示要额外交互态，这里不引入。
                val listTags = searchVisibleTags(tags, emptyList(), tagLabel)
                if (listTags.isNotEmpty()) {
                    Spacer(Modifier.height(tokens.spacing.space2))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                        maxLines = 2,
                    ) {
                        listTags.take(5).forEach { tag ->
                            VeneraTagChip(text = tag, onClick = null)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一行「N 赞 · ★ x · 更新时间」。
 *
 * 只渲染**确实存在**的字段；任一缺失即该段省略，全空则整行不挂载 ——
 * 绝不输出 0 likes / — / N/A / 空占位符。
 */
@Composable
fun ComicRowMetadata(likesCount: Int?, rating: Float?, updateTime: String) {
    val tokens = VeneraTokens
    val parts = buildList {
        likesCount?.takeIf { it > 0 }?.let { add(it.toString() + " 赞") }
        rating?.takeIf { it > 0f }?.let { add("★ " + it) }
        updateTime.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        fontSize = tokens.type.overline,
        color = tokens.color.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = tokens.spacing.space1),
    )
}
