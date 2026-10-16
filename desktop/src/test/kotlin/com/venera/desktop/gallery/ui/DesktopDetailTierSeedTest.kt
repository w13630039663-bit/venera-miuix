package com.venera.desktop.gallery.ui

import com.venera.desktop.ForensicFlags
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 92：给「档位」补一枚取证种子 `--detail-tier=`，并把顺带量出来的**三条轨**收成一颗。
 *
 * ## 为什么初值那一格不是落点（这一颗测试存在的主要理由）
 *
 * 第一眼的设计是「种子 = `tier` 那颗状态的初值」，而它在屏上**永远不会生效**：点卡那一路
 * （`LocalDesktopWallPick`，应用根里那一枚共用的 lambda）紧跟着就 `tier = desktopViewerEntryTier(...)`
 * 把档位升到「大图」—— 而取证那颗 `--detail` 走的**就是同一枚 lambda**。
 * 于是读数念了「认成 档=原图」而屏上是大图档，正是本仓量过四次的那一族「stdout 念了而屏上没动」。
 * ⇒ 落点必须在**点卡那一跳**，且要经 `desktopDetailTiers` 那张表（摆不出这一档的卡不许硬取）。
 *
 * ## 落点那一跳带出来的两条轨（同一批里改掉，不留半成品）
 *
 * 面板自己会钳（`shownTier`：手里那枚摆不出就落回这一张的第一档），而**大图那一屏与预取吃的是未钳的
 * `tier`** —— 停在有原图的那一张选「原图」，再按一次「下一张」翻到一条视频，那一层要的就是
 * `file_url`（原片 mp4），面板底下显示的却是静帧。面板/大图/预取三条轨读同一颗状态却不同轨。
 * ⇒ 钳档算式收成一颗 `desktopDetailTierForCard`，三条轨各吃它的结果（判据 ⑥⑦）。
 *
 * ## 只在这一颗测试里钉得住的
 *
 * 1. **屏名与枚举名都认，认不出不猜**：种子递的是 `原图` 还是 `ORIGINAL` 都得落到同一档，
 *    而递错字时不许悄悄落回默认档（那是把「我参数打错了」读成「这一档今天兑现不了」）。
 * 2. **三串档名单源**：读数里念的那句屏名与那排分段控件上的字必须是同一颗函数给的，
 *    否则改名之后读数念「原图」而屏上摆「原始档」，取证那一行就成了旧文案。
 * 3. **读数吃的是落点而不是请求**：`要=` 与 `落在=` 两格在"这一张摆不出那一档"时必须不同。
 */
class DesktopDetailTierSeedTest {

