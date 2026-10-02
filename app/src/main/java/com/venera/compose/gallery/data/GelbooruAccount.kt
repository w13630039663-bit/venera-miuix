package com.venera.compose.gallery.data

import android.content.Context
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.data.platform.android.AndroidKeyValueStore
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Gelbooru 账号（**User ID + API Key**）。
 *
 * ## 这两样与 Danbooru 那套不是一回事
 *
 * | | Danbooru（旧） | Gelbooru（本类） |
 * |---|---|---|
 * | 凭据形态 | 用户名 + API Key | **User ID（数字）+ API Key** |
 * | 传输方式 | `Authorization: Basic base64(用户名:Key)` | **两个 query 参数** `&api_key=&user_id=` |
 * | 校验端点 | `/profile.json`（回真实 id/name/level） | **没有**这类端点，只能拿一次最小请求当探针 |
 * | 失败答案 | 401，body 里有信息 | **401，body 为空** |
 *
 * ## 为什么"登录"在这一站是**必需**而不是加分
 *
 * DAPI **匿名一律 401**（2026-09-26 实测：`s=post` 与 `s=tag` 不带凭据都回 401）。
 * 所以没配这个账号时，Gelbooru 在画廊里**一张图都取不到** —— 不是"少几档权限"。
 * UI 必须直说这件事（[GalleryScreen] 的页尾与搜索前置提示都按这条写），
 * 否则它会静默长成"这一站今天没图"，那是最难查的一类假读数。
 *
 * ## 校验为什么是"发一笔 limit=1 的请求"
 *
 * 官方 DAPI 里**没有**"我是谁"这类端点（wiki `howto:api` 只有 post / tag / user / comment
 * 四条列表端点），所以拿不到 Danbooru 那种 `/profile.json`。
 * 退而取一笔**最小**的 posts 请求（`limit=1`，只要 200 就说明两个参数都被接受）：
 * - 401 → 这组凭据没被接受（实测"错 key"与"错 user_id"给的是**同一个 401**，
 *   body 为空 → 本类判不出是哪个错，如实把两种可能都写进提示，不猜）；
 * - 200 → 两个参数都对上了。
 *
 * ⚠️ **不拿"随便发个 posts 请求看有没有数据"当校验**：那分不清"凭据错"和"这个查询本身没结果"。
 * 所以探针的查询条件必须是**保证非空**的（用空 `tags`，即"全部"）。
 *
 * ## 与旧实现的一处刻意保留
 *
 * 凭据只落在本机 SharedPreferences，注销即删；**不写日志、不进任何提示文案**
 * （提示里只说"凭据没被接受"，从不回显 key 或 id）。
 */
