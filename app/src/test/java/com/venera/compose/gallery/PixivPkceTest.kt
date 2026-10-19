package com.venera.compose.gallery

import com.venera.compose.gallery.data.PixivPkce
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixiv 登录算式的判据（方案 §四 · P1）。
 *
 * 这一族之所以值得单钉：算式错了的表现**不在登录页**，而在几十分钟之后
 * —— challenge 编码错、`state` 没核、token 少一枚，屏上都还是"已登录"，
 * 请求却开始整片 401。所以每一条都钉**整串**，不钉"有没有包含"。
 */
class PixivPkceTest {

    /** RFC 7636 附录 B 那一组 `S256` 已知向量：拿标准答案对，不拿自己的输出当答案。 */
    @Test
    fun `S256 challenge 对得上 RFC 7636 的已知向量`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            PixivPkce.challengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `challenge 不带 base64 填充`() {
        // 留着 `=` 站方不认，而这一条用任何一串都能过 ⇒ 挑一枚**编码后本来会有填充**的输入。
        val verifier = PixivPkce.newVerifier()
        repeat(40) { i ->
            val v = "verifier-with-padding-check-" + i
            assertTrue("challenge 里不许有填充：$v", !PixivPkce.challengeOf(v).endsWith("="))
        }
        assertTrue(verifier.isNotEmpty())
    }

    @Test
    fun `verifier 与 state 各抽各的，且只用 URL-safe 那一套字符`() {
        val a = PixivPkce.newVerifier()
        val b = PixivPkce.newVerifier()
        assertEquals(64, a.length)
        assertNotEquals("两次抽到同一个 verifier（随机源坏了）", a, b)
        assertTrue("字符集越界：$a", a.all { it.isLetterOrDigit() || it in "-._~" })
        assertNotEquals(a, PixivPkce.newState())
        assertEquals(24, PixivPkce.newState().length)
    }

    @Test
    fun `authorize 地址就是站方 App 登录页那几枚参数`() {
        // 2026-10-17 第四轮改：从标准 OAuth 的 `accounts.pixiv.net/authorize` 换成站方 App
        // 登录页（pixez `buildLoginUrl` 那一条）。钉**整串前缀**而不是"包含某个词"：
        // 端点是这一族最容易悄悄改回去的一格，而改回去的表现是"页面打得开、换 token 恒 400"。
        val url = PixivPkce.authorizeUrl(codeChallenge = "CHAL", state = "ST/ATE~x")
        assertTrue(
            "登录页必须是站方 App 那一页：$url",
            url.startsWith("https://app-api.pixiv.net/web/v1/login?"),
        )
        assertTrue("少了 challenge：$url", url.contains("code_challenge=CHAL"))
        assertTrue("方法必须是 S256：$url", url.contains("code_challenge_method=S256"))
        assertTrue("少了 client 标签（站方要 pixiv-android）：$url", url.contains("client=pixiv-android"))
        assertTrue("state 没转义：$url", url.contains("state=ST%2FATE%7Ex"))
        // 标准 OAuth 那四枚在这一页上不认（带上只是被忽略），一个都不许出现 ——
        // 出现即说明有人把端点改回 authorize 却没改参数组。
        for (stale in listOf("client_id=", "response_type=", "redirect_uri=", "prompt=")) {
            assertTrue("这一页不认 $stale，别混着发：$url", !url.contains(stale))
        }
    }

    @Test
    fun `回跳取码：state 给了却不等必须拒，没给不拒`() {
        val ok = PixivPkce.codeFrom("pixiv://account?code=ABC&state=XYZ", expectedState = "XYZ")
        assertEquals("ABC", ok)

        val mismatched = runCatching { PixivPkce.codeFrom("pixiv://account?code=ABC&state=OTHER", "XYZ") }
        assertTrue("state 不符必须抛：$mismatched", mismatched.exceptionOrNull() is IOException)
        assertTrue("要说出是 state 的问题：${mismatched.exceptionOrNull()?.message}",
            mismatched.exceptionOrNull()?.message?.contains("state") == true)

        // 2026-10-17 第四轮：站方 App 登录页**不回声** state（pixez 那一条压根不发也能用），
        // 所以"没有这一项"不拒；真正绑住这一枚码的是 PKCE 的 verifier（一次性、只在内存里）。
        assertEquals(
            "没有 state 时应当照收（站方这一页不回声）",
            "ABC",
            PixivPkce.codeFrom("pixiv://account/login?code=ABC", "XYZ"),
        )

        val missing = runCatching { PixivPkce.codeFrom("pixiv://account?state=XYZ", "XYZ") }
        assertTrue("没有 code 要抛而不是交空串：$missing", missing.exceptionOrNull() is IOException)
    }

