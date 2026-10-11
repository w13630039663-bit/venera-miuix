package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.security.guard.GuardRule
import com.venera.compose.security.guard.GuardRulePattern

/**
 * 桌面守卫那一屏的**写侧判据**（批 6）：三档分级、四型屏蔽规则、加一条该不该发。
 *
 * 全部是纯函数 —— 为的是这些判据能被单测真跑，而不是只能扫文本
 * （本仓一贯：能真跑的判据永远比能扫的可靠）。落盘那一步在
 * `DesktopGalleryContentGuard`，它只负责"照这里说的做"。
 *
 * ## 词与值都不许自己发明
 *
 * [desktopMaskModeLabels] 与 [desktopRuleTypes] 这两张表由 `DesktopGuardWriteTest` ①③
 * **逐字回核 Android** 的 `BlockingSettings.kt` 那两行（判据直接从对岸那行抽字面量回来比）。
 * 两端各写一份"同一个键的两种措辞"，用户换设备就读到两张不同的表 —— 而"这张图给不给看"恰好是最不该分叉的那一格。
 */

/** 「不过滤」那一档落盘的值（`nsfw_mode` 的原值，也是 [desktopAllowNsfw] 唯一认的那一枚）。 */
internal const val DESKTOP_MASK_MODE_OFF: String = "OFF"

/** `nsfw_mode` 的三枚取值与屏上那三个词（值是要落盘的，Android 读的是同一枚键）。 */
internal fun desktopMaskModeLabels(): List<Pair<String, String>> = listOf(
    DESKTOP_MASK_MODE_OFF to "不过滤",
    "BLUR" to "封面打码",
    "HIDE" to "彻底隐藏",
)

/**
 * 反搜要不要让站方把成人内容也带回来 —— **与那面墙同一把**判据，不是第二枚开关。
 *
 * 对岸那句在 `GalleryScreen.kt`（`val allowNsfw = maskMode == "OFF"`），
 * 由 `DesktopReverseSearchTest` ⑪ 逐字回核那一行，桌面这边不重定语义；
 * 之所以只有一处而不是各屏各写一份：写第二份的人只会照着布尔名猜，而猜的那一半永远是键值。
 */
internal fun desktopAllowNsfw(maskMode: String): Boolean = maskMode == DESKTOP_MASK_MODE_OFF

/**
 * 只认那三枚值，其余**抛**。
 *
 * 为什么不是"不认就退回 OFF"：写进一档 Android 侧读不懂的值，会让那一侧的
 * `maskStateFor` 走到 `else` 那一支（把不认识的档当成"命中就 BLURRED"），
 * 而屏上显示的仍是用户刚点的那一枚 —— 那是"看着改成功了、两边各按各的判"。宁可当场炸在写之前。
 */
internal fun desktopRequireMaskMode(mode: String): String {
    val known = desktopMaskModeLabels().map { it.first }
    require(mode in known) { "「$mode」不是分级的一档，能写的只有 ${known.joinToString(" / ")}" }
    return mode
}

/** 四型屏蔽规则（键是库里的 `rule_type` 原值，词是屏上那一行的标题）。 */
internal fun desktopRuleTypes(): List<Pair<String, String>> = listOf(
    "KEYWORD" to "关键词",
    "TAG" to "标签",
    "AUTHOR" to "画师",
    "COMIC_ID" to "作品",
)

/**
 * 那一型**怎么匹配**的那一句（直接上屏，用户拿它决定往里填什么）。
 *
 * 认不出来的型回空串而不是回一句通用的：这一句说的是匹配算法，
 * 拿"按包含匹配"去兜一枚不知道怎么回事的型，等于替库里的新类型编造判据。
 */
internal fun desktopRuleTypeHint(type: String): String = when (type) {
    "KEYWORD" -> "标题按包含匹配，不区分大小写。"
    "TAG" -> "标签按包含匹配，不是精确匹配。"
    "AUTHOR" -> "作者按包含匹配，只有提供作者信息的源生效。"
    "COMIC_ID" -> "填写作品编号，按编号精确匹配，不区分源。"
    else -> ""
}

/** 加一条之前判出来的四档落点。四档各说一句人话，不许合成一句「已保存」。 */
internal sealed interface DesktopAddRule {
    /** 串是空的（或整串空白）：不发，也不报错到屏外。 */
    data object Blank : DesktopAddRule

    /** 开了正则开关而这一串编译不过 —— **写库前**就得拦下。 */
    data object BadPattern : DesktopAddRule

    /** 同一型里已经有这一串（忽略大小写）。 */
    data object Duplicate : DesktopAddRule

    /** 该发的那一串（已 trim 过，就是站点原值）。 */
    data class Accept(val pattern: String) : DesktopAddRule
}

/**
 * 这一串该不该发出去。
 *
 * 三条判据一条都不能少，而且**次序**是判据：
 * 空串先判（往库里塞一颗空规则会让"包含匹配"那一型把所有条目都挡掉）；
 * 坏正则第二判（Android 原话：坏模式会**静默永不命中**，比报错更难查）；
 * 查重最后判（同型同名才算重复 —— 标签 `ai` 与画师 `ai` 是两条不同的规则，
 * 跨型算重复就是把用户想做的事挡在门外）。
 */
internal fun desktopAddRule(
    input: String,
    regexMode: Boolean,
    type: String,
    existing: List<GuardRule>,
): DesktopAddRule {
    val pattern = input.trim()
    if (pattern.isEmpty()) return DesktopAddRule.Blank
    if (regexMode && !GuardRulePattern.compiles(pattern)) return DesktopAddRule.BadPattern
    if (existing.any { it.type == type && it.pattern.equals(pattern, ignoreCase = true) }) return DesktopAddRule.Duplicate
    return DesktopAddRule.Accept(pattern)
}

