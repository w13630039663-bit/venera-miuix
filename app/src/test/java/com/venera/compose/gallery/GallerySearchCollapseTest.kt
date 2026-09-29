package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GallerySearchCollapse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「键盘收起就收成一条」那把判据。
 *
 * 2026-09-29 真机缺陷：滚到深处点🔍再点「排行」，菜单闪一下就没 —— 因为弹层抢焦点导致的
 * 键盘下落被当成了"用户要收键盘看图"，把搜索区连锚点一起收走了。
 */
class GallerySearchCollapseTest {

    @Test
    fun `用户自己收键盘 该收成一条`() {
        assertTrue(
            GallerySearchCollapse.shouldCollapseOnImeHidden(
                imeWasVisible = true,
                popupOpen = false,
                reverseOpen = false,
            ),
        )
    }

    @Test
    fun `没见过键盘起来 不收 否则刚展开就被自己关掉`() {
        assertFalse(
            GallerySearchCollapse.shouldCollapseOnImeHidden(
                imeWasVisible = false,
                popupOpen = false,
                reverseOpen = false,
            ),
        )
    }

    @Test
    fun `排行菜单开着 那次下落是弹层抢焦点 不算用户要收`() {
        assertFalse(
            GallerySearchCollapse.shouldCollapseOnImeHidden(
                imeWasVisible = true,
                popupOpen = true,
                reverseOpen = false,
            ),
        )
    }

    @Test
    fun `反搜层开着 不收`() {
        assertFalse(
            GallerySearchCollapse.shouldCollapseOnImeHidden(
                imeWasVisible = true,
                popupOpen = false,
                reverseOpen = true,
            ),
        )
    }
}
