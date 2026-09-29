package com.venera.compose.feature.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.venera.VeneraTextField
import com.venera.compose.components.venera.VeneraTextButton
import com.venera.compose.data.prefs.AppearanceStyle
import com.venera.compose.data.prefs.NavigationBarStyle
import com.venera.compose.data.prefs.SurfaceMaterial
import com.venera.compose.data.prefs.TagTranslationMode
import com.venera.compose.data.prefs.ThemeColorSource
import com.venera.compose.data.prefs.ThemeMode
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.ui.tokens.ThemeSeedPresets
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppearanceSettings(prefs: VeneraPreferences, onBack: () -> Unit) {
    val mode by prefs.themeMode.collectAsState()
    val appearance by prefs.appearanceStyle.collectAsState()
    val navigationBar by prefs.navigationBarStyle.collectAsState()
    val material by prefs.surfaceMaterial.collectAsState()
    val tagTranslation by prefs.tagTranslationMode.collectAsState()
    val colorSource by prefs.themeColorSource.collectAsState()
    val seedArgb by prefs.themeSeedColor.collectAsState()
    SettingsPage("外观", onBack, largeTitle = "外观与主题") {
        // 对照原版顶部手机模型，直接跟随真实 Miuix 色板。
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.size(196.dp, 296.dp).clip(RoundedCornerShape(28.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant).padding(10.dp)) {
                Column(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                    .background(MiuixTheme.colorScheme.surface).padding(12.dp)) {
                    Text("薇奈拉", fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreviewBlock(74, true); PreviewBlock(60, false)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreviewBlock(52, false); PreviewBlock(82, false)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(13.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Text("●     ·     ·     ·", color = MiuixTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                }
            }
        }
        SettingsGroup("主题模式") {
            SettingsSelect("主题模式", mode.name, listOf("SYSTEM" to "跟随系统", "LIGHT" to "浅色", "DARK" to "深色"),
                { prefs.setThemeMode(ThemeMode.valueOf(it)) })
        }
        SettingsGroup("界面风格") {
            SettingsSelect("界面风格", appearance.name,
                listOf("MIUIX" to "Miuix", "MD3" to "MD3 · 壁纸动态取色"),
                { prefs.setAppearanceStyle(AppearanceStyle.valueOf(it)) })
            // 取色来源只在 MD3 下出现：Miuix 风格用它自己设计好的固定色板，
            // 拿种子色硬展开会破坏那套配色，所以这里不给入口而不是给了不生效。
            if (appearance == AppearanceStyle.MD3) {
                SettingsSelect(
                    "取色来源", colorSource.name,
                    listOf("WALLPAPER" to "跟随系统壁纸", "CUSTOM" to "自定义颜色"),
                    { prefs.setThemeColorSource(ThemeColorSource.valueOf(it)) },
                    summary = if (colorSource == ThemeColorSource.WALLPAPER &&
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                    ) "此 Android 版本拿不到壁纸动态色板，会退回 MD3 静态色板" else null,
                )
                if (colorSource == ThemeColorSource.CUSTOM) {
                    var paletteOpen by rememberSaveable { mutableStateOf(false) }
                    ThemeSeedSummary(seedArgb, paletteOpen) { paletteOpen = !paletteOpen }
                    if (paletteOpen) {
                        ThemeSeedGrid(seedArgb) { prefs.setThemeSeedColor(it) }
                        ThemeSeedHexInput { prefs.setThemeSeedColor(it) }
                    }
                }
            }
            Text(
                text = when {
                    appearance == AppearanceStyle.MIUIX -> "用 Miuix 自带的浅色/深色配色。"
                    colorSource == ThemeColorSource.CUSTOM ->
                        "按你选的这个颜色算出一整套配色。上面看到的色块是种子色，不是最后的效果。"
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                        "跟随系统壁纸取色，所有页面一起变。"
                    else -> "这个 Android 版本不支持跟随壁纸取色，改用固定的一套 MD3 配色。"
                },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        SettingsGroup("界面材质") {
            SettingsSelect(
                "界面材质", material.name,
                listOf(
                    "SOLID" to "实色（默认）",
                    "LIQUID_GLASS" to "Miuix 液态悬浮玻璃",
                ),
                { prefs.setSurfaceMaterial(SurfaceMaterial.valueOf(it)) },
                summary = "卡片、分组、按钮这些表面要不要半透明浮起来。这里只管透明度，" +
                    "配色和形状还是由上面的界面风格决定，两边互不影响。选实色时没有任何透明效果。" +
                    "底部导航栏由下面的导航栏样式单独决定。两处都开透明会多一层全屏模糊，配置低的机器可能掉帧。",
            )
        }
        SettingsGroup("导航栏") {
            SettingsSelect("导航栏样式", navigationBar.name,
                listOf("MD3" to "Miuix 悬浮栏（默认）", "LIQUID_GLASS" to "Liquid Glass 液态玻璃"),
                { prefs.setNavigationBarStyle(NavigationBarStyle.valueOf(it)) })
            Text(
                "把底栏做成玻璃质感：背景模糊、边缘折射和高光，拖动时还有形变效果。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        SettingsGroup("标签") {
            SettingsSelect("标签译文显示", tagTranslation.name,
                listOf(
                    "SYSTEM" to "跟随系统语言",
                    "SIMPLIFIED" to "始终简体",
                    "TRADITIONAL" to "始终繁体",
                    "OFF" to "关闭（显示原文）",
                ),
                { prefs.setTagTranslationMode(TagTranslationMode.valueOf(it)) })
            Text(
                "命中的英文标签会同时给出译文和原文，括号里是原文。" +
                    "词典里没有的标签（多数是中文站的词）只显示原文。" +
                    "搜索时发给各源的词还是原文，不受这里影响。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        // 审计后删除 4 条不打算做的灰行：返回动画样式、沉浸式背景、自定义壁纸、模糊强度。
        // 壁纸/背景体系是独立工程，缺口清单见 settings-audit-2026-09.md，不拿设置页当 TODO。
    }
}

@Composable
private fun PreviewBlock(height: Int, primary: Boolean) {
    Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(8.dp))
        .background(if (primary) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant))
}

/** 种子色的显示形式：大写 `#RRGGBB`，与预设表与手输框共用一套。 */
internal fun themeSeedHex(argb: Int): String =
    "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

/**
 * 手输色值 → ARGB。只收 6 位十六进制（可省 `#`），不认 `#RGB` 缩写与 8 位带透明度 ——
 * 种子色必须是实色，alpha 进来只会让展开出来的色板说不清自己在哪个明暗档。
 * 归一规则同 BiliPai 的 `normalizeMd3CustomColorHex`。
 */
internal fun parseThemeSeedHex(input: String): Int? {
    val body = input.trim().removePrefix("#")
    if (!Regex("^[0-9a-fA-F]{6}$").matches(body)) return null
    return THEME_SEED_OPAQUE or body.toInt(16)
}

private const val THEME_SEED_OPAQUE = 0xFF000000.toInt()

@Composable
private fun ThemeSeedSummary(argb: Int, expanded: Boolean, onToggle: () -> Unit) {
    val tokens = VeneraTokens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = tokens.spacing.rowHorizontal, vertical = tokens.spacing.space5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "主题色：${ThemeSeedPresets.nameOf(argb)}",
                fontSize = tokens.type.itemTitle,
                fontWeight = tokens.type.weightMedium,
            )
            Text(
                "当前 ${themeSeedHex(argb)}；点按${if (expanded) "收起" else "展开"}预设色板",
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(top = tokens.spacing.space1),
            )
        }
        // 小结行自己带一枚当前色，收起状态下也能确认改没改对。
        Box(Modifier.size(22.dp).clip(CircleShape).background(Color(argb)))
        Text(
            if (expanded) "收起" else "展开",
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
            modifier = Modifier.padding(start = tokens.spacing.space1),
        )
    }
}

@Composable
private fun ThemeSeedGrid(selectedArgb: Int, onPicked: (Int) -> Unit) {
    val tokens = VeneraTokens
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.rowHorizontal)
            .padding(bottom = tokens.spacing.space5),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
    ) {
        ThemeSeedPresets.All.chunked(ThemeSeedPresets.Columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap)) {
                row.forEach { preset ->
                    // weight(1f) 让 5 列在任何屏宽下等分（宽屏不会挤成一行小点）。
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(preset.color)
                                .clickable { onPicked(preset.argb) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (preset.argb == selectedArgb) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "已选择",
                                    // 勾的颜色按亮度选黑白：日光黄底上白勾等于看不见。
                                    tint = if (preset.color.luminance() > 0.5f) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Text(
                            preset.name,
                            fontSize = tokens.type.caption,
                            maxLines = 1,
                            color = if (preset.argb == selectedArgb) tokens.color.textPrimary
                                else tokens.color.textTertiary,
                            modifier = Modifier.padding(top = tokens.spacing.space1),
                        )
                    }
                }
                // 尾行不满 5 枚时用占位撑住列宽，否则最后一行会被拉长。
                repeat(ThemeSeedPresets.Columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ThemeSeedHexInput(onApplied: (Int) -> Unit) {
    val tokens = VeneraTokens
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.rowHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VeneraTextField(
            value = text,
            onValueChange = { text = it; error = null },
            label = "或输入自定义色值 #RRGGBB",
            singleLine = true,
            isError = error != null,
            modifier = Modifier.weight(1f),
        )
        VeneraTextButton(
            text = "应用",
            enabled = text.isNotBlank(),
            onClick = {
                val parsed = parseThemeSeedHex(text)
                if (parsed == null) error = "请输入 6 位十六进制色值" else { error = null; onApplied(parsed) }
            },
        )
    }
    if (error != null) {
        Text(
            error!!,
            fontSize = tokens.type.caption,
            color = MiuixTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = tokens.spacing.rowHorizontal),
        )
    }
}
