package com.venera.compose.reader

/**
 * 阅读器的**可选图片增强**（锐化 + 色彩调整）。**默认全关**，关着时渲染路径与不加这个功能逐像素相同。
 *
 * ── 为什么两件事走两条完全不同的技术路线 ──
 *
 * - **色彩**（亮度 / 对比度 / 饱和度）是**逐像素的线性变换**，`ColorFilter.colorMatrix` 正好是它，
 *   而且它已经在四条渲染路径（条漫 / 横向连续 / 翻页 / 双页）上穿好了 —— 夜间反色滤镜就是走这条路。
 *   加一条同型的东西，代价只有"多乘一个矩阵"，没有新依赖、没有新渲染机制。
 * - **锐化**是**卷积**（每个像素要读邻居），而 `ColorMatrix` 只看得到自己那一个像素 ——
 *   数学上就做不到，硬凑只能用 `ColorMatrix` 冒充锐化（抬高局部对比度），那是**假的锐化**：
 *   屏上会变成"更硬的边"而不是"更清楚的细节"，而用户按了"锐化"却拿到别的东西是本仓最忌的那一类。
 *   所以锐化走 AGSL `RuntimeShader`（minSdk 33 可用），失败就**如实降级**、不假装生效。
 *
 * ── 「默认不改观感」是一个机器判据，不是一句承诺 ──
 *
 * [readerColorMatrix] 在全中性（且不开夜间反色）时返回 `null`。调用方据此**一个 ColorFilter 都不挂**，
 * 锐化同理返回 null。于是"默认态与改动前一致"这件事可以被断言，而不是靠肉眼比。
 */
object ReaderEnhancement {

    /** 中性值：三个色彩量各自的原图点。设置页与判据都读这一份，别在别处再写一遍字面量。 */
    const val NEUTRAL_CONTRAST = 1f
    const val NEUTRAL_SATURATION = 1f
    const val NEUTRAL_BRIGHTNESS = 0f
    const val NEUTRAL_SHARPEN = 0f

    /** 各档取值范围（设置页滑块与 setter 的钳制读同一份）。 */
    val CONTRAST_RANGE = 0.5f..1.5f
    val SATURATION_RANGE = 0f..2f
    val BRIGHTNESS_RANGE = -0.25f..0.25f
    val SHARPEN_RANGE = 0f..1f

    /**
     * Rec.709 亮度权重。饱和度变换要"保持亮度不变"，就得知道人眼把三通道各算多少 ——
     * 用 (1/3, 1/3, 1/3) 的话，纯蓝与纯绿会被当成一样亮，去饱和后蓝比绿亮一大截。
     */
    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f

    /**
     * 三个色彩量 + 夜间反色 → 4×5 色彩矩阵（`ColorMatrix` 的行主序 20 个数）。
     *
     * **返回 null = 全中性**，调用方据此不挂 ColorFilter。
     *
     * ── 合成顺序（后写的先作用在像素上）──
     *
     * 结果是 `night ∘ brightness ∘ contrast ∘ saturation`：先在**原图**上做饱和度、对比度、亮度，
     * 最后才反色。反过来（先反色再调对比度）会出两种可见的错：反色后"亮"与"暗"互换，
     * 同一个对比度系数作用出来的效果与不反色时不对称；而亮度偏移在反色前后更是直接反号。
     * 用户调的是"这张图要多亮"，他心里的参照是**原图**。
     *
     * @param brightness −0.25..0.25，按 ±255 偏移（0 = 原图）
     * @param contrast 0.5..1.5，绕中灰 128 缩放（1 = 原图）
     * @param saturation 0..2（1 = 原图，0 = 灰度）
     * @param nightInvert 夜间反色（与设置里那枚滤镜同一个语义）
     */
    fun readerColorMatrix(
        brightness: Float,
        contrast: Float,
        saturation: Float,
        nightInvert: Boolean,
    ): FloatArray? {
        val b = brightness.coerceIn(BRIGHTNESS_RANGE)
        val c = contrast.coerceIn(CONTRAST_RANGE)
        val s = saturation.coerceIn(SATURATION_RANGE)

        // 全中性 ⇒ 连矩阵都不必算。这一条就是"默认不改观感"的判据本身。
        if (b == NEUTRAL_BRIGHTNESS && c == NEUTRAL_CONTRAST && s == NEUTRAL_SATURATION && !nightInvert) {
            return null
        }

        // ① 饱和度：out = luma + s * (in - luma)，其中 luma = 三通道的加权和。
        //    写成矩阵就是"每行 = 权重*(1-s) ，对角再 +s"。
        val invSat = 1f - s
        val sr = LUMA_R * invSat
        val sg = LUMA_G * invSat
        val sb = LUMA_B * invSat

        // ② 对比度：out = (in - 128) * c + 128 = in*c + 128*(1-c)。
        //    偏移那一项按 255 归一化后进第 5 列（ColorMatrix 的偏移列取值域是 0..255）。
        val contrastOffset = 128f * (1f - c)

        // ③ 亮度：out = in + b*255。
        val brightnessOffset = b * 255f

        // 把 ①②③ 先乘起来（都是"仿射"，可以直接合成一个仿射），最后再叠 ④ 反色。
        // 逐元素展开而不是引入矩阵库：20 个数写清楚比"调一个通用乘法"更容易核对，
        // 而这一段正是最需要能一眼核对的地方。
        val m = FloatArray(20)

        // 逐元素展开三行（R/G/B）而不是引入矩阵库：20 个数写清楚比"调一个通用乘法"更容易核对，
        // 而这一段正是最需要能一眼核对的地方。每行 = 饱和度行的三个系数再乘对比度，对角那格
        // 额外带上 s（饱和度在自身通道上的恒等部分）。
        m[0] = (sr + s) * c
        m[1] = sg * c
        m[2] = sb * c
        m[3] = 0f
        m[4] = contrastOffset + brightnessOffset

        m[5] = sr * c
        m[6] = (sg + s) * c
        m[7] = sb * c
        m[8] = 0f
        m[9] = contrastOffset + brightnessOffset

        m[10] = sr * c
        m[11] = sg * c
        m[12] = (sb + s) * c
        m[13] = 0f
        m[14] = contrastOffset + brightnessOffset

        // ④ alpha 行恒等。
        m[15] = 0f
        m[16] = 0f
        m[17] = 0f
        m[18] = 1f
        m[19] = 0f

        if (!nightInvert) return m

        // ⑤ 夜间反色叠在最外层：out = 255 - in。
        //    对上一步的仿射再复合一次"乘 -1、加 255"，逐行把系数取负、偏移换成 255 - 原偏移。
        for (row in 0 until 4) {
            val base = row * 5
            if (row == 3) continue // alpha 行不参与反色
            for (col in 0 until 4) m[base + col] = -m[base + col]
            m[base + 4] = 255f - m[base + 4]
        }
        return m
    }

