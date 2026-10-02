package com.venera.engineprobe

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * S0-3 两级探针（判据来自方案文档第七节）：
 * 第一级 `nhentai` 出 ≥10 条结果；第二级 `jm` 出结果 + 详情。
 *
 * 装载序列照抄 `source/js/ComicSourceParser.kt`（`temp_source` 那套），
 * 这样两侧的差异只可能来自 JS 引擎本身，而不是探针自己发明的装载方式。
 *
 * 代理：`-Pproxy=127.0.0.1:7890`（本机直连这些域名全不通）。
 */
fun main(args: Array<String>) {
    val wantKey = args.firstOrNull { !it.startsWith("--") } ?: "nhentai"
    val root = File(System.getProperty("user.dir"))
    val assetDir = File(root, "app/src/main/assets")
    val dataDir = File(System.getProperty("venera.probeDataDir") ?: System.getProperty("java.io.tmpdir"), "venera-engine-probe")
    val proxy = args.firstOrNull { it.startsWith("--proxy=") }?.removePrefix("--proxy=")
    val gson = Gson()
    val mapType = object : TypeToken<Map<String, Any?>>() {}.type
    val defsType = object : TypeToken<Map<String, Any?>>() {}.type

    println("[probe] want=$wantKey assets=${assetDir.path} data=$dataDir proxy=${proxy ?: "直连"}")

    DesktopJsHost(assetDir, dataDir, proxy).use { host ->
        val sourceFile = File(assetDir, "sources/$wantKey.js")
        if (!sourceFile.exists()) {
            println("[probe] FAIL 源脚本不存在: $sourceFile")
            return
        }
        val js = sourceFile.readText().replace("\r\n", "\n")
        val className = Regex("""class\s+(\w+)\s+extends\s+ComicSource""").find(js)?.groupValues?.get(1)
        if (className == null) {
            println("[probe] FAIL 未找到 extends ComicSource 的类声明")
            return
        }
        host.evaluate("(function() {\n$js\nwindow['temp_source'] = new $className();\n})();")

        val key = host.evaluate("window['temp_source'] ? window['temp_source'].key : null")
            ?.trim('"', '\'')
        if (key.isNullOrBlank() || key == "null") {
            println("[probe] FAIL 装载后取不到 source key")
            return
        }
        println("[probe] 已装载 class=$className key=$key")

        // 默认设置项必须先注册，否则源里的 loadSetting 拿不到 default（与 Android 侧同一步）
        val defsJson = host.evaluate(
            """
            (function() {
                var s = window['temp_source'];
                var defs = {};
                if (s.settings) {
                    for (var k in s.settings) {
                        if (s.settings[k] && s.settings[k].default !== undefined) defs[k] = s.settings[k].default;
                    }
                }
                return JSON.stringify(defs);
            })()
            """.trimIndent()
        )
        if (!defsJson.isNullOrBlank() && defsJson != "null") {
            runCatching { gson.fromJson<Map<String, Any?>>(defsJson, defsType) }
                .getOrNull()?.let { host.registerDefaultSettings(key, it) }
        }

        host.evaluate("ComicSource.sources['$key'] = window['temp_source']; delete window['temp_source'];")
        host.evaluate("if (ComicSource.sources['$key'].init) { ComicSource.sources['$key'].init(); }")
        println("[probe] ComicSource.sources 现有 ${host.evaluate("Object.keys(ComicSource.sources).length")} 个源")

        // explore 是另一条入口。search 空返回时用它区分"引擎不通"与"该源的搜索有前置条件"
        val exploreEnv = gson.fromJson<Map<String, Any?>>(
            host.evaluateAsync(
                """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s.explore) return { __noexplore: true };
                    var r = Array.isArray(s.explore)
                        ? await s.explore[0].load(1)
                        : await s.explore.load();
                    return { data: (r && r.data) ? r.data : r };
                })()
                """.trimIndent()
            ), mapType
        )
        run {
            val shape = when (val ed = exploreEnv["data"]) {
                is List<*> -> "${ed.size} 项"
                is Map<*, *> -> "map(${ed.keys.take(4).joinToString(",")})"
                else -> ed?.javaClass?.simpleName ?: "null"
            }
            println("[probe] explore success=${exploreEnv["success"]} error=${exploreEnv["error"]} 形状=$shape")
        }

        val keyword = args.firstOrNull { it.startsWith("--kw=") }?.removePrefix("--kw=")
            ?: if (wantKey == "jm") "one" else "a"
        val searchScript = """
            return (async function() {
                var s = ComicSource.sources['$key'];
                var opts = s.search && s.search.optionList ? _veneraOptionValues(s.search.optionList) : [];
                var res = null;
                if (s.search && s.search.load) {
                    res = await s.search.load(${gson.toJson(keyword)}, opts, 1);
                } else if (s.search && s.search.loadNext) {
                    res = await s.search.loadNext(${gson.toJson(keyword)}, opts, null);
                } else {
                    return { __nosearch: true, comics: [] };
                }
                return res || { comics: [] };
            })()
        """.trimIndent()

        val envelope = gson.fromJson<Map<String, Any?>>(host.evaluateAsync(searchScript), mapType)
        if (envelope["success"] != true) {
            println("[probe] FAIL search 信封失败: ${envelope["error"]}")
            return
        }
        @Suppress("UNCHECKED_CAST")
        val data = envelope["data"] as? Map<String, Any?>
        val comics = data?.get("comics") as? List<*> ?: emptyList<Any?>()
        println("[probe] search(${comics.size} 条) maxPage=${data?.get("maxPage")} next=${data?.get("next")}")
        comics.take(3).forEachIndexed { i, c ->
            println("[probe]   #$i ${(c as? Map<*, *>)?.get("title")} id=${(c as? Map<*, *>)?.get("id")}")
        }

        if (wantKey == "jm" && comics.isNotEmpty()) {
            verifyComicChain(host, gson, mapType, key, (comics[0] as Map<*, *>)["id"]?.toString().orEmpty(), root)
        }

        val threshold = if (wantKey == "nhentai") 10 else 1
        println(
            if (comics.size >= threshold) "PROBE_OK $wantKey ${comics.size}"
            else "PROBE_FAIL $wantKey ${comics.size}<$threshold"
        )
    }
}

