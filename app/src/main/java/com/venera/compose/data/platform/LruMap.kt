package com.venera.compose.data.platform

/**
 * 按**条数**淘汰的 LRU 小表 —— `android.util.LruCache` 的跨端替身。
 *
 * 存在的理由：`YandeReClient` 的别名缓存与 `GalleryTagCategories` 的分类缓存都用
 * `android.util.LruCache`，而那颗类上不了桌面编译面。两处都只用 `get` / `put`
 * （没有 size 读数、没有手动 trim、没有 weight），所以这里也只给这两枚成员 ——
 * 补齐 `evictAll` / `trimToSize` 之类是"接口先于消费方"的老毛病。
 *
 * ⚠️ 语义对齐点，改动前先确认这两条没被破坏：
 * 1. **线程安全**：两个调用点都在 `withContext(Dispatchers.IO)` 里，多协程会并发碰它。
 *    `android.util.LruCache` 内部自带同步，所以这里也必须同步 —— 换成裸
 *    `LinkedHashMap` 是引入竞态，不是等价替换。
 * 2. **访问序**：`accessOrder = true` + `removeEldestEntry` 才是 LRU；
 *    写成插入序就退化成"先来的先被踢"，热键反而先没。
 */
class LruMap<K : Any, V : Any>(private val maxEntries: Int) {

    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > maxEntries
    }

    fun get(key: K): V? = synchronized(map) { map[key] }

    fun put(key: K, value: V) {
        synchronized(map) { map[key] = value }
    }
}
