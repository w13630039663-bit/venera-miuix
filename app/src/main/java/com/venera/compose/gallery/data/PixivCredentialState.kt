package com.venera.compose.gallery.data

import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.data.platform.NoInteractiveBypassTag
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * pixiv 的登录态（方案 `docs/rounds/gallery-pixiv-account-pkce-2026-10-17.md` §四 · P2）。
 *
 * 三档而不是两档，是因为**中间那一档真会出现**：`access_token` 几十分钟就换一次，
 * 而换新的那一跳可能撞上风控、断网、站方 500。这时候"没登录"与"登过但现在刷不动"是两件事：
 * 前者该请用户去登录，后者该请他**重新登一次**（而不是把界面退回匿名档假装一切正常 ——
 * 方案 §三 第 4 步钉的就是这条：降级不许静默）。
 *
 * 2026-10-21 补一条边界：只有**站方明确判 refresh token 失效**（400 `invalid_grant` / 401）
 * 才进 [NeedsRelogin]。403 是出口/风控，408/429/5xx 与连接失败是暂时性故障；那些档
 * 只要旧 access token 还没真过期，就继续用它，不能因为换票失败而把用户踢去重登。
 */
sealed interface PixivAuthState {

    /** 界面上念的那一句。文案住在判据件而不是各颗 UI：两端（和首页那一节）只许有一份。 */
    val screenLine: String

    /** 盘上什么都没有。 */
    object Anonymous : PixivAuthState {
        override val screenLine: String = "登录 pixiv 账号之后才有"
    }

    /**
     * 已登录。
     *
     * ⚠️ 这一档**刻意不带 token**：它会进 `StateFlow`、会出现在调试输出与异常拼接里，
     * 而 token 就是账号本体（方案 §三 红线二）。要取 token 只走 [PixivCredentials.bearerOrNull]。
     */
    data class SignedIn(val userId: String?, val userName: String?) : PixivAuthState {
        override val screenLine: String
            get() = if (userName.isNullOrBlank()) "已登录 pixiv" else "已登录：$userName"
    }

    /**
     * 登过、但换不到新的了 —— **要重新登录**。
     *
     * [reason] 只带站方那句话或本地异常的消息（[PixivCredentialState] 那边保证消息里没有 token），
     * 界面可以把它当副标题念出来；念不出成因的失败会被读成"这站没图"。
     */
    data class NeedsRelogin(val reason: String) : PixivAuthState {
        override val screenLine: String = "pixiv 登录失效了，重新登录一次吧"
    }

    /**
     * **刚那一趟登录没成**（回跳核不住 `state`、站方拒了授权码、或压根没有进行中的会话）。
     *
     * 与 [NeedsRelogin] 分两档：那一句说"登过、现在刷不动"，这一句说"这一趟没登上"，
     * 合成一句用户就分不清该等还是该再登一次。
     *
     * 它是 P6 之后**唯一**能让用户在屏上读出"登录失败"的地方：登录改走外部浏览器之后，
     * 那一屏 WebView 里的红字没有了，而成功是头像自己变脸 —— 失败若没有档，
     * 屏上表现就与"用户根本没点过登录"逐像素相同（方案 §三 红线：降级不许静默）。
     */
    data class LoginRejected(val reason: String) : PixivAuthState {
        override val screenLine: String get() = "pixiv 这一趟没登上：$reason"
    }
}

/**
 * 一次登录尝试带出去的那三样。
 *
 * `codeVerifier` 与 `state` **只活在内存里**：它们只服务于这一次回跳，落盘等于多留一份
 * 可以被人翻出来的材料。进程没了就重来一次登录，代价是一屏而不是数据。
 */
data class PixivLoginChallenge(val authorizeUrl: String, val codeVerifier: String, val state: String)

/**
 * pixiv 凭据的**对外那一面**（口径同 `GelbooruCredentials`：契约不带 `Context`）。
 *
 * 与 Gelbooru 那一对**形状不同**是必然的：那边的 `user_id` + `api_key` 是静态的，
 * 界面上"配没配"跟一次 `val` 读就够；这边两枚是有保质期的票据，所以只有 `suspend` 的
 * [bearerOrNull] 能回答"现在还能不能用"。把 `accessToken` 做成同步 `val` 就等于
 * 让调用方自己判断过期 —— 那是第二个判据，正是这一族缺陷的来源。
 */
