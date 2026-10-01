package com.venera.compose.gallery.ui

import android.app.Activity
import android.graphics.Bitmap
import android.view.PixelCopy
import android.graphics.Rect as AndroidRect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 起点那一帧的读数档位。**四档不同脸**，因为调用方要按档位决定退不退路：
 *
 * - [NONE]：这一趟压根没截（深链直接进大图页、从收藏那些没有"墙上那一张卡"的入口）——
 *   对面**不该等**，立刻走"整页抬上来"。
 * - [WAITING]：截帧在路上（[PixelCopy] 是异步的，约一到数帧）。对面等到这档结束才决定飞不飞。
 * - [READY]：像素拿到了，[GalleryFlyIn.payload] 与 [origin] 都非空 —— 飞。
 * - [FAILED]：窗口不给像素（超出窗口边界、无 surface、超时、认不出 Activity）——
 *   老老实实退回抬页，**并且日志里说得出是哪一种**：静默交回 null 会让"这屏为什么不飞"
 *   变成只能靠猜的问题（2026-10-01 那一版就是这么丢的，见 [GalleryFlyIn.capture] 的头注）。
 */
enum class GalleryFlyInStage { NONE, WAITING, READY, FAILED }

/**
 * 卡片 → 大图页的**占位那一帧**：那张卡此刻的像素。
 *
 * 为什么自己画而不用系统那套：翻了本机 SDK 的 `platforms/android-{33,34,35,36}/android.jar`，
 * `android.window` 里只有启动图那套（`SplashScreen` / `SplashScreenView` /
 * `OnExitAnimationListener`），**没有** `SplashScreenViewProvider`、也没有
 * `overrideNextTransitionSplashScreenStartingPoint` —— 跨 Activity 的"共享图像"能力这里够不着。
 * 剩下的经典 View scene transition 要**真的 View** 带 `transitionName`，
 * 而卡片是 Composable，框架会去搬整个 `ComposeView`（不是一张图）。所以只能自带一层。
 *
 * 与 §九 被否掉的"一次性截图当背景"不是一回事：背景要的是**活的**那一屏（交给窗口
 * blur-behind 实时糊），而这里要的**本来就是起点那一帧**，冻结才是对的。
 * 2026-09-29 第四轮起它多带一个 [origin]：那一帧不再只是"垫着不空"，而是**从卡片原位
 * 飞到大图位置**的那个飞行体 —— 跨窗口做不了真共享元素，那就把它的两样输入
 * （起点那一帧 + 起点矩形）自己递过去，落地后交叉淡入真图，观感同一条。
 *
 * 只截**起点那一块矩形**，不占整屏内存。
 *
 * ## 起点是"封面那一块"，不是"整张卡"（2026-10-01 12:00 用户报"包裹还会闪一下"）
 *
 * 落点是大图页里按真实比例定出来的**画面框**（只有图）。所以起点也必须是**同一张图的那一块**：
 * 早期版本把 `GalleryPostCard` 整卡的矩形递进来，卡上那条标题与卡片面板的圆角就一起被截进
 * 飞行体，落到位时整块"包裹"被放大铺在画面框里，交棒那一刻消失 —— 那就是"闪"。
 * 判据一句话：**起点与终点要同构**（同一比例的一张图 → 同一比例的一张图）。
 * 顺带一提，页内那对真共享元素（`coverSharedElement`）挂的本来就是封面，这下两套口径一致了。
 *
 * ## 为什么从 `view.draw(canvas)` 换成 `PixelCopy`（2026-10-01，读数钉死）
 *
 * 原来那一版是"把卡片所在的 ComposeView 画进一张软件位图"。真机读数是：
 * `IllegalArgumentException: Software rendering doesn't support RuntimeShader`
 * —— 卡面上的**液态玻璃**（`textureBlur` 走 `RuntimeShader`）在软件 canvas 上回放不了，
 * 异常被 `runCatching` 吞掉，`payload`/`origin` 双双不置，于是"点墙上的图"永远不飞。
 * 这不是偶发：只要「界面材质 = 液态玻璃」在开，**每一张卡都抛**。
 *
 * [PixelCopy] 要的是**窗口已经合成好的那一块表面**，所以玻璃、遮罩、圆角全都是
 * 画完之后的真像素 —— 它不需要懂 Compose 的绘制树，也就不会被任何一个 shader 卡住。
 * 附带两条它比 `view.draw` 更正的地方：
 * - 打码那张卡截下来的是**屏上那层遮罩的样子**（安全边界由"截的是像素"这件事本身保证，
 *   不靠调用方记得判 `masked`）；
 * - 动画中的那一帧（正在淡入、正在位移）也能截到，`view.draw` 截到的是回放姿态。
 *
 * 代价是**异步**：像素约在一到数帧之后才回来，所以对面不能"首次组合时看一眼就定案"，
 * 要等 [GalleryFlyInStage] 离开 [WAITING] 再决定（改法见 `GalleryPostScreen` 那一处）。
 */
