package com.venera.compose.gallery

import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.gallery.data.PixivAuthState
import com.venera.compose.gallery.data.PixivClientCredentials
import com.venera.compose.gallery.data.PixivCredentialState
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixiv 登录态的判据（方案 `docs/rounds/gallery-pixiv-account-pkce-2026-10-17.md` §四 · P2）。
 *
 * 出网那一半用 **OkHttp 的应用拦截器**伪造（不打网络、也不换掉被测代码里那一笔真实请求）：
 * 假引擎与真引擎走同一条 `newCall().execute()`，所以"什么时候才发这笔"这件事仍然是真读数。
 * 这一族的缺陷都长在**时机与措辞**上（什么时候判成要重新登录、失败那句话说出了什么），
 * 而不是长在"JSON 能不能解出来"上。
 */
class PixivCredentialStateTest {

    private class FakeStore : KeyValueStore {
        val table = mutableMapOf<String, Any?>()

        override fun getString(k: String, d: String?): String? = if (k in table) table[k] as String? else d
        override fun getBoolean(k: String, d: Boolean): Boolean = table[k] as? Boolean ?: d
        override fun getInt(k: String, d: Int): Int = table[k] as? Int ?: d
        override fun getLong(k: String, d: Long): Long = table[k] as? Long ?: d
        override fun getFloat(k: String, d: Float): Float = table[k] as? Float ?: d
        override fun getStringSet(k: String): Set<String> = table[k] as? Set<String> ?: emptySet()
        override fun put(k: String, v: Any?) {
            if (v == null) table.remove(k) else table[k] = v
        }

        override fun putAll(values: Map<String, Any?>) {
            values.forEach { (k, v) -> put(k, v) }
        }

        override fun remove(k: String) {
            table.remove(k)
        }

        override fun clear() = table.clear()
        override fun keys(): Set<String> = table.keys.toSet()
    }

    private class FakeCredentials(private val configured: Boolean = true) : PixivClientCredentials {
        override val isConfigured: Boolean get() = configured
        override fun require(): Pair<String, String> =
            if (configured) "CID" to "CSECRET" else throw IOException("没配")
    }

    private val now = 1_700_000_000_000L

    /** 站方回 200 那一档；[body] 就是换 token 的答复原文。 */
    private fun stubEngine(body: String, code: Int = 200): Pair<OkHttpClient, MutableList<String>> {
        val seen = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            seen += "${chain.request().method} ${chain.request().url}"
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("stub")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        return client to seen
    }

    private fun stateWith(
        store: FakeStore,
        engine: (() -> OkHttpClient)? = null,
        clock: () -> Long = { now },
    ) = PixivCredentialState(store, engine, clock, FakeCredentials())

    /** 盘上有完整票据、且还没到期。 */
    private fun seededStore(expiresAt: Long): FakeStore = FakeStore().apply {
        table.putAll(
            mapOf(
                PixivCredentialState.KEY_ACCESS_TOKEN to "OLD-ACCESS",
                PixivCredentialState.KEY_REFRESH_TOKEN to "OLD-REFRESH",
                PixivCredentialState.KEY_EXPIRES_AT to expiresAt,
                PixivCredentialState.KEY_USER_ID to "77",
                PixivCredentialState.KEY_USER_NAME to "旧名",
            ),
        )
    }

    private val freshResponse =
        """{"access_token":"NEW-ACCESS","refresh_token":"NEW-REFRESH","expires_in":3600,"user":{"id":"42","name":"新名"}}"""

    @Test
    fun `冷启动盘上没有票据就是没登录，不猜`() {
        val state = stateWith(FakeStore())
        assertEquals(PixivAuthState.Anonymous, state.authState.value)
        assertTrue("没登录也要念得出话", state.authState.value.screenLine.isNotBlank())
    }

