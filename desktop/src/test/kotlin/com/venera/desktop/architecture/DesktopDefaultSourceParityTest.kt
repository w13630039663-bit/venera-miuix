package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A 条裁决的机械落地（2026-10-21 主人拍板：**探索默认源换 `copy_manga`，默认遮罩档保持 `OFF`**）。
 *
 * ## 为什么这一格需要尺，而不是一句"改好了"
 *
 * 换默认源这件事**改在源码里看不出后果**：探索屏照样出 30 张卡、照样能点进详情，
 * 而"成人封面裸奔"这一格的风险**只在预设表那一份 json 里**。也就是说 ——
 * 下一次谁把这颗 const 改回 `"jm"`（或者在别处硬编码一颗 nsfw 源当默认），
 * 全仓 1130 条判据**一条都不会红**。那正是主人在选项里点名要"尺子钉住"的东西。
 *
 * ## 四条各钉一件事
 *
 *  ① **默认源是 `copy_manga` 而非 `jm`**，且它在预设表里的等级确实是 `safe`
 *     （不是本喵口头说的 safe）；
 *  ② **桌面默认遮罩档与安卓默认遮罩档逐字相等**（主人选了"保持 OFF 以免分叉"，
 *     那这条就是"别分叉"的可执行形式）；
 *  ③ **UI 壳里不许出现硬编码源名字面量** —— 默认源必须只有 `DESKTOP_EXPLORE_SOURCE` 这一颗，
 *     否则"改成读偏好"那一天会漂（那颗 const 的注释自己就这么写着）；
 *  ④ **预设表真进了桌面资源清单**（A 条理由②的子因 (1) 已经关掉，这条是它别又漂回去）。
 *
 * ## ⚠️ 这一把尺管不到的（别读成"安全侧都做完了"）
 *
 *  - 取证那一枚 `startKey = "jm"`（`VeneraDesktop.kt:199`）**不在本条裁决范围内** ——
 *    它只服务无人值守那一路（`runAutofavEvidence` / `D_启动 起始=`），与用户看的探索屏无关。
 *    ④ 那条把它作为**唯一允许的字面量例外**明写下来，而不是装作它不存在。
 *  - 历史/收藏里**已存在**的 `jm` 条目不受影响（它们按各自 `sourceKey` 走）。
 *  - 条目级判定仍比安卓窄（缺安卓那份 R18 词表正则，`DesktopUxPlatformServices.kt:103-106`
 *    那颗的注释自己写着"宁可漏判，也不假装有那份词表"）—— 那是子因 (2)，仍开着。
 */
class DesktopDefaultSourceParityTest {

