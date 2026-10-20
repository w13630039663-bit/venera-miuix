package com.venera.compose.feature

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-14 UI 审查第一批的接线尺子：三件事都是"屏上说法与实现必须同轨"。
 *
 * 为什么扫源码而不是跑起来看：这三处的失败形态都是**静默**的 ——
 * 并发滑块改了没反应、封面取不到是一片纯黑、追更整页没有入口，
 * 没有一处会报错。等真机发现时已经发出去了。
 */
class UiAuditBatch1Test {

    private fun linesOf(packagePath: String, fileName: String): List<String> =
        RepoSources.kotlinFiles(packagePath)
            .first { it.name == fileName }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private val downloadManager = linesOf("com/venera/compose/download", "DownloadManager.kt")
    private val networkSettings = linesOf("com/venera/compose/feature/settings", "NetworkSettings.kt")
    private val settingsHost = linesOf("com/venera/compose/feature", "SettingsHost.kt")
    private val coverViewer = linesOf("com/venera/compose/feature", "CoverViewerScreen.kt")

    /** ① 并发上限每轮现取：不许再出现"构造时抄一份值"那种缓存。 */
    @Test
    fun `下载并发每轮队列现取偏好值`() {
        val gate = downloadManager.first { it.contains("activeJobs.size <") }
        assertTrue("闸门读的是缓存字段而不是当下偏好：$gate", gate.contains("currentConcurrency()"))
        assertTrue(
            "并发数又变成构造时抄一份了：这一条一红就说明滑块又会假",
            downloadManager.none { it.startsWith("private var maxConcurrency") },
        )
        val helper = downloadManager.first { it.contains("fun currentConcurrency()") }
        assertTrue("钳位丢了，盘上的 0 会让队列永远不放行：$helper", helper.contains("coerceIn(1, 16)"))
    }

    /** ② 设置页那句摘要必须与实现同轨（旧话"对新排进队列的任务生效"当年是假的）。 */
    @Test
    fun `并发滑块的说明不再承诺一个不存在的生效时机`() {
        val summary = networkSettings.first { it.contains("同时下载几章") }
        assertTrue("说明没写立即生效：$summary", summary.contains("立即生效"))
        assertFalse("又写回'新排进队列才生效'那套旧说法：$summary", summary.contains("对新排进队列的任务生效"))
    }

    /** ③ 追更整页要有入口：枚举、分发点、设置页那一行三处齐了才算接上。 */
    @Test
    fun `追更整页有入口`() {
        val host = linesOf("com/venera/compose/feature/settings", "AppSettings.kt")
        assertTrue("SettingsSubScreen 枚举里没有追更那一档", settingsHost.contains("FOLLOW_UPDATES,"))
        assertTrue(
            "枚举有了但没分发到这页 —— 点进去会是一片空白",
            settingsHost.any { it.contains("SettingsSubScreen.FOLLOW_UPDATES -> AndroidFollowUpdatesScreen(") },
        )
        assertTrue("设置页没有那一行入口", host.any { it.contains("追更更新") && it.contains("onFollowUpdates") })
        assertTrue("AppSettings 形参没递进来", host.any { it.contains("onFollowUpdates: () -> Unit") })
    }

    /** ④ 封面查看器不许再"取不到就一片黑且什么都不说"。 */
    @Test
    fun `封面查看器有加载与失败两态`() {
        assertTrue("没有失败回调：取不到图仍然是一片纯黑", coverViewer.any { it.contains("onError =") })
        assertTrue("没有加载中指示：慢网下与'坏了'分不清", coverViewer.any { it.contains("CircularWavyProgressIndicator(") })
        // ⚠️ 2026-10-20 文案纪律（`CopyDisciplineTest`）之后，"出路按钮"不再统一叫「重试」——
        // 各场景按自己的话说（再来一次 / 再试一次 / 重新加载…）。所以这把尺钉**有没有出路**，
        // 而不是钉那个具体的词：VeneraTextButton 出现在失败分支里就算有出路。
        assertTrue(
            "失败态没有出路（只说坏了不给按钮）",
            coverViewer.any { it.contains("VeneraTextButton(") },
        )
        assertTrue(
            "重试没绕开内存里那份失败结果 —— 点一下只是把同一个错误再放一遍",
            coverViewer.any { it.contains("memoryCacheKey(") && it.contains("retryTick") },
        )
    }
}