    @Test
    fun `两种回跳形态都取得出 code`() {
        // 站方可能直接把 `pixiv://account/login?code=…` 交给系统，也可能先落到它自己登记的
        // https 回调再由浏览器转一手 —— 少认一条的表现是"浏览器里登成功、回 app 毫无反应"。
        assertEquals("A1", PixivPkce.codeFrom("pixiv://account/login?code=A1", "S"))
        assertEquals(
            "A2",
            PixivPkce.codeFrom(PixivPkce.REDIRECT_URI + "?code=A2&state=S", "S"),
        )
    }

    @Test
    fun `站方回错误时把那一句原样带上，不读成密码错了`() {
        val e = runCatching {
            PixivPkce.codeFrom("pixiv://account?error=access_denied&error_description=user%20cancelled&state=S", "S")
        }.exceptionOrNull()
        assertTrue("取消/拒绝也要抛：$e", e is IOException)
        assertTrue("要带上站方的 error：${e?.message}", e?.message?.contains("access_denied") == true)
        assertTrue("要带上站方那句说明：${e?.message}", e?.message?.contains("user cancelled") == true)
    }

    @Test
    fun `换 token 的答复缺任一枚都抛，只拿到半套不算登录成功`() {
        val full = """{"access_token":"A","refresh_token":"R","expires_in":3600,"user":{"id":"7","name":"某"}}"""
        val tokens = PixivPkce.tokensOf(full)
        assertEquals("A", tokens.accessToken)
        assertEquals("R", tokens.refreshToken)
        assertEquals(3600L, tokens.expiresInSeconds)
        assertEquals("7", tokens.userId)
        assertEquals("某", tokens.userName)

        // 站方这一族的 expires_in 有时是字符串 ⇒ 两种形状都要吃（吃不下就是 0，会立刻误判成"已过期"）。
        assertEquals(3600L, PixivPkce.tokensOf("""{"access_token":"A","refresh_token":"R","expires_in":"3600"}""").expiresInSeconds)
        assertEquals(0L, PixivPkce.tokensOf("""{"access_token":"A","refresh_token":"R"}""").expiresInSeconds)

        assertTrue("缺 access 必须抛", runCatching { PixivPkce.tokensOf("""{"refresh_token":"R"}""") }.exceptionOrNull() is IOException)
        // 只拿到 access 而没 refresh = 几十分钟后变成"界面说已登录、请求全 401"，比当场失败难解释。
        assertTrue("缺 refresh 必须抛", runCatching { PixivPkce.tokensOf("""{"access_token":"A"}""") }.exceptionOrNull() is IOException)
        assertTrue("不是 JSON 要抛", runCatching { PixivPkce.tokensOf("<html>") }.exceptionOrNull() is IOException)
    }

    @Test
    fun `两枚 token 不出现在 toString 里`() {
        // 这一袋会被读进日志与异常消息的场合不少，而这两枚就是账号本体。
        val tokens = PixivPkce.tokensOf(
            """{"access_token":"SECRET-ACCESS","refresh_token":"SECRET-REFRESH","expires_in":60,"user":{"id":"1"}}""",
        )
        assertTrue("toString 漏了 access_token：${tokens}", !tokens.toString().contains("SECRET-ACCESS"))
        assertTrue("toString 漏了 refresh_token：${tokens}", !tokens.toString().contains("SECRET-REFRESH"))
        assertTrue("toString 要能认出是谁：${tokens}", tokens.toString().contains("userId=1"))
    }
}
