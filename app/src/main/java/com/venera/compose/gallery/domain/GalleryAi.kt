package com.venera.compose.gallery.domain

import com.venera.compose.security.guard.AiTagKeys

/**
 * 画廊这一把的 AI 判据（批次 C2，2026-09-29 用户拍板"画廊和漫画分开"）。
 *
 * 分开的是**开关**，词表仍以漫画侧那一张 [AiTagKeys] 为准（两份表迟早漂），
 * 但画廊侧对那张表做**两处只收窄不放宽**的改动，两处都是 2026-09-29 复探针实测出来的：
 *
 * 1. **归一 `_` 与 `-`**：Gelbooru 搜索侧两种写法都出图（`ai_generated` 与 `ai-generated`
 *    第一页各 9 张卡片，阴性对照 `zzz_not_a_tag` 是 0 张），而它写在条目上的标签是**连字符**那一版；
 *    不归一就是"换一种写法这枚角标永远不出现"。
 * 2. **不认裸标签 `ai`**：yande.re 上 `tags=ai` 有 19 条，逐条看标签串是
 *    `ai maid pointy_ears sage shuffle suzuhira_hiro tick_tack` —— 那是**角色名**（《Artery Gear》的 AI），
 *    不是"AI 画的"。而 yande.re 上 `ai-generated` / `ai_generated` / `generated_by_ai` / `ai_drawn`
 *    四种写法**全是 0 条**（JSON API 与站方 HTML 两条通路各量一遍，不带 tags 的对照都正常返回数据），
 *    也就是这一站目前根本没人打 AI 标签。
 *    留着裸 `ai` 就是把 19 张手工插画判成 AI —— 漫画侧那把需要它（EH 的 `AI生成` 系），画廊侧只需要它碍事。
 *
 * 仍然**不剥**命名空间前缀（`female:ai` 是角色+性别组合，不是"AI 生成"），与漫画侧同一条口径。
 */
object GalleryAi {

    /** 归一后的画廊侧词表：漫画侧那张表去掉裸 `ai`，再把 `_` 折成 `-`。 */
    private val normalizedKeys = AiTagKeys.filterNot { it == "ai" }
        .map { it.replace('_', '-') }
        .toSet()

    fun isAiTag(raw: String): Boolean {
        val value = raw.trim().lowercase().replace('_', '-')
        return value.isNotEmpty() && value in normalizedKeys
    }

    /** 这一条是不是被站方标成 AI 画的。没有标签 = 不算（不编一个判据出来）。 */
    fun isAiMarked(tags: List<String>): Boolean = tags.any { isAiTag(it) }
}
