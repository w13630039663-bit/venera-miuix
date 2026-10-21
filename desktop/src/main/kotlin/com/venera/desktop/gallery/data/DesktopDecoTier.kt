package com.venera.desktop.gallery.data

/**
 * 首页装饰的**三档**（批 116）：素 / 纹样 / 神社。
 *
 * ## 为什么这一枚住在 `gallery/data` 而不是 `gallery/ui`
 *
 * 与 [DesktopReaderMode] 同一形（批 87 立的这一层）：一枚**要落盘**的档位枚举连同它的
 * `key`/`label` 与归一那颗住在 data，读写它的那颗偏好件就在同一个包，界面只吃形参。
 * 门控与画法（哪一格属于哪一档、鸟居怎么画、hero 多高）在 `gallery/ui/DesktopShrineDecor.kt` ——
 * 那些要读 Metrics 与 Theme，抬不进 data。
 *
 * ## 三档的分工（稿 `:223-238`）
 *
 * 稿 `:262` 那句注释把分档理由写死了：**素=零装饰；纹样=平面纹样（网点那一层）；
 * 神社=再加建筑剪影、竖排与更高的 hero 与整窗微粒**。键名 `plain`/`pattern`/`shrine`
 * 逐字沿用稿上 `body[data-deco=…]` 的三枚选择器 —— 设计稿是这一族的权威文档，
 * 回核时"键名对不上"要能立刻看出是漂了而不是换了叫法。
 */
enum class DesktopDecoTier(val key: String, val label: String) {
    /** 零装饰：hero 只留背景画与遮罩，rail 只留那七枚域标签。 */
    PLAIN("plain", "素"),

    /** 平面纹样：网点那一层上屏（稿 `.deco` 那一批）。 */
    PATTERN("pattern", "纹样"),

    /** 建筑剪影 + 竖排 + 整窗微粒 + 更高的 hero。 */
    SHRINE("shrine", "神社"),
    ;

    companion object {
        /**
         * 默认档 = 神社。
         *
         * 用户点名的就是这一档（"神社（+ 背景画与鸟居）你按照ui先做"），所以第一帧就该看见它；
         * 另外两档在设置那一屏真能选回去，且**落盘**（[DesktopDecoTierPreferences]）。
         */
        val DEFAULT: DesktopDecoTier = SHRINE
    }
}

/**
 * 盘上那串到档的映射。
 *
 * 认不出来（含 null 与空串）退 [DesktopDecoTier.DEFAULT] —— 与对岸 `readEnum` 同一口径：
 * 存储里可能是被人手工改过的一串，那种时候屏上要有东西，而不是崩或空白。
 * ⚠️ 大小写敏感是刻意的：盘上那串只由屏上那一排按钮写下（`tier.key`），不是人打的字。
 */
fun desktopDecoTierOf(key: String?): DesktopDecoTier =
    DesktopDecoTier.entries.firstOrNull { it.key == key } ?: DesktopDecoTier.DEFAULT

/**
 * 种子顶过盘上那一档。
 *
 * 与 `desktopReaderModeOf`（批 87）同一条次序：取证种子排得比盘上档靠前。
 * 为什么这一格必须有种子：**桌面 OS 级鼠标键盘是自验禁域**，屏上那三枚按钮在交付包里
 * 没法用真点击逐档验证，"三档各一帧"的图证只能由启动参数驱动。
 */
fun desktopDecoTierForScreen(stored: DesktopDecoTier, seed: DesktopDecoTier?): DesktopDecoTier = seed ?: stored

/** 竖排那四字的原串（稿 `.rail .kanji` 的内容）。全仓只许这一处出现这串字面。 */
const val DESKTOP_DECO_RAIL_KANJI_TEXT: String = "博麗神社"

/**
 * 竖排逐字列。
 *
 * 稿 `:701` 把这一条列在"待量（不写成已知）"里：**Compose 文本没有 vertical writing mode 的现成 API**，
 * 它给的两条路是"逐字换行 vs 自绘，各做一次，取可读的那条"。桌面取**逐字各摆一枚**，理由有三：
 * ① 不依赖任何字体或平台的竖排支持（`text-orientation:upright` 在 skiko 上没量过）；
 * ② 每枚字本身不旋转，与稿上 `upright` 的观感一致（竖排汉字本来就正着摆）；
 * ③ 字与字之间的缝由 `DesktopGalleryMetrics.railDecoKanjiGap` 明确给，
 *    而不是靠 `letter-spacing` 在竖排里落到哪个轴向 —— 那一格在 CSS 里是含糊的。
 */
val desktopDecoRailKanji: List<String> = DESKTOP_DECO_RAIL_KANJI_TEXT.map { it.toString() }

/**
 * 一格里按名义步长能摆多少个网点（含边界那一行那一列）。
 *
 * **乘法走 Long**：两轴的枚数各自都是几千到几千万，`Int * Int` 会在 ≈46,340 枚/轴那里绕回成一个小数 ——
 * 而这一颗的用途就是"判断有没有超过上限"，绕回的小数会让上限**静默不生效**（比没有上限更坏：读代码的人以为有兜底）。
 * 桌面今天递不进那么大的面积（窗宽几千 px），但判式 ③ 递得进，而且递进来的那一刻量到的就是绕回。
 * 超界一律**钉在 `Int.MAX_VALUE`**（宁可多算也不少算 ⇒ 宁可在荒谬面积上多放大步长，也不画几万枚点）。
 */
fun desktopDecoDotCount(tilePx: Float, widthPx: Float, heightPx: Float): Int {
    if (tilePx <= 0f || widthPx <= 0f || heightPx <= 0f) return 0
    val perWidth = kotlin.math.floor(widthPx / tilePx).toLong() + 1L
    val perHeight = kotlin.math.floor(heightPx / tilePx).toLong() + 1L
    val product = perWidth * perHeight
    return if (product > Int.MAX_VALUE.toLong()) Int.MAX_VALUE else product.toInt()
}

/**
 * 点阵那一层**实际用的步长**。
 *
 * 名义步长（稿 9px / 11px）在够大的面积上会数出几万枚点，而每枚点是一笔绘制操作；
 * 稿 `:702` 自己把"背景画 + 模糊的内存与帧时间"列在**待量**那一栏，所以这里不赌"应该没事"：
 * 超过上限就把步长**成倍放大**（点数与步长的平方成反比）。只走整数倍是为了点阵仍对齐在同一格上 ——
 * 非整数倍会让点间距忽宽忽窄。
 */
fun desktopDecoDotTileForArea(nominalTilePx: Float, widthPx: Float, heightPx: Float, maxDots: Int): Float {
    if (nominalTilePx <= 0f || maxDots <= 0) return nominalTilePx
    var tile = nominalTilePx
    var multiple = 1
    while (desktopDecoDotCount(tile, widthPx, heightPx) > maxDots && multiple < 32) {
        multiple += 1
        tile = nominalTilePx * multiple
    }
    return tile
}

/** 网点的点径：稿 `:151` 是 `1px` 实心到 `1.7px` 收淡，桌面一枚画出来取中间值。 */
const val DESKTOP_DECO_DOT_RADIUS_PX: Float = 1.35f
