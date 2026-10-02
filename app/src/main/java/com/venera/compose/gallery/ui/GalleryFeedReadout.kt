package com.venera.compose.gallery.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 画廊各面墙的**页尾读数片段**共用件（搜索 / 猜你喜欢 / 每日热门 / 画廊收藏四处）。
 *
 * 为什么只抽片段而不是抽整块页尾：四处的页尾**不是同一段话** —— 搜索那行报
 * 「"查询" 已摆出 N 张 · 排行 X · 第 P 页」，猜你喜欢报「按你的收藏已摆出 N 张（各站分布 …）」，
 * 每日热门报「某天的热门已全部显示（…）」，收藏墙又少一节。抽成一整块必然要改措辞，
 * 而措辞是各页各自定过的。真正逐字重复、且改一处就会让别处落后的，只有下面这三件：
 *
 * 1. 「命中屏蔽规则」那半句（4 处逐字相同）；
 * 2. 「按「成人内容处理」收起」那半句（3 处逐字相同）；
 * 3. 「正在取下一页 / 已经到底 / 上滑继续取 / 失败时不说」这四档互斥判据（2 处逐字相同）；
 * 4. 续页失败那一整块「下一页没取到：X + 一枚真能发请求的重试」（2 处逐字相同）。
 *
 * ⚠️ 第 3、4 件连起来是一条**假读数**的修法：取下一页失败之后页面进入"永远不再重试"，
 * 而页尾还写着"上滑继续取" —— 怎么滑都不会再发请求。所以失败那档必须同时做两件事：
 * 把状态串改成空串，并把失败原因与重试按钮摆出来。二者只做一个就是还在骗人，
 * 因此它们在这里成对出现。
 *
 * 本文件只做画廊侧。跨侧（漫画的图片收藏页）不共享这些字样：隔离口径要的是各侧自持。
 */

/** 「N 张命中屏蔽规则 甲、乙」。调用方自己决定前缀（「；另有 」或直接接在括号里）。 */
internal fun blockedByRulesFragment(count: Int, rules: List<String>): String =
    "$count 张命中屏蔽规则 ${rules.joinToString("、")}"

/** 「N 张按「成人内容处理」收起」。 */
internal fun ratingHiddenFragment(count: Int): String =
    "$count 张按「成人内容处理」收起"

/**
 * 页尾的取数状态半句。三档互斥，顺序**不能调**：
 * 正在取 -> 已到底 -> 失败（空串）-> 还能滑（「上滑继续取」）。
 * 失败档返回空串是有意的，别"顺手"补一句提示 —— 提示由 [GalleryLoadMoreRetryLine] 说，
 * 两头都说就成了两行重复读数。
 */
internal fun feedTailStatus(
    loadingMore: Boolean,
    exhausted: Boolean,
    hasLoadMoreError: Boolean,
): String = when {
    loadingMore -> "；正在取下一页"
    exhausted -> "；已经到底"
    hasLoadMoreError -> ""
    else -> "；上滑继续取"
}

/**
 * 续页失败那一段：说清是哪一笔失败，并给一枚**真能发出请求**的重试。
 * `loadMoreError` 为空时整段不渲染（调用方不用自己判空）。
 */
@Composable
internal fun GalleryLoadMoreRetryLine(
    loadMoreError: String?,
    onRetryLoadMore: () -> Unit,
) {
    if (loadMoreError == null) return
    val tokens = VeneraTokens
    Spacer(modifier = Modifier.height(tokens.spacing.space3))
    Text(
        text = "下一页没取到：$loadMoreError",
        fontSize = tokens.type.caption,
        color = tokens.color.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(tokens.spacing.space2))
    VeneraChip(
        text = "重试",
        variant = VeneraChipVariant.Assist,
        onClick = onRetryLoadMore,
    )
}
