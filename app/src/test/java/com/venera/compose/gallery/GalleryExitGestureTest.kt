package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryExitGestureCommand
import com.venera.compose.gallery.domain.GalleryExitGestureEvent
import com.venera.compose.gallery.domain.GalleryExitGesturePhase
import com.venera.compose.gallery.domain.clampExitGestureProgress
import com.venera.compose.gallery.domain.nextExitGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跟手返回那四道门闩的判据。每条都对应一个**真实踩过的症状**（写在 `GalleryExitGesture.kt` 头注），
 * 而这些症状在屏上是一帧的事、在单测里是状态序列的事 —— 只有这一层钉得住。
 */
class GalleryExitGestureTest {

    private val idle = GalleryExitGesturePhase.Idle
    private val arming = GalleryExitGesturePhase.Arming
    private val flying = GalleryExitGesturePhase.Flying
    private val committed = GalleryExitGesturePhase.Committed

    private fun step(
        phase: GalleryExitGesturePhase,
        last: Float = 0f,
        event: GalleryExitGestureEvent,
    ) = nextExitGesture(phase, last, event)

    @Test
    fun `只有稳态能被起手激活，且起手只做备料`() {
        val d = step(idle, event = GalleryExitGestureEvent.Started(0f))
        assertEquals(arming, d.phase)
        assertEquals(listOf<GalleryExitGestureCommand>(GalleryExitGestureCommand.Prepare), d.commands)
    }

    @Test
    fun `没有起手的杂散进度不激活任何东西`() {
        // 澎湃 / HyperOS 会乱序补发；这一条就是把"凭空启动一次假手势"钉在门外。
        for (p in listOf(0f, 0.3f, 1f, -0.5f, 1.5f)) {
            val d = step(idle, event = GalleryExitGestureEvent.Progress(p))
            assertEquals("进度 $p 不该把稳态拽进返回", idle, d.phase)
            assertTrue("进度 $p 不该产出指令：${d.commands}", d.commands.isEmpty())
        }
    }

    @Test
    fun `像素没到的那几帧视觉钉在 0`() {
        val a = step(idle, event = GalleryExitGestureEvent.Started(0f)).phase
        val d = step(a, event = GalleryExitGestureEvent.Progress(0.42f))
        assertEquals(arming, d.phase)
        assertTrue("arming 期间不许动画面，否则屏上先空一下：${d.commands}", d.commands.isEmpty())
    }

    @Test
    fun `像素一到就补到手已经拖到的那一档`() {
        // 手指已经拖到 0.6 而帧才回来 —— 补到 0.6，不是从 0 重飞。
        val d = step(arming, last = 0.6f, event = GalleryExitGestureEvent.PixelsReady)
        assertEquals(flying, d.phase)
        assertEquals(
            listOf<GalleryExitGestureCommand>(GalleryExitGestureCommand.SetVisualTarget(0.6f)),
            d.commands,
        )
    }

    @Test
    fun `飞着的时候进度跟着手指且被钳住`() {
        assertEquals(0f, clampExitGestureProgress(-0.5f), 0f)
        assertEquals(1f, clampExitGestureProgress(1.5f), 0f)
        for (p in listOf(0.1f, 0.5f, 0.9f)) {
            val d = step(flying, event = GalleryExitGestureEvent.Progress(p))
            assertEquals(flying, d.phase)
            assertEquals(
                listOf<GalleryExitGestureCommand>(GalleryExitGestureCommand.SetVisualTarget(p)),
                d.commands,
            )
        }
        val over = step(flying, event = GalleryExitGestureEvent.Progress(1.5f))
        assertEquals(
            "越界要钳住，不许外推出 1.5 的缩放",
            listOf<GalleryExitGestureCommand>(GalleryExitGestureCommand.SetVisualTarget(1f)),
            over.commands,
        )
    }

    @Test
    fun `确认那笔是先落到位再收尾，次序钉死`() {
        // 只钉"两条都在"会放过并发执行 —— 半路抹掉飞行体就是这么来的。
        val d = step(flying, last = 0.8f, event = GalleryExitGestureEvent.Commit)
        assertEquals(committed, d.phase)
        assertEquals(
            listOf(
                GalleryExitGestureCommand.SetVisualTarget(1f),
                GalleryExitGestureCommand.Finish,
            ),
            d.commands,
        )
    }

    @Test
    fun `像素没到就松手确认时直接交给系统，不硬飞`() {
        val d = step(arming, last = 0.3f, event = GalleryExitGestureEvent.Commit)
        assertEquals(committed, d.phase)
        assertEquals(listOf<GalleryExitGestureCommand>(GalleryExitGestureCommand.Finish), d.commands)
    }

