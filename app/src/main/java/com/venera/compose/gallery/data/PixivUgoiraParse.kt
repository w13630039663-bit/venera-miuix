package com.venera.compose.gallery.data

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * pixiv `/ajax/illust/{id}/ugoira_meta` 的形状解析（2026-10-23，动图播放）。
 *
 * 口径与 [PixivFeedParse] 逐字一致（手写逐字段、不给 `@Serializable` DTO）：
 * 1. **整份读不懂 = 抛**（缺 body、坏 JSON、`error=true`）；
 * 2. **认得出结构但没有可播的帧 = 交 null**（非 ugoira 那条站方回 `error=true`，那是失败；
 *    而真 ugoira 却回空 `frames` 是"答上了、没法播"，屏上不摆播放、退回静帧）。
 *
 * 现量（真号 151160372，匿名、无凭据）：
 * `{"error":false,"body":{"src":"…_ugoira600x600.zip","originalSrc":"…_ugoira1920x1080.zip",
 * "mime_type":"image/jpeg","frames":[{"file":"000000.jpg","delay":1000}, …]}}`。
 * `delay` 现量有 0 出现（站方偶发），[PixivUgoiraFrame] 一侧夹到地板值。
 */
internal object PixivUgoiraParse {

    private val json = Json { ignoreUnknownKeys = true }

    private const val ERROR = "error"
    private const val MESSAGE = "message"
    private const val BODY = "body"
    private const val SAMPLE = 120

    /**
     * 答复 → [PixivUgoira]。null = 这条没有可播的逐帧（屏上退回静帧）。
     *
     * 抛的情形：整份不是 JSON、`error=true`（含"指定的 ID 并非 ugoira"那一档 —— 那是**取数点认错号**，
     * 必须炸出来而不是静默当"没动画"，否则一个坏号会在屏上读成"这条只是静图"）。
     */
    fun ugoiraMeta(body: String): PixivUgoira? {
        val root = parseObject(body)
        if (root[ERROR].isTrue()) {
            throw IOException(
                "pixiv 动图元信息回 error=true（message=${root[MESSAGE]?.jsonPrimitive?.contentOrNull.orEmpty()}）",
            )
        }
        val meta = root[BODY]?.jsonObject ?: throw IOException("pixiv 动图元信息答复里没有 body")
        val src = meta["src"].textOrNull() ?: return null
        val rows = (meta["frames"] as? JsonArray).orEmpty().mapNotNull { element ->
            val frame = element as? JsonObject ?: return@mapNotNull null
            val file = frame["file"].textOrNull() ?: return@mapNotNull null
            val delay = frame["delay"].numberOrStringOrNull()?.toIntOrNull() ?: 0
            PixivUgoiraFrame(file = file, delay = delay.coerceAtLeast(UGOIRA_MIN_DELAY_MILLIS))
        }
        // 认得出 zip 地址、却一帧都没认出来 = 站方没给可用逐帧，交 null 由屏上退静帧。
        if (rows.isEmpty()) return null
        return PixivUgoira(
            src = src,
            originalSrc = meta["originalSrc"].textOrNull() ?: src,
            mimeType = meta["mime_type"].textOrNull() ?: "image/jpeg",
            frames = rows,
        )
    }


    /**
     * 解析 App API 回传的 ugoira_metadata 格式。
     * {"ugoira_metadata": {"zip_urls": {"medium": "..."}, "frames": [{"file": "000000.jpg", "delay": 100}]}}
     */
    fun ugoiraMetadataAppApi(body: String): PixivUgoira? {
        val root = parseObject(body)
        val meta = (root["ugoira_metadata"] as? JsonObject) ?: return null
        val zipUrls = meta["zip_urls"] as? JsonObject
        val src = zipUrls?.get("medium")?.textOrNull() ?: return null
        val orig = zipUrls?.get("original")?.textOrNull() ?: src
        val rows = (meta["frames"] as? JsonArray).orEmpty().mapNotNull { element ->
            val frame = element as? JsonObject ?: return@mapNotNull null
            val file = frame["file"].textOrNull() ?: return@mapNotNull null
            val delay = frame["delay"].numberOrStringOrNull()?.toIntOrNull() ?: 0
            PixivUgoiraFrame(file = file, delay = delay.coerceAtLeast(UGOIRA_MIN_DELAY_MILLIS))
        }
        if (rows.isEmpty()) return null
        return PixivUgoira(
            src = src,
            originalSrc = orig,
            mimeType = "image/jpeg",
            frames = rows
        )
    }

    private fun parseObject(body: String): JsonObject =
        runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw IOException("pixiv 动图元信息回来的不是 JSON 对象（可能是维护页或被换盾）：${body.take(SAMPLE)}") }

    private fun JsonElement?.textOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonElement?.numberOrStringOrNull(): String? = textOrNull()

    private fun JsonElement?.isTrue(): Boolean = textOrNull()?.lowercase() == "true"
}
