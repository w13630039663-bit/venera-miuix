package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GallerySite
import com.venera.desktop.gallery.data.DesktopDictWhy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第四十一批 批 41 · 画师那一栏的**离线词典兜底**。
 *
 * ## 这一格是谁量出来的
 *
 * 批 40 起窗那一跑（2026-10-08，PID 1892）stdout 念过一句：
 * `D_日志 [GalleryTagCategories] 取 https://safebooru.donmai.us/posts/12336879 的标签分类失败，退到离线词典兜底：分类页没有可识别的 tag-type 类名`
 * —— 那句"退到离线词典兜底"是**共享面**写的，而桌面这一侧当时根本没接兜底：
 * [desktopDetailArtists] 只吃两枚参数，第三枚 `fallbackArtistNames` 走的是那颗共享件的**默认值** `emptySet()`
 * （"漏传不报错的新参数用默认值"那一族，手册 §0 与 `project-uncomposed-grid-state-noop`）。
 * 于是站方没答上那一档，屏上永远是一句"这次没取到"，而真表里 25,526 行画师档就在包里躺着。
 *
 * ## 判据取的是 Android 那条互斥口径，不是"多一处名字"
 *
 * `GalleryPostScreen.kt:374-377`：兜底**只在站方判定没拿到时参与**，拿到分类就把词典那一份撤掉 ——
 * 两套判据同时进 [com.venera.compose.gallery.domain.galleryArtistNames] 会摆出
 * "站方判通用、词典判画师"那种谁都解释不了的分组。所以这一批的核心不是"接上"，
 * 而是**接上并且作用域互斥**（② 那条钉的就是它，方向是"站方答上了而词典那份没被撤"）。
 */
class DesktopArtistFallbackTest {

    /** ① 站方那一页没答上 ⇒ 词典那份顶上，且这一档要说得出名字来自词典。 */
    @Test
    fun 站方没答上时词典那份顶上() {
        val line = desktopDetailArtists(
            listOf("ahoym", "touhou", "1girl"),
            categories = null,
            fallbackArtistNames = setOf("ahoym"),
            asked = true,
        )
        assertTrue("兜底没顶上：$line", line is DesktopDetailArtists.Names)
        val names = (line as DesktopDetailArtists.Names)
        assertEquals(listOf("ahoym"), names.names)
        assertEquals(
            "名字来自词典而屏上不说来源，就是把「我们猜的」摆成站方给的",
            DesktopArtistSource.DICTIONARY,
            names.from,
        )
    }

    /** ② 站方答上了 ⇒ 词典那份必须撤掉（两套判据不许同时进同一排）。 */
    @Test
    fun 站方答上了就把词典那份撤掉() {
        val line = desktopDetailArtists(
            listOf("gijang", "ahoym", "touhou"),
            categories = mapOf("gijang" to 1, "touhou" to 3),
            fallbackArtistNames = setOf("ahoym"),
            asked = true,
        )
        assertTrue(line is DesktopDetailArtists.Names)
        val names = (line as DesktopDetailArtists.Names)
        assertEquals("站方判出的那一枚之外混进了词典那份（两套判据同排）：${names.names}", listOf("gijang"), names.names)
        assertEquals(DesktopArtistSource.SITE, names.from)
    }

    /** ③ 两处都没答上仍是"这次没取到"，不许说成"这张没有画师"。 */
    @Test
    fun 两处都没答上时仍不说成没有画师() {
        val line = desktopDetailArtists(listOf("touhou"), categories = null, fallbackArtistNames = emptySet(), asked = true)
        assertTrue("兜底空着时该退回'没取到'那一档：$line", line is DesktopDetailArtists.NotAnswered)
        val notice = desktopArtistLineNotice(line, GallerySite.SAFEBOORU, dictionary = null)
        assertFalse("那句读数把我们的故障说成了用户没干活：$notice", notice.contains("没有画师"))
    }

    /** ④ 那句读数说得出名字是哪来的：词典那一档必须点名，站方那一档不留多余一句。 */
    @Test
    fun 名字来自词典时那句说得出来源() {
        val fromDictionary = DesktopDetailArtists.Names(listOf("ahoym"), DesktopArtistSource.DICTIONARY)
        val notice = desktopArtistLineNotice(fromDictionary, GallerySite.SAFEBOORU, dictionary = null)
        assertTrue("屏上摆着词典猜的名字而一句来源都没有：$notice", notice.contains("离线词典"))
        // 站方那一档仍然一句都不多写（名字本身就是内容，那条口径是批 2 定的）
        assertEquals(
            "站方给的名字前面又写了一句废话",
            "",
            desktopArtistLineNotice(DesktopDetailArtists.Names(listOf("gijang"), DesktopArtistSource.SITE), GallerySite.YANDERE, dictionary = null),
        )
    }

