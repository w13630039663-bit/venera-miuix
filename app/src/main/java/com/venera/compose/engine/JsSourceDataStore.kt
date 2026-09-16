package com.venera.compose.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class JsSourceDataStore(context: Context) {
    private val baseDir = File(context.filesDir, "comic_source").apply { mkdirs() }
    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, MutableMap<String, Any?>>()

    private fun getSourceMap(sourceKey: String): MutableMap<String, Any?> {
        return cache.getOrPut(sourceKey) {
            val file = File(baseDir, "$sourceKey.data")
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
            val file = File(baseDir, "$sourceKey.data")
            file.writeText(gson.toJson(map))
        } catch (e: Exception) {
            android.util.Log.e("VeneraJS", "Failed to persist data for source $sourceKey", e)
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
}