    @Test
    fun `四档各自念一句不一样的话`() {
        val lines = listOf(
            PixivAuthState.Anonymous.screenLine,
            PixivAuthState.SignedIn(userId = "1", userName = "小林").screenLine,
            PixivAuthState.NeedsRelogin(reason = "刷新失败").screenLine,
            PixivAuthState.LoginRejected(reason = "回跳里没有 code").screenLine,
        )
        assertTrue("每档都得有话说：$lines", lines.none { it.isBlank() })
        assertEquals("四档两两不同：$lines", lines.size, lines.toSet().size)
        assertTrue("已登录那档要念得出是谁：$lines", lines[1].contains("小林"))
        // 「登过但刷不动」与「刚那一趟没登上」必须是两句话：合成一句，用户就分不清该等还是该再登。
        assertTrue("没登上那档要念得出成因：$lines", lines[3].contains("回跳里没有 code"))
    }

    @Test
    fun `登录态那一袋里不许有 token 本体`() {
        // 它会进 StateFlow、会出现在拼接与调试输出里 —— 而这两枚票据就是账号。
        val state = stateWith(seededStore(expiresAt = now + 3_600_000L))
        val rendered = state.authState.value.toString()
        assertFalse("authState 漏了 access_token：$rendered", rendered.contains("OLD-ACCESS"))
        assertFalse("authState 漏了 refresh_token：$rendered", rendered.contains("OLD-REFRESH"))
    }

    @Test
    fun `票据还没到期就直接用，一笔网络都不发`() {
        var asked = false
        val state = stateWith(
            seededStore(expiresAt = now + 3_600_000L),
            engine = { asked = true; throw IOException("不该出网") },
        )
        val bearer = runBlocking { state.bearerOrNull() }
        assertEquals("该用盘上那枚", "OLD-ACCESS", bearer)
        assertFalse("没到期也去刷新了", asked)
    }

    @Test
    fun `过期判定喂两种时间：还剩一分钟也算该换`() {
        // 卡到 0 秒的写法会让请求正好落在过期那一刻，症状是偶发一片 401。
        val almostGone = stateWith(seededStore(expiresAt = now + 60_000L))
        assertNull("还剩 1 分钟仍在提前换的窗口里", runBlocking { almostGone.bearerOrNull() })
        assertEquals(PixivAuthState.NeedsRelogin::class, almostGone.authState.value::class)

        val gone = stateWith(seededStore(expiresAt = now - 1L))
        assertNull(runBlocking { gone.bearerOrNull() })
        // 刷不动就明说"要重新登录"，**不许静默退回匿名档**（方案 §三 第 4 步）。
        assertTrue(
            "要说得出为什么刷不动：${gone.authState.value.screenLine}",
            gone.authState.value is PixivAuthState.NeedsRelogin,
        )
    }

    @Test
    fun `刷新成功时两枚票据与身份一起换，请求走的是 token 端点`() {
        val (client, seen) = stubEngine(freshResponse)
        val store = seededStore(expiresAt = now - 1L)
        val state = stateWith(store, engine = { client })
        assertEquals("NEW-ACCESS", runBlocking { state.bearerOrNull() })
        assertEquals("NEW-REFRESH", store.table[PixivCredentialState.KEY_REFRESH_TOKEN])
        assertEquals("NEW-ACCESS", store.table[PixivCredentialState.KEY_ACCESS_TOKEN])
        assertEquals("42", store.table[PixivCredentialState.KEY_USER_ID])
        assertEquals("新名", store.table[PixivCredentialState.KEY_USER_NAME])
        assertEquals(PixivAuthState.SignedIn("42", "新名"), state.authState.value)
        // 票据只在**请求体**里。出现在 URL 上就是另一种泄露方式（日志里通常只有 URL）。
        assertEquals(1, seen.size)
        // 端点**逐字**核：少了 `/auth` 那一段，站方回 404（2026-10-17 实测两档：`/token` = 404
        // `404 Not Found`、`/auth/token` = 400 `invalid_grant`/1508）。所以这里钉全路径，
        // 而不是钉一个"含 token 就算过"的宽松式 —— 宽松式正是让 404 那版活下来的原因。
        assertTrue(
            "请求该打到站点那个 `/auth/token` 端点（少一段就是 404）：${seen[0]}",
            seen[0].startsWith("POST https://oauth.secure.pixiv.net/auth/token"),
        )
        assertTrue("URL 里不许挂票据：${seen[0]}", !seen[0].contains("OLD-REFRESH"))
    }

