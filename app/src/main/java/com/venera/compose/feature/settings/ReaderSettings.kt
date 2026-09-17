package com.venera.compose.feature.settings

import androidx.compose.runtime.*
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.reader.ReaderReadingMode

/** 原版四个分组及顺序保持不变；仅用阅读器真正消费的偏好值。 */
@Composable
internal fun ReaderSettings(prefs: VeneraPreferences, onBack: () -> Unit, onImages: () -> Unit, onStats: () -> Unit) {
    val mode by prefs.defaultReadingMode.collectAsState()
    val tap by prefs.clickToTurn.collectAsState()
    val volume by prefs.volumeKeyTurn.collectAsState()
    val keepOn by prefs.keepScreenOn.collectAsState()
    val night by prefs.nightFilter.collectAsState()
    val gap by prefs.pageGapDp.collectAsState()
    val crop by prefs.autoCropBorders.collectAsState()
    SettingsPage("阅读", onBack) {
        SettingsGroup {
            UnsupportedSetting("启用设备专属设置", "当前只有全局阅读偏好，未实现按设备覆盖与重置")
        }
        SettingsGroup("翻页与模式") {
            SettingsSelect("阅读模式", ReaderReadingMode.fromKey(mode).key,
                listOf("HORIZONTAL_LTR" to "翻页（从左到右）", "HORIZONTAL_RTL" to "翻页（从右到左）",
                    "HORIZONTAL_CONTINUOUS" to "连续（从左到右）", "VERTICAL_CONTINUOUS" to "连续（从上到下）",
                    "DOUBLE_PAGE" to "对开双页"), prefs::setDefaultReadingMode)
            UnsupportedSetting("翻页（从上到下）／连续（从右到左）", "原版这两种阅读模式尚未实现")
            SettingsToggle("点击翻页", tap, prefs::setClickToTurn)
            UnsupportedSetting("反转点击翻页方向", "尚无反转偏好及阅读器处理逻辑")
            UnsupportedSetting("翻页动画", "当前动画不可配置")
            UnsupportedSetting("自动翻页间隔", "当前没有自动翻页计时器与偏好")
            UnsupportedSetting("横屏每屏图片数（仅翻页模式）", "当前仅有固定对开模式，尚无每方向图片数偏好")
            UnsupportedSetting("竖屏每屏图片数（仅翻页模式）", "当前没有竖屏图片数偏好")
            UnsupportedSetting("第一页只显示单张图片", "当前没有首图独立排版偏好")
            UnsupportedSetting("鼠标滚动速度", "当前使用系统滚动行为，没有速度倍率偏好")
        }
        SettingsGroup("缩放手势") {
            UnsupportedSetting("双击缩放", "阅读器手势行为尚无可配置的持久化开关")
            UnsupportedSetting("长按缩放", "尚未提供长按缩放偏好")
            UnsupportedSetting("长按缩放位置", "依赖尚未实现的长按缩放功能")
        }
        SettingsGroup("显示") {
            UnsupportedSetting("限制图片宽度", "纵向连续模式尚无宽度限制偏好")
            SettingsToggle("音量键翻页", volume, prefs::setVolumeKeyTurn)
            UnsupportedSetting("显示时间与电池信息", "阅读器没有对应显示偏好")
            UnsupportedSetting("显示系统状态栏", "沉浸式显示尚未接入此偏好")
            UnsupportedSetting("显示页码", "阅读器页码目前不可单独配置")
        }
        SettingsGroup("图片与评论") {
            UnsupportedSetting("快速收藏图片", "已有图片收藏，但没有双击或滑动快捷收藏偏好")
            UnsupportedSetting("自定义图片处理", "尚未接入图片处理脚本编辑器与启用开关")
            UnsupportedSetting("预加载图片数量", "当前没有可配置的预加载数量")
            UnsupportedSetting("显示章节评论", "已有章节评论入口，但没有默认显示开关")
            UnsupportedSetting("在章节末尾显示评论", "当前没有章末评论页面")
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
