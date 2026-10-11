package com.venera.desktop.gallery.ui

import com.venera.desktop.gallery.data.DesktopGalleryArtistDirectory
import com.venera.desktop.gallery.data.DesktopGalleryHandle
import com.venera.desktop.platform.FakePaths
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 桌面第四十二批 批 42 · pixiv 那两枚契约成员不再交抛桩。
 *
 * ## 量过的可达性（2026-10-08，本机，不是推断）
 *
 * - `https://www.pixiv.net/ajax/user/3017456` —— **走代理 200，裸连也 200**（与 saucenao.com 那族不同：
 *   那一站两条出口都 403，所以批 35 只能把"本机过不了盾"写进读数；这一站没有那一层）；
 * - `PixivClient` 的工厂只收 `HttpEngine`（注释原话："只收 HttpEngine，不收 Context —— 收口前这里是
 *   `getInstance(context)`"），而它在 `gallery/data/` 里 ⇒ **本来就在桌面编译面上**；
 * - 用户号的取法在共享的 `gallery/domain/GalleryArtistLinks.pixivUserId`（认 `/users/N`、`/en/users/N`、
 *   `member.php?id=N`，明确不要 `/artworks/N`）⇒ 桌面**不许重述**这一条（判据 ④）。
 *
 * ## 为什么这一批不是"顺手多接一站"
 *
 * 屏上那句读数今天写着「够得着的路只有 fanbox 与 Mastodon 两条，pixiv 那一路桌面还没接」——
 * 那是一句**拍板的缺席**（`PixivNotWiredOnDesktop` 的注释自己写着"不是忘了接"）。
 * 缺席可以拍板，**说错的话不行**：这一批把两枚成员转交真 client，并把链里那一档接上（次序照 Android），
 * 于是那句读数要么改成真话，要么撤掉 —— 判据 ③ 钉的就是"不许留着"。
 */
class DesktopPixivDirectoryTest {

    private val directory = DesktopGalleryArtistDirectory(DesktopGalleryHandle(FakePaths(Files.createTempDirectory("pixiv42-").toFile()), engine))

    @Before
    fun setUp() {
        engine.reset()
    }

    /** ① 头像那一枚：转交真 client，端点逐字是 `/ajax/user/{用户号}`，答复里的两档由共享件挑。 */
    @Test
    fun 头像那一枚转交真client而端点逐字对得上() {
        engine.on(
            PIXIV_USER_URL,
            body = """{"error":false,"body":{"image":"$PIXIV_SMALL","imageBig":"$PIXIV_BIG"}}""",
        )
        val url = runBlocking { directory.pixivAvatarUrl(3017456L) }.getOrThrow()
        assertEquals("两档都给出时取的是静态大档（GalleryArtistUrls.avatarUrl 的判据）", PIXIV_BIG, url)
        assertEquals("请求打出去的那枚 URL 不对（用户号没进路径？）", listOf(PIXIV_USER_URL), engine.requests)
    }

    /** ② 作品号反查作者那一枚：三根字段都要原样交回，`error=true` 不许读成"没有作者"。 */
    @Test
    fun 作品号反查作者那一枚也转交真client() {
        engine.on(
            PIXIV_ILLUST_URL,
            body = """{"error":false,"body":{"userId":"3017456","userAccount":"take","userName":"TAKE"}}""",
        )
        val author = runBlocking { directory.pixivArtworkAuthor(999L) }.getOrThrow()
        assertEquals(3017456L, author.userId)
        assertEquals("take", author.account)
        assertEquals("TAKE", author.name)
        assertEquals(listOf(PIXIV_ILLUST_URL), engine.requests)
        // 站方明说没这个人 ⇒ 是**失败**，不是"作者为空"（Android 同一条判据）
        engine.on(PIXIV_ILLUST_URL, body = """{"error":true,"message":"pixiv 回 error=true"}""")
        val failure = runBlocking { directory.pixivArtworkAuthor(999L) }.exceptionOrNull()
        assertNotNull("error=true 被吞成了成功", failure)
        assertTrue("那句失败没点名 error=true：${failure!!.message}", failure.message.orEmpty().contains("error=true"))
    }

    /** ③ 抛桩那颗类与屏上那句"还没接"都不许再留在桌面源码里（机械扫整目录，不列名单）。 */
    @Test
    fun 抛桩那颗类与那句还没接都不许留着() {
        val sources = DesktopSourceTree.desktopMainSources().filter { it.extension == "kt" }
        assertTrue("对照组失效：一颗桌面源码都没扫到", sources.isNotEmpty())
        val stub = sources.filter { DesktopSourceTree.codeText(it).contains("PixivNotWiredOnDesktop") }
        assertTrue("抛桩那颗类还在：${stub.joinToString(" · ") { it.name }}", stub.isEmpty())
        val stale = sources.filter { it.readText().contains("pixiv 那一路桌面还没接") || it.readText().contains("桌面刻意没接 pixiv") }
        assertTrue("屏上或注释里还念着「还没接」：${stale.joinToString(" · ") { it.name }}", stale.isEmpty())
    }

    /** ④ 用户号的取法转交共享那颗，桌面不许自己再写一遍正则（两份判据会漂，漂的形状是"少一位画师"）。 */
    @Test
    fun 用户号那一跳转交共享件而桌面不重述() {
        val policy = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopArtistsPolicy.kt"))
        assertTrue("链里没走 GalleryArtistLinks.pixivUserId（桌面自己写了一份？）", policy.contains("GalleryArtistLinks.pixivUserId"))
        assertEquals("pixiv 的用户号规则被重述了（正则或 member.php 字面）", 0, policy.split("member.php").size - 1)
        // 对照组：那一档确实在链里，而不是只在注释里
        assertTrue("对照组失效：链里根本没有 pixiv 那一枚参数", policy.contains("pixivAvatar: suspend (Long) -> Result<String?>"))
    }

    private companion object {
        /**
         * 三颗 client 都是**进程内单例**（第一次装配就把 engine 存进 INSTANCE）⇒ 整类只能共用一颗引擎，
         * 每条用例 reset 路由表。各条用例各 new 一颗的话，第二起请求会打到第一条那颗已被缓存的引擎上
         * （症状是「没有为这枚 URL 登记假响应」，而真因与业务无关 —— 这条口径写在 FakeHttpEngine 的注释里）。
         */
        val engine = FakeHttpEngine()
        const val PIXIV_BIG = "https://i.pximg.net/img-master/user/3017456_170.jpg"
        const val PIXIV_SMALL = "https://i.pximg.net/img-master/user/3017456_50.jpg"
        const val PIXIV_USER_URL = "https://www.pixiv.net/ajax/user/3017456"
        const val PIXIV_ILLUST_URL = "https://www.pixiv.net/ajax/illust/999"
    }
}
