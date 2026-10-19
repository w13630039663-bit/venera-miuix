package com.venera.compose.gallery.data

import android.content.Context
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.data.platform.android.AndroidKeyValueStore
import kotlinx.coroutines.flow.StateFlow

/**
 * pixiv 账号的 **Android 接线那一半**（方案 `docs/rounds/gallery-pixiv-account-pkce-2026-10-17.md` §四 · P2）。
 *
 * 形状逐字照 [GelbooruAccount]：`private constructor(context)` + 自备一枚 SharedPreferences
 * + 把判据整个转发给 [PixivCredentialState] + `getInstance` 单例。
 * **这一颗里不许有第二条判据** —— 过期怎么算、失败念哪一句、哪些键落盘全在那颗判据件里，
 * 两端各写一份就是"界面说已登录而请求全 401"那一族的成因。
 *
 * 住进 `android/` 子目录而不是与判据件同目录：那个子目录被 `:desktop` 整目录挡在桌面编译面外
 * （吃 `Context` 的东西都搬那儿），而包名不变 ⇒ 同包的装配点（[GalleryPortsAndroid]）不用加 import。
 */
class PixivAccount private constructor(context: Context) : PixivCredentials {

    private val appContext = context.applicationContext

    private val prefs = AndroidKeyValueStore(appContext, PREFS_NAME)

    private val state = PixivCredentialState(
        store = prefs,
        engine = { VeneraNetworkClient.getInstance(appContext).okHttpClient },
    )

    override val authState: StateFlow<PixivAuthState> = state.authState

    override val isClientConfigured: Boolean get() = state.isClientConfigured

    override suspend fun bearerOrNull(): String? = state.bearerOrNull()

    override fun beginLogin(): PixivLoginChallenge? = state.beginLogin()

    override suspend fun completeLogin(redirectUrl: String): Result<PixivAuthState> =
        state.completeLogin(redirectUrl)

    override suspend fun signInWithPasted(text: String): Result<PixivAuthState> =
        state.signInWithPasted(text)

    override fun signOut() = state.signOut()

    /**
     * 已登录那位的用户号，取来直接打 `PixivClient.avatarUrl(Long)`（顶栏头像那一跳）。
     *
     * null = 没登录，**或站方答复里根本没有 `user`**（那时 `authState` 仍是 SignedIn：
     * 票据是好的，只是不知道是谁 —— 头像换成一枚占位圈，而不是把登录态读成没登录）。
     */
    val signedInUserId: Long?
        get() = (state.authState.value as? PixivAuthState.SignedIn)?.userId?.toLongOrNull()

    companion object {
        private const val PREFS_NAME = PreferenceKeys.PREFS_PIXIV_ACCOUNT

        @Volatile
        private var INSTANCE: PixivAccount? = null

        fun getInstance(context: Context): PixivAccount = INSTANCE ?: synchronized(this) {
            INSTANCE ?: PixivAccount(context.applicationContext).also { INSTANCE = it }
        }
    }
}