    /**
     * ⑤ 面板那一问真发出去，而且第三枚参数是从**构造点**递进去的。
     *
     * 只钉那颗纯函数会漏掉整批的动机（那一问根本没发）；只钉调用点又会漏掉"递进去的是默认值"
     * —— 所以两头各一钉，且第三枚按整串写（手册 §0 坑 ㉔：只数标识符看不见死分支）。
     */
    @Test
    fun 面板发那一问而第三枚从构造点递进去() {
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        assertTrue("面板没问离线词典那一枚画师档", panel.contains("ports.lexicon.artistNames(post.tagList)"))
        // 只看那一处调用的**实参表**里有没有这两枚 —— 命名写法与位置写法都算过（扰动 y2 是等价改写，
        // 把锚点钉成 `fallbackArtistNames = artistFallback` 那种措辞形状，就会红在一次纯换写上：手册 §0 坑 ㉖）
        val call = panel.substringAfter("desktopDetailArtists(").substringBefore(")")
        assertTrue("画师那一栏没吃到词典那份（或吃的还是那颗默认值）：$call", call.contains("artistFallback"))
        assertTrue("画师那一栏没吃到「答没答上」那一档：$call", call.contains("siteCategoriesAsked"))
        // 对照：那一问与兜底住在同一颗 effect 里（Android 同档：本地那两样不等面板）
        assertTrue("对照组失效：面板里根本没有 desktopDetailArtists 这一句", panel.contains("desktopDetailArtists("))
    }

    /** ⑦ 那一页还没答上时是"正在问"，不许提前念"没取到"（`asked` 那一档不是装饰）。 */
    @Test
    fun 还没答上时不许提前说没取到() {
        val line = desktopDetailArtists(
            listOf("ahoym"),
            categories = null,
            fallbackArtistNames = setOf("ahoym"),
            asked = false,
        )
        assertTrue("第一帧就越过 Fetching 直接给了结论：$line", line is DesktopDetailArtists.Fetching)
        assertTrue(
            "正在问那一句不许出现没取到（那是把还没等到说成问过了而失败）",
            !desktopArtistLineNotice(line, GallerySite.SAFEBOORU, dictionary = null).contains("没取到"),
        )
    }

    /**
     * ⑥ 旧那两句"桌面拿不到词典 / 兜底恒空"的说明不许回来（批 40 之后它们是假话）。
     *
     * ⚠️ 这一条**必须扫全文**而不是 `codeText`：那两句都是 KDoc 里的说明，而 `codeText` 滤掉注释行
     * —— 手册 §0 坑 ㉑ 记的就是这一族（"过期说明九成住在注释里，用 readText 扫就被注释满足了 ⇒ 对照恒真"）。
     */
    @Test
    fun 旧那两句恒空的说明不许回来() {
        val policy = DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPolicy.kt").readText()
        val panel = DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt").readText()
        for ((name, code) in listOf("DesktopGalleryDetailPolicy.kt" to policy, "DesktopGalleryDetailPanel.kt" to panel)) {
            assertEquals("$name 里还挂着「titleTags 恒 null」", 0, code.split("恒 null").size - 1)
            assertEquals("$name 里还挂着「桌面恒空」", 0, code.split("桌面恒空").size - 1)
        }
        // 对照组：两颗文件真在被扫（读空会让上面四条恒真）。
        assertTrue("对照组失效：政策那颗文件里没有画师那一栏", policy.contains("internal fun desktopDetailArtists("))
        assertTrue("对照组失效：面板那颗文件里没有译名那一问", panel.contains("ports.lexicon.translations(post.tagList)"))
    }

