package com.venera.compose.data.db

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import java.io.StringReader
import java.util.LinkedHashMap

/**
 * 持久层这一侧的 JSON 读法与写法（`org.json` 的替身）。
 *
 * 为什么要换掉 `org.json`：那套类是 Android framework 内置的，桌面侧没有，
 * 而在 JVM 单测里它是**返回默认值的桩** —— 备份那条链路最怕的就是"解析没报错但读出 0 条"。
 * 换成仓库已有的 gson（`:app` 早已依赖，`LocalFavoritesManager` 在用），不新增第二套 JSON 库。
 *
 * **备份文件的文本格式逐字不许变**（一个 `.venera` 包要跨设备、跨版本读），所以这里的每条
 * 读法都写明它对齐的是 android `JSONObject` / `JSONArray` 的哪一个方法、边界行为是什么：
 *  - 写：键序 = 插入序（android 用 LinkedHashMap，gson 的 [JsonObject] 同样是）；
 *    数字按 `Number.toString()` 原样出（`5` 不会变成 `5.0`）；非 ASCII 不转义；
 *    `null` 出一个 `null` 字面量（对应 `JSONObject.NULL`）—— 三条都有锚点用例钉着。
 *  - 读：严格 JSON（`JsonReader.isLenient = false`）—— 注释、裸键、单引号那些
 *    gson 宽容语法在这里一律**抛**，与改造前 `JSONArray(text)` 的判据一致，
 *    不能让"手工拼出来的半截归档"被读成合法数据。
 *
 * 与改造前**有意不同**的两处，都写在用例里：
 *  1. `getInt` / `getLong` 遇到 `"5"` 这种数字串：android 抛，这里也抛（不放宽）；
 *     遇到 `5.7` 这种小数：android 按 `Number.intValue()` 截成 5，这里同样截断，不四舍五入。
 *  2. 值里出现 `Map` / `List` 之外的陌生类型时，`org.json` 会 `toString()` 塞进去，
 *     这里直接抛 —— 备份行的类型是代码写死的，出现陌生类型就是代码错，不该往下写。
 */

private val STRICT_GSON = com.google.gson.Gson()

/** 归档里一行 = 一个 JSON object；`FavoriteBackupRows` / `ImageFavoriteBackupRows` 吃的是普通 Map。 */
internal typealias BackupRowMap = Map<String, Any?>

/** 任意 Kotlin 值 → JSON 树。对齐 android `JSONObject.wrap(Object)`，除上面第 2 条的收紧。 */
internal fun toJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull.INSTANCE
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Map<*, *> -> {
        val obj = JsonObject()
        value.forEach { (key, item) -> obj.add(key.toString(), toJsonElement(item)) }
        obj
    }

    is Collection<*> -> {
        val array = JsonArray()
        value.forEach { array.add(toJsonElement(it)) }
        array
    }

    else -> throw IllegalArgumentException(
        "备份行里有无法写进 JSON 的值类型 ${value.javaClass.name}：$value"
    )
}

/**
 * 有序键值 → [JsonObject]。用 [LinkedHashMap] 存一遍再转，是为了让**调用方给的键序**
 * 就是产出的键序（改造前 `JSONObject.put` 逐次插入就是这个行为）。
 */
internal fun jsonObject(fields: List<Pair<String, Any?>>): JsonObject {
    val ordered = LinkedHashMap<String, Any?>(fields.size)
    fields.forEach { (key, value) -> ordered[key] = value }
    return toJsonElement(ordered) as JsonObject
}

/** 一行备份（Map） → JSON object。 */
internal fun jsonObjectOf(row: BackupRowMap): JsonObject = toJsonElement(row) as JsonObject

/** 一批行 → JSON 数组。 */
internal fun jsonArray(rows: List<BackupRowMap>): JsonArray =
    JsonArray().apply { rows.forEach { add(jsonObjectOf(it)) } }

