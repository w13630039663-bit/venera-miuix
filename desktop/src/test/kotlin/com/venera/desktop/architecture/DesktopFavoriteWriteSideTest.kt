package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「详情页那枚收藏钮真的接到了写侧，而且没走那颗会误判的 `toggleFavorite`」。
 *
 * ## 这一格为什么值得单独立一颗
 *
 * 清单 §3 记的等待坑是这一族：`FavoriteDao` 的 `init { refresh() }` 是
 * `scope.launch { _favoritesFlow.value = … }`，**发起即返回**，而初值是 `emptyList()`。
 * 于是任何"拿 `_favoritesFlow.value` 判存在与否"的写法，在冷启动头几按都会把
 * "还没读回来"当成别的东西。收藏那一屏的读侧已经用 `first { isNotEmpty() }` 绕过去了；
 * **写侧此前根本没有**（详情页那句注释写着"后面接"）⇒ 库里 0 条收藏，
 * 那一屏的两档文案就永远只是文案，量不出谁在说话。
 *
 * ## 现在接的形状（三条各钉一条）
 *
 *  ① 装配点交的是 `isFavorite(comicId)`（真查库），**不是** `toggleFavorite`；
 *  ② 壳里按"查回来的那一档"显式选 add / remove，而不是把决定权交给 DAO 里那份内存缓存；
 *  ③ 写侧没接（`addFavorite == null`）时那枚钮**不摆**（评审 R6 `onRetest = {}` 那一族）。
 *
 * ## 为什么不是行为尺
 *
 * 真写库要 `DesktopDatabasePorts.bind`（落点、建表），而离屏 `runComposeUiTest` 渲的是屏本体、
 * 拿不到装配点那颗 lambda。屏上那四档文案 + 禁用态按不动由
 * `DesktopNewScreensSemanticsTest` 钉；**接线形状**由这里钉。
 */
class DesktopFavoriteWriteSideTest {

