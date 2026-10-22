package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GalleryPost

/**
 * 图库站（Booru：yande.re / Gelbooru / Safebooru）下载保存格式解析器。
 *
 * 支持占位符：{site}、{id}、{artist}、{uploader}、{md5}、{rating}、{timestamp}。
 */
object GallerySaveFormatHelper {
    const val DEFAULT_FORMAT = "{site}_{artist}_{id}"

    val AVAILABLE_TOKENS = listOf(
        "站点" to "{site}",
        "作品ID" to "{id}",
        "画师" to "{artist}",
        "上传者" to "{uploader}",
        "MD5" to "{md5}",
        "分级" to "{rating}",
        "时间戳" to "{timestamp}",
    )

    fun format(
        template: String,
        post: GalleryPost,
        artistName: String,
        timestamp: String,
        ext: String,
    ): String {
        val tpl = template.ifBlank { DEFAULT_FORMAT }
        val cleanArtist = sanitize(artistName)
        val cleanUploader = sanitize(post.author)
        val cleanSite = sanitize(post.site.routeKey)
        val cleanMd5 = sanitize(post.md5)
        val cleanRating = sanitize(post.rating)

        var name = tpl
            .replace("{site}", cleanSite)
            .replace("{id}", post.id.toString())
            .replace("{artist}", cleanArtist)
            .replace("{uploader}", cleanUploader)
            .replace("{md5}", cleanMd5)
            .replace("{rating}", cleanRating)
            .replace("{timestamp}", timestamp)

        // 占位符为空时（例如没有画师名），平滑折叠连续的分隔符，避免出现 "yandere__1269641" 或 "_1269641"
        name = collapseDelimiters(name)
        val pageTail = if (post.pageOrdinal != null && !tpl.contains("{part}")) {
            "-p${post.pageOrdinal}"
        } else {
            ""
        }
        val sanitized = sanitizeFileName(name)
        val baseName = if (sanitized.isBlank()) "${cleanSite}_${post.id}" else sanitized
        val cleanExt = ext.trimStart('.').ifBlank { "jpg" }
        return "$baseName$pageTail.$cleanExt"
    }

    internal fun collapseDelimiters(raw: String): String {
        return raw
            .replace(Regex("_{2,}"), "_")
            .replace(Regex("-{2,}"), "-")
            .replace(Regex("([_-]){2,}"), "$1")
            .trim('_', '-', ' ')
    }

    private fun sanitize(raw: String): String = raw
        .trim()
        .replace(Regex("[/\\\\:*?\"<>|]+"), "_")
        .trim('_', '.')

    private fun sanitizeFileName(raw: String): String = raw
        .trim()
        .replace(Regex("[/\\\\:*?\"<>|]+"), "_")
        .trim('_', '.')
}
