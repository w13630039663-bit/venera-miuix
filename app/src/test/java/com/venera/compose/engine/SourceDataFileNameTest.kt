package com.venera.compose.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 源数据档命名规则：档名只由源声明的 key 决定，与脚本文件名无关。
 * `komiic.js` 的 key 是 `Komiic`；`copy_manga.js` 与
 * `copy_manga_multi_accounts.js` 共用 `copy_manga`。
 * 按文件名猜档名会回落到不存在的 `File`，`readText()` 抛 `ENOENT`。
 */
class SourceDataFileNameTest {

    @Test
    fun `档名由 key 决定`() {
        assertEquals("baozi.data", sourceDataFileName("baozi"))
        assertEquals("copy_manga.data", sourceDataFileName("copy_manga"))
    }

    @Test
    fun `key 与脚本文件名不同时，档名跟着 key 走 —— komiic 的 key 是大写 K`() {
        // 源脚本是 komiic.js，而它声明 key = "Komiic"。
        // 按文件名猜会去找 komiic.data，那份档根本不存在。
        assertEquals("Komiic.data", sourceDataFileName("Komiic"))
    }

    @Test
    fun `两个脚本共用一个 key 时共用同一份档 —— 这是源协议允许的`() {
        // copy_manga.js 与 copy_manga_multi_accounts.js 声明同一个 key，
        // 所以它们的数据档就是同一份 copy_manga.data（运行期两者也互斥）。
        assertEquals(sourceDataFileName("copy_manga"), sourceDataFileName("copy_manga"))
        assertEquals("copy_manga.data", sourceDataFileName("copy_manga"))
    }

    @Test
    fun `key 里的路径分隔符被剥掉，不会写到数据目录之外`() {
        // key 来自源脚本（第三方内容），不能让它带着 ../ 或斜杠指到目录外面去。
        assertEquals("evil.data", sourceDataFileName("../../evil"))
        assertEquals("evil.data", sourceDataFileName("a/b\\evil"))
    }
}