interface PixivCredentials {

    /** 登录态（渲染用）。冷启动从盘上读回，不预设"打开就发请求"。 */
    val authState: StateFlow<PixivAuthState>

    /** 本机有没有填过那一对 client 凭据（[PixivOAuthCredentials]）；没填就别开登录页。 */
    val isClientConfigured: Boolean

    /**
     * 一枚当下能用的 Bearer；**null = 这一会儿给不出**（没登录，或刷新失败已推成 [PixivAuthState.NeedsRelogin]）。
     *
     * 拿 null 的调用方**不许退回匿名再打同一笔请求** —— 那一档的区别在界面上看不出来，
     * 用户会以为"登录后也没多什么"。
     */
    suspend fun bearerOrNull(): String?

    /** 起一次登录：给出要打开的地址与留在内存里的 verifier/state。没配 client 凭据就是 null。 */
    fun beginLogin(): PixivLoginChallenge?

    /** 回跳地址进来：先核 `state`，过了才去换票据（顺序见实现处那条判据）。 */
    suspend fun completeLogin(redirectUrl: String): Result<PixivAuthState>

    /**
     * 粘贴一条东西来登录（token JSON / 回跳链接 / 光秃秃的 refresh token 三选一）。
     *
     * 浏览器那一路断在半路时的公共出口；认哪三种形状、按什么顺序试，写在实现处那条注释上。
     */
    suspend fun signInWithPasted(text: String): Result<PixivAuthState>

    /** 注销：票据与身份一起删。 */
    fun signOut()
}

/**
 * 判据本体：落哪、什么时候算过期、失败念哪一句。
 *
 * ## 为什么与 [PixivAccount] 分两颗
 *
 * 照 `GelbooruAccount` / `GelbooruCredentialState` 那一对的分法：吃 `Context` 的那颗在桌面编译面外，
 * 判据这颗两端都能编。今天桌面没有 pixiv 的取数路径，但"登录态"这件事只许有一份判据。
 *
 * @param store 落盘口。Android 侧 `AndroidKeyValueStore`，桌面侧 `JsonKeyValueStore`。
 * @param engine 出网口；null = 这一侧没接（[NotWiredOnDesktop]），刷新就判成"换不动"。
 * @param clock 现在几点。**测试从这里喂两种时间** —— 过期判定若不接时钟就只能等 40 分钟，
 *   而"等 40 分钟再看一次"不是判据。
 */
