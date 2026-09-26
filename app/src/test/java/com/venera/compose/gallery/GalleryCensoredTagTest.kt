package com.venera.compose.gallery

import com.venera.compose.gallery.data.DanbooruClient
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GalleryTagFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 站方「受管制标签」这条规则（wiki `help:censored_tags`，2026-09-26）。
 *
 * 为什么值得单测钉住：这条规则是**用户投诉的答案**（"我登录了、也验证邮箱了，为什么
 * Danbooru 还是没图"），而它的形状很容易被想歪 —— 当年就把成因错挂在"成人分级"上了。
 * 三个反直觉的点都要有断言守着：
 *
 * 1. 门槛是 **Gold**，所以 **Member 与未登录一样看不到**（"登录就好"是错觉）；
 * 2. 判据是**条目自己的标签**，不是分级、不是查询串（所以 `loli rating:s` 一样被挡）；
 * 3. 换标签词**绕不过**：`lolicon` 不在名单里（它是空标签），但搜出来的条目带着 `loli`。
 */
class GalleryCensoredTagTest {

    private fun post(tags: String) = GalleryPost(site = GallerySite.DANBOORU, id = 1, tags = tags)

    @Test
    fun `门槛是 Gold 所以 Member 与未登录一样看不到`() {
        assertFalse(DanbooruClient.canViewCensoredTags(null))
        assertFalse(DanbooruClient.canViewCensoredTags(0))
        // Restricted(10)：注册时走了 VPN / 代理的账号就是这一档（实测用户账号正是 10）。
        assertFalse(DanbooruClient.canViewCensoredTags(10))
        // Member(20)：免费注册的上限 —— 就停在这里，看不到。
        assertFalse(DanbooruClient.canViewCensoredTags(20))

        assertTrue(DanbooruClient.canViewCensoredTags(30))
        assertTrue(DanbooruClient.canViewCensoredTags(31))
        assertTrue(DanbooruClient.canViewCensoredTags(32))
    }

    @Test
    fun `判据是条目上的那两枚标签`() {
        assertTrue(DanbooruClient.hasCensoredTag(post("1girl loli solo")))
        assertTrue(DanbooruClient.hasCensoredTag(post("shota")))
        assertFalse(DanbooruClient.hasCensoredTag(post("1girl solo rating:e")))
        // 逐字比对：`loli_(genshin_impact)` 是另一枚标签，站方不管它。
        assertFalse(DanbooruClient.hasCensoredTag(post("loli_(genshin_impact) solo")))
    }

    @Test
    fun `查询里含管制标签要能在发车前看出来`() {
        val loli = listOf(GalleryTagFilter("loli"))
        assertEquals(listOf("loli"), GallerySearch.censoredTagsIn(GallerySite.DANBOORU, loli))
        // yande.re 没有这条规则，同一串标签在那边照搜。
        assertTrue(GallerySearch.censoredTagsIn(GallerySite.YANDERE, loli).isEmpty())
        // 普通标签不报警，否则提示就变成噪音。
        assertTrue(
            GallerySearch.censoredTagsIn(GallerySite.DANBOORU, listOf(GalleryTagFilter("1girl"))).isEmpty(),
        )
    }

    @Test
    fun `排除项不算命中`() {
        // `-loli` 正是被封之后该做的动作（把这类图排掉），对它报警等于劝人别做对的事。
        val excluded = listOf(GalleryTagFilter("loli", excluded = true))
        assertTrue(GallerySearch.censoredTagsIn(GallerySite.DANBOORU, excluded).isEmpty())
    }

    @Test
    fun `lolicon 不在名单里 但搜出来的条目带着 loli`() {
        // 这是"换词绕不过"的形状：`lolicon` 本身是空标签（实测 post_count = 0），
        // 靠站方标签蕴含命中的仍是带 `loli` 的那批 —— 所以查询层不预警，
        // 而落到条目上照样判得出来（由 noImageNotice 负责解释）。
        assertTrue(
            GallerySearch.censoredTagsIn(GallerySite.DANBOORU, listOf(GalleryTagFilter("lolicon"))).isEmpty(),
        )
        assertTrue(DanbooruClient.hasCensoredTag(post("lolicon loli 1girl")))
    }
}
