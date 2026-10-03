package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryReverseSearch
import com.venera.compose.gallery.data.SauceNaoClient
import com.venera.compose.gallery.data.SauceNaoPage

/**
 * 桌面侧的以图搜图 —— **整颗没接**，两枚方法直接抛 [ReverseSearchNotWiredOnDesktop]。
 *
 * 为什么是抛而不是交回一个空页：契约这两枚的返回类型是 [SauceNaoPage]（不带 `Result`），
 * 因为正常路径上出错时页面要念的是**站方自己那句** `message`，客户端本来就是靠抛
 * `SauceNaoException` 把原因带上去的。所以"这一路本机没有"顺着同一条通道走，
 * 消费方今天的 `try/catch` 直接就能接住，不需要为桌面另开一条状态。
 *
 * [maxResults] 仍给真数（20）：它描述的是**端点**一次回多少条（`GalleryReverseSearch` 的注释
 * 定了这条 —— 那一枚是要给用户看的读数），不是"桌面今天能不能搜"。
 */
class DesktopGalleryReverseSearch : GalleryReverseSearch {

    override val maxResults: Int get() = SauceNaoClient.SAUCE_NUM_RESULTS

    override suspend fun searchByFile(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        allowNsfw: Boolean,
    ): SauceNaoPage = throw ReverseSearchNotWiredOnDesktop()

    override suspend fun searchByUrl(imageUrl: String, allowNsfw: Boolean): SauceNaoPage =
        throw ReverseSearchNotWiredOnDesktop()
}
