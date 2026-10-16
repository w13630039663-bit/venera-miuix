package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 99 · 卡上那枚「已下载 / 离线不全」的**接线与分家**那几格。
 *
 * 那颗件是私有的、又要一颗引擎才渲得出来，所以这一族照本仓既有那几颗的形状来钉：
 * 数出现次数（不是数"存在"）+ 负向钉"没在旁边写第二种口径" + 逐字钉那笔高度账没被动。
 */
class DesktopComicCardOfflineTest {

    private val pane = DesktopSourceTree.desktopUiSource("DesktopComicPane.kt")
    private val mark = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicOfflineMark.kt")
    private val badge = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicCardOffline.kt")

    /** ① 卡上真摆那一枚：声明一处 + 卡上一处（只断言 contains 会被别处一处合法调用喂饱）。 */
    @Test
    fun 那一枚标记在卡上摆得出来() {
        assertEquals("声明只许一处：" + DesktopSourceTree.codeOccurrences(mark, "internal fun ComicOfflineMark("),
            1, DesktopSourceTree.codeOccurrences(mark, "internal fun ComicOfflineMark("))
        assertEquals("卡上那一处调用不对：" + DesktopSourceTree.codeOccurrences(pane, "ComicOfflineMark("),
            1, DesktopSourceTree.codeOccurrences(pane, "ComicOfflineMark("))
    }

    /** ② 那一枚**不许点得动**（它会压在封面那一片命中区上，能点就会吃掉"点卡进详情"那一条路）。 */
    @Test
    fun 那一枚标记不吃点击() {
        val code = DesktopSourceTree.codeText(mark)
        assertFalse("那枚标记点得动（会把卡那一条路抢走）：" + code, code.contains("clickable"))
        // 对照：同角那枚「收藏」是**故意**点得动的，负向不许把两枚一起钉成"整屏都没 clickable"
        assertTrue("对照组失效：收藏那枚不可点了",
            DesktopSourceTree.codeText(pane).contains("ComicFavoritePill("))
        assertTrue("收藏那颗件自己还带着 clickable（对照）：",
            DesktopSourceTree.codeOccurrences(pane, "private fun ComicFavoritePill(") == 1)
    }

    /** ③ 读盘那一跳住在**另一颗文件**里，且整颗文件只一跳（没挤进那一屏、也没多一跳）。 */
    @Test
    fun 读盘那一跳在另一颗文件里() {
        assertEquals("装载那一跳不止一处：" + DesktopSourceTree.codeOccurrences(mark, "withContext(Dispatchers.IO)"),
            1, DesktopSourceTree.codeOccurrences(mark, "withContext(Dispatchers.IO)"))
        // 那一屏今天本来就有两处读盘（引擎装载 + 收藏夹那一路），所以"没挤进来"钉的不是跳数而是**那颗读侧没进那一屏**。
        assertEquals("读侧那颗跑到那一屏里去了（装载该住在另一颗件）：" +
                DesktopSourceTree.codeOccurrences(pane, "desktopComicDownloadedChapters("),
            0, DesktopSourceTree.codeOccurrences(pane, "desktopComicDownloadedChapters("))
        assertEquals("装载那颗只许一处被调用：" + DesktopSourceTree.codeOccurrences(pane, "rememberDesktopComicOfflineBadges("),
            1, DesktopSourceTree.codeOccurrences(pane, "rememberDesktopComicOfflineBadges("))
        assertEquals("读数那句得有消费点：" + DesktopSourceTree.codeOccurrences(mark, "println(desktopComicOfflineReading("),
            1, DesktopSourceTree.codeOccurrences(mark, "println(desktopComicOfflineReading("))
    }

    /**
     * ④ **只许复用批 97 那颗读侧**：那条线上一共存着三把"齐不齐"的口径，
     * 这里再数一次页就是第四把 —— 于是把"自己数盘"那几种写法各钉一枚负向。
     */
    @Test
    fun 只复用那颗读侧而不写第四种口径() {
        assertEquals("读侧那颗没被复用：" + DesktopSourceTree.codeOccurrences(mark, "desktopComicDownloadedChapters("),
            1, DesktopSourceTree.codeOccurrences(mark, "desktopComicDownloadedChapters("))
        val code = DesktopSourceTree.codeText(mark) + DesktopSourceTree.codeText(badge)
        listOf("chapter.json", "listFiles", "expectedPages ==", "writtenPages ==", "isFile", "walkTopDown").forEach {
            assertFalse("那颗件里出现了自己数盘的那一族（第二口径）：$it ｜" + code.take(200), code.contains(it))
        }
    }

