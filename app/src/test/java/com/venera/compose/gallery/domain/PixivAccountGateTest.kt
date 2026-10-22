package com.venera.compose.gallery.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixiv 账号页那扇闸门的四档（方案 `docs/rounds/gallery-pixiv-account-page-2026-10-19.html` §七）。
 *
 * 四档在屏上长得几乎一样（一段空墙），出路却完全不同。这一颗用例钉的是**分叉本身**：
 *
 * 1. 四句话两两不同 —— 合成一句"没有内容"，用户就不知道该点哪儿；
 * 2. 「登录着但没记下用户号」那一档**不许**跟着说"你还没登录" —— 顶栏那颗头像同一屏说的是"已登录"，
 *    两句话打架会被读成"应用自己不知道自己的状态"（本仓真机复现过的一族假读数）；
 * 3. 出路不在这一屏的那两档 `retryIsMeaningful` 必须是 false —— 给它摆一枚「重试」，
 *    按下去还是同一句话，那是本仓反复立案的**假开关**。
 *
 * 最后一条是文案尺：这四句是直接上屏的字，星号、反引号、内部黑话一个都不许出现
 * （判的是枚举里那四个**运行期的值**，不是源码文本 —— 不会因为别的文件里有一句代码就被带红）。
 */
class PixivAccountGateTest {

    private val all = listOf(
        PixivAccountGate.Ready,
        PixivAccountGate.Measuring,
        PixivAccountGate.NotSignedIn,
        PixivAccountGate.MissingUserId,
        PixivAccountGate.BearerUnavailable,
    )

    @Test
    fun `票据还没量到的那一档既不说没接上也不放行`() {
        // 首帧 `bearerOrNull()` 必然没回来。给 true 会让三段墙各发一趟必被 401 的请求；
        // 给 false 会让没登录的用户先看见一句"这会儿没接上"。所以单独一档。
        assertFalse("量到之前不许说已登录态可用", PixivAccountGate.Measuring.retryIsMeaningful)
        assertTrue("这一档要有自己的话", PixivAccountGate.Measuring.screenLine != null)
        assertTrue(
            "那一句不许与 BearerUnavailable 撞车：${PixivAccountGate.Measuring.screenLine}",
            PixivAccountGate.Measuring.screenLine != PixivAccountGate.BearerUnavailable.screenLine,
        )
    }

    @Test
    fun `三档空态各说一句不一样的话`() {
        val lines = listOf(
            PixivAccountGate.NotSignedIn.screenLine,
            PixivAccountGate.MissingUserId.screenLine,
            PixivAccountGate.BearerUnavailable.screenLine,
            PixivAccountGate.Measuring.screenLine,
        )
        assertEquals(4, lines.map { it!! }.distinct().size)
        // Ready 是正常那一档，它没有话要说（有话就被读成出事了）。
        assertEquals(null, PixivAccountGate.Ready.screenLine)
    }

    @Test
    fun `没记下用户号那一档不许说没登录`() {
        // 顶栏那颗头像此时说的是"已登录"。
        val missing = PixivAccountGate.MissingUserId.screenLine!!
        assertFalse(missing.contains("没登录"))
        assertFalse(missing.contains("未登录"))
        assertTrue(missing.contains("用户号"))
    }

    @Test
    fun `出路不在这一屏的两档不许摆重试`() {
        assertFalse(PixivAccountGate.NotSignedIn.retryIsMeaningful)
        assertFalse(PixivAccountGate.MissingUserId.retryIsMeaningful)
        // 票据这一刻取不到：再点一次确实可能换回结果。
        assertTrue(PixivAccountGate.BearerUnavailable.retryIsMeaningful)
        assertTrue(PixivAccountGate.Ready.retryIsMeaningful)
    }

    @Test
    fun `判定的优先序是先看有没有登录`() {
        // 没登录时 bearer 必然也没有；此时若先问 bearer，用户会被指向"重试"而真正出路在别处。
        assertEquals(PixivAccountGate.NotSignedIn, pixivAccountGate(signedIn = false, userId = null, bearerReady = false))
        assertEquals(PixivAccountGate.MissingUserId, pixivAccountGate(signedIn = true, userId = null, bearerReady = true))
        assertEquals(PixivAccountGate.BearerUnavailable, pixivAccountGate(signedIn = true, userId = "123", bearerReady = false))
        assertEquals(PixivAccountGate.Ready, pixivAccountGate(signedIn = true, userId = "123", bearerReady = true))
    }

    @Test
    fun `记成空串的用户号算没记下`() {
        // 老版本的记录里那一格可能是空串而不是 null；只判 null 会放行一趟带空用户号的请求，
        // 站方回什么都不知道，屏上就是一句 401。
        assertEquals(PixivAccountGate.MissingUserId, pixivAccountGate(signedIn = true, userId = "   ", bearerReady = true))
        assertEquals(PixivAccountGate.MissingUserId, pixivAccountGate(signedIn = true, userId = "", bearerReady = true))
    }

    @Test
    fun `四档的屏上话都不带机器味`() {
        for (gate in all) {
            val line = gate.screenLine ?: continue
            for (forbidden in listOf("`", "*", "#", "Bearer", "票据", "restrict", "app-api", "401", "null")) {
                assertFalse("$forbidden 不许出现在屏上那句话里（$line）", line.contains(forbidden))
            }
        }
    }

    @Test
    fun `话与把手对得上`() {
        // 摆得出「重试」的那一档才许说"再点一次"；不许摆的那两档跟着说就是骗用户再点。
        assertTrue(PixivAccountGate.BearerUnavailable.screenLine!!.contains("稍后再试"))
        assertFalse(PixivAccountGate.NotSignedIn.screenLine!!.contains("再点一次"))
        assertFalse(PixivAccountGate.MissingUserId.screenLine!!.contains("再点一次"))
    }
}
