package com.venera.desktop

import androidx.compose.ui.graphics.BlurEffect
import com.kyant.backdrop.isRuntimeShaderSupported as backdropRuntimeShader

/**
 * S0-7 判据探针：玻璃那 4 个消费点都先过 `isRuntimeShaderSupported()`，
 * 它在 desktop 返回 false 就等于**整段静默跳过**（按"降级必须可见"这条不能接受）。
 *
 * 量四层：Miuix 口径、Kyant backdrop 口径、AGSL 类在桌面 classpath 上到底存不存在、
 * 以及 Skia 层的 blur 原语能不能真造出来。只报一层就是假读数 —— 前两层可以各不相同，
 * 而"AGSL 没有"与"blur 有"是两件事，决定了降级要降到哪一档。
 */
fun main() {
    println("S07_MIUIX_isRuntimeShaderSupported=" + runCatching { top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported() })
    println("S07_BACKDROP_isRuntimeShaderSupported=" + runCatching { backdropRuntimeShader() })

    val agslClass = runCatching { Class.forName("androidx.compose.ui.graphics.RuntimeShader") }
    println(
        "S07_AGSL_RuntimeShader 在桌面 classpath=" + agslClass.isSuccess +
            agslClass.exceptionOrNull()?.let { " (${it.javaClass.simpleName})" }.orEmpty()
    )

    val blur = runCatching { BlurEffect(4f, 4f) }
    blur.getOrNull()?.let {
        println("S07_BlurEffect 构造=成功 isSupported=${it.isSupported()}")
    }.let {
        if (blur.isFailure) println("S07_BlurEffect 构造=失败 ${blur.exceptionOrNull()}")
    }

    // 两个库的 gate 都返回 true，说明它们走的是自家 AGSL 通道（backdrop 有 SkikoRuntimeShader）。
    // 那就把真 shader 造出来：构造 + 转成 compose Shader 都不抛，才算"玻璃真能画"。
    val agsl = "uniform float2 size; half4 main(float2 xy) { return half4(xy / size, 0.5, 1.0); }"
    val kyantShader = runCatching { com.kyant.backdrop.RuntimeShader(agsl) }
    println(
        "S07_BACKDROP_RuntimeShader 构造=${kyantShader.isSuccess} " +
            kyantShader.exceptionOrNull()?.let { "(${it.javaClass.simpleName}: ${it.message?.take(160)})" }.orEmpty()
    )
    kyantShader.getOrNull()?.let { s ->
        println("S07_BACKDROP 实现类=${s.javaClass.name}")
    }
}