/** 归档成员文本 → JSON 数组；不是数组、语法不严格、尾部有多余内容 ⇒ 抛并带上原文开头。 */
internal fun parseJsonArray(text: String): JsonArray {
    val element = parseStrict(text)
    if (!element.isJsonArray) {
        throw IllegalStateException("备份成员不是 JSON 数组（读到 ${element.javaClass.simpleName}）：${text.preview()}")
    }
    return element.asJsonArray
}

/** 同上，但要求是 object（`meta.json`、PicaComic 的 `sync_data`）。 */
internal fun parseJsonObject(text: String): JsonObject {
    val element = parseStrict(text)
    if (!element.isJsonObject) {
        throw IllegalStateException("备份内容不是 JSON object（读到 ${element.javaClass.simpleName}）：${text.preview()}")
    }
    return element.asJsonObject
}

private fun parseStrict(text: String): JsonElement {
    val reader = JsonReader(StringReader(text)).apply { isLenient = false }
    return try {
        val element = STRICT_GSON.getAdapter(JsonElement::class.java).read(reader)
        // 读完还剩下东西 = 这份文本本身不合法（org.json 会把它当残缺数据抛错），不许只取前一半
        if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) {
            throw IllegalStateException("JSON 文本在有效内容之后还有多余字符：${text.preview()}")
        }
        element
    } catch (e: IllegalStateException) {
        throw e
    } catch (e: Exception) {
        throw IllegalStateException("JSON 解析失败：${e.message}；开头：${text.preview()}", e)
    } finally {
        runCatching { reader.close() }
    }
}

private fun String.preview(): String = take(120)

/**
 * `JsonObject` → 普通 Map（对齐改造前 `BackupManager.asMap(JSONObject)`）。
 * `JSONObject.NULL` 读成 null；数组读成 [List]、对象读成 [Map]。
 *
 * 这一条比改造前**更对**：android 的 `JSONArray` 本身就是 `List`，而 gson 的 [JsonArray] 不是，
 * 所以这里显式转成 List —— 否则 `FavoriteBackupRows.strings("tags")` 会走进 `else -> emptyList()`，
 * 恢复出来的收藏就没标签了。
 */
internal fun asMap(obj: JsonObject): Map<String, Any?> {
    val map = LinkedHashMap<String, Any?>(obj.size())
    for ((key, value) in obj.entrySet()) {
        map[key] = value.toPlainValue()
    }
    return map
}

/** JSON 值 → Kotlin 值：null / String / Boolean / Long / Double / List / Map。 */
internal fun JsonElement.toPlainValue(): Any? = when (val self = this) {
    is JsonNull -> null
    is JsonPrimitive -> when {
        self.isBoolean -> self.asBoolean
        self.isNumber -> self.numberOrNull() ?: self.asString // 读不成数字的（理论不该出现）原样交文本，不悄悄给 0
        else -> self.asString
    }

    is JsonArray -> self.map { it.toPlainValue() }
    is JsonObject -> asMap(self)
    else -> throw IllegalStateException("无法解读的 JSON 值：${self.javaClass.name}")
}

/** JSON 数字 → Long（整数字面量）或 Double（带小数点），与 android 解析器交回的类型同形。 */
private fun JsonPrimitive.numberOrNull(): Number? =
    asString.toLongOrNull() ?: asString.toDoubleOrNull()

/** 元素 → 文本；对齐 android `JSON.toString(Object)`：数字/布尔给字面文本，数组/对象给紧凑 JSON。 */
private fun JsonElement.coerceText(): String = when (this) {
    is JsonNull -> throw IllegalStateException("值必须是文本，实际是 null")
    is JsonPrimitive -> asString
    else -> toString()
}

// ── 读法：与 android JSONObject 的 getX / optX 一一对齐，缺列与类型不符的判据写在每条上 ──

