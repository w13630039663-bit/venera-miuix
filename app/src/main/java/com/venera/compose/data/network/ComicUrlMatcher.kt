package com.venera.compose.data.network

/**
 * 漫画 URL 直达识别器 (S4 核心能力)
 *
 * 识别用户粘贴进搜索栏的各类漫画站点链接，直接解析出 sourceKey 与 comicId，
 * 支持一键直达漫画详情页，避免二次手动检索。
 */
object ComicUrlMatcher {

    data class MatchedComic(
        val sourceKey: String,
        val sourceName: String,
        val comicId: String,
        val originalUrl: String
    )

    fun match(input: String): MatchedComic? {
        val trimmed = input.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null

        // 1. 拷贝漫画:
        // https://copymanga.site/comic/{id}
        // https://copymanga.tv/comic/{id}
        // https://mangacopy.com/comic/{id}
        val copyRegex = Regex("""copymanga\.[^/]+/comic/([a-zA-Z0-9_-]+)""")
        val copyMatch = copyRegex.find(trimmed)
            ?: Regex("""mangacopy\.[^/]+/comic/([a-zA-Z0-9_-]+)""").find(trimmed)
        if (copyMatch != null) {
            return MatchedComic(
                sourceKey = "copy_manga",
                sourceName = "拷贝漫画",
                comicId = copyMatch.groupValues[1],
                originalUrl = trimmed
            )
        }

        // 2. 包子漫画:
        // https://baozimh.com/comic/{id}
        // https://cn.baozimh.com/comic/{id}
        // https://www.baozimh.com/comic/{id}
        val baoziRegex = Regex("""baozimh\.[^/]+/comic/([a-zA-Z0-9_-]+)""")
        val baoziMatch = baoziRegex.find(trimmed)
        if (baoziMatch != null) {
            return MatchedComic(
                sourceKey = "baozi",
                sourceName = "包子漫画",
                comicId = baoziMatch.groupValues[1],
                originalUrl = trimmed
            )
        }

        // 3. MangaDex:
        // https://mangadex.org/title/{id}
        val mdRegex = Regex("""mangadex\.org/title/([a-zA-Z0-9_-]+)""")
        val mdMatch = mdRegex.find(trimmed)
        if (mdMatch != null) {
            return MatchedComic(
                sourceKey = "manga_dex",
                sourceName = "MangaDex",
                comicId = mdMatch.groupValues[1],
                originalUrl = trimmed
            )
        }

        // 4. 哔哩哔哩漫画:
        // https://manga.bilibili.com/detail/mc{id}
        val biliRegex = Regex("""manga\.bilibili\.com/detail/mc(\d+)""")
        val biliMatch = biliRegex.find(trimmed)
        if (biliMatch != null) {
            return MatchedComic(
                sourceKey = "bilibili",
                sourceName = "哔哩哔哩漫画",
                comicId = biliMatch.groupValues[1],
                originalUrl = trimmed
            )
        }

        return null
    }
}
