package com.venera.compose.engine

/**
 * 引擎层的平台出口。
 *
 * 抽步骤 A 之前这几个 handler 直接写 `android.util.Log` / `android.util.Base64` / `Context`，
 * 于是整层无法在纯 JVM（桌面宿主）下编译。这里只收**两处平台差异**，不改任何行为：
 * 日志仍走 logcat（Android 侧在构造引擎时装 sink），Base64 语义与 Android 对齐。
 */
object EngineLog {

    @Volatile
    var sink: (level: String, tag: String, message: String) -> Unit =
        { level, tag, message -> println("[$level/$tag] $message") }

    fun w(tag: String, message: String) = sink("W", tag, message)

    fun e(tag: String, message: String) = sink("E", tag, message)
}

object EngineBase64 {

    // ≙ android.util.Base64.NO_WRAP：带 padding、编码不插换行
    private val encoder: java.util.Base64.Encoder = java.util.Base64.getEncoder()

    // ≙ android.util.Base64.DEFAULT：解码容忍串里夹的换行与空白
    private val decoder: java.util.Base64.Decoder = java.util.Base64.getMimeDecoder()

    fun encodeToString(bytes: ByteArray): String = encoder.encodeToString(bytes)

    fun decode(text: String): ByteArray = decoder.decode(text)
}
