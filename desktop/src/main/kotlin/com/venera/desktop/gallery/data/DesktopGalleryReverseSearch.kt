package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.gallery.data.GalleryReverseSearch
import com.venera.compose.gallery.data.SauceNaoClient
import com.venera.compose.gallery.data.SauceNaoCredentials
import com.venera.compose.gallery.data.SauceNaoPage

/**
 * 桌面侧的以图搜图 —— 批 35 起**真接**：两枚方法转交共享那颗 [SauceNaoClient]，桌面这边不重写请求。
 *
 * ## 为什么必须收这两枚参数（而不是像从前那样无参构造）
 *
 * 客户端要的是一枚 [HttpEngine] 与一枚 [SauceNaoCredentials]。凭据那一枚**只能**是
 * `DesktopGalleryCredentials.sauceNao` 那一枚匿名实现：`SauceNaoCredentials.apiKey` 与
 * `GelbooruCredentials.apiKey` **同名而不同存储**（批 34 量到的那一条），递错一枚等于
 * 把 A 站的 key 拼进 saucenao.com 的请求里。所以这里连 `credentials.apiKey` 都不出现，
 * 由 `DesktopReverseSearchTest` ⑨ 把它反钉成违例。
 *
 * ## 客户端是共享面那颗单例
 *
 * `SauceNaoClient.getInstance` 双检一份缓存，缓存的键**其实是第一次递进去的那对** ——
 * 桌面这一侧引擎与凭据各只建一颗（`DesktopGalleryPorts.createPorts` 那条"凭据只建一颗"），
 * 所以单例与"同一个事实"这两条不冲突。哪天接线改成可重入（换 handle 再装一次），
 * 这一句要跟着重看 —— 单例不会跟着换凭据走。
 *
 * ## `searchByFile` 这一路：批 35 接上、批 77 下半 才有入口
 *
 * 批 35 摘掉抛桩时，界面上还没有"用户从本机挑一张图"那一步，它当时写的是"接上了而界面上还没有走到这里的那一步"。
 * 批 77 下半 做出来了那一层：「用本机的一张图找」（`DesktopGalleryLocalReverseSection` 那两颗件），
 * 挑图走 AWT 的文件框，而那枚闸口在发车之前判完六档（没挑 / 没键 / 找不到 / 是目录 / 不是图 / 太大）。
 * 这一枚**照原样转交**而不是抛 —— 转交的那一句一字未动，变的只有"有没有人走到这里"。
 *
 * ⚠️ 文件框那一步今天**没有读数**（鼠标自验是禁的，手册 §8）：取证种子 `--reverse-file=`
 * 走的是"跳过文件框、直接给一条路径"那一条路，它验到的是路径之后的三跳。
 *
 * ## 上限仍转交共享那枚常量
 *
 * `maxResults` 描述的是**端点**一次回多少条（契约注释定了这一条：那一枚是要给用户看的读数），
 * 桌面这边不写第二个 20。
 */
class DesktopGalleryReverseSearch(
    private val engine: HttpEngine,
    private val credentials: DesktopGalleryCredentials,
) : GalleryReverseSearch {

    override val maxResults: Int get() = SauceNaoClient.SAUCE_NUM_RESULTS

    private val client: SauceNaoClient get() = SauceNaoClient.getInstance(engine, credentials.sauceNao)

    override suspend fun searchByFile(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        allowNsfw: Boolean,
    ): SauceNaoPage = client.searchByFile(bytes, fileName, mimeType, allowNsfw)

    override suspend fun searchByUrl(imageUrl: String, allowNsfw: Boolean): SauceNaoPage =
        client.searchByUrl(imageUrl, allowNsfw)
}