    /**
     * ⑧ 兜底那一句按**词典那一态**分三种说法（批 78）。
     *
     * 从前只有一句：「…离线词典里也没有这一串标签」。那句在词典读不出时是假话 ——
     * 它声称查过而没查到，而真相是我们没能查。三态因此各给一句，且负向钉住"没查过的那两格不许说成没查到"。
     */
    @Test
    fun 兜底那一句按词典那一态分三种说法() {
        val read = desktopArtistLineNotice(DesktopDetailArtists.NotAnswered, GallerySite.SAFEBOORU, dictionary = null)
        val absent = desktopArtistLineNotice(DesktopDetailArtists.NotAnswered, GallerySite.SAFEBOORU, DesktopDictWhy.Absent)
        val failed = desktopArtistLineNotice(
            DesktopDetailArtists.NotAnswered,
            GallerySite.SAFEBOORU,
            DesktopDictWhy.Unreadable(IllegalStateException("盘上那颗读不动")),
        )
        assertTrue("真读过而没命中那一句丢了原口径：$read", read.contains("离线词典里也没有这一串标签"))
        assertTrue("包里没带那一句没点名『包里没带』：$absent", absent.contains("这个包里也没带离线词典"))
        assertTrue("读不出那一句没点名『没读出来』：$failed", failed.contains("离线词典这次没读出来"))
        assertEquals("三态念成三句各异（合并就是本批要禁的那一格）", 3, listOf(read, absent, failed).distinct().size)
        // 负向：没查过的那两格不许声称"词典里没有这一串标签"
        assertFalse("包里没带却说成查过而没有：$absent", absent.contains("也没有这一串标签"))
        assertFalse("没读出来却说成查过而没有：$failed", failed.contains("也没有这一串标签"))
        // 站方答上了那两档不吃词典这一态（句子逐字不许变）
        assertEquals("这一张站方没标画师", desktopArtistLineNotice(DesktopDetailArtists.Unlabeled, yandeSite, DesktopDictWhy.Absent))
        assertEquals(
            "取到名字就不必再写一句废话",
            "",
            desktopArtistLineNotice(
                DesktopDetailArtists.Names(listOf("gijang"), DesktopArtistSource.SITE),
                yandeSite,
                DesktopDictWhy.Unreadable(IllegalStateException("x")),
            ),
        )
        // 对照组：词典兜底那一档仍然要点名"离线词典"（批 41 判据 ④ 的口径没被这次改动冲掉）
        assertTrue(
            "词典顶上那一句不再点名离线词典",
            desktopArtistLineNotice(
                DesktopDetailArtists.Names(listOf("ahoym"), DesktopArtistSource.DICTIONARY),
                GallerySite.SAFEBOORU,
                DesktopDictWhy.Absent,
            ).contains("离线词典"),
        )
    }

    /** ⑨ 面板把那一态**取到并递进去**（只钉那颗纯函数会漏掉整批：屏上那句还是老的那一句）。 */
    @Test
    fun 面板取到词典那一态而递进那一句() {
        val panelFile = DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt")
        val panel = DesktopSourceTree.codeText(panelFile)
        assertEquals(
            "面板问词典画师档那一枚的次数（现在是 ${DesktopSourceTree.codeOccurrences(panelFile, "artistNamesRead(post.tagList)")} 处）—— 0 处就是态没取，2 处就是问了两遍",
            1,
            DesktopSourceTree.codeOccurrences(panelFile, "artistNamesRead(post.tagList)"),
        )
        assertTrue(
            "那一态没从读口落到那一句的变量上：$panel",
            panel.contains("artistDictWhy = if (read is DesktopDictRead.Miss) read.why else null"),
        )
        assertEquals(
            "上屏那两处都要吃到那一态（现在是 ${DesktopSourceTree.codeOccurrences(panelFile, "desktopArtistLineNotice(line, post.site, artistDictWhy)")} 处）",
            2,
            DesktopSourceTree.codeOccurrences(panelFile, "desktopArtistLineNotice(line, post.site, artistDictWhy)"),
        )
        // 次序：先问词典拿到 read，再把态写进变量（反过来是把上一次的态递给这一次）
        val asked = panel.indexOf("artistNamesRead(post.tagList)")
        val stored = panel.indexOf("artistDictWhy = if (read is DesktopDictRead.Miss)")
        assertTrue("问与记的次序反了（$asked / $stored）", asked in 0 until stored)
        // 对照组：那颗文件真在被扫，且旧的译名那一问没被这次改动搬走
        assertTrue("对照组失效：面板里根本没有画师那一栏的那一句", panel.contains("fallbackArtistNames = artistFallback"))
    }

    private val yandeSite = GallerySite.YANDERE
}
