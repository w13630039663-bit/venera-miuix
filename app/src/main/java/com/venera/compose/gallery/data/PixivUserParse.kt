package com.venera.compose.gallery.data

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * `user_previews` 那一族答复的解析（关注列表与推荐用户共用）。
 *
 * 现量口径（pixez-miuix 的 `UserPreview.kt` / `UserRepository.kt` 两侧同形，2026-10-17 抄的是它那份）：
 * ```
 * {
 *   "user_previews": [
 *     {
 *       "user": { "id": 123, "name": "…", "account": "…",
 *                 "profile_image_urls": { "medium": "https://…px_170x170…" } },
 *       "illusts": [ … ], "novels": [ … ], "is_muted": false
 *     }
 *   ],
 *   "next_url": "https://app-api.pixiv.net/v1/user/recommended?filter=for_android&offset=30"
 * }
 * ```
 *
 * 同一套口径照旧：
 * 1. **整份读不懂 = 抛**（不是交空表）；
 * 2. **有 N 条却一条都认不出 = 也抛**（站方改键名的表现就是整栏消失，那不能说成"你没关注人"）。
 *
 * ⚠️ `illusts` / `novels` 那两格**不读**：这一栏要的是"人"，摆作品缩略图是另一件事
 * （pixez 那份预览图也用，但我们的首页已经有推荐墙了，混进来只会重复）。
 */
internal object PixivUserParse {

    private val json = Json { ignoreUnknownKeys = true }

    fun userPreviews(body: String, who: String): PixivUserPage {
        val root = parseObject(body, who)
        // app-api 的错误答复是平铺在 `error` 上的对象（同 `PixivAppParse`）。
        (root["error"] as? JsonObject)?.let { error ->
            val status = error["status_code"].textOrNull()
            val message = error["message"].textOrNull().orEmpty()
            val reason = error["reason"].textOrNull().orEmpty()
            val userMessage = error["user_message"].textOrNull()
            throw IOException(
                "$who 回 error" + (status?.let { "（$it）" } ?: "") + "：" +
                    listOf(message, userMessage, reason).filter { !it.isNullOrBlank() }
                        .joinToString(" · ").ifBlank { "站方没给原因" },
            )
        }
        val previews = root["user_previews"] as? JsonArray
            ?: throw IOException("$who 答复里没有 user_previews 数组：${body.take(SAMPLE)}")
        val users = previews.mapNotNull { element ->
            val record = element as? JsonObject ?: return@mapNotNull null
            val user = record["user"]?.jsonObject ?: return@mapNotNull null
            val id = user["id"].numberOrStringOrNull()?.toLongOrNull() ?: return@mapNotNull null
            val name = user["name"].textOrNull()
                ?: user["account"].textOrNull()
                ?: return@mapNotNull null
            PixivUser(
                id = id,
                name = name,
                account = user["account"].textOrNull().orEmpty(),
                avatarUrl = user["profile_image_urls"]?.jsonObject?.get("medium").textOrNull().orEmpty(),
                muted = record["is_muted"].isTrue(),
            )
        }
        if (previews.isNotEmpty() && users.isEmpty()) {
            throw IOException("$who 给了 ${previews.size} 条，但没有一条认得出用户号与名字 —— 站方大概改了字段名")
        }
        return PixivUserPage(users = users, nextUrl = root["next_url"].textOrNull())
    }

    private fun parseObject(body: String, who: String): JsonObject =
        runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw IOException("$who 回来的不是 JSON 对象（可能是风控页或被换盾）：${body.take(SAMPLE)}") }

    private fun JsonElement?.textOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonElement?.numberOrStringOrNull(): String? = textOrNull()

    private fun JsonElement?.isTrue(): Boolean = textOrNull()?.lowercase() == "true"

    private const val SAMPLE = 120
}
