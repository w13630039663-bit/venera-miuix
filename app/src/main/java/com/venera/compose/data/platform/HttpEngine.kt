package com.venera.compose.data.platform

import okhttp3.OkHttpClient

/**
 * 出站 HTTP 客户端的唯一取用口。
 *
 * 存在的理由：`gallery/data` 那十颗站客户端原先各自写一遍
 * `VeneraNetworkClient.getInstance(appContext).okHttpClient` —— 那是 Android 侧的类
 * （它吃 `Context`、`VeneraPreferences`、Cloudflare 过盾），于是整棵 `gallery/data`
 * 上不了桌面编译面。把"从哪拿 client"抽成一颗口之后，站客户端本身与平台无关，
 * 两端各接各的实现。
 *
 * ⚠️ 只交回 client，不交回 `downloadBytes`：那个成员唯一的消费方是 `GallerySaver`
 * （MediaStore 落盘，本来就在桌面排除名单里）。为它放宽这颗口，等于给桌面凭空造一条
 * "有接口、没人接、将来谁都会以为它可用"的假路。
 *
 * ⚠️ 实现侧不许在这里加调度器、缓存策略或重试语义 —— 那些是 `VeneraNetworkClient`
 * 与调用方各自的私事，抽口只解决"从哪拿"这一件事。
 */
interface HttpEngine {

    val okHttpClient: OkHttpClient
}
