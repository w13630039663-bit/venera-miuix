package com.venera.compose.gallery.data

import android.content.Context
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.data.platform.android.AndroidKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SauceNAO 的 API Key（**可选**）。
 *
 * ## 与 [GelbooruAccount] 不是一类东西
 *
 * | | Gelbooru | SauceNAO |
 * |---|---|---|
 * | 不配能不能用 | **完全不能用**（DAPI 匿名一律 401） | 能用，但额度小 |
 * | 凭据组成 | User ID + API Key 两样 | 只有 API Key 一样 |
 * | 校验探针 | 有（见那边注释） | **没有** —— 见下 |
 *
 * 这里**刻意不做"填完就发一笔校验请求"**：SauceNAO 没有"保证非空的最小请求"这种端点 ——
 * 任何一次搜索都会真的消耗一次额度，而免费额度本来就少。拿用户的额度去证明"key 能用"
 * 是笔不划算的账，而且"这次搜不出东西"和"key 不对"两件事会被搅在一起。
 * 所以 key 对不对，由第一次真实搜索的结果去说（状态码与站方给的 message 都会念出来）。
 *
 * Key 只落本机 SharedPreferences；**不写日志、不进任何提示文案**（与 Gelbooru 同一条规矩）。
 */
class SauceNaoAccount private constructor(context: Context) : SauceNaoCredentials {

    private val appContext = context.applicationContext

    private val prefs = AndroidKeyValueStore(appContext, PREFS_NAME)

    /**
     * 有没有配 key。
     *
     * StateFlow 而不是普通字段：设置页存好之后，画廊那屏要**立刻**从"没配 key 时的说明"
     * 切回可用状态，而不是等重启。值本身是布尔，key 不外流。
     */
    private val _hasKey = MutableStateFlow(prefs.getString(KEY_API_KEY, "").orEmpty().isNotBlank())

    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    /** 当前 key；没配时空串。只给 [SauceNaoClient] 拼参数用。 */
    override val apiKey: String get() = prefs.getString(KEY_API_KEY, "").orEmpty()

    fun save(key: String) {
        val trimmed = key.trim()
        prefs.put(KEY_API_KEY, trimmed)
        _hasKey.value = trimmed.isNotBlank()
    }

    fun clear() = save("")

    companion object {
        private const val PREFS_NAME = PreferenceKeys.PREFS_SAUCENAO_ACCOUNT
        private const val KEY_API_KEY = "api_key"

        /** 免费 key 的领取页 —— 设置页那颗「去拿 Key」开它。 */
        const val KEY_PAGE = "https://saucenao.com/user.php?page=search-api"

        @Volatile
        private var INSTANCE: SauceNaoAccount? = null

        fun getInstance(context: Context): SauceNaoAccount = INSTANCE ?: synchronized(this) {
            INSTANCE ?: SauceNaoAccount(context.applicationContext).also { INSTANCE = it }
        }
    }
}
