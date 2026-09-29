package com.venera.compose.gallery.data

import coil3.Extras
import coil3.ImageLoader
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.getExtra
import coil3.gif.AnimatedImageDecoder
import coil3.request.ImageRequest
import coil3.request.Options

/**
 * 画廊那把 ImageLoader 的**动图闸门**：一笔请求要不要被解成动画，由请求自己表态。
 *
 * 为什么要有这一层（2026-09-29 拍板）：动图自动播放那一档只管大图页，**墙上的卡片一律静帧**。
 * 一屏动图同时解动画就是当天那条 OOM 读数的形状（256 MB Java 堆只剩 52 MB），
 * 而"墙上不许动"这件事不能靠观感约定，得有一道真的拦得住的闸。
 *
 * 拦法：Coil 的解码器链是**第一个 `create` 返回非 null 的赢**。这一层放在链上取代裸注册的
 * `AnimatedImageDecoder.Factory`：
 * - 请求带了 `allowAnimated = true` → 交给动图解码器（大图页那一档允许动时）；
 * - 没表态（默认 false）→ **返回 null**，请求顺着链落到 Coil 内置的 `StaticImageDecoder`
 *   / `BitmapFactoryDecoder`，它们对 GIF 只出首帧。
 *
 * 关键一点：**不能**在这里"返回一个静态解码器"来强制静帧 —— 那两个静态解码器各有适用条件
 * （`StaticImageDecoder` 要求 `bitmapConfig` 是 ARGB_8888/HARDWARE 且拿得到文件源），
 * 它们拒绝时我们若已经把链让出去，后面那颗动图解码器就会把动画解出来，
 * "从不放动图"就成了半真半假的开关。返回 null + 链上根本没有动图解码器，才是**恒静**。
 */
class GalleryAnimationGate(
    private val animated: Decoder.Factory = AnimatedImageDecoder.Factory(),
) : Decoder.Factory {

    override fun create(
        result: SourceFetchResult,
        options: Options,
        imageLoader: ImageLoader,
    ): Decoder? {
        if (!options.getExtra(GalleryAnimationKeys.allowAnimated)) return null
        return animated.create(result, options, imageLoader)
    }
}

/** 请求级开关的键。默认 false = 静帧（**默认保守**：没表态的地方不许自己动）。 */
object GalleryAnimationKeys {
    val allowAnimated = Extras.Key(default = false)
}

/** 给某一笔请求表态"这一笔允许解动画"。 */
fun ImageRequest.Builder.allowAnimatedImage(allow: Boolean): ImageRequest.Builder = apply {
    extras[GalleryAnimationKeys.allowAnimated] = allow
}
