package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryAi
import com.venera.compose.gallery.domain.GalleryAnimatedMode
import com.venera.compose.gallery.domain.GalleryMotion
import com.venera.compose.gallery.domain.GalleryViewerBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 C2 三条判据（动图该不该解动画、这条是不是 AI 画的、大图页背景）。
 *
 * 全部抽成纯函数的理由与 C1 同一条：本项目单测没有 Robolectric，写进 composable
 * 或写进 `ConnectivityManager` 那层就永远没人钉。网络"是不是不计费"那一半留在
 * `GalleryConnectivity`（要 Context），这一层只管**拿到读数之后怎么判**。
 */
class GalleryViewerPoliciesC2Test {

    // ---------- 动图自动播放三档 ----------

    @Test
    fun `始终档在有网没网都解动画`() {
        assertTrue(GalleryMotion.animates(GalleryAnimatedMode.ALWAYS, unmetered = true))
        assertTrue(GalleryMotion.animates(GalleryAnimatedMode.ALWAYS, unmetered = false))
    }

    @Test
    fun `从不档一律不解动画`() {
        assertFalse(GalleryMotion.animates(GalleryAnimatedMode.NEVER, unmetered = true))
        assertFalse(GalleryMotion.animates(GalleryAnimatedMode.NEVER, unmetered = false))
    }

    @Test
    fun `仅 Wi-Fi 档只认不计费网络`() {
        assertTrue(GalleryMotion.animates(GalleryAnimatedMode.WIFI_ONLY, unmetered = true))
        assertFalse(GalleryMotion.animates(GalleryAnimatedMode.WIFI_ONLY, unmetered = false))
    }

    @Test
    fun `默认档是仅 Wi-Fi`() {
        // 默认值本身要钉住：刚修完一屏动图 OOM，默认给"始终"等于把流量与内存的口子重新敞开。
        assertEquals(GalleryAnimatedMode.WIFI_ONLY, GalleryAnimatedMode.entries.first())
    }

    // ---------- AI 判据 ----------

    @Test
    fun `两种写法都认`() {
        // 2026-09-29 复探针：Gelbooru `tags=ai_generated` 与 `tags=ai-generated` 第一页各 9 张卡片
        // （阴性对照 `zzz_not_a_tag` = 0 张），而条目标签串上写的是连字符那一版。
        // 上一轮这里记的"yande.re 只有 ai-generated（83 条）"复现不出来：那一站这四种写法现在全是 0 条。
        assertTrue(GalleryAi.isAiMarked(listOf("1girl", "ai-generated")))
        assertTrue(GalleryAi.isAiMarked(listOf("ai_generated")))
    }

    @Test
    fun `裸标签 ai 不算 —— 它是角色名`() {
        // yande.re `tags=ai` 那 19 条的标签串是 `ai maid pointy_ears sage shuffle suzuhira_hiro tick_tack`，
        // 即《Artery Gear》的角色 AI。漫画侧需要裸 `ai` 那一枚（EH 的 AI生成 系），画廊侧剔掉它。
        assertFalse(GalleryAi.isAiMarked(listOf("ai")))
        assertFalse(GalleryAi.isAiMarked(listOf("ai", "maid", "suzuhira_hiro")))
    }

    @Test
    fun `含 ai 子串的普通标签不算`() {
        // `long_hair` / `hair_bun` 这类是历史上真出过的误伤形状：必须整词比，不能 contains。
        assertFalse(GalleryAi.isAiMarked(listOf("long_hair", "rain", "chain", "painting")))
    }

    @Test
    fun `命名空间前缀不剥`() {
        // 与漫画侧那把同一口径（isAiTagValue 刻意不剥 `female:` 前缀）：
        // 剥了就会把 `female:hair` 这类判成别的什么，而 AI 标记在两站都是**裸标签**。
        assertFalse(GalleryAi.isAiMarked(listOf("female:ai")))
    }

    @Test
    fun `没有标签不算 AI`() {
        assertFalse(GalleryAi.isAiMarked(emptyList()))
        assertFalse(GalleryAi.isAiMarked(listOf("", "  ")))
    }

    @Test
    fun `中文那两枚沿用漫画侧词表`() {
        assertTrue(GalleryAi.isAiMarked(listOf("ai生成")))
        assertTrue(GalleryAi.isAiMarked(listOf("AI绘图")))
    }

    // ---------- 大图页背景 ----------

    @Test
    fun `只有现状那一档是透明的`() {
        // 判据要钉住的原因：非 GLASS 三档是**不透明底**，它们盖住窗口模糊，
        // 所以不需要在运行时去动 window flag —— 这条不写清，下次就有人去加一层"关模糊"的假逻辑。
        assertFalse(GalleryViewerBackdrop.GLASS.opaque)
        assertTrue(GalleryViewerBackdrop.BLACK.opaque)
        assertTrue(GalleryViewerBackdrop.DARK_GRAY.opaque)
        assertTrue(GalleryViewerBackdrop.WHITE.opaque)
    }

    @Test
    fun `默认档是现状`() {
        // 默认必须是"用户已经看惯的那一版"，否则升级即改观感。
        assertEquals(GalleryViewerBackdrop.GLASS, GalleryViewerBackdrop.entries.first())
    }
}