/**
 * 那一屏的规则表：四型各一组，**外加一份"认不出的那些"**。
 *
 * 为什么要有 [unlisted]：库里可能出现桌面这份表没有的型（Android 侧将来加一枚、
 * 或从别处导入的档）。静默丢掉它就是"屏上少一条而没人知道为什么"——
 * 那一型今天仍然生效（命中判定读的是全表），只是这一屏不说它。
 */
internal class DesktopRuleTable(
    private val byType: Map<String, List<GuardRule>>,
    val unlisted: List<GuardRule>,
) {
    fun of(type: String): List<GuardRule> = byType[type].orEmpty()
}

/** 分组现算，不另存一份 state（另存就会与规则流不同步，而不同步的读数比没有读数更坏）。 */
internal fun desktopRulesByType(rules: List<GuardRule>): DesktopRuleTable {
    val known = desktopRuleTypes().map { it.first }.toSet()
    return DesktopRuleTable(
        byType = rules.groupBy { it.type }.filterKeys { it in known },
        unlisted = rules.filterNot { it.type in known },
    )
}

/**
 * 长按那一枚标签写进去的型 —— **与对岸同一枚**（TAG 而不是 KEYWORD）。
 *
 * 这条不是风格：画廊那面墙的屏蔽判据只喂 `post.tagList`，而两站的 `tags` 就是那一串平铺标签，
 * 所以存 TAG 每一枚 chip 都必然命中；用 KEYWORD 会连作者名一起误伤。
 * 那条推导的原文住在 Android 的那颗 `blockTag` 上面几行，判据 ① 逐字回核它。
 */
internal const val DESKTOP_GUARD_TAG_RULE_TYPE: String = "TAG"

/**
 * 设置那一屏里这一区的标题。页脚那句、落点那句都引用这一颗常量 ——
 * "改回来在哪"这件事在两端各写一份字面量，将来改名时就会有一处留在屏上指错地方。
 */
internal const val DESKTOP_GUARD_SECTION_TITLE: String = "敏感内容与屏蔽规则"

/**
 * 「列表里已经有这一枚」：同型同名才算（大小写不算差别）。
 *
 * ⚠️ 停用着的那一条**也算**在列表里，这是跟着对岸的口径而不是"顺手改得更聪明"：
 * 屏上那句说的是"在列表里"，而不是"正在挡"；若按启用与否分档，这一枚会再添一条同名项，
 * 而用户想做的其实是去那一屏把原来那条启用。
 */
internal fun desktopTagAlreadyBlocked(rules: List<GuardRule>, tag: String): Boolean =
    rules.any { it.type == DESKTOP_GUARD_TAG_RULE_TYPE && it.pattern.equals(tag, ignoreCase = true) }

/** 那一枚 chip 按下去的三档落点。三档各说一句（合成「已保存」那种话是本仓最忌的读数）。 */
internal sealed interface DesktopBlockTagOutcome {
    data object Already : DesktopBlockTagOutcome
    data object Added : DesktopBlockTagOutcome
    data object Failed : DesktopBlockTagOutcome
}

/**
 * 定档：**先看有没有，再看 id**。
 *
 * 已经有这一条时根本不发那一笔，所以 `ruleId` 为 null 也不算失败；
 * 而发出去那一笔回报 -1（写不进去）也不是成功 —— `>= 0` 这一条与对岸同一枚判据，
 * 少半句就会把"没写成"念成"已屏蔽"。
 */
internal fun desktopBlockTagOutcome(
    alreadyBlocked: Boolean,
    ruleId: Long?,
): DesktopBlockTagOutcome = when {
    alreadyBlocked -> DesktopBlockTagOutcome.Already
    ruleId != null && ruleId >= 0 -> DesktopBlockTagOutcome.Added
    else -> DesktopBlockTagOutcome.Failed
}

/** 三档各翻成一句人话：点了什么、成了没有、去哪儿改回来。 */
internal fun desktopBlockTagReading(tag: String, outcome: DesktopBlockTagOutcome): String = when (outcome) {
    DesktopBlockTagOutcome.Already ->
        "「$tag」已经在屏蔽列表里，要改动它去「$DESKTOP_GUARD_SECTION_TITLE」那一区"

    DesktopBlockTagOutcome.Added ->
        "已屏蔽「$tag」，那一面墙下一帧就认这条新规则；要改回去去「$DESKTOP_GUARD_SECTION_TITLE」那一区"

    DesktopBlockTagOutcome.Failed ->
        "「$tag」这一枚没屏蔽上，再试一次"
}

/**
 * 长按那一枚标签的全部动作：判有没有 → 发那一笔 → 翻成人话。
 *
 * 写口只有契约上那一枚（`GalleryContentGuard.addRule`），这里不碰库、不拼 SQL ——
 * 浮层自己写一份匹配口径，就又是"同一枚规则在两端挡得不一样"。
 * 那颗具体件的失败是**回报 -1**（它自己把异常接走了），所以这里不再包一层 runCatching。
 */
internal suspend fun desktopBlockTag(
    guard: GalleryContentGuard,
    rules: List<GuardRule>,
    tag: String,
): String {
    val already = desktopTagAlreadyBlocked(rules, tag)
    val id = if (already) null else guard.addRule(DESKTOP_GUARD_TAG_RULE_TYPE, tag)
    return desktopBlockTagReading(tag, desktopBlockTagOutcome(already, id))
}
