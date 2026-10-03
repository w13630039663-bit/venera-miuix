package com.venera.compose.data.platform.android

import android.content.Context
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.data.platform.HttpEngine
import okhttp3.OkHttpClient

/**
 * Android 侧实现：转调 `VeneraNetworkClient`，不改它的任何配置（缓存、代理、优选 IP、
 * 熔断、限流、UA 全在那颗类里，本口只负责"给出去"）。
 *
 * ⚠️ `okHttpClient` 写成 `get()` 而不是构造期取值：`VeneraNetworkClient` 是懒建的重件
 * （它自己那侧还要读 33 源的偏好），构造适配器时就去取等于把建 client 的时机提前到
 * 每一次 `getInstance(context)`。本仓适配器一律守"成员体里现取"这条。
 */
class AndroidHttpEngine(context: Context) : HttpEngine {

    private val appContext = context.applicationContext

    override val okHttpClient: OkHttpClient
        get() = VeneraNetworkClient.getInstance(appContext).okHttpClient
}
