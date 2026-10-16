package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 常驻入口条 ↔ 展开的搜索卡：**真容器变化**（2026-10-16 第七轮）。
 *
 * 用户原话：「我想你另外看看画廊的主页搜索框真的做不了容器变化动画吗，现在还是淡出淡入的」。
 * 这一轮把它做出来了 —— 形状是两端各包一层 `AnimatedVisibility`、挂**同一个 key** 的
 * `sharedBounds`（公开 API；`sharedBoundsWithCallerManagedVisibility` 是 `internal`，调不到）。
 *
 * 这几条钉的不是"动画调了没有"，而是**这一对能成立的几条耦合**：
 *  - key 与曲线只有一个出处（key 不一致时共享元素不报错，只退化成各自淡入淡出）；
 *  - 两端都挂着它（只挂一头 = 没挂）；
 *  - 入口条那一端**不许用 fade** 做进出场 —— 它与
 *    `renderInOverlayDuringTransition = false` 是**成对**的：就地渲染时父级 alpha 会乘到
 *    飞行体上，源一旦真淡出，后半程的 bounds 动画就看不见了（那正是用户抱怨的"淡出淡入"）；
 *  - 入口条**恒占位**（它是墙的第一行，让位时若不兜住高度，墙会硬跳一行）；
 *  - 卡那一端的等比缩放近似物（`scaleIn/scaleOut(0.94f)`）**退休**——它叠在 bounds 插值上
 *    是双重形变。
 */
class GallerySearchContainerTest {

    private val uiDir = "com/venera/compose/gallery/ui"

    private fun file(name: String): List<String> {
        val hit = RepoSources.kotlinFiles(uiDir).firstOrNull { it.name == name }
        // ⚠️ 找不到就直接 fail：扫描类用例静默通过等于没有用例。
        assertTrue(
            "$name 不在 $uiDir 里（量具失效）—— 实际读到的文件：" +
                RepoSources.kotlinFiles(uiDir).joinToString { it.name },
            hit != null,
        )
        return hit!!.readLines()
    }

    /** 只看**代码行**：注释里点名组件是正常的（好几处就靠注释交代历史），不该被尺子惩罚。 */
    private fun code(name: String): String =
        file(name).filterNot {
            val t = it.trim()
            t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
        }.joinToString("\n")

    private fun codeHits(needle: String): List<Triple<String, Int, String>> =
        RepoSources.linesOf(uiDir).filter { it.third.contains(needle) }.filterNot {
            val t = it.third.trim()
            t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
        }

    /**
     * ① 唯一出处里有 key、曲线、以及那两个**成对**的开关。
     *
     * `resizeMode` 与 `renderInOverlayDuringTransition` 是这一对能不能成立的关键，
     * 而两条都有"看起来更默认、其实会坏"的取值（`scaleToBounds` / `true`）——
     * 把它们钉在源码上，比钉"有没有调 sharedBounds"有意义。
     */
    @Test
    fun `唯一出处里有 key 曲线与那两个成对的开关`() {
        val src = code("GallerySearchContainer.kt")
        assertTrue(
            "key 不在唯一出处里（两端各写一个字符串迟早分叉，而分叉时共享元素不报错）",
            src.contains("const val Key = \"gallery-search-container\""),
        )
        assertTrue("曲线不在唯一出处里", src.contains("val Bounds: BoundsTransform = BoundsTransform {"))
        assertTrue(
            "resizeMode 不是 RemeasureToBounds —— 默认的 scaleToBounds 会把输入框里那行字整体缩放" +
                "（56 → 200dp 这个量级下必糊）",
            src.contains("resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds"),
        )
        assertTrue(
            "renderInOverlayDuringTransition 不是 false —— 进 overlay 就采不到 NavHost 上那块" +
                "玻璃背板，玻璃档的卡会褪成一块死色",
            src.contains("renderInOverlayDuringTransition = false"),
        )
        assertTrue(
            "曲线没按方向选档（长高 / 缩回）",
            src.contains("if (growing) SEARCH_CONTAINER_RESPONSE_SHOW else SEARCH_CONTAINER_RESPONSE_HIDE"),
        )
    }

    /**
     * ② 两端都挂着这枚共享元素 —— 而且**只有**两端。
     *
     * 只挂一头等于没挂（key 对不上就是普通换页）；挂到第三处则是"谁才是对面"这笔账说不清。
     */
    @Test
    fun `两端挂着同一个 key 的共享元素`() {
        val decl = codeHits("fun Modifier.gallerySearchContainerElement(")
        assertEquals(
            "这枚修饰符的声明只许有一处，实际：$decl",
            1,
            decl.size,
        )
        assertTrue("声明不在唯一出处里：$decl", decl.single().first.endsWith("GallerySearchContainer.kt"))

        // ⚠️ `Modifier.gallerySearchContainerElement(` 这个声明行本身也含 `.gallerySearchContainerElement(`，
        // 不排掉的话调用计数恒多一处 —— 那种"尺子自己错了"必须当场发现，而不是去放宽数目。
        val calls = codeHits(".gallerySearchContainerElement(").filterNot { it.third.contains("fun ") }
        assertEquals(
            "调用点必须**恰好两处**（常驻入口条 / 展开的搜索卡）。实际：\n" +
                calls.joinToString("\n") { "  ${it.first}:${it.second}  ${it.third.trim()}" },
            2,
            calls.size,
        )
        assertEquals(
            "两端必须在 GalleryHomeChrome.kt 与 GalleryScreen.kt 各一处。实际：$calls",
            setOf("GalleryHomeChrome.kt", "GalleryScreen.kt"),
            calls.map { it.first.substringAfterLast('/') }.toSet(),
        )
    }

