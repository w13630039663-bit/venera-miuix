package com.venera.compose.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「本机该下哪一份」的判据（批 1）。
 *
 * 四份产物的体积几乎相同（14.42~14.48 MB），所以这一层省不了什么钱，
 * 它唯一的作用是**别静默给一份装不上或跑不了的**—— 而误判的形状恰好是"匹配字符串匹配糊了"：
 * `x86` 是 `x86_64` 的前缀、`armeabi` 是 `armeabi-v7a` 的前缀。所以匹配式带尾点，
 * 下面两条前缀用例就是钉这一件事的。
 */
class ApkAssetPickerTest {

    private fun apk(name: String) = ReleaseAsset(name, 1L, "a".repeat(64), "https://github.com/x/$name")

    private val four = listOf(
        apk("venera-miuix-2.0.3.apk"),
        apk("venera-miuix-2.0.3-arm64-v8a.apk"),
        apk("venera-miuix-2.0.3-armeabi-v7a.apk"),
        apk("venera-miuix-2.0.3-x86_64.apk"),
    )

    @Test
    fun `现代手机按自己的 abi 拿那一份`() {
        val arm64 = apk("venera-miuix-2.0.3-arm64-v8a.apk")
        assertEquals(arm64, pickApkAsset(four, listOf("arm64-v8a", "armeabi-v7a")))
        val v7a = apk("venera-miuix-2.0.3-armeabi-v7a.apk")
        assertEquals(v7a, pickApkAsset(four, listOf("armeabi-v7a", "armeabi")))
    }

    @Test
    fun `x86_64 不许被 x86 那半截抢走`() {
        val x86_64 = apk("venera-miuix-2.0.3-x86_64.apk")
        assertEquals(x86_64, pickApkAsset(four, listOf("x86_64", "x86")))
    }

    @Test
    fun `abi 顺序里靠前的那个优先而不是清单里的第一个`() {
        // Build.SUPPORTED_ABIS 本身按优先级排，通用包在清单里排第一也不该赢。
        val x86 = apk("venera-miuix-2.0.3-x86.apk")
        assertEquals(x86, pickApkAsset(listOf(apk("venera-miuix-2.0.3.apk"), x86), listOf("x86")))
    }

    @Test
    fun `没有匹配的那一份时退到通用包`() {
        val universal = apk("venera-miuix-2.0.3.apk")
        assertEquals(universal, pickApkAsset(four, listOf("riscv64")))
        assertEquals(universal, pickApkAsset(listOf(universal), listOf("arm64-v8a")))
    }

    @Test
    fun `本机 abi 全不命中又没有通用包时交回 null 而不是随便挑一份`() {
        // 这一条是本层存在理由的另一半：宁可说"这一版没有适配本机的安装包"，
        // 也不要下一份 arm64 包给一台 x86 机器 —— 那会一路走到安装器才失败。
        val splits = four.filter { it.name != "venera-miuix-2.0.3.apk" }
        assertNull(pickApkAsset(splits, listOf("mips")))
        assertNull(pickApkAsset(splits, emptyList()))
        assertNull(pickApkAsset(emptyList(), listOf("arm64-v8a")))
    }

    @Test
    fun `空白 abi 不进匹配`() {
        // 否则 "" 会拼出 "-." 这种式子，而任何 "xxx-.apk" 都可能撞上。
        val universal = apk("venera-miuix-2.0.3.apk")
        assertEquals(universal, pickApkAsset(listOf(universal), listOf("", "  ")))
    }
}
