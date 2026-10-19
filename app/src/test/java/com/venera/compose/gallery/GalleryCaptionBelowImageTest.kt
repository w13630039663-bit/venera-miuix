package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「文字区住在封面**下方**，不住在图上」的判据（2026-10-17 第三十二轮）。
 *
 * ## 它拦的是什么事
 *
 * 用户原话：「这边我之前说的做这个浮层，改成图片能在外面预览全部显示那种不要浮层，
 * 这样飞行动画也会好很多」。
 *
 * 2026-10-17 第二十四轮起，pixiv 推荐墙的文字区曾**浮在封面下沿**（半透明毛玻璃压在图上）。
 * 那条浮层压掉封面底沿一条 ⇒ 图在网格里看不全。更麻烦的是飞行：`PixelCopy` 截的是
 * **封面那一块**，而浮层 caption 叠在这一块里 ⇒ 被一起截进位图，于是大图页要额外做
 * 「把位图切两段画 + 条子按 `1f - t` 淡出 + 垫帧把底部一绺裁掉」才接得上画面框
 * （第二十八 / 二十九轮那一整套减退机理）。撤掉浮层之后，截帧里只有图，起点与落点
 * **天然同构**，那一整套机理随之退役。
 *
 * 所以这一轮的判据是**双向**的：
 *  - **正向**：文字区仍在（头像 / 标题 / 画师名），搬到封面下方；两行行高仍写死；
 *  - **负向**：浮层的形状、开关、减退机理**一处都不许留** —— 留着就是死代码，
 *    而"浮层还在不在"这件事用源码扫描才算得清（纯 JVM 里量不到绘制）。
 *
 * ## 头像为什么还在，且判据是"传没传头像地址"
 *
 * 第二十四轮用户要的是「和排行榜那种一样的有头像显示」，浮层只是当时的落法。
 * 这一轮撤的是浮层，不是头像 ⇒ 文字区保留「头像 + 两行」那一档。
 *
 * ⚠️ 判据必须是 `authorAvatar != null`（**墙级**传没传头像表），不能写成
 * `!authorName.isNullOrBlank()`：画师名五面墙都非空，拿它判就会把主墙 / 搜索 /
 * 收藏那几处卡的形状一起改掉（那几处不传头像表，`authorAvatars[uid]` 是 null）。
 * 而 pixiv 两墙的表是 `associate { uid -> url }`，**每张卡都有这一项**（地址可能是空串），
 * 所以整墙同一形状，不会在瀑布流里高矮不齐 —— 地址为空那一张落首字母座。
 *
 * ## 为什么判据钉在源码形状上
 *
 * 同仓内全部尺子的口径：`app/` 没有 Robolectric / compose-ui-test，卡片画的是
 * `Box` / `Column`，纯 JVM 里量不到。但这一轮的成因全是**结构性的** ——
 * 「浮层在不在」与「减退机理留没留」都各有一处唯一形状，扫源码就钉得住。
 *
 * ⚠️ 注释行不参与匹配（`codeLines` 那把口径）：这些文件的大段注释里写着同样的符号名，
 * 不过滤就等于判据读的是我们自己的解释（本文件里就有好几处"浮层"二字）。
 */
class GalleryCaptionBelowImageTest {

