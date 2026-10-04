package com.venera.compose.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `releases/latest` 的解析层（2026-10-04，应用内自更新批 1）。
 *
 * fixture 逐字取自本机对该发布通道的一次真实响应（见 `docs/rounds/app-self-update-2026-10-04.md` §0.4），
 * 包括那条真的 `digest` 与 `output-metadata.json` 那颗伴生产物 —— 不凭记忆造形状，
 * 因为这一层的价值恰恰在于"字段名与类型写对了没有"。
 *
 * 钉住的四件事：
 * - 类型门只放 APK 过去，伴生的 `output-metadata.json` 不许进候选；
 * - `digest` 形状不对一律退成 null（**这是"能不能走镜像"的唯一开关**，见 [DownloadCandidatesTest]）；
 * - 解不出可用版本必须交 null 让调用侧折成 `Failed`，不许折成"已是最新"；
 * - `assets` 缺失时版本号与日志照样成立（没有包 ≠ 没有发布）。
 */
class ReleaseFeedTest {

    private val arm64Url =
        "https://github.com/w13630039663-bit/venera-miuix/releases/download/v2.0.3-miuix/venera-miuix-2.0.3-arm64-v8a.apk"

    private val realShape = """
    {
      "tag_name": "v2.0.3-miuix",
      "name": "venera-miuix v2.0.3",
      "body": "# venera-miuix v2.0.3\n\nv2.0 系列的修正版。",
      "published_at": "2026-10-03T19:41:02Z",
      "assets": [
        {
          "name": "output-metadata.json",
          "label": "",
          "content_type": "application/octet-stream",
          "size": 1934,
          "digest": "sha256:1111111111111111111111111111111111111111111111111111111111111111",
          "browser_download_url": "https://github.com/w13630039663-bit/venera-miuix/releases/download/v2.0.3-miuix/output-metadata.json"
        },
        {
          "name": "venera-miuix-2.0.3-arm64-v8a.apk",
          "content_type": "application/vnd.android.package-archive",
          "size": 14430084,
          "digest": "sha256:E6F6ECB5CB4F1D8A12123D1B3B9E88C52317677405590A5F5927F063D01696B4",
          "browser_download_url": "$arm64Url"
        },
        {
          "name": "venera-miuix-2.0.3.apk",
          "content_type": "application/vnd.android.package-archive",
          "size": 14480157,
          "digest": "sha256:b20cd4cbf8107d531b84b62f1f800039ffc339690c405fadad770ad1c1fbdab1",
          "browser_download_url": "https://github.com/w13630039663-bit/venera-miuix/releases/download/v2.0.3-miuix/venera-miuix-2.0.3.apk"
        }
      ]
    }
    """.trimIndent()

    @Test
    fun `真实响应解出版本号日志与安装包清单`() {
        val release = parseLatestRelease(realShape)!!
        assertEquals("v2.0.3-miuix", release.tagName)
        assertEquals("2.0.3", release.version)
        assertTrue(release.changelog.startsWith("# venera-miuix v2.0.3"))
        assertEquals(listOf("venera-miuix-2.0.3-arm64-v8a.apk", "venera-miuix-2.0.3.apk"), release.apks.map { it.name })
        assertEquals(14430084L, release.apks.first().size)
        assertEquals(arm64Url, release.apks.first().url)
    }

    @Test
    fun `伴生的 json 产物被类型门挡在候选之外`() {
        // 判式是 content_type，不是"名字以 .apk 结尾"—— 后者会放过一份改名过的非包文件。
        val release = parseLatestRelease(realShape)!!
        assertTrue(release.apks.none { it.name == "output-metadata.json" })
    }

    @Test
    fun `digest 剥掉前缀并统一成小写`() {
        // 真实响应里那串是大写形状（GitHub 实际给小写，这里两种都要能吃）：
        // 比较大写在落盘散列那一侧一定是错，所以归一化必须在解析层做完。
        val release = parseLatestRelease(realShape)!!
        assertEquals("e6f6ecb5cb4f1d8a12123d1b3b9e88c52317677405590a5f5927f063d01696b4", release.apks.first().sha256)
    }

    @Test
    fun `digest 形状不对就当没拿到`() {
        assertNull(sha256Of(null))
        assertNull(sha256Of(""))
        assertNull(sha256Of("e6f6ecb5"))                                  // 太短
        assertNull(sha256Of("sha256:" + "z".repeat(64)))                   // 含非十六进制字符
        assertNull(sha256Of("md5:0123456789abcdef0123456789abcdef"))       // 换了算法，不是我们要比的那个
        assertEquals("a".repeat(64), sha256Of("sha256:" + "A".repeat(64)))
    }

    @Test
    fun `解不出可用版本一律交回 null 而不是空清单`() {
        // 这几条都会走到调用侧的 Failed 分支。若这里退成一个 tag 为空的 LatestRelease，
        // 表现就是"检查更新说已是最新版"—— 一次限流变成一次假成功。
        assertNull(parseLatestRelease("{}"))
        assertNull(parseLatestRelease("""{"tag_name": ""}"""))
        assertNull(parseLatestRelease("""{"tag_name": "   "}"""))
        assertNull(parseLatestRelease("not json at all"))
        assertNull(parseLatestRelease(""))
        assertNull(parseLatestRelease("""[1,2,3]"""))                      // 顶层不是对象
        assertNull(parseLatestRelease("""{"tag_name":"2.0.4"""))           // 半截
    }

    @Test
    fun `没有 assets 或全是非安装包时版本与日志照样成立`() {
        val noAssets = parseLatestRelease("""{"tag_name":"v2.1-miuix","body":"新东西"}""")!!
        assertEquals("2.1", noAssets.version)
        assertEquals(emptyList<String>(), noAssets.apks.map { it.name })

        val onlyOtherFiles = parseLatestRelease(
            """{"tag_name":"v2.1-miuix","assets":[{"name":"notes.txt","content_type":"text/plain","browser_download_url":"https://github.com/x/y"}]}""",
        )!!
        assertTrue(onlyOtherFiles.apks.isEmpty())
    }

    @Test
    fun `缺直链的那一条不进候选而不是带空地址进表`() {
        val release = parseLatestRelease(
            """
            {"tag_name":"v2.1-miuix","assets":[
              {"name":"a.apk","content_type":"application/vnd.android.package-archive","digest":"sha256:${"a".repeat(64)}","browser_download_url":"https://github.com/w13630039663-bit/venera-miuix/releases/download/v2.1-miuix/a.apk"},
              {"name":"b.apk","content_type":"application/vnd.android.package-archive","browser_download_url":"  "},
              {"name":"c.apk","content_type":"application/vnd.android.package-archive","browser_download_url":"https://github.com/w13630039663-bit/venera-miuix/releases/download/v2.1-miuix/c.apk"}
            ]}
            """.trimIndent(),
        )!!
        // b.apk 没有可用直链：留着它等于"下到一个空地址"，那要等到下载器里才炸，太晚了。
        assertEquals(listOf("a.apk", "c.apk"), release.apks.map { it.name })
        assertEquals("a".repeat(64), release.apks[0].sha256)
        assertEquals(0L, release.apks[0].size)          // size 缺失退 0，只影响文案不影响判据
        assertNull(release.apks[1].sha256)              // digest 缺失 → 这一条不许走镜像（下游由 DownloadCandidatesTest 钉）
    }
}
