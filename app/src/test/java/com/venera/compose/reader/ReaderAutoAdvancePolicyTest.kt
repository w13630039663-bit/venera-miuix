package com.venera.compose.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 巡航「本话到底之后干什么」的判据。
 *
 * 这三条分支各钉一刀的理由：改动前他们是散在协程里的两处代码（翻页档写 `!turnToNextPage() 就停`，
 * 条漫档压根不判到底），收成一颗纯函数后要钉住的是**优先级**与**逐档表态**这两件事 ——
 * 顺序错了会变成"取页表期间连切好几话"，档错了会变成"按钮亮着、按下去协程立刻退出"那种假开关。
 */
class ReaderAutoAdvancePolicyTest {

    @Test fun loadingBeatsHasNextChapter() {
        // 正在取页表：哪怕后面还有话也不许再切，否则每帧都发一次换章请求。
        assertEquals(
            ReaderAutoStep.Continue,
            autoStepAtChapterEnd(hasNextChapter = true, chapterLoading = true),
        )
    }

    @Test fun loadingWithoutNextChapterStillContinues() {
        // 加载中也说"接着推"：停不停由协程那侧等落地后再判，这里不停。
        assertEquals(
            ReaderAutoStep.Continue,
            autoStepAtChapterEnd(hasNextChapter = false, chapterLoading = true),
        )
    }

    @Test fun hasNextChapterAdvances() {
        assertEquals(
            ReaderAutoStep.NextChapter,
            autoStepAtChapterEnd(hasNextChapter = true, chapterLoading = false),
        )
    }

    /**
     * 「用户关了续章」被折进 `hasNextChapter = false` 这一档：结果与全书到头完全一样（都停）。
     * 这一条钉的是"关了续章但还切了一话"那种只有一边改对的漏法。
     */
    @Test fun noNextChapterStops() {
        assertEquals(
            ReaderAutoStep.Stop,
            autoStepAtChapterEnd(hasNextChapter = false, chapterLoading = false),
        )
    }

    /** 五档逐一表态：三支参与巡航，两支不参与（改动前那两支也不参与，本次不扩）。 */
    @Test fun cruiseSupportedPerMode() {
        assertTrue(autoCruiseSupported(ReaderReadingMode.VERTICAL_CONTINUOUS))
        assertTrue(autoCruiseSupported(ReaderReadingMode.HORIZONTAL_LTR))
        assertTrue(autoCruiseSupported(ReaderReadingMode.HORIZONTAL_RTL))
        assertFalse(autoCruiseSupported(ReaderReadingMode.HORIZONTAL_CONTINUOUS))
        assertFalse(autoCruiseSupported(ReaderReadingMode.DOUBLE_PAGE))
    }

    /**
     * 五档都得在这张表里，新增模式时上面那条会漏判。
     * 比集合不比顺序：`ReaderReadingMode` 的声明顺序（条漫、RTL、LTR…）不是语义顺序，
     * 钉顺序等于给以后每次调枚举表都添一条假红。
     */
    @Test fun allFiveModesAreAccountedFor() {
        assertEquals(5, ReaderReadingMode.entries.size)
        val supported = ReaderReadingMode.entries.filter { autoCruiseSupported(it) }.toSet()
        assertEquals(
            setOf(
                ReaderReadingMode.VERTICAL_CONTINUOUS,
                ReaderReadingMode.HORIZONTAL_RTL,
                ReaderReadingMode.HORIZONTAL_LTR,
            ),
            supported,
        )
    }
}
