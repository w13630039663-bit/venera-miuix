package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页 pixiv 那一栏这四件事的**接线判据**（2026-10-17 第七轮）。
 *
 * 用户那张截图上一共点了四样，四条都不是"画得好不好"，而是**读不读得到**：
 * 1. 「推荐下面的图标题没了，直接用 p 站自己的标题就行」⇒ 站方标题必须真接进墙；
 * 2. 「不要每次切页面都刷新，手动下拉才刷新」⇒ 取数前必须先看缓存/凭证；
 * 3. 「这个推荐也是要做成瀑布流的无限下拉的」⇒ 续页判据 + 追加；
 * 4. 「下拉有了，但下拉没有加载指示器」⇒ 下拉那一行必须有转的东西。
 *
 * 为什么用源码判据而不是组合测试：`app/` 没有 Robolectric（同 `PixivLoginPathsTest` 那条口径），
 * 这几件事又全是"顺序/有无"的形状 —— 顺序错一格（比如取数写在缓存命中之前）屏上就看不出差别，
 * 只有把两段的相对位置钉住才拦得住。
 *
 * ⚠️ 注释行一律不参与匹配（`codeLines` 那把口径）：这些文件里大段注释都写着同样的函数名，
 * 不过滤就等于判据读的是我们自己的解释、不是代码。
 */
class GalleryPixivHomeWiringTest {

    private val screen = codeLines("com/venera/compose/gallery/ui", "GalleryScreen.kt")
    private val forYou = codeLines("com/venera/compose/gallery/ui", "GalleryForYouScreen.kt")

