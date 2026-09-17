package com.venera.compose.source.js

import com.google.gson.Gson
import com.venera.compose.source.model.Comment
import com.venera.compose.source.model.SearchOptionGroup

/** Pure protocol conversions, shared by every list entry point. */
internal object SourcePayloadParser {
    private val gson = Gson()

    fun data(json: String): Any? {
        val envelope = gson.fromJson(json, Map::class.java)
            ?: error("漫画源返回空响应")
        check(envelope["success"] == true) { envelope["error"]?.toString() ?: "漫画源调用失败" }
        return envelope["data"]
    }

    fun id(value: Any?): String = when (value) {
        is Number -> if (value.toDouble() == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        else -> value?.toString().orEmpty()
    }

    fun rating(map: Map<*, *>): Float? = (map["stars"] ?: map["rating"])
        ?.toString()?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }

    fun likesCount(map: Map<*, *>): Int? = (map["likesCount"] ?: map["likes"])
        ?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE }
        ?.toInt()

    fun searchOptions(raw: Any?): List<SearchOptionGroup> = (raw as? List<*>)?.mapNotNull { entry ->
        val map = entry as? Map<*, *> ?: return@mapNotNull null
        val options = linkedMapOf<String, String>()
        (map["options"] as? List<*>)?.forEach { item ->
            val text = item?.toString().orEmpty()
            val separator = text.indexOf('-')
            if (separator >= 0) options.putIfAbsent(text.substring(0, separator), text.substring(separator + 1))
        }
        val type = map["type"]?.toString() ?: "select"
        val default = map["default"]?.let {
            if (type == "multi-select") {
                if (it is List<*>) gson.toJson(it) else it.toString()
            } else if (it is Number && it.toDouble() == it.toLong().toDouble()) it.toLong().toString()
            else it.toString()
        }
        SearchOptionGroup(map["label"]?.toString().orEmpty(), options, type, default)
    }.orEmpty()

    fun comment(map: Map<*, *>): Comment? = map["content"]?.toString()?.let { content ->
        Comment(
            id = id(map["id"]),
            userName = map["userName"]?.toString() ?: "读者",
            avatar = map["avatar"]?.toString(),
            content = content,
            time = map["time"]?.toString(),
            replyCount = (map["replyCount"] as? Number)?.toInt(),
            score = (map["score"] as? Number)?.toInt() ?: 0,
            voteStatus = (map["voteStatus"] as? Number)?.toInt() ?: 0,
            isLiked = map["isLiked"] == true
        )
    }
}
