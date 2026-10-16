package com.venera.desktop.gallery.ui

import com.venera.engineprobe.desktopImageRequestOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第一百零九批 · 源**没声明** `onImageLoad` 时，桌面取图那一路要照对岸兜。
 *
 * 题面来自批 108 包内那一趟：goda 真号、泵确实接走了（`跑=1`），但 30 页全落在
 * `取页失败 … onImageLoad is not a function`。现量随包 34 份脚本里 **16 份没声明这一枚**，
 * 而对岸把它读成**能力位**（`JsComicSource.kt:628` 取真值、`DownloadManager.kt:417/433` 与
 * `VeneraReaderScreen.kt:388/404` 缺声明那支把 item 原样当地址），协议里**没有**装载期补默认实现那一步。
 * 桌面今天是裸调，所以那 16 族在桌面上整话读不了 —— 下载、阅读、预览大图三条都走同一颗 `pageImage`。
 *
 * 判式分两层：前四枚打**抽出来的那一颗纯件**（它能脱离 JS 真跑，所以是唯一写得 behavioral 的那一面），
 * 后三枚钉 **JS 那一段的守卫形状与顺序**（纯件写得再对，调用点不加守卫就永远喂不进 `__none`）。
 */
class DesktopImageLoadCapabilityTest {

    @Test
    fun `声明缺失那一档把图片键原样当地址且不带请求头也不去混淆`() {
        // 与对岸 `else { item }` 逐字同语义。切块数 0 是**结论**而不是兜底：
        // `const num` 只可能来自 onImageLoad 回的 modifyImage，没过那一枚就没有它。
        for (imageKey in listOf("/images/1234/001.jpg", "/images/1234/002.jpg#crop.0.0.100.200")) {
            val req = desktopImageRequestOf(imageKey, mapOf("__none" to true))
            assertEquals("原样那一档地址就是图片键本身", imageKey, req.url)
            assertEquals("原样那一档没有请求头可带", emptyMap<String, String>(), req.headers)
            assertEquals("原样那一档不去混淆", 0, req.scrambleBlocks)
        }
    }

    @Test
    fun `声明了且回 data 时地址请求头切块数三样都从答复里取`() {
        // 这一档防的是"把守卫写成恒 __none" —— 那种写法下面那一条也会过，只有这条能分清。
        val req = desktopImageRequestOf(
            "p1",
            mapOf(
                "success" to true,
                "data" to mapOf(
                    "url" to "https://cdn.example/full.jpg",
                    "headers" to mapOf("Referer" to "https://example.com", "Accept-Encoding" to "gzip"),
                    "modifyImage" to "function modifyImage(img){ const num = 8; ... }",
                ),
            ),
        )
        assertEquals("https://cdn.example/full.jpg", req.url)
        assertEquals(
            mapOf("Referer" to "https://example.com", "Accept-Encoding" to "gzip"),
            req.headers,
        )
        assertEquals("切块数只认 `const num = N` 那一形", 8, req.scrambleBlocks)
    }

    @Test
    fun `源声明了却调用失败时抛出去而不静默退回原地址`() {
        // 那一条红线（降级宁可错慢不可静默交错）在这一格的具体形状：
        // 缺声明 ⇒ 原样走；有声明而站方回 success=false ⇒ **必须响**。
        // 两支混成一支，凭证过期这类故障就会被读成"这本图就这么糙"。
        val failure = runCatching {
            desktopImageRequestOf(
                "p1",
                mapOf("success" to false, "error" to "Login required"),
            )
        }.exceptionOrNull()
        assertTrue("success=false 必须抛，不许退回 item", failure != null)
        assertTrue(
            "抛出去的那句要带上站方的原话，否则排障时只剩一个类型名：${failure?.message}",
            failure?.message?.contains("Login required") == true,
        )
    }

    @Test
    fun `答复里既没有 __none 也没有 data 时那句点名是信封缺货`() {
        val failure = runCatching { desktopImageRequestOf("p1", mapOf("success" to true)) }.exceptionOrNull()
        assertTrue("没有 data 必须抛", failure != null)
        assertTrue(
            "那一句要念得出缺的是哪一层：${failure?.message}",
            failure?.message?.contains("data") == true,
        )
    }

    @Test
    fun `JS 那一段的守卫串在取图这一路上恰一处`() {
        val engine = DesktopSourceTree.engineProbeSource("EngineSession.kt")
        // 与同文件 coverBytes 那枚缩略图守卫**同一颗守卫**，不加第三种说法。
        // 只有活的那一处（pageImage）：`imageBytes` 全仓零调用点，本批整颗撤掉了（见判式 ⑦）。
        assertEquals(
            "取图那一路的缺声明守卫恰一处（多一处就是有人在补第二套说法，少一处就是有人在裸调）",
            1,
            DesktopSourceTree.codeOccurrences(
                engine,
                "if (!s.comic || !s.comic.onImageLoad) return { __none: true };",
            ),
        )
    }

    @Test
    fun `裸调那一形只许出现在守卫之后而缩略图那枚守卫不许被顺手改掉`() {
        val engine = DesktopSourceTree.engineProbeSource("EngineSession.kt")
        val code = DesktopSourceTree.codeText(engine)
        val call = "comic.onImageLoad("
        assertEquals("真调裸调都算在这一形上：加了守卫后只许剩下调用点那一处", 1, code.split(call).size - 1)
        val guardIdx = code.indexOf("if (!s.comic || !s.comic.onImageLoad) return { __none: true };")
        val callIdx = code.indexOf(call)
        assertTrue("守卫必须存在", guardIdx >= 0)
        assertTrue("守卫必须排在裸调之前 —— 顺序反了等于没有守卫", guardIdx < callIdx)
        // 邻座那一枚：缩略图的守卫本批不动，也不许被顺手改宽或摘掉。
        assertEquals(
            "coverBytes 那枚缩略图守卫仍恰一处",
            1,
            DesktopSourceTree.codeOccurrences(
                engine,
                "if (!s.comic || !s.comic.onThumbnailLoad) return { __none: true };",
            ),
        )
    }

    @Test
    fun `取页那一跳吃纯件而那颗死件不许回来`() {
        val engine = DesktopSourceTree.engineProbeSource("EngineSession.kt")
        assertEquals(
            "pageImage 里对纯件的那一跳恰一处（把它换回就地解析，判式 ①~④ 就全在测一颗没人叫的东西）",
            1,
            DesktopSourceTree.codeOccurrences(engine, "desktopImageRequestOf("),
        )
        // 反向钉那颗死件：它「实现了而零调用点」，本批撤掉；写回来说明有人又把它挂上了。
        val stray = DesktopSourceTree.engineProbeSourceDir()
            .sumOf { DesktopSourceTree.codeOccurrences(it, "fun imageBytes(") }
        assertEquals("imageBytes 全仓零调用点，撤掉后不许回来", 0, stray)
    }
}