    private val shell = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktopLargeScreen.kt"))
    private val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
    private val detail = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("detail/DesktopComicDetailScreen.kt"))

    @Test
    fun 装配点查真值走库而不走内存缓存那一条() {
        val line = app.lines().firstOrNull { "isFavoriteOf = {" in it }
        assertTrue("`isFavoriteOf` 没在装配点接上（那屏上那枚钮就永远停在'正在读收藏…'）：", line != null)
        assertTrue(
            "查收藏走的不是 `favorite().isFavorite(comicId)`（真查库那一条）。实际：$line",
            "isFavoriteOf = { comicId -> favorite().isFavorite(comicId) }" in app,
        )
        // 反向：不许有人图省事把那颗带内存缓存的件接进来。
        val offenders = (shell.lines() + app.lines()).filter { "toggleFavorite(" in it }
        assertTrue(
            "桌面用上了 `FavoriteDao.toggleFavorite` —— 那颗拿 `_favoritesFlow.value` 判存在与否，" +
                "冷启动头几下会把'还没读回来'当成'已收过'，于是点「收藏」反而执行 DELETE：$offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun 写侧那一跳接的是add与remove两颗而不是整个DAO() {
        assertTrue("`addFavorite` 没接：", "addFavorite = { record -> favorite().addFavorite(record) }" in app)
        assertTrue("`removeFavorite` 没接：", "removeFavorite = { comicId -> favorite().removeFavorite(comicId) }" in app)
        // 与 `writeHistory` 同一条口径：只递动作，不把整颗 DAO 递进 UI（UI 不该能删全表）。
        assertFalse("把 `FavoriteDao` 整个递进了 UI：", "favorite()" in shell)
    }

    /** 壳里按查回来的那一档显式分叉（`wantAdd` 来自 `detailFavorited`，不是来自 DAO）。 */
    @Test
    fun 壳里按查回来那一档显式选add或remove() {
        val block = blockAfter("val wantAdd = detailFavorited != true", 24)
        assertTrue("add 那一路没接上：$block", "addFavorite.invoke(" in block)
        assertTrue("remove 那一路没接上：$block", "removeFavorite.invoke(r.comicId)" in block)
        // 写完必须回查：乐观反映没有真值校正就成了"屏上说已收藏而库里没这条"。
        assertTrue("写完没有回查真值（`favoriteTick++` 那一下）：$block", "favoriteTick++" in block)
    }

    /** 写失败要把屏上那一档交给回查、把成因念出来，而不是悄悄翻一档。 */
    @Test
    fun 写失败时念成因而不留下假的那一档() {
        val block = blockAfter("detailFavoriteBusy = false", 8)
        assertTrue("写失败没把成因上屏：$block", "收藏没写上：" in block)
        assertTrue("写失败那一路判的是 `isFailure`（而不是无条件翻档）：$block", "isFailure" in block)
    }

    /** 从 `anchor` 那一行往后取 `span` 行（锚必须整行精确匹配，免得咬到同名的别处）。 */
    private fun blockAfter(anchor: String, span: Int): String {
        val lines = DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopSourceOf("VeneraDesktopLargeScreen.kt"))
        val at = lines.indexOf(anchor)
        assertTrue("读不到锚「$anchor」（那一处被改了而这里没跟上 ⇒ 判据失效，不是通过）", at >= 0)
        return lines.drop(at).take(span).joinToString("\n")
    }

    /**
     * 三颗形参 + 三档禁用 都落在屏的签名上（不是靠默认值糊过去的）。
     *
     * 写侧缺席时那枚钮**不摆** —— 由 `DesktopNewScreensSemanticsTest.详情屏没有写口时那枚收藏钮根本不摆`
     * 在那一层量；这里只钉"缺席"这一判据在源码里是真的以 `null` 表达的。
     */
    @Test
    fun 没接写侧时那枚钮在源码层就是不摆而不是空回调() {
        assertTrue("`onToggleFavorite` 必须是可空且**无默认值**（带 `{}` 默认值 = 假开关）：", "onToggleFavorite: (() -> Unit)?," in detail)
        assertTrue("缺席时那枚钮仍然摆（`if (onToggleFavorite != null)` 那道闸门没了）：", "if (onToggleFavorite != null) {" in detail)
        assertTrue("还没读回来 / 正在写 那两档必须禁用：", "enabled = favorited != null && !favoriteBusy," in detail)
        assertEquals("那一枚钮的档名恰好四句：", 4, listOf("正在写收藏…", "正在读收藏…", "已收藏 · 点取消", "收藏").count { "$it" in detail })
    }

    /** 写进共享表的那一列口径：`sourceName` 吃 key，与桌面写历史、对岸 `toLegacyRecord` 同一条。 */
    @Test
    fun 收藏那一列写的是源key而不是显示名() {
        // 锚在构造那一行（`codeOccurrences` 量过：那颗构造在全文件恰好 1 处），
        // 不用 `comicId = r.comicId,` —— 那一串在取页表那一段也有一份，会咬错窗口。
        val block = blockAfter("com.venera.compose.data.db.FavoriteRecord(", 11)
        assertTrue("`sourceName` 没吃 r.sourceKey（那是往共享表里写显示名，与桌面写历史那条分叉）：$block",
            "sourceName = r.sourceKey," in block)
        assertTrue("标题没吃 r.title：$block", "title = r.title," in block)
        // 对岸同一条口径（改了对岸这边也该被读出来）。
        val counterpart = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("feature/HomeViewModel.kt"))
        assertTrue("对岸 `HomeViewModel.toLegacyRecord` 不再是 `sourceName = sourceKey`（它改了而这里没跟上）：",
            "sourceName = sourceKey," in counterpart)
    }

    /**
     * 清单 §3 那句「这条只被'照抄一个已验证的做法'支撑，没被实测支撑」里的那个等待写法，
     * 至今还是那一条（`first { isNotEmpty() }` + 超时回退），没被谁顺手简化成直接读 `.value`。
     */
    @Test
    fun 收藏读侧那条等待还在() {
        val block = app.lines().dropWhile { "readFavorites = {" !in it }.take(9).joinToString("\n")
        assertTrue("等一个非空的值那一句没了 ⇒ 会把'还没读回来'说成'没有收藏'：$block",
            "first { it.isNotEmpty() }" in block)
        assertTrue("超时回退没了 ⇒ 库确实空的时候白等：$block", "withTimeoutOrNull" in block)
    }
}
