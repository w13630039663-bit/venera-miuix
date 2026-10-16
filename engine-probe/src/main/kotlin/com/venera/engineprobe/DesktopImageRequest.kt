package com.venera.engineprobe

/**
 * 取一页要打的三件套：**地址、请求头、切块数**。
 *
 * 它存在的唯一理由是让"怎么把源的 `onImageLoad` 信封读成一次请求"这一跳能脱离 JS 真跑被写判据 ——
 * `:engine-probe` 的 `internal` 桌面测试够不着，所以这一颗抬 public（判据要红在自己家才有信息量）。
 */
data class DesktopImageRequest(
    val url: String,
    val headers: Map<String, String>,
    val scrambleBlocks: Int,
)

/**
 * 把 `onImageLoad` 的答复读成 [DesktopImageRequest]。三种信封形状各有落点，**与 Android 侧逐字同语义**：
 *
 * - `{ __none: true }`（该源**没声明** `onImageLoad`，由调用点的守卫补上这一档）⇒ 图片键原样当地址、
 *   不带请求头、不去混淆。对岸同语义那两支是 `DownloadManager.kt:417/433` 的 `else { item }`
 *   与 `VeneraReaderScreen.kt:404` 的 `ComicPageSource.Network(url = u, …)`。
 *   切块数 `0` 是**结论**而不是兜底：`const num` 只可能来自 `onImageLoad` 回的 `modifyImage`。
 * - `{ success: false }` ⇒ **抛**。缺声明走原样与调用失败是两件事，后者静默退回 item 会把
 *   凭证过期读成"这本图就这么糙"（降级路径宁可错慢不可静默交错那条红线）。
 * - 有 `data` ⇒ 与撤出来那一跳同一套取法。
 */
fun desktopImageRequestOf(imageKey: String, env: Map<String, Any?>): DesktopImageRequest {
    if (env["__none"] == true) return DesktopImageRequest(imageKey, emptyMap(), 0)
    if (env["success"] == false) error("onImageLoad 失败: ${env["error"]}")
    val data = env["data"] as? Map<*, *> ?: error("onImageLoad 没有 data")
    val url = data["url"]?.toString() ?: imageKey
    val headers = (data["headers"] as? Map<*, *>)
        ?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()
    val num = Regex("""const\s+num\s*=\s*(\d+)""")
        .find(data["modifyImage"]?.toString().orEmpty())
        ?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return DesktopImageRequest(url, headers, num)
}
