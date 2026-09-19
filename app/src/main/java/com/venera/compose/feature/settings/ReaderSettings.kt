package com.venera.compose.feature.settings

import androidx.compose.runtime.*
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.reader.ReaderReadingMode

/**
 * 阅读设置。
 *
 * 2026-09-19 设置审计后：预加载数、反转点击方向、自动翻页间隔从灰行转正；
 * 删掉 15 条"需要新子系统"的灰行（设备专属、翻页动画、每屏图数、首图单张、
 * 鼠标滚速、限宽、时间/状态栏/页码、快速收藏、自定义图片处理、章节评论两处）；
 * 剩下的未实现项收进底部「尚未实现」折叠区。原先那个"看得到、点不动"的
 * 自动裁剪白边开关也移了进去 —— 不保留无效开关。
 * 完整判据与清单见 settings-audit-2026-09.md。
 */
@Composable
internal fun ReaderSettings(prefs: VeneraPreferences, onBack: () -> Unit, onImages: () -> Unit, onStats: () -> Unit) {
    val mode by prefs.defaultReadingMode.collectAsState()
    val tap by prefs.clickToTurn.collectAsState()
    val reverseTap by prefs.reverseTapDirection.collectAsState()
    val volume by prefs.volumeKeyTurn.collectAsState()
    val keepOn by prefs.keepScreenOn.collectAsState()
    val night by prefs.nightFilter.collectAsState()
    val gap by prefs.pageGapDp.collectAsState()
    val autoInterval by prefs.autoScrollPageIntervalSec.collectAsState()
    val preload by prefs.preloadImageCount.collectAsState()
    SettingsPage("阅读", onBack, largeTitle = "阅读设置") {
        SettingsGroup("翻页与模式") {
            SettingsSelect("阅读模式", ReaderReadingMode.fromKey(mode).key,
                listOf("HORIZONTAL_LTR" to "翻页（从左到右）", "HORIZONTAL_RTL" to "翻页（从右到左）",
                    "HORIZONTAL_CONTINUOUS" to "连续（从左到右）", "VERTICAL_CONTINUOUS" to "连续（从上到下）",
                    "DOUBLE_PAGE" to "对开双页"), prefs::setDefaultReadingMode)
            SettingsToggle("点击翻页", tap, prefs::setClickToTurn)
            SettingsToggle("反转点击翻页方向", reverseTap, prefs::setReverseTapDirection,
                summary = "左右半屏语义互换，方便左利手。与「从右到左」模式的镜像互不干扰，可叠加。")
            // 条漫连续流用的是阅读器面板里的 px/s 速度（临时态，不持久化）；
            // 这个间隔只对翻页模式（美漫 LTR / 日漫 RTL）的自动巡航生效。
            SettingsSlider("自动翻页间隔", autoInterval, 1f..15f,
                prefs::setAutoScrollPageIntervalSec, steps = 13, suffix = " 秒",
                summary = "仅翻页模式的自动巡航使用；条漫连续流请在阅读器内调滚动速度。")
        }
        SettingsGroup("图片") {
            SettingsSlider("页间距", gap, 0f..32f, prefs::setPageGapDp, steps = 31, suffix = " 像素密度单位")
            SettingsSlider("预加载图片数量", preload.toFloat(), 0f..20f,
                { prefs.setPreloadImageCount(it.toInt()) }, steps = 19, suffix = " 页",
                summary = "当前页之后并发预取的页数。动态页源每页都要跨 WebView 请求，" +
                    "调大会增加流量与内存；0 表示只加载当前页。")
        }
        SettingsGroup("显示") {
            SettingsToggle("音量键翻页", volume, prefs::setVolumeKeyTurn)
            SettingsToggle("保持屏幕常亮", keepOn, prefs::setKeepScreenOn)
            SettingsToggle("夜间柔光滤镜", night, prefs::setNightFilter)
        }
        SettingsGroup("入口") {
            SettingsAction("单页与插图收藏", onClick = onImages)
            SettingsAction("阅读足迹与统计", onClick = onStats)
        }
        SettingsFutureGroup {
            UnsupportedSetting("双击缩放", "telephoto 0.19 只暴露 EnabledZoomGestures" +
                "（None / PanOnly / ZoomOnly / ZoomAndPan）这组粗粒度开关，未确证可单独关掉" +
                "双击而保留捏合缩放；确认签名后再接，不用总开关冒充双击开关。")
            UnsupportedSetting("自动裁剪白边", "偏好已存但阅读器未接入裁剪处理，" +
                "原先是「看得到、点不动」的禁用开关；移入此处，不保留无效开关。")
            UnsupportedSetting("章节评论默认展开", "已有章节评论入口，但没有默认显示开关。")
        }
    }
}
