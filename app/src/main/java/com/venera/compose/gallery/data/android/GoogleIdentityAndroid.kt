package com.venera.compose.gallery.data

import android.content.Context
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.data.platform.android.AndroidKeyValueStore

/**
 * 本机 Google 账号身份的 **Android 接线那一半**（判据与理由见 [LocalGoogleIdentity]）。
 *
 * 形状与 [PixivAccount] 逐字同构：`private constructor(context)` + 自备一枚 SharedPreferences
 * + `getInstance` 单例。**这里没有第二套判据** —— 只有"哪个键、什么算空"这两件事，
 * 它们也就只写在下面这几行里。
 *
 * 住在 `android/` 子目录的理由同 `PixivAccount`：那个目录被 `:desktop` 整目录排除
 * （吃 `Context` 的东西都搬那儿），而包名不变 ⇒ 装配点 `GalleryPortsAndroid` 不用加 import。
 *
 * ⚠️ 配置名与 pixiv 那份**必须不同**（`PreferenceKeys.PREFS_GOOGLE_IDENTITY`）：
 * `PixivCredentialState.signOut()` 走 `store.clear()`，同文件的话"退出 pixiv"会把这条
 * 与登录态毫无关系的身份一起抹掉。
 */
class GoogleIdentityAndroid private constructor(context: Context) : LocalGoogleIdentity {

    private val store = AndroidKeyValueStore(context.applicationContext, PreferenceKeys.PREFS_GOOGLE_IDENTITY)

    override fun email(): String? =
        store.getString(KEY_EMAIL, "").orEmpty().trim().ifBlank { null }

    override fun remember(email: String) {
        store.put(KEY_EMAIL, email.trim())
    }

    override fun forget() {
        store.remove(KEY_EMAIL)
    }

    companion object {
        /** 落盘键名（值就是键名：改一个字符 = 老身份读不回来）。 */
        const val KEY_EMAIL = "email"

        @Volatile
        private var INSTANCE: GoogleIdentityAndroid? = null

        fun getInstance(context: Context): GoogleIdentityAndroid = INSTANCE ?: synchronized(this) {
            INSTANCE ?: GoogleIdentityAndroid(context.applicationContext).also { INSTANCE = it }
        }
    }
}
