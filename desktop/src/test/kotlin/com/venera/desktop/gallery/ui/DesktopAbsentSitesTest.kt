package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GelbooruClient
import com.venera.compose.gallery.data.GelbooruCredentialState
import com.venera.compose.gallery.data.GelbooruCredentials
import com.venera.compose.gallery.data.SafebooruClient
import com.venera.compose.gallery.data.YandeReClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S4 的判据：**"缺席必须明说"** 与 **凭据门禁**。
 *
 * ## 这批用例钉的四件事
 *
 * 1. **匿名 ⇒ Gelbooru 401 ⇒ `failures` 里有它 ⇒ 界面上有那句话**；
 * 2. **带凭据 ⇒ 三站均有货**（不是"看起来有"，是真有 `GalleryPost`）；
 * 3. **pid 是 0 基**（`GelbooruClient.kt:167` 的 `(page - 1).coerceAtLeast(0)`）——
 *    这条错了**不会报错**，只会静默取到第二页的数据，所以只能断言它；
 * 4. **401 那句人话逐字**、且**不许出现"敬请期待"这类模糊文案**。
 *
 * ## 为什么这批走的是真 client 而不是构造假集合
 *
 * 前一版的写法是 `val failures = setOf("gelbooru")` 然后断言它含有 `"gelbooru"` ——
 * 那是**自证**：无论生产代码怎么坏它都绿。真正要拦的失败形态是"401 那句话没写出来"或
 * "pid 减 1 那行被删了"，这两样只有打真 client 才看得见。
 * 所以这里用 [FakeHttpEngine] 在 `HttpEngine` 那一层掐住末一跳：**URL 拼装、解信封、
 * 401 → 人话、失败 → `failures`** 全部走生产代码，只有 socket 不出去。
 */
class DesktopAbsentSitesTest {

    /**
     * 清凭据跑一轮：Gelbooru 401，**另两站照旧出图**，且失败原因逐字是那句 401 人话。
     *
     * 钉的是三件事同时成立：三站混搭**没有**静默退化成一两站、缺席站被点名、
     * 而且点名时给的是"凭据没被接受"而不是"这一站今天没图"。
     */
    @Test
    fun `匿名时 Gelbooru 缺席而另两站照旧出图`() = runBlocking {
        val engine = FakeHttpEngine.fresh {
            onStatus(GELBOORU_BASE, code = 401)
            on(YANDERE_POPULAR, body = YANDERE_POPULAR_BODY)
            on(SAFEBOORU_POSTS, body = SAFEBOORU_POOL_BODY)
        }

        val client = GelbooruClient(engine, FakeHttpEngine.AnonymousCredentials)
        val failure = client.fetchTopScored().exceptionOrNull()

        assertNotNull("匿名请求必须失败（实测 DAPI 匿名一律 401）", failure)
        // 401 的 body 是空的，所以这句人话是**唯一**的诊断依据 —— 它变了就等于用户看不懂为什么。
        assertEquals(GelbooruCredentialState.REJECTED_HINT, failure!!.message)

        // 另两站不受影响：它们是匿名可用的，这正是"缺席要说清是哪一站"的前提。
        val yande = YandeReClient.getInstance(engine).fetchDailyPopular()
        val safe = SafebooruClient.getInstance(engine).fetchTopScored()
        assertEquals(1, yande.getOrThrow().size)
        assertEquals(1, safe.getOrThrow().size)
    }

    /**
     * 带凭据：Gelbooru **真的出货**（不是"不报错"），且请求里带上了那两枚参数。
     *
     * ⚠️ 后半句（URL 里真有 `api_key` / `user_id`）是这条用例真正的 teeth：
     * 只断言"没失败"的话，一颗**把凭据丢掉**的 client 照样绿 ——
     * 而那正是这一站最坏的一种错：它不报错，只是永远 401。
     */
    @Test
    fun `带凭据时 Gelbooru 出货且请求带上了两枚参数`() = runBlocking {
        val engine = FakeHttpEngine.fresh { on(GELBOORU_BASE, body = GELBOORU_POOL_BODY) }
        val client = GelbooruClient(engine, FakeHttpEngine.GoodCredentials)

        val posts = client.fetchTopScored().getOrThrow()

        assertEquals("带凭据就该有货", 1, posts.size)
        val sent = engine.requests.single { it.contains("sort%3Ascore") }
        assertTrue("请求必须带 api_key：$sent", sent.contains("api_key=deadbeef"))
        assertTrue("请求必须带 user_id：$sent", sent.contains("user_id=1234"))
    }

    /**
     * pid 是 **0 基**：调用方传的第 1 页要发 `pid=0`。
     *
     * 减 1 那行被删或被写成 `page` 直传，第一页就会取到第二页的数据 ——
     * **而且不会有任何报错**（实测：站方对越界 pid 照常回 200）。所以这条只能断言请求 URL。
     */
    @Test
    fun `第 1 页发的是 pid 0 而不是 pid 1`() = runBlocking {
        val engine = FakeHttpEngine.fresh { on(GELBOORU_BASE, body = GELBOORU_POOL_BODY) }
        val client = GelbooruClient(engine, FakeHttpEngine.GoodCredentials)

        client.searchPosts("rating:g", page = 1, limit = 20).getOrThrow()

        val sent = engine.requests.single { it.contains("rating") }
        assertTrue("第 1 页必须是 pid=0（Gelbooru 的 pid 是 0 基页号）：$sent", sent.contains("pid=0"))
    }

