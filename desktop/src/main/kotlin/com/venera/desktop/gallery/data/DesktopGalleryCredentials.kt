package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.gallery.data.GalleryCredentials
import com.venera.compose.gallery.data.GelbooruCredentialState
import com.venera.compose.gallery.data.GelbooruCredentials
import com.venera.compose.gallery.data.GelbooruIdentity
import com.venera.compose.gallery.data.SauceNaoCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 桌面侧的两枚站方凭据读数。
 *
 * ## 同一颗对象实现三颗接口，不是图省事
 *
 * [GalleryCredentials] 是问"配没配"（两枚 `StateFlow`，给界面用），而
 * [GelbooruCredentials] / [SauceNaoCredentials] 是取数时拼参数用的那两枚串（给 client 用）。
 * S1 之所以要给 client 抽那两颗窄接口，是因为 `GelbooruAccount` / `SauceNaoAccount` 本体
 * 留在 Android 面（它们自己建 SharedPreferences，而设置页三处 UI 直接拿它们的流在渲染）。
 * 桌面这边没有那层包袱：**一份存储、一处读数**，两端拿到的必然是同一个事实 ——
 * 分成两颗对象就是给"界面显示已登录而请求带不上 key"这种分叉留位置。
 *
 * ## 键名与"没配"的判据照 Android 侧
 *
 * 存储名与键名照 `GelbooruAccount.kt` / `SauceNaoAccount.kt`（`user_id` / `api_key`，
 * 存储名引 [PreferenceKeys]）。[gelbooruIdentity] 的判据也照它的 `restoreIdentity`：
 * **uid 与 key 两枚都非空**才算配过 —— 只填一半在 Android 侧是"没登录"，桌面不能算成"已登录"。
 *
 * 两端今天落在**不同文件**上（Android `venera_*_account.xml`、桌面同名 `.json`）：
 * 名字一致不等于数据共享。
 *
 * ⚠️ 这一颗**只读**。写侧（用户在桌面填 key 那一路）属 S4，且有一条已拍板的红线：
 * 不许从浏览器 cookie 库里取他人凭据。
 */
class DesktopGalleryCredentials(private val handle: DesktopGalleryHandle) :
    GalleryCredentials,
    GelbooruCredentials,
    SauceNaoCredentials {

    private val gelbooruStore = JsonKeyValueStore(PreferenceKeys.PREFS_GELBOORU_ACCOUNT, handle.paths)
    private val sauceNaoStore = JsonKeyValueStore(PreferenceKeys.PREFS_SAUCENAO_ACCOUNT, handle.paths)

    /**
     * 判据本体是 `GelbooruCredentialState`（S4 抽出来的那颗，两端共用一份）。
     *
     * 桌面这一侧把出网口交给 [DesktopHttpEngine]：这一站每笔请求自带 UA 与两个 query 参数，
     * 不吃 `data.network` 的公共头（Android 侧那条注释的理由：`VeneraNetworkClient` 的头是
     * 全应用的，把站别凭据挂上去会漏给别的站）。
     */
    val gelbooru = GelbooruCredentialState(
        store = gelbooruStore,
        engine = { handle.engine.okHttpClient },
    )

    private val _sauceNaoHasKey = MutableStateFlow(sauceNaoKey().isNotBlank())
    override val sauceNaoHasKey: StateFlow<Boolean> = _sauceNaoHasKey.asStateFlow()

    override val gelbooruIdentity: StateFlow<GelbooruIdentity?> get() = gelbooru.identity

    /** SauceNao 的 key 单独一枚：Android 侧那颗 account 存的键名与 Gelbooru 的同名但不同存储。 */
    private fun sauceNaoKey(): String = sauceNaoStore.getString(KEY_API_KEY, "").orEmpty()

    override val userId: String get() = gelbooru.userId
    override val apiKey: String get() = gelbooru.apiKey

    /**
     * SauceNao 的写侧：**只落盘，不做校验**（照 `SauceNaoAccount.save`）。
     *
     * 这条判据不可省：那一站没有"保证非空的最小请求"，拿任何一笔搜索去验 key 都会真的
     * 消耗一次额度，而免费额度本来就少 —— 还会把"这次搜不出东西"和"key 不对"搅在一起。
     * key 对不对由第一次真实搜索的结果去说。
     */
    fun saveSauceNaoKey(key: String) {
        val trimmed = key.trim()
        sauceNaoStore.put(KEY_API_KEY, trimmed)
        _sauceNaoHasKey.value = trimmed.isNotBlank()
    }

    fun clearSauceNaoKey() = saveSauceNaoKey("")

    companion object {
        private const val KEY_API_KEY = "api_key"
    }
}
