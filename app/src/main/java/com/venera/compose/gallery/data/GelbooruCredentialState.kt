package com.venera.compose.gallery.data

import com.venera.compose.data.platform.KeyValueStore
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Gelbooru 凭据的**判据层** —— 落哪、怎么算"配好了"、怎么验、401 那句话怎么写。
 *
 * ## 为什么要把它从 [GelbooruAccount] 里抽出来
 *
 * `GelbooruAccount` 住在 Android 面（它自己建 SharedPreferences，而 `feature/settings` 那几处
 * 登录 UI 直接拿它的 `identity` 流在渲染）⇒ 它整颗在 `desktop/build.gradle.kts` 的排除清单里。
 * 但"凭据通了没有"这件事**两端必须同一条判据**，否则桌面会写出第二种判断：
 * 桌面说"配好了"而下一轮取数照样 401，用户被两处口径来回打，症状读起来像"存了但没生效"。
 *
 * 所以按本仓 S1 那条手法（把 Context 工厂搬进同包扩展）把这颗**判据件**抽出来：
 * 两端各建一个 `AndroidKeyValueStore` / `JsonKeyValueStore` 交给它，判据与文案只有这一份。
 *
 * ## 三条判据（照 Android 侧原样搬，不许在桌面"顺手优化"）
 *
 * 1. **两样都非空才算配过**。只填一半在两端都是"没配" —— 少了那一条会让界面显示已登录、
 *    而请求带不上参数，长成"Gelbooru 今天一张图都没有"这种最难查的假读数。
 * 2. **站方点头才落盘**。`signIn` 先验后存：401/403 就**不动**已有那份存储。
 *    拿一次 401 去覆盖掉一组本来正确的凭据，是这个面板最坏的一种错。
 * 3. **401 那句话逐字共用**。[GelbooruClient.failure] 与本类 `verify` 各写了一份同样的话 ——
 *    而实测这一站的 401 body 为空，三种错法（错 key / 错 id / 不带凭据）**给的是同一个答案**，
 *    所以谁也判不出是哪一半错，提示只能把两种可能都写上。桌面那个 footer 引的就是 [ANONYMOUS_HINT]。
 *
 * ## 一条已拍板的红线
 *
 * **不许从浏览器 cookie 库里取凭据。** 本仓不提供那条路，也不接受用户从别处导入未声明来源的会话。
 *
 * @param store 落盘口。Android 侧传 `AndroidKeyValueStore`，桌面传 `JsonKeyValueStore`。
 * @param engine 出网口。这一站每笔请求都自带 UA 与两个 query 参数，不吃 `data.network` 的公共头。
 */
