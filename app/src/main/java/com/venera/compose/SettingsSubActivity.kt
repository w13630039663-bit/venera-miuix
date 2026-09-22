package com.venera.compose

import android.os.Bundle
import androidx.compose.runtime.Composable
import com.venera.compose.feature.SettingsSubScreen
import com.venera.compose.feature.VeneraSettingsSubHost

/**
 * 设置子页的统一宿主。
 *
 * 7 个分区与各分区下的叶子页共用这一个类，靠 [EXTRA_SCREEN] 分发 —— 关键是每一屏都
 * **真的换一个 Activity**，系统才会给这一跳施加 AOSP 跨 activity 预测式返回动画。
 * 写在同一个 Activity 里（哪怕自带一套内部页栈）系统都不认为是换页，既拿不到那套动画，
 * 也拿不到本窗口的 blur-behind。
 *
 * 边到边契约、防窥、返回模糊全部由 [VeneraSubActivityBase] 提供，与设置主页同一份。
 */
class SettingsSubActivity : VeneraSubActivityBase() {

    /** 不给兜底默认值：真要漏了 extra，就让 resolveScreen 那条 error 炸出来。 */
    private lateinit var screen: SettingsSubScreen
    private var arg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        arg = intent.getStringExtra(EXTRA_ARG)
        screen = resolveScreen(intent.getStringExtra(EXTRA_SCREEN))
        super.onCreate(savedInstanceState)
    }

    /** extra 缺失只会来自编码错误：宁可立刻炸出来，也不要静默停在错误的页上。 */
    private fun resolveScreen(name: String?): SettingsSubScreen {
        val resolved = SettingsSubScreen.entries.firstOrNull { it.name == name }
            ?: error("未知的设置子页屏名: $name")
        if (resolved == SettingsSubScreen.BLOCKING_RULES) {
            require(!arg.isNullOrBlank()) { "BLOCKING_RULES 需要规则类型 extra" }
        }
        return resolved
    }

    @Composable
    override fun SubScreen() = VeneraSettingsSubHost(screen, arg)

    companion object {
        const val EXTRA_SCREEN = "settings_sub_screen"
        /** 少数屏需要额外参数（目前只有 BLOCKING_RULES 的规则类型）。 */
        const val EXTRA_ARG = "settings_sub_arg"
    }
}