    private fun codeLines(dir: String, name: String): List<IndexedValue<String>> {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（目录 $dir，根：${RepoSources.root.absolutePath}）")
        return file.readLines().withIndex().filter { (_, line) ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    private fun firstIndex(fragment: String, from: Int = 0): Int =
        screen.firstOrNull { it.index >= from && it.value.contains(fragment) }?.index
            ?: error("找不到 `$fragment`（从第 $from 行起）")

    private fun textBetween(from: Int, to: Int): String =
        screen.filter { it.index in from..to }.joinToString("\n") { it.value }

    // ── 一、标题：站方标题要真进墙 ─────────────────────────────────────────────

    @Test
    fun `推荐墙的标题取自站方 title，而不是靠标签去猜`() {
        val table = firstIndex("val pixivRecommendTitles")
        val body = textBetween(table, table + 6)
        assertTrue(
            "标题表必须从 PixivWork.title 来：\n$body",
            body.contains("it.post.uid to it.title"),
        )

        // 交给墙（`GalleryForYouPage` 的 stationTitles）：只在 pixiv 那一域传，其余域保持空表。
        //
        // ⚠️ 2026-10-17 第二轮：`stationTitles = if (domain == ...)` 这一段现在有**两处**
        // （首页主墙 + 搜索墙，后者交的是 `svm.pixivTitles`）。所以这里不能取"第一处"——
        // 那会去校验搜索墙、而这条用例问的是**首页**那一处。
        val call = screen
            .filter { it.value.contains("stationTitles = if (domain == GalleryHomeDomain.PIXIV)") }
            .map { it.index }
            .firstOrNull { index -> textBetween(index, index + 6).contains("pixivRecommendTitles") }
            ?: error("首页那处 stationTitles 没把 pixivRecommendTitles 交出去")
        assertTrue(
            "首页要把站方标题交给墙：\n" + textBetween(call, call + 6),
            textBetween(call, call + 6).contains("pixivRecommendTitles"),
        )
    }

    @Test
    fun `墙上站方标题覆盖算出来的那批，空串也要盖住`() {
        val dict = firstIndex("val dictTitles = rememberGalleryCardTitles")
        val body = textBetween(dict, dict + 14)
        assertTrue("要先算出词典那批：\n$body", body.contains("rememberGalleryCardTitles"))
        assertTrue(
            "站方给了的 uid 要从词典那批里摘掉（空串也算给过）：\n$body",
            body.contains("if (uid !in stationTitles) merged[uid] = title"),
        )
        assertTrue(
            "站方标题非空才写进表：\n$body",
            body.contains("if (title.isNotBlank()) merged[uid] = title"),
        )
    }

    // ── 二、切域不重发：取数之前必须先看缓存 / 凭证 ─────────────────────────────

    @Test
    fun `排行榜取数之前先看缓存，切域回来直接摆上一轮那一份`() {
        val guard = firstIndex("pixivRankingCache[cacheKey]?.let")
        val fetch = firstIndex("works.rankingInApp(", guard)
        assertTrue("缓存命中必须 return（不再发网）", textBetween(guard, guard + 5).contains("return@LaunchedEffect"))
        assertTrue(
            "缓存命中要排在取数**之前**（顺序反了就等于每次切回来都重发）",
            guard < fetch,
        )
        // 缓存键必须同时带档位与"哪条腿"：同一档在登录前后是两份不同内容。
        val key = firstIndex("val cacheKey = rankingMode.name")
        assertTrue(
            "缓存键要带登录态（app / www）：\n" + textBetween(key, key + 2),
            textBetween(key, key + 2).contains("#app") && textBetween(key, key + 2).contains("#www"),
        )
    }

    @Test
    fun `推荐取数之前先看这一档取过没有`() {
        val guard = firstIndex("if (pixivRecommendLoadedKey == requestKey)")
        val fetch = firstIndex("works.recommended(bearer)", guard)
        assertTrue("凭证命中必须 return", textBetween(guard, guard + 1).contains("return@LaunchedEffect"))
        assertTrue("凭证判据要排在取数之前", guard < fetch)
    }

    @Test
    fun `只有下拉刷新才清缓存`() {
        val refresh = firstIndex("val refreshWholePage")
        val body = textBetween(refresh, refresh + 30)
        assertTrue("下拉要清排行榜缓存：\n$body", body.contains("pixivRankingCache.clear()"))
        assertTrue("下拉要作废推荐凭证：\n$body", body.contains("pixivRecommendLoadedKey = null"))
    }

    @Test
    fun `切主 Tab 回来不重取：凭证不拼计数，状态写回进程级存档`() {
        // 域切换靠缓存，切 Tab 靠存档 —— 后者在 `popUpTo(saveState)` 之后 `remember` 全没了。
        val requestKey = firstIndex("val requestKey = if (signedIn)")
        val line = screen.first { it.index >= requestKey }.value
        assertTrue(
            "凭证里不许拼刷新计数（重新组合时它归零，凭证就永远对不上 ⇒ 每次回来都重取）：$line",
            !line.contains("pixivRecommendTick"),
        )
        assertTrue(
            "缓存要住进程级存档（切 Tab 时 remember 会丢）",
            screen.any { it.value.contains("GalleryPixivHomeStore.ranking") },
        )
        assertTrue(
            "离开时要把这一池写回存档",
            screen.any { it.value.contains("pixivStore.recommendLoadedKey = pixivRecommendLoadedKey") },
        )
        // 首帧不许清：否则刚恢复的那一池当场被自己抹掉。
        val authSeen = firstIndex("var pixivAuthSeen by remember")
        assertTrue(
            "登录态 effect 首帧要早退：\n" + textBetween(authSeen, authSeen + 8),
            textBetween(authSeen, authSeen + 8).contains("if (pixivAuth == pixivAuthSeen) return@LaunchedEffect"),
        )
    }

    // ── 三、无限下拉 ──────────────────────────────────────────────────────────

    @Test
    fun `推荐那一池滑到底会取下一页，且按 next_url 走`() {
        // 判据复用图库站那道 GalleryLoadMoreState（同一套三道闸门）。
        val effect = firstIndex("LaunchedEffect(forYouGridState, domain, pixivWorks)")
        val pixivGuard = screen.firstOrNull {
            it.index > effect && it.value.contains("if (domain != GalleryHomeDomain.PIXIV) return@LaunchedEffect")
        }?.index
            ?: error("续页 effect 里要判域（不然图库站那一屏也会去取 pixiv 的下一页）")
        val body = textBetween(pixivGuard, pixivGuard + 90)
        assertTrue("续页要重发站方推荐：\n$body", body.contains("works.recommended(bearer, next)"))
        assertTrue("取下一页要按 uid 去重（站方推荐跨页会重复）：\n$body", body.contains("it.post.uid !in seen"))
        assertTrue("站方没给 next_url 就是到底：\n$body", body.contains("pixivRecommendExhausted = true"))
    }

    @Test
    fun `续页判据与图库站同一道闸门`() {
        val gates = screen.filter { it.value.contains("GalleryLoadMoreState(") }
        assertTrue("首页要两处续页判据（图库站 + pixiv 推荐），现在只有 ${gates.size} 处", gates.size >= 2)
        assertTrue(
            "续页闸门要同时看'到底了'与'在取'：\n" + gates.last().value,
            screen.any { it.value.contains("exhausted = pixivRecommendExhausted") },
        )
        assertTrue(
            "在取时不许并发再取：",
            screen.any { it.value.contains("busy = pixivRecommendLoading || pixivRecommendLoadingMore") },
        )
    }

    // ── 四、下拉指示器 ────────────────────────────────────────────────────────

    @Test
    fun `下拉那一行里真的有转的东西`() {
        val row = forYou.firstOrNull { it.value.contains("if (onRefresh != null)") }?.index
            ?: error("找不到下拉那一行")
        val body = forYou.filter { it.index in row..(row + 24) }.joinToString("\n") { it.value }
        assertTrue(
            "下拉行要有指示器（从前只有 height，没有内容 —— 用户「下拉没有加载指示器」）：\n$body",
            body.contains("CircularWavyProgressIndicator("),
        )
        assertTrue(
            "指示器尺寸要走 token（`loaderInline` 那一档）：\n$body",
            body.contains("tokens.spacing.loaderInline"),
        )
    }

    // ── 五、每日热门：先到先上屏 + 下拉不清屏 ──────────────────────────────────

    @Test
    fun `每日热门哪一站先回来就先上屏`() {
        val call = firstIndex("source.loadDaily(")
        val body = textBetween(call, call + 8)
        assertTrue("要把先到的那几站交给屏上：\n$body", body.contains("onLeg = { pools ->"))
        assertTrue("落地要走 acceptPartial（不写缓存、不写凭证）：\n$body", body.contains("vm.acceptPartial(pools)"))
    }

    @Test
    fun `下拉刷新不清掉已有那一屏`() {
        val refresh = firstIndex("val refreshWholePage")
        val body = textBetween(refresh, refresh + 30)
        assertTrue(
            "下拉要走 refreshKeepingContent（refresh() 会清 posts ⇒ 整面墙换成骨架）：\n$body",
            body.contains("vm.refreshKeepingContent()"),
        )
        assertTrue("下拉这一段里不许出现会清屏的 vm.refresh()：\n$body", !body.contains("vm.refresh()"))
    }

    // ── 六、排行榜那排的打码集合（2026-10-17 第八轮，用户那张「全是 R18」的截图）────────

    @Test
    fun `排行榜的打码集合只收命中打码的那几张，不是整墙`() {
        // 病根：`buildGalleryWall` 里 `masked = true` 与 `masked = false` **都**进 `GalleryWall.cards`，
        // 所以 `cards.mapTo(HashSet()){…}` 等于"每一张都要打码" —— 排行榜因此恒全糊 + 恒画 R18 角标，
        // 与 rating、与 nsfw_mode 都无关。这条判据钉的是"先按 masked 滤一遍"。
        val head = firstIndex("val pixivRankingMasked")
        val body = textBetween(head, head + 8)
        assertTrue(
            "打码集合必须先 filter { it.masked }：\n$body",
            body.contains(".cards.filter { it.masked }.mapTo"),
        )
        assertTrue(
            "不许再出现「整墙 uid 直接进集合」那种写法：\n$body",
            !body.contains("}.cards.mapTo("),
        )
    }

    // ── 七、关注画师 / 推荐用户（2026-10-17 第八轮）────────────────────────────

    @Test
    fun `关注与推荐是两条腿，参数照 pixez 那一套`() {
        // `filter=for_android` 是这条端点能不能答的关键（我们先前量到 recommended 回 400/191 就是缺它）。
        //
        // 2026-10-19：`restrict` 从端点里写死的 `"public"` 变成了调用点给的形参（账号页要读非公开那一档）。
        // 首页这一栏**仍是公开**，所以锚点把档位一起钉住 —— 谁把它改成 Private，
        // 首页就会出现"你关注了一些你根本没关注的人"那种最容易误解的假读数。
        val following = firstIndex("works.following(bearer, me, PixivRestrict.Public)")
        assertTrue("关注列表要真打那一跳：\n" + textBetween(following, following + 3), following > 0)
        val recommended = firstIndex("works.recommendedUsers(bearer)")
        assertTrue("推荐用户也要打（两栏分开取）：\n" + textBetween(recommended, recommended + 3), recommended > 0)
        // 取数要挂在登录态与域上，且带"取过就不再取"的凭证（同排行榜/推荐那两条）。
        val effect = firstIndex("LaunchedEffect(domain, pixivAuth, pixivWorks, pixivArtistsTick)")
        val body = textBetween(effect, effect + 26)
        assertTrue("要先判域：\n$body", body.contains("if (domain != GalleryHomeDomain.PIXIV) return@LaunchedEffect"))
        assertTrue("未登录要如实清空，不许拿旧号的人顶：\n$body", body.contains("me.isNullOrBlank()"))
        assertTrue("命中凭证就不要再发：\n$body", body.contains("if (pixivUsersLoadedKey == requestKey) return@LaunchedEffect"))
    }

    @Test
    fun `下拉刷新也把关注画师一起重取`() {
        val refresh = firstIndex("val refreshWholePage")
        val body = textBetween(refresh, refresh + 30)
        assertTrue("下拉要作废那两栏的凭证：\n$body", body.contains("pixivUsersLoadedKey = null"))
        assertTrue("下拉要抬 tick 让 effect 重跑：\n$body", body.contains("pixivArtistsTick++"))
    }

    // ── 八、画师格合并（2026-10-17 第九轮，用户「推荐画师合并到发现和已关注供切换」）──────

    @Test
    fun `画师那一格是两档切换，两栏数据同时在场`() {
        val sections = codeLines("com/venera/compose/gallery/ui", "GalleryHomeSections.kt")
        // ⚠️ 锚点必须钉 `when` 分支那两行（带 `->`），**不能**钉次序清单里那两行：
        // `GalleryHomeSectionKey.PIXIV_ARTISTS,` 在 `galleryHomeSectionOrder` 里先出现一次，
        // 而 `to` 紧随其后就是同一张清单的下一行 —— 那样切出来的"一节"只有两行声明。
        //
        // ⚠️⚠️ 更要紧的是**量具本身**：`codeLines` 返回 `IndexedValue`，`indexOfFirst { … }`
        // 拿到的是**它在过滤后列表里的位置**，而下面 `filter { it.index in … }` 比的是**源码行号**。
        // 两者不是一个东西（本文件注释多，位置比行号小一大截），早先那条写法切出来的区间是空的，
        // 于是四条断言里第一条就红在一个空串上 —— 判据自己坏了，不是代码坏了。
        // 现在一律走 `.index`：位置只用来"找"，区间一律用行号。
        val from = sections.firstOrNull { it.value.contains("GalleryHomeSectionKey.PIXIV_ARTISTS ->") }?.index
            ?: -1
        val to = sections.firstOrNull {
            it.index > from && it.value.contains("GalleryHomeSectionKey.PIXIV_RANKING ->")
        }?.index ?: -1
        assertTrue("两处锚点都要在（要 when 分支那两行，不是次序清单那两行）", from >= 0 && to > from)
        val body = sections.filter { it.index in from until to }.joinToString("\n") { it.value }
        // ① 节头只有「画师」一个，切换器在节头下面（不再有两节）。
        // 2026-10-17 第十八轮：节头多了图标与 `chevron` 两个参数、写成多行了 —— 判据拆成
        // 「有这个节头」+「标题是『画师』」两条，不再要求挤在同一行（那一版钉的是排版，不是事实）。
        assertTrue("要有节头：\n$body", body.contains("MiuixSectionHeader("))
        assertTrue("节头是「画师」：\n$body", body.contains("title = \"画师\""))
        assertTrue("要有两档切换：\n$body", body.contains("PixivArtistTabRow(selected = pixiv.artistTab"))
        // ② 两栏数据都在同一节里按 Tab 取，而不是"切过去才取"。
        assertTrue("要按 Tab 选那一份：\n$body", body.contains("val onFollowing = pixiv.artistTab == PixivArtistTab.FOLLOWING"))
        assertTrue("关注那一份仍在：\n$body", body.contains("pixiv.following"))
        assertTrue("推荐那一份仍在：\n$body", body.contains("pixiv.recommendedUsers"))
        // ③ 最后只剩一排（两个 if 分支各交一份，不许并排两排）。
        assertTrue("这一节只许一条画师行：\n$body", body.split("PixivArtistRail(").size - 1 == 1)
        // ④ 2026-10-17 第十八轮（用户稿）：这节要包在实色卡里（三节各一张白卡）。
        assertTrue("画师这一节要吃到 surface = true：\n$body", body.contains("surface = true"))
        // ④ 切 Tab 不许进取数 effect 的键（进了就会切一次重取一次，还会闪一帧空态）。
        assertTrue(
            "切 Tab 只是换摆哪一份，不许触发取数：\n" +
                textBetween(firstIndex("LaunchedEffect(domain, pixivAuth, pixivWorks, pixivArtistsTick)"), 
                    firstIndex("LaunchedEffect(domain, pixivAuth, pixivWorks, pixivArtistsTick)") + 2),
            !screen.any { it.value.contains("pixivArtistTab") && it.value.contains("LaunchedEffect(") },
        )
    }

    @Test
    fun `排行榜的名次是角标，不再拼进标题`() {
        val home = codeLines("com/venera/compose/gallery/ui", "GalleryPixivHome.kt")
        val body = home.joinToString("\n") { it.value }
        assertTrue("名次要单独画（work.rank?.let 那一档）：\n$body", body.contains("work.rank?.let { rank ->"))
        assertTrue("标题要回到纯标题：\n$body", body.contains("text = work.title,"))
        // 旧写法：把名次与标题拼在同一行 —— 标题长了先截掉的是名次后面那几个字。
        // 只钉"拼接"这一半（`padStart` 与那个分隔符），标题变量名不进来：
        // 字符串里写 `${'$'}{work.title}` 这种转义只会让这条判据自己先崩。
        assertTrue(
            "名次不许再拼回标题里",
            !body.contains("padStart(2, '0')} · "),
        )
    }

    /**
     * 排行榜卡那颗 ♥（2026-10-17 第十八轮，用户稿）。
     *
     * 这条守的是**不给假把手**：卡片只有在调用方给了回调时才画 ♥；收藏集合按 `uid` 建
     * （`siteKey:id`），与本地收藏档同一把键。少任何一条，屏上会出现"点不动的心"。
     */
    @Test
    fun `排行榜卡的心只在有回调时画，收藏按 uid 查`() {
        val home = codeLines("com/venera/compose/gallery/ui", "GalleryPixivHome.kt")
            .joinToString("\n") { it.value }
        assertTrue("心要按回调有无来画：\n$home", home.contains("if (onToggleFavorite != null)"))
        assertTrue("心要能显示已收藏那一档：\n$home", home.contains("Icons.Filled.Favorite"))
        assertTrue("未收藏那一档：\n$home", home.contains("Icons.Outlined.FavoriteBorder"))
        // 收起态的卡宽算法。
        //
        // ⚠️ 这条原来是第十八轮的「三列均分 = `(maxWidth - gap * 2) / 3`」，
        // 已被用户 2026-10-17 第二十二轮「缩到一行只同时显示两个半吧」推翻 ——
        // 三张均分让卡宽只剩 102dp，叠上 170dp 定高封面就成细长条（用户报「压扁了」）。
        // 所以这里钉**新**口径而不是放宽：分子照旧（要减的仍是两枚间距），
        // 分母换成有名有姓的 `RANKING_PREVIEW_SLOTS`（2.5f），不许写回裸数字。
        assertTrue(
            "收起态按「一行露 2.5 张」算卡宽，且分母读常量：\n$home",
            home.contains("(maxWidth - gap * 2) / GalleryPixivHome.RANKING_PREVIEW_SLOTS"),
        )

        val fav = codeLines("com/venera/compose/gallery/ui", "GalleryScreen.kt")
            .joinToString("\n") { it.value }
        assertTrue("收藏集合按 uid 建：\n$fav", fav.contains("\"\${it.siteKey}:\${it.id}\""))
        assertTrue("点 ♥ 要真写档：\n$fav", fav.contains("favorites.toggle(post)"))
    }
}
