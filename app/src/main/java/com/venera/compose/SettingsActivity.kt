package com.venera.compose

import androidx.compose.runtime.Composable
import com.venera.compose.feature.VeneraSettingsHost

/**
 * 设置主页的独立 Activity。
 *
 * 存在的唯一理由：预测式返回的「跨 activity」那套动画只在**真的跨过 Activity 边界**时
 * 由系统施加。Android 16（API 36）起 targetSdk ≥ 36 的应用默认启用 back-to-home /
 * cross-task / cross-activity 三套系统动画，本应用 targetSdk 37 落在里面；而单 Activity
 * 内的 NavHost 目的地系统不认为是换页，只能自绘复刻。走真系统路的收益是缩放、圆角、
 * 遮罩的节奏与参数全是系统原值，我们不写一个数字。
 *
 * 边到边契约、防窥、返回模糊（blur-behind）都在 [VeneraSubActivityBase] 里，
 * 设置二级页的 [SettingsSubActivity] 共用同一份。
 */
class SettingsActivity : VeneraSubActivityBase() {
    @Composable
    override fun SubScreen() = VeneraSettingsHost()
}
