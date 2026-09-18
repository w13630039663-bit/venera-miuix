package com.venera.compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.foundation.background

/**
 * 评论富文本渲染：把源返回的 HTML 片段拆成「文本行」与「行内图片」。
 *
 * 很多源（JM / 拷贝漫画等）的评论含 <img src="..."> 表情或配图，纯 Text 会把
 * 标签原文暴露给用户。这里用轻量正则拆解 —— 不引入 HTML 解析器依赖：
 *  - <br> / <br/> / <br /> → 换行
 *  - <img src="...">      → Coil 行内表情图（最大高 80dp，圆角剪裁，加载失败静默隐藏）
 *  - 其余标签（<b>、<a> 等）剥壳保留纯文本
 */
@Composable
fun RichCommentContent(
    content: String,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val segments = remember(content) { parseCommentSegments(content) }

    Column(modifier = modifier.fillMaxWidth()) {
        for (segment in segments) {
            when (segment) {
                is CommentSegment.Text -> {
                    if (segment.text.isNotEmpty()) {
                        Text(
                            text = segment.text,
                            fontSize = tokens.type.caption,
                            color = tokens.color.textSecondary,
                        )
                    }
                }
                is CommentSegment.Image -> {
                    // 行内表情/配图：最大高 80dp，圆角剪裁，加载占位同封面规范
                    SubcomposeAsyncImage(
                        model = segment.src,
                        contentDescription = "评论图片",
                        contentScale = ContentScale.FillWidth,
                        loading = {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .background(StatusColors.BadgeSurface),
                            )
                        },
                        error = {
                            // 表情图加载失败静默隐藏（不占位不报错，不打断阅读流）
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.height(0.dp),
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 80.dp)
                            .clip(RoundedCornerShape(tokens.shape.small)),
                    )
                }
            }
        }
    }
}

/** 评论内容的渲染片段。 */
internal sealed interface CommentSegment {
    /** 一段纯文本（可能含换行符）。 */
    data class Text(val text: String) : CommentSegment

    /** 一张行内图片（表情 / 配图）。 */
    data class Image(val src: String) : CommentSegment
}

private val imgTagRegex = Regex("""<img\b[^>]*src\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
private val brTagRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val anyTagRegex = Regex("""<[^>]+>""")

/**
 * 拆解 HTML 评论：按 <img> 切块，块内 <br> 转换行、剥掉其余标签。
 * src 为空或 data:URI 的图不渲染。
 */
internal fun parseCommentSegments(content: String): List<CommentSegment> {
    if (content.isBlank()) return emptyList()
    val segments = mutableListOf<CommentSegment>()
    var cursor = 0
    for (match in imgTagRegex.findAll(content)) {
        val before = content.substring(cursor, match.range.first)
        appendTextSegments(before, segments)
        val src = match.groupValues[1].trim()
        if (src.isNotBlank() && !src.startsWith("data:")) {
            segments += CommentSegment.Image(src)
        }
        cursor = match.range.last + 1
    }
    appendTextSegments(content.substring(cursor), segments)
    return segments.filterNot { it is CommentSegment.Text && it.text.isBlank() }
}

private fun appendTextSegments(raw: String, out: MutableList<CommentSegment>) {
    if (raw.isEmpty()) return
    // <br> → 换行；剩余标签剥壳保留纯文本（<b>加粗</b> → 加粗）
    val normalized = brTagRegex.replace(raw, "\n")
    val plain = anyTagRegex.replace(normalized, "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
    if (plain.isNotEmpty()) out += CommentSegment.Text(plain)
}
