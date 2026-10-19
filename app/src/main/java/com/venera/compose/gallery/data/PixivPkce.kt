package com.venera.compose.gallery.data

import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * pixiv 登录的**纯算式**那一半（方案 `docs/rounds/gallery-pixiv-account-pkce-2026-10-17.md` §三 · P1）。
 *
 * 这一颗刻意不碰网络、不碰 `Context`、不存任何东西：换 token 与落盘在 P2 那两颗里。
 * 分开是因为这一族的缺陷都长在"算式对不对"上（challenge 编码、`state` 核不核、URL 转义），
 * 而那些**单测测得到**；网络那一半只有真机能验，混在一起就等于两半都测不了。
 *
 * PKCE 走 RFC 7636 的 `S256`：`code_challenge = BASE64URL(SHA256(code_verifier))`（**不带填充**）。
 */
internal object PixivPkce {

    /**
     * 换 token 时**必须回填**的那一条登记地址。
     *
     * 2026-10-17 第四轮改：从 `pixiv://account` 换成站方 App 登录那条 https 回跳。
     * 依据是 pixez-miuix（`network/OAuthClient.kt` 的常量组 + `composeApp/src/androidMain/AndroidManifest.xml`
     * 里那颗 `https://app-api.pixiv.net` 的 intent-filter）：`pixiv-android` 这个 client 在站方登记的
     * 就是这一条。⚠️ 它与**回跳实际落到手机上的形态**是两件事 —— 见 [DEEP_LINK_PREFIX]。
     */
    const val REDIRECT_URI = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback"

    /**
     * 回跳到手机上的那一条自定义 scheme（站方登完会跳 `pixiv://account/login?code=…`）。
     *
     * ⚠️ 这个字面量**只许出现在本文件**：`GalleryPixivAccountWiringTest.回跳判定只有一处` 会扫全仓
     * 代码行，别处再拼一份就是第二把尺子（改了这里只坏一半，表现是"登录页照常开、回跳永远不算回跳"）。
     */
    const val DEEP_LINK_PREFIX = "pixiv://account"

    /**
     * 站方 App 登录页（不是标准 OAuth 的 `/authorize`）。
     *
     * `client=pixiv-android` 是站方自己那个客户端的标识，登录成功后它会按 App 那条路回跳；
     * 标准 OAuth 的 `accounts.pixiv.net/authorize` 需要我们自己登记过应用并过审 —— 那条今天走不通。
     */
    private const val AUTHORIZE_BASE = "https://app-api.pixiv.net/web/v1/login"

    /** 站方认的那个客户端标签（App 登录页要这一枚，不是我们的 client_id）。 */
    private const val AUTH_CLIENT_LABEL = "pixiv-android"

    private const val VERIFIER_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private const val VERIFIER_LENGTH = 64
    private const val STATE_LENGTH = 24

    private val json = Json { ignoreUnknownKeys = true }

    /** `code_verifier`：URL-safe 字符集里的随机一段（RFC 7636 允许 43~128 位，这里取 64）。 */
    fun newVerifier(random: SecureRandom = SecureRandom()): String =
        buildString(VERIFIER_LENGTH) {
            repeat(VERIFIER_LENGTH) { append(VERIFIER_ALPHABET[random.nextInt(VERIFIER_ALPHABET.length)]) }
        }

    /** 防 CSRF 的那一段。与 verifier 各抽各的：混用会让"核 state"变成核自己。 */
    fun newState(random: SecureRandom = SecureRandom()): String =
        buildString(STATE_LENGTH) {
            repeat(STATE_LENGTH) { append(VERIFIER_ALPHABET[random.nextInt(VERIFIER_ALPHABET.length)]) }
        }

