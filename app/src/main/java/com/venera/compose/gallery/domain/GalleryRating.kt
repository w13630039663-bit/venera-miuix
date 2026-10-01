package com.venera.compose.gallery.domain

/**
 * 分级值的**严重度带**（不是分级本身）。
 *
 * ## 为什么要有这一层，而不是"站方那个字符串 → 一个颜色"直接映射
 *
 * 两站的取值集不一样，一共五档语义：
 * - yande.re：单字母 `s` / `q` / `e`；
 * - Gelbooru：单词 `general` / `sensitive` / `questionable` / `explicit`（比 yande.re 多一档）。
 *
 * 但**能用的颜色只有四档**（含"未知"），这是量出来的、不是挑出来的。分级徽标走的是
 * 同为"功能性固定色"的 SourceBadge 那条路 —— **实底胶囊 + 白字**，于是每一档都要保证
 * 白字压住容器色 ≥ 4.5:1（正文门槛）。逐色算过（WCAG 2.1，sRGB 相对亮度）：
 *
 * | 候选 | 白字对比度 |
 * |---|---|
 * | `#2E7D32` | 5.13:1 ✅ |
 * | `#A15C00` | 5.17:1 ✅ |
 * | `#C62828` | 5.63:1 ✅ |
 * | `#616161` | 6.29:1 ✅ |
 * | `#4CAF50`（Material 绿 500） | 2.78:1 ❌ |
 * | `#F9A825`（Material 黄 800） | 1.97:1 ❌ |
 * | `#FFB300`（Material 琥珀 600） | 1.90:1 ❌ |
 *
 * 也就是说：**黄/琥珀族根本进不了这套口子**（明度太高，白字压不住；换成深色又变棕）。
 * 若硬把色阶压深到能过对比度，`sensitive` 与 `questionable` 两档在屏上就分不出来了
 * —— 实测 `#9A6A00` 与 `#A15C00` 的色相差不到 10°。
 *
 * 所以这一层只把「**这一档有多需要留意**」编成色，**精确档位由徽标上的字承担**
 * （[ratingLabel] 给的中文 + 英文原文）。这与标签分类色那条
 * "颜色只编码粗略归属、文字才是精确值"是同一份口径。
 *
 * ⚠️ 反过来也有一处**不许偷懒**的地方：站方给了一个我们不认识的值（或空），
 * 一律是 [Unknown]，**不归进 [Caution]**。「认不出」与「站方说是敏感」是两件事，
 * 把前者画成后者等于替站方下一个它没下过的判断 —— 与全仓"没有的字段一律不编"同一条红线。
 */
internal enum class GalleryRatingTone {
    /** 安全 / 一般：两站的 `s`、`safe`、`g`、`general`。 */
    Safe,

    /** 敏感 / 存疑：Gelbooru 的 `sensitive`，与两站的 `q`、`questionable` 合用一个色（理由见头注）。 */
    Caution,

    /** 成人：两站的 `e`、`explicit`。 */
    Explicit,

    /** 站方给了个不认识的值、或干脆是空的。**不猜**。 */
    Unknown,
}

/**
 * 分级串 → 严重度带。
 *
 * 大小写与前后的空白都不参与判定（站方两站的输出是稳定的，但这一层不该假设它稳定）。
 */
internal fun ratingToneOf(rating: String): GalleryRatingTone = when (rating.trim().lowercase()) {
    "s", "safe", "g", "general" -> GalleryRatingTone.Safe
    "sensitive", "q", "questionable" -> GalleryRatingTone.Caution
    "e", "explicit" -> GalleryRatingTone.Explicit
    else -> GalleryRatingTone.Unknown
}

/**
 * 分级值 → 人话（**中文 + 英文原文，两个都留**）。
 *
 * ⚠️ **两站的字形不同**（实测），所以这张表要同时收两套：
 * - yande.re：单字母 `s` / `q` / `e`；
 * - Gelbooru：单词 `general` / `sensitive` / `questionable` / `explicit`。
 *
 * 只认单字母的话，Gelbooru 的每一条都会显示成「未知」—— 那不是"数据缺失"，
 * 是我们没认出来，属于最没必要的一种显示瑕疵。
 *
 * 英文原文常驻括号里也是刻意的：站方页面上写的就是那个词，用户拿它去对账时
 * 只留中文会对不上。这一条在信息卡右上那枚分级徽标上同样成立 —— 徽标宽度
 * （实宽 ≈136dp，行内还有标题的 weight(1f) 让位）读得下，就不为了"短"把原文砍掉。
 */
internal fun ratingLabel(rating: String): String = when (rating.trim().lowercase()) {
    "s", "safe" -> "安全 (s)"
    "g", "general" -> "一般 (general)"
    "sensitive" -> "敏感 (sensitive)"
    "q", "questionable" -> "存疑 (questionable)"
    "e", "explicit" -> "成人 (explicit)"
    else -> "未知 (${rating.trim().ifBlank { "空" }})"
}