    @Test
    fun `站方不换新的 refresh_token 时手里那枚留着`() {
        // 刷新答复里**没有** refresh_token 是合法行为（不轮换）。这时候若按首登那条判据硬抛，
        // 用户会被踢成"要重新登录" —— 那是把站方的正常答复读成了故障。
        val (client, _) = stubEngine("""{"access_token":"ROTATED","expires_in":3600}""")
        val store = seededStore(expiresAt = now - 1L)
        val state = stateWith(store, engine = { client })
        assertEquals("ROTATED", runBlocking { state.bearerOrNull() })
        assertEquals("OLD-REFRESH", store.table[PixivCredentialState.KEY_REFRESH_TOKEN])
    }

    @Test
    fun `换 token 失败那句话说得出状态码，但不说得出票据`() {
        val (client, _) = stubEngine("""{"error":{"user_message":"invalid grant"}}""", code = 400)
        val state = stateWith(seededStore(expiresAt = now - 1L), engine = { client })
        assertNull(runBlocking { state.bearerOrNull() })
        val reason = (state.authState.value as PixivAuthState.NeedsRelogin).reason
        assertTrue("要说得出站方回了 400：$reason", reason.contains("400"))
        assertFalse("失败消息里不许带手里的 refresh_token：$reason", reason.contains("OLD-REFRESH"))
        assertFalse("失败消息里不许带 client_secret：$reason", reason.contains("CSECRET"))
    }

    @Test
    fun `state 不符先失败且不落盘`() {
        // 回跳由外部浏览器交回来：拿别人塞进来的码去 POST 我们自己的 client_secret，
        // 等于替别人跑一次登录。所以核 state 必须排在出网**之前**，这一条就是钉这个顺序的。
        val (client, seen) = stubEngine(freshResponse)
        val store = FakeStore()
        val state = stateWith(store, engine = { client })
        val challenge = checkNotNull(state.beginLogin())
        val result = runBlocking { state.completeLogin("pixiv://account?code=EVIL&state=WRONG") }
        assertTrue("state 不符必须失败：$result", result.isFailure)
        assertTrue(
            "要念得出是 state 的问题：${result.exceptionOrNull()?.message}",
            result.exceptionOrNull()?.message?.contains("state") == true,
        )
        assertTrue("核 state 在出网之前：$seen", seen.isEmpty())
        assertTrue("失败不许落盘：${store.table}", store.table.isEmpty())
    }

