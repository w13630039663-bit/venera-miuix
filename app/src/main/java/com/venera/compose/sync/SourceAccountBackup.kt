package com.venera.compose.sync

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.venera.compose.data.db.jsonObject

/**
 * 归档里「漫画源账号」那一栏的编解码（纯 JVM：不碰磁盘、不碰 Context、不碰 Android 类型）。
 *
 * 抽出来的理由与 [GalleryBackupRows] / [FavoriteBackupRows] 是同一条：
 * 这一层出错**不抛异常**，键名对不上只是恢复出来少一块数据，屏上表现为
 * 「导入成功，但还是没登录」。能测的部分全放这里，磁盘读写留在调用方。
 *
 * ## 这一栏装的是什么
 *
 * 源侧 `this.saveData(k, v)` 落下的那份 `filesDir/comic_source/<key>.data`
 * （见 `engine/JsSourceDataStore`），以及 `ComicSourceManager` 元数据里
 * 用户改过的 `enabled` / `pinned` / 展示名。**不包括源脚本本身** ——
 * 这是刻意的：脚本是可从仓库重装的可再生物，而账号与设置是用户攒下的、
 * 重装源换不回来的东西。两者混在一栏里，"恢复登录态"就会被"要不要
 * 覆盖本机的源脚本"这个无关的问题拖住。
 *
 * ## 为什么拆分导出时会动这一行
 *
 * `.data` 档里的键有两类，混在一个 JSON 对象里：
 * - **账号类**：`account` / `token` / `_cookies` / `_localStorage` —— 登录凭据；
 * - **设置类**：`settings`（`saveSetting` 写在这里）、`setting`、以及源自己塞的零散键。
 *
 * 用户勾了「只导出账号」时，[Kind.SETTINGS] 那类留在本机，不进包；勾了全量则整行带走。
 * 判定按**顶层键名**，不按源 —— 源脚本五花八门，只有这组键名是引擎约定的。
 *
 * ## 一行在归档里的形状
 *
 * ```json
 * {"fileName":"ehentai.js","key":"ehentai","name":"E-Hentai","version":"1.4.2",
 *  "enabled":true,"pinned":false,"data":{"account":"ok","settings":{...}}}
 * ```
 *
 * `data` **原样内嵌**（解出的 JSON 对象，不转字符串）—— 转字符串会让整份归档在
 * 文本编辑器里不可读，也让"少带了一个键"这类问题在肉眼层面藏起来。
 * `fileName` 是身份标识（与 [com.venera.compose.source.InstalledSourceMeta] 同一口径：
 * `copy_manga.js` 与 `copy_manga_multi_accounts.js` 声明同一个 `key`，只有文件名分得开）。
 */
internal object SourceAccountBackup {

    /** 归档 member 名。`meta.json` 里对应的计数键是 [META_COUNT_KEY]。 */
    const val MEMBER = "source_accounts.json"

    /** `meta.json` 里这一栏的条数键。 */
    const val META_COUNT_KEY = "sourceAccountCount"

    /**
     * 顶层键里属于**登录凭据**的那些。不在这个集合里的（尤其 `settings`）都算设置，
     * 「只导出账号」时会被摘掉。
     *
     * `_localStorage` 也算账号：网页登录抓下来的 accessToken 就存在那里
     * （`ccc.js` 取它解析 JWT），丢掉等于白登录一次。
     */
    val ACCOUNT_KEYS = setOf("account", "token", "_cookies", "_localStorage")

    /** 导出粒度。用户可以在设置页里单独选「只导出账号」。 */
    enum class Kind {
        /** 账号凭据 + 源设置，整行带走。 */
        ALL,

        /** 只带登录凭据；源设置留在本机（换机后重新调即可，不必连偏好一起搬）。 */
        ACCOUNT_ONLY,
    }

    /**
     * 归档里的一行。
     *
     * [data] 是 `.data` 档解析出来的顶层对象；没有该档、或档是空的 ⇒ 空对象
     * （"源装了但从没存过东西"，与"这一行丢了"是两件事，不能都表示成缺键）。
     */
    data class Row(
        val fileName: String,
        val key: String,
        val name: String,
        val version: String,
        val enabled: Boolean,
        val pinned: Boolean,
        val data: Map<String, Any?>,
    ) {
        /** 这一行里有没有值得恢复的登录凭据。 */
        val hasAccount: Boolean get() = data.keys.any { it in ACCOUNT_KEYS }

        /** 这一行里有没有源设置。 */
        val hasSettings: Boolean get() = data.keys.any { it !in ACCOUNT_KEYS }
    }

    /**
     * 把一行拼进归档数组（键序固定：先身份、后状态、最后 data —— 改动键序会让
     * 跨版本读的文本锚点用例红，见 `CoreTableBackupTest` 那套口径）。
     */
    fun encode(rows: List<Row>): JsonArray = JsonArray().apply {
        for (row in rows) {
            add(
                jsonObject(
                    listOf(
                        "fileName" to row.fileName,
                        "key" to row.key,
                        "name" to row.name,
                        "version" to row.version,
                        "enabled" to row.enabled,
                        "pinned" to row.pinned,
                        "data" to row.data,
                    )
                )
            )
        }
    }

    /**
     * 解一行 `.data` 档的文本。
     *
     * 档不是合法 JSON 对象、或顶层不是对象 ⇒ [空对象]，**不抛**：
     * 一份坏掉的源数据档不该让整个归档导不进来 —— 它能提供的只是登录态，
     * 而"登录态没了"是用户重新登一次就能恢复的损失，"整个包导入失败"不是。
     * 与 [GalleryBackupRows] 那边对坏档抛出去的判据不同，是因为那边那一栏
     * 是归档的**主体内容**，而这一栏是可再生的附属物。
     */
    fun decodeSourceData(raw: String?): Map<String, Any?> {
        if (raw.isNullOrBlank()) return emptyMap()
        val element = runCatching { JsonParser.parseString(raw) }.getOrNull() ?: return emptyMap()
        if (!element.isJsonObject) return emptyMap()
        return jsonToPlainMap(element.asJsonObject)
    }

