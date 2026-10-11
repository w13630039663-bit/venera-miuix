package com.venera.desktop.gallery.ui

/**
 * 动图那一档的五道算式（批 51，设计 §3）。
 *
 * 全在播放器外面：播放器只负责"取字节、解码、推进帧号"，而这几问一旦散进播放器，
 * 就成了只有真跑一遍才看得见的东西（本仓在连播与预取上各吃过一次）。
 */

/** 退化的三种成因。**各一句**，不许塌成一句"动图失败"。 */
internal enum class DesktopAnimatedCause {
    /** 缓存里没有这一张的原始字节（预取没赶上、或那一次根本没落盘）。 */
    NO_CACHE_SNAPSHOT,

    /** 拿到了字节但 `Codec` 起不来 —— 那不是动图，是坏了。 */
    DECODE_FAILED,

    /** 解出来了而站方只给一帧。 */
    SINGLE_FRAME,
}

/**
 * 该不该逐帧：站方给得多帧 **且** 这一屏该动。
 *
 * 只看帧数的那一版会把"设置里那枚开关"做成假开关；只看开关的那一版会给单帧图白起时钟。
 */
internal fun desktopShouldAnimate(frameCount: Int, animateGifs: Boolean): Boolean =
    animateGifs && frameCount > 1

/**
 * 帧间隔下界。⚠️ 那个 1 **不是观感口径**，是防自旋的下界：站方给 0 或负数时不许把 CPU 打满。
 *
 * 为什么不写一个"看起来更合理"的 20ms / 100ms：本仓与 Coil 桌面侧都没有既有的下限常量可抄
 * （`coil-gif` 根本没有桌面变体），在这里造第二个数就是往口径表里塞一枚没人定过的值。
 */
internal fun desktopFrameDelayMs(rawMs: Int): Int = rawMs.coerceAtLeast(1)

/** 像不像动图：只看地址本身，不发请求也不解码（解码之后还有一条真判据在 [desktopShouldAnimate]）。 */
internal fun desktopLooksAnimated(url: String): Boolean =
    url.substringBefore('?').substringBefore('#').endsWith(".gif", ignoreCase = true)

/** 那一句退化说明上不上屏由调用方定，但**三句必须两两不同**（「没取到 / 没有 / 没接」是三句话）。 */
internal fun desktopAnimatedFallback(cause: DesktopAnimatedCause): String = when (cause) {
    DesktopAnimatedCause.NO_CACHE_SNAPSHOT -> "这一张的原始字节还没进缓存，先按静帧摆"
    DesktopAnimatedCause.DECODE_FAILED -> "原始字节解不出动图，先按静帧摆"
    DesktopAnimatedCause.SINGLE_FRAME -> "站方这一张只给了一帧"
}

/**
 * 一行读数：帧数、循环档、缓存命中与否、吃的是哪一档、那一帧多大。
 *
 * `repetition < 0` 在 Skia 那边是**无限循环**的记号，直接把它念出去就是假读数
 * （屏上在无限循环而读数写"循环=-1"，读的人会判成解码错了）。
 * 缓存那一枚不许省：`openSnapshot` 键不中是**恒空**，不是命中。
 * 位深由调用方从真物件取（`Bitmap.bytesPerPixel`）而不是这里假设 4 —— 站方哪天给出一张
 * 非 32 位的动图，那行 `每帧=` 仍然念的是真实字节。
 */
internal fun desktopAnimatedReadout(
    frames: Int,
    repetition: Int,
    cacheHit: Boolean,
    tier: DesktopDetailTier,
    width: Int,
    height: Int,
    bytesPerPixel: Int,
): String =
    "D_动图 帧=$frames 循环=" +
        (if (repetition < 0) "无限" else "$repetition") +
        " 缓存=" + (if (cacheHit) "中" else "空") +
        " 档=$tier 尺寸=${width}x${height}" +
        " 每帧=" + desktopByteLabel(width.toLong() * height.toLong() * bytesPerPixel.toLong())

/**
 * 字节数念成 KB / MB 一档。
 *
 * 为什么用整数除法而不是 `format("%.1f")`：默认 locale 会把小数点换成逗号，
 * 而那条读数是给人和尺子两边读的 —— `每帧=3,8MB` 在判式眼里等于没念。
 */
internal fun desktopByteLabel(bytes: Long): String {
    val scaled = if (bytes >= 1024L * 1024L) bytes * 10 / (1024L * 1024L) else bytes * 10 / 1024L
    val unit = if (bytes >= 1024L * 1024L) "MB" else "KB"
    return "${scaled / 10}.${scaled % 10}$unit"
}
