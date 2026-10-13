package com.venera.compose.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 源数据档命名规则的判据。
 *
 * 这条规则小到看着不值得测，但它**已经漂移过一次**，代价是一次真机导出整个失败：
 * 备份那一栏按「脚本文件名去扩展名」猜档名（`baozi.js` → `baozi.data`），
 * 而真实档名由**源声明的 key** 决定，两者经常不同 —— `komiic.js` 的 key 是 `Komiic`，
 * `copy_manga.js` 与 `copy_manga_multi_accounts.js` 共用 `copy_manga`。
 * 猜错之后档不存在，而那条路径还会回落成一个同样不存在的 `File`，
 * 调用方一 `readText()` 就抛 `ENOENT`，把整包导出一起带崩。
 *
 * 所以这里钉的是"档名只由 key 决定、与脚本文件名无关"，以及两个反例。
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
        // 所以它们的数据档**就是同一份** copy_manga.data（运行期两者也互斥）。
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