class GelbooruCredentialState(
    private val store: KeyValueStore,
    private val engine: (() -> okhttp3.OkHttpClient)? = null,
) : GelbooruCredentials {

    /**
     * 当前身份；null = 没配。
     *
     * 是 [StateFlow] 而不是普通字段（照 `GelbooruAccount` 的判据）：界面在渲染，
     * 存好或注销之后那屏要**立刻**跟着变，而不是等进程重启。
     */
    private val _identity = MutableStateFlow(restoreIdentity())

    val identity: StateFlow<GelbooruIdentity?> = _identity.asStateFlow()

    /** 已配置（两样都有）就是 true。 */
    val isConfigured: Boolean get() = apiKey.isNotBlank() && userId.isNotBlank()

    override val userId: String get() = store.getString(KEY_USER_ID, "").orEmpty()

    override val apiKey: String get() = store.getString(KEY_API_KEY, "").orEmpty()

    /**
     * 用一组凭据登录：**站方点头才落盘**。
     *
     * ⚠️ 校验请求**不经** [GelbooruClient]：那边会去读 [apiKey] / [userId]，
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
     * 拿**存着的那组**再问站方一次"还认不认"。
     *
     * 压根没配 ⇒ 回 null（无从复查，那不是失败）。**失败不注销** ——
     * 一次网络抽风不该把用户登出，凭据留着，下轮再校。
     */
    suspend fun refresh(): Result<GelbooruIdentity>? = withContext(Dispatchers.IO) {
        val uid = userId
        val key = apiKey
        if (uid.isBlank() || key.isBlank()) return@withContext null
        runCatching { verify(uid, key) }.onSuccess { identity -> store(identity, uid, key) }
    }

    /** 注销：凭据与身份一起删（一个都不留，免得下次"半配置"）。 */
    fun signOut() {
        store.clear()
        _identity.value = null
    }

    /** 落盘 + 推流，两处调用（[signIn] / [refresh]）共用一条口径。 */
    private fun store(identity: GelbooruIdentity, uid: String, key: String) {
        store.putAll(mapOf(KEY_USER_ID to uid, KEY_API_KEY to key))
        _identity.value = identity
    }

    /**
     * 拿一组凭据打一次**最小的** posts 请求（`limit=1`），看 401 有没有消失。
     *
     * `limit=1` 刻意：校验只需要"通不通"，不该顺手把 100 条拖下来。
     * 不带 `tags` 即"全部"，保证查询本身非空 —— 否则 200 分不清是"凭据被接受"还是
     * "这个查询碰巧没结果"（后者也是 200）。见类注释里"⚠️ 不拿随便一笔请求当校验"那条。
     *
     * 200 就够了：官方 DAPI 没有"我是谁"端点（wiki `howto:api` 只有 post / tag / user / comment
     * 四条列表端点），所以这是能拿到的最强信号。**不解析 body** —— 探针只回答"通不通"。
     */
    private fun verify(uid: String, key: String): GelbooruIdentity {
        val client = engine?.invoke() ?: throw NotWiredOnDesktop(
            "桌面这一侧还没接 Gelbooru 的校验通道（出网口没装上）",
        )
        val url = "$PROBE_URL&api_key=${enc(key)}&user_id=${enc(uid)}"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", GelbooruClient.API_USER_AGENT)
            .build()
        return client.newCall(request).execute().use { response ->
            when {
                response.code == 401 -> throw IOException(REJECTED_HINT)
                response.code == 403 -> throw IOException("Gelbooru 回了 403：这一组凭据被拒或触发了风控")
                !response.isSuccessful -> throw IOException("Gelbooru 返回 ${response.code}")
                else -> GelbooruIdentity(userId = uid)
            }
        }
    }

    /** 冷启动读回。**两样缺一就没有身份**，不做"有 ID 就算配好"这种推断。 */
    private fun restoreIdentity(): GelbooruIdentity? {
        val uid = userId
        val key = apiKey
        if (uid.isBlank() || key.isBlank()) return null
        return GelbooruIdentity(userId = uid)
    }

    companion object {
        /**
         * 键名与 `GelbooruAccount` **逐字相同**（两枚文件各自的 `companion` 私有常量，
         * 跨类不共享一处 —— 所以这里显式重写并在 [DesktopGalleryCredentials] 那边用同一组键名读写）。
         * 名字一致不等于数据共享：两端今天落在不同的文件上。
         */
        const val KEY_USER_ID = "user_id"
        const val KEY_API_KEY = "api_key"

        /**
         * 401 的那句话（[verify] 与 [GelbooruClient.failure] 共用这一份）。
         *
         * 实测这一站的 401 **body 为空**，且错 key / 错 id / 不带凭据三种情形给的是同一个答案
         * ⇒ 谁也判不出是哪一半错，所以把两种可能都写上，**不编一句"Key 不对"**（那可能是冤枉的）。
         */
        const val REJECTED_HINT =
            "Gelbooru 没接受这组凭据（API Key 与 User ID 要对得上，且都应来自同一账号）"

        /**
         * **没配凭据**时那一站的原话 —— S4 的判据文案。
         *
         * DAPI 匿名一律 401（2026-09-26 实测：`s=post` 与 `s=tag` 不带凭据都回 401，body 为空），
         * 所以没配不是"少几档权限"，是**一张图都取不到**。这句话必须出现在界面上，
         * 否则它会静默长成"这一站今天没图"——那是本仓最忌的静默交错。
         * `DesktopAbsentSitesTest` 逐字断这一句。
         */
        const val ANONYMOUS_HINT = "DAPI 匿名一律 401，需 api_key + user_id"

        /**
         * 设置页与桌面面板那枚「去账号页拿 Key」要打开的页面。
         * 官方 wiki 写明 API Key 与 User ID **都在账号选项页**。
         */
        /** 不是 `const`：站点域名由 `GallerySite` 派生（字符串模板吃的是 `val`），与 `GelbooruClient.BASE` 同一条理由。 */
        val ACCOUNT_PAGE: String = "https://${GallerySite.GELBOORU.apiHost}/index.php?page=account&s=options"

        private val PROBE_URL =
            "https://${GallerySite.GELBOORU.apiHost}/index.php?page=dapi&s=post&q=index&json=1&limit=1"

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
    }
}

/** 桌面侧那枚"这一路本机没接"的显式失败（不抛在装配那一刻 —— 那会让整个接线建不出来）。 */
internal class NotWiredOnDesktop(message: String) : IllegalStateException(message)

/**
 * [GelbooruClient.failure] 里 401 那一档改指 [GelbooruCredentialState.REJECTED_HINT]。
 *
 * 单独放一颗 `GalleryCredentialHints` 而不是让 client 直接引那颗类：client 那颗文件在
 * 桌面编译面上，而 `GelbooruCredentialState` 也一样 —— 两者都在就成了一颗类同时扮演
 * "读侧拼参数"与"写侧落盘"两个角色。把那句话提出来，两边各引一处，改文案只改一处。
 */
internal object GelbooruCredentialHints {
    val rejected: String get() = GelbooruCredentialState.REJECTED_HINT
    val anonymous: String get() = GelbooruCredentialState.ANONYMOUS_HINT
}
