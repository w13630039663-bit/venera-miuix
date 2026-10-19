package com.venera.compose.gallery.domain

import com.venera.compose.security.guard.AiTagKeys

/**
 * 画廊这一把的 AI 判据（批次 C2，2026-09-29 用户拍板"画廊和漫画分开"）。
 *
 * 分开的是**开关**，词表仍以漫画侧那一张 [AiTagKeys] 为底（两份表迟早漂），
 * 但画廊侧对它做**三处改动**：两处只收窄、一处定向补宽。
 *
 * ## 收窄一：不认裸标签 `ai`
 *
 * yande.re 上 `tags=ai` 有 19 条，逐条看标签串是
 * `ai maid pointy_ears sage shuffle suzuhira_hiro tick_tack` —— 那是**角色名**（《Artery Gear》的 AI），
 * 不是"AI 画的"。而该站上 `ai-generated` / `ai_generated` / `generated_by_ai` / `ai_drawn`
 * 四种写法**全是 0 条**（JSON API 与站方 HTML 两条通路各量一遍，不带 tags 的对照都正常返回数据），
 * 也就是这一站目前根本没人打 AI 标签。
 * 留着裸 `ai` 就是把 19 张手工插画判成 AI —— 漫画侧那把需要它（EH 的 `AI生成` 系），画廊侧只需要它碍事。
 *
 * ## 收窄二：不剥命名空间前缀
 *
 * `female:ai` 是角色+性别组合，不是"AI 生成"，与漫画侧同一条口径。
 *
 * ## 定向补宽：pixiv 那一族的写法 booru 词表里一个都没有（2026-10-18 用户「开了 AI 角标一个都没见到」）
 *
 * [AiTagKeys] 只有四枚（`ai` / `ai-generated` / `ai生成` / `ai绘图`），
 * 那是照 **booru / EH** 的写法定的。而 **pixiv 的标签是另一套语言**：
 * 站方把作品标成 AI 时常写 `AIart`、`NovelAI`、`StableDiffusion`、`Midjourney`、`AI生成画像`
 * —— 这五枚在 [AiTagKeys] 里一枚都没有，于是 pixiv 域这一把**恒不命中**，
 * 用户把开关打开也看不到一个角标（截图里正是 pixiv 域首页）。
 *
 * 补进去的五枚全部满足两条纪律，所以不构成"放宽"：
 *  1. **整枚相等**，不是包含 —— `aiassist` / `aigirl` 这类只含 "ai" 前缀的标签依旧不命中；
 *  2. **不收裸 `ai`**（上面收窄一那条），也不收任何可能是角色名的写法。
 *
 * 未实量上 pixiv 线上数据之前，这里只补"该站在用的那几家工具名"，**不猜画风词**。
 * 补错一枚的代价是"多一枚没意义的角标"，比"整个域一个角标都没有"轻，但仍然要控制数量。
 */
object GalleryAi {

    /** pixiv 侧实测补的那几枚（见头注「定向补宽」）。 */
    private val pixivExtraKeys = setOf(
        "aiart",
        "aigenerated",
        "novelai",
        "stablediffusion",
        "midjourney",
        "ai生成画像",
    )

    /**
     * 归一后的画廊侧词表。
     *
     * 归一把 `_`、`-`、空格**全折成同一枚**：booru 侧 `ai_generated` 与 `ai-generated` 是同一枚标签的
     * 两种写法（Gelbooru 搜索侧两种都出图，而它写在条目上的是连字符那一版），pixiv 侧
     * `Stable Diffusion` 与 `StableDiffusion` 同理。不归一就是"换一种写法这枚角标永远不出现"。
     */
    private val normalizedKeys: Set<String> =
        (AiTagKeys.filterNot { it == "ai" } + pixivExtraKeys)
            .map { normalizeKey(it) }
            .toSet()

    private fun normalizeKey(raw: String): String =
        raw.trim().lowercase()
            .replace('_', '-')
            .replace(' ', '-')

    fun isAiTag(raw: String): Boolean {
        val value = normalizeKey(raw)
        return value.isNotEmpty() && value in normalizedKeys
    }

    /**
     * 这一条算不算 AI：**站方硬位优先，标签词表兜底**（2026-10-18，用户「开了 AI 角标一个都没见到」）。
     *
     * 为什么写成"或"而不是"硬位 `false` 就推翻词表"：作者可以不勾那一格，
     * 而把一枚明明写着 `NovelAI` 的图读成"站方说不是 AI ⇒ 不摆"，方向是**少摆**；
     * 这一侧两种错法里只有"多摆一枚没意义的角标"更轻（同一理由见文件头那段补宽纪律）。
     *
     * `siteAiFlag == null` = 这一腿站方压根不给这一格（booru 三站、pixiv 榜单），那时只看词表。
     */
    fun isAiMarked(siteAiFlag: Boolean?, tags: List<String>): Boolean =
        siteAiFlag == true || tags.any { isAiTag(it) }
}
