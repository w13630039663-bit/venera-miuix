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
