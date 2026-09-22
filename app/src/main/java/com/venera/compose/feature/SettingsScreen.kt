package com.venera.compose.feature

import androidx.compose.runtime.Composable
import com.venera.compose.feature.settings.SettingsHome

/**
 * 设置主页的对外契约。
 *
 * 原先还带 8 个「往下钻到叶子页」的回调，那是页面内部栈时代的接线；分区与叶子页现在
 * 都由 [com.venera.compose.SettingsSubActivity] 跨 Activity 承载，跳转在子页宿主里
 * 就地构造（见 [VeneraSettingsSubHost]），这里只剩一个返回。
 */
@Composable
fun AndroidSettingsScreen(onBack: () -> Unit = {}) = SettingsHome(onBack = onBack)
