package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第四十八批 ② · 落点读数要念得出**是谁把落点改走的**。
 *
 * 批 48 第一版那行 `D_落点` 修好了"落点没有读数"这一半，但当场量到一件更难看的事：
 * 带 `-PstartDomain=SETTINGS` 起窗、**一次鼠标都没动**，落点连念三行
 * `域=设置 行=画廊显示档 浮层=关` → `域=图库 行=每日热门 浮层=关` → `域=图库 行=每日热门 浮层=开`。
 * 而全桌面写 `domain` 的地方只有四颗，四颗**都在"用户动作"那一路**（侧栏点击、标签交接、顶栏搜索、画师单人页）
 * —— 也就是说"没人点却像被人点了一下"。光有"改了"这一句定不了案，所以每一处写 `domain` 的地方
 * 必须同时写一枚戳，读数把戳念出来：**下一帧起"是谁改的"是一行读数，不是一次猜测。**
 */
class DesktopLandingMoverTest {

    private val app = DesktopSourceTree.desktopSourceOf("gallery/ui/VeneraDesktopApp.kt")
    private val code = DesktopSourceTree.codeText(app)

    /** ① 每一处写 `domain` 的地方都配一枚戳（初值那两枚写在 `remember` 里，不算一处改动，故不计数）。 */
    @Test
    fun 写域的每一处都必须同时写那枚戳() {
        val writes = DesktopSourceTree.codeOccurrences(app, "domain = ")
        val stamps = DesktopSourceTree.codeOccurrences(app, "domainStamp = ")
        assertTrue("一处都没写 domain（那这条判据是空的）", writes >= 4)
        assertEquals(
            "写 domain 的地方与写那枚戳的地方不配对（$writes 处写域 / $stamps 处写戳）——" +
                "少一枚戳就是" + "「落点被改走而读数说不出是谁改的」",
            writes,
            stamps,
        )
    }

    /** ② 读数把两枚戳都念出来：域是谁改的、浮层是谁开的。 */
    @Test
    fun 落点读数念得出是谁改的() {
        val line = code.lines().firstOrNull { it.contains("D_落点") }.orEmpty()
        assertTrue("读数里没有域那一枚戳：$line", line.contains("domainStamp"))
        assertTrue("读数里没有浮层那一枚戳：$line", line.contains("pickStamp"))
        assertTrue("初值那一档「启动」不见了（那才是「没人改过」那一句）：", code.contains("mutableStateOf(\"启动\")"))
    }

    /** ③ 墙上点卡那一路必须有自己的一句（否则浮层自己开了只会念"由=启动"，那是假读数）。 */
    @Test
    fun 墙上点卡那一路有自己的读数() {
        assertTrue("墙上点卡那一路没写戳", code.contains("pickStamp = \"墙上点卡\""))
        assertTrue("步进那一路没写戳", code.contains("pickStamp = \"浮层内步进\""))
        assertTrue("关掉那一路没写戳", code.contains("pickStamp = \"关掉浮层\""))
    }
}
