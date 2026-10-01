package com.venera.compose.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 头图落盘链的纯判据（[SettingsHeroImageStore] 的无 Android 依赖部分）。
 * 拷贝本身依赖 contentResolver，不在这里测（真机验收项）。
 */
class SettingsHeroImageStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ---- sniffExtension ----

    @Test
    fun sniffsJpegByFFD8FFMagic() {
        assertEquals("jpg", SettingsHeroImageStore.sniffExtension(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
    }

    @Test
    fun sniffsPngBySignature() {
        assertEquals(
            "png",
            SettingsHeroImageStore.sniffExtension(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            ),
        )
    }

    @Test
    fun sniffsWebpByRiffHeader() {
        assertEquals(
            "webp",
            SettingsHeroImageStore.sniffExtension(
                byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50)
            ),
        )
    }

    @Test
    fun sniffsGif() {
        assertEquals("gif", SettingsHeroImageStore.sniffExtension("GIF89a".toByteArray()))
    }

    @Test
    fun unknownHeaderFallsBackToJpg() {
        // 落盘扩展名只是第一道提示（Coil 对本地文件会再嗅一次），
        // 认不出时宁可普通（jpg）也不要存成解不开的 .bin。
        assertEquals("jpg", SettingsHeroImageStore.sniffExtension(ByteArray(16) { 0x11 }))
        assertEquals("jpg", SettingsHeroImageStore.sniffExtension(ByteArray(2)))
    }

    // ---- resolve ----

    @Test
    fun resolveReturnsNullForBlankPaths() {
        assertNull(SettingsHeroImageStore.resolve(null))
        assertNull(SettingsHeroImageStore.resolve(""))
        assertNull(SettingsHeroImageStore.resolve("   "))
    }

    @Test
    fun resolveReturnsNullWhenFileIsGone() {
        // prefs 里的路径只是一张收据：文件被清掉后必须回落「未设置」，
        // 绝不把收据当真（那会渲染出裂图）。
        assertNull(SettingsHeroImageStore.resolve(tmp.root.resolve("gone.png").absolutePath))
    }

    @Test
    fun resolveReturnsNullForEmptyFile() {
        val f = tmp.newFile("empty.jpg")
        assertNull(SettingsHeroImageStore.resolve(f.absolutePath))
    }

    @Test
    fun resolveReturnsTheFileWhenItExists() {
        val f = tmp.newFile("image.png")
        f.writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(f.absolutePath, SettingsHeroImageStore.resolve(f.absolutePath)?.absolutePath)
        assertNotNull(SettingsHeroImageStore.resolve(f.absolutePath))
    }

    // ---- 目录约定 ----

    @Test
    fun twoKindsDoNotShareOneSlot() {
        // 头图与名言小图是两个槽位：换头图绝不能顺手删掉名言小图。
        org.junit.Assert.assertNotEquals(
            SettingsHeroImageStore.KIND_HERO,
            SettingsHeroImageStore.KIND_QUOTE_AVATAR,
        )
        org.junit.Assert.assertEquals(
            "image",
            SettingsHeroImageStore.FILE_BASENAME,
        )
    }
}