    /**
     * ③ 入口条那一端**不许用 fade** 做进出场，且位移必须恒 0。
     *
     * 它与 `renderInOverlayDuringTransition = false` 成对：就地渲染时父级 alpha 会乘到飞行体上，
     * 源（入口条在"展开"这一下就是源）一旦真淡出，飞行体会被提前抹到 0，而 bounds 动画还跑着
     * —— 后半程什么都看不见，读起来又回到"淡出淡入"。
     */
    @Test
    fun `入口条那一端只借时长不做视觉动效`() {
        val src = code("GalleryHomeChrome.kt")
        assertTrue(
            "入口条那一端没走 slide 进出场",
            src.contains("slideInVertically(animationSpec = SEARCH_CONTAINER_SHOW_SLIDE_SPEC) { 0 }") &&
                src.contains("slideOutVertically(animationSpec = SEARCH_CONTAINER_HIDE_SLIDE_SPEC) { 0 }"),
        )
        assertTrue(
            "入口条那一端出现了 fade —— 它会作用到就地渲染的飞行体上，把后半程抹掉" +
                "（要 fade 就得把 renderInOverlayDuringTransition 改回 true，而那会毁掉玻璃档的模糊）",
            !src.contains("fadeIn(") && !src.contains("fadeOut("),
        )
    }

    /**
     * ④ 入口条**恒占位**。
     *
     * 它是墙的第一行，高度参与墙的布局；而"让位给卡"是靠 `AnimatedVisibility` 把内容撤下来做的
     * —— 撤下来的那一行若不兜住，墙会硬跳一行（正是 2026-10-15 要修的那一条）。
     * 高度取 `galleryHomeChromeHeight()` 而不是另写一个数：它就是这个组件的自然高度。
     */
    @Test
    fun `入口条让位时高度仍占着`() {
        val src = code("GalleryHomeChrome.kt")
        assertTrue(
            "入口条外面没有那层固定高度的占位框 —— 让位时墙会跳一行",
            src.contains("Box(modifier = modifier.height(galleryHomeChromeHeight()))"),
        )
    }

    /**
     * ⑤ 卡那一端的等比缩放近似物退休，且那个常量不许复活。
     *
     * `scaleIn/scaleOut(initialScale = 0.94f, transformOrigin = 顶边中点)` 是"容器变化"的
     * **近似物**：`0.94` 对 `56 / 200 = 0.28` 这条竖向落差毫无表达力。真容器变化落地后它若还在，
     * 就会叠在 bounds 插值上变成双重形变（卡一边被拉伸一边被缩放）。
     */
    @Test
    fun `卡那一端不再用等比缩放近似容器变化`() {
        val src = code("GalleryScreen.kt")
        assertTrue("卡那一端还挂着 scaleIn/scaleOut（与共享元素的 bounds 插值打架）", !src.contains("scaleIn("))
        assertTrue("卡那一端还挂着 scaleIn/scaleOut", !src.contains("scaleOut("))
        assertTrue("SEARCH_OVERLAY_INITIAL_SCALE 复活了", !src.contains("SEARCH_OVERLAY_INITIAL_SCALE"))
    }

    /**
     * ⑥ 曲线与时长**只有一个数字来源**。
     *
     * 时长对不上时，内容会在 bounds 动画跑完之前被 `AnimatedVisibility` 移除、尾巴被硬切 ——
     * 所以 `GalleryScreen.kt` 里那三颗必须是别名，不许再出现一处 `folmeSpring(...)` 字面量。
     */
    @Test
    fun `曲线只在唯一出处实例化`() {
        val hits = codeHits("folmeSpring")
        assertEquals(
            "folmeSpring 只许在唯一出处里出现。实际：\n" +
                hits.joinToString("\n") { "  ${it.first}:${it.second}  ${it.third.trim()}" },
            setOf("GallerySearchContainer.kt"),
            hits.map { it.first.substringAfterLast('/') }.toSet(),
        )

        val screen = code("GalleryScreen.kt")
        assertTrue(
            "卡那一端的进出场曲线不是别名（数字又分叉了）",
            screen.contains("private val SEARCH_CARD_SHOW_SPEC = SEARCH_CONTAINER_SHOW_SPEC") &&
                screen.contains("private val SEARCH_CARD_HIDE_SPEC = SEARCH_CONTAINER_HIDE_SPEC"),
        )
    }

    /**
     * ⑦ 让位开关**不给默认值**，且调用方真的传了。
     *
     * 默认 `false` 会让下一个调用点忘传时静默退化成"永远显示"（= 这一轮之前的样子，
     * 两块东西各动各的）—— 那种退化在屏上看得见、在代码里看不见。
     * 同一个理由见 `GallerySearchArea.resultCount`。
     */
    @Test
    fun `让位开关不给默认值且调用方传了`() {
        val lines = file("GalleryHomeChrome.kt")
        val at = lines.indexOfFirst { it.trim() == "searchOpen: Boolean," }
        assertTrue(
            "GalleryHomeChrome 里找不到 searchOpen 参数（量具失效）",
            at >= 0,
        )
        assertTrue(
            "searchOpen 给了默认值 —— 忘传的调用点会静默退回'永远显示'",
            !lines[at].contains("="),
        )
        assertTrue(
            "调用点没传 searchOpen",
            code("GalleryScreen.kt").contains("searchOpen = svm.active,"),
        )
    }
}
