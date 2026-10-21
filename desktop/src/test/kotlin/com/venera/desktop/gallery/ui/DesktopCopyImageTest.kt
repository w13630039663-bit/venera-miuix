package com.venera.desktop.gallery.ui

import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「复制这张图」那一枚（批 54）。
 *
 * ## 为什么是这一格
 *
 * 浮层那排动作今天有四枚（收藏 / 复制链接 / 在浏览器打开原图 / 找同一张图），而桌面上把一张图
 * 交给**另一个应用**只有复制链接这一条 —— 对方要的是图，不是一条 URL。剪贴板写图是纯 JDK
 * （`Toolkit.systemClipboard` + `ImageSelection`），而"复制链接"那枚今天已经在用同一颗剪贴板，
 * 所以这一格不加依赖、不动共享面。
 *
 * ## 三条纪律各有一条尺子
 *
 * 1. **取字节走同一颗共用件**：批 51 那条"冷缓存要先取一趟"的教训只许住在一处，
 *    重述一遍就是将来只改一处（§0.1 那条"同一件事只许一把尺子"）。
 * 2. **读数不带地址**：站方那一路地址常带 `api_key`/`user_id`，而这一格正好最容易把 URL 打进读数里。
 *    做成**结构上拿不到**（那颗纯函数只吃数字），比"记得脱敏"硬。
 * 3. **按钮与取证种子共用同一枚 lambda**：`--copy` 那一枚存在的理由就是"按下去才出来的那一格
 *     拍不到"（禁鼠标），两处各写一遍的话，种子跑通的那条路就不是用户按的那条路。
 */
class DesktopCopyImageTest {

    // ① 四种失败各一句，两两不许相同（「没地址 / 没字节 / 不是图 / 这台机器不给剪贴板写图」是四句话）。
    @Test
    fun `复制图片的四种失败各念一句`() {
        val lines = DesktopCopyImageCause.entries.map { desktopCopyImageFallback(it) }
        assertEquals("四档要各一句", 4, lines.size)
        assertEquals("有两句撞在一起（塌成一句就是把四种病读成同一种）：" + lines, lines.size, lines.distinct().size)
        assertTrue("每句都得能上屏：" + lines, lines.all { it.isNotBlank() })
    }

    // ② 成功那一行念得出**两枚数**（字节与尺寸），且结构上带不出地址。
    @Test
    fun `成功那行念得出字节与尺寸而不带地址`() {
        val line = desktopCopyImageReadout(bytes = 1_234_567, width = 1000, height = 1000)
        println("CIP_$line")
        assertTrue("少了字节那枚：$line", line.contains("1234567"))
        assertTrue("少了尺寸：$line", line.contains("1000x1000"))
        assertTrue("前缀要能被 grep 挑出来：$line", line.startsWith("D_复制图片"))
        assertFalse("读数里出现了 http（地址带键，这一格绝不能念它）：$line", line.contains("http"))
    }

    // ③ 摆不摆那枚按钮只看能力位，不支持时那句说明要上屏 —— 照"在浏览器打开原图"那一族的形状。
    // ④ 取字节只许有一处实现：播放器与复制这一路共用同一颗件，谁都不许自带一份冷缓存逻辑。
    // ⑤ 按钮与种子共用同一枚 lambda，而"把图交给剪贴板"这件事全树只有一处。
    //
    // ⚠️ 这一条原本钉的是 `ImageSelection(` —— 编译器先把它否了：那枚类住在 `sun.datatransfer`
    // （JDK 内部件、没导出），2026-10-08 报的是 Unresolved reference。公开面上只剩自持一枚
    // `Transferable` 这一条，而它的身份证据是**交得出的那个 flavor** ⇒ 尺子跟着改成数 `imageFlavor`，
    // 并把"内部件没被 import"钉成一条负向对照（否则下一个人又会去抄那条不存在的短路）。
    // ⑥ `-Pcopy=` 那一跳三处都在（同一颗纪律第五次）：构建脚本 → 判读层 → 消费点。
    // ⑦ 凭据面：这一格拿的是**图的字节**，不许顺手把键那一路牵进来（对照：凭据件不被本批引用）。
    @Test
    fun `这一格不碰凭据`() {
        val copier = DesktopSourceTree.desktopUiSource("DesktopCopyImage.kt")
        val text = copier.readText()
        assertFalse("复制这一路读到了凭据件（这一格不该知道键）", text.contains(DesktopGalleryCredentials::class.simpleName!!))
        assertTrue("但取字节那一路是真的", text.contains("desktopCoilBytes"))
    }

    /**
     * ⑧ 失败也念得出一行（图证逼出来的那一半）。
     *
     * 第一版只有成功打读数，于是屏上念了「这台机器没给出…：IllegalArgumentException」而 stdout 一字没有 ——
     * "没跑到"与"跑到了而被拒"在 stdout 上同一个样，归因只能靠猜。三格各钉：有前缀、抛了东西时念得出
     * 类型与栈顶、没抛东西时那一格念"没有"而不是空着。
     * 消息那一格走批 53 那颗脱敏原子（键值不许进读数），这里连它一起量。
     */
    @Test
    fun `失败也念得出一行`() {
        val bare = desktopCopyImageRefusalReading(DesktopCopyImageCause.NO_BYTES, null)
        println("CIP_$bare")
        assertTrue("没成的那一行前缀不在：" + bare, bare.startsWith("D_复制图片 未成"))
        assertTrue("没抛东西时那一格要念得出：" + bare, bare.contains("栈=没有"))
        val thrown = IllegalStateException("cannot open  system clipboard")
        val line = desktopCopyImageRefusalReading(DesktopCopyImageCause.CLIPBOARD_UNAVAILABLE, thrown)
        println("CIP_$line")
        assertTrue("类型名没念出来：" + line, line.contains("IllegalStateException"))
        assertTrue("栈顶没念出来：" + line, Regex("栈=[A-Za-z0-9.<>]+\\.kt:\\d+").containsMatchIn(line))
        assertTrue("消息里的空白没压成一行：" + line, line.contains("cannot open system clipboard"))
        val keyed = IllegalStateException("https://x/ajax?api_key=SECRETVALUE&user_id=42")
        val scrubbed = desktopCopyImageRefusalReading(DesktopCopyImageCause.NO_BYTES, keyed)
        assertFalse("键值进了读数：" + scrubbed, scrubbed.contains("SECRETVALUE"))
        assertTrue("键名要留着（说得出是哪一枚被挡）：" + scrubbed, scrubbed.contains("api_key=***"))
        // 对照：真值没被抹平时那一句要念得出值（防止脱敏做成"整条消息都打星"）。
        assertTrue("对照组失效：普通消息也被抹平了", !line.contains("***"))
    }

    /** ⑨ 失败出口只有一颗：每一处没写成都得先念那一行，直接构造 `Refused` 就是第二份出口。 */
    @Test
    fun `失败只有一颗出口`() {
        val copier = DesktopSourceTree.desktopUiSource("DesktopCopyImage.kt")
        val code = DesktopSourceTree.codeText(copier)
        assertEquals("失败出口那颗件被写了第二份", 1, DesktopSourceTree.codeOccurrences(copier, "private fun refuse("))
        assertTrue("有一处没写成没走那颗出口（念不到那一行）：" + code,
            DesktopSourceTree.codeOccurrences(copier, "refuse(DesktopCopyImageCause.") >= 4)
        assertEquals(
            "`Refused` 在出口之外被直接构造了（那一处就不会念读数）",
            1,
            DesktopSourceTree.codeOccurrences(copier, "DesktopCopyImageOutcome.Refused("),
        )
    }
}
