package com.venera.engineprobe

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 桌面侧可复用的**一次引擎会话**：装载一个真源 + 出探索/搜索数据 + 取图。
 *
 * 装载序列与 `ProbeMain`（S0-3 探针）逐字同构，而 `ProbeMain` 又是照抄
 * `source/js/ComicSourceParser.kt` 的 —— 三者同源，桌面与 Android 的差异才只可能来自引擎本身。
 *
 * 刻意不依赖 Compose：返回的是**图片字节**，解码归 UI 侧。
 */
class EngineSession(
    val host: DesktopJsHost,
    private val assetDir: File,
) : AutoCloseable {

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type
    var key: String = ""
        private set
    var name: String = ""
        private set

    data class ComicCard(val title: String, val id: String, val cover: String)

    /** 探索页的一个分区（jm 等的 `explore[i].load()` 回的是**分区数组**，不是单个 comics 列表） */
    data class Section(val title: String, val comics: List<ComicCard>)

    data class Page(
        val comics: List<ComicCard>,
        val sections: List<Section> = emptyList(),
        val maxPage: Int? = null,
        val error: String? = null,
    )

    /** @return 源名；装载失败直接抛，不返回半套状态 */
    fun load(sourceKey: String): String {
        // Task 6：源脚本改走 EngineAssets —— 包内 `/sources/<key>.js` 优先，取不到才回仓库目录，
        // 两处都没有就抛（原来是 `error("源脚本不存在: …")`，形状不变）。CRLF 归一仍在这颗做，
        // 送进 eval 的**内容**与改动前逐字节相同。
        val sourcePath = "sources/$sourceKey.js"
        val js = EngineAssets.readText(assetDir, sourcePath).replace("\r\n", "\n")
        val className = Regex("""class\s+(\w+)\s+extends\s+ComicSource""").find(js)?.groupValues?.get(1)
            ?: error("未找到 extends ComicSource 的类声明: $sourcePath")
        host.evaluate("(function() {\n$js\nwindow['temp_source'] = new $className();\n})();")

        val loadedKey = host.evaluate("window['temp_source'] ? window['temp_source'].key : null")
            ?.trim('"', '\'')
        if (loadedKey.isNullOrBlank() || loadedKey == "null") error("装载后取不到 source key: $sourceKey")

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
            runCatching { gson.fromJson<Map<String, Any?>>(defsJson, mapType) }
                .getOrNull()?.let { host.registerDefaultSettings(loadedKey, it) }
        }

        host.evaluate("ComicSource.sources['$loadedKey'] = window['temp_source']; delete window['temp_source'];")
        host.evaluate("if (ComicSource.sources['$loadedKey'].init) { ComicSource.sources['$loadedKey'].init(); }")
        key = loadedKey
        name = host.evaluate("ComicSource.sources['$loadedKey'].name")?.trim('"', '\'') ?: loadedKey
        return name
    }

    /** 探索页：两种形态都兜（数组项各自带 load / 直接一个 load），与 Android 侧同一分支形状 */
    fun explore(page: Int = 1): Page = call(
        """
        return (async function() {
            var s = ComicSource.sources['$key'];
            if (!s.explore) return { comics: [], __err: "该源没有 explore" };
            var r = Array.isArray(s.explore)
                ? await s.explore[${page.coerceAtLeast(1) - 1}].load($page)
                : await s.explore.load();
            return r || { comics: [] };
        })()
        """
    )

    fun search(keyword: String, page: Int = 1): Page = call(
        """
        return (async function() {
            var s = ComicSource.sources['$key'];
            var opts = s.search && s.search.optionList ? _veneraOptionValues(s.search.optionList) : [];
            var res = null;
            if (s.search && s.search.load) {
                res = await s.search.load(${gson.toJson(keyword)}, opts, $page);
            } else if (s.search && s.search.loadNext) {
                res = await s.search.loadNext(${gson.toJson(keyword)}, opts, null);
            } else {
                return { comics: [], __err: "该源没有 search" };
            }
            return res || { comics: [] };
        })()
        """
    )

    private fun call(script: String): Page {
        val raw = host.evaluateAsync(script)
        val envelope = gson.fromJson<Map<String, Any?>>(raw, mapType)
        val err = envelope["error"]?.toString() ?: envelope["__err"]?.toString()
        if (envelope["success"] == false) {
            println("[session] 信封失败 error=$err raw=${raw.take(240)}")
            return Page(emptyList(), error = err)
        }
        val data0 = envelope["data"]
        // nhentai 的 explore 多套一层：`{data:{data:[{title, comics:[…]}, …]}}`
        val data = if (data0 is Map<*, *> && data0["data"] != null && data0["comics"] == null) {
            data0["data"]
        } else {
            data0
        }
        val maxPage = (data as? Map<*, *>)?.get("maxPage")?.let { (it as? Number)?.toInt() }
        // 三种形状都实测存在：search 回 `{comics:[…], maxPage}`；jm 的 explore 回**分区数组**
        // `[{title, comics:[…]}, …]`；nhentai 的 explore 回**漫画数组** `[{title, id, cover}, …]`
        val sections = when (data) {
            is List<*> -> {
                val rows = data.mapNotNull { it as? Map<*, *> }
                if (rows.any { it["comics"] != null }) {
                    rows.map { m -> Section(m["title"]?.toString() ?: "", cards(m["comics"])) }
                } else {
                    listOf(Section("", cards(rows)))
                }
            }

            is Map<*, *> -> listOf(Section("", cards(data["comics"])))
            else -> {
                println("[session] data 形状不认识=${data?.javaClass?.simpleName} raw=${raw.take(240)}")
                emptyList()
            }
        }
        val total = sections.sumOf { it.comics.size }
        if (total == 0) println("[session] 解析出 0 条 data形状=${data?.javaClass?.simpleName} raw=${raw.take(300)}")
        return Page(sections.flatMap { it.comics }, sections, maxPage, err)
    }

    private fun cards(raw: Any?): List<ComicCard> = (raw as? List<*> ?: emptyList<Any?>()).mapNotNull { c ->
        (c as? Map<*, *>)?.let {
            ComicCard(
                title = it["title"]?.toString() ?: "",
                id = it["id"]?.toString() ?: "",
                cover = it["cover"]?.toString() ?: "",
            )
        }
    }

    /**
     * 缩略图：源若有 `onThumbnailLoad` 必须先过它（它给的是 `{url, headers}`，
     * 直接拿 `cover` 原值去打常缺 Referer），没有才原地址取。与 Android 侧同一判据。
     */
    fun coverBytes(cover: String): ByteArray {
        var url = cover
        var headers = emptyMap<String, String>()
        val env = runCatching {
            gson.fromJson<Map<String, Any?>>(
                host.evaluateAsync(
                    """
                    return (async function(){
                        var s = ComicSource.sources['$key'];
                        if (!s.comic || !s.comic.onThumbnailLoad) return { __none: true };
                        return await s.comic.onThumbnailLoad(${gson.toJson(cover)}, "", "");
                    })()
                    """.trimIndent()
                ), mapType
            )
        }.getOrNull()
        if (env != null && env["__none"] != true && env["success"] != false) {
            val data = env["data"] as? Map<*, *>
            if (data != null) {
                url = data["url"]?.toString() ?: url
                headers = (data["headers"] as? Map<*, *>)
                    ?.entries?.associate { it.key.toString() to it.value.toString() } ?: headers
            }
        }
        val (status, bytes) = host.fetchBytes(url, headers)
        if (status != 200 || bytes.isEmpty()) error("取缩略图失败 http=$status bytes=${bytes.size} url=$url")
        return bytes
    }

    /** `placeholder=true` 表示这是"整部就一章"的占位章，取页时话号要还原成 `null` */
    data class Episode(val name: String, val id: String, val placeholder: Boolean = false)

    /**
     * 详情：标题 + 简介 + 章节表。**怎么读那份答复全在 [parseComicLoadInfo]**（那一颗是纯函数，
     * 判据直接喂假信封进它）；这里只负责发一次 JS。
     */
    fun details(comicId: String): ComicInfo {
        val env = gson.fromJson<Map<String, Any?>>(
            host.evaluateAsync(
                """
                return (async function(){
                    var s = ComicSource.sources['$key'];
                    return await s.comic.loadInfo(${gson.toJson(comicId)});
                })()
                """.trimIndent()
            ), mapType
        )
        return parseComicLoadInfo(comicId, env)
    }

    /** 页表（图片键，可能带 `#crop` 之类后缀，交给 onImageLoad 之前原样传） */
    fun pages(comicId: String, episode: Episode): List<String> {
        val epArg = if (episode.placeholder) "null" else gson.toJson(episode.id)
        val env = gson.fromJson<Map<String, Any?>>(
            host.evaluateAsync(
                """
                return (async function(){
                    var s = ComicSource.sources['$key'];
                    return await s.comic.loadEp(${gson.toJson(comicId)}, $epArg, "");
                })()
                """.trimIndent()
            ), mapType
        )
        if (env["success"] == false) error("loadEp 失败: ${env["error"]}")
        val data = env["data"] as? Map<*, *> ?: error("loadEp 没有 data")
        // 页表的键有两家：jm 系回 `images`，多数源回 `pages`。**顺序与 Android 侧一致**
        // （`JsComicSource:642-643` 是先 images 后 pages）
        val list = ((data["images"] ?: data["pages"]) as? List<*> ?: emptyList<Any?>()).mapNotNull { it?.toString() }
        if (list.isEmpty()) {
            println("[session] loadEp 回了 0 页 ep=${if (episode.placeholder) "null" else episode.id} data键=${data.keys}")
        }
        return list
    }

    /**
     * 取一页并**按需去混淆**：`modifyImage` 里的 `const num` 就是 jm 的切块数（权威口径见
     * `doc` 与 `ImagePipelinePolicy`），`num<=1` 原样返回。
     * 还原不了时抛错，不把混淆图当正常图交回去。
     *
     * 第一百零九批：`comic.onImageLoad` 是**能力位**而不是必需 —— 随包 34 份脚本里 16 份没声明它
     * （goda / gelbooru / zaimanhua / wnacg / kavita / happy…）。没声明的那些，`loadEp` 交回的
     * 每一项本身就是完整地址，所以守卫那一档直接回 `{ __none: true }`；怎么把信封读成
     * 「地址 + 请求头 + 切块数」全在 [desktopImageRequestOf]，判据是 `DesktopImageLoadCapabilityTest`。
     * 守卫那一形与同文件 [coverBytes] 的缩略图守卫**同一颗**，不加第三种说法。
     */
    fun pageImage(imageKey: String, comicId: String, episodeId: String): ByteArray {
        val env = gson.fromJson<Map<String, Any?>>(
            host.evaluateAsync(
                """
                return (async function(){
                    var s = ComicSource.sources['$key'];
                    if (!s.comic || !s.comic.onImageLoad) return { __none: true };
                    return await s.comic.onImageLoad(
                        ${gson.toJson(imageKey)}, ${gson.toJson(comicId)}, ${gson.toJson(episodeId)}, undefined);
                })()
                """.trimIndent()
            ), mapType
        )
        val request = desktopImageRequestOf(imageKey, env)
        val (status, bytes) = host.fetchBytes(request.url, request.headers)
        if (status != 200 || bytes.isEmpty()) error("取页失败 http=$status bytes=${bytes.size}")
        if (request.scrambleBlocks <= 1) return bytes
        val img = try {
            JvmImageOps.decode(bytes)
        } catch (t: Throwable) {
            throw IllegalStateException(
                "页图解不开 num=${request.scrambleBlocks} 头=${JvmImageOps.magic(bytes)} " +
                    "解码器=[${JvmImageOps.readerFormats()}] <- ${t.message}",
                t,
            )
        }
        return JvmImageOps.descrambleBlocks(img, request.scrambleBlocks)
            ?: error("${img.height}px 不够切 ${request.scrambleBlocks} 块，去混淆无从谈起")
    }

    /**
     * 手填 cookie 那一形（批 88·下半）：把用户粘进来的那几枚交给**源自己的**
     * `account.loginWithCookies.validate(values)`，过了才落「已登录」那一格。
     *
     * ## 口径为什么不在这里重述
     *
     * 要哪几枚字段、什么算成功，全在源脚本里（`fields: [...]` 与 `validate` 的返回值），
     * 桌面**一个字都不认**：脚本名单随这一趟的答复一起回来（[CookieLogin.fields]），
     * 与批 83 读 `name = "…"` 那一行同一族做法。桌面写死一份字段名单的话，
     * 站方改名单时屏上照旧摆老几格而答案永远过不了。
     *
     * ## 信封形状照 Android 侧
     *
     * 对岸那颗 `JsComicSource.validateCookies`（`JsComicSource.kt:1838`）认的是
     * `env["success"] == true` 且 `data === true`，桌面沿用同一判式，只把"为什么没过"多带一句回来 ——
     * 那一格在屏上必须说得出话，只回 `false` 用户读到的是"点了没反应"。
     *
     * ⚠️ 源在 `validate` 里自己会走 `Network.setCookies`（ehentai 甚至先 `deleteCookies` 再 set），
     * 所以"先种凭据再校验"这一步由脚本做，桌面不代做（代做就是第二条轨）。
     */
    fun loginWithCookies(values: List<String>): CookieLogin {
        val script = """
            return (async function() {
                var s = ComicSource.sources['$key'];
                if (!s || !s.account || !s.account.loginWithCookies) {
                    return { ok: false, why: "该源没有 loginWithCookies 那一形", fields: [] };
                }
                if (typeof s.account.loginWithCookies.validate !== 'function') {
                    return { ok: false, why: "该源的 validate 不是函数", fields: [] };
                }
                var fields = Array.isArray(s.account.loginWithCookies.fields)
                    ? s.account.loginWithCookies.fields : [];
                var r = await s.account.loginWithCookies.validate(${gson.toJson(values)});
                return { ok: r === true, fields: fields };
            })()
        """.trimIndent()
        val raw = host.evaluateAsync(script)
        val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
            ?: return CookieLogin(false, "源那句 validate 的答复解不开", emptyList())
        if (env["success"] == false) {
            return CookieLogin(false, env["error"]?.toString() ?: "validate 执行失败", emptyList())
        }
        val data = env["data"] as? Map<*, *>
            ?: return CookieLogin(false, "validate 回的形状不认识", emptyList())
        val fields = (data["fields"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        if (data["ok"] != true) {
            return CookieLogin(false, data["why"]?.toString() ?: "源说这批凭据不算通过", fields)
        }
        host.markLoggedIn(key)
        return CookieLogin(true, "源认了这批凭据", fields)
    }

    /** 手填 cookie 那一跑的三样结果。[fields] 是脚本自己那份名单（屏上回核与"摆哪几格"都读它）。 */
    data class CookieLogin(val ok: Boolean, val note: String, val fields: List<String>)

    override fun close() = host.close()
}
