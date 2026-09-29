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
 * 2026-09-30 用户拍板：剩下的未实现项**连标题一起撤**，底部那个「尚未实现」折叠区已删除 ——
 * 双击缩放、自动裁剪白边、章节评论默认展开三条不再出现在设置页。
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
                summary = "左右两半屏的作用互换。日漫模式本身的反向不受影响。")
            // 条漫连续流用的是阅读器面板里的 px/s 速度（临时态，不持久化）；
            // 这个间隔只对翻页模式（美漫 LTR / 日漫 RTL）的自动巡航生效。
            SettingsSlider("自动翻页间隔", autoInterval, 1f..15f,
                prefs::setAutoScrollPageIntervalSec, steps = 13, suffix = " 秒",
                summary = "只在翻页模式下自动翻页。连续滚动请在阅读器面板里调速度。")
        }
        SettingsGroup("图片") {
            SettingsSlider("页间距", gap, 0f..32f, prefs::setPageGapDp, steps = 31, suffix = " dp")
            SettingsSlider("预加载图片数量", preload.toFloat(), 0f..20f,
                { prefs.setPreloadImageCount(it.toInt()) }, steps = 19, suffix = " 页",
                summary = "往后多加载几页。有的源每页都要单独请求，调大更费流量和内存；0 是只加载当前页。")
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
    }
}
