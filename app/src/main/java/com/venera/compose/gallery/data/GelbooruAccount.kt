package com.venera.compose.gallery.data

import android.content.Context
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.data.platform.android.AndroidKeyValueStore
import kotlinx.coroutines.flow.StateFlow

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
class GelbooruAccount private constructor(context: Context) : GelbooruCredentials {

    private val appContext = context.applicationContext

    private val prefs = AndroidKeyValueStore(appContext, PREFS_NAME)

    /**
     * 判据本体在 [GelbooruCredentialState] —— 桌面的 S4 面板要共用同一条（落盘 / 401 文案 / 站方点头才存）。
     * 这颗类退化成 **Android 接线 + `StateFlow` 门面**：`feature/settings` 那几处 UI 直接拿
     * [identity] / [loginUserId] / [isConfigured] 渲染，所以这些成员一颗都不能少、签名一字未改。
     */
    private val state = GelbooruCredentialState(
        store = prefs,
        engine = { VeneraNetworkClient.getInstance(appContext).okHttpClient },
    )

    /** 当前登录身份；null = 没配。零 diff 转发到 [GelbooruCredentialState.identity]。 */
    val identity: StateFlow<GelbooruIdentity?> = state.identity

    /** User ID（离线可用，登录框要用它回填）。没配时空串。 */
    val loginUserId: String get() = state.userId

    /** 已配置（两样都有）就是 true。 */
    val isConfigured: Boolean get() = state.isConfigured

    /** 当前 API Key；没配时空串。⚠️ 只给客户端拼 query，别摆到界面或日志上。 */
    override val apiKey: String get() = state.apiKey

    /** 当前 User ID；没配时空串。 */
    override val userId: String get() = state.userId

    /** 校验与落盘的判据逐字转发（不重写第二份）。 */
    suspend fun signIn(userId: String, apiKey: String): Result<GelbooruIdentity> =
        state.signIn(userId, apiKey)

    suspend fun refresh(): Result<GelbooruIdentity>? = state.refresh()

    /** 注销：凭据与身份一起删。 */
    fun signOut() = state.signOut()

    companion object {
        private const val PREFS_NAME = PreferenceKeys.PREFS_GELBOORU_ACCOUNT

        /** 转发到判据件那份，两边不许各写一个字面量。 */
        val ACCOUNT_PAGE: String get() = GelbooruCredentialState.ACCOUNT_PAGE

        @Volatile
        private var INSTANCE: GelbooruAccount? = null

        fun getInstance(context: Context): GelbooruAccount = INSTANCE ?: synchronized(this) {
            INSTANCE ?: GelbooruAccount(context.applicationContext).also { INSTANCE = it }
        }
    }
}

// `GelbooruIdentity` 住在 GelbooruClient.kt：`GalleryCredentials` 那颗契约的返回类型里有它，
// 而契约要进桌面编译面、这颗 account 不上（它吃 `Context` 与 `AndroidKeyValueStore`）。
// 同包移动，`feature/settings` 那几处的 import 一字未改。
