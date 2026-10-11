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
 * ## 同一颗对象实现两枚接口，而**第三枚必须分出去**
 *
 * [GalleryCredentials] 是问"配没配"（两枚 `StateFlow`，给界面用），而
 * [GelbooruCredentials] / [SauceNaoCredentials] 是取数时拼参数用的那两枚串（给 client 用）。
 * S1 之所以要给 client 抽那两颗窄接口，是因为 `GelbooruAccount` / `SauceNaoAccount` 本体
 * 留在 Android 面（它们自己建 SharedPreferences，而设置页三处 UI 直接拿它们的流在渲染）。
 * 桌面这边没有那层包袱：**一份存储一处读数，但一站一枚读数** ——
 * 分成两颗对象是给"界面显示已登录而请求带不上 key"留位置，而**把两站的 key 并成一颗是更坏的那种分叉**
 * （见下面"两枚 key 必须是两枚读数"那一节）。
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
 * ## 两枚 key 必须是**两枚读数**（第三十四批 批 34）
 *
 * 从前这一颗同时实现 [GelbooruCredentials] 与 [SauceNaoCredentials]，而 Kotlin 里两枚接口的成员
 * **同名**（都叫 `apiKey`）⇒ 一颗对象只能交出一个 `apiKey`，实际交的是 Gelbooru 那一份。
 * 批 34 那天它**还没炸**，只是因为反搜那两枚方法是抛桩；批 35 把那一族接上之后，
 * 这条路带的就是 [sauceNao] 这一枚读数，而**接线那两颗参数由 `DesktopReverseSearchTest` ⑨ 钉着**
 * —— 递错一枚的后果是把 A 站的凭据发给 B 站（发给 saucenao.com 的 `api_key=` 成了 Gelbooru 那一份）。
 * 对岸不撞是因为两颗 account 各是各的对象。
 * 所以这里改成：这颗只实现 Gelbooru 那枚窄口，SauceNao 的那枚走 [sauceNao] 这一颗独立读数。
 *
 * ⚠️ 写侧只有一枚 [setSauceNaoKey]（空串 = 清掉），不补"成对的两枚"：
 * 批 26 撤掉的就是那对零调用点的死函数，而 `clear() = save("")` 本来就是同一条动作。
 */
class DesktopGalleryCredentials(private val handle: DesktopGalleryHandle) :
    GalleryCredentials,
    GelbooruCredentials {

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

    /**
     * 反搜那一侧要的 key —— **与上面那枚 `apiKey` 不是同一份**（两枚接口成员同名，
     * 并成一颗就会把 Gelbooru 的 key 拼进 saucenao.com 的请求里）。
     */
    val sauceNao: SauceNaoCredentials = object : SauceNaoCredentials {
        override val apiKey: String get() = sauceNaoKey()
    }

    /**
     * 落盘这一枚反搜的 key；**空串就是清掉**（对岸 `clear() = save("")` 同一条，所以这边只暴露一枚）。
     *
     * 只落盘、不发校验请求：对岸那颗 account 的类注释写着理由 —— SauceNAO 没有"保证非空的最小请求"
     * 那种端点，填完就发一笔等于拿用户的键去撞墙。所以这一屏不许写"已校验"，只说"已保存"。
     *
     * 去首尾空白：半截空格会让屏上那句「已保存」变成假话（站方按空 key 处理，用户以为填过了）。
     */
    fun setSauceNaoKey(key: String) {
        sauceNaoStore.put(KEY_API_KEY, key.trim())
        _sauceNaoHasKey.value = sauceNao.apiKey.isNotBlank()
    }

    override val userId: String get() = gelbooru.userId
    override val apiKey: String get() = gelbooru.apiKey

    companion object {
        private const val KEY_API_KEY = "api_key"
    }
}
