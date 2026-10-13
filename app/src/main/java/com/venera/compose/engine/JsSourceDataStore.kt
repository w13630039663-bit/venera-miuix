package com.venera.compose.engine

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 源数据档的文件名规则：**`<源 key>.data`**，只有这一条。
 *
 * 抽成顶层函数是因为这条规则被**三个**地方需要，而它已经漂移过一次：
 * 备份那一栏原本按「脚本文件名去扩展名」猜档名（`baozi.js` → `baozi.data`），
 * 于是 `komiic.js`（key 是 `Komiic`）和 `copy_manga_multi_accounts.js`
 * （key 是 `copy_manga`）这两个源全部读不到自己的档；更糟的是那条路径在档不存在时
 * 会回落成一个**同样不存在的** `File`，调用方一 `readText()` 就抛 ENOENT，
 * 把整个导出一起带崩（真机实测：`baozi.data: open failed: ENOENT`）。
 *
 * key 与脚本文件名**经常不同**，这是源协议允许的（`ComicSourceParser` 从脚本里读
 * `key = "..."`），所以档名只能由 key 决定。
 */
fun sourceDataFileName(sourceKey: String): String =
    "${sourceKey.substringAfterLast('/').substringAfterLast('\\')}.data"

class JsSourceDataStore(private val baseDir: File) {
    init {
        baseDir.mkdirs()
    }
    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, MutableMap<String, Any?>>()

    private fun getSourceMap(sourceKey: String): MutableMap<String, Any?> {
        return cache.getOrPut(sourceKey) {
            val file = File(baseDir, sourceDataFileName(sourceKey))
            if (file.exists()) {
                try {
                    val text = file.readText()
                    val type = object : TypeToken<MutableMap<String, Any?>>() {}.type
                    gson.fromJson<MutableMap<String, Any?>>(text, type) ?: ConcurrentHashMap()
                } catch (e: Exception) {
                    ConcurrentHashMap()
                }
            } else {
                ConcurrentHashMap()
            }
        }
    }

    private fun persist(sourceKey: String) {
        val map = cache[sourceKey] ?: return
        try {
            val file = File(baseDir, sourceDataFileName(sourceKey))
            file.writeText(gson.toJson(map))
        } catch (e: Exception) {
            EngineLog.e("VeneraJS", "Failed to persist data for source $sourceKey :: ${e.message}")
        }
    }

    fun loadData(sourceKey: String, dataKey: String): Any? {
        return getSourceMap(sourceKey)[dataKey]
    }

    fun saveData(sourceKey: String, dataKey: String, data: Any?): String? {
        if (dataKey == "setting") {
            return "setting is not allowed to be saved"
        }
        val map = getSourceMap(sourceKey)
        map[dataKey] = data
        persist(sourceKey)
        return null
    }

    fun deleteData(sourceKey: String, dataKey: String) {
        val map = getSourceMap(sourceKey)
        map.remove(dataKey)
        persist(sourceKey)
    }

    private val defaultSettings = ConcurrentHashMap<String, MutableMap<String, Any?>>()

    fun registerDefaultSettings(sourceKey: String, defaults: Map<String, Any?>) {
        defaultSettings.getOrPut(sourceKey) { ConcurrentHashMap() }.putAll(defaults)
    }

    fun loadSetting(sourceKey: String, settingKey: String): Any? {
        val map = getSourceMap(sourceKey)
        val settings = map["settings"] as? Map<*, *>
        val userVal = settings?.get(settingKey)
        if (userVal != null) return userVal
        return defaultSettings[sourceKey]?.get(settingKey)
    }

    fun saveSetting(sourceKey: String, settingKey: String, value: Any?) {
        val map = getSourceMap(sourceKey)
        val settings = (map["settings"] as? Map<*, *>)?.let { HashMap(it) } ?: HashMap()
        if (value != null) {
            settings[settingKey] = value
        } else {
            settings.remove(settingKey)
        }
        map["settings"] = settings
        persist(sourceKey)
    }

    fun getAllSettings(sourceKey: String): Map<String, Any?> {
        val map = getSourceMap(sourceKey)
        val userSettings = (map["settings"] as? Map<*, *>)?.mapNotNull { (k, v) ->
            if (k is String) k to v else null
        }?.toMap() ?: emptyMap()
        val defaults = defaultSettings[sourceKey] ?: emptyMap()
        return defaults + userSettings
    }

    fun isLogged(sourceKey: String): Boolean {
        val map = getSourceMap(sourceKey)
        val account = map["account"]
        return account != null && (account is List<*> || account is Map<*, *> || account == "ok" || account == true)
    }

    fun getAccount(sourceKey: String): Any? {
        val map = getSourceMap(sourceKey)
        return map["account"]
    }

    fun saveAccount(sourceKey: String, accountData: Any?) {
        val map = getSourceMap(sourceKey)
        map["account"] = accountData
        persist(sourceKey)
    }

    fun logout(sourceKey: String) {
        val map = getSourceMap(sourceKey)
        map.remove("account")
        map.remove("token")
        map.remove("_cookies")
        persist(sourceKey)
    }

    /**
     * 丢弃某个源的全部内存态（**并删除其磁盘数据文件**）。
     *
     * 必须存在的理由：本类的 [cache] / [defaultSettings] 是纯内存 Map，
     * 源被卸载时若只删磁盘文件、不清内存，则重新安装同一个源会读到
     * 上一份残留的 settings / account（幽灵登录态）。
     */
    fun evict(sourceKey: String) {
        cache.remove(sourceKey)
        defaultSettings.remove(sourceKey)
        try {
            File(baseDir, sourceDataFileName(sourceKey)).delete()
        } catch (e: Exception) {
            EngineLog.w("VeneraJS", "Failed to delete data file for source $sourceKey :: ${e.message}")
        }
    }
}
