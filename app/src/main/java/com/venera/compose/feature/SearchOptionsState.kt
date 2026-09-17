package com.venera.compose.feature

import com.venera.compose.source.model.SearchOptionGroup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** Source protocol values are positional: null, empty string and [] are distinct. */
object SearchOptionValues {
    fun defaults(groups: List<SearchOptionGroup>): List<String?> = groups.map { it.defaultKey }

    fun selectedKeys(value: String?): List<String> = try {
        (Json.parseToJsonElement(value ?: "[]") as JsonArray).map { it.jsonPrimitive.content }
    } catch (_: Exception) { emptyList() }

    fun normalize(group: SearchOptionGroup, value: String?): String? = when (group.type) {
        "multi-select" -> encode(selectedKeys(value).filter { it in group.options }.distinct())
        "dropdown" -> if (value == null) null else value.takeIf { it in group.options } ?: group.defaultKey
        else -> value?.takeIf { it in group.options } ?: group.defaultKey
    }

    fun toggle(group: SearchOptionGroup, value: String?, key: String): String? {
        if (key !in group.options) return value
        if (group.type != "multi-select") return key
        val selected = selectedKeys(value).toMutableList()
        if (!selected.remove(key)) selected.add(key)
        return encode(selected)
    }

    private fun encode(keys: List<String>): String = JsonArray(keys.map(::JsonPrimitive)).toString()
}

/** Reuse choices only for the same source and unchanged declaration. */
class SearchOptionsStore {
    private data class Entry(val groups: List<SearchOptionGroup>, val values: List<String?>)
    private val entries = mutableMapOf<String, Entry>()

    fun load(sourceKey: String, groups: List<SearchOptionGroup>): List<String?> {
        val previous = entries[sourceKey]
        val values = if (previous?.groups == groups) previous.values else SearchOptionValues.defaults(groups)
        entries[sourceKey] = Entry(groups, values)
        return values
    }

    fun apply(sourceKey: String, groups: List<SearchOptionGroup>, values: List<String?>): List<String?> {
        val normalized = groups.mapIndexed { index, group ->
            SearchOptionValues.normalize(group, if (index < values.size) values[index] else group.defaultKey)
        }
        entries[sourceKey] = Entry(groups, normalized)
        return normalized
    }
}

data class SearchTag(val namespace: String = "", val raw: String, val label: String = raw)

suspend fun composeSearchQuery(
    keyword: String,
    tags: List<SearchTag>,
    format: suspend (String, String) -> String
): String = (listOf(keyword.trim()) + tags.map { format(it.namespace, it.raw) })
    .filter { it.isNotBlank() }.joinToString(" ")
