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

    fun loadSetting(sourceKey: String, settingKey: String): Any? {
        val map = getSourceMap(sourceKey)
        val settings = map["settings"] as? Map<*, *>
        return settings?.get(settingKey)
    }

    fun isLogged(sourceKey: String): Boolean {
        val map = getSourceMap(sourceKey)
        return map["account"] != null
    }
}
