package com.venera.compose.components

/**
 * 结果卡上要显示的标签文本 —— 从 `feature/SearchScreen.kt:1843` 搬来 `components/`（2026-10-20）。
 *
 * ## 为什么搬
 *
 * `ComicListPresentation.kt`（`components/`，大屏复用的核心层）要调它来画取景卡上的标签药丸，
 * 而它原先住在 `feature/SearchScreen.kt` —— 那颗屏带着 38 处 `LocalContext`、18 处 `Toast`、
 * 12 处 `Intent`、11 处 `viewModel`，是 Android 绑定最重的一颗文件。
 * 一行纯判据的调用把整层 `components/` 钉在了 Android 上，那就是这次要拆的锁。
 *
 * 这三个函数（本函数 + `conceptKey` + `literalKey` + `WHITESPACE_RUN`）**零依赖**，
 * 整块搬过来即可，语义一字未改。`SearchScreen.kt` 那边改成 import 本文件。
 *
 * ## 折叠规则（原文保留）
 *
 * 严格对应方案 §4.1 的「只合并字面同义」：
 * - 大小写 / 空白差异 → 同一枚（`Full Color` 与 `full  color`）；
 * - **裸词**与其**带命名空间**写法 → 留带命名空间那个（`chinese` 让位给 `language:chinese`）；
 * - **两个不同命名空间**下的同名值 → 各留一枚。真机图里 `東方: 靈夢` 这类前缀就是这种形态；
 *   早期版本只拿「去前缀后的值」当唯一键，会把 `female:dog` 与 `parody:dog` 错误并成一枚。
 * - 语义同义（`chinese` 与 `translated`）一律不合并 —— 它们是不同检索面词，合并等于丢功能。
 *
 * 存活下来的原文才是发给源的词，所以这里只会减少重复药丸，不可能改变搜索结果。
 */
internal fun searchVisibleTags(
    tags: List<String>,
    exclude: List<String>,
    display: (String) -> String = { it },
): List<String> {
    val excluded = exclude.map { conceptKey(it) }.toSet()
    val entries = tags.map { it.trim() }
        .filter { it.isNotEmpty() && conceptKey(it) !in excluded }
    // 已被带命名空间写法认领的概念，裸词不再单独出现。
    val claimedByQualified = entries.filter { ':' in it }.map { conceptKey(it) }.toSet()
    val seen = HashSet<String>()
    val survivors = ArrayList<String>()
    entries.forEach { raw ->
        if (':' !in raw && conceptKey(raw) in claimedByQualified) return@forEach
        // 带前缀的按完整字面键去重（不同前缀互不吞并），裸词按概念键去重（只并大小写/空白）。
        val key = if (':' in raw) literalKey(raw) else conceptKey(raw)
        if (seen.add(key)) survivors.add(raw)
    }
    return survivors.map(display)
}

/** 概念键：小写 + 折叠全部空白 + 去命名空间前缀。 */
private fun conceptKey(value: String): String {
    val literal = literalKey(value)
    return literal.substringAfter(':', literal)
}

/** 字面键：小写 + 折叠全部空白，保留命名空间前缀。 */
private fun literalKey(value: String): String =
    value.trim().lowercase().replace(WHITESPACE_RUN, "")

private val WHITESPACE_RUN = Regex("\\s+")
