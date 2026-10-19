package com.venera.compose.gallery.domain

/**
 * 大图页**离场**那一趟（画面框 → 墙上那张卡）的单帧几何与透明度算式。
 *
 * ## 为什么单独抽出来
 *
 * 原来这些算式写死在 `GalleryPostScreen` 的 `drawBehind` / `graphicsLayer` lambda 里，进度只由
 * 一颗 `Animatable` 喂（松手之后才开始走）。要做成**跟手**（手势进度直驱、中途松开能原路回弹），
 * 前提就是"给定进度出给定画面"这件事得是一颗不依赖动画驱动、也不依赖 Compose 类型的纯函数：
 * 固定弹簧和手指直驱两条路读同一颗算式，才不会各写一遍、各错一遍。
 *
 * ## `progress` 的语义（这一颗文件的轴）
 *
 * `0 = 飞行体还站在画面框原位`，`1 = 完全收进卡片`。与 pixez 那条 `expansion`（0=缩在卡片、
 * 1=铺满容器）**同轴反向**，对读时换算 `1 - p` 即可。
 *
 * ## 刻意不碰 Compose / Android 任何类型
 *
 * 与 `GalleryFlyGeometry.kt` 同一条口径：调用侧自己把 [FlyRect] 翻成 `IntOffset`/`IntSize`，
 * 这样它能脱离 gradle 单跑（`_probe/l0/run-judgment-tests.sh`），手边没设备时它是这条落点算式
 * 唯一能跑的回归证据。
 *
 * ## 为什么几何钳进度、透明度用原始进度
 *
 * 这是**原来那两个写法**，不是笔误：`drawBehind` 里那条插值先 `coerceIn(0f, 1f)`，而页面淡出那条
 * 直接吃 `exitFly.value` 再把结果钳进 0..1。合流成一处会改掉两端各自越界时的观感，所以逐位留着。
 *
 * ## 帧里为什么**不装** alpha
 *
 * 幕布那一层与整页在原来那两处读的是**同一个** `exitFadeAlpha()`（一个真相）。把页面 alpha
 * 与"幕布 alpha"拆成帧里两格就是凭空造第二个真相 —— 需要它的时候应当是**语义真的分叉**那天。
 *
 * 那个"分叉那天"就是 2026-10-19：页面让开之后，飞行体在**末段**要自己淡出（[exitBodyAlpha]）。
 * 它仍然不进 [GalleryExitFrame] —— 帧是几何，alpha 是时间轴上的两条曲线，混进同一颗 data class
 * 会让"给定进度出给定形状"这条判据被一个与形状无关的读数污染。
 */

/** 离场飞行体在给定进度上的绘制参数：画到哪（[dst]）、取源位图哪一块（[source]）、圆角缩多少。 */
data class GalleryExitFrame(
    val dst: FlyRect,
    val source: FlySourceRect,
    /**
     * 圆角的**等比补偿**（批 B③ 换锚，2026-10-19）。
     *
     * 旧值是 `dst.width / to.width`（当帧框相对**终点框**缩了多少）—— 那是一把借来的尺：
     * 只在"起点与终点同宽"的整屏场景里恰好等于内容缩放比。批 B② 把离场起点换成
     * **当前那一页图块**之后起点与终点宽高都不同，宽比就此与缩放比分家。
     *
     * 新值是 `dst.width / source.width`（当帧框宽 ÷ cover 之后那一块源宽）= 内容真的被缩放了多少。
     * 两枚在旧场景下数值相等（等价改写，旧尺不会红），新场景下各自正确。
     *
     * 档位（哪一枚圆角）由调用侧插值后乘这一枚，"哪一档"与"缩多少"从此是两件事。
     */
    val cornerRadiusScale: Float,
)

