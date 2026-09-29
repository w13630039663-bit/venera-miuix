package com.venera.compose.ui.tokens

import com.venera.compose.data.prefs.SurfaceMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「界面材质」这一轴的判据（阶段 1）。
 *
 * 抽成纯函数的理由与本仓其余判据同一条：项目单测没有 Robolectric，写进 composable 或写进
 * `Modifier` 里就永远没人钉。而这一轴最要命的两条恰恰是**能不能被断言**的那两条：
 * 默认档必须是实色（否则升级即改所有人口感）、玻璃关闭时必须什么都不做。
 */
class SurfaceMaterialPolicyTest {

    // ---------- 默认档：这条决定"改动前 vs 改动后"是不是逐像素相同 ----------

    @Test
    fun `默认档是实色`() {
        // 首项即默认值（与 GalleryAnimatedMode 同一口径），所以 entries.first() 就是没配过的新用户看到的档。
        assertEquals(SurfaceMaterial.SOLID, SurfaceMaterial.entries.first())
        assertFalse(SurfaceMaterialPolicy.glassEnabled(SurfaceMaterial.SOLID))
    }

    @Test
    fun `只有液态玻璃档开玻璃`() {
        assertTrue(SurfaceMaterialPolicy.glassEnabled(SurfaceMaterial.LIQUID_GLASS))
    }

    // ---------- 角色 → 档位 ----------

    @Test
    fun `卡片与设置分组归容器档`() {
        assertEquals(VeneraGlassTier.CONTAINER, SurfaceMaterialPolicy.tierOf(VeneraGlassRole.CARD))
        assertEquals(VeneraGlassTier.CONTAINER, SurfaceMaterialPolicy.tierOf(VeneraGlassRole.SETTINGS_GROUP))
        assertEquals(VeneraGlassTier.CONTAINER, SurfaceMaterialPolicy.tierOf(VeneraGlassRole.PANEL))
    }

    @Test
    fun `按钮与胶囊归控件档`() {
        assertEquals(VeneraGlassTier.CONTROL, SurfaceMaterialPolicy.tierOf(VeneraGlassRole.BUTTON))
        assertEquals(VeneraGlassTier.CONTROL, SurfaceMaterialPolicy.tierOf(VeneraGlassRole.CHIP))
        assertEquals(VeneraGlassTier.CONTROL, SurfaceMaterialPolicy.tierOf(VeneraGlassRole.SEGMENTED))
    }

    @Test
    fun `图标按钮与角标与进度底衬归点缀档`() {
        // 这一档一屏可能出现二十个，逐个上离屏模糊就是层数炸弹 —— 归到这里是为了让"不 blur"有对象。
        assertEquals(VeneraGlassTier.INLINE, SurfaceMaterialPolicy.tierOf(VeneraGlassRole.ICON_BUTTON))
        assertEquals(VeneraGlassTier.INLINE, SurfaceMaterialPolicy.tierOf(VeneraGlassRole.BADGE))
        assertEquals(VeneraGlassTier.INLINE, SurfaceMaterialPolicy.tierOf(VeneraGlassRole.PROGRESS))
    }

    // ---------- 档位 → 描边高光预设 ----------

    @Test
    fun `容器档与控件档按明暗取自家预设`() {
        assertEquals(
            VeneraGlassHighlight.MIDDLE_LIGHT,
            SurfaceMaterialPolicy.highlightFor(isDark = false, VeneraGlassTier.CONTAINER),
        )
        assertEquals(
            VeneraGlassHighlight.MIDDLE_DARK,
            SurfaceMaterialPolicy.highlightFor(isDark = true, VeneraGlassTier.CONTAINER),
        )
        assertEquals(
            VeneraGlassHighlight.SMALL_LIGHT,
            SurfaceMaterialPolicy.highlightFor(isDark = false, VeneraGlassTier.CONTROL),
        )
        assertEquals(
            VeneraGlassHighlight.SMALL_DARK,
            SurfaceMaterialPolicy.highlightFor(isDark = true, VeneraGlassTier.CONTROL),
        )
    }

    @Test
    fun `点缀档不描边`() {
        // 小元素描边在真机上读成"脏边"，而且这一档本来就不 blur；宁可不给。
        assertNull(SurfaceMaterialPolicy.highlightFor(isDark = false, VeneraGlassTier.INLINE))
        assertNull(SurfaceMaterialPolicy.highlightFor(isDark = true, VeneraGlassTier.INLINE))
    }

    @Test
    fun `预设的库内真名逐字钉住`() {
        // 这四个串是 2026-09-29 从 miuix-blur 0.9.4-rc01 的 sources jar 里逐字读出来的
        // （highlight/Highlight.kt:34-58，是**扁平名**，不存在 `Highlight.GlassStrokeSmall.Light` 那种点链写法）。
        // 库一升级就改名，这条断言会先把"玻璃描边静默消失"变成一条红测试。
        assertEquals("GlassStrokeMiddleLight", VeneraGlassHighlight.MIDDLE_LIGHT.libName)
        assertEquals("GlassStrokeMiddleDark", VeneraGlassHighlight.MIDDLE_DARK.libName)
        assertEquals("GlassStrokeSmallLight", VeneraGlassHighlight.SMALL_LIGHT.libName)
        assertEquals("GlassStrokeSmallDark", VeneraGlassHighlight.SMALL_DARK.libName)
    }
}
