package com.venera.desktop

import com.venera.desktop.gallery.ui.DesktopSourceTree
import com.venera.engineprobe.DesktopCookieJar
import java.io.File
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第八十八批·下半 第一格：把那罐凭据**落到盘上**（`DesktopCookieJar`）。
 *
 * ## 为什么第一格是盘而不是入口
 *
 * 批 88 量到的形状是（rounds `desktop-web-login-verdict-2026-10-11.md` §3）：源脚本 `loginWithCookies.validate`
 * 自己会走桥里的 `Network.setCookies`（= 那颗罐），而"已登录"那一格记的是 `JsSourceDataStore` 里**已经落盘**的
 * `account`。**只补入口不补盘**就成了本仓最忌的那一格假开关 —— 屏上念「已登录」而重启后请求带不上凭据。
 * 所以顺序是先盘后入口，这一批就是那半张盘。
 *
 * ## 三条口径不是这一格定的
 *
 * - **桶按请求 host 分**：照对岸 `PersistentCookieJar`（它落 SharedPreferences 时也是"每 host 一桶"）；
 * - **同名键新的赢**：域与径三者全同才算同一枚（`name + domain + path`），照对岸那颗里 `removeAll{}` 再 `add` 的形状；
 * - **落点由调用点递进来**：这颗件**不自己造目录、不在构造时"看一眼"就建文件**（批 85 那条纪律 —— 读数不许自造证据），
 *   而递进来的那棵目录本身随数据根走 ⇒ 隔离数据根把那罐凭据一起隔离（批 82.9b 同一课）。
 *
 * ## 坏档那一格为什么"留档再重开"
 *
 * 盘上那份坏掉时，静默当空罐用 = 用户以为自己还登录着而凭据没了。所以留一份 `.bad-<毫秒>` 再重开，
 * 并念一行点名是哪一个文件坏了（与画廊历史那颗同一条处理）。
 */
class DesktopCookieJarTest {

    private val root: File = File(System.getProperty("java.io.tmpdir"), "venera-cookies-${System.nanoTime()}")
    private val store = File(root, "cookies.json")
    private val ehat = "https://forums.e-hentai.org/index.php".toHttpUrl()
    private val exh = "https://exhentai.org/".toHttpUrl()

    private fun cookie(name: String, value: String, domain: String = "e-hentai.org"): Cookie =
        Cookie.Builder().name(name).value(value).domain(domain).secure().build()

    @After
    fun cleanup() {
        root.deleteRecursively()
    }

    /** ① 同一枚键写第二遍：仍是一枚，且**新的那一枚赢**。 */
    @Test
    fun 同一枚键写两遍仍是新的一枚赢() {
        val jar = DesktopCookieJar(store)
        jar.saveFromResponse(ehat, listOf(cookie("ipb_member_id", "42")))
        jar.saveFromResponse(ehat, listOf(cookie("ipb_member_id", "43")))
        val kept = jar.loadForRequest(ehat)
        assertEquals("同一枚键攒成两枚（请求头里就是两套真相）", 1, kept.size)
        assertEquals("新的那枚没赢过旧的", "43", kept.first().value)
    }

    /** ② 跨 host 不串：桶按请求 host 分（对岸那颗就是这个形状）。 */
    @Test
    fun 换了请求的host就带不上上一桶的凭据() {
        val jar = DesktopCookieJar(store)
        jar.saveFromResponse(ehat, listOf(cookie("ipb_member_id", "42")))
        assertEquals("另一颗 host 的请求被喂上了上一桶的 cookie", 0, jar.loadForRequest(exh).size)
    }

    /** ③ 登出真清空：清完那一桶是空的，而且**盘上也跟着空**（不许只清内存）。 */
    @Test
    fun 清掉那一桶之后内存与盘上都是空的() {
        val jar = DesktopCookieJar(store)
        jar.saveFromResponse(ehat, listOf(cookie("ipb_member_id", "42")))
        jar.clearForUrl(ehat)
        assertEquals("清掉之后罐里还剩东西", 0, jar.loadForRequest(ehat).size)
        assertEquals("内存清了而盘上没清（那是两条轨）", 0, DesktopCookieJar(store).loadForRequest(ehat).size)
    }

