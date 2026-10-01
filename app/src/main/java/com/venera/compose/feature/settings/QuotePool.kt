package com.venera.compose.feature.settings

import java.util.Random

/**
 * 一句名言卡上的话。`source` 为 null 时卡片不摆出处行（匿名短句）。
 */
data class SettingsQuote(val text: String, val source: String? = null)

/**
 * 名言池（2026-10-02，设置页重设计）。
 *
 * 两条内容纪律：
 * 1. **不伪造出处** —— 对得上真实出处的（古诗词）才写人名；其余是本应用的原创短句，
 *   出处要么写应用自己（"Venera"），要么不写。绝不编「某某作家说」。
 * 2. 主题限定在**阅读 / 图画 / 收藏**：这是漫画阅读器，不摆跟场景无关的鸡汤。
 */
object QuotePool {

    val quotes: List<SettingsQuote> = listOf(
        // 本应用 / 场景原创（出处写应用自己或不写）
        SettingsQuote("在无尽图画中，找到你最喜欢的那一张。", "Venera"),
        SettingsQuote("每一页收藏，都是寄给未来自己的一封信。", "Venera"),
        SettingsQuote("好看的图永远在下一张。", "Venera"),
        SettingsQuote("翻页是小事，记住翻开时的心情才是。"),
        SettingsQuote("收藏夹不会说话，但它替你记着喜欢过什么。"),
        // 古诗词（出处可考，公版）
        SettingsQuote("读书破万卷，下笔如有神。", "杜甫"),
        SettingsQuote("腹有诗书气自华。", "苏轼"),
        SettingsQuote("纸上得来终觉浅，绝知此事要躬行。", "陆游"),
        SettingsQuote("问渠那得清如许？为有源头活水来。", "朱熹"),
        SettingsQuote("书卷多情似故人，晨昏忧乐每相亲。", "于谦"),
        SettingsQuote("旧书不厌百回读，熟读深思子自知。", "苏轼"),
        SettingsQuote("奇文共欣赏，疑义相与析。", "陶渊明"),
        // 阅读主题的现代短句（不署名，避免伪造出处）
        SettingsQuote("读一本好书，像是替自己多活了一段人生。"),
        SettingsQuote("慢一点，图和字都不会跑。"),
        SettingsQuote("今天也翻过了值得记住的一页。"),
    )

    /**
     * 由种子确定性抽样：同一种子必得同一句。
     *
     * 为什么不直接 `random()`：单测需要可复现的断言；UI 侧用 [randomIndex]
     * 拿真随机下标，把「随机」关在调用方，纯判据保持确定。
     */
    fun pick(seed: Long): SettingsQuote = quotes[Random(seed).nextInt(quotes.size)]

    /** 真随机抽一句。 */
    fun pickRandom(): SettingsQuote = quotes.random()

    /** 真随机下标（供 `rememberSaveable { mutableIntStateOf(...) }` 存，旋屏不换句）。 */
    fun randomIndex(): Int = quotes.indices.random()
}