    private fun codeLines(dir: String, name: String): List<IndexedValue<String>> {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（目录 $dir，根：${RepoSources.root.absolutePath}）")
        return file.readLines().withIndex().filter { (_, line) ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    /** 按**函数体**切一段：从头部那一行到下一个第 0 列的 `}`（函数体内部的括号都缩进）。 */
    private fun bodyOf(lines: List<IndexedValue<String>>, header: String): String {
        val start = lines.firstOrNull { it.value.contains(header) }?.index
            ?: error("找不到 `$header`")
        val end = lines.firstOrNull { it.index > start && it.value == "}" }?.index
            ?: error("找不到 `$header` 的收尾括号")
        return lines.filter { it.index in start..end }.joinToString("\n") { it.value }
    }

    /** 按起点/终点**代码**锚点切一段（含起点行、不含终点行）—— 锚点不能挑注释行。 */
    private fun regionBetween(lines: List<IndexedValue<String>>, from: String, to: String): String {
        val start = lines.firstOrNull { it.value.contains(from) }?.index
            ?: error("找不到起点 `$from`")
        val end = lines.firstOrNull { it.index > start && it.value.contains(to) }?.index
            ?: error("找不到终点 `$to`（起点在第 ${start + 1} 行）")
        return lines.filter { it.index in start until end }.joinToString("\n") { it.value }
    }

    private fun countIn(body: String, regex: String): Int = Regex(regex).findAll(body).count()

    private fun all(lines: List<IndexedValue<String>>): String =
        lines.joinToString("\n") { it.value }

    private val caption = codeLines("com/venera/compose/gallery/ui", "GalleryCardCaption.kt")
    private val screen = codeLines("com/venera/compose/gallery/ui", "GalleryScreen.kt")
    private val forYou = codeLines("com/venera/compose/gallery/ui", "GalleryForYouScreen.kt")
    private val flyIn = codeLines("com/venera/compose/gallery/ui", "GalleryFlyIn.kt")
    private val post = codeLines("com/venera/compose/gallery/ui", "GalleryPostScreen.kt")
    private val bookmarks = codeLines("com/venera/compose/gallery/ui", "GalleryPixivBookmarksScreen.kt")

    /** 去程飞行体那一段：从源窗算式到返回程那一层之前。 */
    private val flightBody: String = regionBetween(
        post,
        "val src = coverSourceRect(frame.width, frame.height, width, height)",
        "val exitFrom = exitStart",
    )

    /** 垫帧那一段：从圆角倍率到加载态分流之前。 */
    private val padFrame: String = regionBetween(
        post,
        "val padCornerRadius = with(density) {",
        "!(fastLoaded || largeSettled || hdSettled)",
    )

    // ── 一、浮层那一档整档退役：形状、开关、信道都零残留 ──────────────────

    @Test
    fun `浮层的形状与开关零残留`() {
        assertEquals(
            "`GalleryCardCaption` 不再收 `floating` —— 文字区只有「封面下方」这一个住处：" +
                "浮层压掉封面底沿一条，图在网格里就看不全：\n${all(caption)}",
            0,
            countIn(all(caption), """floating"""),
        )
        assertEquals(
            "模糊底那两个参数（`coverUrl` / `coverAspect`）随浮层一起退役 —— 只有模糊底用得到，" +
                "留着就是没人读的死参数：\n${all(caption)}",
            0,
            countIn(all(caption), """coverUrl|coverAspect"""),
        )
        assertEquals(
            "`FloatingCardCaption` 与 `FLOATING_LINE_HEIGHT` 零残留（整颗件跟着浮层退场，" +
                "行高改钉统一的 `TEXT_LINE_HEIGHT`）：\n${all(caption)}",
            0,
            countIn(all(caption), """FloatingCardCaption|FLOATING_LINE_HEIGHT"""),
        )
        assertEquals(
            "卡片与墙上的 `floatingCaption(s)` 开关零残留（全仓曾只有一个调用点开它）：\n${all(screen)}",
            0,
            countIn(all(screen), """floatingCaption"""),
        )
        assertEquals(
            "`GalleryForYouPage` 那一路透传也一并撤（六处墙同形状，开关没了意义）：\n${all(forYou)}",
            0,
            countIn(all(forYou), """floatingCaptions"""),
        )
    }

    @Test
    fun `减退机理零残留：不量高、不递高度、不切两段、不裁垫帧`() {
        assertEquals(
            "`GalleryFlyIn` 上不再有 caption 高度那一套（`captionHeight` / `pendingCaptionHeight` /" +
                " `noteCaptionHeight`）—— 没有卡再把文字叠进封面块，这条信道就无从谈起：\n${all(flyIn)}",
            0,
            countIn(all(flyIn), """captionHeight"""),
        )
        assertEquals(
            "卡片不再量文字区高度（`onSizeChanged { captionHeight = ... }` 那一处随浮层退场）：\n${all(screen)}",
            0,
            countIn(all(screen), """captionHeight"""),
        )
        assertEquals(
            "大图页不再读 `GalleryFlyIn.captionHeight`，也不再有 `capSrcHeight` / `capFrac` 那两颗" +
                "中间量：\n${all(post)}",
            0,
            countIn(all(post), """captionHeight|capSrcHeight|capFrac"""),
        )
        assertEquals(
            "垫帧不再拦一层 `drawWithContent`（那层是把位图底部一绺裁掉用的，浮层没了它就没了活）：" +
                "\n${all(post)}",
            0,
            countIn(all(post), """drawWithContent|clipRect"""),
        )
    }

    // ── 二、文字区两档：按"传没传头像地址"分 ────────────────────────────

    @Test
    fun `文字区两档，判据是传没传头像地址`() {
        val body = bodyOf(caption, "internal fun GalleryCardCaption(")
        assertEquals(
            "两档的闸门必须是 `if (authorAvatar != null)` 且**只有一处**：判据是" +
                "「墙级传没传头像表」，不是「有没有画师名」—— 画师名五面墙都非空，拿它判就把" +
                "主墙 / 搜索 / 收藏那几处卡的形状一起改了：\n$body",
            1,
            countIn(body, """if \(authorAvatar != null\)"""),
        )
        assertEquals(
            "头像那一颗必须是 `space10`(24dp) 圆座（`veneraAvatarRing`）—— " +
                "2026-10-18 用户「作者的头像可以大点，画廊首页推荐的头像也大点吧」把它从 " +
                "`space9`(20dp) 提了一档；三面墙共用本组件，所以一处改三处一起变：\n$body",
            1,
            countIn(body, """\.size\(tokens\.spacing\.space10\)"""),
        )
        assertEquals(
            "头像必须挂描边 `veneraAvatarRing`（与全站那颗件同一口径）：\n$body",
            1,
            countIn(body, """veneraAvatarRing\("""),
        )
        assertEquals(
            "站方没给头像地址（空串）必须落首字母座 `authorName.orEmpty().take(1)`，" +
                "**不拿别人的头像顶**：\n$body",
            1,
            countIn(body, """authorName\.orEmpty\(\)\.take\(1\)"""),
        )
        assertEquals(
            "原一行支（站标 + 标题）必须还在 —— 不传头像表的五处调用点逐像素不变：\n$body",
            1,
            countIn(body, """VeneraGallerySourceMark\("""),
        )
    }

    @Test
    fun `两行都写死行高，倍数与 miuix paragraph 同档`() {
        val body = bodyOf(caption, "internal fun GalleryCardCaption(")
        assertEquals(
            "标题与画师名两行都必须写死 `lineHeight = TEXT_LINE_HEIGHT.em`（1.2f，照抄 miuix" +
                " `paragraph`）：不指定时行高随字形走（拉丁/CJK/emoji 差 1~2px），瀑布流里就是" +
                "同比例的两张卡差一截：\n$body",
            2,
            countIn(body, """lineHeight = TEXT_LINE_HEIGHT\.em"""),
        )
    }

    // ── 三、卡片恒走 Column：封面在上，文字区在下 ────────────────────────

    @Test
    fun `卡片不再叠放，文字区住在封面下方`() {
        val body = bodyOf(screen, "fun GalleryPostCard(")
        assertEquals(
            "卡片形状必须只有一条：`Column(modifier = Modifier.fillMaxWidth())` —— 浮层那一档的" +
                "`Box + align(BottomCenter)` 零残留（它是真叠放的唯一落法）：\n$body",
            1,
            countIn(body, """Column\(modifier = Modifier\.fillMaxWidth\(\)\)"""),
        )
        assertEquals(
            "`align(Alignment.BottomCenter)` 零残留：它只可能是文字区往封面上叠那一档，而现在" +
                "文字区住在封面**外面**：\n$body",
            0,
            countIn(body, """align\(Alignment\.BottomCenter\)"""),
        )
        assertEquals(
            "头像必须原样交给文字区那一颗（`authorAvatar = authorAvatar,`），由它决定走哪一档：\n$body",
            1,
            countIn(body, """authorAvatar = authorAvatar,"""),
        )
    }

    // ── 四、飞行体与垫帧：整张位图一次画完 ──────────────────────────────

    @Test
    fun `飞行体只画一次，不再按进度淡一条`() {
        assertEquals(
            "飞行体必须只有**一个** `drawImage(`（整张位图一次画完）—— 浮层在时是三个（封面一截、" +
                "条子一截、没有条可淡时的等价支）：\n$flightBody",
            1,
            countIn(flightBody, """drawImage\("""),
        )
        assertEquals(
            "去程仍全程不淡（`alpha = bodyAlpha,`），且不许再有 `alpha = bodyAlpha * (1f - t)` 那条" +
                "随进度淡出的支：截帧里现在只有图，起点与落点同构，不需要减退：\n$flightBody",
            0,
            countIn(flightBody, """alpha = bodyAlpha \* \(1f - t\)"""),
        )
        assertEquals(
            "源尺寸必须是整块源窗 `IntSize(src.width, src.height)`，不许再扣掉一条" +
                "（`src.height - capSrcHeight`）：\n$flightBody",
            1,
            countIn(flightBody, """IntSize\(src\.width, src\.height\)"""),
        )
        assertEquals(
            "垫帧不再裁底部一绺（`drawWithContent` / `clipRect` 零残留）：位图就是封面那一块，整张" +
                "铺进来才与画面框同构：\n$padFrame",
            0,
            countIn(padFrame, """drawWithContent|clipRect"""),
        )
    }

    // ── 五、头像表两墙都传：推荐墙与云端书签墙 ───────────────────────────

    @Test
    fun `pixiv 推荐墙与云端书签墙都递头像表`() {
        // ⚠️ 2026-10-17 第二轮改判：这一处从 **1** 变成 **2**。
        // 用户原话「p 站搜索出来的图片卡片就不要带 p 站图标了，按照外面的头像 + 标题 + 作者名」——
        // 于是**搜索墙**在 pixiv 那一域也递头像表（`svm.pixivAvatars`），与首页推荐墙同一档形状。
        // 两处都必须是"按域递"而不是无条件递：booru 三站响应里没有头像那一格，
        // 传空表就是给墙画首字母座。
        assertEquals(
            "首页主墙与搜索墙都按域递头像表（`authorAvatars = if (domain == GalleryHomeDomain.PIXIV)`）——" +
                "booru 三站响应里没有头像那一格，传空表就是给墙画首字母座：\n${all(screen)}",
            2,
            countIn(all(screen), """authorAvatars = if \(domain == GalleryHomeDomain\.PIXIV\)"""),
        )
        assertEquals(
            "`GalleryForYouPage` 必须把头像表透传给墙（页这一层不掺和形状）：\n${all(forYou)}",
            1,
            countIn(all(forYou), """authorAvatars = authorAvatars,"""),
        )
        assertEquals(
            "云端书签墙也要递头像表（`authorAvatars = avatars,`）：那一屏也是 pixiv 单站，" +
                "且与推荐墙共用同一颗 `GalleryCardsGrid`：\n${all(bookmarks)}",
            1,
            countIn(all(bookmarks), """authorAvatars = avatars,"""),
        )
    }
}
