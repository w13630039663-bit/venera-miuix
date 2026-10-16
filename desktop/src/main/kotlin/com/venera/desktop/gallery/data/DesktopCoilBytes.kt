package com.venera.desktop.gallery.data

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从 **`AsyncImage` 背后同一份** Coil 磁盘缓存里取一张图的原始字节（批 54 抽成共用件）。
 *
 * ## 为什么全树只许有这一处
 *
 * 播放器（批 51）与「复制这张图」（批 54）要的都是同一件事：这张图的字节。各写一份就等于
 * 将来只改一处 —— 而这一颗件里最容易被漏改的正是下面那段"冷缓存先取一趟"。
 *
 * ## 那一趟"先取"是图证换来的，不是补出来的漂亮实现
 *
 * 第一版只读快照，而大图那一屏是**点卡即开**的：播放器起效那一刻这张图还没进缓存
 * （`AsyncImage` 与预取都还没跑完），于是每次都退回首帧、动图永远不动。
 * 读数 `D_动图 退化 …还没进缓存` 当场把这条抓了出来（手册 §75 那一批）。
 * 修法是**先让同一颗 loader 走一趟**再读，而不是另起一条请求轨 ——
 * 另起一枚 loader 就是"写进另一份缓存 = 预取白花流量"那一族（§76 钉过）。
 *
 * 拿不到就是 `null`：那既不是"站方不给这张图"，也不是"这张图不是图"，是第三句话（缓存里没它）。
 */
suspend fun desktopCoilBytes(context: PlatformContext, url: String): ByteArray? =
    withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val loader = SingletonImageLoader.get(context)
        readCachedBytes(loader, url)?.let { return@withContext it }
        runCatching { loader.execute(ImageRequest.Builder(context).data(url).build()) }
        readCachedBytes(loader, url)
    }

/**
 * 只读缓存，不发请求。那颗 `diskCache` 为 null（批 90 起由 `DesktopImagePipeline.install` 递目录进来配，
 * 撤掉它就交回 null）与没命中（快照 null）都交回 null，
 * 因为这两种在界面上说同一句话：这一张此刻不在缓存里。
 */
private fun readCachedBytes(loader: ImageLoader, url: String): ByteArray? =
    runCatching {
        val cache = loader.diskCache ?: return@runCatching null
        val snapshot = cache.openSnapshot(url) ?: return@runCatching null
        snapshot.use { Files.readAllBytes(Paths.get(it.data.toString())) }
    }.getOrNull()
