package com.venera.compose.security.guard

/**
 * 屏蔽判据的两枚**纯词法声明**，从 `ContentGuardManager.kt` 搬进来。
 *
 * 为什么单独一颗文件：`ContentGuardManager` 吃 `Context`（还有 assets 预设表与 StartupTrace），
 * 整颗文件上不了桌面编译面；而它原先声明的 [GuardRule] 与 [AiTagKeys] 是两枚与平台无关的
 * 数据 —— 画廊的契约 `GalleryPorts.kt:10-11` 与判据 `gallery/domain/GalleryAi.kt:3` 都只认这两枚，
 * 不认那颗 manager。它们被困在同一个文件里，等于整个 `gallery/data` + `gallery/domain`
 * 被一行 `import android.content.Context` 钉死在 Android 面。
 *
 * **包名一字未改**（仍是 `com.venera.compose.security.guard`），所以五处
 * `import com.venera.compose.security.guard.GuardRule` 与本文件里 `isAiTagValue` 的取用
 * 全部原样解析，Android 侧行为零变更 —— 这次搬迁对旧文件是纯减法。
 *
 * 顺带解掉漫画侧同一颗债：`data/api/ContentGuardApi.kt` 也 import [GuardRule]，
 * 那颗契约此前同样因为这一枚类型而桌面不可编。
 */
data class GuardRule(
    val id: Long,
    val type: String, // "KEYWORD", "TAG", "AUTHOR", "COMIC_ID"
    val pattern: String,
    val isRegex: Boolean,
    val isEnabled: Boolean
)

/**
 * AI 标签词。归一（繁转简 + 小写）后**精确等值**。
 *
 * 出处：`ai` / `ai-generated` 逐字同 master 卡片 AI 角标的判据
 * （`lib/components/comic.dart:485-487`）；`ai生成` / `ai绘图` 取自本仓
 * [com.venera.compose.data.tags.TagNormalizer] 已归类好的中文形态（`:84`）。
 * 刻意不加"含 ai 字样就算"——那会误伤角色名与英文单词，master 也没这么做。
 * （反过来，裸 `ai` 这一枚在两站画廊上**也**是角色名：yande.re `tags=ai` 那 19 条是《Artery Gear》的 AI。
 * 漫画侧不动它 —— EH 那边 `AI生成` 那一系要用；画廊侧把它剔出去了，理由与读数见
 * [com.venera.compose.gallery.domain.GalleryAi]。）
 *
 * `internal` 而不是 private：画廊那把 AI 判据（[com.venera.compose.gallery.domain.GalleryAi]）
 * **以这张表为准再只收窄不放宽**，只把开关分家（用户 2026-09-29 拍板"画廊和漫画分开"= 分开的是开关）。
 * 词表复制第二份迟早会漂，漂的那一半通常是被抄的那份。
 */
internal val AiTagKeys = setOf("ai", "ai-generated", "ai生成", "ai绘图")
