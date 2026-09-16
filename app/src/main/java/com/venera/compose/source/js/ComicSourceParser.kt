package com.venera.compose.source.js

import com.venera.compose.engine.VeneraJsEngine
import java.io.File

class ComicSourceParser(private val engine: VeneraJsEngine) {

    fun parse(js: String): JsComicSource {
        val normalizedJs = js.replace("\r\n", "\n")
        val regex = Regex("""class\s+(\w+)\s+extends\s+ComicSource""")
        val match = regex.find(normalizedJs)
            ?: throw IllegalArgumentException("Invalid comic source: class extending ComicSource not found")
        val className = match.groupValues[1]

        val initScript = """
            (function() {
                $normalizedJs
                window['temp_source'] = new $className();
            })();
        """.trimIndent()

        engine.evaluate(initScript)

        val name = engine.evaluate("window['temp_source'] ? window['temp_source'].name : null")?.trim('"', '\'')
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: throw IllegalArgumentException("Source name is required (class $className)")
        val key = engine.evaluate("window['temp_source'] ? window['temp_source'].key : null")?.trim('"', '\'')
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: throw IllegalArgumentException("Source key is required (class $className)")
        val version = engine.evaluate("window['temp_source'] ? window['temp_source'].version : null")?.trim('"', '\'')
            ?.takeIf { it.isNotBlank() && it != "null" } ?: "1.0.0"

        // Register into global ComicSource.sources[key]
        engine.evaluate("ComicSource.sources['$key'] = window['temp_source']; delete window['temp_source'];")

        // 提取并注册该源定义的默认设置项，确保 init() 或网络请求中 loadSetting 绝不为 null
        try {
            val defsJson = engine.evaluate("""
                (function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.settings) return '{}';
                    var defs = {};
                    for (var k in s.settings) {
                        if (s.settings[k] && s.settings[k].default !== undefined) {
                            defs[k] = s.settings[k].default;
                        }
                    }
                    return JSON.stringify(defs);
                })()
            """.trimIndent())
            if (!defsJson.isNullOrBlank() && defsJson != "null") {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                val defsMap: Map<String, Any?> = engine.gson.fromJson(defsJson, type) ?: emptyMap()
                engine.dataStore.registerDefaultSettings(key, defsMap)
            }
        } catch (e: Exception) {
            android.util.Log.w("ComicSourceParser", "Failed to extract default settings for $key", e)
        }

        // Trigger init() if defined
        engine.evaluate("if (ComicSource.sources['$key'].init) { ComicSource.sources['$key'].init(); }")

        return JsComicSource(
            engine = engine,
            key = key,
            name = name,
            version = version,
            iconUrl = null
        )
    }

    fun parseFile(file: File): JsComicSource {
        val js = file.readText()
        return parse(js)
    }
}