/** 原 `getString(name)`：键不存在、值为 null、或类型读不出文本 ⇒ 抛并带键名。 */
internal fun JsonObject.textValue(key: String): String {
    val element = requireElement(key)
    if (element is JsonNull) {
        throw IllegalStateException("备份行字段「$key」是 null，而按建表/写入口径它不该为 null")
    }
    return element.coerceText()
}

/** 原 `getInt(name)` / `getLong(name)`：必须是 JSON 数字；小数按 `Number.intValue()` 截断；字符串不算数字（抛）。 */
internal fun JsonObject.longValue(key: String): Long {
    val element = requireElement(key)
    if (element !is JsonPrimitive || !element.isNumber) {
        throw IllegalStateException("备份行字段「$key」不是 JSON 数字（${element.asDescriptor()}）")
    }
    return element.numberOrThrow(key)
}

/** 同 [longValue]，收成 Int（与 android 的 `intValue()` 一样是窄化，不做范围检查）。 */
internal fun JsonObject.intValue(key: String): Int = longValue(key).toInt()

/** 原 `optString(name, fallback)`：缺键或 null 给 fallback，其他类型按 `JSON.toString` 取文本。 */
internal fun JsonObject.optTextValue(key: String, fallback: String = ""): String {
    val element = this[key]
    if (element == null || element is JsonNull) return fallback
    return element.coerceText()
}

/** 原 `optInt(name, fallback)`：缺键/null 给 fallback；数字截断；布尔 1/0；数字串也认（android 认）。 */
internal fun JsonObject.optIntValue(key: String, fallback: Int): Int =
    optLongValue(key, fallback.toLong()).toInt()

/** 原 `optLong(name, fallback)`，判据同 [optIntValue]。 */
internal fun JsonObject.optLongValue(key: String, fallback: Long): Long {
    val element = this[key]
    if (element == null || element is JsonNull) return fallback
    if (element is JsonPrimitive) {
        if (element.isNumber) return element.numberOrThrow(key)
        if (element.isBoolean) return if (element.asBoolean) 1L else 0L
        return element.asString.trim().toLongOrNull()
            ?: element.asString.trim().toDoubleOrNull()?.toLong()
            ?: fallback
    }
    return fallback
}

/** 原 `getJSONObject(i)` 的对象版：键不存在或不是对象 ⇒ 抛。 */
internal fun JsonObject.objectValue(key: String): JsonObject {
    val element = requireElement(key)
    if (!element.isJsonObject) {
        throw IllegalStateException("备份行字段「$key」不是 JSON object（${element.asDescriptor()}）")
    }
    return element.asJsonObject
}

/** [JsonArray] 取一行：越界与 null 元素都抛，不返回"看起来像空行"的东西。 */
internal fun JsonArray.objectAt(index: Int): JsonObject {
    val element = get(index) // 越界时 gson 自己抛 IndexOutOfBounds，与 org.json 一致
    if (!element.isJsonObject) {
        throw IllegalStateException("数组第 $index 项不是 JSON object（${element.asDescriptor()}）")
    }
    return element.asJsonObject
}

private fun JsonObject.requireElement(key: String): JsonElement =
    this[key] ?: throw IllegalStateException("备份行缺字段「$key」，现有字段：${keySet()}")

private fun JsonElement.asDescriptor(): String =
    if (this is JsonPrimitive) "值=$asString" else "类型=${javaClass.simpleName}"

/**
 * JSON 数字 → Long。整数字面量直读；带小数点的按 Java `Number.longValue()` 截断
 * （改造前 `((Number) object).intValue()` 就是这个语义，四舍五入反而会改数据）。
 */
private fun JsonPrimitive.numberOrThrow(key: String): Long {
    val literal = asString
    literal.toLongOrNull()?.let { return it }
    val asDouble = literal.toDoubleOrNull()
        ?: throw IllegalStateException("备份行字段「$key」是数字但读不成整数：$literal")
    return asDouble.toLong()
}
