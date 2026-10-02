package com.venera.compose.data.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 桌面侧实现：**一个 prefs 名 = 一个 JSON 文件**，落在 [PathProvider.subDir] 的 `prefs/` 下。
 *
 * 保持"一名一文件"是为了跟 Android 那 13 个 XML 边界一一对得上，对照排查时不用猜哪张表。
 *
 * 三条行为判据：
 *  - 文件不存在 ⇒ 空表（全新数据，合法）；
 *  - 文件存在但解析不出来 ⇒ **抛，并把文件路径写进消息**。半截 JSON 当成"没配置"是最难查的一类
 *    假读数：界面会回到默认档，用户以为设置丢了，日志里什么都没有；
 *  - 写 ⇒ 先落 `<name>.json.tmp` 再整体替换目标名。Windows 上对已存在的目标改名会失败，
 *    所以显式要 `REPLACE_EXISTING`，能 `ATOMIC_MOVE` 就优先原子。
 */
class JsonKeyValueStore(
    private val name: String,
    private val paths: PathProvider,
) : KeyValueStore {

    init {
        // 名字直接拼进文件名，所以段校验跟 PathProvider 同口径：不许空、不许 `..`、不许带分隔符。
        require(name.isNotBlank() && name != ".." && !name.contains('/') && !name.contains('\\')) {
            "prefs 名不合法：$name"
        }
    }

    /** 每次都从磁盘重读，实例里不留缓存。 */
    private fun table(): Map<String, JsonElement> {
        val target = file
        if (!target.exists()) return emptyMap()
        val text = try {
            target.readText()
        } catch (e: IOException) {
            throw IllegalStateException("读不到键值文件 ${target.path}：${e.message}", e)
        }
        val element = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw IllegalStateException("键值文件 ${target.path} 解析失败，不许当成「没配置」：${e.message}", e)
        }
        return element as? JsonObject
            ?: throw IllegalStateException("键值文件 ${target.path} 顶层不是 JSON 对象")
    }

    override fun getString(k: String, d: String?): String? {
        val el = table()[k] ?: return d
        val p = el as? JsonPrimitive
        if (p == null || !p.isString) throw mismatch(k, el, "字符串")
        return p.content
    }

    override fun getBoolean(k: String, d: Boolean): Boolean {
        val el = table()[k] ?: return d
        return numberContent(k, el).toBooleanStrictOrNull() ?: throw mismatch(k, el, "布尔值")
    }

    override fun getInt(k: String, d: Int): Int {
        val el = table()[k] ?: return d
        return numberContent(k, el).toIntOrNull() ?: throw mismatch(k, el, "整数")
    }

    override fun getLong(k: String, d: Long): Long {
        val el = table()[k] ?: return d
        return numberContent(k, el).toLongOrNull() ?: throw mismatch(k, el, "长整数")
    }

    override fun getFloat(k: String, d: Float): Float {
        val el = table()[k] ?: return d
        return numberContent(k, el).toFloatOrNull() ?: throw mismatch(k, el, "小数")
    }

    override fun getStringSet(k: String): Set<String> {
        val el = table()[k] ?: return emptySet()
        val arr = el as? JsonArray ?: throw mismatch(k, el, "字符串数组")
        return arr.map { item ->
            val p = item as? JsonPrimitive
            if (p == null || !p.isString) throw IllegalStateException("键 $k 在 ${file.path} 里的数组元素不是字符串")
            p.content
        }.toSet()
    }

    override fun put(k: String, v: Any?) {
        if (v == null) {
            remove(k)
            return
        }
        mutate { it[k] = toJsonElement(k, v) }
    }

    override fun putAll(values: Map<String, Any?>) {
        mutate { table ->
            values.forEach { (key, v) ->
                if (v == null) table.remove(key) else table[key] = toJsonElement(key, v)
            }
        }
    }

    override fun remove(k: String) {
        mutate { it.remove(k) }
    }

    /** 清空只删键，文件留着（空表 `{}`）—— 与 SharedPreferences 的 clear 同形。 */
    override fun clear() {
        mutate { it.clear() }
    }

    override fun keys(): Set<String> = table().keys.toSet()

    // ------------------------------------------------------------------ 内部

    private val file: File by lazy { File(paths.subDir("prefs"), "$name.json") }

    /**
     * 读-改-写成一个整体，锁在类级别。
     *
     * 桌面侧同一个名字可能被多处各自 new 一个实例（Android 的 SharedPreferences 是进程内共享的，
     * 换成各读各的缓存就会互相看不见对方的写），所以：不加缓存、每次落盘前重读、写全程串行。
     * 代价是每次读多解析一遍一个几百字节的小文件 —— 宁可慢，不可静默交错。
     */
    private fun mutate(block: (MutableMap<String, JsonElement>) -> Unit) = synchronized(writeLock) {
        val next = LinkedHashMap(table())
        block(next)
        write(JsonObject(next))
    }

    private fun write(table: JsonObject) {
        val target = file
        val tmp = File(target.parentFile, "${target.name}.tmp")
        try {
            tmp.writeText(table.toString())
            replace(tmp, target)
        } catch (e: IOException) {
            tmp.delete()
            throw IllegalStateException("写键值文件 ${target.path} 失败：${e.message}", e)
        }
    }

    private fun replace(tmp: File, target: File) {
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            // 文件系统不支持"原子 + 覆盖"这一对（跨卷、部分网络盘），退成普通覆盖替换
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            throw IllegalStateException("原子替换 ${target.path} 失败：${e.message}", e)
        }
    }

    /** 数值读法共用的前置检查：非原生 JsonPrimitive（字符串、对象、数组、JsonNull）一律拒。 */
    private fun numberContent(k: String, el: JsonElement): String {
        val p = el as? JsonPrimitive
        if (p == null || p is JsonNull || p.isString) throw mismatch(k, el, "数值")
        return p.content
    }

    private fun mismatch(k: String, el: JsonElement, want: String): IllegalStateException =
        IllegalStateException("键 $k 在 ${file.path} 里不是${want}（实际存的是 ${el.toString().take(60)}）")

    private fun toJsonElement(k: String, v: Any): JsonElement = when (v) {
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Int -> JsonPrimitive(v)
        is Long -> JsonPrimitive(v)
        is Float -> JsonPrimitive(v)
        is Set<*> -> JsonArray(v.map { item ->
            if (item !is String) {
                throw IllegalArgumentException("键 $k 的集合元素只支持字符串，实际是 ${item?.javaClass?.name}")
            }
            JsonPrimitive(item)
        })
        else -> throw IllegalArgumentException("键 $k 的值类型不支持落盘：${v.javaClass.name}")
    }

    private companion object {
        /** 进程内的写锁：一个名字一个文件，写与写不许交错。 */
        val writeLock = Any()
    }
}
