package com.venera.compose.sync

/**
 * Dart VM `String.hashCode` 的复刻（纯 JVM，无依赖，可单测）。
 *
 * ## 为什么需要它
 *
 * 官方 Venera 是 Flutter/Dart 应用，它把「漫画源」编码成一个整数：
 * `ComicType(key.hashCode)`（见 `.reference/flutter-master/lib/foundation/comic_type.dart`
 * 与 `comic_source/comic_source.dart` 的 `intKey` / `fromIntKey`）。官方归档里的
 * `local_favorite.db` 与 `history.db` 的 `type` 列存的正是这个数值 —— **没有明文来源名**。
 *
 * 本仓的 `type` 是 JVM `String.hashCode`（见 `FavoriteItem.type`）。两者算法不同、数值
 * 互不相通，且哈希不可逆 ⇒ 导入官方归档时只能**先算出 Dart 值再反查**，于是必须复刻它。
 *
 * ## 用的是哪一套
 *
 * Dart 有两个不一致的实现，**不能混用**：
 * - **VM/AOT（Flutter 用的）**：Jenkins one-at-a-time 变体，累加器按 32 位自然溢出，
 *   最后收敛到 30 位（`& 0x3fffffff`）。即 `runtime/vm/object.cc` 的
 *   `StringHasher::Hash` + `FinalizeHash`。
 * - **dart2js（Web）**：每一步都 `& 0x1fffffff`（29 位），末尾不收敛。**同一字符串结果不同。**
 *
 * Flutter 跑在 VM 上，所以这里只能是前者。
 *
 * ## 校验
 *
 * `hash("Hello Axe") == 154086258`，与公开的 Dart 实测值一致（见 `DartStringHashTest`）。
 * 另外本文件不依赖任何 Android/org.json 类型，单测可直接跑。
 */
internal object DartStringHash {

    private const val MASK32 = 0xFFFF_FFFFL
    private const val MASK30 = 0x3FFF_FFFFL

    /**
     * 复刻 Dart VM `String.hashCode`。
     *
     * 遍历的是 UTF-16 code unit（Kotlin 的 `Char` 与 Dart 的 `codeUnitAt` 同为 UTF-16 单元），
     * 所以非 BMP 字符（emoji 等）会按代理对拆成两个单元参与运算 —— 与 Dart 行为一致。
     */
    fun hash(text: String): Int {
        var h = 0L
        for (ch in text) {
            h = (h + ch.code) and MASK32
            h = (h + ((h shl 10) and MASK32)) and MASK32
            h = h xor (h shr 6)
        }
        h = (h + ((h shl 3) and MASK32)) and MASK32
        h = h xor (h shr 11)
        h = (h + ((h shl 15) and MASK32)) and MASK32
        // 收敛到 30 位，保证落进 Dart Smi；0 会被 Dart 换成 1（`_hashCode` 用它做「未计算」哨兵）。
        val masked = h and MASK30
        return if (masked == 0L) 1 else masked.toInt()
    }
}