    /** ④ 落盘回读：换一颗实例（= 重启）读得到，而且七枚字段一枚不丢。 */
    @Test
    fun 重启之后凭据还在且字段一枚不丢() {
        val jar = DesktopCookieJar(store)
        jar.saveFromResponse(ehat, listOf(cookie("ipb_pass_hash", "abc123")))
        assertTrue("写完盘上根本没有那颗文件", store.isFile)

        val back = DesktopCookieJar(store).loadForRequest(ehat)
        assertEquals("重启就读不到上一趟的凭据（那入口那一格就是假开关）", 1, back.size)
        val c = back.first()
        assertEquals("回读把名字变了", "ipb_pass_hash", c.name)
        assertEquals("回读把值变了", "abc123", c.value)
        assertEquals("回读把域变了", "e-hentai.org", c.domain)
        assertEquals("回读把径变了", "/", c.path)
        assertTrue("回读丢了 secure 位（那 https 才带的凭据会被平文发出去）", c.secure)
    }

    /** ⑤ 算落点不建目录：只构造不写 ⇒ 盘上不该多出任何东西。 */
    @Test
    fun 只看一眼不许在盘上造出那颗文件() {
        assertFalse("构造时就把目录建了（读数自造证据）", root.isDirectory)
        val jar = DesktopCookieJar(store)
        assertEquals("盘上什么都没有时不该凭空读到一枚", 0, jar.loadForRequest(ehat).size)
        assertFalse("构造时把文件建了", store.exists())
        assertFalse("构造时把目录建了", root.exists())
    }

    /** ⑥ 坏档：不抛、当空罐重开，但**留一份坏档**并念一行点名。 */
    @Test
    fun 坏档不炸且留档点名() {
        root.mkdirs()
        store.writeText("{ this is not json", Charsets.UTF_8)
        val captured = java.io.ByteArrayOutputStream()
        val saved = System.out
        val jar = try {
            System.setOut(java.io.PrintStream(captured))
            DesktopCookieJar(store)
        } finally {
            System.setOut(saved)
        }
        assertEquals("坏档被读成了有货", 0, jar.loadForRequest(ehat).size)
        assertTrue("坏档那一句没念出是哪一个文件（静默吞掉 = 用户以为自己还登录着）",
            captured.toString().contains("D_凭据罐 读=坏档"))
        assertTrue("坏档没留档就被覆盖了（用户那句『我明明登录过』再也查不回去）",
            root.listFiles()?.any { it.name.startsWith("cookies.json.bad-") } == true)
        // 留档 = 把坏的那份挪走，所以现档要么还没被建、要么已经不含那串（绝不"边用边带着坏内容"）。
        val fresh = if (store.isFile) store.readText() else ""
        assertFalse("坏档还留在现档里（下一次写会把坏内容与凭据混在一颗文件）", fresh.contains("this is not json"))
    }

    /** ⑧ 那份文件的名字只许有一处出处。 */
    @Test
    fun 罐的文件名全仓只写一遍() {
        val scope = DesktopSourceTree.desktopMainSources() + DesktopSourceTree.engineProbeSourceDir()
        // 底本明确只含产品码（两份 main），所以这里不必把名字拼出来绕开判据自己（坑 ㉜ 的那三种宿主都不在这条射程里）。
        val hits = scope.flatMap { file ->
            List(DesktopSourceTree.codeOccurrences(file, "\"cookies.json\"")) { file.name }
        }
        // 漂成两处的后果不是难看：宿主写 A 而界面回核读 B，屏上那句"重启也带得上"就成了假话。
        assertEquals("那份文件的名字被写了两遍（两条轨）：$hits", listOf("DesktopCookieJar.kt"), hits)
    }

    /** ⑦ 接线：宿主吃的是**这一颗**带盘的罐，而那颗纯内存的不许留在树上（两轨不许）。 */
    @Test
    fun 宿主用的是带盘那一颗而没有第二轨() {
        val host = DesktopSourceTree.engineProbeSource("DesktopJsHost.kt")
        val code = DesktopSourceTree.codeText(host)
        assertEquals("宿主没接带盘的那颗罐：${DesktopSourceTree.codeOccurrences(host, "DesktopCookieJar(")}",
            1, DesktopSourceTree.codeOccurrences(host, "DesktopCookieJar("))
        val strays = DesktopSourceTree.engineProbeSourceDir().filter {
            DesktopSourceTree.codeText(it).contains("InMemory" + "CookieJar")
        }.map { it.name }
        assertEquals("树上还留着第二颗罐（两轨会漂成只有开发跑带着凭据）：$strays", 0, strays.size)
    }
}
