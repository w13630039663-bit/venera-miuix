package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.data.refineGalleryTagSuggestions

/**
 * 搜索那一屏的**标签补全**：把几腿候选并成一排，并记下哪几腿没答上。
 *
 * ## 为什么桌面不能自己拼一份
 *
 * 共享面那颗 [refineGalleryTagSuggestions] 存在的原因写在 `GalleryTag.kt` 里：两站的"前缀匹配"
 * 参数都**可能被静默忽略**，而忽略之后回的还是 200 + 一个像样的数组 —— 站方给错数据时没有任何信号。
 * 桌面原先自己 `map { it.name }.distinct().take(8)`，于是屏上摆的是不相干的标签，
 * 而它看起来像"你要搜的那个"。复检只留一处，两端同一颗件。
 *
 * ## 三句话是三句话
 *
 * 「没答上」（这一腿失败了）、「没有」（答上了但站里 0 条）、「没问」（词太短 / 没配账号 ⇒ 根本没发车）
 * 在屏上必须念成三句不同的话，或干脆不念 —— [desktopSuggestionReading] 就是那把尺子。
 * 上一版把失败 `getOrDefault(emptyList())` 吞成空列表，三句塌成一句沉默。
 */
internal class DesktopSuggestionOutcome(
    /** 并完、复检完、封顶后的候选（保先到：同名留站表序在前的那条腿）。 */
    val candidates: List<GalleryTagSuggestion>,
    /** 发车了的那几站的显示名，**顺序 = 发车顺序**（屏上点名的顺序就用它）。 */
    val askedSites: List<String>,
    /** 其中没答上的那几站。 */
    val unansweredSites: List<String>,
)

/**
 * 几腿候选 → 一排候选 + 那几腿没答上。
 *
 * `limit` 必须是正数：0 或负是**调用方写错**，而它的表现恰好是"站里没有这个词"那句假话
 * —— 所以这里当场抛，不替调用方兜（屏上那句读数比一排空候选重要）。
 */
internal fun desktopMergeSuggestionLegs(
    term: String,
    legs: List<Pair<GallerySite, Result<List<GalleryTagSuggestion>>>>,
    limit: Int,
): DesktopSuggestionOutcome {
    require(limit >= 1) { "补全候选的上限得是正数，收到 $limit" }
    val merged = LinkedHashMap<String, GalleryTagSuggestion>()
    val unanswered = mutableListOf<String>()
    for ((site, outcome) in legs) {
        outcome
            .onSuccess { list ->
                refineGalleryTagSuggestions(term, list, limit).forEach { merged.putIfAbsent(it.name, it) }
            }
            .onFailure { unanswered += site.displayName }
    }
    return DesktopSuggestionOutcome(
        candidates = merged.values.take(limit),
        askedSites = legs.map { it.first.displayName },
        unansweredSites = unanswered,
    )
}

/**
 * 这一排候选在屏上该补哪一句；**没事可说就返回 null**（不许编一句"没有候选"来填那一格）。
 *
 * 四条分支按"话越真越先说"排：
 * 1. 根本没发车（0 腿）⇒ 没话；
 * 2. **全都**没答上 ⇒ 对岸那句「标签补全失败」（逐字，见 `GallerySearchViewModel.kt`）；
 * 3. 有几腿没答上 ⇒ 点名那几站 —— 它排在"零条候选"前面，因为拿"答上的那几站都没有"
 *    去盖"另一站我们压根没收到回答"就是把我们的失败说成站方的空结果；
 * 4. 全都答上而候选 0 条 ⇒ 「X 没有以「词」开头的标签」。
 *
 * ⚠️ 分支 4 的多站那句**不是**对岸那句「两个站都没有…」的复制：对岸来源档最多两腿，那句在它
 * 那边是实话，而桌面「全部」档是**三腿**。照抄会在三腿那档念错数，所以这里按发车顺序点名站名。
 * 单站那一档仍与对岸**逐字同串**。
 */
internal fun desktopSuggestionReading(term: String, outcome: DesktopSuggestionOutcome): String? = when {
    outcome.askedSites.isEmpty() -> null
    outcome.unansweredSites.size == outcome.askedSites.size -> DESKTOP_SUGGEST_ALL_FAILED
    outcome.unansweredSites.isNotEmpty() ->
        "${outcome.unansweredSites.joinToString("、")} 没答上这一问，候选只数了答上的那几站"
    outcome.candidates.isNotEmpty() -> null
    outcome.askedSites.size == 1 -> "${outcome.askedSites.first()} 没有以「$term」开头的标签"
    else -> "${outcome.askedSites.joinToString("、")} 都没有以「$term」开头的标签"
}

/** 全部腿都没答上那句。逐字取自对岸 `GallerySearchViewModel` 里赋给 `suggestError` 的那串。 */
internal const val DESKTOP_SUGGEST_ALL_FAILED: String = "标签补全失败"