    @Test
    fun `没有进行中的登录时不许拿任意回跳当登录`() {
        val state = stateWith(FakeStore())
        val result = runBlocking { state.completeLogin("pixiv://account?code=ABC&state=XYZ") }
        assertTrue("凭空的一枚码不算登录：$result", result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `核身通过之后授权码换票据才出网`() {
        val (client, seen) = stubEngine(freshResponse)
        val state = stateWith(FakeStore(), engine = { client })
        val challenge = checkNotNull(state.beginLogin())
        val result = runBlocking {
            state.completeLogin("pixiv://account?code=GOOD&state=${challenge.state}")
        }
        assertEquals(PixivAuthState.SignedIn("42", "新名"), result.getOrNull())
        assertEquals(1, seen.size)
    }

    @Test
    fun `没配 client 凭据时起不了登录，也不出一笔网`() {
        // 打开一张注定换不到 token 的登录页就是假开关：用户输完密码才知道不行。
        val state = PixivCredentialState(FakeStore(), null, { now }, FakeCredentials(configured = false))
        assertFalse(state.isClientConfigured)
        assertNull(state.beginLogin())
    }

    @Test
    fun `回跳判定认的是登记过的那两条地址，不是整个 scheme`() {
        // 判定只此一颗**静态**（P6 之后唯一的调用点在 MainActivity 的入口分流）。
        // 做成静态而不是契约方法：那一句问在冷启动主线程上，不该为它实例化账号那颗件
        // （它会读一次 SharedPreferences）。若这里退化成"认 scheme"，
        // `pixiv://artworks/…` 那种站内深链会被当成回跳并送去换票据。
        assertTrue(PixivCredentialState.isLoginCallbackUrl("pixiv://account?code=ABC&state=S"))
        assertTrue(PixivCredentialState.isLoginCallbackUrl("pixiv://account/login?code=ABC"))
        // 2026-10-17 第四轮：站方登记的 https 回跳也要认（浏览器那一侧可能先落在这里）。
        assertTrue(
            "https 回跳没认：${com.venera.compose.gallery.data.PixivPkce.REDIRECT_URI}",
            PixivCredentialState.isLoginCallbackUrl(
                com.venera.compose.gallery.data.PixivPkce.REDIRECT_URI + "?code=ABC",
            ),
        )
        assertFalse("登录页自己那几跳是 https", PixivCredentialState.isLoginCallbackUrl("https://accounts.pixiv.net/login"))
        assertFalse("同一 scheme 的别的地址不算回跳", PixivCredentialState.isLoginCallbackUrl("pixiv://artworks/123"))
        assertFalse(
            "站方域名下别的路径也不许认（否则一条普通作品链接会被送去换票据）",
            PixivCredentialState.isLoginCallbackUrl("https://app-api.pixiv.net/v2/illust/123"),
        )
    }

    @Test
    fun `登录没成要把那一档推到界面上，而不是只回一个 Result`() {
        // P6 撤掉 WebView 之后，屏上再无别的读点：只回 Result 而没人接 = 失败与"没点过登录"同脸。
        // ⑴ 没有进行中的会话（进程被回收后回跳迟到、或别人手工丢一条 `pixiv://account`）。
        val idle = stateWith(FakeStore())
        val late = runBlocking { idle.completeLogin("pixiv://account?code=ABC&state=S") }
        assertTrue(late.isFailure)
        val pushed = idle.authState.value
        assertTrue("没推成一档：$pushed", pushed is PixivAuthState.LoginRejected)
        assertTrue("要说得出是「没有进行中的会话」：$pushed", (pushed as PixivAuthState.LoginRejected).reason.contains("没有进行中的登录"))

        // ⑵ state 不符（别人塞进来的码）—— 出网之前就该判掉，并把这一档推上屏。
        val (client, seen) = stubEngine(freshResponse)
        val state = stateWith(FakeStore(), engine = { client })
        state.beginLogin()
        val mismatched = runBlocking { state.completeLogin("pixiv://account?code=ABC&state=OTHER") }
        assertTrue(mismatched.isFailure)
        assertTrue("一票都不该出：$seen", seen.isEmpty())
        assertTrue("这一档也要推上屏：${state.authState.value}", state.authState.value is PixivAuthState.LoginRejected)
    }

    @Test
    fun `已登录时一次失败的换票据不洗掉凭据`() {
        // 口径同 `GelbooruCredentialState.signIn`：一次失败不许把本来正确的那份抹掉。
        val (client, _) = stubEngine("""{"error":"invalid_grant"}""", code = 400)
        val store = seededStore(expiresAt = now + 3_600_000L)
        val state = stateWith(store, engine = { client })
        val challenge = checkNotNull(state.beginLogin())
        val failed = runBlocking { state.completeLogin("pixiv://account?code=ABC&state=${challenge.state}") }
        assertTrue(failed.isFailure)
        assertEquals(
            "本来登着的人被一次失败读成了没登录：${state.authState.value}",
            PixivAuthState.SignedIn("77", "旧名"),
            state.authState.value,
        )
        assertTrue("盘上票据一枚不许少：${store.table}", store.table.isNotEmpty())
        val rendered = state.authState.value.toString()
        assertFalse("登录态里漏了凭据本体：$rendered", rendered.contains("OLD-ACCESS") || rendered.contains("OLD-REFRESH"))
    }

    @Test
    fun `注销之后盘上一枚票据都不留`() {
        val store = seededStore(expiresAt = now + 3_600_000L)
        val state = stateWith(store)
        state.signOut()
        assertTrue("注销留了东西：${store.table}", store.table.isEmpty())
        assertEquals(PixivAuthState.Anonymous, state.authState.value)
        assertNull(runBlocking { state.bearerOrNull() })
    }

    // ── 粘贴兜底那一路（2026-10-17 第四轮，用户点了 B）──
    //
    // 浏览器那一路偶尔断在半路（系统没把回跳交回来、或用户是在另一台机器上登的），
    // 这一条是所有同类客户端的公共出口。三种形状的判据各钉一条 —— 判据错了的表现是
    // "粘进去之后界面说已登录、请求全 401"，那比当场失败难解释得多。

    @Test
    fun `粘一枚 refresh token 就去换一套新票据`() {
        val (client, seen) = stubEngine(freshResponse)
        val store = FakeStore()
        val state = stateWith(store, engine = { client })
        val result = runBlocking { state.signInWithPasted("  PASTED-REFRESH  ") }
        assertEquals(PixivAuthState.SignedIn("42", "新名"), result.getOrNull())
        assertEquals("要出一笔换票据的网：$seen", 1, seen.size)
        assertEquals("NEW-REFRESH", store.table[PixivCredentialState.KEY_REFRESH_TOKEN])
        assertEquals("NEW-ACCESS", store.table[PixivCredentialState.KEY_ACCESS_TOKEN])
    }

    @Test
    fun `粘一整套 token JSON 时一笔网都不发`() {
        // 这一档手里已经有票据了，再打一次 refresh 等于把用户刚粘进来的那枚用掉。
        val (client, seen) = stubEngine(freshResponse)
        val state = stateWith(FakeStore(), engine = { client })
        val result = runBlocking {
            state.signInWithPasted("""{"access_token":"A","refresh_token":"R","expires_in":3600,"user":{"id":"9","name":"粘"}}""")
        }
        assertEquals(PixivAuthState.SignedIn("9", "粘"), result.getOrNull())
        assertTrue("这一档不该出网：$seen", seen.isEmpty())
    }

    @Test
    fun `粘回跳链接走的是与浏览器同一条路，没有会话就如实失败`() {
        // 那一枚码要用发起登录时的 verifier 才能换，而 verifier 只活在内存里 ——
        // 没有会话时不许假装能登（假开关），也不许出一笔网。
        val (client, seen) = stubEngine(freshResponse)
        val idle = stateWith(FakeStore(), engine = { client })
        val late = runBlocking { idle.signInWithPasted("pixiv://account/login?code=ABC") }
        assertTrue("没有会话时必须失败：$late", late.isFailure)
        assertTrue("不许出网：$seen", seen.isEmpty())
        assertTrue(
            "要念得出是「没有进行中的登录」：${late.exceptionOrNull()?.message}",
            late.exceptionOrNull()?.message?.contains("没有进行中的登录") == true,
        )

        // 有会话时才真的换（与浏览器回跳同一条 completeLogin）。
        val (client2, seen2) = stubEngine(freshResponse)
        val live = stateWith(FakeStore(), engine = { client2 })
        val challenge = checkNotNull(live.beginLogin())
        val ok = runBlocking {
            live.signInWithPasted("pixiv://account/login?code=GOOD&state=${challenge.state}")
        }
        assertEquals(PixivAuthState.SignedIn("42", "新名"), ok.getOrNull())
        assertEquals(1, seen2.size)
    }

    @Test
    fun `粘一条空串不算登录，也不许动已有凭据`() {
        val (client, seen) = stubEngine(freshResponse)
        val store = seededStore(expiresAt = now + 3_600_000L)
        val state = stateWith(store, engine = { client })
        val result = runBlocking { state.signInWithPasted("   ") }
        assertTrue("空串必须失败：$result", result.isFailure)
        assertTrue("不许出网：$seen", seen.isEmpty())
        assertEquals("OLD-REFRESH", store.table[PixivCredentialState.KEY_REFRESH_TOKEN])
        assertEquals(PixivAuthState.SignedIn("77", "旧名"), state.authState.value)
    }
}
