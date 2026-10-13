package com.venera.compose.reader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图片增强的色彩矩阵与锐化映射。
 *
 * 这里最要紧的一条是 [defaultIsIdentitySoNothingIsAttached]：它就是"默认态与改动前逐像素一致"
 * 那句验收的机器判据 —— 返回 null 意味着调用方**一个 ColorFilter 都不挂**，
 * 而不是"挂了一个看起来没变化的滤镜"。
 *
 * 4×5 的行主序下标：第 i 行 = `m[i*5 .. i*5+3]` 是三通道系数、`m[i*5+4]` 是偏移（0..255 值域）。
 */
class ReaderEnhancementTest {

    private fun matrix(
        brightness: Float = ReaderEnhancement.NEUTRAL_BRIGHTNESS,
        contrast: Float = ReaderEnhancement.NEUTRAL_CONTRAST,
        saturation: Float = ReaderEnhancement.NEUTRAL_SATURATION,
        night: Boolean = false,
    ) = ReaderEnhancement.readerColorMatrix(brightness, contrast, saturation, night)!!

    private fun row(m: FloatArray, i: Int) = FloatArray(4) { m[i * 5 + it] }
    private fun offset(m: FloatArray, i: Int) = m[i * 5 + 4]

    @Test fun defaultIsIdentitySoNothingIsAttached() {
        assertNull(
            ReaderEnhancement.readerColorMatrix(
                brightness = ReaderEnhancement.NEUTRAL_BRIGHTNESS,
                contrast = ReaderEnhancement.NEUTRAL_CONTRAST,
                saturation = ReaderEnhancement.NEUTRAL_SATURATION,
                nightInvert = false,
            )
        )
    }

    /** 中性色彩 + 开夜间 ≠ 恒等：夜间那一路必须仍然挂得上滤镜。 */
    @Test fun neutralColorsWithNightIsNotIdentity() {
        val m = matrix(night = true)
        assertEquals(-1f, m[0], 1e-6f)
        assertEquals(255f, offset(m, 0), 1e-4f)
    }

    /**
     * 夜间单独开启时，矩阵要与改动前那颗写死的反色矩阵逐个数相同 ——
     * 这一条钉的是"把滤镜换成合成式之后，只开夜间的用户看到的还是同一张图"。
     */
    @Test fun nightOnlyMatchesThePreviouslyHardcodedMatrix() {
        val hardcoded = floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
        assertArrayEquals(hardcoded, matrix(night = true), 1e-5f)
    }

    @Test fun saturationZeroIsGrayscale() {
        val m = matrix(saturation = 0f)
        // 三行的通道系数都等于 Rec.709 亮度权重 ⇒ 每行和相同，且三行彼此相同。
        val sums = (0..2).map { row(m, it).sum() }
        assertEquals(sums[0], sums[1], 1e-5f)
        assertEquals(sums[1], sums[2], 1e-5f)
        assertEquals(1f, sums[0], 1e-4f)
        assertArrayEquals(row(m, 0), row(m, 1), 1e-6f)
        assertArrayEquals(row(m, 1), row(m, 2), 1e-6f)
        // 权重用的是 Rec.709（不是 1/3 均分），否则纯蓝与纯绿会被当成一样亮。
        assertEquals(0.2126f, m[0], 1e-4f)
        assertEquals(0.7152f, m[1], 1e-4f)
        assertEquals(0.0722f, m[2], 1e-4f)
    }

    @Test fun contrastAboveOneScalesDiagonalAndPullsOffsetNegative() {
        val m = matrix(contrast = 1.5f)
        assertEquals(1.5f, m[0], 1e-5f)
        assertEquals(1.5f, m[6], 1e-5f)
        assertEquals(1.5f, m[12], 1e-5f)
        // out = (in - 128) * 1.5 + 128 ⇒ 偏移 = 128 * (1 - 1.5) = -64
        assertEquals(-64f, offset(m, 0), 1e-4f)
        assertEquals(-64f, offset(m, 2), 1e-4f)
    }

    @Test fun brightnessOffsetsByTwoFiftyFiveScale() {
        val m = matrix(brightness = 0.1f)
        assertEquals(25.5f, offset(m, 0), 1e-4f)
        assertEquals(1f, m[0], 1e-5f)
    }