/** 纯 Kotlin 的矩形（左上角 + 宽高），免得 domain 层沾上 Compose 类型。 */
data class FlyRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * **离场**时做交叉淡出的那一段（占整条返回程的比例）。原来住 `GalleryPostScreen` 的私有常量区，
 * 搬进来是因为算式与口径得分给脱 gradle 的那趟单跑看见 —— 否则判据只能验到"函数自洽"，
 * 验不到"分数真的是原来那枚"。
 *
 * 去程曾经也用它（把页面的淡入挂在 `fly` 尾段上），2026-10-01 录屏把它量掉了：弹簧的"百分比"
 * 不是"路程"，`fly=0.75` 时飞行体还在半路，交叉淡入就变成**重影 + 暗一下**（那一段现在由
 * `GalleryHandoff.kt` 那组算式守）。返回程没有这个问题 —— 离场是"起点现截一帧、原地起飞"，
 * 飞行体与页面在首帧**逐像素相同**，交叉淡出不会露馅。
 */
const val EXIT_FLIGHT_FADE_FRACTION = 0.25f

/**
 * 离场第 [progress] 成时，页面与幕布该让开到什么程度。
 *
 * 输入是**原始**进度（不钳）：与搬过来之前那句 `(1f - exitFly.value / FLIGHT_FADE_FRACTION).coerceIn(0f, 1f)`
 * 逐位同形，连"结果钳而输入不钳"那一处也一起留着。
 */
fun exitPageAlpha(progress: Float, fadeFraction: Float = EXIT_FLIGHT_FADE_FRACTION): Float =
    (1f - progress / fadeFraction).coerceIn(0f, 1f)

/**
 * **末段**飞行体自己淡出的那一段（占整条返回程的比例）。
 *
 * ## 为什么末段必须淡（2026-10-19 真机帧证）
 *
 * 离场的落点是"把我送进来那一侧"回写的矩形，而**那一侧自己就画着同一张图** ——
 * 从播放器退回作品页时，作品页那张图在整条返回程里一直是全亮的。
 * 于是飞行体不是"飞回去"，是**一块不透明的同一张图压在同一张图上滑过去**：
 * 起点框 1080×1386、落点框 1080×1877，两者套在一起，中途每一帧都是两份比例不同的
 * 同一张画叠着（真机帧：返回第 12 帧，下面那屏已经全亮，飞行体才走了一小段）。
 *
 * 页面那一半（[exitPageAlpha]）救不了它：页面的 alpha 早在 25% 就归零了，剩下的路是飞行体
 * 独自走完的。所以要让**飞行体自己**在末段化进落点那一份 —— 落点那份一直在屏上、位置也对，
 * 两帧叠齐之后再撤掉飞行体，收尾才没有第二次落位。
 *
 * ## 为什么不是整程都淡
 *
 * 首程不能淡：那一段屏上**只有**飞行体（页面还没让干净），一淡就是"图还没飞就先虚了"。
 * 所以起步点钉在**页面让开的终点**（`1 - EXIT_FLIGHT_FADE_FRACTION = 0.75` 那段之前恒为 1）——
 * 两条曲线首尾相接、**不重叠**：页面先让开，飞行体接着化进落点。
 *
 * 2026-10-19 真机先取过"后 40% 才淡"（`0.4`）：返回中段（进度 0.5 附近）那两份同一张画
 * 还是一份全不透明地压在另一份上，标题文字与卡片圆角都读得出两层。往前挪到首尾相接之后，
 * 中段那份只有 0.67，两层的错位边只剩一半对比，读起来是"对上"而不是"叠着"。
 *
 * ## 为什么与"去程不许淡"那条不冲突
 *
 * 去程那条（2026-10-01 录屏）说的是**两层同时半透明会相乘变暗**：页面淡入 + 飞行体淡出，
 * 总不透明度掉到 0.75，图先暗一下。离场末段没有这个乘积 —— 页面早已是 0，飞行体下面垫着的
 * 是落点那一侧**自己画的**图，淡出只是在把两份同一张画并成一份。
 */
const val EXIT_BODY_FADE_FRACTION = 0.75f

/**
 * 离场第 [progress] 成时，飞行体自己该有多不透明。
 *
 * 输入是**原始**进度（不钳），与 [exitPageAlpha] 同一口径：弹簧回弹过头那一档（>1）照样
 * 归零，不许因为"越界"又亮回来。
 */
fun exitBodyAlpha(progress: Float, fadeFraction: Float = EXIT_BODY_FADE_FRACTION): Float =
    ((1f - progress) / fadeFraction).coerceIn(0f, 1f)

