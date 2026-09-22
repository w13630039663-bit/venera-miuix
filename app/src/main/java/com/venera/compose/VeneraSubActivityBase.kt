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

    /** 跨窗口模糊开关可在运行期被系统改（省电模式、开发者选项），所以监听而不是只读一次。 */
    private val blurEnabledListener = Consumer<Boolean> { applyBlurBehind(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 与 MainActivity 同一份契约：顶部不加 padding，让页面顶栏覆盖状态栏。
        enableEdgeToEdge()
        // 这些子页会露出封面 / 图片，防窥必须一起覆盖，否则「屏蔽与过滤」里那个开关静默失效。
        applySecureScreenPreference()
        setContent {
            // VeneraAmbientBackground 必须包在这里：它用 Canvas 铺一层不透明的氛围光底色。
            // 少了它，页面没画满的地方就会露出 Activity 主题
            // （@android:style/Theme.Material.NoActionBar）的平台深色窗口底 —— 表现为
            // 浅色卡片浮在死灰上、深色标题压深色背景。设置页原先在 MainActivity 的
            // NavHost 里就是铺在这层之上，补回来即恢复原样。
            VeneraTheme {
                VeneraAmbientBackground { SubScreen() }
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
     * 挂 / 摘本窗口的背后模糊。设备不支持跨窗口模糊
     * （[WindowManager.isCrossWindowBlurEnabled] 为 false，ColorOS 这类定制 ROM 有可能关）
     * 时直接不挂，退回普通系统转场 —— 不自己画一份假的。
     */
    private fun applyBlurBehind(enabled: Boolean) {
        if (!enabled) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            return
        }
        val radiusPx = (BlurBehindDp * resources.displayMetrics.density)
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
