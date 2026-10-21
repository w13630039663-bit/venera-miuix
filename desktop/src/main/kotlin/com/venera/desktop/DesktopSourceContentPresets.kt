package com.venera.desktop

/**
 * 源级内容分级预设表 —— 桌面侧的 `sourcePresets`。
 *
 * ## 为什么需要它（安全评审 C）
 *
 * 安卓 `ContentGuardManager.maskHit` 的**第二道**判据就是这张表
 * （`ContentGuardManager.kt:441-443`）：
 *
 * ```kotlin
 * if (sourceKey != null) {
 *     val level = sourcePresets[resolveSourceKey(sourceKey)]
 *     if (level == "nsfw" || level == "mixed") return true
 * }
 * ```
 *
 * 桌面缺这一整道，于是**历史记录那种不带 tags 的模型永远判不出成人向**：
 * `HomeHomeWidgets.kt:165` 调 `coverMaskStateOf` 时只传
 * `sourceKey/title/author/comicId`，**不传 tags**，而原来的桌面判据只看 tags
 * ⇒ `looksAdult(emptyList())` 恒 false ⇒ **历史卡封面恒裸**。
 *
 * 这不是"判据更宽松"，是**少了一整道判据**。
 *
 * ## 数据从哪来
 *
 * `app/src/main/assets/source_content_warning.json`（3751 字节，按源 key 索引，
 * 三态 `safe` / `mixed` / `nsfw`，`defaultLevel` = `safe`）。
 * 桌面已把它加进 `desktop/build.gradle.kts` 的 resources include 清单 ——
 * 在此之前它**不在清单里**，所以桌面根本读不到。
 *
 * ## 解析为什么这么写
 *
 * 不引 JSON 库、不手搓完整解析器：这张表的形状是**扁平的**
 * （`"key": { "level": "...", … }`），两条线性正则取 `key → level` 就够，
 * 无嵌套量词（无 ReDoS 面）。
 *
 * **解析失败返回空表 + 记成因**：空表意味着"不按源级预设判"，那是**更松**的方向，
 * 所以必须能被看见（[lastError] 与 `DesktopSourceContentPresetsTest` 钉住），
 * 不能静默 —— 静默会让读的人以为这一道判据在起作用。
 */
internal object DesktopSourceContentPresets {

    /** 上一次解析的失败成因（`null` = 上次解析成功）。 */
    @Volatile
    var lastError: String? = null
        private set

    /** `源 key → 等级`（`safe` / `mixed` / `nsfw`）。解析失败时是空表（**更松**的方向）。 */
    val levels: Map<String, String> by lazy { parse() }

    /**
     * 这个源要不要按"可能成人"处理。
     *
     * `nsfw` 与 `mixed` 都算命中 —— 与安卓 `maskHit` 那句 `||` 逐字一致。
     * `mixed` 也命中是有意的：e-hentai 这类"确实有非 H 内容但成人向占多数"的站，
     * 安卓宁可多遮（多糊一张封面的代价远小于错放一张）。
     */
    fun isGuarded(sourceKey: String): Boolean = when (levels[sourceKey]) {
        "nsfw", "mixed" -> true
        else -> false
    }

    private fun parse(): Map<String, String> {
        val text = try {
            val stream = DesktopSourceContentPresets::class.java
                .getResourceAsStream("/source_content_warning.json")
            if (stream == null) {
                // 资源清单漏了就会走到这里。必须留下成因：空表 = 这一道判据没生效。
                lastError = "包内找不到 source_content_warning.json（resources include 清单漏了？）"
                return emptyMap()
            }
            stream.bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            lastError = "读预设表失败：${t.javaClass.simpleName} ${t.message?.take(80)}"
            return emptyMap()
        }

        // 每条形如  "jm": { "level": "nsfw", "reason": "…" }
        // 两条线性正则：先切每个 `"key": {…}` 块，再取块里的 level。
        val out = LinkedHashMap<String, String>()
        val entry = Regex("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\\{([^}]*)\\}")
        val levelOf = Regex("\"level\"\\s*:\\s*\"(safe|mixed|nsfw)\"")
        for (m in entry.findAll(text)) {
            val key = m.groupValues[1]
            val level = levelOf.find(m.groupValues[2])?.groupValues?.get(1) ?: continue
            out[key] = level
        }

        lastError = if (out.isEmpty()) "预设表解析出 0 条 —— 文件形状变了？" else null
        return out
    }
}
