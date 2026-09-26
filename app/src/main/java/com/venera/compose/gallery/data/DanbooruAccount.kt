package com.venera.compose.gallery.data

import android.content.Context
import android.util.Base64
import com.venera.compose.data.network.VeneraNetworkClient
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * Danbooru 账号（用户名 + API Key）。
 *
 * ⚠️ **先纠正一条曾经写反的结论**（2026-09-26 复核）：登录**不是"看到成人图"的钥匙**。
 * 这里从前写着"成人分级（`rating:e`）匿名看不到、登录后同一批条目照给 URL"，
 * 那是把 `loli` 那批被抹的成因错挂在了分级上。两条对照实测：
 * - `cat rating:e` 匿名取回 **5/5 都带图** —— 成人分级根本不被挡；
 * - `loli`（不论配什么分级，连 `loli rating:s` 也是）匿名取回 **0/5 带图**，
 *   站方是把四个 URL 键**整片删掉**（顶层键数 46 → 42），`tag_string` 照给。
 * 挡图的是**标签**，出处与完整解释见 [DanbooruClient.CENSORED_TAGS]：
 * Member 与未登录一律封，Gold 起才开 —— 所以**登录也解决不了**。
 *
 * 这个账号因此**只买到一件事**：一次能搜几枚标签（[DanbooruClient.tagsPerSearch]，
 * 站方 `help:users` 那张表，实测第 3 枚直接 422 `PostQuery::TagLimitError`）——
 * 匿名 / Member = **2**，Gold = **6**，Platinum 及以上**不限**。
 * ⚠️ 免费注册只到 Member —— **登录本身并不能把 2 枚放宽**，能放宽的是付过费的 Gold。
 * 这条必须如实告诉用户（设置页文案写明了），否则"登录了怎么还是 2 枚"会变成一次没必要的投诉。
 *
 * 还有一点会踩空：**等级是会变的**。站方给"注册时走了 VPN / 代理"的账号发
 * Restricted(10)（wiki `help:users` 原话：*A user is restricted if they signup using a VPN or
 * proxy*），验证邮箱后才升到 Member —— 而这里记下的是**登录那一刻**的值。
 * 所以有 [refresh] 负责校准，别让设置页一直显示旧的等级。
 *
 * 凭据只落在本机 SharedPreferences，注销即删；不写日志、不进任何提示文案。
 * 鉴权走站方文档给的两条正规路之一：HTTP Basic（`base64(用户名:API Key)`）。
 *
 * 校验用 `/profile.json`（实测：无凭据 200 且 `id:null`、`name:"Anonymous"`、`level:0`；
 * 错误凭据 **401**；正确凭据回真实 `id`/`name`/`level`）。**只认这一条**
 * —— 不拿"随便发个 posts 请求看通不通"当校验，那分不清"凭据错"和"接口抽风"。
 */
