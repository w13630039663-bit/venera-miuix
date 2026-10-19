package com.venera.compose.gallery.data

/**
 * pixiv 会员身份的**唯一一份**缓存（2026-10-18）。
 *
 * 两处要用它，而且必须拿同一个值：
 * - **搜索**：收藏门槛的发法（会员发 `bookmark_num_min`，其余走词尾 `users入り`），
 *   以及「人气」这一档点不点得动；
 * - **收藏同步**：往站方加书签时 `restrict` 给 `public` 还是 `private`（私密书签是会员档）。
 *
 * 各探一份的话，屏上就会出现"搜索按会员、收藏按非会员"这种自相矛盾的读数 ——
 * 与 `chapterId` 两端不同轨是同一族坑，所以这里只留一颗。
 *
 * **取不到就是 false（当非会员）**：两边的 false 都只是换成另一种**真生效**的发法；
 * 而 false 判成 true 会让私密书签被拒、会员参数被静默忽略，那才是假开关的长相。
 */
internal object PixivPremium {

    private var probedFor: String? = null
    private var cached: Boolean = false

    /**
     * 同一个账号只打一次。[fetch] 回 `null` = 这一跳没答上 ⇒ **不记号**，下次再问一次
     * （记了号就会把这个账号永久钉成非会员）。
     */
    suspend fun of(userKey: String?, fetch: suspend () -> Boolean?): Boolean {
        if (userKey.isNullOrBlank()) {
            probedFor = null
            cached = false
            return false
        }
        if (probedFor == userKey) return cached
        val answer = runCatching { fetch() }.getOrNull()
        if (answer != null) {
            probedFor = userKey
            cached = answer
        }
        return answer == true
    }

    /** 屏上当前的读数（没探过 = false）。给"这一条按公开同步了"那种即时文案用，不再打网络。 */
    val current: Boolean get() = cached

    /** 退出登录 / 测试收尾用：把缓存清干净，免得换账号还吃着上一位的档位。 */
    fun forget() {
        probedFor = null
        cached = false
    }
}
