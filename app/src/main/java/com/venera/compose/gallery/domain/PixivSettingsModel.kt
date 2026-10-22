package com.venera.compose.gallery.domain

/**
 * pixiv 专属画质档位（2026-10-22）。
 *
 * 分别用于：卡片预览画质、作品/漫画详情页画质、大图全屏看图器画质。
 */
enum class PixivImageQuality(val label: String) {
    MEDIUM("中画质"),
    LARGE("大图"),
    ORIGINAL("原图");
}

/**
 * pixiv 网络连接模式。
 *
 * 分别用于：OAuth 授权登录、以及 API 数据与图片服务。
 */
enum class PixivNetworkMode(val label: String, val summary: String) {
    ECH("ECH", "通过 Encrypted ClientHello 绕过 SNI 审查"),
    COMPAT("兼容", "绕过 SNI 并使用 DoH"),
    STANDARD("标准", "标准连接，不使用绕过策略");
}

/**
 * pixiv 下载保存格式解析器。
 *
 * 支持占位符：{illust_id}、{part}、{title}、{user_id}、{artist_name}。
 */
object PixivSaveFormatHelper {
    const val DEFAULT_FORMAT = "{illust_id}_p{part}"

    val AVAILABLE_TOKENS = listOf(
        "作品ID" to "{illust_id}",
        "part" to "{part}",
        "标题" to "{title}",
        "用户ID" to "{user_id}",
        "画师名" to "{artist_name}",
    )

    fun format(
        template: String,
        illustId: Long,
        part: Int,
        title: String,
        userId: Long?,
        artistName: String,
        ext: String,
    ): String {
        val tpl = template.ifBlank { DEFAULT_FORMAT }
        val name = tpl
            .replace("{illust_id}", illustId.toString())
            .replace("{part}", part.toString())
            .replace("{title}", sanitize(title))
            .replace("{user_id}", userId?.toString() ?: "")
            .replace("{artist_name}", sanitize(artistName))
        val sanitized = sanitizeFileName(name)
        val cleanExt = ext.trimStart('.').ifBlank { "jpg" }
        return if (sanitized.isBlank()) "${illustId}_p${part}.$cleanExt" else "$sanitized.$cleanExt"
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
