package com.venera.compose.reader

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.venera.compose.data.platform.KeyValueStore

/**
 * 阅读书签 —— 只记「哪本的哪一章哪一页」，不落任何图片字节。
 *
 * 与插图收藏的分工：插图收藏是**把这一页存下来**（去混淆落盘、进收藏墙），重且是"拿走去别处看"；
 * 书签是**原地记号**（回来直接跳到这页），轻且只服务本书阅读。两者互不合并：
 * 合并的结果必然是"打个记号还先写一张图到磁盘"。
 *
 * ## 存储
 *
 * 走 [KeyValueStore]（由调用方经 `BusinessPorts.stores.open()` 递进来，本类不自己 new 实现 ——
 * reader/ 在业务边界守卫的扫描目录里，不许直连 AndroidKeyValueStore）。
 * 每本一条 JSON 数组，键 `bm:<comicKey>`；comicKey = `<sourceKey>|<comicId>`，
 * 与跨源撞 id 的漫画天然隔离。
 */
data class ComicBookmark(
    val chapterId: String,
    val chapterTitle: String,
    val pageIndex: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

class ComicBookmarkStore(private val store: KeyValueStore) {

    private val gson = Gson()

    fun bookmarksFor(comicKey: String): List<ComicBookmark> {
        val raw = store.getString(key(comicKey), null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<ComicBookmark>>() {}.type
            gson.fromJson<List<ComicBookmark>>(raw, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** 当前页有没有书签。 */
    fun has(comicKey: String, chapterId: String, pageIndex: Int): Boolean =
        bookmarksFor(comicKey).any { it.chapterId == chapterId && it.pageIndex == pageIndex }

    /**
     * 翻切当前页书签：没有就加，有就删。返回**加与否**（调用方拿它出 Toast 与图标态）。
     */
    fun toggle(comicKey: String, chapterId: String, chapterTitle: String, pageIndex: Int): Boolean {
        val current = bookmarksFor(comicKey)
        val hit = current.any { it.chapterId == chapterId && it.pageIndex == pageIndex }
        val next = if (hit) {
            current.filterNot { it.chapterId == chapterId && it.pageIndex == pageIndex }
        } else {
            current + ComicBookmark(chapterId, chapterTitle, pageIndex)
        }
        persist(comicKey, next)
        return !hit
    }

    fun remove(comicKey: String, bookmark: ComicBookmark) {
        persist(comicKey, bookmarksFor(comicKey).filterNot { it == bookmark })
    }

    private fun persist(comicKey: String, list: List<ComicBookmark>) {
        // 接口口径：put null 等于删键，没有独立的 remove。
        if (list.isEmpty()) store.put(key(comicKey), null)
        else store.put(key(comicKey), gson.toJson(list))
    }

    private fun key(comicKey: String) = "bm:$comicKey"
}