class GelbooruAccount private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val prefs = AndroidKeyValueStore(appContext, PREFS_NAME)

    /**
     * 当前登录身份；null = 没配。
     *
     * 是 StateFlow 而不是普通字段：设置页配好或注销之后，画廊那边要**立刻**跟着变
     * （页尾读数、搜索前置提示都挂在"有没有账号"上），而不是等重启。
     */
    private val _identity = MutableStateFlow(restoreIdentity())

    val identity: StateFlow<GelbooruIdentity?> = _identity.asStateFlow()

    /** User ID（离线可用，登录框要用它回填）。没配时空串。 */
    val loginUserId: String get() = prefs.getString(KEY_USER_ID, "").orEmpty()

    /** 已配置（两样都有）就是 true。 */
    val isConfigured: Boolean get() = apiKey.isNotBlank() && userId.isNotBlank()

    /**
     * 当前 API Key；没配时空串。
     *
     * ⚠️ 只给 [GelbooruClient] 拼 query 用，**不要**把它摆到任何界面或日志上。
     * 不是 private 是因为客户端在另一个类里，而这两个值要分别拼进 URL。
     */
    val apiKey: String get() = prefs.getString(KEY_API_KEY, "").orEmpty()

    /** 当前 User ID；没配时空串。 */
    val userId: String get() = prefs.getString(KEY_USER_ID, "").orEmpty()

    /**
     * 用一组凭据登录（**不落任何状态，除非站方点头**）。
     *
     * 校验请求**不经过** [GelbooruClient]：那边会去读 [apiKey] / [userId]，
     * 而此刻要验的正是"还没存下来的这一组" —— 走那条路就成了"拿旧的验新的"。
     */
    suspend fun signIn(userId: String, apiKey: String): Result<GelbooruIdentity> =
        withContext(Dispatchers.IO) {
            val uid = userId.trim()
            val key = apiKey.trim()
            runCatching {
                if (uid.isEmpty() || key.isEmpty()) throw IOException("User ID 与 API Key 都不能为空")
                if (uid.toLongOrNull() == null) {
                    throw IOException("User ID 应该是纯数字（在 Gelbooru 账号页能看到）")
                }
                verify(uid, key)
            }.onSuccess { identity -> store(identity, uid, key) }
        }

    /**
     * 拿**存着的那组凭据**再问一次站方"还认不认"，把身份校准过来。
     *
     * 返回 null = 压根没配（没有凭据，无从复查）。**失败不注销** ——
     * 一次网络抽风不该把用户登出；凭据留着，下次再校。
     */
    suspend fun refresh(): Result<GelbooruIdentity>? = withContext(Dispatchers.IO) {
        val uid = prefs.getString(KEY_USER_ID, "").orEmpty()
        val key = prefs.getString(KEY_API_KEY, "").orEmpty()
        if (uid.isBlank() || key.isBlank()) return@withContext null
        runCatching { verify(uid, key) }.onSuccess { identity -> store(identity, uid, key) }
    }

    /**
     * 拿一组凭据打一次**最小的** posts 请求（`limit=1`），看 401 有没有消失。
     *
     * 判据只有 401 一条：实测三种错法（错 key / 错 user_id / 不带凭据）给的都是
     * **401 且 body 为空**，所以这里**分不出是哪一半错** —— 提示里如实写两种可能，
     * 不编一句"Key 不对"（那可能是冤枉的）。
     */
    private fun verify(uid: String, key: String): GelbooruIdentity {
        val url = "$PROBE_URL&api_key=${enc(key)}&user_id=${enc(uid)}"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", GelbooruClient.API_USER_AGENT)
            .build()
        val client = VeneraNetworkClient.getInstance(appContext).okHttpClient
        return client.newCall(request).execute().use { response ->
            when {
                response.code == 401 ->
                    throw IOException("Gelbooru 没接受这组凭据（API Key 与 User ID 要对得上，且都应来自同一账号）")
                response.code == 403 -> throw IOException("Gelbooru 回了 403：这一组凭据被拒或触发了风控")
                !response.isSuccessful -> throw IOException("Gelbooru 返回 ${response.code}")
                else -> {
                    // 200 就够了 —— 站方没有"我是谁"端点，这是能拿到的最强信号。
                    // ⚠️ 不解析 body：探针只回答"凭据通不通"，别把它的内容当身份写下来。
                    GelbooruIdentity(userId = uid)
                }
            }
        }
    }

    /** 落库 + 推流，两处调用（[signIn] / [refresh]）共用同一条口径。 */
    private fun store(identity: GelbooruIdentity, uid: String, key: String) {
        // 一次写两条：只有一个半边凭据的状态在下一句就会被判成"没配"
        prefs.putAll(
            mapOf(
                KEY_USER_ID to uid,
                KEY_API_KEY to key,
            ),
        )
        _identity.value = identity
    }

    /** 注销：把凭据与身份一起删掉（一个都不留，免得下次"半配置"）。 */
    fun signOut() {
        prefs.clear()
        _identity.value = null
    }

    /** 冷启动时把上次的身份读回来。**两样缺一就没有身份**，不做"有 ID 就算配好"这种推断。 */
    private fun restoreIdentity(): GelbooruIdentity? {
        val uid = prefs.getString(KEY_USER_ID, "").orEmpty()
        val key = prefs.getString(KEY_API_KEY, "").orEmpty()
        if (uid.isBlank() || key.isBlank()) return null
        return GelbooruIdentity(userId = uid)
    }

    companion object {
        private const val PREFS_NAME = PreferenceKeys.PREFS_GELBOORU_ACCOUNT
        private const val KEY_USER_ID = "user_id"
        private const val KEY_API_KEY = "api_key"

        /**
         * 校验探针：一笔**最小的** posts 请求。
         *
         * `limit=1` 是刻意的 —— 校验只需要"通不通"，不该顺手把 100 条拖下来。
         * 不带 `tags` 即"全部"，保证查询本身非空，这样 200 就一定是"凭据被接受"
         * 而不是"这个查询碰巧没结果"（后者也是 200）。
         */
        private val PROBE_URL =
            "https://${GallerySite.GELBOORU.apiHost}/index.php?page=dapi&s=post&q=index&json=1&limit=1"

        /**
         * 设置页那枚「去账号页拿 Key」要打开的页面。
         * 官方 wiki 写明 API Key 与 User ID **都在账号选项页**。
         */
        val ACCOUNT_PAGE = "https://${GallerySite.GELBOORU.apiHost}/index.php?page=account&s=options"

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

        @Volatile
        private var INSTANCE: GelbooruAccount? = null

        fun getInstance(context: Context): GelbooruAccount = INSTANCE ?: synchronized(this) {
            INSTANCE ?: GelbooruAccount(context.applicationContext).also { INSTANCE = it }
        }
    }
}

/**
 * 站方确认过的身份。
 *
 * ⚠️ 只有一个字段 —— 因为 Gelbooru 的 DAPI **不告诉你"我是谁、什么等级"**
 * （没有 `/profile` 类端点，实测）。所以这里存的是**我们发出去的那个 user_id**，
 * 不是站方回给我们的身份。不要给它补 `name` / `level` 这类字段去"对齐"旧实现：
 * 那些值在这一站**取不到**，编一个出来会让界面显示假信息。
 */
data class GelbooruIdentity(val userId: String)
