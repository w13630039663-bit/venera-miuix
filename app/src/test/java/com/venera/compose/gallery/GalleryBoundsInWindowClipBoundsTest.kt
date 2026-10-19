package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 B 判据③：**`app/src/main` 里不许出现无参 `boundsInWindow()`**（负向扫描一次列全违例）。
 *
 * ## 为什么这条要有尺而不是修完就算
 *
 * Compose 1.12 的 `boundsInWindow(clipBounds: Boolean = true)` **默认把矩形裁到窗口内**
 * （源码 KDoc：完全裁掉时直接给 `Rect.Zero`）。pixiv 的页是竖长条（真机读数 2400×4000，
 * 窗口那格只有 [0,0][1080,979]），拿默认值当"位置"用就是 §3 那块地基坑：
 * - 进场落点是一幅被裁短的真图矩形 ⇒ 交棒那一帧高度对不上（"错位/压一下"）；
 * - 被裁剩 16px/2px 的卡照样入表（10-19 晚 FlyProbe 活体读数）。
 *
 * 2026-10-19 批 B §3 把接线全部改成显式：定位吃 `clipBounds = false`，截像素与 chrome 相交吃
 * `clipBounds = true`。这一颗尺钉的是**形状**：谁再写一次无参调用，编译器不响、屏上难查，
 * 所以只能在源码层面拦。违例清单**只许缩短、不许变长**（新加一处 = 红）。
 *
 * 注释行除外：KDoc 里解释这个坑必然要提到 `boundsInWindow()`，那不算调用。
 */
class GalleryBoundsInWindowClipBoundsTest {

    private fun isCode(line: String): Boolean {
        val t = line.trim()
        return !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*")
    }

    @Test
    fun `main 里每一处 boundsInWindow 调用都显式写 clipBounds`() {
        val offenders = RepoSources.allMainLines().filter { (_, _, line) ->
            isCode(line) && Regex("""boundsInWindow\(\s*\)""").containsMatchIn(line)
        }
        assertTrue(
            "无参 boundsInWindow() 默认裁到窗口内 ⇒ 裁剩的矩形被当位置用了（§3 地基坑）。" +
                "定位写 clipBounds = false，截像素/相交写 clipBounds = true，违例：\n" +
                offenders.joinToString("\n") { (rel, no, l) -> "$rel:$no: ${l.trim()}" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `落点表的登记侧吃两把矩形（没裁过定位 + 裁过判锚）`() {
        // 形状尺的配套：登记必须**同时**拿到两把 —— 只拿裁过的会重蹈 §3，只拿没裁的没法算可见比。
        // 扫调用点实参里出现 clipBounds = false 那把的传法（full 变量名进 noteCardBounds 第一实参位）。
        val notes = RepoSources.linesOf("com/venera/compose/gallery/ui").filter { (_, _, line) ->
            isCode(line) && line.contains("GalleryFlyIn.noteCardBounds(")
        }
        assertTrue("登记侧一处都没扫到（量具失效或全被删了）", notes.isNotEmpty())
        val bad = notes.filter { (_, _, line) ->
            // 三参形状：noteCardBounds(键, full, visible) —— 逗号数不足 = 还在吃单矩形旧契约。
            val args = line.substringAfter("noteCardBounds(").substringBeforeLast(")")
            args.count { it == ',' } < 2
        }
        assertTrue(
            "noteCardBounds 必须吃双矩形（full 定位 + visible 判锚），旧单参调用违例：\n" +
                bad.joinToString("\n") { (rel, no, l) -> "$rel:$no: ${l.trim()}" },
            bad.isEmpty(),
        )
    }
}
