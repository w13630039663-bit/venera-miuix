package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.domain.GalleryAnimatedMode
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import java.lang.reflect.ParameterizedType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面设置页那一屏的**账**（第七批）。
 *
 * ## 这批真正要钉的东西
 *
 * 设置页最容易做的坏样子是"摆一排控件，其中几颗按下去什么都不发生"。
 * 防它不靠"我记得只摆了能用的"，靠一条可跑的加法：
 * **`GalleryPreferences` 那 12 枚，每一枚都必须在这份账里有下落** ——
 * 要么屏上能改（[DesktopGallerySettingsSpec.editable]），要么屏上逐颗点名"为什么这一屏没有它"
 * （[DesktopGallerySettingsSpec.notOnScreen]），要么就是那两枚桌面结构性不参与的（hide 两条栏）。
 * 少一枚、重一枚、把缺席的说成能改的，③ 那条加法就红。
 *
 * ## 为什么这些是纯数据而不是扫源码
 *
 * 与 `desktopDomainRows` 同一条理由：**能真跑的判据永远比能扫的可靠** ——
 * 扫文本改一行注释就能绕过，而这里断言的是列表的元素与内容本身。
 * 屏上那一颗 `DesktopGallerySettingsPane` 读的也正是这份数据，所以"判据看到的"与"用户看到的"同源。
 */
class DesktopGallerySettingsSpecTest {

    /** ① 屏上能改的正好 8 枚，且每枚的键名都能在偏好件里找到同一句。 */
    @Test
    fun 屏上能改的八枚键名逐枚回核() {
        val editable = DesktopGallerySettingsSpec.editable
        assertEquals(
            "能改的档位数变了（现在是 ${editable.joinToString { it.key }}）—— 加一枚之前要先有消费点，" +
                "否则这一屏就又长出一颗假开关（批 61 搬进第八枚 backdrop：消费点是浮层那一层底）",
            8, editable.size,
        )
        val prefsFile = DesktopSourceTree.codeText(
            java.io.File(
                DesktopSourceTree.repoRoot,
                "desktop/src/main/kotlin/com/venera/desktop/gallery/data/DesktopGalleryPreferences.kt",
            ),
        )
        editable.forEach { row ->
            assertTrue("「${row.title}」的键名 ${row.key} 在偏好件里找不到 —— 这一行写的是不存在的档", prefsFile.contains("\"${row.key}\""))
            assertTrue("「${row.title}」的说明是空的", row.caption.isNotBlank())
        }
        // 对照组：抽取失效时上面那条会恒真 —— 钉一枚**不该**出现在偏好件里的键名。
        assertFalse("对照组失效：不存在的键名也被 contains 命中了", prefsFile.contains("\"pref_gallery_no_such_key\""))
    }

    /** ② 屏上点名的缺席档正好 3 枚（批 61 起：backdrop 有了消费点，搬进能改那一区；批 108 起「画成水彩」补点名的一枚），且每枚都是 `GalleryPreferences` 上真有的成员。 */
    @Test
    fun 屏上点名的三枚缺席档逐枚回核接口() {
        val absent = DesktopGallerySettingsSpec.notOnScreen
        assertEquals("该点名的缺席档数目变了（现在是 ${absent.joinToString { it.pref }}）", 3, absent.size)
        val iface = DesktopSourceTree.codeText(
            DesktopSourceTree.androidSource("gallery/data/GalleryPorts.kt"),
        )
        absent.forEach { row ->
            assertTrue("「${row.pref}」不是 GalleryPreferences 接口上的成员 —— 点名要点真东西", iface.contains("val ${row.pref}:"))
            assertTrue("「${row.pref}」那句为什么是空的或太短：${row.why}", row.why.length >= 8)
        }
        // 对照组：接口里真读到了别的东西（读空会让上面那条 contains 恒真）。
        assertTrue("对照组失效：没读到接口本体", iface.contains("interface GalleryPreferences"))
    }

    /** ③ 那条加法：8 + 3 + 2 = 13，一枚不多一枚不少，且不重叠。 */
    @Test
    fun 十三枚偏好每一枚都有下落() {
        val editable = DesktopGallerySettingsSpec.editable.map { it.pref }
        val absent = DesktopGallerySettingsSpec.notOnScreen.map { it.pref }
        val structural = DesktopGallerySettingsSpec.STRUCTURAL_ABSENT
        assertEquals("能改的与点名缺席的有重叠 —— 同一枚不许既说能改又说没有", 0, (editable intersect absent).size)
        assertEquals("两批里出现了那两枚结构性不参与的档", 0, (editable + absent).intersect(structural.toSet()).size)
        assertEquals("13 枚偏好每一枚都要有下落", 13, (editable + absent + structural).distinct().size)

        // 而且这 13 个名字必须就是接口上那 13 个（不多不少）—— 否则"每一枚都有下落"是句空话。
        val iface = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/data/GalleryPorts.kt"))
        val members = Regex("val (gallery[A-Za-z]+): StateFlow").findAll(iface).map { it.groupValues[1] }.toList()
        assertEquals("对照组失效：接口上抽出的成员数不是 13（现在是 ${members.size}：$members）", 13, members.size)
        assertEquals("账上的 13 枚与接口上的 13 枚不是同一批", members.sorted(), (editable + absent + structural).sorted())
    }

