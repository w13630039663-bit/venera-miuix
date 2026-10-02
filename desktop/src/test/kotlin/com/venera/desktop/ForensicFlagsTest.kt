package com.venera.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 取证参数的判读（Task 6 第 4 件）。
 *
 * 这几条钉的是**形状**，不是打印：`-Pautofav=0` 必须判成关（原来是开，因为只看给没给）、
 * 两参同给必须有一句实话（原来是 favcheck 先 exitProcess、autofav 被静默吃掉）、
 * 不认识的 `--xxx` 必须被列出来（原来是无声忽略）。
 * 读数本身在 `:desktop:app` 的真实运行里另取（见 task-6 报告）。
 */
class ForensicFlagsTest {

    @Test
    fun 值为关就判成关而不是开() {
        for (off in listOf("0", "false", "off", "OFF ", "False")) {
            assertEquals("--autofav=$off 该判成关", false, ForensicFlags.state(listOf("--autofav=$off"), "--autofav"))
        }
    }

    @Test
    fun 没给参数是null只给名字是开() {
        assertNull(ForensicFlags.state(listOf("jm", "--proxy=127.0.0.1:7890"), "--autofav"))
        assertEquals(true, ForensicFlags.state(listOf("--autofav"), "--autofav"))
        for (on in listOf("1", "true", "on", "ON")) {
            assertEquals(true, ForensicFlags.state(listOf("--favcheck=$on"), "--favcheck"))
        }
    }

    @Test
    fun 读不懂的值按关处理不猜成开() {
        assertEquals(false, ForensicFlags.state(listOf("--autofav=maybe"), "--autofav"))
        assertEquals(false, ForensicFlags.state(listOf("--autofav="), "--autofav"))
    }

    @Test
    fun 两参同给必须说一句() {
        val both = listOf("--autofav=1", "--favcheck=1")
        val note = ForensicFlags.bothGivenNote(
            ForensicFlags.state(both, "--autofav"),
            ForensicFlags.state(both, "--favcheck"),
        )
        assertNotNull("同给必须有读数", note)
        assertTrue("要说清 autofav 不执行：$note", note!!.contains("只跑 favcheck") && note.contains("autofav"))
        // favcheck 值为关时不提前退出，这句得跟着变，不能照抄"只跑 favcheck"
        val favOff = ForensicFlags.bothGivenNote(true, ForensicFlags.state(listOf("--favcheck=0"), "--favcheck"))
        assertTrue("favcheck 关时要改口：$favOff", favOff!!.contains("值为关"))
        assertNull(ForensicFlags.bothGivenNote(true, null))
        assertNull(ForensicFlags.bothGivenNote(null, null))
    }

    @Test
    fun 拼错的参数一律被列出来() {
        assertEquals(
            listOf("--autofv=1", "--shots=a.png"),
            ForensicFlags.unknown(listOf("jm", "--autofv=1", "--shots=a.png", "--proxy=127.0.0.1:7890")),
        )
        // 认识的（含带值的）不许混进来
        assertEquals(
            emptyList<String>(),
            ForensicFlags.unknown(listOf("jm", "--autofav=0", "--favcheck=1", "--shot=a.png", "--proxy=null")),
        )
    }
}
