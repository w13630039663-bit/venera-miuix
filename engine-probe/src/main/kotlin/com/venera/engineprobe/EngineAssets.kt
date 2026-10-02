package com.venera.engineprobe

import java.io.File
import java.net.JarURLConnection

/**
 * 桌面链路的**源脚本取用口**（Task 6）：同一份内容、同一个取用次序，只把"从哪读"改成
 * **包内优先、仓库目录兜底**，两处都取不到就抛。
 *
 * 为什么要这一层：正式产物（jpackage 出来的 app）不该依赖仓库目录还在不在。装载侧
 * （[DesktopJsHost] 的 shim/init、[EngineSession] 的源脚本）原来一律 `File(assetDir, …).readText()`，
 * 仓库不在就是启动即崩，而且崩相和"脚本本身有语法错"分不开。
 *
 * 三条口径写死在这里（降级路径一律不许静默）：
 * - **包内命中**打 `D_资源 来源=包内 /sources/jm.js`；
 * - **仓库兜底**打 `D_资源 来源=仓库 <绝对路径>`；
 * - 包内条目存在但是**空内容**时不当成"读到了空脚本"继续跑 —— 那正是"包内没有却被当成空字符串"
 *   的假绿形状，如实打一条 `包内条目为空` 的读数后走仓库兜底，兜不到就抛。
 *
 * 装载顺序与脚本内容一字未动：这里只决定**文本从哪来**。
 */
object EngineAssets {

    /** 源脚本目录在 classpath 根下的位置（打进 jar 后就是 `/sources/…`） */
    const val SOURCES_CLASSPATH_DIR = "/sources"

    /**
     * 读一份随包资源。
     *
     * @param assetDir 仓库里的 assets 目录（**兜底**用；不存在时也必须有值，取不到就抛）
     * @param relative 相对路径，`sources/jm.js` 或 `venera-init.js`
     * @throws IllegalStateException 包内与仓库都没有（或包内空且仓库没有）时抛，绝不返回空内容
     */
    fun readText(assetDir: File, relative: String): String {
        val path = classpathPath(relative)
        val stream = javaClass.getResourceAsStream(path)
        if (stream != null) {
            val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
            if (text.isNotBlank()) {
                println("D_资源 来源=包内 $path 字节=${text.toByteArray(Charsets.UTF_8).size}")
                return text
            }
            println("D_资源 包内条目为空 $path 改用仓库兜底 ${File(assetDir, relative).path}")
        }
        val file = File(assetDir, relative)
        if (file.isFile) {
            println("D_资源 来源=仓库 ${file.absolutePath}")
            return file.readText()
        }
        throw IllegalStateException(
            "资源两处都取不到：classpath $path 与仓库 ${file.absolutePath}（包内${if (stream != null) "有空条目" else "无条目"}）",
        )
    }

    /**
     * 枚举随包的源脚本键名（`sources` 目录下的 js 脚本，去掉扩展名，排序）。
     *
     * 不能只用 `getResourceAsStream` —— 目录列表在 jar 里不是"一个资源"，得走 `JarURLConnection`
     * 拿条目清单；exploded 资源目录（gradle 跑 :desktop:test / :desktop:app 时）就是普通目录。
     * 两处都枚举不出来时返回空表并如实打一条读数（不静默当成"这个包没有源"）。
     */
    fun listSourceKeys(assetDir: File): List<String> {
        classpathSourceKeys()?.let { keys ->
            println("D_资源 来源=包内 $SOURCES_CLASSPATH_DIR/ 清单=${keys.size} 颗")
            return keys
        }
        val dir = File(assetDir, "sources")
        val keys = dir.listFiles { f -> f.isFile && f.extension == "js" }
            ?.map { it.nameWithoutExtension }?.sorted()
        if (keys != null) {
            println("D_资源 来源=仓库 ${dir.absolutePath} 清单=${keys.size} 颗")
            return keys
        }
        println(
            "D_资源 源清单两处都取不到：classpath $SOURCES_CLASSPATH_DIR/ 与仓库 ${dir.absolutePath}" +
                "（按 0 颗继续，这不是「包里没有源」的意思，是真的一处都枚举不出来）",
        )
        return emptyList()
    }

    /** classpath 上有没有这份资源（**不读内容**，只判在不在；空条目也算在） */
    fun hasClasspathResource(relative: String): Boolean =
        javaClass.getResource(classpathPath(relative)) != null

    private fun classpathPath(relative: String): String =
        "/" + relative.replace('\\', '/').removePrefix("/")

    private fun classpathSourceKeys(): List<String>? {
        val url = javaClass.getResource(SOURCES_CLASSPATH_DIR) ?: return null
        val keys = when (url.protocol) {
            // jar: 条目清单在 JarFile 里；**不关它** —— 这条连接是 JVM 缓存的，关了之后
            // getResourceAsStream 再取同包内资源就取不到了（一次装载完再装第二颗就崩）。
            "jar" -> runCatching {
                val conn = url.openConnection() as JarURLConnection
                val prefix = conn.entryName.trimEnd('/') + "/"
                conn.jarFile.entries().toList()
                    .filter { !it.isDirectory && it.name.startsWith(prefix) && it.name.endsWith(".js") }
                    .map { it.name.removePrefix(prefix).removeSuffix(".js") }
                    .sorted()
            }.getOrNull()

            "file" -> runCatching {
                File(url.toURI()).listFiles { f -> f.isFile && f.extension == "js" }
                    ?.map { it.nameWithoutExtension }?.sorted()
            }.getOrNull()

            else -> null
        }
        // 枚举得出但 0 颗（jar 里有 /sources 目录条目却没 .js 条目）不算命中，交给仓库兜底
        return keys?.takeIf { it.isNotEmpty() }
    }
}
