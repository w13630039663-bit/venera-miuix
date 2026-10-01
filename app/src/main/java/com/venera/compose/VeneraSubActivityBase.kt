package com.venera.compose

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.venera.compose.components.VeneraAmbientBackground
import com.venera.compose.feature.VeneraTheme
import java.util.function.Consumer

/**
 * 「跨 Activity 子页」的共用外壳。
 *
 * 抽出来是因为设置二级页也要走真跨 Activity：只有跨过 Activity 边界，
 * targetSdk ≥ 36 的应用才会被系统施加 AOSP 跨 activity 预测式返回动画
 * （缩放 / 圆角 / 遮罩全是系统原值）。单 Activity 内的 NavHost 目的地系统不认为是换页。
 *
 * 每加一个子页就多一份边到边契约 + 防窥订阅 + blur-behind 生命周期，
 * 这些复制成几份必然漂，所以放在基类里。
 */
abstract class VeneraSubActivityBase : ComponentActivity() {

    /**
     * 要不要给内容铺那层**不透明**的氛围光底色。
     *
     * 设置那几页需要它（否则页面没画满的地方会露出平台深色窗口底）。
     * 但"要透出后面那一屏"的页（画廊大图页靠窗口 blur-behind 实时糊后面的列表）必须关掉，
     * 否则系统糊好的东西被我们自己用一层不透明 Canvas 盖死了 —— blur-behind 白挂。
     */
    protected open val opaqueAmbientBackground: Boolean = true

    /**
     * 本窗口 blur-behind 的半径（dp）。
     *
     * 默认那档 18dp 是"内容重度模糊"（打码封面）的口径；整屏都是照片的页面要更糊一档，
     * 否则背景还能认出构图，主体那张图就浮不起来。
     */
    protected open val blurBehindDp: Float get() = BlurBehindDp

    /**
     * 要不要**推迟**挂 blur-behind：不跟着 `onStart` 一起挂，等页面自己调 [armBlurBehind]。
     *
     * 为什么有的页需要它（2026-10-01 12:26 录屏逐帧量出来的）：那一层糊盖住的正是**后面那屏
     * 主界面**，`onStart` 就挂等于"新窗口一出现就把后面糊掉 + 压暗"。而大图页的入场是
     * "图从卡片原位飞过来"，起飞前那一两帧屏上本该**与点击前逐像素相同**——先糊一下再飞，
     * 读起来就是用户连着报了三轮的"打开图片会闪一下"。
     * 读数：点击后主界面静止 250ms（帧间差 0.00），然后**一帧之内**整屏亮度从 174 掉到 106，
     * 再花 350ms 爬回来 —— 那一下就是它。
     *
     * 默认 false = 老行为（设置页那些一进来就该是玻璃底的页照旧）。
     */
    protected open val deferredBlurBehind: Boolean = false

    /** 系统侧此刻允不允许跨窗口模糊（省电模式、开发者选项都可能关掉它）。 */
    private var crossWindowBlurEnabled = false

    /**
     * 本页此刻允不允许挂模糊。推迟型页面在 [armBlurBehind] 之前恒为 false。
     *
     * ⚠️ 初值只能在 `onCreate` 里按 [deferredBlurBehind] 定，**不能**写成属性初始化式：
     * 那是在基类构造期求值的，子类那个 `override val` 还没初始化，读到的必然是 false ——
     * 于是推迟型页面静默退回"立刻挂"，症状与原样一模一样。
     */
    private var blurBehindArmed = true

    /** 跨窗口模糊开关可在运行期被系统改（省电模式、开发者选项），所以监听而不是只读一次。 */
    private val blurEnabledListener = Consumer<Boolean> { enabled ->
        crossWindowBlurEnabled = enabled
        applyBlurBehind(enabled)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 子类属性此刻已初始化（属性初始化在构造函数里，早于 onCreate），读得准。
        blurBehindArmed = !deferredBlurBehind
        // 与 MainActivity 同一份契约：顶部不加 padding，让页面顶栏覆盖状态栏。
        enableEdgeToEdge()
        // 这些子页会露出封面 / 图片，防窥必须一起覆盖，否则「屏蔽与过滤」里那个开关静默失效。
        applySecureScreenPreference()
        setContent {
            if (opaqueAmbientBackground) {
                // VeneraAmbientBackground 必须包在这里：它用 Canvas 铺一层不透明的氛围光底色。
                // 少了它，页面没画满的地方就会露出 Activity 主题
                // （@android:style/Theme.Material.NoActionBar）的平台深色窗口底 —— 表现为
                // 浅色卡片浮在死灰上、深色标题压深色背景。设置页原先在 MainActivity 的
                // NavHost 里就是铺在这层之上，补回来即恢复原样。
                VeneraTheme { VeneraAmbientBackground { SubScreen() } }
            } else {
                VeneraTheme { SubScreen() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        windowManager.addCrossWindowBlurEnabledListener(mainExecutor, blurEnabledListener)
    }

    override fun onStop() {
        windowManager.removeCrossWindowBlurEnabledListener(blurEnabledListener)
        super.onStop()
    }

    /** 本 Activity 要渲染的那一屏。主题与边到边已由基类负责。 */
    @Composable
    protected abstract fun SubScreen()

    /**
     * [deferredBlurBehind] 那一档：页面**入场动作开始的那一刻**调这个，把玻璃底挂上。
     * 幂等 —— 之后再调无副作用（飞行那条路与退路各调一次，谁先到都行）。
     */
    protected fun armBlurBehind() {
        if (blurBehindArmed) return
        blurBehindArmed = true
        applyBlurBehind(crossWindowBlurEnabled)
    }

    /**
     * 挂 / 摘本窗口的背后模糊。设备不支持跨窗口模糊
     * （[WindowManager.isCrossWindowBlurEnabled] 为 false，ColorOS 这类定制 ROM 有可能关）
     * 时直接不挂，退回普通系统转场 —— 不自己画一份假的。
     *
     * 还有一道门：[blurBehindArmed] 为假时一律摘掉（推迟型页面在起飞之前就该露出清晰的主界面）。
     */
    private fun applyBlurBehind(enabled: Boolean) {
        if (!enabled || !blurBehindArmed) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            return
        }
        val radiusPx = (blurBehindDp * resources.displayMetrics.density)
            .toInt()
            // AOSP《Window blurs》建议上限 150px，超了只是白烧 GPU。
            .coerceAtMost(MaxBlurBehindPx)
        // addFlags / clearFlags 在 Window 上，LayoutParams 只有 setBlurBehindRadius。
        val params = window.attributes
        params.blurBehindRadius = radiusPx
        window.attributes = params
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }

    protected companion object {
        /** 半径沿用应用内既有的「内容重度模糊」口径 18dp（打码封面模糊就是这个值）。 */
        const val BlurBehindDp = 18f

        /** AOSP 文档给出的性能建议上限。 */
        const val MaxBlurBehindPx = 150
    }
}
