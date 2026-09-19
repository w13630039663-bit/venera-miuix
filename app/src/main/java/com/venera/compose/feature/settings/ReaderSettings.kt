package com.venera.compose.feature.settings

import androidx.compose.runtime.*
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.reader.ReaderReadingMode

/**
 * 阅读设置。
 *
 * 2026-09-19 设置审计后，这里删掉了 15 条"原版有但我们不打算做"的灰行
 * （设备专属设置、翻页动画、每屏图片数、首图单张、鼠标滚动速度、限制图片宽度、
 * 时间/电池/状态栏/页码、快速收藏图片、自定义图片处理、章节评论两处）。
 * 完整缺口清单与判定依据见 settings-audit-2026-09.md，不要靠设置页当 TODO 列表。
 * 保留的灰行都是"引擎已在、只差一个键"的近期候选。
 */
@Composable
internal fun ReaderSettings(prefs: VeneraPreferences, onBack: () -> Unit, onImages: () -> Unit, onStats: () -> Unit) {
    val mode by prefs.defaultReadingMode.collectAsState()
    val tap by prefs.clickToTurn.collectAsState()
    val volume by prefs.volumeKeyTurn.collectAsState()
    val keepOn by prefs.keepScreenOn.collectAsState()
    val night by prefs.nightFilter.collectAsState()
    val gap by prefs.pageGapDp.collectAsState()
    val crop by prefs.autoCropBorders.collectAsState()
    val autoInterval by prefs.autoScrollPageIntervalSec.collectAsState()
    SettingsPage("阅读", onBack) {
        SettingsGroup("翻页与模式") {
            SettingsSelect("阅读模式", ReaderReadingMode.fromKey(mode).key,
                listOf("HORIZONTAL_LTR" to "翻页（从左到右）", "HORIZONTAL_RTL" to "翻页（从右到左）",
                    "HORIZONTAL_CONTINUOUS" to "连续（从左到右）", "VERTICAL_CONTINUOUS" to "连续（从上到下）",
                    "DOUBLE_PAGE" to "对开双页"), prefs::setDefaultReadingMode)
            SettingsToggle("点击翻页", tap, prefs::setClickToTurn)
            UnsupportedSetting("反转点击翻页方向", "尚无反转偏好及阅读器处理逻辑")
            // 条漫连续流用的是面板里的 px/s 速度（临时态，不持久化）；
            // 这个间隔只对翻页模式（美漫 LTR / 日漫 RTL）的自动巡航生效。
            SettingsSlider("自动翻页间隔", autoInterval, 1f..15f,
                prefs::setAutoScrollPageIntervalSec, steps = 13, suffix = " 秒",
                summary = "仅翻页模式的自动巡航使用；条漫连续流请在阅读器内调滚动速度。")
        }
        SettingsGroup("缩放手势") {
            UnsupportedSetting("双击缩放", "阅读器手势行为尚无可配置的持久化开关")
            UnsupportedSetting("长按缩放", "尚未提供长按缩放偏好")
            UnsupportedSetting("长按缩放位置", "依赖尚未实现的长按缩放功能")
        }
        SettingsGroup("显示") {
            SettingsToggle("音量键翻页", volume, prefs::setVolumeKeyTurn)
        }
        SettingsGroup("图片与评论") {
            UnsupportedSetting("预加载图片数量", "当前固定预加载 5 页，尚无持久化偏好")
        }
        SettingsGroup("现有阅读功能") {
            SettingsSlider("页间距", gap, 0f..32f, prefs::setPageGapDp, steps = 31, suffix = " 像素密度单位")
            SettingsToggle("保持屏幕常亮", keepOn, prefs::setKeepScreenOn)
            SettingsToggle("夜间柔光滤镜", night, prefs::setNightFilter)
            SettingsToggle("自动裁剪白边", crop, prefs::setAutoCropBorders,
                summary = "已保存偏好，阅读器尚未接入裁剪处理，暂不支持", enabled = false)
            SettingsAction("单页与插图收藏", onClick = onImages)
            SettingsAction("阅读足迹与统计", onClick = onStats)
        }
    }
}