    /**
     * 按 [kind] 过滤一行的 data：只导出账号时，非账号键（`settings` 那一类）全部摘掉。
     * 过滤后[Row.data]为空的行**仍然保留** —— 源本身（装了哪个源、启用没有）也是用户的状态，
     * 且留一行在归档里比让同一栏的长度随勾选项跳变更容易解释。
     */
    fun filterData(data: Map<String, Any?>, kind: Kind): Map<String, Any?> = when (kind) {
        Kind.ALL -> data
        Kind.ACCOUNT_ONLY -> data.filterKeys { it in ACCOUNT_KEYS }
    }

    /**
     * 解析归档里的整个数组。
     *
     * 缺 member（v5 及更早的归档）⇒ 空列表（[raw] 为 null），那本来就没有这一栏；
     * 不是合法 JSON 数组 ⇒ 抛（[raw] 非空但解不开），由调用方在**动任何存储之前**失败。
     * 单行缺 `fileName` ⇒ 跳过：没有它就没法把数据写回哪个源，留着只会变成一条幽灵登录态。
     */
    fun decode(raw: String?): List<Row> {
        if (raw.isNullOrBlank()) return emptyList()
        val element = JsonParser.parseString(raw)
        check(element.isJsonArray) {
            "备份里的 $MEMBER 不是 JSON 数组（读到 ${element.javaClass.simpleName}）"
        }
        val rows = mutableListOf<Row>()
        for (item in element.asJsonArray) {
            if (!item.isJsonObject) continue
            val obj = item.asJsonObject
            val fileName = obj.stringOrNull("fileName")?.trim().orEmpty()
            if (fileName.isBlank()) continue
            rows += Row(
                fileName = fileName,
                key = obj.stringOrNull("key").orEmpty(),
                name = obj.stringOrNull("name").orEmpty(),
                version = obj.stringOrNull("version").orEmpty(),
                // 缺状态键时的默认值 = "装了并启用"：这份源的脚本要真在本机存在才谈得上恢复，
                // 而恢复出来的源默认被停用，用户看到的是"导入了但搜不到东西"。
                enabled = obj.booleanOr("enabled", true),
                pinned = obj.booleanOr("pinned", false),
                data = obj.getAsJsonObject("data")?.let { jsonToPlainMap(it) } ?: emptyMap(),
            )
        }
        return rows
    }

    /**
     * 把解析出来的 data 编码回 `.data` 档的文本。
     *
     * 用 [JsonObject.toString] 而不是另起一套序列化：归档里这份东西的**唯一**归宿
     * 就是 `JsSourceDataStore` 的那只 gson（`File.writeText(gson.toJson(map))`），
     * 两边用同一个 gson 才谈得上"写回去的档跟原档逐字同形"。
     */
    fun encodeSourceData(data: Map<String, Any?>): String {
        val obj = JsonObject()
        for ((key, value) in data) {
            obj.add(key, plainToJson(value))
        }
        return obj.toString()
    }

    // ── 小工具：gson JsonElement ↔ 普通 Map/List/标量 ──

    private fun jsonToPlainMap(obj: JsonObject): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>(obj.size())
        for ((key, value) in obj.entrySet()) {
            map[key] = jsonToPlain(value)
        }
        return map
    }

    private fun jsonToPlain(element: com.google.gson.JsonElement): Any? = when {
        element.isJsonNull -> null
        element.isJsonObject -> jsonToPlainMap(element.asJsonObject)
        element.isJsonArray -> element.asJsonArray.map { jsonToPlain(it) }
        else -> {
            val primitive = element.asJsonPrimitive
            when {
                primitive.isBoolean -> primitive.asBoolean
                // 数值刻意收成 Double 而不是 gson 的 LazilyParsedNumber：这份 Map 要交给
                // `JsSourceDataStore` 那只 **plain gson** 落盘（`gson.fromJson(text, Map)` /
                // `gson.toJson(map)`），plain gson 解数字给的就是 Double。留一个
                // LazilyParsedNumber 在 Map 里，跟 store 自己读出来的那份类型不一致 ——
                // 一边是 Double、一边是 LazilyParsedNumber，`==` 判 false 而没有任何异常。
                primitive.isNumber -> primitive.asDouble
                else -> primitive.asString
            }
        }
    }

    private fun plainToJson(value: Any?): com.google.gson.JsonElement = when (value) {
        null -> com.google.gson.JsonNull.INSTANCE
        is com.google.gson.JsonElement -> value
        is String -> com.google.gson.JsonPrimitive(value)
        is Boolean -> com.google.gson.JsonPrimitive(value)
        is Number -> com.google.gson.JsonPrimitive(value)
        is Map<*, *> -> JsonObject().apply {
            for ((k, v) in value) add(k.toString(), plainToJson(v))
        }

        is Collection<*> -> JsonArray().apply { value.forEach { add(plainToJson(it)) } }
        // 源的 data 是 gson 解出来的，理论只有上面几类。真出现陌生类型，
        // 交给 toString 落成文本，不抛 —— 一行数据不该把整包导出拖垮。
        else -> com.google.gson.JsonPrimitive(value.toString())
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        return if (element.isJsonNull) null else element.asString
    }

    private fun JsonObject.booleanOr(key: String, fallback: Boolean): Boolean {
        val element = get(key) ?: return fallback
        if (element.isJsonNull) return fallback
        return runCatching { element.asBoolean }.getOrDefault(fallback)
    }
}