    /** ⑤ 新形参必填、不给默认值（漏传不报错那一族：给了默认值就等于允许没人接）。 */
    @Test
    fun 新形参必填不给默认值() {
        val code = DesktopSourceTree.codeText(pane)
        assertTrue("卡上那枚形参没递进 ComicCard：" + code, code.contains("offline: DesktopComicOfflineBadge?"))
        assertFalse("那枚形参带了默认值（漏传不报错）：" + code, code.contains("offline: DesktopComicOfflineBadge? ="))
        assertTrue("section 那一层也必填：" + code, code.contains("offline: Map<String, DesktopComicOfflineBadge>"))
        assertFalse("section 那枚带了默认值：" + code, code.contains("offline: Map<String, DesktopComicOfflineBadge> ="))
    }

    /** ⑥ 那笔高度账不许被动（`estimatedGridHeight` 与封面 3:4 是同一颗算式；往卡下面加一行就会算错行高）。 */
    @Test
    fun 那笔高度账没被动() {
        val code = DesktopSourceTree.codeText(pane)
        assertTrue("卡高那笔账被改了（标记只能压在封面上那一角，不能加行）：" + code,
            code.contains("val cardHeight = cardWidth * (4f / 3f) + 22.dp"))
        assertTrue("那一枚仍挂在封面那一片里（BottomEnd）：" + code, code.contains("Alignment.BottomEnd"))
    }

    /** ⑦ 查表吃的是「站 + 号」那一枚键，而且**只有一处出处**（手工拼串就会与写侧那把不同轨）。 */
    @Test
    fun 键只有一处出处() {
        assertEquals("算键那颗不止一处声明：" + DesktopSourceTree.codeOccurrences(badge, "internal fun desktopComicOfflineKey("),
            1, DesktopSourceTree.codeOccurrences(badge, "internal fun desktopComicOfflineKey("))
        assertFalse("那一屏手工拼键（绕开那颗唯一的出处）：" + DesktopSourceTree.codeText(pane),
            DesktopSourceTree.codeText(pane).contains("config.sourceKey + \"/\" + card.id"))
        assertEquals("装载那颗查表用的是唯一那颗键：" + DesktopSourceTree.codeOccurrences(pane, "desktopComicOfflineKey("),
            1, DesktopSourceTree.codeOccurrences(pane, "desktopComicOfflineKey("))
    }

    /** ⑧ 那两句重复定掉了：`第 2 话 第2話` 那种挨着念的两截不许再上屏（图证量出来的观感）。 */
    @Test
    fun 行里那两句不再挨着念() {
        val line = desktopComicDownloadedRowLine(
            com.venera.desktop.gallery.data.DesktopComicDownloadedChapter(
                sourceKey = "jm", comicId = "1461316", chapterId = "1461318", order = 1,
                title = "第2話", expectedPages = 89, writtenPages = 89, bytes = 1L, complete = true,
            )
        )
        assertFalse("行里还是两截挨着念：" + line, line.contains("第 2 话 第2話"))
        assertTrue("题名该用「」包起来（与仓里其它处同一写法）：" + line, line.contains("第 2 话「第2話」"))
    }

    /**
     * ⑨ 命中那句得有消费点 —— 「墙上那 30 张里贴出了几枚」必须**念得出来**。
     *
     * ⚠️ 这一条是包内图证逼出来的：那一帧墙上真出了 30 张卡（代理带通），而本机那棵树里只有 `1461316` 一本
     * —— 它不在墙上，所以那一枚**一枚都没贴出来**，像素那条路量不到。没有这句读数就只能登记"没拿到"，
     * 有了它，"没贴上"与"贴上了而我没拍到"是两种分得开的事实。
     */
    @Test
    fun 命中那句有消费点() {
        val code = DesktopSourceTree.codeText(pane)
        assertEquals("那句命中读数被调用的次数不对（只许屏上那一条）：" +
                DesktopSourceTree.codeOccurrences(pane, "desktopComicOfflineHitReading("),
            1, DesktopSourceTree.codeOccurrences(pane, "desktopComicOfflineHitReading("))
        assertFalse("读数串漏进界面文案：" + code, code.contains("\"D_卡标命中"))
        // 钉**整串表达式**而不是 `flatMap` 那一段：装载那一段里也有 `all = shown.flatMap { it.comics }`，
        // 只钉半串就会被它合法喂饱 ⇒ "恒交回空墙"这种假动作全绿（台架 N19 量出来的那一版）。
        assertTrue("它得吃**墙上那排卡**的号而不是只吃盘上那张表：" + code,
            code.contains("ready.sections.flatMap { it.comics }.map { it.id }"))
    }
}
