package com.venera.compose.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 发布日志的 Markdown 子集解析（批 1）。
 *
 * fixture 用的是本包 v2.0.3 那份 body 的真实形状（标题、加粗、行内码、三列表格、引用块、围栏、链接），
 * 不是理想化的教科书 Markdown。它要钉住的是**界面上不会再出现 `#` 和竖线**这件事，
 * 以及"表格降成一行、围栏保留原字"这两条刻意的取舍。
 */
class UpdateChangelogTest {

    private fun blocks(md: String) = changelogBlocks(md)

    @Test
    fun `标题按级认出来并剥掉井号`() {
        val out = blocks("# venera-miuix v2.0.3\n\n## 选择哪个安装包\n\n### 小注")
        assertEquals(
            listOf(
                ChangelogBlock.Heading("venera-miuix v2.0.3", 1),
                ChangelogBlock.Heading("选择哪个安装包", 2),
                ChangelogBlock.Heading("小注", 3),
            ),
            out,
        )
    }

    @Test
    fun `四级以上夹到三级而不是原样出`() {
        val out = blocks("###### 太深了")
        assertEquals(3, (out.single() as ChangelogBlock.Heading).level)
    }

    @Test
    fun `粗体行内码链接都剥记号剩下要读的字`() {
        // 真实 body 里 **… 有 24 处、`…` 12 处、链接 2 条。
        val out = blocks("**可直接覆盖安装** v2.0，`versionName` 为 `2.0.3`。见 [发布页](https://github.com/x/y)。")
        assertEquals(
            ChangelogBlock.Paragraph("可直接覆盖安装 v2.0，versionName 为 2.0.3。见 发布页 (https://github.com/x/y)。"),
            out.single(),
        )
    }

    @Test
    fun `表格的分隔行不进正文而数据行降成一行`() {
        val out = blocks(
            """
            | 文件名 | 适用设备 | 体积 |
            | --- | --- | --- |
            | `venera-miuix-2.0.3.apk` | 通用（含全部 ABI，不确定机型选这个） | 13.8 MB |
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                ChangelogBlock.Paragraph("文件名 — 适用设备 — 体积"),
                ChangelogBlock.Paragraph("venera-miuix-2.0.3.apk — 通用（含全部 ABI，不确定机型选这个） — 13.8 MB"),
            ),
            out,
        )
    }

    @Test
    fun `引用块的连续两行并成一块且不带尖角号`() {
        val out = blocks("> 系统要求 Android 13（API 33）及以上。\n> 本版四份产物的签名证书 SHA-256 均为 `62b89c49`。")
        assertEquals(
            ChangelogBlock.Quote("系统要求 Android 13（API 33）及以上。 本版四份产物的签名证书 SHA-256 均为 62b89c49。"),
            out.single(),
        )
    }

    @Test
    fun `列表逐条成块不并句`() {
        val out = blocks("- **画廊取数层与 Android 平台解耦**：不再点名 Android 类型。\n- **判据层增加用例**。")
        assertEquals(
            listOf(
                ChangelogBlock.Bullet("画廊取数层与 Android 平台解耦：不再点名 Android 类型。"),
                ChangelogBlock.Bullet("判据层增加用例。"),
            ),
            out,
        )
    }

    @Test
    fun `换行折的普通段落并成一句而列表与标题断开`() {
        val out = blocks(
            """
            第一行接着
            第二行还是同一句

            ## 下一节

            - 项一
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                ChangelogBlock.Paragraph("第一行接着 第二行还是同一句"),
                ChangelogBlock.Heading("下一节", 2),
                ChangelogBlock.Bullet("项一"),
            ),
            out,
        )
    }

    @Test
    fun `围栏代码保留原字并剥掉两侧的三反引号`() {
        val out = blocks("前面的话\n```\nadb shell dumpsys | grep activeMode\n另一行\n```\n后面的话")
        assertEquals(
            listOf(
                ChangelogBlock.Paragraph("前面的话"),
                ChangelogBlock.Paragraph("adb shell dumpsys | grep activeMode\n另一行"),
                ChangelogBlock.Paragraph("后面的话"),
            ),
            out,
        )
    }

    @Test
    fun `围栏内不解析记号`() {
        // 代码块里出现 * 或 | 是常事，把它们当 Markdown 会把代码念错。
        val out = blocks("```\n| 这行像表格 |\n**这行像粗体**\n```")
        assertEquals(ChangelogBlock.Paragraph("| 这行像表格 |\n**这行像粗体**"), out.single())
    }

    @Test
    fun `只剩记号没有内容的那些行不产出空块`() {
        // 空块会让界面上多出一条没有内容的高亮行，读起来像坏了。
        assertTrue(blocks("").isEmpty())
        assertTrue(blocks("\n\n  \n").isEmpty())
        assertTrue(blocks("## \n>\n|").isEmpty())
    }

    @Test
    fun `crlf 与行尾空白不多出空行`() {
        val out = blocks("# 甲\r\n\r\n正文  \r\n")
        assertEquals(listOf(ChangelogBlock.Heading("甲", 1), ChangelogBlock.Paragraph("正文")), out)
    }
}