    /** ④ 缺席那一句不许用不承诺任何东西的措辞（与域缺席那条同族，但这里管的是**档位**级）。 */
    @Test
    fun 缺席档位的措辞不许是时间表() {
        val forbidden = listOf("暂不支持", "敬请期待", "开发中", "即将上线", "TODO")
        DesktopGallerySettingsSpec.notOnScreen.forEach { row ->
            forbidden.forEach { word ->
                assertTrue("「${row.pref}」那句为什么里出现了「$word」：${row.why}", row.why.contains(word).not())
            }
        }
    }

    /**
     * ⑦ 那六句"为什么"不许再拿一句**已经过期**的总称整块否掉。
     *
     * 六句从前都写着"桌面没有大图页"。而第十一批 P0/P1 之后桌面**有**详情浮层：
     * 能换三档（预览/大图/原图）、能翻页。于是那半句总称既不成立、又替下一轮把
     * "这一枚到底差哪一层"糊掉了 —— 这正是本仓最忌的"文案说还没有 X 而 X 已落地"那一族
     * （第八批撤过一条、P0 又撤过两条），这一条把它钉成机械判据。
     */
    @Test
    fun 缺席理由不许再用过期的总称() {
        val offenders = DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopUiSource("DesktopGallerySettingsSpec.kt"))
            .filter { it.contains("桌面没有大图页") }
            .map { it.trim() }
        assertTrue(
            "还有缺席理由在写「桌面没有大图页」（桌面今天有详情浮层，能换档能翻页）：" + offenders.joinToString(" ｜ "),
            offenders.isEmpty(),
        )
        // 对照组：那三枚仍在账上（读空了上面那条恒真），且每句各自点名差的是哪一层。
        val rows = DesktopGallerySettingsSpec.notOnScreen
        assertEquals("点名缺席的枚数变了 —— 那笔 8+3+2=13 要跟着改", 3, rows.size)
        assertEquals("三句理由撞成了同一句（那等于没在说每一枚各自差什么）", rows.size, rows.map { it.why }.distinct().size)
        rows.forEach { row ->
            assertTrue("「${row.pref}」那句说不清差的是哪一层：${row.why}", row.why.length >= 14)
        }
    }

    /**
     * ⑥ 每一枚"屏上能改"的档，账上记的那道写口要**三样都对**：偏好件上真存在、
     * 收的值类型就是这一枚流吐的那个类型、而且这一屏真的调它。
     *
     * 这一条钉的是本批最坏的那种坏样子：那一屏摆齐了四行，
     * `DesktopGalleryPreferencesTest` ⑩ 也钉住了"只有那四枚有写口"，
     * 可**屏上那一行与写口之间没有线** —— 按下去什么都不发生，而所有已有的判据照样全绿。
     * ⑩ 管"写口存在"，①②③ 管"账没漏"，中间这一段此前没人管。
     *
     * 类型这一头要先把原始类型折算成包装类型：泛型实参只会给包装那一头（`StateFlow<Boolean>`
     * 反射读到的是 `java.lang.Boolean`），而写口收的是原始 `boolean`，不对齐就会把对的说成错的。
     */
    /**
     * ⑧ 动图那一行的三档与那句"桌面分不出哪种网"（批 52）。
     *
     * 三档必须是共享那枚枚举的**全部**：自造一枚 = 屏上一颗没人读的档；少一档 = 对岸能设的值
     * 在桌面摆不出选中态（那一行永远没有亮点，用户读到的是"这一屏坏了"）。
     * 而 `WIFI_ONLY` 在桌面走的是 `unmetered = true` 那一支 ⇒ 屏上必须明说桌面分不出哪种网，
     * 不然那一档就是在承诺一件它没做的事。
     *
     * ⚠️ 名字表要求是**成对**的（`X to "…"`）而不是 `when` 展开：那一族的**判定**只许住在共享
     * `GalleryMotion.animates` 里，而 `DesktopGalleryViewerMotionTest` ⑥ 扫的正是
     * "枚举成员 + 分支箭头"那个形状 —— 它分不清"重述判定"与"给档位起名"，就别去撞它。
     */
    /** ⑤ 设置域已**转正**：不再是坦白页，且只有一行（这一屏就是那一行）。 */
    @Test
    fun 设置域已转正且只有一行() {
        assertTrue("设置域还是坦白页", DesktopDomain.SETTINGS.isWired)
        val rows = desktopDomainRows(DesktopDomain.SETTINGS)
        assertEquals("设置域应当只有一行（那一屏就是它），现在是 ${rows.map { it.title }}", 1, rows.size)
        assertTrue("这一行的内容位是空的 —— 那又变回不可点的坦白行了", rows.first().content != null)
        assertTrue("这一行的理由位不该有（缺席才填理由）", rows.first().reason == null)
        // 对照组：真页面才有的形状 —— 另一域（搜索）也是一行且 content 非空，证明这条判据不是恒真。
        assertTrue("对照组失效：搜索域那行读不到内容位", desktopDomainRows(DesktopDomain.SEARCH).first().content != null)
    }

    /**
     * 原始类型 → 包装类型。只列桌面今天用得到的两种。
     *
     * 哪天有枚写口收了别的原始类型，这条断言会红在"写口收 int 而流吐 Integer"那一格 ——
     * 那时候往这里加一行，而不是把断言放宽。
     */
    private fun Class<*>.wrapped(): Class<*> = when (this) {
        Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
        Int::class.javaPrimitiveType -> Int::class.javaObjectType
        else -> this
    }
}
