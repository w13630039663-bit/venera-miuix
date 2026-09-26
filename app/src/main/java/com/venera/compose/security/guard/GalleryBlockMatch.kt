package com.venera.compose.security.guard

/**
 * 画廊侧的黑名单匹配判据。与漫画侧 [ContentGuardManager.isComicBlocked] **唯一的差别是 tag 怎么切**：
 *
 * 图站的 tag 是一个个**下划线标识符**（`long_hair`、`hair_ornament`、`tail`），
 * 而关键字规则是**词**。拿子串去比标识符，用户的屏蔽词 `ai` 就会命中 `long_hair` 里的 `ai` ——
 * 2026-09-25 真机实测：Danbooru 当天日榜 200 条里 199 条含 `*_hair*` 类 tag，
 * **整站被这一条规则清空**，而 yande.re 同判据下 40 条挡掉 12 条。
 * 所以这里改成：整串相等 **或** 切成的词元相等（正则同理，用整词匹配而不是 find）。
 * 结果 `ai` 仍然命中 `ai` / `ai_generated` / `generated_by_ai`，不再命中 `long_hair`。
 *
 * 刻意**不改漫画侧那一份**：那边的 tag 是自由文本（`AI汉化`、`中文翻译`），
 * 中文没有分隔符，子串匹配是唯一能用的判据；换成整词会把用户已经挡掉的东西放出来。
 */
internal object GalleryBlockMatch {

    /**
     * 标识符切词元。`:` 剥命名空间（`female:big_breasts`），`-` 与 `_` 是图站的分词约定，
     * 空格留给 e-hentai 那种 `big breasts` 形态。
     */
    fun tokens(tag: String): List<String> =
        tag.split(':', '_', '-', ' ').map { it.trim() }.filter { it.isNotEmpty() }

    /** 这条规则是否命中（author 是人名，按子串比，与漫画侧同口径）。 */
    fun blocked(rule: GuardRule, author: String, tags: List<String>): Boolean = when (rule.type) {
        "KEYWORD" -> contains(rule, author) || tags.any { wholeToken(rule, it) }
        "TAG" -> tags.any { wholeToken(rule, it) }
        "AUTHOR" -> author.isNotBlank() && contains(rule, author)
        else -> false
    }

    private fun contains(rule: GuardRule, target: String): Boolean = target.isNotBlank() && try {
        if (rule.isRegex) regexes.getOrPut(rule.pattern) { Regex(rule.pattern, RegexOption.IGNORE_CASE) }
            .containsMatchIn(target)
        else target.contains(rule.pattern, ignoreCase = true)
    } catch (_: Exception) {
        false
    }

    /**
     * 整词判定：整串相等或某个词元相等。
     *
     * 保留"整串相等"这一路是为了 `ai-generated` 这类**本身就带分隔符**的模式 ——
     * 只比词元会让它永远配不上（切完成了 `ai` 与 `generated`）。
     */
    private fun wholeToken(rule: GuardRule, tag: String): Boolean {
        if (tag.isBlank()) return false
        if (!rule.isRegex) {
            return tag.equals(rule.pattern, ignoreCase = true) ||
                tokens(tag).any { it.equals(rule.pattern, ignoreCase = true) }
        }
        return try {
            val rx = regexes.getOrPut(rule.pattern) { Regex(rule.pattern, RegexOption.IGNORE_CASE) }
            rx.matches(tag) || tokens(tag).any { rx.matches(it) }
        } catch (_: Exception) {
            false
        }
    }

    /** 判定跑在列表滚动的密度上，所以正则预编译缓存（口径同 [ContentGuardManager]）。 */
    private val regexes = java.util.concurrent.ConcurrentHashMap<String, Regex>()
}
