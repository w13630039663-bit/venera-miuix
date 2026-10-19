package com.venera.compose.feature.pet

import com.venera.compose.testsupport.RepoSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页顶栏桌宠这一整条链的接线尺子（2026-10-16 批 116）。
 *
 * 为什么全是源码扫描而不是跑起来看：Android 侧没有 Compose UI 测试宿主
 * （`app/src` 下只有 `main` 与 `test`，无 `androidTest`、无 Robolectric），
 * 而这一批最可能出的三种坏形态**恰好都是静态可见的**：
 *
 * 1. **静默不动** —— 请求没向闸门表态，GIF 只会解出首帧：不报错、不闪、截图完全正常。
 *    这是本件最贵的失败，所以单独钉一条，且钉的是"那一行整串表达式"而不是符号名。
 * 2. **开关是假的** —— 设置页摆了行但没人读那枚偏好，拨了没反应。
 * 3. **默认档被顺手改成开** —— 新能力默认关闭是本仓的零回归铁律。
 *
 * ⚠️ 存在性锚点一律**数次数**：同屏另一处合法调用会把"存在即通过"喂饱（这条判据来自
 * `AppDefaultPreferenceValuesTest.appearanceLineWith` 的同一族坑）。
 */
class PetStickerEntryTest {

    private fun codeLines(packagePath: String, fileName: String): List<String> =
        RepoSources.kotlinFiles(packagePath)
            .first { it.name == fileName }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private val petMascot = codeLines("com/venera/compose/feature/pet", "PetMascot.kt")
    private val homeScreen = codeLines("com/venera/compose/feature", "HomeScreen.kt")
    private val appearanceSettings = codeLines("com/venera/compose/feature/settings", "AppearanceSettings.kt")
    private val prefsFile = codeLines("com/venera/compose/data/prefs", "VeneraPreferences.kt")

    private fun exactlyOne(lines: List<String>, anchor: String, where: String): String {
        val hits = lines.filter { it.contains(anchor) }
        assertEquals("`$anchor` 在 $where 应当只出现在一行，实际 ${hits.size} 行", 1, hits.size)
        return hits.first()
    }

    @Test
    fun `桌宠请求必须向动图闸门表态，否则会静默停在首帧`() {
        val line = exactlyOne(petMascot, ".allowAnimatedImage(", "PetMascot.kt")
        assertTrue("表态不能是写死的 true —— 系统关掉动画那一档要靠它。实际：$line", line.contains("animate)"))
    }

    @Test
    fun `桌宠走的是自己那把 loader，漫画封面那条链仍然恒静帧`() {
        exactlyOne(petMascot, "imageLoader = PetImageLoader.get(context)", "PetMascot.kt")
        val app = codeLines("com/venera/compose", "VeneraApp.kt")
        assertFalse(
            "应用级 ImageLoader 上出现了动图解码器 —— 全站封面都会跟着可解动画，" +
                "画廊那条「墙上卡片一律静帧」的 OOM 判据就被拆了",
            app.any { it.contains("AnimatedImageDecoder") || it.contains("GalleryAnimationGate") },
        )
    }

    @Test
    fun `开关默认关`() {
        val decl = exactlyOne(prefsFile, "KEY_HOME_PET_STICKER, false", "VeneraPreferences.kt")
        assertTrue("默认档那一句必须读盘上值并兜到 false。实际：$decl", decl.contains("getBoolean"))
    }

    @Test
    fun `设置页那一行是真的开关而不是灰行`() {
        exactlyOne(appearanceSettings, "\"桌宠小肥鱼\"", "AppearanceSettings.kt")
        exactlyOne(appearanceSettings, "prefs::setHomePetSticker", "AppearanceSettings.kt")
        // 订阅与写入口都在：只有 SettingsToggle 而没人 collect，就是一枚拨了不动的假开关
        exactlyOne(appearanceSettings, "prefs.homePetSticker.collectAsState()", "AppearanceSettings.kt")
    }

    @Test
    fun `首页只在开关打开时组合那一格`() {
        // 钉整串表达式：只钉 `PetMascot` 这个符号名的话，"排完立刻撤掉"那种假接线也能过
        exactlyOne(homeScreen, "navigationIcon = { if (petStickerOn) PetMascot() }", "HomeScreen.kt")
        assertEquals(
            "首页只有一处顶栏调用，接线不能被抄成两份",
            1,
            homeScreen.count { it.contains("navigationIcon = {") },
        )
        // 关着时不组合（而不是画了个隐形的）：判据就是上面那一整串表达式里的 if
        exactlyOne(homeScreen, "appearancePrefs.homePetSticker.collectAsState()", "HomeScreen.kt")
    }

    @Test
    fun `随包素材在盘上且没有涨回原始尺寸`() {
        val asset = File(RepoSources.root, "app/src/main/assets/pet/idle.gif")
        assertTrue("随包素材不见了：${asset.absolutePath}", asset.isFile)
        val bytes = asset.length()
        // 原始素材 3.63MB（488×292 / 72 帧）。重编码到 198×119 / 8fps 后实测 577,768 字节。
        // 这条闸门是为了防止有人把原始那份直接拷回来。
        assertTrue("桌宠素材 $bytes 字节，超过 700KB 上限（原始素材是 3.63MB）", bytes in 1..700_000)
    }
}