class PixivCredentialState(
    private val store: KeyValueStore,
    private val engine: (() -> OkHttpClient)? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    /**
     * 那一对 client 凭据的**取用面**。默认取真件 [PixivOAuthCredentials]；
     * 单测必须能递自己那一份 —— 否则"核 `state` 发生在出网之前"这条判据压根测不到
     * （真件今天没填，[beginLogin] 恒回 null，连一次会话都起不来）。
     */
    private val client: PixivClientCredentials = PixivOAuthCredentials,
) : PixivCredentials {

    private val _authState = MutableStateFlow(restore())

    override val authState: StateFlow<PixivAuthState> = _authState.asStateFlow()

    override val isClientConfigured: Boolean get() = client.isConfigured

    /**
     * 同一时刻只允许一笔换票。
     *
     * 首页、推荐、画师页会几乎同时问票据；refresh token 在站方是**会轮换**的，
     * 几笔并发的 refresh 可能只有第一笔成功，后到的 400/403 再把刚写好的新登录态盖掉。
     * 拿到锁之后必须重新读一次盘：前一笔若已经换好，后来者直接用新 access token。
     */
    private val refreshMutex = Mutex()

    override suspend fun bearerOrNull(): String? = withContext(Dispatchers.IO) {
        // 连可换新的那枚都没有 ⇒ 真的没登录。不做"拿旧 access 顶一下"：那条会把
        // "登过但票据没了"与"从没登过"读成同一件事。
        if (store.getString(KEY_REFRESH_TOKEN, "").orEmpty().isBlank()) return@withContext null
        usableCachedAccess()?.let { return@withContext it }

        refreshMutex.withLock {
            // 排队期间前一笔可能已经把新票据写回来了，这里必须重读，不能拿锁外那份旧值再换一次。
            if (store.getString(KEY_REFRESH_TOKEN, "").orEmpty().isBlank()) return@withLock null
            usableCachedAccess()?.let { return@withLock it }

            val refresh = store.getString(KEY_REFRESH_TOKEN, "").orEmpty()
            val previousAccess = store.getString(KEY_ACCESS_TOKEN, "").orEmpty()
            val previousExpiry = expiryAt()
            runCatching { postForTokens(refreshForm(refresh), keepRefreshOnAbsent = refresh) }
                .onSuccess(::persist)
                .onFailure { failure ->
                    // 提前 5 分钟刷新只是"尽量别卡到过期那一刻"，并不代表旧票据已经不能用。
                    // 数据网络抖动、代理节点被风控、站方 5xx 都会让这一笔失败；此时把仍然
                    // 有效的旧 access token 丢掉并判成登录过期，是用户看到的"经常过期"。
                    if (previousAccess.isNotBlank() && clock() < previousExpiry) {
                        return@onFailure
                    }
                    if (failure.isDefinitiveAuthFailure() || failure is NotWiredOnDesktop) {
                        _authState.value = PixivAuthState.NeedsRelogin(failure.readableReason())
                    }
                }
                .getOrNull()?.accessToken
                ?: previousAccess.takeIf { it.isNotBlank() && clock() < previousExpiry }
        }
    }

    /** 仍可直接使用的盘上票据；进入提前刷新窗口时返回 null，让调用方去换票。 */
    private fun usableCachedAccess(): String? {
        val access = store.getString(KEY_ACCESS_TOKEN, "").orEmpty()
        if (access.isBlank()) return null
        return access.takeIf { clock() < expiryAt() - EARLY_REFRESH_MS }
    }

    private var attempt: PixivLoginChallenge? = null

    /**
     * 起一次登录。
     *
     * 没配 client 凭据时回 **null**（而不是打开一张注定换不到 token 的登录页）——
     * 那一页看起来一切正常、用户输完密码之后才失败，就是方案 §五 要防的假开关。
     */
    override fun beginLogin(): PixivLoginChallenge? {
        if (!isClientConfigured) return null
        val verifier = PixivPkce.newVerifier()
        val state = PixivPkce.newState()
        return PixivLoginChallenge(
            // 登录页认的是站方那个客户端标签（`client=pixiv-android`），不再是我们的 client_id ——
            // 那一对只在换 token 那一跳用（见 [exchangeCode]）。
            authorizeUrl = PixivPkce.authorizeUrl(PixivPkce.challengeOf(verifier), state),
            codeVerifier = verifier,
            state = state,
        ).also { attempt = it }
    }

    /**
     * 回跳地址 → 票据。
     *
     * ⚠️ **核 `state` 一定在出网之前**：回跳是外部浏览器交回来的东西，拿一枚别人塞进来的码去
     * POST 我们自己的 `client_secret` 就是替别人跑一次登录。这条顺序只有在这颗里才测得出来
     * （判据：`engine = null` 时喂一枚 state 不符的地址，失败消息里必须有 state 而不能有"没接出网口"）。
     *
     * **失败不动已有那份凭据**（口径同 `GelbooruCredentialState.signIn`：一次失败不许洗掉本来正确的凭据）。
     * 但**必须有话**：P6 之后登录走外部浏览器，本 app 里再没有那一屏 WebView 的红字，
     * 失败若只回一个 `Result` 而没人接，屏上的表现就与"用户根本没点过登录"逐像素相同 ——
     * 所以没有登录态可保时把这一档推成 [PixivAuthState.LoginRejected]（红线：降级不许静默）。
     */
    override suspend fun completeLogin(redirectUrl: String): Result<PixivAuthState> =
        attemptLogin(redirectUrl).onFailure { failure ->
            if (_authState.value !is PixivAuthState.SignedIn) {
                _authState.value = PixivAuthState.LoginRejected(failure.readableReason())
            }
        }

    private suspend fun attemptLogin(redirectUrl: String): Result<PixivAuthState> {
        val current = attempt
            ?: return Result.failure(IOException("没有进行中的登录，请重新点一次头像"))
        val code = runCatching { PixivPkce.codeFrom(redirectUrl, current.state) }
            .getOrElse { return Result.failure(it) }
        return exchangeCode(code, current.codeVerifier).also { if (it.isSuccess) attempt = null }
    }

    /** 授权码换票据。 */
    private suspend fun exchangeCode(code: String, codeVerifier: String): Result<PixivAuthState> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pair = client.require()
                postForTokens(
                    fields = mapOf(
                        "grant_type" to "authorization_code",
                        "client_id" to pair.first,
                        "client_secret" to pair.second,
                        "code" to code,
                        "code_verifier" to codeVerifier,
                        "redirect_uri" to PixivPkce.REDIRECT_URI,
                        // 站方要求这一枚：不带它换回来的票据会缺"已同意条款"那一档，
                        // 后续请求按未同意处理（pixez 那一条首登与刷新都带，我们照齐）。
                        "include_policy" to "true",
                    ),
                    // 首登没有"上一枚 refresh_token"可留：站方不给就是失败（P1 那条判据）。
                    keepRefreshOnAbsent = null,
                )
            }.map { tokens ->
                persist(tokens)
                PixivAuthState.SignedIn(userId = tokens.userId, userName = tokens.userName)
            }
        }

    /**
     * 粘贴一条东西来登录（2026-10-17 第四轮，用户点了 B 之后补的兜底路）。
     *
     * 浏览器那一路偶尔会断在半路（系统没把回跳交回来、或用户是在另一台机器上登的），
     * 这一条是所有同类客户端的公共出口，认三种形状 —— **按信息量从多到少**试：
     *
     * 1. **token JSON**（`{"access_token":…,"refresh_token":…}`）：整套票据，直接落盘。
     * 2. **回跳链接**（带 `code=` 的那一串）：等于"手动把回跳交回来"，走的是与浏览器
     *    同一条 `completeLogin`。⚠️ 它必须有进行中的会话 —— 那一枚码要用发起时的
     *    `code_verifier` 才能换，而 verifier 只活在内存里。没有会话就如实说这一句，
     *    不假装能登（假开关）。
     * 3. **光秃秃的 refresh token**：拿它直接换一套新票据（`grant_type=refresh_token`）。
     *
     * 失败**不动已有凭据**（口径同 [completeLogin]），并且要有话 —— 否则屏上表现与"没点过"相同。
     */
    override suspend fun signInWithPasted(text: String): Result<PixivAuthState> {
        val raw = text.trim()
        if (raw.isBlank()) return Result.failure(IOException("粘贴的内容是空的"))
        // ① 回跳链接：交给与浏览器同一条路（它自己会核 state、会拒绝"没有进行中的登录"）。
        if (raw.contains("code=") && raw.contains("://")) {
            return completeLogin(raw)
        }
        return withContext(Dispatchers.IO) {
            // ② token JSON：`tokensOf` 的严格性就是这里的判据（缺任一枚都抛，只拿到半套不算登录）。
            val asBundle = runCatching { PixivPkce.tokensOf(raw) }.getOrNull()
            if (asBundle != null) {
                persist(asBundle)
                return@withContext Result.success(PixivAuthState.SignedIn(asBundle.userId, asBundle.userName))
            }
            // ③ 光秃秃的 refresh token。
            runCatching { postForTokens(refreshForm(raw), keepRefreshOnAbsent = raw) }
                .map { tokens ->
                    persist(tokens)
                    PixivAuthState.SignedIn(userId = tokens.userId, userName = tokens.userName)
                }
                .onFailure { failure ->
                    if (_authState.value !is PixivAuthState.SignedIn) {
                        _authState.value = PixivAuthState.LoginRejected(failure.readableReason())
                    }
                }
        }
    }

    /** 注销：票据与身份一起删，一个都不留（留半套下次就会长成"半登录"）。 */
    override fun signOut() {
        store.clear()
        _authState.value = PixivAuthState.Anonymous
    }

    private fun persist(tokens: PixivTokens) {
        store.putAll(
            mapOf(
                KEY_ACCESS_TOKEN to tokens.accessToken,
                KEY_REFRESH_TOKEN to tokens.refreshToken,
                // 站方给的是**相对值**，落盘换成绝对时刻：进程被杀、手机睡一整晚之后再读，
                // "还剩多久"这种问法已经没有意义了。
                KEY_EXPIRES_AT to clock().plus(tokens.expiresInSeconds * 1_000L),
                KEY_USER_ID to tokens.userId.orEmpty(),
                KEY_USER_NAME to tokens.userName.orEmpty(),
            ),
        )
        _authState.value = PixivAuthState.SignedIn(userId = tokens.userId, userName = tokens.userName)
    }

    private fun expiryAt(): Long = store.getLong(KEY_EXPIRES_AT, 0L)

    /** 冷启动读回：**两枚都没有就是没登录**，不从"有过 access_token 的痕迹"推断出身份。 */
    private fun restore(): PixivAuthState {
        val refresh = store.getString(KEY_REFRESH_TOKEN, "").orEmpty()
        val access = store.getString(KEY_ACCESS_TOKEN, "").orEmpty()
        if (refresh.isBlank() && access.isBlank()) return PixivAuthState.Anonymous
        return PixivAuthState.SignedIn(
            userId = store.getString(KEY_USER_ID, "").orEmpty().ifBlank { null },
            userName = store.getString(KEY_USER_NAME, "").orEmpty().ifBlank { null },
        )
    }

    private fun refreshForm(refresh: String): Map<String, String> {
        val pair = client.require()
        return mapOf(
            "grant_type" to "refresh_token",
            "client_id" to pair.first,
            "client_secret" to pair.second,
            "refresh_token" to refresh,
            // 与首登那一跳同一枚（理由写在 [exchangeCode] 的注释上）：两处不一致的话，
            // 首登拿到的票据带同意标记、刷新之后又不带了 —— 症状是"用一阵子之后突然有功能不可用"。
            "include_policy" to "true",
        )
    }

    /**
     * POST `oauth.secure.pixiv.net/auth/token`（`/auth` 那一段不能省，理由见 [TOKEN_URL]）。
     *
     * ⚠️ 两条从这一族缺陷里长出来的规矩：
     *
     * 1. **失败消息只带状态码与答复片段，绝不带请求体** —— 那一体里就写着 `refresh_token` 与
     *    `client_secret` 本体。异常沿 `Result.failure` 上屏之后会变成副标题，泄露就是这么发生的。
     * 2. [keepRefreshOnAbsent] 只给**刷新**那一跳用：站方不换新的那枚时（合法行为）把手里这枚留着。
     *    首登传 null，所以答复缺 `refresh_token` 仍然是失败 —— 只有半套票据的账号
     *    会在几十分钟后变成"界面说已登录、请求全 401"。
     */
    private fun postForTokens(fields: Map<String, String>, keepRefreshOnAbsent: String?): PixivTokens {
        val client = engine?.invoke()
            ?: throw NotWiredOnDesktop("这一侧没接 pixiv 的出网口，换不到票据")
        val body = FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()
        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(body)
            .header("Accept", "application/json")
            // token 端点和 app-api 一样会按客户端身份筛请求。2026-10-21 现量：
            // 只带 Accept 时同一份 refresh 请求回 403 HTML；补上 User-Agent 后回
            // 400 `invalid_grant`（说明端点认出了请求，只是票据假）。这几枚必须和
            // PixivAppClient 保持一致，否则首登与后台刷新会同时表现成"换票 403"。
            .header("App-OS", "android")
            .header("App-OS-Version", APP_OS_VERSION)
            .header("App-Version", APP_VERSION)
            .header("User-Agent", "PixivAndroidApp/$APP_VERSION (Android $APP_OS_VERSION)")
            .header("Accept-Language", PIXIV_LANGUAGE)
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
            .build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val reason = when (response.code) {
                    400 -> text.pixivTokenError() ?: "站方说这一枚 refresh token 不能用了"
                    401 -> "站方不认这一枚 refresh token 了"
                    403 -> "站方把换票请求挡下了（可能是网络出口或风控），换网络或稍后再试"
                    408, 429 -> "站方这一会儿太忙或限流了，稍后再试"
                    in 500..599 -> "站方暂时出错了，稍后再试"
                    else -> "站方回了 HTTP ${response.code}"
                }
                throw IOException("pixiv 换 token 失败（${response.code}）：$reason")
            }
            PixivPkce.tokensOf(text, keepRefreshOnAbsent = keepRefreshOnAbsent)
        }
    }

    companion object {
        /** 落盘键名（**值就是键名**，改一个字符 = 老凭据读不回来，用户被要求重新登录一次）。 */
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at_ms"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"

        /**
         * 这一次导航是不是 pixiv 登录的回跳 —— **全仓只此一处**认这两条地址。
         *
         * 值只在 `PixivPkce` 一处（[PixivPkce.REDIRECT_URI] 与 [PixivPkce.DEEP_LINK_PREFIX]），
         * 判定也在这**一颗静态**里：界面或 Activity 各自拼一份 `pixiv://` 就是第二把尺子
         * （改了常量只坏一半，表现是"登录页照常打开、回跳永远不算回跳"）。
         * 做成静态而不是契约方法，是因为唯一的调用点在 `MainActivity.handleEntryIntent` ——
         * 那是冷启动主线程，不该为了问一句话去实例化账号那颗件（它会读一次 SharedPreferences）。
         *
         * 两种形态都要认（2026-10-17 第四轮）：站方登完可能直接把 `pixiv://account/login?code=…`
         * 交给系统，也可能先落到它自己的 https 回跳再由浏览器转一手 —— 少认一条的表现是
         * 「浏览器里登录成功、回到 app 毫无反应」，而屏上一句报错都没有。
         */
        fun isLoginCallbackUrl(url: String): Boolean =
            url.startsWith(PixivPkce.REDIRECT_URI) || url.startsWith(PixivPkce.DEEP_LINK_PREFIX)

        /**
         * 换 token 那一颗的地址。
         *
         * ⚠️ **路径里必须有 `/auth`**（2026-10-17 实测）：`oauth.secure.pixiv.net/token` 回
         * **HTTP 404**（`{"has_error":true,…,"message":"404 Not Found"}`），而
         * `oauth.secure.pixiv.net/auth/token` 对同一份请求体回 400 `invalid_grant`
         * （`code 1508 Invalid refresh token`）—— 后者是"端点认得出这个形状、只是票据假"。
         * 三家同栈参考件（pixez `OAuthClient.kt`、gallery-dl `pixiv.py`、本仓植入的
         * `PixivOAuthCredentials`）用的都是带 `/auth` 那条。
         *
         * 少写这一段的表现最阴：登录页照常开、浏览器里也登成功，回来换票恒 404，
         * 屏上只会念一句"pixiv 换 token 回了 404"。判据 `刷新成功时两枚票据与身份一起换…` 钉着它。
         */
        private const val TOKEN_URL = "https://oauth.secure.pixiv.net/auth/token"

        /** 与 `PixivAppClient` 同一套客户端标识；只用于让站方按 App 请求处理。 */
        private const val APP_VERSION = "6.0.0"
        private const val APP_OS_VERSION = "14"
        private const val PIXIV_LANGUAGE = "zh-cn"

        /**
         * 提前 5 分钟就换新（留一段"取回来之后还要用它打几笔"的余量）。
         * 卡到 0 秒的写法会让请求正好落在过期的那一刻，症状是偶发一片 401。
         */
        private const val EARLY_REFRESH_MS = 5 * 60 * 1_000L
    }
}