    /**
     * 缺席文案不许写"敬请期待"那一族。
     *
     * 红线的具体形式：本仓最忌的是"这一站没图"与"这一站没接"混成一句话 ——
     * 前者用户明天重试就好了，后者得去配凭据。所以缺席必须**点名缺的是哪一件**。
     */
    @Test
    fun `401 那句话必须点名要凭据而不许含糊`() {
        val hint = GelbooruCredentialState.ANONYMOUS_HINT
        assertTrue("必须点名 api_key：$hint", hint.contains("api_key"))
        assertTrue("必须点名 user_id：$hint", hint.contains("user_id"))
        assertTrue("必须说明 401 这件事：$hint", hint.contains("401"))
        listOf("敬请期待", "即将推出", "未来支持", "coming soon").forEach {
            assertTrue("缺席文案不许出现「$it」：$hint", !hint.contains(it, ignoreCase = true))
        }
    }

    /**
     * "没配"与"配了但被拒"是**两句话**，不能合并。
     *
     * 前者告诉用户"去配"，后者告诉用户"你配错了" ——
     * 合成一句"请配置凭据"会让后者被误读成前者，用户反复重填一对同样错误的凭据。
     * 两条都出现同一枚提示文案里时各自点名的成分不一样，这里逐字钉住。
     */
    @Test
    fun `没配与被拒是两句话且成分不同`() {
        val anonymous = GelbooruCredentialState.ANONYMOUS_HINT
        val rejected = GelbooruCredentialState.REJECTED_HINT
        assertTrue("两句话必须不同：都叫「$anonymous」", anonymous != rejected)
        assertTrue("没配那句要点名「需」：$anonymous", anonymous.contains("需"))
        assertTrue("被拒那句要点名「没接受」：$rejected", rejected.contains("没接受"))
        // 被拒那句**不许**回显用户刚敲的 key —— 提示文案里出现 key 就是把凭据抄进了日志面。
        listOf("deadbeef", "api_key=").forEach {
            assertTrue("提示文案里不许回显凭据（出现了「$it」）：$rejected", !rejected.contains(it))
        }
    }

    private companion object {
        // 站方 URL 前缀逐字照 client 里那几处拼装（`GallerySite.apiHost` 派生的 BASE）。
        // 假站按 `startsWith` 匹配，所以这里给到路径段就够，不必抄完整个 query。
        const val GELBOORU_BASE = "https://gelbooru.com/index.php?page=dapi&s=post"
        // yande.re 的日榜端点**不是** `/post.json`：日榜是 `/post/popular_recent.json?period=1d`
        // （`YandeReClient.fetchDailyPopular`）。按记忆写 `post.json` 的症状是假站报
        // 「没有为这枚 URL 登记假响应」—— 那条报错本身在提醒端点写错了，不是假站坏了。
        const val YANDERE_POPULAR = "https://yande.re/post/popular_recent.json"
        const val SAFEBOORU_POSTS = "https://safebooru.donmai.us/posts.json"

        /**
         * 一条真形状的 Gelbooru 响应。
         *
         * ⚠️ 必须是**信封**（`{"@attributes":…,"post":[…]}`）而不是裸数组：
         * 那一站把条目放在 `post` 键里（`GelbooruPostEnvelopeDto`），而**单条也可能是数组**。
         * 写成裸数组的话，"解信封"这段根本没被跑到，判据就是假绿。
         *
         * ⚠️ 三枚 `file_ext` / `preview_*_url` 都要给 —— `GalleryMerge.isDisplayable` 要求
         * 「扩展名在白名单 **且** 缩略图非空」，缺一枚就会被判成"给了行没给图"而滤掉。
         */
        const val GELBOORU_POOL_BODY =
            """{"@attributes":{"limit":100,"offset":0,"count":14304678},"post":[
              {"id":14970719,"tags":"rating:g solo","score":31144,"rating":"general",
               "owner":"someone","md5":"abc","file_ext":"png",
               "file_url":"https://i.example/g.png",
               "preview_url":"https://i.example/g-prev.png","width":800,"height":1000}
            ]}"""

        const val YANDERE_POPULAR_BODY =
            """[{"id":37,"tags":"rating:g","score":120,"rating":"g","author":"an",
               "file_ext":"png","preview_url":"https://i.example/y.png",
               "file_url":"https://i.example/y-full.png","width":800,"height":600}]"""

        const val SAFEBOORU_POOL_BODY =
            """[{"id":9000001,"tag_string":"rating:g solo","score":50,"rating":"g",
               "tag_string_artist":"an","fav_count":12,"file_ext":"jpg",
               "preview_file_url":"https://i.example/s.png",
               "file_url":"https://i.example/s-full.png","image_width":800,"image_height":600}]"""
    }
}