    @Test
    fun `确认之后补发的起手 进度 取消一律丢弃`() {
        // 这一条是"首次返回误变侧滑样式 / 双层重影 / 返回后列表一片空白"那一族的闸门。
        val followUps = listOf<GalleryExitGestureEvent>(
            GalleryExitGestureEvent.Started(0f),
            GalleryExitGestureEvent.Progress(0.2f),
            GalleryExitGestureEvent.Progress(1f),
            GalleryExitGestureEvent.Cancel,
            GalleryExitGestureEvent.PixelsReady,
            GalleryExitGestureEvent.PixelsFailed,
            GalleryExitGestureEvent.BackWithoutProgress,
            GalleryExitGestureEvent.Commit,
        )
        for (e in followUps) {
            val d = step(committed, last = 1f, event = e)
            assertEquals("事件 $e 之后状态被翻掉了", committed, d.phase)
            assertTrue("事件 $e 之后还产出指令：${d.commands}", d.commands.isEmpty())
        }
    }

    @Test
    fun `真取消才回弹，并且把玻璃底挂回来`() {
        for (phase in listOf(arming, flying)) {
            val d = step(phase, last = 0.35f, event = GalleryExitGestureEvent.Cancel)
            assertEquals(idle, d.phase)
            assertEquals(
                listOf(
                    GalleryExitGestureCommand.SetVisualTarget(0f),
                    GalleryExitGestureCommand.Restore,
                ),
                d.commands,
            )
        }
    }

    @Test
    fun `回弹之后又能重新起手`() {
        // 手指在同一趟里"拖出去又收回来再拖出去"是常事：闸门不能把自己焊死。
        val back = step(flying, last = 0.4f, event = GalleryExitGestureEvent.Cancel)
        val again = step(back.phase, event = GalleryExitGestureEvent.Started(0f))
        assertEquals(arming, again.phase)
        assertEquals(listOf<GalleryExitGestureCommand>(GalleryExitGestureCommand.Prepare), again.commands)
    }

    @Test
    fun `没有进度事件的一笔返回只交自驱那一趟`() {
        // 自驱那条自带备料与退路：这里再配一次 Prepare 就是同一起手截两帧，白烧一次 PixelCopy。
        for (phase in listOf(idle, arming, flying)) {
            val d = step(phase, event = GalleryExitGestureEvent.BackWithoutProgress)
            assertEquals("起点档位 $phase 的归属错了", committed, d.phase)
            assertEquals(
                listOf<GalleryExitGestureCommand>(GalleryExitGestureCommand.FlySelfDriven),
                d.commands,
            )
        }
    }

    @Test
    fun `拿不到像素时收尾但不把玻璃底挂回来`() {
        // 页面马上 finish，Restore 那一档会把已经摘掉的模糊又加回去 —— 白做一次，还可能闪一下。
        val d = step(arming, event = GalleryExitGestureEvent.PixelsFailed)
        assertEquals(committed, d.phase)
        assertEquals(listOf<GalleryExitGestureCommand>(GalleryExitGestureCommand.Finish), d.commands)
    }

    @Test
    fun `一整笔手势从头走到提交`() {
        var phase = idle
        var last = 0f
        val trace = ArrayList<GalleryExitGesturePhase>()
        trace.add(phase)
        val seq = listOf<GalleryExitGestureEvent>(
            GalleryExitGestureEvent.Started(0f),
            GalleryExitGestureEvent.Progress(0.15f),
            GalleryExitGestureEvent.PixelsReady,
            GalleryExitGestureEvent.Progress(0.4f),
            GalleryExitGestureEvent.Progress(0.7f),
            GalleryExitGestureEvent.Progress(0.95f),
            GalleryExitGestureEvent.Commit,
        )
        for (e in seq) {
            if (e is GalleryExitGestureEvent.Progress) last = e.progress
            val d = step(phase, last = last, event = e)
            phase = d.phase
            trace.add(phase)
        }
        assertEquals(
            listOf(idle, arming, arming, flying, flying, flying, flying, committed),
            trace,
        )
    }

    @Test
    fun `重复的起手事件不会二次备料`() {
        val d = step(arming, event = GalleryExitGestureEvent.Started(0.2f))
        assertEquals(arming, d.phase)
        assertTrue("重复起手不该再 Prepare 一次：${d.commands}", d.commands.isEmpty())
    }
}
