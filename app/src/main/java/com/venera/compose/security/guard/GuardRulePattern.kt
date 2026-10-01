package com.venera.compose.security.guard

/**
 * 「这条屏蔽规则写得能不能用」的判据。
 *
 * 为什么要有：屏蔽规则存进去之后只由 [ContentGuardManager] 的 `match()` 消费，而那里的兜底是
 * `catch → false` —— 编译不过的正则**不报错，只是永不命中**，列表上却明明白白写着
 * 「已启用 · 正则规则」。设置页早就在写库前拦了一道，但**恢复备份那条路没拦**
 * （`BackupManager` 把 `is_regex=1` 的行直接写库），备份档被改过或来自别的版本时，
 * 恢复完就是一堆永不生效的规则。
 *
 * 与「说不出被谁挡的屏蔽就是假读数」是同一条口径：生效不了的规则也不该占着那一格。
 * 用例见 `GuardRulePatternTest`。
 */
object GuardRulePattern {

    /**
     * 用 `match()` **真正会用的那组选项**去编译一次：只判语法不判选项的话，
     * 校验通过而运行时抛开的口子就又开回来了。
     */
    fun compiles(pattern: String): Boolean =
        runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.isSuccess
}
