package com.venera.compose.data.platform

/**
 * 打上这个 tag 的请求**不弹** Cloudflare 交互式过盾窗口，撞盾就原样失败。
 *
 * ⚠️ 与 [com.venera.compose.data.network.ImageFetchTag] 是**两件事**，别合并：
 * - [com.venera.compose.data.network.ImageFetchTag] = "图片取流"，同时关掉过盾**和**域名熔断（图片不该把 host 拉黑）；
 * - 本 tag = **只**关过盾，熔断照旧参与。
 *
 * 用途是画廊日榜那类"整屏等结果"的 API 流量（2026-09-26）：
 * 过盾是 `runBlocking { bypass() }` 等一个人机交互，而那个 `await()` 没有超时 ——
 * 用户若 Home 掉过盾窗口而不是点"取消"，取数线程会一直挂住，
 * 屏上就是永远的波浪环（OkHttp 的 callTimeout 关 socket 唤醒不了 parked 的线程）。
 * 但这类流量**仍然要参与熔断** —— 否则连续失败不会把 host 拉黑，
 * 「刷新 / 重试」的 `resetBreakers()` 也就没了对象。
 *
 * 所以：撞盾时直接失败并说一句话（代价是要手动重试），
 * 但"连不上"仍然走熔断快败（保住重试按钮的语义）。
 *
 * ## 为什么住在 `data/platform` 而不是原来的 `data/network`
 *
 * 它是"调用方 ↔ 平台出站层"之间的一条约定，不是一堆网络实现。原先住在
 * `HostCircuitBreaker.kt` 里，而八颗画廊客户端要引它 —— 那八颗因此被钉在
 * `data/network`（Android 面）上，桌面编译面过不去。声明本身是个空标记类，
 * 与平台无关，所以搬到两端都看得见的那一层；读它的那侧（`CloudflareBypassInterceptor`）
 * 留在原处，只是换个 import。
 */
class NoInteractiveBypassTag
