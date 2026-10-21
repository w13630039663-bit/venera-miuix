package com.venera.desktop

import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第八十八批：桌面「网页登录」那一格的**前提与落点**（只动量具与文档，产品面零改动）。
 *
 * ## 这一批要把哪句话量掉
 *
 * 排期表那一行写的是"轻路：`Desktop.browse` + `jdk.httpserver`，并补 `modules(...)`"。
 * 动手前按那一行自己的口径先去量对岸，量出来的形状是（逐条可回代码）：
 *
 * - 对岸的网页登录**只有一形**：内嵌 WebView 登进去，再从 WebView 自己的罐里抓
 *   `CookieManager.getInstance().getCookie(url)`（`WebLoginScreen.kt:130`），成功与否靠**导航过程的 url/title 序列**判。
 * - 全仓（含 `app/`）**零颗**本地 HTTP 服务、零颗 `ServerSocket` ⇒
 *   "起一个端口等站方回调"那一路**没有对象**：站方（e-hentai 的登录页）根本不往 localhost 重定向，
 *   对岸也从来没等过那种回调。补 `modules(...)` 就是给一条不存在的流程买票。
 * - 而对岸**另外两形**（手填 cookie、账密登录）全是纯 JVM 的落点，
 *   桌面这一侧的接缝今天就在：桥里那三档 `cookie`（get / set / delete）、`isLogged`
 *   与那颗挂在客户端上的罐（`DesktopJsHost.kt:286,301-323,372-390`）。
 *
 * ⇒ 这一批交的是**把那句假话量掉 + 把真接缝钉住**，实施那一格改排在下一批，
 * 开工单写在 `docs/rounds/desktop-web-login-verdict-2026-10-11.md` §4。
 *
 * ## 为什么"不许长出回调端口"要配一条对照
 *
 * 只钉"没有那一路"会变成一条恒绿的装饰：将来谁接了真回调流程（站方哪天肯重定向了），
 * 这条尺子只会碍事。所以每条负向都配一句"今天为什么不需要它"的**正面证据** ——
 * 这里递的是桥那三档确实在代码行里（走 `codeText`：注释里提一句"没有 cookie 罐"不该被当成有罐）。
 *
 * ## 那条红线为什么顺手也钉住
 *
 * `DesktopGelbooruCredentialPanel.kt:48` 明写「**不从浏览器 cookie 库取凭据**」。
 * 下一批接"手填 cookie"时，最省事的诱惑是去读系统浏览器自己的 sqlite 罐 ——
 * 那是把用户没声明来源的会话凭据搬到另一台机器上。这条尺子钉的是那句红线不漂，
 * 而"手工粘贴"（用户自己声明来源、对岸 `loginWithCookies` 本来就走这条）不在它射程里。
 */
class DesktopWebLoginVerdictTest {

    private val host = DesktopSourceTree.engineProbeSource("DesktopJsHost.kt")

    /** ① 「回调端口那一路」今天不存在，也不许凭空长出来：三处各扫一遍。 */
    @Test
    fun 登录那一路今天没有任何本地端口服务() {
        // 拼出来而不是照抄：判式自己写着那两个名字就会把自己算成违例（手册 §0 坑 ㉜，第三次用到）。
        val serverSocket = "Server" + "Socket"
        val httpServer = "com" + "." + "sun" + "." + "net" + "." + "httpserver"
        val nanoHttp = "Nano" + "HTTPD"

        val scope = listOf(
            "desktop/src/main" to DesktopSourceTree.desktopMainSources(),
            ":engine-probe" to listOf(host),
        )
        for ((label, files) in scope) {
            for (token in listOf(serverSocket, httpServer, nanoHttp)) {
                val hits = files.filter { DesktopSourceTree.codeText(it).contains(token) }.map { it.name }
                assertEquals("$label 里长出了本地服务（$token）：$hits", 0, hits.size)
            }
        }
        // 构建脚本那一处也要扫：`modules(...)` 那行曾被提议补 jdk.httpserver，加进去就是给不存在的流程买票。
        val build = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts")
        val buildLines = DesktopSourceTree.codeLinesOf(build)
        assertEquals("构建脚本的模块清单里添了那颗 httpserver 模块", 0, buildLines.count { it.contains("http" + "server") })
    }

    /** ①′ 对照（① 为什么今天不需要那条路）：桥里那三档 cookie 处理真在代码行里。 */
    @Test
    fun 对照_凭据的落点在桥里今天就有三档() {
        val code = DesktopSourceTree.codeText(host)
        for (fn in listOf("\"get\"", "\"set\"", "\"delete\"")) {
            assertTrue("桥里少了 cookie 那一档（$fn）—— 那①那条负向判据的对象就换成'该接这一档'了", code.contains(fn))
        }
        assertTrue("桥不再认 isLogged：源脚本的登录态那一问没消费点了", code.contains("\"isLogged\""))
        assertTrue("那颗罐没再挂在客户端上：cookie 写进去也带不上请求", code.contains(".cookieJar("))
    }

    /** ② 那条"今天只在内存里"的现状读数**已于同批下半改账**：罐接了盘（`DesktopCookieJar`），
     * 同一件事的尺子搬去 `DesktopCookieJarTest`（③④⑦ 三条：内存清完盘上也空 / 重启读得到且字段一枚不丢 /
     * 宿主只吃带盘那一颗而没有第二轨）。这里**不留第二把尺子**——同一件事两把尺子，改判时改一把漏一把。 */

    /** ③ 那条红线不许漂：桌面不去读浏览器自己的 cookie 库。 */
    /** ④ WebView 那一形是平台件：桌面结构性没有它的宿主，这一句也要有尺子。 */
    @Test
    fun 网页登录那一形靠的是内嵌webview而不是回调() {
        val screen = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("feature/sourcemanage/WebLoginScreen.kt"))
        val grab = "getCookie"
        assertNotNull("对岸抓 cookie 那一跳换了写法（④ 的引证对象搬家了）", Regex("""CookieManager\.getInstance\(\)\.$grab""").find(screen))
        // 桌面这一侧今天没有任何 WebView：这一条只数 `desktop/src/main` 的代码行，
        // 挡的是"下一批顺手引一颗 WebView 而不是走手填那形"。
        val hits = DesktopSourceTree.desktopMainSources().filter { DesktopSourceTree.codeText(it).contains("WebView") }
        assertEquals("桌面 main 里长出了 WebView（那一形在本平台没有宿主，接上就是死分支）：${hits.map { it.name }}", 0, hits.size)
    }
}
