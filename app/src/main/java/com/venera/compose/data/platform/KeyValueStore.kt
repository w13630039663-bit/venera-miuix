package com.venera.compose.data.platform

/**
 * 键值配置的唯一存取通道 —— 之前 13 处直连 `SharedPreferences` 的旁路都从这里走。
 *
 * 两端各一个实现：
 *  - Android（[com.venera.compose.data.platform.android.AndroidKeyValueStore]）贴在系统
 *    SharedPreferences 上，一个名字一个 XML；
 *  - 桌面（[JsonKeyValueStore]）一个名字一个 JSON 文件，落在 `prefs/` 子目录。
 *
 * 两侧共同遵守的语义（**不许在实现里偷偷放宽**）：
 *  1. 键不存在 ⇒ 给默认值；
 *  2. 键存在但类型与读法不符 ⇒ 抛，不拿默认值顶替 —— "配置读不出来"和"没配置过"
 *     是两件事，让前者冒充后者就是静默交错；
 *  3. 能落的值只有 String / Boolean / Int / Long / Float / 字符串 Set 这六种，
 *     别的类型当场抛（没有对应的 get 方法，存进去就是读不回来的死数据）。
 */
interface KeyValueStore {

    fun getString(k: String, d: String?): String?

    fun getBoolean(k: String, d: Boolean): Boolean

    fun getInt(k: String, d: Int): Int

    fun getLong(k: String, d: Long): Long

    fun getFloat(k: String, d: Float): Float

    /** 没这个键就是空集，不是 null —— 调用方拿到 null 迟早会写成 `?: emptySet()` 到处漂。 */
    fun getStringSet(k: String): Set<String>

    /** 写一个值；[v] 为 null 等于删掉这个键（与 `putX(key, null)` 的口径一致）。 */
    fun put(k: String, v: Any?)

    /**
     * 一批键一次写。
     *
     * 收口前这些调用点写在一个 `edit { putX; putY }` 里，是一次事务；逐条 [put] 会把它
     * 拆成多次落盘，崩溃窗口里可能只进去一半（代理那三条、WebDAV 那份配置都是这样）。
     * 默认实现按条目循环，两个实现各自覆盖成一次原子写。
     */
    fun putAll(values: Map<String, Any?>) {
        values.forEach { (key, value) -> put(key, value) }
    }

    fun remove(k: String)

    fun clear()

    fun keys(): Set<String>
}

/** 有没有这个键。等价于收口前的 `contains()`，省得调用点写 `k in keys()` 绕一圈。 */
fun KeyValueStore.contains(k: String): Boolean = k in keys()