/** [Throwable.message] 的可用版：只截一段、空消息给一句实话，并且**不许是 null**（NeedsRelogin 要念得出字）。 */
private fun Throwable.readableReason(): String {
    val line = (this as? IOException)?.message ?: this.message ?: ""
    return line.trim().take(160).ifBlank { "站方没说什么（${this.javaClass.simpleName}）" }
}

/**
 * 这次换票失败是否**足以判定 refresh token 已死**。
 *
 * token 端点把真正的票据失效放在 400 `invalid_grant` / 401 里；403 是风控或出口被挡，
 * 408 / 429 / 5xx 与连接异常是暂时性失败。把后者也读成"登录过期"，就会让用户在家里
 * 换个 Wi-Fi、代理节点抖一下时反复被要求重登 —— 而重登本身又可能撞同一个 403。
 */
private fun Throwable.isDefinitiveAuthFailure(): Boolean {
    val message = this.message.orEmpty()
    if (message.contains("invalid_grant", ignoreCase = true) ||
        message.contains("Invalid refresh token", ignoreCase = true)
    ) {
        return true
    }
    val status = TOKEN_STATUS_REGEX.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
    return status == 400 || status == 401
}

private val TOKEN_STATUS_REGEX = Regex("""\b(\d{3})\b""")

/**
 * 从 token 端点的 400 答复里取一句**能上屏的**原因。
 *
 * 只认站方的错误形状，不把整个正文交出去：答复可能很长，而其中绝不能包含请求体里的票据。
 * 常见的 `invalid_grant` / `Invalid refresh token` 读成同一句人话；其余 400 原样截一小段。
 */