/** S0-3 第二级：详情 → 章节页 → onImageLoad → 取图 → 去混淆 → 落盘 */
private fun verifyComicChain(
    host: DesktopJsHost,
    gson: Gson,
    mapType: java.lang.reflect.Type,
    key: String,
    cid: String,
    root: File,
) {
    fun call(script: String): Map<String, Any?> = gson.fromJson(host.evaluateAsync(script), mapType)

    val info = call("return (async function(){ return await ComicSource.sources['$key'].comic.loadInfo(${gson.toJson(cid)}); })()")
    val d = info["data"] as? Map<*, *>
    @Suppress("UNCHECKED_CAST")
    val episodes = d?.get("episodes") as? Map<*, *>
    println("[probe] loadInfo success=${info["success"]} error=${info["error"]} title=${d?.get("title")} episodes=${episodes?.size}")
    println("[probe]   loadInfo 字段表=${d?.keys?.map { it.toString() }?.sorted()}")

    var eid = episodes?.keys?.firstOrNull()?.toString()
    var images: List<*>? = null
    if (eid == null) {
        // 无章节的源：官方占位章传 null，但 jm 对 null 报 Invalid Data，逐个候选试出它要的那个
        for (candidate in listOf(null, "0", cid)) {
            val tryEp = call(
                "return (async function(){ return await ComicSource.sources['$key'].comic.loadEp(" +
                    "${gson.toJson(cid)}, ${candidate?.let { gson.toJson(it) } ?: "null"}); })()"
            )
            images = (tryEp["data"] as? Map<*, *>)?.get("images") as? List<*>
            println("[probe] loadEp eid=${candidate ?: "null"} success=${tryEp["success"]} error=${tryEp["error"]} images=${images?.size}")
            if (!images.isNullOrEmpty()) {
                eid = candidate
                break
            }
        }
    } else {
        val ep = call(
            "return (async function(){ return await ComicSource.sources['$key'].comic.loadEp(" +
                "${gson.toJson(cid)}, ${gson.toJson(eid)}); })()"
        )
        images = (ep["data"] as? Map<*, *>)?.get("images") as? List<*>
        println("[probe] loadEp success=${ep["success"]} error=${ep["error"]} eid=$eid images=${images?.size}")
    }
    val imageKey = images?.firstOrNull()?.toString()
    if (imageKey == null) {
        println("PROBE_IMAGE_FAIL 拿不到图片键")
        return
    }

    val cfg = call(
        "return (async function(){ return await ComicSource.sources['$key'].comic.onImageLoad(" +
            "${gson.toJson(imageKey)}, ${gson.toJson(cid)}, ${gson.toJson(eid)}, undefined); })()"
    )
    val c = cfg["data"] as? Map<*, *>
    val url = c?.get("url")?.toString() ?: imageKey
    val headers = (c?.get("headers") as? Map<*, *>)
        ?.entries?.associate { it.key.toString() to it.value.toString() }
        ?: emptyMap()
    val num = Regex("""const\s+num\s*=\s*(\d+)""")
        .find(c?.get("modifyImage")?.toString().orEmpty())
        ?.groupValues?.get(1)?.toIntOrNull() ?: 0
    println("[probe] onImageLoad success=${cfg["success"]} error=${cfg["error"]} num=$num")

    val (status, bytes) = host.fetchBytes(url, headers)
    println("[probe] 取图 HTTP=$status bytes=${bytes.size}")
    if (status != 200 || bytes.isEmpty()) {
        println("PROBE_IMAGE_FAIL http=$status")
        return
    }
    val outDir = File(root, "_qa").apply { mkdirs() }
    File(outDir, "s03-jm-scrambled.bin").writeBytes(bytes)
    // 两种"还原不了"要分开说：解不了码是格式问题，高度不够是切块问题
    val decoded = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
    println(
        "[probe] 图头=" + bytes.take(12).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) } +
            " ImageIO可解=${decoded != null} dims=${decoded?.width}x${decoded?.height}"
    )
    if (decoded == null) {
        println("PROBE_IMAGE_FAIL 取到的字节解不了码（格式/内容问题，不是切块问题）")
        return
    }
    if (num <= 1) {
        println("PROBE_IMAGE_SKIP num=$num 该页无需去混淆")
        return
    }
    val fixed = JvmImageOps.descrambleBlocks(decoded, num)
    if (fixed == null) {
        println("PROBE_IMAGE_FAIL ${decoded.height}px 不够切 $num 块，去混淆无从谈起")
        return
    }
    // "已还原"要有可量化的判据，不能靠肉眼：自然图的相邻行差异远小于被切块打乱的图，
    // 所以还原正确 ⇒ 纵向梯度显著下降。
    val gAsIs = JvmImageOps.verticalGradient(decoded)
    val gFixed = JvmImageOps.verticalGradient(
        javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(fixed)) ?: decoded
    )
    println("[probe] 纵向梯度 混淆态=%.3f 还原态=%.3f 降幅=%.1f%%".format(gAsIs, gFixed, (1 - gFixed / gAsIs) * 100))
    val png = File(outDir, "s03-jm-descrambled.png")
    png.writeBytes(fixed)
    // 同时留一份"未还原"的同尺寸 PNG，便于肉眼比对块序是否真的倒回来了
    val asIs = java.io.ByteArrayOutputStream()
    javax.imageio.ImageIO.write(decoded, "png", asIs)
    File(outDir, "s03-jm-as-is.png").writeBytes(asIs.toByteArray())
    println("PROBE_IMAGE_OK 已落盘 ${png.name} bytes=${fixed.size} num=$num dims=${decoded.width}x${decoded.height}")
}
