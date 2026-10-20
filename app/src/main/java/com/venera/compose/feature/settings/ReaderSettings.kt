package com.venera.compose.feature.settings

import androidx.compose.runtime.*
import com.venera.compose.data.api.ReaderPreferences
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.reader.ReaderEnhancement
import com.venera.compose.reader.ReaderPageTurnEffect
import com.venera.compose.reader.ReaderReadingMode
import kotlin.math.roundToInt

/**
 * 阅读设置。
 *
 * 2026-09-19 设置审计后：预加载数、反转点击方向、自动翻页间隔从灰行转正；
 * 删掉 15 条"需要新子系统"的灰行（设备专属、翻页动画、每屏图数、首图单张、
 * 鼠标滚速、限宽、时间/状态栏/页码、快速收藏、自定义图片处理、章节评论两处）；
 * 2026-09-30 用户拍板：剩下的未实现项**连标题一起撤**，底部那个「尚未实现」折叠区已删除 ——
 * 双击缩放、自动裁剪白边、章节评论默认展开三条不再出现在设置页。
 * 完整判据与清单见 settings-audit-2026-09.md。
 *
 * 2026-10-13 转正两条新的：自动翻页**续到下一话**、以及「图片增强」整组（默认关闭）。
 */

/** 增强四枚都以"百分比"读数进出：滑块给的是 0..100 的整数，偏好存的是倍率。 */
private fun percentOf(stored: Float): Float = (stored * 100f).roundToInt().toFloat()

@Composable
internal fun ReaderSettings(prefs: ReaderPreferences, onBack: () -> Unit, onImages: () -> Unit, onStats: () -> Unit) {
    val mode by prefs.defaultReadingMode.collectAsState()
    val tap by prefs.clickToTurn.collectAsState()
    val reverseTap by prefs.reverseTapDirection.collectAsState()
    val volume by prefs.volumeKeyTurn.collectAsState()
    val keepOn by prefs.keepScreenOn.collectAsState()
    val night by prefs.nightFilter.collectAsState()
    val gap by prefs.pageGapDp.collectAsState()
    val autoInterval by prefs.autoScrollPageIntervalSec.collectAsState()
    val preload by prefs.preloadImageCount.collectAsState()
    val autoNextChapter by prefs.autoNextChapter.collectAsState()
    val pageTurnEffect by prefs.pageTurnEffect.collectAsState()
    val enhanceOn by prefs.readerEnhanceEnabled.collectAsState()
    val sharpen by prefs.readerEnhanceSharpen.collectAsState()
    val contrast by prefs.readerEnhanceContrast.collectAsState()
    val saturation by prefs.readerEnhanceSaturation.collectAsState()
    val brightness by prefs.readerEnhanceBrightness.collectAsState()
    SettingsPage("阅读", onBack, largeTitle = "阅读设置", heroSubtitle = "翻页 · 显示 · 预加载") {
        SettingsGroup("翻页与模式") {
            SettingsSelect("阅读模式", ReaderReadingMode.fromKey(mode).key,
                listOf("HORIZONTAL_LTR" to "翻页（从左到右）", "HORIZONTAL_RTL" to "翻页（从右到左）",
                    "HORIZONTAL_CONTINUOUS" to "连续（从左到右）", "VERTICAL_CONTINUOUS" to "连续（从上到下）",
                    "DOUBLE_PAGE" to "对开双页"), prefs::setDefaultReadingMode)
            // 翻页特效默认「无（原样）」：守仓库"新能力默认不改动观感"铁律（同图片增强总开关、内容层材质）。
            SettingsSelect("翻页特效", ReaderPageTurnEffect.fromKey(pageTurnEffect).key,
                ReaderPageTurnEffect.entries.map { it.key to it.label },
                prefs::setPageTurnEffect,
                summary = "只在单页左右翻时生效。")
            SettingsToggle("点击翻页", tap, prefs::setClickToTurn)
            SettingsToggle("反转点击翻页方向", reverseTap, prefs::setReverseTapDirection,
                summary = "左右两半屏的作用互换。日漫模式本身的反向不受影响。")
            SettingsSlider("自动翻页间隔", autoInterval, PreferenceKeys.AUTO_PAGE_INTERVAL_SEC_RANGE,
                prefs::setAutoScrollPageIntervalSec, steps = 9, suffix = " 秒",
                summary = "只在左右翻页时生效。连续滚动请在阅读器面板里调速度。")
            SettingsToggle("自动翻页续到下一话", autoNextChapter, prefs::setAutoNextChapter,
                summary = "翻到本话最后一页后，自动读取下一话。全书结束后停止。")
        }
        SettingsGroup("图片") {
            SettingsSlider("页间距", gap, 0f..32f, prefs::setPageGapDp, steps = 31, suffix = " dp")
            SettingsSlider("预加载图片数量", preload.toFloat(), 0f..20f,
                { prefs.setPreloadImageCount(it.toInt()) }, steps = 19, suffix = " 页",
                summary = "提前加载后面几页。调大更流畅，也更费流量和内存；0 只加载当前页。")
        }
        SettingsGroup("图片增强") {
            SettingsToggle("锐化与色彩调整", enhanceOn, prefs::setReaderEnhanceEnabled,
                summary = "开启后会调整画面颜色，不再是原图。")
            SettingsSlider("锐化", percentOf(sharpen), 0f..100f,
                { prefs.setReaderEnhanceSharpen(it / 100f) }, steps = 19, suffix = " %",
                summary = "让线条更清楚。0 是原图。", enabled = enhanceOn)
            SettingsSlider("对比度", percentOf(contrast), 50f..150f,
                { prefs.setReaderEnhanceContrast(it / 100f) }, steps = 19, suffix = " %",
                summary = "100 是原图。", enabled = enhanceOn)
            SettingsSlider("饱和度", percentOf(saturation), 0f..200f,
                { prefs.setReaderEnhanceSaturation(it / 100f) }, steps = 19, suffix = " %",
                summary = "100 是原图，0 是黑白。", enabled = enhanceOn)
            SettingsSlider("亮度", percentOf(brightness), -25f..25f,
                { prefs.setReaderEnhanceBrightness(it / 100f) }, steps = 9, suffix = " %",
                summary = "0 是原图。夜间反色会在之后叠加。", enabled = enhanceOn)
        }
        SettingsGroup("显示") {
            SettingsToggle("音量键翻页", volume, prefs::setVolumeKeyTurn)
            SettingsToggle("保持屏幕常亮", keepOn, prefs::setKeepScreenOn)
            SettingsToggle("夜间反色滤镜", night, prefs::setNightFilter,
                summary = "黑白反转，降低夜间亮度。")
        }
        SettingsGroup("入口") {
            SettingsAction("单页与插图收藏", onClick = onImages)
            SettingsAction("阅读足迹与统计", onClick = onStats)
        }
    }
}