    private val route = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("DesktopRoute.kt"))
    private val shell = DesktopSourceTree.codeLinesOf(
        DesktopSourceTree.desktopSourceOf("VeneraDesktopLargeScreen.kt"),
    ).filter { it.isNotBlank() }
    private val assembly = DesktopSourceTree.codeLinesOf(
        DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"),
    ).filter { it.isNotBlank() }

    /** 安卓预设表（桌面资源清单里那份是它的副本，④ 那一条量的是"有没有被带上"）。 */
    private val presetTable: String by lazy {
        val f = File(DesktopSourceTree.repoRoot, "app/src/main/assets/source_content_warning.json")
        check(f.isFile) { "判据失效（不是通过）：读不到预设表 ${f.path}" }
        f.readText()
    }

    /** ① 默认源 = `copy_manga`，而它在预设表里的等级真的是 `safe`。 */
    @Test
    fun 默认探索源换成safe站而等级按表念不按口头() {
        val line = shell.plus(assembly).plus(route.split("\n"))
            .firstOrNull { "internal const val DESKTOP_EXPLORE_SOURCE" in it }
        assertNotNull("读不到 DESKTOP_EXPLORE_SOURCE 的定义（判据失效，不是通过）", line)
        assertTrue(
            "A 条裁决把默认源换成 copy_manga（jm 在安卓预设表里 level=nsfw）：$line",
            line!!.contains("copy_manga"),
        )
        // 等级不是本喵口头说的：直接从那份表里读那一条。
        val entry = Regex("\"copy_manga\"\\s*:\\s*\\{([^}]*)\\}").find(presetTable)
        assertNotNull("预设表里读不到 copy_manga 这一条（表形状变了 ⇒ 桌面 isGuarded 会当成不遮）", entry)
        val level = Regex("\"level\"\\s*:\\s*\"(safe|mixed|nsfw)\"").find(entry!!.groupValues[1])
            ?.groupValues?.get(1)
        assertEquals("默认源必须在预设表里是 safe，否则这次换源没换来任何东西：", "safe", level)
        // 反向：被换掉的那一颗**确实**是 nsfw（不然这条判据钉的是个假想敌）。
        val jmEntry = Regex("\"jm\"\\s*:\\s*\\{([^}]*)\\}").find(presetTable)
        assertNotNull("预设表里读不到 jm 这一条：", jmEntry)
        val jmLevel = Regex("\"level\"\\s*:\\s*\"(safe|mixed|nsfw)\"").find(jmEntry!!.groupValues[1])
            ?.groupValues?.get(1)
        assertEquals(
            "jm 在表里应当是 nsfw（这正是本喵当初把它设成默认源捅出来的风险）：",
            "nsfw", jmLevel,
        )
    }

    /**
     * ② 桌面默认遮罩档 == 安卓默认遮罩档（**这条就是"别分叉"的可执行形式**）。
     *
     * 两边都是从各自源码里读那个默认值的字面量，不是读常量名：
     * 谁单独改了默认档（哪怕名字没动），这里就红，而红话里点名是哪一方动的。
     */
    @Test
    fun 桌面默认遮罩档与安卓逐字相等而分叉会点名谁动的() {
        val desktop = DesktopSourceTree.codeLinesOf(
            DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryContentGuard.kt"),
        ).firstOrNull { "const val DEFAULT_MASK_MODE" in it }
        assertNotNull("读不到桌面 DEFAULT_MASK_MODE（判据失效，不是通过）", desktop)
        val desktopValue = Regex("\"(OFF|BLUR|HIDE)").find(desktop!!)?.groupValues?.get(1)
        assertNotNull("桌面那颗默认档读不出三档之一的字面量：$desktop", desktopValue)

        // 安卓那侧：`prefs.getString("nsfw_mode", "OFF") ?: "OFF"` —— 取这一行里的字面量。
        val android = DesktopSourceTree.codeLinesOf(
            DesktopSourceTree.androidSource("security/guard/ContentGuardManager.kt"),
        ).firstOrNull { "getString(\"nsfw_mode\"" in it }
        assertNotNull("读不到安卓侧 nsfw_mode 的默认值（判据失效，不是通过）", android)
        val androidValue = Regex("\"(OFF|BLUR|HIDE)").find(android!!)?.groupValues?.get(1)
        assertNotNull("安卓那行读不出三档之一的字面量：$android", androidValue)

        assertEquals(
            "两端默认遮罩档分叉了（主人 2026-10-21 的裁决是保持与安卓一致）：" +
                "桌面=$desktopValue 安卓=$androidValue ⇒ 改的那一方要么回退，要么这条裁决由主人重签",
            androidValue, desktopValue,
        )
    }

    /**
     * ③ 默认源只有一颗：UI 壳与装配点里**不许**再出现 `"jm"` 这种源名字面量。
     *
     * 唯一例外 = 取证那一枚 `startKey`（不在本次裁决范围内，但必须数得清它有几处）：
     * 所以这条钉的是"恰好 1 处，且那一行带 startKey"，不是"0 处"——
     * 把例外写成 0 是本喵假装的干净，写成一处并点名才是量出来的形状。
     */
    @Test
    fun 默认源收成一颗而唯一的例外是取证那枚且点名是谁() {
        val jmLines = (shell + assembly).filter { it.contains("\"jm\"") }
        // 壳（UI）那一侧必须一颗硬编码都没有。
        assertTrue(
            "UI 壳里不许硬编码源名字面量，默认源只有 DESKTOP_EXPLORE_SOURCE 这一颗：" +
                shell.filter { it.contains("\"jm\"") },
            shell.none { it.contains("\"jm\"") },
        )
        assertEquals(
            "全桌面 main 里 \"jm\" 字面量应当恰好一处（取证 startKey），多出来的就是又漂了一颗：",
            1, jmLines.size,
        )
        assertTrue(
            "那一处必须是取证那枚 startKey（`--shot=` 那一路），不是偷偷给 UI 用了：" + jmLines,
            jmLines.single().contains("startKey"),
        )
        // 这颗 const 的定义也只许一处（两处就是两个真源）。
        assertEquals(
            "DESKTOP_EXPLORE_SOURCE 的定义只许一处：",
            1, route.split("\n").count { "internal const val DESKTOP_EXPLORE_SOURCE" in it },
        )
    }

    /** ④ 预设表真进了桌面资源清单（A 条理由②的子因 (1) 别又漂回去）。 */
    @Test
    fun 预设表进了桌面资源清单而不只住在安卓包里() {
        val gradle = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts")
        check(gradle.isFile) { "判据失效（不是通过）：读不到 ${gradle.path}" }
        // ⚠️ 不许用 readText() 整篇找字面量：扰动实验抓到过一次**空转** ——
        // 本喵把资源清单里那一行改名后这一条仍然绿，因为 374 行那句**注释里也提了这个文件名**。
        // 注释不是证据（本仓的一贯口径），所以这里只数"非注释行里的那一枚"。
        val entries = gradle.readText().lines().filter {
            it.contains("source_content_warning.json") && !it.trimStart().startsWith("//")
        }
        assertTrue(
            "桌面资源清单没带上 source_content_warning.json ⇒ 桌面预设表读空、" +
                "isGuarded 恒 false，成人站在 OFF 档下就真裸奔了（找到的非注释行=$entries）",
            entries.isNotEmpty(),
        )
        // 带上还不够：桌面那颗读它的类不许把它当"读不到就当没事"。
        val presets = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("DesktopSourceContentPresets.kt"),
        )
        assertTrue(
            "预设表解析成 0 条时得能出声（这条是子因 (1) 的配套：静默的空表比没有表更危险）",
            Regex("0 条").containsMatchIn(presets),
        )
    }
}