    private val flags = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("ForensicFlags.kt"))
    private val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
    private val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
    private val policy = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPolicy.kt"))
    private val viewerPolicy = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryViewerPolicy.kt"))
    private val viewer = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryViewer.kt"))
    private val host = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
    private val gradle = DesktopSourceTree.codeText(File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts"))

    /** 收 stdout：这一族的读数只活在 stdout，屏上什么都没有（手册 §0 那一格）。 */
    private fun speak(block: () -> Unit): String {
        val captured = ByteArrayOutputStream()
        val previous = System.out
        try {
            System.setOut(PrintStream(captured, true, "UTF-8"))
            block()
        } finally {
            System.setOut(previous)
        }
        return captured.toString("UTF-8")
    }

    /** 全桌面 main 源里那颗名字的代码行出现次数（声明 + 调用点）。 */
    private fun occurrencesAcrossMain(token: String): Int =
        DesktopSourceTree.desktopMainSources().sumOf { DesktopSourceTree.codeOccurrences(it, token) }

    // ① 判读四档真跑：没给 / 认枚举名 / 认屏名 / 认不出与没带值各念自己的那句。
    @Test
    fun 判读四档各走各的() {
        assertNull("没给这枚参数时不许改档", ForensicFlags.detailTier(emptyList()))
        assertEquals("枚举名大写", DesktopDetailTier.ORIGINAL, ForensicFlags.detailTier(listOf("--detail-tier=ORIGINAL")))
        assertEquals("枚举名小写也认", DesktopDetailTier.PREVIEW, ForensicFlags.detailTier(listOf("--detail-tier=preview")))
        assertEquals("屏名逐字也认", DesktopDetailTier.SAMPLE, ForensicFlags.detailTier(listOf("--detail-tier=大图")))

        val silent = speak { ForensicFlags.detailTier(emptyList()) }
        assertEquals("没给参数却念了读数（把「没打参数」说成「打过了」）：" + silent, 0, silent.lines().count { it.contains("D_") })
        val text = speak {
            ForensicFlags.detailTier(listOf("--detail-tier"))
            ForensicFlags.detailTier(listOf("--detail-tier="))
            ForensicFlags.detailTier(listOf("--detail-tier=4K"))
        }
        assertEquals("三档各念一句，多念少念都不对：" + text, 3, text.lines().count { it.contains("D_取证参数 --detail-tier") })
        assertTrue("不带值那一档没点名：" + text, text.lines().count { it.contains("没带上那一串") } == 2)
        assertTrue("认不出那串没被原样点名：" + text, text.contains("4K"))
        assertTrue("认不出时念了『不猜』那句：" + text, text.contains("不替你猜一档"))
        assertFalse("认不出却被判成了某一档：" + text, text.contains("认成"))
    }

    // ② 登记：漏了 KNOWN 那一格，带它起窗时 unknown 会多念一句"不认识的参数"（读数自相矛盾）。
    @Test
    fun 参数登记过而拼错照报() {
        assertTrue("`--detail-tier` 没登记进 KNOWN", ForensicFlags.KNOWN.contains("--detail-tier"))
        assertEquals("登记过却被报成拼错", emptyList<String>(), ForensicFlags.unknown(listOf("--detail-tier=原图")))
        // 对照组：那张表仍然真在管不认识的那一族（清空 KNOWN 会让上面两条恒真）。
        assertEquals("对照组失效：拼错的参数没被报出来", listOf("--detail-teir=原图"), ForensicFlags.unknown(listOf("--detail-teir=原图")))
    }

    // ③ 三串档名单源：面板那排分段控件与读数吃同一颗函数。
    @Test
    fun 屏名只有一颗生产者() {
        assertEquals("三串屏名各一处，多写一份就会漂：" + policy, 1, DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPolicy.kt"), "-> \"预览\""))
        listOf("预览", "大图", "原图").forEach { label ->
            assertTrue("策略件里少了「$label」", policy.contains("\"$label\""))
            assertEquals("面板里还自己写着「$label」（改名会有一处指错地方）：$label", 0, DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"), "\"$label\""))
        }
        assertTrue("面板没把那颗委托出去：" + panel, panel.contains("fun DesktopDetailTier.panelLabel(): String = desktopDetailTierLabel(this)"))
        // 对照组：委托那一颗真有三个分支（读空了上面几条恒真）。
        assertEquals(3, Regex("DesktopDetailTier\\.\\w+ -> \"").findAll(policy).count())
    }

    // ④ 落点走「摆得出来的那一档」：摆不出时照旧升档，而不是硬取一枚站方没给的地址。
    @Test
    fun 落点经那张表钳住() {
        val three = listOf(DesktopDetailTier.PREVIEW, DesktopDetailTier.SAMPLE, DesktopDetailTier.ORIGINAL)
        val video = listOf(DesktopDetailTier.PREVIEW, DesktopDetailTier.SAMPLE)
        assertEquals("摆得出就吃种子", DesktopDetailTier.ORIGINAL, desktopViewerEntryTier(three, DesktopDetailTier.ORIGINAL))
        assertEquals("摆不出落回这一张该落的那档", DesktopDetailTier.SAMPLE, desktopViewerEntryTier(video, DesktopDetailTier.ORIGINAL))
        assertEquals("没给种子时旧行为一字不变", DesktopDetailTier.SAMPLE, desktopViewerEntryTier(three, null))
        assertNull("一档都不摆时不许造一档出来", desktopViewerEntryTier(emptyList(), DesktopDetailTier.ORIGINAL))
    }

    // ⑤ 落点读数：要= 与 落在= 在"摆不出"时必须是两句话，且两种 offered 各念一份。
    @Test
    fun 落点读数念得出三格() {
        val three = listOf(DesktopDetailTier.PREVIEW, DesktopDetailTier.SAMPLE, DesktopDetailTier.ORIGINAL)
        val video = listOf(DesktopDetailTier.PREVIEW, DesktopDetailTier.SAMPLE)
        val ok = desktopTierSeedReading(DesktopDetailTier.ORIGINAL, three, desktopViewerEntryTier(three, DesktopDetailTier.ORIGINAL))
        val clamped = desktopTierSeedReading(DesktopDetailTier.ORIGINAL, video, desktopViewerEntryTier(video, DesktopDetailTier.ORIGINAL))
        val none = desktopTierSeedReading(DesktopDetailTier.ORIGINAL, emptyList(), desktopViewerEntryTier(emptyList(), DesktopDetailTier.ORIGINAL))
        assertTrue("少了 要= 那一格：" + ok, ok.startsWith("D_档位种子 要=原图"))
        assertTrue("摆得出那一格没念成能：" + ok, ok.contains("这一张摆得出=能") && ok.contains("落在=原图"))
        assertTrue("摆不出那一格没念成不能：" + clamped, clamped.contains("这一张摆得出=不能") && clamped.contains("落在=大图"))
        assertTrue("钳过之后 落在= 还说要的那档：" + clamped, ok != clamped)
        assertTrue("一档都不摆时念不出实话：" + none, none.contains("落在=一档都不摆") && none.contains("这一张摆得出=不能"))
        assertEquals("读数被算了不止一处（落点只许有一个说法）：" + occurrencesAcrossMain("desktopTierSeedReading("), 2, occurrencesAcrossMain("desktopTierSeedReading("))
        assertTrue("应用根没念这一行：" + app, app.contains("println(desktopTierSeedReading("))
    }

    // ⑥ 三条轨（面板 / 大图那一屏 / 预取）读同一颗钳档算式。
    @Test
    fun 钳档算式只有一颗而三条轨各吃它() {
        assertEquals("声明 + 面板 + 应用根 = 3：" + occurrencesAcrossMain("desktopDetailTierForCard("), 3, occurrencesAcrossMain("desktopDetailTierForCard("))
        assertTrue("面板还在自己钳一遍（那就是第二颗判据）：" + panel, panel.contains("val shownTier = desktopDetailTierForCard(tier, tiers)"))
        assertFalse("面板里留着旧那份内联钳档：" + panel, panel.contains("if (tiers.contains(tier)) tier else tiers.firstOrNull()"))
        assertTrue("应用根没算那一枚：" + app, app.contains("val effectiveTier = selected?.card?.let { desktopDetailTierForCard(tier, desktopDetailTiers(it.post, it.masked)) } ?: tier"))
        // 三条轨各吃 effectiveTier，而不是各自回头吃原始那颗。
        // 三条轨各吃 effectiveTier —— 这一枚**从前写的是 `contains`，台架 P9 当场证明它不咬**：
        // 把大图那一层的实参改回 `tier = tier,` 时，面板那一处同形实参把锚点喂饱了
        // （本仓「接线类要数出现次数」那一族，这次抓在自己批的台架上 ⇒ 改成计数 + 负向各一条）。
        val appFile = DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt")
        assertEquals("应用根里 `tier = effectiveTier,` 应恰好两处（那一层 + 面板）：" + app, 2, DesktopSourceTree.codeOccurrences(appFile, "tier = effectiveTier,"))
        assertEquals("三条轨里还有一条吃未钳的那枚：" + app, 0, DesktopSourceTree.codeOccurrences(appFile, "tier = tier,"))
        // 预取那一跳的锚点由 `DesktopGalleryPreloadTest` ⑧ 钉（同一件事只许一把尺子钉措辞）。
        // 大图那一层只吃**自己那枚形参**（它不该再钳一遍，也不该回头读根上那颗原始状态）。
        assertTrue("那一层不是吃形参取的址：" + viewer, viewer.contains("desktopDetailTierUrl(it.post, tier)"))
        assertEquals("那一层自己又钳了一遍（第二条轨）：" + viewer, 0, DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopGalleryViewer.kt"), "desktopDetailTierForCard("))
    }

    // ⑦ 那颗算式真跑：五种输入各给一个说法，且不许凭空造档。
    @Test
    fun 钳档算式五种输入各走各的() {
        val three = listOf(DesktopDetailTier.PREVIEW, DesktopDetailTier.SAMPLE, DesktopDetailTier.ORIGINAL)
        val video = listOf(DesktopDetailTier.PREVIEW, DesktopDetailTier.SAMPLE)
        assertEquals("摆得出就原样交回", DesktopDetailTier.ORIGINAL, desktopDetailTierForCard(DesktopDetailTier.ORIGINAL, three))
        assertEquals("摆不出落回这一张的第一档", DesktopDetailTier.PREVIEW, desktopDetailTierForCard(DesktopDetailTier.ORIGINAL, video))
        assertEquals("手里没有档时给这一张的第一档", DesktopDetailTier.PREVIEW, desktopDetailTierForCard(null, video))
        assertEquals("一档都不摆时那格说的是『没什么可取』", DesktopDetailTier.PREVIEW, desktopDetailTierForCard(DesktopDetailTier.ORIGINAL, emptyList()))
        // 对照组：这一张本来就是第一档时不许被"落回第一档"那一步改写。
        assertEquals(" SAMPLE 在第一档那张被改走了", DesktopDetailTier.SAMPLE, desktopDetailTierForCard(DesktopDetailTier.SAMPLE, video))
    }

    // ⑧ 必填形参与三层落点：判读层、集成方、构建脚本那一跳、种子清单。
    @Test
    fun 五层接线都在() {
        assertTrue("应用根没收这一枚：" + app, app.contains("startDetailTier: DesktopDetailTier?"))
        assertFalse("形参带了默认值（漏传不报错的形状不许有）：" + app, Regex("startDetailTier: DesktopDetailTier\\?\\s*=").containsMatchIn(app))
        assertTrue("集成方没判读：" + host, host.contains("ForensicFlags.detailTier(args.asList())"))
        assertTrue("集成方没把它递给应用根：" + host, host.contains("startDetailTier = detailTierSeed,"))
        assertTrue("构建脚本那一跳漏了（`-PdetailTier=` 会静默丢掉）：" + gradle, gradle.contains("providers.gradleProperty(\"detailTier\").orNull"))
        assertTrue("那一跳没拼成进程参数：" + gradle, gradle.contains("--detail-tier=\$it"))
        assertTrue("判读层没登记那枚名字：" + flags, flags.contains("private const val DETAIL_TIER = \"--detail-tier\""))
        assertTrue("D_落点 的种子清单漏了这一枚：" + app, app.contains("\"detailTier\".takeIf { startDetailTier != null }"))
        // 读数住在判读层：那一行必须出现在 ForensicFlags 的代码行里（在消费点补一句 = 参数根本没被读过也念得出）。
        assertTrue(
            "判读层没念这一枚：" + DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopSourceOf("ForensicFlags.kt")).count { it.contains("DETAIL_TIER") },
            DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopSourceOf("ForensicFlags.kt"))
                .any { it.contains("DETAIL_TIER") && it.contains("D_取证参数") && it.contains("println") },
        )
    }

    // ⑨ 只给了档位种子而没开浮层：那一档哪儿也没去，必须出声（readPageNote 同一族）。
    @Test
    fun 没配套那枚参数时念得出() {
        assertNull("没给档位种子时不该吭声", ForensicFlags.detailTierNote(null, overlayOpens = false))
        assertNull("浮层本来就要开，没有可念的", ForensicFlags.detailTierNote(DesktopDetailTier.ORIGINAL, overlayOpens = true))
        val note = requireNotNull(ForensicFlags.detailTierNote(DesktopDetailTier.ORIGINAL, overlayOpens = false))
        assertTrue("没点名是那一枚参数：" + note, note.contains("没有配套的 --detail"))
        assertTrue("没念出要的是哪一档：" + note, note.contains("原图"))
        assertTrue("集成方没念这一句：" + host, host.contains("ForensicFlags.detailTierNote(detailTierSeed"))
    }

    // ⑩ 种子不落盘：它只改本轮的落点，不许写用户的「预览清晰度」偏好。
    @Test
    fun 种子只改本轮而不写偏好() {
        assertEquals("应用根里出现了偏好写口：" + app, 0, DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"), "setGalleryPreviewQuality("))
        // 对照组：读侧还在（读空了这一条就恒真）。
        assertTrue("读侧丢了：偏好不再给初值：" + app, app.contains("desktopDetailInitialTier(previewQuality)"))
        // 对照组二：应用根**不该**自己算地址 —— 取址只有浮层与大图那一层各一处（第四条轨的形状）。
        assertEquals("应用根自己算了一遍档位地址：" + app, 0, DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"), "desktopDetailTierUrl("))
    }
}