/**
 * 离场第 [progress] 成那一帧飞行体该怎么画。
 *
 * 矩形是 [from] 与 [to] 的**逐边线性插值**，所以：
 * - `progress = 0` 逐像素等于起点框（离场第一帧与屏上那一帧看起来一样，这是这一层最初的职责）；
 * - `progress = 1` 逐像素等于 [to]（卡片封面那一块），落点归位不需要收尾补偿。
 *
 * 源像素走 cover 语义（复用 [coverSourceRect]，已有单测）：起点框与终点框比例未必同档，
 * 拉伸会让交棒读起来像"闪"。
 *
 * ## 圆角（批 B③ 换锚，2026-10-19）
 *
 * 旧锚是 `dst.width / to.width` —— "当帧框相对终点框缩了多少"。它在**整屏起点**那一族里是对的
 * （`from` 与 `to` 宽相同、只有高在变 ⇒ 宽比恰好等于内容缩放比），可它是一把**借来的尺**：
 * 一旦起点不再是整屏（批 B② 把起点换成当前那一页图块，`from` 与 `to` 宽高都不同），
 * 宽比与内容缩放比就此分家，圆角会在中途软掉/硬掉。
 *
 * 新锚换成**内容真的被缩放了多少**（cover 那颗 `scale`，`maxOf(宽比, 高比)` 两向恒等，
 * `maxOf` 只为吸收源块取整的 1px 漂移）。与旧式的关系由 `_probe/l0/probe_scale.kt` 实测：
 * 同宽族（整屏起点）两式相等 ⇒ **等价改写**；异宽族（批 B② 之后的页块起点）分家，
 * 新式量的是"内容缩多少"（圆角该跟着缩的那枚），旧式量的是"相对终点缩多少"。
 * 档位本身仍由调用侧插值（两档之间随路程走）再乘这一枚 —— 从此"哪一档"与"缩多少"两件事分开，
 * 与 [com.venera.compose.gallery.domain.flightFrame] 的 `clipRadius` 同一条口径。
 *
 * @param from 起点框（批 B② 之后：当前那一页图块）。
 * @param to 终点框（墙上那张卡的落点）。
 * @param sourceWidthPx 截屏那张位图的像素宽。
 * @param sourceHeightPx 截屏那张位图的像素高。
 */
fun exitExpansion(
    progress: Float,
    from: FlyRect,
    to: FlyRect,
    sourceWidthPx: Int,
    sourceHeightPx: Int,
): GalleryExitFrame {
    val t = progress.coerceIn(0f, 1f)
    val dst = FlyRect(
        left = from.left + (to.left - from.left) * t,
        top = from.top + (to.top - from.top) * t,
        width = from.width + (to.width - from.width) * t,
        height = from.height + (to.height - from.height) * t,
    )
    val source = coverSourceRect(sourceWidthPx, sourceHeightPx, dst.width, dst.height)
    // 批 B③：补偿换成**内容真的被缩放了多少**。
    //
    // cover 的定义就是"源块放大后恰好盖满 dst"，所以 `dst宽÷源宽` 与 `dst高÷源高`
    // **恒等**（同是 cover 那颗 `scale`，实测见 `_probe/l0/probe_scale.kt`：两向都是 0.3448）。
    // 取 `maxOf` 不是为选大，是为**吸收 `roundToInt` 的取整漂移**（源块是 Int，两向可能差 1px）。
    //
    // ⚠️ **不钳上界**（旧式有 `coerceIn(0f, 1f)`，那一条跟着旧式一起退休）：落点比起点还宽
    // 那一档（宽屏作品，见 `GalleryExitExpansionTest` 第三档用例）内容是被**放大**的
    // （scale 1.5），圆角补偿必须跟着放大 —— 钳成 1 会让那一档的圆角比内容细一圈。
    // 只钳下界：负面积/零源宽不许产出负数。
    val scaleX = dst.width / source.width.toFloat().coerceAtLeast(1f)
    val scaleY = dst.height / source.height.toFloat().coerceAtLeast(1f)
    return GalleryExitFrame(
        dst = dst,
        source = source,
        cornerRadiusScale = maxOf(scaleX, scaleY).coerceAtLeast(0f),
    )
}
