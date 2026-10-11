package com.venera.desktop.gallery.data

import com.venera.compose.gallery.domain.GalleryTitleTag
import com.venera.desktop.gallery.ui.DesktopSourceTree
import com.venera.desktop.platform.FakePaths
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第七十八批：**词典读不出来时，不再被说成"这批名字里没有"**。
 *
 * ## 这一批补的是哪一格
 *
 * 批 40 那份形状是「三枚读口交可空，契约那一侧把 null 压成空集/空映象」，而压的那一步**一个字都不念**；
 * 更要紧的是它下面还有两枚合并分支：
 * - `DesktopTagDictionaryAsset.classpathNames()` 在**枚举失败**时交空清单，而空清单在读数里就是「包里没有」
 *   —— 「我们数不出来」与「包里真没带」压成一格（§1 那一族的第一次）；
 * - `query()` 把「库没打开」与「查询抛了」都交回 null，契约口再把 null 交回空表 —— 三层合并之后，
 *   屏上与日志里都只剩"这批标签没译名"。
 *
 * ## 契约不在改动面上
 *
 * `GalleryTagLexicon` 那三枚的返回类型写在 `app/…/GalleryBusinessApi.kt`（受保域），本批**一个字都不动**，
 * 判据 ⑨ 逐字钉着那三行。三态因此住在桌面自持的那一侧：读口交 `DesktopDictRead`，
 * 契约口在 `Miss` 那一支**必须出声**（⑦ 捕 stdout），屏上那句兜底按态分支（在 `DesktopArtistFallbackTest` ⑧⑨）。
 */
class DesktopDictionaryThreeStateTest {

    /** ① 真 classpath 上那条路是**读得到**的：清单交 Hit，而 presence 落 InPackage（对照组：行数不是零）。 */
    @Test
    fun 真包里的清单交命中而读数落在有那一态() {
        val listing = DesktopTagDictionaryAsset.classpathListing()
        assertTrue("真包里的清单没交命中：$listing", listing is DesktopDictRead.Hit)
        val names = (listing as DesktopDictRead.Hit).value
        assertTrue("清单是空的（include 清单没带上那颗？）：$names", names.isNotEmpty())
        val presence = DesktopTagDictionaryAsset.presence(listing)
        assertTrue("有货却读成别的态：$presence", presence is DesktopDictionaryPresence.InPackage)
        assertTrue("行数取不到（名字里那段没解析出来？）：$presence", (presence as DesktopDictionaryPresence.InPackage).rows > 0)
    }

    /**
     * ② `presence(listing)` 的三态各一条：有 / 包里没有 / 数不出。
     *
     * ⚠️ 三条**各喂一种输入**而不是拿同一份真清单：只量"有"那一格时，把 `Miss(Unreadable)` 改成
     * `NotInPackage` 的扰动（那就是本批要禁的合并）全树看不出来。
     */
    @Test
    fun 态的映射三格各给一种输入() {
        val hit = DesktopTagDictionaryAsset.presence(DesktopDictRead.Hit(listOf("gallery_tags_79415.sqlite")))
        assertTrue("有货那一格没落在 InPackage：$hit", hit is DesktopDictionaryPresence.InPackage && hit.rows == 79415)
        // 空清单 = 包里真没带；认不出行数的名字同样算这一格（等于没有可用的词典）
        assertTrue("空清单没落在包里没有：$hit", DesktopTagDictionaryAsset.presence(DesktopDictRead.Hit(emptyList())) == DesktopDictionaryPresence.NotInPackage)
        assertEquals(
            "认不出行数的资产名没落在包里没有",
            DesktopDictionaryPresence.NotInPackage,
            DesktopTagDictionaryAsset.presence(DesktopDictRead.Hit(listOf("gallery_tags_x.sqlite"))),
        )
        val failed = IllegalStateException("jar 读不动")
        val unreadable = DesktopTagDictionaryAsset.presence(DesktopDictRead.Miss(DesktopDictWhy.Unreadable(failed)))
        assertTrue("数不出那一格没落在 Unreadable：$unreadable", unreadable is DesktopDictionaryPresence.Unreadable)
        assertEquals("数不出那一格把成因丢了", failed, (unreadable as DesktopDictionaryPresence.Unreadable).error)
    }