    /** 亮度与对比度先合成（对比度绕中灰、亮度直接加），偏移是两者之和。 */
    @Test fun contrastAndBrightnessComposeIntoOneOffset() {
        val m = matrix(brightness = 0.1f, contrast = 1.5f)
        assertEquals(-64f + 25.5f, offset(m, 0), 1e-4f)
    }

    /**
     * 夜间叠在最后一层：`255 - in` ⇒ 系数取负、偏移变 `255 - 原偏移`。
     * 参照物是**原图**的亮暗 —— 若反过来先反色再调亮度，同一个亮度值会得到反号的效果。
     */
    @Test fun nightIsAppliedLast() {
        val plain = matrix(brightness = 0.1f, contrast = 1.5f)
        val withNight = matrix(brightness = 0.1f, contrast = 1.5f, night = true)
        assertEquals(-plain[0], withNight[0], 1e-5f)
        assertEquals(255f - offset(plain, 0), offset(withNight, 0), 1e-4f)
        assertTrue(withNight[0] < 0f)
    }

    /** alpha 行任何情况下都恒等：增强不该改变透明像素的形状。 */
    @Test fun alphaRowStaysIdentity() {
        listOf(matrix(night = true), matrix(saturation = 0f), matrix(brightness = -0.25f, contrast = 0.5f))
            .forEach { m ->
                assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f), row(m, 3), 1e-6f)
                assertEquals(0f, offset(m, 3), 1e-6f)
            }
    }

    /** 通道行不吃 alpha（第 4 列恒 0），否则透明区会被色彩调整污染。 */
    @Test fun colorRowsIgnoreAlpha() {
        val m = matrix(saturation = 0.3f, contrast = 1.2f, brightness = 0.2f, night = true)
        (0..2).forEach { assertEquals(0f, m[it * 5 + 3], 1e-6f) }
    }

    @Test fun outOfRangeInputsAreClamped() {
        // 越界值被钳到各自范围的端点，而不是让矩阵算出 10 倍对比度那种东西。
        assertArrayEquals(
            matrix(contrast = 9f),
            matrix(contrast = ReaderEnhancement.CONTRAST_RANGE.endInclusive),
            1e-6f,
        )
        assertArrayEquals(
            matrix(contrast = -9f),
            matrix(ReaderEnhancement.NEUTRAL_BRIGHTNESS, ReaderEnhancement.CONTRAST_RANGE.start, ReaderEnhancement.NEUTRAL_SATURATION),
            1e-6f,
        )
        assertArrayEquals(
            matrix(saturation = 40f),
            matrix(
                saturation = ReaderEnhancement.SATURATION_RANGE.endInclusive,
            ),
            1e-6f,
        )
        assertNotNull(matrix(brightness = -1f))
        assertEquals(
            matrix(brightness = ReaderEnhancement.BRIGHTNESS_RANGE.start)
                .let { offset(it, 0) },
            offset(matrix(brightness = -5f), 0),
            1e-4f,
        )
    }

    @Test fun sharpenMapsZeroToNoEffectAndSaturatesAtOneAndHalf() {
        assertEquals(0f, ReaderEnhancement.sharpenKernelAmount(0f), 1e-6f)
        assertEquals(1.5f, ReaderEnhancement.sharpenKernelAmount(1f), 1e-6f)
        // 越界钳制：0.5 的强度就是 0.75 的中心加成（线性）
        assertEquals(0.75f, ReaderEnhancement.sharpenKernelAmount(0.5f), 1e-6f)
        assertEquals(1.5f, ReaderEnhancement.sharpenKernelAmount(9f), 1e-6f)
        assertEquals(0f, ReaderEnhancement.sharpenKernelAmount(-9f), 1e-6f)
    }

    /** AGSL 源的结构判据：uniform 名与调用点写死的两个串必须一致，否则 shader 编得过而采样接不上。 */
    @Test fun agslDeclaresTheUniformsTheCallerSets() {
        val src = ReaderEnhancement.SHARPEN_AGSL
        assertTrue(src, src.contains("uniform shader uContent;"))
        assertTrue(src, src.contains("uniform float uAmount;"))
        assertTrue(src, src.contains("uContent.eval"))
        assertTrue(src, src.contains("half4 main(float2 coord)"))
    }
}