class DanbooruAccount private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 当前登录身份；null = 匿名。
     *
     * 是 StateFlow 而不是普通字段：标签预算（[DanbooruClient.tagsPerSearch]）与
     * "受管制标签看不到"那句读数（门槛 Gold）都要跟着它变 —— 登录完或校准完等级、
     * 设置页一退，搜索页那边得立刻跟着变，而不是等重启。
     */
    private val _identity = MutableStateFlow(restoreIdentity())

    val identity: StateFlow<DanbooruIdentity?> = _identity.asStateFlow()

    /** 用户名（离线可用，登录框要用它回填）。没登录时空串。 */
    val loginName: String get() = prefs.getString(KEY_LOGIN, "").orEmpty()

    /** 已登录（有凭据）就是 true。**是否真的还有效**以 [identity] 为准（杀进程时那边是缓存值）。 */
    val isSignedIn: Boolean get() = authHeader() != null

    /** 这一站一次能搜几枚标签；判据与出处见 [DanbooruClient.tagsPerSearch]。 */
    fun tagsPerSearch(): Int? = DanbooruClient.tagsPerSearch(_identity.value?.level)

    /**
     * 请求要带的鉴权头；没登录时 null。
     *
     * 每次现算而不缓存：注销之后在途的请求不该还带着旧凭据（缓存过就做不到）。
     * 代价是两次 SharedPreferences 读取 + 一次 base64，相对一次网络往返可以忽略。
     */
    fun authHeader(): String? {
        val login = prefs.getString(KEY_LOGIN, "").orEmpty()
        val key = prefs.getString(KEY_API_KEY, "").orEmpty()
        if (login.isBlank() || key.isBlank()) return null
        val token = Base64.encodeToString("$login:$key".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $token"
    }

    /**
     * 用一组凭据登录（不落任何状态，除非站方点头）。
     *
     * 校验请求**不经过** [DanbooruClient]：那边会去读 [authHeader]，
     * 而此刻要验的正是"还没存下来的这一组" —— 走那条路就成了"拿旧的验新的"。
     */
    suspend fun signIn(login: String, apiKey: String): Result<DanbooruIdentity> =
        withContext(Dispatchers.IO) {
            val user = login.trim()
            val key = apiKey.trim()
            runCatching {
                if (user.isEmpty() || key.isEmpty()) throw IOException("用户名与 API Key 都不能为空")
                verify(user, key)
            }.onSuccess { identity -> store(identity, user, key) }
        }

    /**
     * 用**存着的那组凭据**再问一次站方"我是谁、什么等级"，把身份校准过来。
     *
     * 为什么需要：等级记的是**登录那一刻**的值（[restoreIdentity] 冷启动也只读那份存档），
     * 而站方的等级会变 —— 最日常的一条就是"验证邮箱后从 Restricted(10) 升到 Member"。
     * 不复查的话，设置页那句"（等级）"会永远是登录时的旧值，用户会觉得"我都验证过了怎么没变"。
     *
     * 返回 null = 压根没登录（没有凭据，无从复查）。**失败不注销** ——
     * 一次网络抽风不该把用户登出；凭据留着，下次再校。身份没更新时屏上显示的仍是"上次问到的"，
     * 那是当时为真的事实，不是编的。
     */
    suspend fun refresh(): Result<DanbooruIdentity>? = withContext(Dispatchers.IO) {
        val login = prefs.getString(KEY_LOGIN, "").orEmpty()
        val key = prefs.getString(KEY_API_KEY, "").orEmpty()
        if (login.isBlank() || key.isBlank()) return@withContext null
        runCatching { verify(login, key) }.onSuccess { identity -> store(identity, login, key) }
    }

    /**
     * 拿一组凭据问 `/profile.json`。**不落任何状态** —— 落库由 [store] 一处负责，
     * 这样"用哪组凭据问"与"把什么存下来"不会各写一遍。
     */
    private fun verify(user: String, key: String): DanbooruIdentity {
        val token = Base64.encodeToString(
            "$user:$key".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )
        val request = Request.Builder()
            .url(PROFILE_URL)
            .header("Accept", "application/json")
            .header("User-Agent", DanbooruClient.API_USER_AGENT)
            .header("Authorization", "Basic $token")
            .build()
        val client = VeneraNetworkClient.getInstance(appContext).okHttpClient
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when {
                // 401 是站方给"用户名/API Key 不对"的唯一答案，别与网络故障混为一谈。
                response.code == 401 ->
                    throw IOException("用户名或 API Key 不对（Danbooru 回 401）")
                response.code == 403 ->
                    throw IOException("Danbooru 回了 403：这一组凭据被拒或触发了风控")
                !response.isSuccessful -> throw IOException("Danbooru 返回 ${response.code}")
                else -> {
                    val profile = json.decodeFromString<DanbooruProfileDto>(body)
                    // `id` 为空 = 站方把我们当匿名（凭据没生效）。宁可报"没登上"，
                    // 也不要存一条假的"已登录"——那会让预算与"看不到"的读数全跟着错。
                    val id = profile.id
                        ?: throw IOException("Danbooru 没认这组凭据（回的是匿名身份）")
                    DanbooruIdentity(
                        id = id,
                        name = profile.name.ifBlank { user },
                        level = profile.level,
                    )
                }
            }
        }
    }

    /** 落库 + 推流，两处调用（[signIn] / [refresh]）共用同一条口径。 */
    private fun store(identity: DanbooruIdentity, login: String, apiKey: String) {
        prefs.edit()
            .putString(KEY_LOGIN, login)
            .putString(KEY_API_KEY, apiKey)
            .putLong(KEY_ID, identity.id)
            .putString(KEY_NAME, identity.name)
            .putInt(KEY_LEVEL, identity.level)
            .apply()
        _identity.value = identity
    }

    /** 注销：把凭据与身份一起删掉（一个都不留，免得下次"半登录"）。 */
    fun signOut() {
        prefs.edit().clear().apply()
        _identity.value = null
    }

    /** 冷启动时把上次的身份读回来。**没有凭据就没有身份**，不做"有名字就算登录"这种推断。 */
    private fun restoreIdentity(): DanbooruIdentity? {
        val login = prefs.getString(KEY_LOGIN, "").orEmpty()
        val key = prefs.getString(KEY_API_KEY, "").orEmpty()
        if (login.isBlank() || key.isBlank()) return null
        val id = prefs.getLong(KEY_ID, 0L)
        val name = prefs.getString(KEY_NAME, "").orEmpty()
        return DanbooruIdentity(
            id = id,
            name = name.ifBlank { login },
            level = prefs.getInt(KEY_LEVEL, 0),
        )
    }

    companion object {
        private const val PREFS_NAME = "venera_danbooru_account"
        private const val KEY_LOGIN = "login"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_ID = "user_id"
        private const val KEY_NAME = "user_name"
        private const val KEY_LEVEL = "user_level"

        /** 站方的身份端点：既当"凭据对不对"的校验，也当"你是谁、什么等级"的来源。 */
        private const val PROFILE_URL = "https://danbooru.donmai.us/profile.json"

        /** 设置页那枚「去生成 API Key」要打开的页面（站方文档给的就是这一页）。 */
        const val API_KEY_PAGE = "https://danbooru.donmai.us/profile"

        private val json = Json { ignoreUnknownKeys = true }

        @Volatile
        private var INSTANCE: DanbooruAccount? = null

        fun getInstance(context: Context): DanbooruAccount = INSTANCE ?: synchronized(this) {
            INSTANCE ?: DanbooruAccount(context.applicationContext).also { INSTANCE = it }
        }
    }
}

/** 站方确认过的身份。[level] 是站方的用户等级（0 匿名 / 20 Member / 30 Gold / 31 Platinum+）。 */
data class DanbooruIdentity(val id: Long, val name: String, val level: Int)

/**
 * `/profile.json` 的应答。匿名时这一整份也在（`id:null`、`name:"Anonymous"`、`level:0`），
 * 所以"有没有登录"的判据是 **`id` 是不是 null**，不是"请求成不成功"。
 */
@Serializable
internal data class DanbooruProfileDto(
    val id: Long? = null,
    val name: String = "",
    val level: Int = 0,
)
