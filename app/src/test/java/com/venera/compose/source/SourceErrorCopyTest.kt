package com.venera.compose.source

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 源 / 网络异常 → 屏上文案的唯一出口。
 *
 * 2026-10-14 真机读数（手机档）驱动的三条硬判据：
 *  1. **原始 message 绝不上屏**。包子漫画那屏直接念 "Invalid status code: 403"，
 *     拷贝漫画那屏念 "The coroutine scope left the composition" —— 都是写给开发者看的，
 *     用户看不懂，只能感到"被拦住了还不知为什么"。
 *  2. **不出现代码**。状态码（210/403/429/5xx）、异常类名、`undefined`、方括号这些
 *     一律不许进屏；要说得像人话，还要给出下一步。
 *  3. **取消不是错误**。`CancellationException` 继承自 `Exception`，被
 *     `catch (e: Exception)` 收走就会变成"加载失败" —— [isCancellation] 就是那道闸。
 */
class SourceErrorCopyTest {

    /* ── 判据 1：原样 message 不上屏 ── */

    @Test
    fun 裸状态码原样不出现在屏上() {
        val msg = humanizeSourceError("Invalid status code: 210")
        assertFalse("屏上不许出现 210", msg.contains("210"))
        assertFalse("屏上不许出现 Invalid", msg.contains("Invalid"))
        assertFalse("屏上不许出现 status code", msg.contains("status code"))
    }

    @Test
    fun 英文异常类名与占位词不许漏到屏上() {
        val raws = listOf(
            "java.net.SocketTimeoutException: failed to connect",
            "TypeError: undefined is not an object",
            "Exception: null",
            "UnknownHostException: Unable to resolve host \"example.com\"",
        )
        for (raw in raws) {
            val msg = humanizeSourceError(raw)
            assertTrue("空文案：$raw", msg.isNotBlank())
            for (token in listOf("Exception", "undefined", "null", "TypeError", "java.")) {
                assertFalse("「$token」漏到屏上（来自 $raw）：$msg", msg.contains(token))
            }
            assertFalse("屏上不该出现方括号（来自 $raw）：$msg", msg.contains("[") || msg.contains("]"))
        }
    }

    /* ── 判据 2：说人话 + 给下一步 ── */

    @Test
    fun 登录态失效指向重新登录() {
        for (raw in listOf("Login expired", "未登录", "請先登錄", "Unauthorized", "not logged in")) {
            val msg = humanizeSourceError(raw)
            assertTrue("没指到「登录」（来自 $raw）：$msg", msg.contains("登录"))
        }
    }

    @Test
    fun 支持登录的源遇到四零三说成要先登录() {
        val msg = humanizeSourceError("Invalid status code: 403", loginable = true)
        assertTrue(msg, msg.contains("登录"))
        assertFalse(msg, msg.contains("403"))
    }

    @Test
    fun 不支持登录的源遇到四零三不甩锅给用户网络() {
        val msg = humanizeSourceError("Invalid status code: 403", loginable = false)
        assertFalse(msg, msg.contains("403"))
        assertFalse("不许说成「你的网络」（包子漫画那次就是这么误诊的）：$msg", msg.contains("网络"))
        assertTrue("要给下一步：$msg", msg.contains("换一颗源"))
    }

    @Test
    fun 限流类文案劝等待而不是失败() {
        for (raw in listOf("访问过于频繁", "Too many requests", "rate limit exceeded", "Invalid status code: 429", "210")) {
            val msg = humanizeSourceError(raw)
            assertTrue("没给「等一等」的下一步（来自 $raw）：$msg", msg.contains("等") || msg.contains("歇"))
        }
    }

    @Test
    fun 网络与超时各有自己的自检动作() {
        assertTrue(humanizeSourceError("Unable to resolve host \"a.b\"").contains("连接"))
        assertTrue(humanizeSourceError("read timeout").contains("网络"))
        assertTrue(humanizeSourceError("连接超时").contains("网络"))
    }

    @Test
    fun 服务端五叉明确说不怪用户() {
        for (raw in listOf("HTTP 500", "502 Bad Gateway", "503", "504 Gateway Timeout")) {
            val msg = humanizeSourceError(raw)
            assertTrue("没把锅从用户身上拿走（来自 $raw）：$msg", msg.contains("跟你没关系"))
        }
    }

    @Test
    fun 源脚本跟不上改版时建议换源或等更新() {
        for (raw in listOf("exploadMixed is not a function", "Cannot read property 'data' of undefined", "接口未返回")) {
            val msg = humanizeSourceError(raw)
            assertTrue("没给换源/等更新（来自 $raw）：$msg", msg.contains("换一颗源") || msg.contains("等它更新"))
        }
    }

    /* ── 判据 3：取消不是错误 ── */

    @Test
    fun 取消异常被认出来() {
        assertTrue(isCancellation(CancellationException("job was cancelled")))
        assertTrue("真机那条原话", isCancellation(RuntimeException("The coroutine scope left the composition")))
        assertTrue("包在 cause 链里也要认出来", isCancellation(RuntimeException("boom", CancellationException())))
        assertTrue(isCancellation(RuntimeException("Scope was cancelled")))
    }

    @Test
    fun 普通异常不会被误判成取消() {
        assertFalse(isCancellation(RuntimeException("Invalid status code: 403")))
        assertFalse(isCancellation(null))
    }

    @Test
    fun 取消即使漏进翻译层也只给通用句() {
        val msg = humanizeSourceError("The coroutine scope left the composition")
        assertEquals(GENERIC, msg)
        assertFalse(msg.contains("composition"))
    }

    /* ── 兜底 ── */

    @Test
    fun 翻不出来时给通用句而不是原样保留() {
        assertEquals(GENERIC, humanizeSourceError(null as String?))
        assertEquals(GENERIC, humanizeSourceError(""))
        assertEquals(GENERIC, humanizeSourceError("   "))
        assertEquals("旧口径「翻不出来原样保留」必须已废除", GENERIC, humanizeSourceError("boom"))
    }

    @Test
    fun Throwable重载与字符串重载一致() {
        assertEquals(
            humanizeSourceError("Invalid status code: 403", loginable = true),
            humanizeSourceError(RuntimeException("Invalid status code: 403"), loginable = true),
        )
        assertEquals(GENERIC, humanizeSourceError(null as Throwable?))
    }
}
