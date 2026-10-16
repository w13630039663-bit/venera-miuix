package com.venera.compose.components.venera

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.SinkFeedback

/**
 * 「图片面按压反馈」这一轮（2026-10-16 用户要求「给图廊所有图片都加上按压反馈」）的尺子。
 *
 * 为什么必须有它：这一轮的缺陷形状是**静默的**。miuix `Card` 的 `pressFeedbackType` 默认就是
 * `None`，所以"漏传一个口子"的后果不是编译错、也不是崩，而是"这一块按下去没回话" ——
 * 而没回话正是用户已经报过一次的那件事。编译器和运行期都管不到"新加了一处可点的图片面却忘了接"。
 *
 * 三条各自钉一件事：
 *  - [两条路是同一个 miuix 原语]：唯一出处那两个成员必须是同一件东西的两种递法，
 *    否则"全画廊同一档手感"只是句口号（枚举那条改了参数、Indication 那条没改，这种漂法看不出来）；
 *  - [面板露头的那几处都接了唯一出处]：每一个图片面真的挂在唯一出处上；
 *  - [VeneraCard 默认不挂反馈]：漫画侧三十几处调用点零回归 —— 默认值一翻，整个 app 的卡都开始缩。
 */
class VeneraPressFeedbackTest {

    private fun file(rel: String) = RepoSources.root.resolve(rel)

    private fun src(rel: String): String {
        val f = file(rel)
        assertTrue("找不到源文件：${f.absolutePath}", f.isFile)
        return f.readText()
    }

    /** 画廊 `ui` 下所有 `.kt` 的 `相对路径 → 正文`。 */
    private fun galleryUiSources(): Map<String, String> =
        RepoSources.kotlinFiles("com/venera/compose/gallery/ui").associate { file ->
            file.name to file.readText()
        }

    /** ① 唯一出处那两个成员是**同一个 miuix 原语**的两种递法。 */
    @Test
    fun `两条路是同一个 miuix 原语`() {
        // 运行时那一半：两条路都真的是 Sink 那一档（枚举 + data class 取默认参数）。
        assertEquals(
            "card 那一档不是 Sink —— 图墙要的是「沉一下」，不是 Tilt（瀑布流里逐格歪）",
            PressFeedbackType.Sink,
            VeneraPressFeedback.card,
        )
        assertEquals(
            "indication 那一档不是 SinkFeedback 的默认参数 —— 它与 miuix Card 内部那颗就不是同一个了，" +
                "于是「全画廊同一档手感」只覆盖 miuix Card 那半边",
            SinkFeedback(),
            VeneraPressFeedback.indication,
        )
        // 源码那一半：钉住"miuix 内部就是这么接的"这一条断言本身，防止哪天升级 miuix
        // 把 `Sink -> SinkFeedback()` 换成带参数的构造，而我们的 KDoc 还照旧写着"逐字一致"。
        val miuixCard = src(".reference/miuix-ui-094/commonMain/top/yukonga/miuix/kmp/basic/Card.kt")
        assertTrue(
            "miuix Card 不再是 `PressFeedbackType.Sink -> SinkFeedback()` 了（升级后请重新核这条等价）",
            miuixCard.contains("PressFeedbackType.Sink -> SinkFeedback()"),
        )
        val sink = src(".reference/miuix-ui-094/commonMain/top/yukonga/miuix/kmp/utils/PressFeedback.kt")
        assertTrue(
            "SinkFeedback 的默认下沉量变了（类头注里那个 0.94 的口径要一起改）",
            sink.contains("val sinkAmount: Float = 0.94f"),
        )
    }

    /** ② 画廊每一个可点的图片面都挂在唯一出处上。 */
    @Test
    fun `画廊每一个可点的图片面都接了唯一出处`() {
        val ui = galleryUiSources()
        fun has(name: String, needle: String) {
            val body = ui[name] ?: error("画廊 ui 下找不到 $name")
            assertTrue(
                "$name 里没找到「$needle」—— 这一处图片面又变回『按下去没回话』了",
                body.contains(needle),
            )
        }
        // 图墙上那张卡（主墙 / 搜索 / 收藏 / 猜你喜欢 / 画师页 / 日榜共用同一颗）。
        has("GalleryScreen.kt", "pressFeedbackType = VeneraPressFeedback.card")
        // 浏览历史那一格（走 Indication 那条路：它是 Box + combinedClickable，不是 miuix Card）。
        has("GalleryHistoryScreen.kt", "indication = VeneraPressFeedback.indication")
        // 首页预览行那一张（与主墙上是同 uid 的孪生卡，两处力度必须一样）。
        has("GalleryHomeSections.kt", "pressFeedback = VeneraPressFeedback.card")
        // 画师那一张卡（头像是"这个人的图"）。
        has("GalleryArtistRows.kt", "pressFeedback = VeneraPressFeedback.card")
        // 以图搜图结果那一条（整卡可点 = 开大图页）。
        has("GalleryReverseResults.kt", "VeneraPressFeedback.card")
    }

    /**
     * ③ 以图搜图那一条：反馈必须**跟着可点性**同进同退。
     *
     * miuix 的 `pressable` 只看 `pressFeedbackType` 是不是 None，**不看有没有 `onClick`**
     * （`basic/Card.kt:113` 现读），所以 `ref == null` 那一档若还挂着 Sink 就是
     * "按得动、什么都不发生"的假开关。这一屏的 aesthetic 条目十有八九没有我方条目 —— 那是常态。
     */
    @Test
    fun `不可点的图片面不许挂着反馈`() {
        val body = galleryUiSources()["GalleryReverseResults.kt"]
            ?: error("画廊 ui 下找不到 GalleryReverseResults.kt")
        assertTrue(
            "以图搜图那一条没有把反馈绑在可点性上：`ref == null` 时会变成假开关",
            body.contains(
                "pressFeedbackType = if (ref != null) VeneraPressFeedback.card else PressFeedbackType.None",
            ),
        )
    }

    /** ④ 带命名风格：画廊里不许再出现绕过唯一出处的裸档（`pressFeedbackType = PressFeedbackType.Xxx`）。 */
    @Test
    fun `画廊里没有绕过唯一出处的裸档`() {
        val offenders = galleryUiSources().filter { (name, body) ->
            // 以图搜图那一条是唯一允许出现裸枚举的地方，且只许是 None 那一支（上面 ③ 单独钉）。
            Regex("""pressFeedbackType\s*=\s*PressFeedbackType\.[A-Za-z]+""").containsMatchIn(body) &&
                !name.equals("GalleryReverseResults.kt")
        }.keys
        assertTrue(
            "这些文件直接写死了 miuix 的档位而不是取 VeneraPressFeedback：$offenders",
            offenders.isEmpty(),
        )
    }

    /** ⑤ VeneraCard 的默认档必须是 None —— 漫画侧三十几处调用点靠这条零回归。 */
    @Test
    fun `VeneraCard 默认不挂反馈`() {
        val body = src("app/src/main/java/com/venera/compose/components/venera/VeneraCard.kt")
        assertTrue(
            "VeneraCard 的 pressFeedback 默认值被改动 —— 漫画侧（冻结区）三十几处调用点会跟着开始缩，那是无需求的重构",
            body.contains("pressFeedback: PressFeedbackType = PressFeedbackType.None"),
        )
    }
}
