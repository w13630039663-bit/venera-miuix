package com.venera.desktop.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 83 · 源管理那一屏的**纯数据层**（不启引擎、不开库）。
 *
 * ## 为什么这一颗要存在
 *
 * 桌面今天只认**包内**那 34 颗源脚本（`D_资源 来源=包内 /sources/ 清单=34 颗`），而"选哪一颗源"
 * 是命令行上的一枚 `-Pkey=`。要把它变成屏上的一行一行，缺的第一格不是界面而是**名字**：
 * 引擎装载那趟确实回一个显示名（`EngineSession.load` 的返回值），可"列表"这一屏若为了报名字
 * 就去起一颗 GraalJS + 整源装载，就是批 82.7 刚立的那条 `desktopComicEngineNeeded` 反着来。
 * 所以名字从脚本头部**现取**，而取不到时不许编一个（编出来的站名会被当成站方给的）。
 *
 * ## 口径的回核对象
 *
 * 对岸那颗 `JsComicSource` 的 `name` 就是源脚本里那一行赋值 —— 这里逐字回核 `jm.js`，
 * 不自造第二套解析（同一份脚本两处读成两种形状，就是"桌面看到的与手机不同"那一族缺陷的成因）。
 */
class DesktopSourceCatalogTest {

    @Test
    fun 名字从脚本头部现取而口径回核对岸那一行() {
        // 与 app/src/main/assets/sources/jm.js:5 逐字同串（那行是 `    name = "禁漫天堂"`）
        val jm = DesktopSourceCatalogTest::class.java.classLoader
            .getResource("sources/jm.js")?.readText()
        val script = jm ?: "    name = \"禁漫天堂\"\n"
        val hit = DesktopSourceNames.displayNameOf(script)
        val expected = if (jm == null) "禁漫天堂" else Regex("""^\s*name\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
            .find(jm)!!.groupValues[1]
        assertEquals("名字必须等于脚本里那一行赋值（对岸读的就是它）：$hit", expected, hit)
    }

    @Test
    fun 头部没有那一行时不猜名字() {
        assertNull(
            "取不到就交回 null 让屏上念 key —— 编一个站名等于替站方作伪证",
            DesktopSourceNames.displayNameOf("let x = 1\nfunction fetch() {}\n"),
        )
        assertNull("空脚本也不许得到一个假名", DesktopSourceNames.displayNameOf(""))
    }

    @Test
    fun 只认顶层那一行赋值而分区名不算() {
        // 真形状：`name = "禁漫天堂"` 在前，`name: "每週必看"` 在后（jm.js:433 那种是**分区**名）。
        // 冒号形式不是源名 ⇒ 只有冒号形式时算没取到。
        val onlySections = "    name: \"每週必看\",\n    name: \"本月排行\",\n"
        assertNull("分区名不许当源名", DesktopSourceNames.displayNameOf(onlySections))
    }

    @Test
    fun 停用只吃掉那一格而不把行抹掉() {
        val rows = DesktopSourceCatalog.rows(
            keys = listOf("jm", "goda"),
            names = mapOf("jm" to "禁漫天堂"),
            disabled = setOf("goda"),
        )
        assertEquals("两颗源就是两行（停用不是删除）", 2, rows.size)
        assertEquals("jm", rows[0].key)
        assertEquals("禁漫天堂", rows[0].title)
        assertTrue("没停用的那颗必须是可以用的", rows[0].enabled)
        assertFalse("停用那一颗要标着停用", rows[1].enabled)
        // 取不到名字的那一行念 key，而不是念一个空标题
        assertEquals("goda", rows[1].title)
    }

    @Test
    fun 停用当前在用的那颗时读数念得出而屏上不静默() {
        val reading = DesktopSourceCatalog.reading(
            sourceKey = "jm",
            rows = DesktopSourceCatalog.rows(listOf("jm"), emptyMap(), disabled = setOf("jm")),
        )
        assertTrue("那一行要念得出在用哪一颗：$reading", reading.startsWith("D_源管理"))
        assertTrue("停用了正在用的那颗必须念出来（否则屏上摆着这一颗而它是关的）：$reading", reading.contains("停用=在用"))
        assertTrue(reading.contains("jm"))
    }

    @Test
    fun 没停用时那句念全开而不是少报一档() {
        val reading = DesktopSourceCatalog.reading(
            sourceKey = "jm",
            rows = DesktopSourceCatalog.rows(listOf("jm", "goda"), mapOf("jm" to "禁漫天堂"), disabled = emptySet()),
        )
        assertTrue("颗数要念得出：$reading", reading.contains("清单=2"))
        assertFalse("没停用就不许念「停用=在用」", reading.contains("停用=在用"))
    }

    // ───────────────────── 选源：停用要真拦住，不是只标个灰 ─────────────────────

    @Test
    fun 停用那颗不许被选为当前源而落回要念得出落点() {
        val outcome = DesktopSourceSelection.resolve(keys = listOf("jm", "goda"), disabled = setOf("jm"), requested = "jm")
        assertEquals("停用了还照它装载 = 那枚开关是假的", DesktopSourcePick.FellBack, outcome.kind)
        assertEquals("goda", outcome.key)
        assertTrue("回落那一句要念得出从谁落到谁：${outcome.reading}", outcome.reading.contains("jm") && outcome.reading.contains("goda"))
    }

    @Test
    fun 启用着的那颗照它走而不许多落一句回落() {
        val outcome = DesktopSourceSelection.resolve(listOf("jm", "goda"), disabled = setOf("goda"), requested = "jm")
        assertEquals(DesktopSourcePick.Exact, outcome.kind)
        assertEquals("jm", outcome.key)
        assertFalse("没回落就不许念回落（读数多一句假话与少一句实话同罪）", outcome.reading.contains("回落"))
    }

    @Test
    fun 全被停用时不许硬挑一颗开着的要交回空手() {
        val outcome = DesktopSourceSelection.resolve(listOf("jm", "goda"), disabled = setOf("jm", "goda"), requested = "jm")
        assertEquals("一颗开着的都没有 ⇒ 不能假装某颗在用", DesktopSourcePick.NoneEnabled, outcome.kind)
        assertEquals("", outcome.key)
        assertTrue("那一句要念得出是「全都关了」而不是「没找到」：${outcome.reading}", outcome.reading.contains("全停用"))
    }

    @Test
    fun 请求的键压根不存在时念没这颗而不算回落() {
        // 与"停用"分开一档：拼错的键与用户真停用了那颗，修法完全不同。
        val outcome = DesktopSourceSelection.resolve(listOf("jm", "goda"), disabled = emptySet(), requested = "nope")
        assertEquals(DesktopSourcePick.UnknownKey, outcome.kind)
        assertEquals("jm", outcome.key)
        assertTrue("那一句要念得出是「没这颗」：${outcome.reading}", outcome.reading.contains("没这颗"))
    }

    @Test
    fun 停用与启用是同一颗开关的两档而不许叠成两份() {
        val off = DesktopSourceSelection.toggle(disabled = setOf("goda"), key = "jm")
        assertEquals(setOf("goda", "jm"), off)
        val on = DesktopSourceSelection.toggle(disabled = off, key = "jm")
        assertEquals("再点一次必须回到原样（叠成两份就永远关不掉）", setOf("goda"), on)
    }

    @Test
    fun 只剩一颗启用着的那一颗不许再关() {
        // 全关之后 resolve 只能交回空手 ⇒ 漫画域一屏都装不起来，那是一枚把自己锁在门外的开关。
        assertFalse(
            "最后一颗启用着的源必须关不掉",
            DesktopSourceSelection.canDisable(listOf("jm", "goda"), disabled = setOf("goda"), key = "jm"),
        )
        assertTrue("还剩两颗以上时可关", DesktopSourceSelection.canDisable(listOf("jm", "goda"), disabled = emptySet(), key = "jm"))
        assertTrue("已经关着的那颗再点一次是开，不算「多关一颗」", DesktopSourceSelection.canDisable(listOf("jm"), disabled = setOf("jm"), key = "jm"))
    }
}
