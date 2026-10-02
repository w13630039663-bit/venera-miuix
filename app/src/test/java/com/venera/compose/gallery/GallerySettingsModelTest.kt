package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GallerySaveNaming
import com.venera.compose.gallery.domain.GallerySettingsModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 画廊设置那三个新开关的**判据**（列数 / 墙上取哪一档 / 保存文件名）。
 *
 * 设置页本身是 composable，本项目单测没有 Robolectric 摸不到；所以把会算错的部分
 * 全抽进 [GallerySettingsModel]，在这里钉住。
 */
class GallerySettingsModelTest {

    private fun post(
        site: GallerySite = GallerySite.YANDERE,
        id: Long = 1234567L,
        author: String = "",
        previewUrl: String = "https://prv/preview_1.jpg",
        largeUrl: String = "https://prv/sample_1.jpg",
        fileUrl: String = "https://prv/0cef369524878750fe49c18a8a5d4de0.png",
        fileExt: String = "png",
    ) = GalleryPost(
        site = site,
        id = id,
        author = author,
        previewUrl = previewUrl,
        largeUrl = largeUrl,
        fileUrl = fileUrl,
        fileExt = fileExt,
    )

    @Test
    fun `AUTO 透传自适应列数 不再自己拍两档`() {
        // 2026-10-02 改：AUTO 原来是这里写死的 `if (wide) 3 else 2`。侧栏落地后宽窗仍只排 3 列
        // ⇒ 一格近 480dp（用户报「图片卡片过大」）。列数改由调用点按窗口宽算好传进来
        //（判据在 WideScreenPolicy.imageWallColumnCount），domain 只管"AUTO 透传 / 手动档覆盖"。
        assertEquals(6, GallerySettingsModel.gridColumns(GalleryColumnMode.AUTO, adaptiveColumns = 6))
        assertEquals(2, GallerySettingsModel.gridColumns(GalleryColumnMode.AUTO, adaptiveColumns = 2))
    }

    @Test
    fun `手动选列数就不再看宽度`() {
        // 用户在平板上选了 2 列，自适应那条判据必须让位 —— 否则这一档是假开关。
        // 传 8 当诱饵：手动档必须无视它。
        assertEquals(2, GallerySettingsModel.gridColumns(GalleryColumnMode.TWO, adaptiveColumns = 8))
        assertEquals(3, GallerySettingsModel.gridColumns(GalleryColumnMode.THREE, adaptiveColumns = 8))
    }

    @Test
    fun `墙上默认吃 preview 档`() {
        assertEquals(
            "https://prv/preview_1.jpg",
            GallerySettingsModel.wallUrl(post(), GalleryPreviewQuality.PREVIEW),
        )
    }

    @Test
    fun `清晰档缺项时退回 preview 不给空地址`() {
        // 空地址进 AsyncImage 就是一格永远摆不出来的灰卡。
        val noLarge = post(largeUrl = "")
        assertEquals(
            "https://prv/preview_1.jpg",
            GallerySettingsModel.wallUrl(noLarge, GalleryPreviewQuality.LARGE),
        )
    }

    @Test
    fun `视频条目在墙上永远吃静帧 两档都不例外`() {
        // 站方对视频条目**没有**更小的转码档，而 Gelbooru 的 `sample_url` 给空串、翻译时兜底成了
        // 原片 mp4（实测 16~26 MB）。墙上按"清晰档"直取 largeUrl 就是把整片 mp4 交给 Coil：
        // 先完整吸进 Java 堆再解码失败，一屏视频卡就是几百 MB —— 2026-09-29 真机闪退的第二条成因
        // （第一条是取流那份三份拷贝，见 ImagePipelinePolicy.needsBytePipeline）。
        val mp4 = post(
            fileExt = "mp4",
            largeUrl = "https://prv/original_1.mp4",
            fileUrl = "https://prv/original_1.mp4",
        )
        assertEquals(
            "https://prv/preview_1.jpg",
            GallerySettingsModel.wallUrl(mp4, GalleryPreviewQuality.LARGE),
        )
        assertEquals(
            "https://prv/preview_1.jpg",
            GallerySettingsModel.wallUrl(mp4, GalleryPreviewQuality.PREVIEW),
        )
    }

    @Test
    fun `站方给了 jpg 静帧的视频条目墙上保留那张静帧`() {
        // yande.re 那一路 large 档本来就是 jpg 静帧、与原片不同址 —— 比 350px 缩略图清楚得多，
        // 不该被上一条一起打回 preview。判据与 videoPosterUrl 同一把。
        val webm = post(
            fileExt = "webm",
            largeUrl = "https://prv/animated_frame.jpg",
            fileUrl = "https://prv/original_1.webm",
        )
        assertEquals(
            "https://prv/animated_frame.jpg",
            GallerySettingsModel.wallUrl(webm, GalleryPreviewQuality.LARGE),
        )
    }

    @Test
    fun `命名 站名键在前 与最初的写法逐字一致`() {
        assertEquals(
            "yandere-1234567.png",
            GallerySettingsModel.fileName(post(), GallerySaveNaming.SITE_ID, null),
        )
    }

    @Test
    fun `命名 原图文件名取地址最后一段 不重复拼扩展名`() {
        assertEquals(
            "0cef369524878750fe49c18a8a5d4de0.png",
            GallerySettingsModel.fileName(post(), GallerySaveNaming.ORIGINAL, null),
        )
    }

    @Test
    fun `命名 地址里没有文件名时退回站名键 不摆半个名字`() {
        val noName = post(fileUrl = "https://cdn/abcdef")
        assertEquals(
            "yandere-1234567.png",
            GallerySettingsModel.fileName(noName, GallerySaveNaming.ORIGINAL, null),
        )
    }

    @Test
    fun `命名 画师名里的非法字符换成下划线`() {
        // 这一串会直接进 File(dir, name) 与 MediaStore 的 DISPLAY_NAME，留 `/` 就变成建子目录。
        assertEquals(
            "a_b_c_1234567.png",
            GallerySettingsModel.fileName(
                post(author = "a/b c"),
                GallerySaveNaming.ARTIST_ID,
                null,
            ),
        )
    }

    @Test
    fun `命名 没有画师时不编一个名字`() {
        assertEquals(
            "yandere-1234567.png",
            GallerySettingsModel.fileName(post(author = ""), GallerySaveNaming.ARTIST_ID, null),
        )
    }

    @Test
    fun `命名 时间戳由调用方给 我们只负责拼`() {
        assertEquals(
            "20260929-113045.png",
            GallerySettingsModel.fileName(post(), GallerySaveNaming.TIMESTAMP, "20260929-113045"),
        )
    }

    @Test
    fun `扩展名拿不到时落 bin 而不是猜一个 jpg`() {
        // 猜错的名字会让相册里出现一张"叫 jpg、其实是 mp4"的东西（MIME 也照这一档声明）。
        assertEquals(
            "yandere-1234567.bin",
            GallerySettingsModel.fileName(post(fileExt = ""), GallerySaveNaming.SITE_ID, null),
        )
    }
}