    /**
     * 锐化强度 0..1 → 矩阵里的"中心加成"。0 = 不锐化（返回 0，调用方据此不挂 shader）。
     *
     * 0..1 映射到 0..1.5 的中心加成：unsharp 的核是
     * `中心 = 1 + k`、四邻 = `-k/4`，`k=1.5` 已经是"明显但还没起白边"的上限，
     * 再往上会在高对比边缘出现肉眼可见的光晕（halo），那不是"更清楚"。
     */
    fun sharpenKernelAmount(strength: Float): Float =
        strength.coerceIn(SHARPEN_RANGE) * 1.5f

    /**
     * AGSL 锐化 shader。**十字四邻 unsharp**（不是 3×3 全邻）：
     * 四邻已经能吃掉线条边缘的糊感，而九邻在超大长图（条漫常见 8000px+）上每像素多读五个采样点，
     * 代价换不来肉眼差别。
     *
     * `uAmount` 为 0 时输出与输入逐像素相同 —— 这也是"降级路径"的兜底：
     * shader 编不出来时调用方**不挂 renderEffect**，而不是挂一个恒等 shader。
     *
     * ── 本仓解析到的 Compose（1.12.0）已经没有 Compose 侧的 `RuntimeShader` / `RuntimeShaderEffect` ──
     * （`javap` 过 ui 与 ui-graphics 两个 aar 的 classes.jar，零个 `RuntimeShader*` 类；
     * AGSL 支持在 `AndroidRenderEffect.android.kt` 里只剩平台通道）。所以这一串喂的是
     * `android.graphics.RuntimeShader(源码串)`（**单参**那颗：双参要 API 34，minSdk 33 够不着）
     * + `RenderEffect.createRuntimeShaderEffect(...)`，强度经 `setFloatUniform("uAmount", …)` 传入，
     * 最后用公开的 `android.graphics.RenderEffect.asComposeRenderEffect()` 转回 Compose 那一侧，
     * 进 `Modifier.graphicsLayer { renderEffect = … }`。
     */
    val SHARPEN_AGSL: String = """
        uniform shader uContent;
        uniform float uAmount;

        half4 main(float2 coord) {
            half4 center = uContent.eval(coord);
            // 四邻采样。坐标是设备像素，±1 即相邻像素。
            half4 up    = uContent.eval(coord + float2(0.0, -1.0));
            half4 down  = uContent.eval(coord + float2(0.0,  1.0));
            half4 left  = uContent.eval(coord + float2(-1.0, 0.0));
            half4 right = uContent.eval(coord + float2( 1.0, 0.0));

            half amount = half(uAmount);
            half4 blurred = (up + down + left + right) * 0.25;
            // unsharp: 中心加权 + (中心 - 模糊) * amount，等价于中心 (1+k) / 四邻 -k/4。
            half3 sharpened = center.rgb + (center.rgb - blurred.rgb) * amount;
            // 保留原 alpha：锐化不该改变透明像素的形状。
            return half4(clamp(sharpened, half3(0.0), half3(1.0)), center.a);
        }
    """.trimIndent()
}