object GalleryFlyIn {

    /** 起点那一帧的读数。对面按它决定"飞"还是"抬页"，其中 [WAITING] 是唯一要等的一档。 */
    var stage by mutableStateOf(GalleryFlyInStage.NONE)
        private set

    var payload by mutableStateOf<ImageBitmap?>(null)
        private set

    /**
     * 起点那一块的矩形（**卡片里封面那一块**，见文件头注）—— 共享元素那一头要的"从哪儿飞过来"。
     *
     * 两个窗口都是 edge-to-edge 全屏（`VeneraSubActivityBase` 与 MainActivity 同一套），
     * 所以这一串坐标在对面那扇窗口里可以直接用。真机上若发现整体偏了一档状态栏高度，
     * 问题就在这一条假设上，改这里而不是在对面加偏移。
     *
     * 与 [payload] 同时写入（成功回调里一起给），所以对面只要看到 [READY] 就能两样一起用。
     */
    var origin by mutableStateOf<Rect?>(null)
        private set

    /**
     * 在点击那一刻调用。[coverBounds] 必须是**窗口坐标**
     * （`Modifier.onGloballyPositioned { it.boundsInWindow() }`，量的是卡片里的**封面**，
     * 不是整张卡 —— 理由见文件头注），而 [view] 传 `LocalView.current`
     * —— 两者同一坐标系，要的那一块才落在窗口表面的对位上。
     *
     * 立刻把 [stage] 置 [WAITING]（同帧），像素回来后再置 [READY] 或 [FAILED]：
     * 对面那一头等的就是这一次翻转，所以这里**任何一条出口都必须离开 WAITING**，
     * 否则那一页会白等到超时才动。
     */
    fun capture(view: View, coverBounds: Rect) {
        val width = coverBounds.width.toInt()
        val height = coverBounds.height.toInt()
        val window = (view.context as? Activity)?.window
        if (window == null || width <= 0 || height <= 0) {
            Log.w(TAG, "不截：window=${window != null} size=${width}x$height bounds=$coverBounds")
            fail()
            return
        }
        stage = GalleryFlyInStage.WAITING
        // 矩形**同步**先给（它不需要像素）：对面那一头靠它立刻分清"我是从一张卡点进来的"
        // 与"深链/收藏那些压根没有卡片当起点的入口"，两种入场动画在首次组合就要分岔，
        // 等像素回来再分的话会先按错的那一档摆一帧。
        origin = coverBounds
        val source = AndroidRect(
            coverBounds.left.toInt(),
            coverBounds.top.toInt(),
            coverBounds.right.toInt(),
            coverBounds.bottom.toInt(),
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // 回调线程要给主线程：payload 是快照状态，从别的线程写会让读侧不自洽。
        PixelCopy.request(window, source, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                payload = bitmap.asImageBitmap()
                stage = GalleryFlyInStage.READY
            } else {
                // 这几种是站得住的失败：超出窗口边界（卡片只露半截）、窗口还没 surface、超时。
                // 都不飞，退路照常 —— 但屏上看不出成因，所以只有这里说得出是哪一种。
                Log.w(TAG, "PixelCopy 没给像素 result=$result bounds=$coverBounds")
                fail()
            }
        }, Handler(Looper.getMainLooper()))
    }

    /** 大图那一档到位就交还，别把这张位图一直攥在手里。 */
    fun consume() {
        payload = null
        origin = null
        stage = GalleryFlyInStage.NONE
    }

    /**
     * 一级那一侧"这张卡现在在哪儿"的实时读数：`uid` → 卡片里**封面那一块**的窗口矩形。
     *
     * 只给**返回程**用（去程的两样输入是点击那一刻现递的）。返回时大图页并不知道墙上那张卡
     * 此刻在屏幕的哪儿 —— 而它必须知道，否则"飞回去"没有落点。
     *
     * 为什么敢直接读后台那一屏的值：大图页压上来之后 MainActivity 只是 `onStop`，
     * 它的 View 树与布局**原封不动**地留在那儿（人又没法在大图页里滚那一面墙），
     * 所以这份读数就是"离开那一刻的位置"，正好是返回要的落点。
     * 卡片被 LazyGrid 回收（滚出屏幕）时由卡片自己 [forgetCardBounds] 撤掉 ——
     * 留着一条过期矩形比"这一次不飞"糟得多。
     *
     * 刻意**不是** Compose 状态：写它的人在大图页下面那一屏、读它的人在大图页，
     * 两边不在同一次组合里，做成 state 只会白拖一次重组。
     */
    private val cardBounds = HashMap<String, Rect>()

    /** 一级的卡片在布局时回写（`onGloballyPositioned` 每帧一次，同一 uid 覆盖）。 */
    fun noteCardBounds(uid: String, bounds: Rect) {
        cardBounds[uid] = bounds
    }

    /** 卡片离开组合（滚出屏幕、换了一批）时撤掉自己的落点。 */
    fun forgetCardBounds(uid: String) {
        cardBounds.remove(uid)
    }

    /** 返回程的落点。null = 墙上现在没有这一张（翻到别处去了）→ 这一次不飞。 */
    fun cardBoundsOf(uid: String): Rect? = cardBounds[uid]

    /**
     * 截取窗口里 [bounds] 那一块像素，**一次性**交给调用方 —— 不写任何 state。
     *
     * 与 [capture] 的分工：那一份是"去程起点"，要跨 Activity 递过去所以得挂在对象上；
     * 这一份是"返回程起点"，就地用、用完就丢（离场这一趟没有第二个读者）。
     *
     * 起点为什么值得截屏而不是让页面自己缩小：截的是**屏上已经画好的那一帧**，
     * 所以离场第一帧的飞行体与它盖住的那张图**逐像素相同** —— 页面淡出期间看不出任何替换，
     * 这正是返回程不需要"淡入"这一半的原因。
     */
    fun captureRegion(view: View, bounds: Rect, onResult: (ImageBitmap?) -> Unit) {
        val width = bounds.width.toInt()
        val height = bounds.height.toInt()
        val window = (view.context as? Activity)?.window
        if (window == null || width <= 0 || height <= 0) {
            Log.w(TAG, "离场不截：window=${window != null} size=${width}x$height bounds=$bounds")
            onResult(null)
            return
        }
        val source = AndroidRect(
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.right.toInt(),
            bounds.bottom.toInt(),
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(window, source, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                onResult(bitmap.asImageBitmap())
            } else {
                Log.w(TAG, "离场 PixelCopy 没给像素 result=$result bounds=$bounds")
                onResult(null)
            }
        }, Handler(Looper.getMainLooper()))
    }

    /** 失败这一档不留位图，也不留矩形 —— 对面看 stage 就够了。 */
    private fun fail() {
        payload = null
        origin = null
        stage = GalleryFlyInStage.FAILED
    }
}

private const val TAG = "GalleryFlyIn"
