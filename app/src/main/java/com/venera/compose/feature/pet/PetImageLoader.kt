package com.venera.compose.feature.pet

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import com.venera.compose.gallery.data.GalleryAnimationGate

/**
 * 桌宠专用的那把 ImageLoader。
 *
 * **为什么不复用应用级单例**（`VeneraApp.newImageLoader`）：那条链上根本没有动图解码器。
 * Coil 3.6.2 在 Android 上内置的两颗解码器都不动图（`GalleryImageLoader.kt:162` 那段 KDoc
 * 就是为这件事写的），所以把 `file:///android_asset/pet/idle.gif` 交给它 ——
 * **静默只出首帧**：不报错、不闪、界面看着完全正常，只是她永远不动。
 * 而给那把单例挂上动图解码器，等于把画廊"墙上卡片一律静帧"那条 OOM 判据的门拆到漫画侧，
 * 代价与收益不对等。
 *
 * **也不直接借画廊那把**：`GalleryImageLoader.get()` 会顺带建起画廊的磁盘缓存目录与派生的
 * OkHttp 客户端，而桌宠要的只是"读一个随包文件、解成动画"，不该为此拖起两套重对象。
 *
 * 解码器仍然走 [GalleryAnimationGate] 而不是裸注册 `AnimatedImageDecoder`：
 * 请求没表态时闸门返回 null，落到内置静态解码器 —— 这正是"系统把动画关掉时她停在首帧"
 * 那一档能成立的前提（理由与"为什么不能返回一个静态解码器"都写在闸门自己的 KDoc 里）。
 */
object PetImageLoader {

    /**
     * 随包素材的地址。素材是 72 帧原始序列重编码来的 198×119 / 8fps 版本
     * （原始 gif 3.63MB、488×292、72 帧共用一张 256 色全局色板 —— 在 66dp 的格子里是 11 倍过采样）。
     */
    const val ASSET_URI = "file:///android_asset/pet/idle.gif"

    /** 一帧 198×119 的 RGBA 约 94KB，两帧的量足够；素材在 APK 里，不需要磁盘缓存。 */
    private const val MEMORY_CACHE_BYTES = 1L shl 20

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: ImageLoader.Builder(context.applicationContext)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(MEMORY_CACHE_BYTES).build() }
            .components { add(GalleryAnimationGate()) }
            .build()
            .also { instance = it }
    }
}