    /** ③ 从清单推到"用哪一份资产"也是三态：命中给名字 / 空清单给"没有" / 数不出给"数不出+因"。 */
    @Test
    fun 资产选取的三态各给一种输入() {
        val hit = DesktopTagDictionaryAsset.assetOf(
            DesktopDictRead.Hit(listOf("gallery_tags_79415.sqlite", "gallery_tags_999.sqlite")),
        )
        assertEquals("该按名字里的行数取最新那一份", "gallery_tags_79415.sqlite", (hit as DesktopDictRead.Hit).value)
        assertEquals(
            "空清单那一格必须是『包里没有』而不是『数不出』",
            DesktopDictWhy.Absent,
            (DesktopTagDictionaryAsset.assetOf(DesktopDictRead.Hit(emptyList())) as DesktopDictRead.Miss).why,
        )
        val why = DesktopDictWhy.Unreadable(IllegalStateException("锚点不在"))
        assertEquals(
            "数不出那一格必须原样带着成因",
            why,
            (DesktopTagDictionaryAsset.assetOf(DesktopDictRead.Miss(why)) as DesktopDictRead.Miss).why,
        )
    }

    /**
     * ④ 负向：**枚举失败不许算成"清单是空的"**（§1 那格合并分支在本批的那一枚）。
     *
     * 扫的是那一颗文件的代码行：`?: emptyList()` 那一形就是合并本身 —— 它把"数不出来"喂给
     * 与"没带词典"同一个下游，而下游只能念成「包里没有」。
     */
    @Test
    fun 枚举失败不许退成空清单() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopTagDictionaryAsset.kt"))
        assertEquals("还有 `?: emptyList()` 那一形（失败与没有压成一格）：$code", 0, code.split("?: emptyList()").size - 1)
        assertEquals("还有 `?: emptyArray()` 那一形（同一格的另一形）：$code", 0, code.split("?: emptyArray()").size - 1)
        assertEquals("还有 `.getOrNull()` 把枚举的失败吞成 null：$code", 0, code.split(".getOrNull()").size - 1)
        // 对照组：扫的确实是那颗文件，且两条负向都有对象（读空会让上面两条恒真）
        assertTrue("对照组失效：扫到的不是词典资产那颗文件", code.contains("fun classpathListing()"))
    }

    /** ⑤ 三枚读口在真库上各交一次命中，且命中里"没有这一枚"与"读不出"仍然分得开。 */
    @Test
    fun 三枚读口真跑各交一次命中() {
        val dictionary = DesktopGalleryTagDictionary(FakePaths(Files.createTempDirectory("dict78-").toFile()))
        val found = dictionary.translations(listOf("blue_eyes", "这一串不会是标签"))
        assertTrue("真库上没交命中：$found", found is DesktopDictRead.Hit)
        assertEquals("蓝瞳", (found as DesktopDictRead.Hit).value["blue_eyes"])
        assertFalse("命中的键表里混进了不存在的名字", found.value.containsKey("这一串不会是标签"))
        assertTrue(dictionary.artistNames(listOf("blue_eyes")) is DesktopDictRead.Hit)
        assertTrue(dictionary.titleTags(listOf("blue_eyes")) is DesktopDictRead.Hit)
    }

    /** ⑥ 库读不动 ⇒ `Miss(Unreadable)` 且**带着成因**（不再是 null，也不再是"这批没有"）。 */
    @Test
    fun 读不动时交的是带成因的那一态() {
        val blocker = File.createTempFile("dict78-blocker-", "")
        val dictionary = DesktopGalleryTagDictionary(FakePaths(File(blocker, "db")))
        try {
            for (read in listOf(
                dictionary.translations(listOf("blue_eyes")),
                dictionary.artistNames(listOf("blue_eyes")),
                dictionary.titleTags(listOf("blue_eyes")),
            )) {
                assertTrue("读不动却交成了别的形状：$read", read is DesktopDictRead.Miss)
                val why = (read as DesktopDictRead.Miss).why
                assertTrue("读不动被说成了『包里没有』：$why", why is DesktopDictWhy.Unreadable)
                val message = ((why as DesktopDictWhy.Unreadable).error.message).orEmpty()
                assertTrue(
                    "那句成因没点名落点（只有类型名的读数答不出为什么）：$message",
                    message.contains(blocker.absolutePath.replace('\\', '/')) || message.contains(blocker.path),
                )
            }
        } finally {
            blocker.delete()
        }
    }

    /** ⑦ 契约口那三枚恒空的返回**必须出声**，而且念得出是哪一态。 */
    @Test
    fun 契约口降级时那一行必须出声() {
        val blocker = File.createTempFile("dict78-loud-", "")
        DesktopGalleryFailureLog.reset()
        val captured = ByteArrayOutputStream()
        val previous = System.out
        System.setOut(PrintStream(captured, true, "UTF-8"))
        try {
            val lexicon = DesktopGalleryTagLexicon(
                DesktopGalleryHandle(FakePaths(File(blocker, "db")), com.venera.desktop.gallery.ui.FakeHttpEngine()),
            )
            runBlocking {
                assertTrue(lexicon.translations(listOf("blue_eyes")).isEmpty())
                assertTrue(lexicon.artistNames(listOf("blue_eyes")).isEmpty())
                org.junit.Assert.assertNull(lexicon.titleTags(listOf("blue_eyes")))
            }
        } finally {
            System.setOut(previous)
            blocker.delete()
        }
        val spoken = captured.toString(StandardCharsets.UTF_8)
        for (member in listOf(
            DESKTOP_DICT_MEMBER_TRANSLATIONS,
            DESKTOP_DICT_MEMBER_ARTISTS,
            DESKTOP_DICT_MEMBER_TITLES,
        )) {
            assertTrue("恒空返回没出声（枚=$member）：$spoken", spoken.contains("D_词典 枚=$member 态=读失败"))
        }
        assertTrue("那句读数没带成因：$spoken", spoken.contains("型="))
    }

    /**
     * ⑧ 措辞只住一处、三态三句各异、而凭据不进读数。
     *
     * 三句各异是本批的**正判据**：若「包里没有」与「读失败」念成同一句，⑦ 那一声就白出 ——
     * 事后翻日志仍然分不出是包残缺还是盘上出问题。
     */
    @Test
    fun 降级那一句三态各异而凭据不落纸() {
        val absent = desktopDictionaryDegradedReading(DESKTOP_DICT_MEMBER_TRANSLATIONS, DesktopDictWhy.Absent)
        val failed = desktopDictionaryDegradedReading(
            DESKTOP_DICT_MEMBER_TRANSLATIONS,
            DesktopDictWhy.Unreadable(IllegalStateException("打不开 https://x.test/y?api_key=SECRET777 那一份")),
        )
        assertTrue("『包里没有』那一句不像人话：$absent", absent.contains("态=包里没有"))
        assertTrue("『读失败』那一句没点态：$failed", failed.contains("态=读失败"))
        assertTrue("那句没带异常类型：$failed", failed.contains("型=IllegalStateException"))
        assertFalse("凭据值进了读数（该被打码）：$failed", failed.contains("SECRET777"))
        assertTrue("打码后键名没留（念不出是哪一枚凭据）：$failed", failed.contains("api_key=***"))
        assertNotEqualsLite("两态不许念成同一句", absent, failed)
        // 措辞单源：那两句字面在全桌面 main 各只有一处
        for (literal in listOf("态=包里没有", "态=读失败")) {
            val hits = DesktopSourceTree.desktopMainSources().sumOf { DesktopSourceTree.codeOccurrences(it, literal) }
            assertEquals("「$literal」那句措辞又有了第二份出处（现在是 $hits 处）：", 1, hits)
        }
        // 对照组：这三枚字面真住在词典那颗文件里（读空会让上面那条恒真）
        val home = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryTagDictionary.kt"))
        assertTrue("对照组失效：「态=读失败」不住在词典那颗文件里", home.contains("态=读失败"))
    }

    /**
     * ⑨ 契约那三枚的返回类型一个字都不动（`app/` 是受保域），而桌面这一侧不许再留 elvis 合并。
     *
     * 负向那一条钉的是 `?: emptySet()` / `?: emptyMap()` 那一形：它正是"我们的故障"被念成
     * "这批名字里没有"的那一步 —— 现在那一步必须走 `Miss` 分支并出声。
     */
    @Test
    fun 契约签名不动而桌面侧不许留elvis合并() {
        val api = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/data/GalleryBusinessApi.kt"))
        assertTrue("契约的译名口被改了：$api", api.contains("suspend fun translations(names: List<String>): Map<String, String>"))
        assertTrue("契约的画师档口被改了：$api", api.contains("suspend fun artistNames(names: List<String>): Set<String>"))
        assertTrue("契约的标题档口被改了：$api", api.contains("suspend fun titleTags(names: List<String>): Map<String, GalleryTitleTag>?"))
        val lexiconFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryTagLexicon.kt")
        val lexicon = DesktopSourceTree.codeText(lexiconFile)
        assertEquals("契约口还在用 elvis 把失败压成空：$lexicon", 0, lexicon.split("?: emptySet()").size - 1)
        assertEquals("契约口还在用 elvis 把失败压成空映象：$lexicon", 0, lexicon.split("?: emptyMap()").size - 1)
        // 对照组：出声那一步真在契约口里（不是只在词典那颗）
        assertEquals(
            "契约口出声的次数（现在是 ${DesktopSourceTree.codeOccurrences(lexiconFile, "desktopDictionaryDegradedReading(")} 处）",
            3,
            DesktopSourceTree.codeOccurrences(lexiconFile, "desktopDictionaryDegradedReading("),
        )
        assertTrue("对照组失效：扫到的不是契约口那颗文件", lexicon.contains("class DesktopGalleryTagLexicon"))
    }

    private fun assertNotEqualsLite(what: String, unexpected: String, actual: String) {
        assertTrue("$what：两句都是「$actual」", unexpected != actual)
    }
}
