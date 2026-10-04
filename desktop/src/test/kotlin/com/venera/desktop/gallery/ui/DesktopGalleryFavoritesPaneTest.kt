package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.toPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收藏域的投影判据 —— 2026-10-04 第二批把收藏域从坦白页转成真页面时落的。
 *
 * ## 为什么这一层值得单测（而不是让 UI 自己算）
 *
 * 那一屏有**三种空态**，而它们各自要念的话完全不同：
 * 「一条收藏都没有」/「有收藏但一张都摆不上屏」/（正常有墙）。
 * 分支写反的后果不是难看，是**误导**：把"有 8 条记录但站方键认不出来"落进
 * "你还没有收藏过"那一支，用户会以为自己的收藏丢了。
 *
 * 而这三条判据是纯的（不碰 IO、不碰组合），所以能上单测 ——
 * 拿不到设备时，这是唯一能跑的证据。
 */
class DesktopGalleryFavoritesPaneTest {

    /**
     * ① 站方键认不出来的条目**点名到键**，且**一条都不许被当成某一站**。
     *
     * 那是本屏最贵的一条：`GalleryFavorite.site` 认不出 routeKey 时给 `null`，
     * 而 `toPost` 的签名要一个非空站 —— 于是这批条目**画不了**。
     * 兜底成"当成某一站"会把 A 站的收藏摆进 B 站的坐标系（那是个串图）。
     */
    @Test
    fun `站方键认不出来的条目被单独点名 不进墙`() {
        val projection = projectFavorites(
            listOf(
                favorite(GallerySite.YANDERE, 1L),
                favorite(GallerySite.SAFEBOORU, 2L),
                favorite(siteKey = "不存在的站", id = 9L),
            ),
        )
        assertEquals(
            "两条认得出的应当都上墙",
            listOf(1L, 2L),
            projection.posts.map { it.id },
        )
        assertEquals(
            "认不出的那一条必须被点名到键 —— 只给条数的话用户无处可查",
            listOf("不存在的站"),
            projection.unrecognizedSites,
        )
        assertEquals("总数是存档条数（3），不是上屏条数（2）", 3, projection.total)
    }

    /**
     * ② 同一个坏键出现多次时：**键念一次，条数照实念**。
     *
     * ## 这一条抓到过一个真 bug（2026-10-04 第二批首跑）
     *
     * 第一版只有 `unrecognizedSites` 一枚字段且是去重的，而屏上计数那一项直接取了它的 `.size`。
     * 于是存档里 3 条同坏键的记录，屏上念的是「站方键认不出 **1** 条」。
     * 用户实际丢掉 3 条却被告知 1 条 —— 他回头数存档时对不上，只会更信不过这面墙。
     * 这正是本仓一号原则（数据缺失/少报要如实说）在本屏的落点，所以修法是**加一枚字段**
     * （`unrecognizedCount`），不是把去重撤掉：键仍要去重，否则那句话会重复 N 遍。
     *
     * 两枚各有各的用途，少任何一枚都会坏：键给用户去 `gallery_favorites.json` 里定位，
     * 条数给他一个"丢了几条"的实数。
     */
    @Test
    fun `同一个坏键只念一次而条数照实念`() {
        val projection = projectFavorites(
            listOf(
                favorite(siteKey = "坏键", id = 1L),
                favorite(siteKey = "坏键", id = 2L),
                favorite(siteKey = "坏键", id = 3L),
            ),
        )
        assertEquals("键只念一次", listOf("坏键"), projection.unrecognizedSites)
        assertEquals("条数不去重 —— 屏上那一句念的是丢了几条记录", 3, projection.unrecognizedCount)
        assertEquals(0, projection.posts.size)
        assertEquals(
            "计数那一位按**条数**念（3 条），不是按去重后的键数（1 个）",
            "站方键认不出 3 条",
            projection.droppedLabel.substringAfter("· "),
        )
    }