    /** `S256` 的 challenge：SHA-256 之后 base64url、**去掉 `=` 填充**（留着填充站方不认）。 */
    fun challengeOf(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * 登录页地址。
     *
     * 2026-10-17 第四轮：改成站方 App 登录页那三枚参数（`code_challenge` / `code_challenge_method` /
     * `client`），与 pixez 的 `buildLoginUrl` 逐项对应。旧的 `client_id` / `response_type` /
     * `redirect_uri` / `prompt=login` 是标准 OAuth 那一套，这一页不认，带上只会被忽略。
     *
     * `state` **仍然带着**：站方这一页今天有没有回声还没在真机核过（没回声时不拒，见 [codeFrom]），
     * 带着它不改变任何行为，哪天真回声了就直接生效。
     */
    fun authorizeUrl(codeChallenge: String, state: String): String =
        AUTHORIZE_BASE.toHttpUrl().newBuilder()
            .addQueryParameter("code_challenge", codeChallenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("client", AUTH_CLIENT_LABEL)
            .addQueryParameter("state", state)
            .build()
            .toString()

    /**
     * 从回跳地址里取 `code`，并核 `state`。
     *
     * 站方在用户取消/拒绝时会带 `error=` 回来，那一支直接抛，并且把站方那句原样带上
     * （"登录失败"有三种成因，读成一种就是假读数）。
     *
     * ## 为什么"回跳里没有 `state`"**不**拒（2026-10-17 第四轮）
     *
     * App 登录页（`client=pixiv-android`）不是标准 OAuth 端点：pixez 那一条**压根不发** `state`
     * 也能换成票据，说明它至少在某些回跳形态里不回声。若我们坚持"缺了就拒"，
     * 表现会是"浏览器里明明登成功了、回 app 一句 state 不符" —— 而这一条在真机上没法预演。
     *
     * 少这一道并不削弱安全性：真正绑定这一枚码的是 **PKCE 的 `code_verifier`**
     * （一次性、只活在本次进程内存里）。别人塞一枚自己的授权码进来，我们拿自己的 verifier 去换，
     * 站方会回 `invalid_grant` —— 核 `state` 在这里是加分项，不是唯一那道闸。
     * 所以判据收紧成：**给了却不等必须拒**，没给不拒。
     */
    fun codeFrom(redirectUrl: String, expectedState: String): String {
        val url = queryOf(redirectUrl)
        url.queryParameter("error")?.let { error ->
            throw IOException("pixiv 那一侧回的是错误：$error（${url.queryParameter("error_description").orEmpty()}）")
        }
        val state = url.queryParameter("state")
        if (state != null && state != expectedState) {
            throw IOException("回跳的 state 与我们发出去的不一致（回跳里带的是另一枚 state）—— 不接受这一枚码")
        }
        return url.queryParameter("code")
            // 只点名**项名**、不带任何项的值：这条消息会沿 `PixivAuthState.LoginRejected` 上屏，
            // 而回跳地址里就写着授权码本体（方案 §三 红线：凭据不进日志、不进报错、不进界面）。
            ?: throw IOException("回跳里没有 code（只带了这些项：${url.queryParameterNames.joinToString("、").ifBlank { "一个都没有" }}）")
    }

    /**
     * 把回跳的 query 交回 `HttpUrl` 解。
     *
     * ⚠️ 这里**不能**直接 `redirectUrl.toHttpUrl()`：OkHttp 的 `HttpUrl` 只认
     * `http/https/ws/wss` 四个 scheme，`pixiv://account?code=…` 会**恒抛**「读不懂」——
     * 于是用户在浏览器里登录成功、我们这一侧每次都报"地址读不懂"。
     * 所以只截 `?` 之后那一段挂到占位基座上：要的是 `HttpUrl` 的百分号解码
     * （`error_description` 里就有空格），不是它对 scheme 的把关。
     */
    private fun queryOf(redirectUrl: String): HttpUrl {
        val query = redirectUrl.substringAfter('?', "")
        // 只带 `?` **之前**那一截（就是回跳地址本身），查询段一个字不抄 —— 它里面就是凭据。
        val bare = redirectUrl.substringBefore('?')
        if (query.isEmpty()) throw IOException("pixiv 回跳里没有 query：$bare")
        return runCatching { ("https://venera.invalid/?$query").toHttpUrl() }
            .getOrElse { throw IOException("pixiv 回跳地址读不懂：$bare") }
    }

    /**
     * 换 token 那一步的答复解析。
     *
     * ⚠️ 两枚 token 缺一枚都抛 —— 只拿到 `access_token` 而没有 `refresh_token` 的账号
     * 会在几十分钟后变成"界面说已登录、请求全 401"，那比当场失败难解释得多。
     * 消息里**不写 token 本身**（口径同方案 §三：不进日志、不进报错）。
     *
     * [keepRefreshOnAbsent] 只给**刷新**那一跳用：站方不换新的那枚时（这是合法行为）答复里就没有
     * `refresh_token`，此时把手里那一枚留着。首登传 null，所以"半套票据"在那里仍然是失败。
     */
    fun tokensOf(body: String, keepRefreshOnAbsent: String? = null): PixivTokens {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            // 只把**非 JSON** 那一档的原文带上（风控页/登录页是一页 HTML，截一段就能定性）；
            // 能解析成 JSON 却不是一个对象 ⇒ 一个字都不带 —— 那种答复里可能写着票据本体，
            // 而这条消息会沿 `PixivAuthState.LoginRejected` 上屏（方案 §三 红线：token 不进报错）。
            .getOrElse {
                val head = if (it is SerializationException) body.take(120) else ""
                throw IOException(
                    "pixiv 换 token 回来的不是 JSON 对象（${body.length} 字节）" +
                        if (head.isBlank()) "" else "：$head",
                )
            }
        val access = root["access_token"].text() ?: throw IOException("pixiv 换 token 的答复里没有 access_token")
        val refresh = root["refresh_token"].text()
            ?: keepRefreshOnAbsent
            ?: throw IOException("pixiv 换 token 的答复里没有 refresh_token")
        val user = root["user"] as? JsonObject
        return PixivTokens(
            accessToken = access,
            refreshToken = refresh,
            expiresInSeconds = root["expires_in"].text()?.toLongOrNull() ?: 0L,
            userId = user?.get("id").text(),
            userName = user?.get("name").text(),
        )
    }

    private fun JsonElement?.text(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

/**
 * 一次成功登录拿到的那四样（`expiresInSeconds` 是**相对值**，落盘时换成绝对时刻，见 P2）。
 *
 * `toString` 刻意遮掉两枚 token：这一袋会被塞进日志与异常消息的场合不少，
 * 而这两枚就是账号本体。
 */
data class PixivTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val userId: String?,
    val userName: String?,
) {
    override fun toString(): String = "PixivTokens(userId=$userId, userName=$userName, expiresInSeconds=$expiresInSeconds)"
}
