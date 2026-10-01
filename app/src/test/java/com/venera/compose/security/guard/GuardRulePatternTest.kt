package com.venera.compose.security.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「这条屏蔽规则的写法能不能用」的判据锁。
 *
 * 为什么要有这一层：屏蔽规则一旦存进去就**只由 [ContentGuardManager] 的 `match()` 消费**，
 * 而那里的兜底是 `catch → false`。也就是编译不过的正则不会报错，只会**永不命中** ——
 * 列表上显示「已启用 · 正则规则」，屏上却一张也没少。设置页早就在写库前拦了一道
 * （`BlockingSettings` 的注释原话：坏模式会静默永不命中，比报错更难查），
 * 但**恢复备份那条路没拦**：`BackupManager` 把 `is_regex=1` 的行直接写库。
 * 备份档被手工改过、或来自别的版本时，恢复完就是一堆永不生效的规则。
 *
 * 所以把那道判断从 Compose 的点击处理器里抽到这里，两边共用一份。
 * 判据本身刻意与「哪条规则被挡」同一条口径（见 `findGalleryBlockedRule` 的注释：
 * 说不出被谁挡的屏蔽就是假读数）—— 生效不了的规则也不该占着那一格。
 */
class GuardRulePatternTest {

    @Test
    fun `写坏的正则判为不可用`() {
        assertFalse(GuardRulePattern.compiles("18+("))
        assertFalse(GuardRulePattern.compiles("[^a-z"))
        assertFalse(GuardRulePattern.compiles("*ai"))        // 悬空量词
        assertFalse(GuardRulePattern.compiles("a{2,1}"))     // 重复区间反了
    }

    @Test
    fun `正常写法一律放行`() {
        assertTrue(GuardRulePattern.compiles("ai"))
        assertTrue(GuardRulePattern.compiles("long_hair"))
        assertTrue(GuardRulePattern.compiles("18\\+"))
        assertTrue(GuardRulePattern.compiles("^(汉化组A|汉化组B)$"))
    }
}