    /**
     * ③ 不可摆上屏的（[com.venera.compose.gallery.domain.GalleryMerge.isDisplayable] 为假）单独计数。
     *
     * 那一档与"图加载失败"是两件事：前者是**存档里那条记录**没有可摆的图，
     * 后者是这一屏取数时图没下下来。混起来用户会去重下图片，而那不会有用。
     */
    @Test
    fun `没有可摆缩略图的条目被计数 不上墙`() {
        val broken = GalleryFavorite(
            siteKey = GallerySite.YANDERE.routeKey,
            id = 7L,
            fileExt = "jpg",
            // previewUrl 缺省 = 空串 ⇒ `isDisplayable` 为假（它要求扩展名可摆**且**缩略图非空）。
            previewUrl = "",
        )
        val projection = projectFavorites(listOf(broken, favorite(GallerySite.YANDERE, 8L)))
        assertEquals(1, projection.droppedUnusable)
        assertEquals(listOf(8L), projection.posts.map { it.id })
        assertTrue(
            "计数读数里必须出现「不可摆 1 条」",
            projection.droppedLabel.contains("不可摆 1 条"),
        )
    }

    /**
     * ④ 三种空态各归各：判据是**存档条数**与**上屏条数**这一对，不是"墙上有没有东西"。
     *
     * 这条不写成"屏上念哪句话"（那是组合期的事，判据层碰不到），
     * 而是钉住"哪一种组合对应哪一种空"的那对数 —— 界面上的分支只要读这两个数就必然对。
     */
    @Test
    fun `空态归属由存档条数与上屏条数这一对决定`() {
        val empty = projectFavorites(emptyList())
        assertTrue("一条收藏都没有 ⇒ 存档与上屏都是 0", empty.total == 0 && empty.posts.isEmpty())

        val allUnrenderable = projectFavorites(
            listOf(favorite(siteKey = "坏键", id = 1L), favorite(siteKey = "坏键2", id = 2L)),
        )
        assertTrue(
            "有收藏但一张都摆不出来 ⇒ 存档非 0 而上屏 0（这与上面那一种必须能分开）",
            allUnrenderable.total == 2 && allUnrenderable.posts.isEmpty(),
        )

        val normal = projectFavorites(listOf(favorite(GallerySite.YANDERE, 1L)))
        assertTrue("正常档 ⇒ 存档与上屏都非 0", normal.total == 1 && normal.posts.size == 1)
    }

    /**
     * ⑤ `toPost` 那一步不许丢字段：存档里存了什么，墙上就还看得见什么。
     *
     * 挑的是**地址与尺寸**三枚 —— 网格在**摆放前**要知道比例与图位（`GalleryFavorite` 的类注释
     * 写明尺寸一并存就是为了这个），少搬一枚就表现为"卡片比例随内容跳动"。
     */
    @Test
    fun `toPost 不丢地址与尺寸`() {
        val stored = GalleryFavorite(
            siteKey = GallerySite.YANDERE.routeKey,
            id = 42L,
            previewUrl = "https://example.invalid/preview.png",
            previewWidth = 300,
            previewHeight = 212,
            largeUrl = "https://example.invalid/large.png",
            width = 1500,
            height = 2000,
            tags = "a b c",
            author = "someone",
        )
        val post: GalleryPost = stored.toPost(GallerySite.YANDERE)
        assertEquals(stored.previewUrl, post.previewUrl)
        assertEquals(stored.previewWidth, post.previewWidth)
        assertEquals(stored.previewHeight, post.previewHeight)
        assertEquals(stored.largeUrl, post.largeUrl)
        assertEquals(stored.width, post.width)
        assertEquals(stored.height, post.height)
        assertEquals("标签串要能还原成词表 —— 卡片标题取的是首个标签", listOf("a", "b", "c"), post.tagList)
        assertEquals("作者名要能原样取回", "someone", post.author)
    }

    /**
     * 造一条收藏记录。
     *
     * ## 为什么 [id] 排在 [siteKey] 前面（而不是按"字母/语义"排）
     *
     * 这一组调用点里绝大多数是 `favorite(GallerySite.X, 42L)` 这种**按位置**传参，
     * 而只有要造假键的那几条才写 `siteKey = "坏键"`。参数序若把 `siteKey` 放在 `id` 前面，
     * 位置传参就会把 `Long` 喂给 `String` 形参 —— 编译期报错（好），
     * 但错误信息指向的是"测试数据写错了"，真正的原因（helper 序）不在现场。
     *
     * 所以序按**调用点的默认形状**排（位置传参占多数），把罕用的那个推到后面并要求具名。
     */
    private fun favorite(
        site: GallerySite = GallerySite.YANDERE,
        id: Long,
        siteKey: String = site.routeKey,
    ): GalleryFavorite = GalleryFavorite(
        siteKey = siteKey,
        id = id,
        fileExt = "png",
        previewUrl = "https://example.invalid/p$id.png",
        previewWidth = 300,
        previewHeight = 400,
    )
}
