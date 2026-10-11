package com.venera.desktop

import com.venera.desktop.gallery.data.DesktopDictionaryPresence
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 启动那一行要说得出**这台机器与这个包给不给**（第五十八批）。
 *
 * ## 这一格补的是什么
 *
 * 两件事今天只有"屏上的行为"这一种读法：
 * - 剪贴板：批 54 那枚 `desktopCanCopyImage()` 只决定「复制图片」摆不摆 —— 不摆时屏上另有一句，
 *   但**不带参数的启动**里 stdout 上一个字都没有，事后翻日志分不清"这台机器不给"与"那枚没接上"。
 * - 词典：批 40 把整份简繁表搬进包里，此后**没有任何一行读数说过它还在不在**。
 *   而这一族恰恰有过"接上了而走空表"的前科（批 39/40 那一串）—— 打包改一行就静默退回原文展示，
 *   屏上看不出任何异常。
 *
 * ## 三条口径
 *
 * 1. **三态不压成两态**：包里有（报行数）/ 枚举成功而清单是空的（包里没有）/ 枚举本身抛了（数不出 + 异常类型）
 *    —— 「没取到 / 没有 / 没接」是三句话那条；把后两种压成一句就把打包残缺读成"今天没查到词"。
 * 2. **措辞住一处**：那几句只在读数件里，集成方不许自己拼（判据 ⑤）。
 * 3. **命名规则住一处**：`gallery_tags_<行数>.sqlite` 那套解析只许住在资产那颗件里（判据 ⑥），
 *    读数件吃的是解析**结果**，否则资产改名而读数还按旧规则数，念出来的就是假数。
 */
class DesktopCapabilityReadingTest {

    // ① 两枚能力 × 各自形状：六句互不撞，且都能被 grep 挑出来。
    @Test
    fun `六句形状互不撞`() {
        val shapes = listOf(
            DesktopDictionaryPresence.InPackage(79415),
            DesktopDictionaryPresence.NotInPackage,
            DesktopDictionaryPresence.Unreadable(IllegalStateException("包不完整")),
        )
        val lines = buildList {
            for (ok in listOf(true, false)) {
                shapes.forEach { add(desktopCapabilityReading(ok, it)) }
            }
        }
        assertEquals("两枚剪贴板形状 × 三本词典账 = 6 句：" + lines.size, 6, lines.size)
        assertEquals("有两句撞在一起：" + lines, lines.size, lines.distinct().size)
        assertTrue("每行都要带前缀：" + lines, lines.all { it.startsWith("D_能力") })
        assertTrue("每行都要念得出两枚：" + lines, lines.all { it.contains("剪贴板=") && it.contains("词典=") })
    }

    // ② 剪贴板那一句只说自己量到的那一层：`isHeadless()` 说的是"有没有那条 API"，不是"写一定成"。
    @Test
    fun `剪贴板那一句只说自己量到的那一层`() {
        val given = desktopCapabilityReading(true, DesktopDictionaryPresence.InPackage(10))
        val denied = desktopCapabilityReading(false, DesktopDictionaryPresence.InPackage(10))
        println("CAP_$given")
        println("CAP_$denied")
        assertTrue("有通路那句：" + given, given.contains("有通路"))
        assertTrue(
            "没通路那句要念得出量的是哪一枚：" + denied,
            denied.contains("无通路") && denied.contains("headless"),
        )
        assertFalse(
            "不许念「可写」—— 本批活体抓到这句是假话：这一侧 isHeadless=false 而真写被 OS 拒了八次（坑 ㊛）：" + given,
            given.contains("可写"),
        )
    }

    // ③ 「包里没有」与「数不出」是两句话，两两不许混（压成一句就把残缺的包读成没查到词）。
    @Test
    fun `词典那三本账念三句话`() {
        val inPkg = desktopCapabilityReading(true, DesktopDictionaryPresence.InPackage(79415))
        val absent = desktopCapabilityReading(true, DesktopDictionaryPresence.NotInPackage)
        val broken = desktopCapabilityReading(
            true,
            DesktopDictionaryPresence.Unreadable(IllegalStateException("classpath 上没有锚点资源")),
        )
        println("CAP_有=$inPkg")
        println("CAP_无=$absent")
        println("CAP_坏=$broken")
        assertTrue("有词典那句要念得出行数：" + inPkg, inPkg.contains("79415行"))
        assertTrue("清单空那句：" + absent, absent.contains("包里没有"))
        assertTrue("枚举抛了那句：" + broken, broken.contains("数不出"))
        assertFalse("空清单不许念成行数 0：" + absent, absent.contains("0行"))
        assertFalse("数不出不许念成包里没有：" + broken, broken.contains("包里没有"))
        assertFalse("包里没有不许念成数不出：" + absent, absent.contains("数不出"))
    }

    // ④ 数不出那句带异常类型（复用批 53 那三颗原子），但不带消息 —— 消息里可能有路径。
    @Test
    fun `数不出那句只带类型不带消息`() {
        val line = desktopCapabilityReading(
            true,
            DesktopDictionaryPresence.Unreadable(IllegalStateException("锚点 /x/venera-init.js 不在")),
        )
        assertTrue("那句要念得出异常类型：" + line, line.contains("IllegalStateException"))
        assertFalse("消息里的路径不许进 stdout：" + line, line.contains("venera-init.js"))
    }

    // ⑤ 出口只许一处：集成方自己拼一句 `D_能力` 就是第二份口径（将来只改一处）。
    @Test
    fun `启动那行只从读数件里念`() {
        val main = DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt")
        assertEquals(
            "集成方自己拼了 `D_能力` 那句（该走读数件）",
            0,
            DesktopSourceTree.codeOccurrences(main, "\"D_能力"),
        )
        assertEquals("启动那行没走读数件（或走了两处）", 1, DesktopSourceTree.codeOccurrences(main, "desktopCapabilityReading("))
    }

    // ⑥ 词典的命名规则只许住一颗件：读数件里再写一遍，资产改名时读数会念假数。
    @Test
    fun `词典命名规则只许住一颗件`() {
        val offenders = DesktopSourceTree.desktopMainSources().filter {
            DesktopSourceTree.codeOccurrences(it, "gallery_tags_") > 0
        }
        assertEquals(
            "「gallery_tags_」这套命名规则写在了多颗文件里：" + offenders.map { it.name },
            listOf("DesktopTagDictionaryAsset.kt"),
            offenders.map { it.name },
        )
    }
}
