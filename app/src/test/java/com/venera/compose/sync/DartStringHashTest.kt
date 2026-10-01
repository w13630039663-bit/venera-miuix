package com.venera.compose.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dart VM `String.hashCode` 复刻的正确性。
 *
 * 这个算法一旦算错，官方归档导进来的每一条收藏都会「来源未知」——而且不报错、不崩溃，
 * 只表现为「一条都没导入」，属于最坏的那类失败。所以这里锁两组数：
 *
 * 1. **外部交叉验证**：`"Hello Axe" == 154086258`。这是公开的 Dart 实测输出，用来证明
 *    我们复刻的是 **VM 版**（Flutter/AOT 用的那套）而不是 dart2js 版 —— 同一个字符串
 *    在 dart2js 上是 473525114，两者不可互换。
 * 2. **回归锁**：官方归档里会出现的那些源 key，把当前算出来的值钉住。将来谁动了算法，
 *    这里会立刻红，而不是等到用户导入时才发现。
 */
class DartStringHashTest {

    @Test
    fun `外部实测值对得上，说明复刻的是 VM 版而不是 dart2js 版`() {
        assertEquals(154086258, DartStringHash.hash("Hello Axe"))
    }

    @Test
    fun `常用源 key 的 Dart 哈希被钉住`() {
        // 这些数值 = 官方 Venera 归档 local_favorite.db / history.db 里 type 列的取值。
        val expected = mapOf(
            "picacg" to 553570794,
            "ehentai" to 385625716,
            "nhentai" to 264196719,
            "jm" to 769844263,
            "hitomi" to 258019538,
            "wnacg" to 823512256,
            "copy_manga" to 557997769,
        )

        for ((key, hash) in expected) {
            assertEquals("源 $key 的 Dart 哈希对不上", hash, DartStringHash.hash(key))
        }
    }

    @Test
    fun `结果始终落在 Dart Smi 能表示的 30 位正数区间`() {
        for (key in ForeignImportMapping.BUILT_IN_SOURCE_KEYS) {
            val h = DartStringHash.hash(key)
            assertEquals("$key 的哈希越界：$h", true, h in 1..0x3FFF_FFFF)
        }
    }

    @Test
    fun `大小写不同即不同哈希（不做任何归一化）`() {
        assertEquals(false, DartStringHash.hash("ManHuaGui") == DartStringHash.hash("manhuagui"))
    }
}
