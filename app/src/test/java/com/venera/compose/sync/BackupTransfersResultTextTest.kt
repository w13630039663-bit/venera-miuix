package com.venera.compose.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入结果文案的判据。
 *
 * 看起来只是拼字符串，但它是**唯一**能把「少导入了几条」说出口的地方：不显示，
 * 用户只会觉得备份是坏的或者导错了。所以这里守三件事：来源说得出、零值不占版面、
 * 跳过必须出现。
 */
class BackupTransfersResultTextTest {

    private fun summary(
        origin: String = BackupOrigin.LOCAL,
        favorite: Int = 12,
        history: Int = 34,
        folders: Int = 0,
        stats: Int = 0,
        guards: Int = 0,
        guardSkipped: Int = 0,
        foreignSkipped: Int = 0,
        imageFavorites: Int = 0,
        galleryFavorites: Int = 0,
        galleryFollows: Int = 0,
        sourceAccounts: Int = 0,
        sourceAccountsSkipped: Int = 0,
    ) = BackupSummary(
        historyCount = history,
        favoriteCount = favorite,
        statsCount = stats,
        guardRulesCount = guards,
        guardRulesSkipped = guardSkipped,
        timestamp = 0L,
        origin = origin,
        folderCount = folders,
        foreignSkipped = foreignSkipped,
        imageFavoriteCount = imageFavorites,
        galleryFavoriteCount = galleryFavorites,
        galleryFollowCount = galleryFollows,
        sourceAccountCount = sourceAccounts,
        sourceAccountSkipped = sourceAccountsSkipped,
    )

    @Test
    fun `本应用备份说恢复完成，外部归档要说清来源`() {
        assertEquals("恢复完成：历史 34 条，收藏 12 部", BackupTransfers.describeResult(summary()))

        val pica = BackupTransfers.describeResult(summary(origin = BackupOrigin.PICA_COMIC))
        assertTrue(pica, pica.startsWith("已从 PicaComic 备份 导入"))
        assertTrue(pica, pica.contains("历史 34 条"))
    }

    @Test
    fun `零值不占版面`() {
        val text = BackupTransfers.describeResult(summary())
        assertFalse(text, text.contains("统计"))
        assertFalse(text, text.contains("屏蔽规则"))
        assertFalse(text, text.contains("收藏夹"))
        assertFalse(text, text.contains("插图收藏"))
        assertFalse(text, text.contains("画廊收藏"))
        assertFalse(text, text.contains("关注画师"))
        assertFalse(text, text.contains("漫画源账号"))
    }

    @Test
    fun `非零的计数一个都不能漏`() {
        val text = BackupTransfers.describeResult(
            summary(
                folders = 3, stats = 5, guards = 7, guardSkipped = 2,
                imageFavorites = 4, galleryFavorites = 6, galleryFollows = 2,
            )
        )
        assertTrue(text, text.contains("收藏夹 3 个"))
        assertTrue(text, text.contains("统计 5 条"))
        assertTrue(text, text.contains("屏蔽规则 7 条"))
        assertTrue(text, text.contains("插图收藏 4 张"))
        assertTrue(text, text.contains("画廊收藏 6 条"))
        assertTrue(text, text.contains("关注画师 2 位"))
        assertTrue(text, text.contains("另有 2 条屏蔽规则写法有误"))
    }

    @Test
    fun `插图收藏要顺带说明图本身不带`() {
        // 恢复出来的那批是按 image_url 现加载的，不写明就会让人以为包里有图、
        // 离线打不开是备份坏了。
        val text = BackupTransfers.describeResult(summary(imageFavorites = 4))
        assertTrue(text, text.contains("按地址重新加载"))
    }

    @Test
    fun `来源认不出的条数必须在文案里说出来`() {
        val text = BackupTransfers.describeResult(
            summary(origin = BackupOrigin.VENERA, foreignSkipped = 9)
        )
        assertTrue(text, text.contains("另有 9 条来源未知"))
    }

    @Test
    fun `两种跳过同时发生时不互相顶掉`() {
        val text = BackupTransfers.describeResult(summary(guardSkipped = 2, foreignSkipped = 3))
        assertTrue(text, text.contains("2 条屏蔽规则写法有误"))
        assertTrue(text, text.contains("3 条来源未知"))
    }

    @Test
    fun `恢复了几个源的账号必须报出来`() {
        // 不说出来，用户没法判断这份包到底带没带登录态 ——
        // 这一栏默认不进包，所以"0 个源"是常态而非异常，更不能编一句"已恢复"。
        val text = BackupTransfers.describeResult(summary(sourceAccounts = 4))
        assertTrue(text, text.contains("漫画源账号 4 个"))
        // 顺带说明前提：本机没装的源恢复不了，用户才不会以为备份坏了。
        assertTrue(text, text.contains("本机已装该源才恢复"))
    }

    @Test
    fun `因本机未装该源而跳过的账号要说得出`() {
        val text = BackupTransfers.describeResult(summary(sourceAccounts = 2, sourceAccountsSkipped = 3))
        assertTrue(text, text.contains("漫画源账号 2 个"))
        assertTrue(text, text.contains("另有 3 个源的账号因本机未安装该源而没有恢复"))
    }

    @Test
    fun `账号跳过与屏蔽规则跳过互不顶掉`() {
        val text = BackupTransfers.describeResult(
            summary(guardSkipped = 1, sourceAccountsSkipped = 2)
        )
        assertTrue(text, text.contains("1 条屏蔽规则写法有误"))
        assertTrue(text, text.contains("2 个源的账号"))
    }
}
