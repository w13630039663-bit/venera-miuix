package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryNetworkHygiene
import com.venera.compose.gallery.data.GallerySite

/**
 * 桌面侧的"清熔断"—— 今天是**有意的 no-op**，且出声一次。
 *
 * Android 侧那枚成员逐站调 `HostCircuitBreaker.reset(host)`（`android/GalleryAdaptersAndroid.kt`
 * 末尾那颗），而桌面这一层的 OkHttpClient 上**没有挂熔断器** —— 那颗类住在 `data.network`，
 * 是共享面的禁引族（`DesktopSharedFaceLedgerTest.FORBIDDEN`）。没什么可清，所以不清。
 *
 * 为什么不干脆抛：调用方是"重试"那一条路（用户点了重试才走到这里）。在这里抛会把一次
 * 正常重试打死，而那并不是"出错"，只是"这一档桌面没有"。
 *
 * 为什么也不是静默空函数：那正是本仓反复栽过的形状 —— 将来桌面挂上熔断器（集成时如果开
 * `HostCircuitBreaker` 的共享面，这一步就要改），没人会想起这里还欠一句真实现。
 * 所以首次调用打一行 stdout，把"这是有意缺席"钉在日志里。
 *
 * 与漫画侧 `data/api/NetworkApi.kt` 的 `resetAllBreakers()` 是**同一条动作的两份**，
 * 按各侧自持不合并（口径见 `gallery/ui/GalleryFeedReadout.kt:32`）。
 */
class DesktopGalleryNetworkHygiene : GalleryNetworkHygiene {

    override fun resetAllSiteBreakers() {
        if (!announced.getAndSet(true)) {
            DesktopGalleryFailureLog.warn(
                TAG,
                "桌面档没有每主机熔断器（那颗住在禁引族 data.network），清熔断这一档今天是有意的 no-op；" +
                    "站点数 ${GallerySite.entries.size} 未变，只是没有东西被清",
            )
        }
    }

    private companion object {
        const val TAG = "DesktopGalleryNetworkHygiene"
        val announced = java.util.concurrent.atomic.AtomicBoolean(false)
    }
}
