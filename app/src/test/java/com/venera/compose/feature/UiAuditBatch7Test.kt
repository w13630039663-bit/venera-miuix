package com.venera.compose.feature

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-14 探索页改版（折叠态 + 按需展开）的判据。
 *
 * 这四条防的都是**编译器看不见、单测也绿、只在真机上才难看**的东西：
 *
 *  ① 假开关：一枚按钮渲染出来了，但它门的区块永远空 —— 判式认**整串表达式**，
 *     不是标识符。本仓栽过"判式里就写着那个名字，喂饱了自己"。
 *  ② 模式药丸行：`SectionHeader("探索方式")` 不许回来 + `size <= 1` 不渲染的判式必须在。
 *
 * 同轮加过的副标题两条（地板补偿项 / 真状态表达式）已于当天删除 —— 副标题整个撤了
 * （源名在源下拉格里、模式名在模式行实心选中态上，两处都在屏上，副标题再说一遍就是
 * 「禁漫天堂 · 禁漫天堂」同词两遍），补偿项与判据随之作废。
 */
class UiAuditBatch7Test {

    private fun code(dir: String, name: String): List<String> =
        RepoSources.kotlinFiles(dir)
            .first { it.name == name }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    /** ① 两枚动作按钮各自的渲染都被**计数判式**门控（认整串，不认标识符）。 */
    @Test
    fun `筛选按钮的渲染被 filterCount 计数判式门控`() {
        val explore = code("com/venera/compose/feature/explore", "UnifiedExploreScreen.kt")
        // 门控整串：必须有"if (filterCount > 0)"
        assertTrue(
            "「筛选 · N」这枚按钮没被 filterCount > 0 门控 —— 点开没东西的按钮不许出现。" +
                "（判式要认整串表达式：只写一个 filterCount 标识符会被别处喂饱）",
            explore.any { it.contains("if (filterCount > 0) {") },
        )
        // 且 N 的值来自同一把尺子（纯函数），不是页面里另算一套
        assertTrue(
            "filterCount 不是从 filterEntryCount(availability) 来的 —— 两本账分叉就会出现" +
                "按钮说 5 个、点开只有 3 个（出现这种不一致就说明 N 与展开区不是同一把尺子）",
            explore.any { it.contains("val filterCount = filterEntryCount(unifiedAvailability)") },
        )
    }

    /**
     * 分类区是**常驻**的：探索 = 浏览，分类就是主干道。
     *
     * 2026-10-14 折叠那一轮把它折进「分类 · N」按钮，用户原话"需要手动点击才显示
     * 不好" —— 折进去等于要先点一下才看得见能逛什么。这条判据防它回潮：
     * 判式里不许再有 categoriesExpanded 那枚门，也不许有「分类 · N」开关按钮。
     */
    @Test
    fun `分类区常驻不被展开开关门控`() {
        val explore = code("com/venera/compose/feature/explore", "UnifiedExploreScreen.kt")
        assertFalse(
            "分类区又被 categoriesExpanded 门控了 —— 常驻分类区不该有点开这一步",
            explore.any { it.contains("if (vm.categoriesExpanded && hasAnyCategoryEntry) {") },
        )
        assertFalse(
            "「分类 · N」开关按钮回来了 —— 分类是常驻的，那枚按钮折叠主干道",
            explore.any { it.contains("分类 · \$categoryCount") },
        )
    }

    /** ① 的对照：展开区自己也要被同一枚计数判式看着，不许出现"按钮在、区里空"。 */
    @Test
    fun `筛选展开区同样被可用性判式门控`() {
        val explore = code("com/venera/compose/feature/explore", "UnifiedExploreScreen.kt")
        assertTrue(
            "筛选展开区的门控里没有可用性判式 —— 只有 filterExpanded 一枚布尔，" +
                "计数为 0 时依然会摊开一块空白",
            explore.any { it.contains("if (vm.filterExpanded && !unifiedAvailability.isEmpty) {") },
        )
    }

    /** ② 模式行：`SectionHeader("探索方式")` 不许回来，且 size<=1 不渲染的判式必须在。 */
    @Test
    fun `模式药丸行没有节头且只有一个模式时不渲染`() {
        val explore = code("com/venera/compose/feature/explore", "UnifiedExploreScreen.kt")
        assertFalse(
            "`SectionHeader(\"探索方式\")` 又回到模式行了 —— 那 25dp 是折叠态 chrome 里最贵的一行",
            explore.any { it.contains("SectionHeader(\"探索方式\")") },
        )
        assertTrue(
            "模式行没有 `size <= 1 不渲染` 的判式 —— 只有一枚模式时那一行既选不动也读不出当前模式",
            explore.any { it.contains("if (exploreModeRowVisible(pageModes)) {") },
        )
        // 判式必须落在纯函数里（可单测），不是页面里另写一套
        assertTrue(
            "exploreModeRowVisible 没在 ExplorePolicy.kt 里 —— 显隐判式该是无 Compose 依赖的纯函数",
            code("com/venera/compose/feature/explore", "ExplorePolicy.kt")
                .any { it.contains("internal fun exploreModeRowVisible(modes: List<ExploreMode>): Boolean = modes.size > 1") },
        )
    }
}
