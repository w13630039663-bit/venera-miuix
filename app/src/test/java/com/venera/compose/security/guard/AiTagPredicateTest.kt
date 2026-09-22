package com.venera.compose.security.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁「屏蔽 AI 生成漫画」的命中面。
 *
 * 这个判据决定它是真开关还是假开关：判宽了会误伤正常漫画（用户零容忍），
 * 判窄了就等于没做。所以正反两面都要钉住。
 * 繁转简那一步要 Context 与语言表，JVM 单测拿不到，不在此覆盖。
 */
class AiTagPredicateTest {
    @Test fun masterBadgeWordsHit() {
        // 逐字同 master 卡片角标判据（comic.dart:485-487）
        assertTrue(isAiTagValue("ai"))
        assertTrue(isAiTagValue("AI"))
        assertTrue(isAiTagValue("ai-generated"))
        assertTrue(isAiTagValue("AI-Generated"))
    }

    @Test fun chineseFormsFromTagNormalizerHit() {
        // TagNormalizer.EXCLUDED_TAG_VALUES 里已归类的中文形态（:84）
        assertTrue(isAiTagValue("ai生成"))
        assertTrue(isAiTagValue("AI生成"))
        assertTrue(isAiTagValue("ai绘图"))
    }

    @Test fun namespacedTagsDoNotHit() {
        // 整串等值，不剥命名空间 —— master 的角标判据就是这个形状。
        // 剥前缀会把 female:ai（角色名 AI）也算成 AI 生成，那是误伤一整本正常漫画。
        assertFalse(isAiTagValue("Tag:AI"))
        assertFalse(isAiTagValue("female:ai"))
        assertFalse(isAiTagValue("language:ai"))
    }

    @Test fun substringDoesNotHit() {
        // 判据是等值不是 contains —— 这几条是误伤高发区
        assertFalse(isAiTagValue("AI少女"))
        assertFalse(isAiTagValue("openai"))
        assertFalse(isAiTagValue("ai generated"))
        // 繁体在本函数里不命中是**预期**：繁转简发生在 isAiTagged 那一步（要 Context 与语言表），
        // 表未就绪时按恒等返回，所以只会少命中、不会抛错。
        assertFalse(isAiTagValue("AI繪圖"))
        assertFalse(isAiTagValue("waiting"))
        assertFalse(isAiTagValue("air"))
    }

    @Test fun nonAiAndBlankStayVisible() {
        assertFalse(isAiTagValue("同人"))
        assertFalse(isAiTagValue("短篇"))
        assertFalse(isAiTagValue(""))
        assertFalse(isAiTagValue("   "))
        assertFalse(isAiTagValue("Tag:"))
    }

    // ── 标题判据：e-hentai 把标记写在标题里、tags 没有 ai（2026-09-22 实测截图）──

    @Test fun bracketedAiMarkersInTitleHit() {
        // 用户截图里的三条真实形态
        assertTrue(isAiTitleMarked("RENZEN 3 [AI Generated]"))
        assertTrue(isAiTitleMarked("[AKANE/Akanecchi] [B Experimental] Lilithea // xxx [AI Generated]"))
        assertTrue(isAiTitleMarked("[Only4u AI Art] Project melody see-through bodysuit (Patreon)"))
        assertTrue(isAiTitleMarked("作品名 (AI)"))
        assertTrue(isAiTitleMarked("作品名【AI】"))
        assertTrue(isAiTitleMarked("AI-Generated one-shot"))
    }

    @Test fun chineseAiMarkersInTitleHitBothVariants() {
        assertTrue(isAiTitleMarked("某作品 AI生成"))
        assertTrue(isAiTitleMarked("某作品 ai绘图"))
        assertTrue(isAiTitleMarked("某作品 AI繪圖"))
        assertTrue(isAiTitleMarked("某作品 AI绘图"))
    }

    @Test fun bareAiSubstringInTitleDoesNotHit() {
        // 不匹配裸 "ai" —— 这几条是误杀高发区
        assertFalse(isAiTitleMarked("AI少女与战车"))       // 角色名 AI
        assertFalse(isAiTitleMarked("openai 教程"))
        assertFalse(isAiTitleMarked("waiting for you"))
        assertFalse(isAiTitleMarked("Maid in the air"))
        assertFalse(isAiTitleMarked("RENZEN 3"))
        assertFalse(isAiTitleMarked(""))
    }

    // ── 「AI 汉化 / AI 翻译」刻意**不**命中（2026-09-22 用户截图里那两条）──

    @Test fun aiTranslationMarkersDoNotHit() {
        // 这两个词标的是**翻译工具链**是 AI，不是画面是 AI 生成的。
        // 收进来会连带屏蔽掉整批人工作画、只是用 AI 嵌字的正常漫画 —— 误杀面远大于收益。
        assertFalse(isAiTitleMarked("[個人AI漢化] Milf Breeder Chapter 6"))
        assertFalse(isAiTitleMarked("[AI翻譯] 稼ぎ方②小遣い稼ぎの先輩"))
        assertFalse(isAiTitleMarked("某作品 AI汉化"))
        assertFalse(isAiTitleMarked("某作品 AI翻译"))
    }

    @Test fun jmStyleCardTagsDoNotHit() {
        // jm.js parseComic(:211-221) 只把 category / category_sub 的 title 塞进 tags，
        // 内容标签只在 getComicDetails(:811) 的 "Tag" 组里 —— 卡片拿不到。
        // 所以 jm 上标签判据恒不命中，只剩标题判据；master 的 AI 角标同理（也读卡片 tags）。
        assertFalse(isAiTagValue("成人"))
        assertFalse(isAiTagValue("同人誌"))
        assertFalse(isAiTagValue("短編"))
    }
}