private fun String.pixivTokenError(): String? {
    val root = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(this)
            .let { it as? kotlinx.serialization.json.JsonObject }
    }.getOrNull() ?: return take(120).takeIf { it.isNotBlank() }
    val system = ((root["errors"] as? kotlinx.serialization.json.JsonObject)
        ?.get("system") as? kotlinx.serialization.json.JsonObject)
    val code = (system?.get("code") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
    val message = (system?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.content
    if (code == 1508 || message?.contains("refresh token", ignoreCase = true) == true) {
        return "站方说这一枚 refresh token 不能用了"
    }
    return message?.take(120)?.takeIf { it.isNotBlank() }
}

/**
 * 那一对 client 凭据的**取用面**（真件 [PixivOAuthCredentials]，单测递自己那一份）。
 *
 * 做成接口而不是直接引真件，是因为有一条判据必须测得到：核 `state` 发生在**出网之前**。
 * 真件今天没填 ⇒ [PixivCredentials.beginLogin] 恒回 null ⇒ 连一次登录会话都起不来 ⇒
 * 那条判据压根跑不到。
 */
interface PixivClientCredentials {

    val isConfigured: Boolean

    /** 取值只走这一条：没配就抛一句说得清的，不交空串出去让请求带着空 `client_id` 打网。 */
    fun require(): Pair<String, String>
}

/**
 * pixiv 那一对**客户端**凭据（OAuth 应用的身份，不是用户密码）。
 *
 * 口径（方案 §三 红线一）：这一对是 pixiv 官方客户端里公开的那一类常量，**不是我们的秘密**；
 * 但"不是秘密"不等于可以外流 —— 它照样不许进日志与报错消息。
 *
 * ## 这两枚是哪来的（2026-10-17 第四轮）
 *
 * 用户点名「参考 https://github.com/137458/pixez-miuix 他是怎么做登录的」，这两枚逐字取自那边
 * `shared/src/commonMain/kotlin/com/perol/pixez/shared/network/OAuthClient.kt` 的常量组
 * （`CLIENT_ID` / `CLIENT_SECRET`）—— 也就是站方自己那个 Android 客户端装进 APK 里的那一对，
 * 任何一台装了 pixiv 的手机上都能反出来。它们决定的是"这一对凭据配不配"，与用户是谁无关。
 *
 * ⚠️ 想换成自己登记的应用：改这两行即可，`authorizeUrl` 里的 `client=pixiv-android` 那一枚
 * 也要一起改（那一枚是登录页认的标签，不是 client_id，两处不同源）。
 * 自己登记那条路要求应用过审，今天拿不到，所以默认值就是上面那一对。
 */
object PixivOAuthCredentials : PixivClientCredentials {

    const val CLIENT_ID: String = "MOBrBDS8blbauoSck0ZfDbtuzpyT"

    const val CLIENT_SECRET: String = "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj"

    override val isConfigured: Boolean get() = CLIENT_ID.isNotBlank() && CLIENT_SECRET.isNotBlank()

    override fun require(): Pair<String, String> {
        if (!isConfigured) {
            throw IOException("这台机器还没填 pixiv 的客户端凭据（client_id / client_secret）")
        }
        return CLIENT_ID to CLIENT_SECRET
    }
}
