package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 画廊首页头图的接线（2026-10-15，用户「图廊首页也加上和设置页面一样的可以切换的头图」）。
 *
 * 「和设置页一样」读成**同一套机制、不同一张图**：独立偏好键 + 独立落盘槽位。
 * 共用一张会让"换画廊的头"顺手改掉设置页，反之也一样 —— 那是两个开关拧到一处。
 *
 * 摆法钉死在墙的那颗 header item **内部**、chrome 之上：新插一枚 item 会撞本仓两族已量过的坑
 * （懒列表"第 0 项之后插入"的锚定漂移 = 首页顶栏重叠那次的真机根因；header 必须单根 Column
 * = 2026-10-01 错误提示压在搜索栏上）。
 */
class GalleryHeroWiringTest {

    private val appMain = RepoSources.appMain

    private fun src(rel: String): String {
        val f = File(appMain, rel)
        assertTrue("找不到 $rel，量具失效", f.isFile)
        return f.readText().replace("\r\n", "\n")
    }

    /** 注释行不参与匹配（扫描类的口径与 TokenBudgetFrozenTest 同一把）。 */
    private fun codeLines(rel: String): List<String> = src(rel).lines().map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    @Test fun `画廊头图用独立键与独立槽位`() {
        val prefs = src("com/venera/compose/data/prefs/VeneraPreferences.kt")
        val store = src("com/venera/compose/feature/settings/SettingsHeroImage.kt")
        assertTrue("缺 pref_gallery_hero_path 这个键", prefs.contains("\"pref_gallery_hero_path\""))
        assertTrue("缺 KIND_GALLERY_HERO 槽位常量", store.contains("KIND_GALLERY_HERO = \"gallery_hero\""))
        // 两本账不许串：画廊那侧不许读设置页的键
        val galleryUi = src("com/venera/compose/gallery/ui/GalleryScreen.kt")
        assertTrue(
            "画廊侧不许复用设置页的 settingsHeroPath（那会把两个开关拧成一处）",
            !galleryUi.contains("settingsHeroPath"),
        )
        // 路径由装配根递进来：`gallery/ui/**` 直连业务单例会被 BusinessApiBoundaryTest 的
        // A/B 两条钉红（白名单只许缩短），所以这一侧**不许**自己去取 VeneraPreferences。
        assertTrue("头图必须是递进来的 heroPath 参数", galleryUi.contains("heroPath: String,"))
        assertTrue(
            "画廊侧不许直连 VeneraPreferences 单例（走装配根递参数）",
            !galleryUi.contains("VeneraPreferences"),
        )
        val nav = src("com/venera/compose/feature/Navigation.kt")
        assertTrue("装配根没读出 galleryHeroPath", nav.contains("prefs.galleryHeroPath.collectAsState()"))
        assertTrue("装配根没把它递给 GalleryScreen", nav.contains("heroPath = galleryHeroPath"))
    }

    @Test fun `递进来的路径要经过收据核验`() {
        // prefs 里那串只是收据：清空应用数据、卸载重装、手工删文件之后它还在。
        // 三种情况都必须落回"没设过头图"，不许摆一张裂图。
        val galleryUi = src("com/venera/compose/gallery/ui/GalleryScreen.kt")
        assertTrue("没核文件是否存在", galleryUi.contains("it.isFile"))
        assertTrue("没核零字节空文件", galleryUi.contains("it.length() > 0L"))
    }

    @Test fun `设置里有换与清两个入口`() {
        val body = src("com/venera/compose/feature/settings/GallerySettings.kt")
        assertTrue("缺换图入口（pick 那一支）", body.contains("rememberSettingsImagePicker(SettingsHeroImageStore.KIND_GALLERY_HERO)"))
        assertTrue("缺清除入口（clear 那一支）", body.contains("clear(context, SettingsHeroImageStore.KIND_GALLERY_HERO)"))
        // 清图必须同时把键写回空串 —— 只删文件不写键，屏上会留一张裂图
        assertTrue("清除那一支必须同时 setGalleryHeroPath(\"\")", body.contains("setGalleryHeroPath(\"\")"))
    }

    @Test fun `头图摆在 chrome 之上而不新增墙内项`() {
        val body = src("com/venera/compose/gallery/ui/GalleryForYouScreen.kt")
        val headerAt = body.indexOf("header = {")
        assertTrue("header 那一块都找不到，量具失效", headerAt >= 0)
        // ⚠️ 2026-10-16 订正**形态**（原文：`body.indexOf("hero?.invoke()")`）。
        //
        // 用户「左右还是有空白」这一轮之后，头图外面裹了一层通栏 Box，那一行从
        // `hero?.invoke()` 变成了 `hero?.let { heroSlot -> … }`。**判据意图一个字没变** ——
        // 它仍是 header 里、chrome **之上**的那一格，也仍然不许新插 grid item（见下方负向断言）。
        // 所以这里不再钉死内层的调用写法（下次再包一层不该又误报），只认
        // 「`hero?.` 这个前缀」+「必须落在 header 块之内」+「排在 chrome 之前」这三件事。
        val heroAt = body.indexOf("hero?.", startIndex = headerAt)
        val chromeAt = body.indexOf("chrome?.invoke()", startIndex = headerAt)
        assertTrue("header 里找不到 hero 那一格", heroAt >= 0 && chromeAt >= 0)
        assertTrue("hero 必须摆在 chrome **之上**（$heroAt vs $chromeAt）", heroAt < chromeAt)
        // 负向：不许为它新插一枚 grid item（锚定漂移那一族）
        val screen = src("com/venera/compose/gallery/ui/GalleryScreen.kt")
        assertTrue(
            "hero 不许出现在新增 item 里",
            !screen.contains("item(key = \"hero\"") && !screen.contains("item(key = \"grid-hero\""),
        )
    }

    @Test fun `头图不参与避让地板与收栏阈值`() {
        // 这两条是刻意的：hero 是**内容**（随墙滚走），而避让地板与收栏阈值只认顶栏与 chrome。
        // 一旦有人把 hero 高度算进这两处，搜索卡的落点与收栏手感会一起漂。
        val screen = src("com/venera/compose/gallery/ui/GalleryScreen.kt")
        assertTrue("gridTopPadding 必须仍只等于 topBarFloor", screen.contains("val gridTopPadding = topBarFloor"))
        assertTrue("gridTopPadding 必须仍只等于 topBarFloor", screen.contains("val gridTopPadding = topBarFloor"))

        // ⚠️ 2026-10-16 订正这一条（原文：`searchOverlayTop` 必须仍只等于 topBarFloor）。
        //
        // 头图**出血**之后（用户「表现方式应该和图二一样才对」），入口条本身整体上移了
        // topBarFloor 那一段 —— 落点若还钉在 topBarFloor，"从入口条原地长开"就退化成
        // "卡从上方掉下来盖住入口条"。所以**在场那一支必须跟随**，不在场那一支仍钉原地。
        // 这条尺子的精神（不许把 hero 高度算进**顶栏避让地板**）没变，漂的只是"落点"这一半。
        assertTrue(
            "搜索卡落点必须分两支：无头图 = topBarFloor；有头图 = 头图那一段的占位高",
            // ⚠️ 2026-10-18 01:32 改判（用户「在首页搜索框的时候，头图会异常显示」）：判据回到
            // `galleryHeroFile != null`。当天早些时候把它换成 heroActive 的前提是"搜索态那一屏
            // 没有头图" —— 那条**不成立**：主墙 `GalleryForYouPage` 在 1947 行是**无条件**组合的，
            // 搜索卡只是叠在它之上的浮层（见本文件「这两层都不许再被 if (svm.active) 包着」），
            // INPUT 态那层 scrim 把它连同头图、入口条一起压暗，图还在原位。
            // 落点跟的是**入口条实际画在哪**，与搜索态开不开无关 ⇒ 跟随判据必须问"设没设过头图"。
            screen.contains("val searchOverlayTop = if (galleryHeroFile != null) {") &&
                screen.contains("statusBarTop + VeneraSpacing.settingsHeroHeightSubpage - VeneraSpacing.settingsHeroOverlap"),
        )
        // ⚠️ 2026-10-16 换档（用户「空位有点太多…1选A吧」）：头图用**设置子页那一档** 148dp。
        // 两条**必须同一个常量** —— 占位高与落点是同一条式子，只改一处，搜索卡就会停在旧落点上，
        // "从入口条原地长开"退化成"卡从上方掉下来盖住入口条"。
        assertTrue(
            "头图 totalHeight 没用子页那一档（148dp）",
            screen.contains("totalHeight = statusBarTop + VeneraSpacing.settingsHeroHeightSubpage,"),
        )
        assertTrue(
            "头图 totalHeight 与搜索卡落点用的不是同一个高度常量 ⇒ 两处会各漂各的",
            !screen.contains("totalHeight = statusBarTop + VeneraSpacing.settingsHeroHeight,"),
        )
    }

    /**
     * 头图在场时的**出血契约**（2026-10-16，用户「画廊的头图有问题，表现方式应该和图二一样才对」）。
     *
     * 图二 = 画廊设置页。头图在那边摆在**内容列之外**、天然从屏幕最顶出血；画廊这边它是墙的
     * 第一行，必须靠这四件事凑出同一个观感 —— 缺任何一件都会退回"一条标题栏 + 一张图"两张皮：
     * ① 墙顶留白归零；② 顶栏大标题让位；③ 标题改由头图画成图上白字；④ 折叠连接不挂。
     *
     * ⚠️ 2026-10-18 给这四条重新划过作用域（「搜索态会被连坐」），当天 01:32 又把它**收窄成两条**：
     * 让位类 —— 顶栏大标题让位、折叠连接不挂、玻璃进度改由墙自己喂 —— 只对画廊首页成立，
     * 判据 `heroActive`（搜索态的大标题是「搜索」，不是空串，"幽灵折叠行程"那一档根本不发生）。
     * 落点类 —— 墙顶留白、搜索卡落点 —— 问的是图与入口条**实际画在哪**，而主墙在搜索态照旧
     * 无条件组合（`GalleryForYouPage` 没被 `if (svm.active)` 包着，搜索卡只是压在它之上的浮层），
     * 所以仍只问"设没设过头图"。落点类换成 heroActive 是有读数的失败：头图在搜索态凭空下移一截
     * （用户「在首页搜索框的时候，头图会异常显示」）。
     */
    @Test fun `头图在场时出血并让位`() {
        val screen = src("com/venera/compose/gallery/ui/GalleryScreen.kt")
        assertTrue(
            "墙顶留白没做头图分支 ⇒ 头图出不了血（还是被顶栏压在下面）",
            // 落点类：判据是"设没设过头图"，不是 heroActive（理由见本类 KDoc 与 wallTopPadding 自己那条）。
            screen.contains("val wallTopPadding = if (galleryHeroFile != null) 0.dp else gridTopPadding"),
        )
        assertTrue(
            "主墙没吃 wallTopPadding ⇒ 分支白算",
            screen.contains("topPadding = wallTopPadding,"),
        )
        assertTrue(
            "顶栏大标题没在头图在场时让位 ⇒ 一条深色大字横压在图的上段上",
            // 2026-10-18：让位只对首页（heroActive），搜索态那一档渲染「搜索」
            // （用户：「搜索页面的居中标题显示搜索才对」+「还是没有左边的大标题」）。
            screen.contains("largeTitle = if (heroActive) \"\" else if (svm.active) \"搜索\" else \"画廊\""),
        )
        assertTrue(
            "标题没交给头图（图上应当是白字「画廊」/ 搜索态「搜索」）",
            screen.contains("title = if (svm.active) \"搜索\" else \"画廊\","),
        )
        assertTrue(
            "头图在场时没摘掉折叠连接 ⇒ 空大标题的\"幽灵折叠行程\"会先吃掉一段上滑",
            screen.contains("else Modifier.nestedScroll(topBarBehavior.nestedScrollConnection)"),
        )
        assertTrue(
            "玻璃进度没改由墙的滚动状态喂（不挂连接之后它会恒 0 ⇒ 玻璃永不出现）",
            screen.contains("scrollProgressOverride = if (heroActive) heroScrollProgress else null,"),
        )
        assertTrue(
            "没接状态栏图标翻转 ⇒ 深色图标压在图上",
            screen.contains("SettingsHeroSystemBarIcons(heroPresent = heroPresent"),
        )
    }

    /**
     * 头图**让位契约**不许泄漏进搜索态（2026-10-18，用户「还是没有左边的大标题」那张截图）。
     *
     * 那条截图里搜索态顶栏左边是空的 —— 因为大标题让位那条判据问的是"设没设过头图"，
     * 没问"现在是不是搜索态"。所以这里钉两件事：
     * ① 有一枚把两者都问上的判据（`heroActive`），且它不是 `heroPresent` 的别名；
     * ② 凡是"因为头图而让位 / 而摘连接"的地方都必须吃 `heroActive`。
     *
     * ⚠️ 2026-10-18 01:32 收窄：这一条**只管让位类**。落点类（墙顶留白、搜索卡落点、跟着折叠收的那一截）
     * 仍只问"设没设过头图"，各自钉在本类 `头图不参与避让地板与收栏阈值` / `头图在场时出血并让位`
     * 和 `GalleryPixivArtistSearchTest` 的「顶栏折起来那一截空白」那把尺子里 —— 别把它们再塞回这个名单，
     * 同一件事只许一把尺子。
     */
    @Test fun `头图契约不泄漏进搜索态`() {
        val rel = "com/venera/compose/gallery/ui/GalleryScreen.kt"
        val screen = src(rel)
        // ① 判据本身存在，且同时问了"设没设"与"是不是搜索态"。
        assertTrue(
            "缺 heroActive 这枚判据（= 设了头图 且 不在搜索态）",
            screen.contains("val heroActive = heroPresent && !svm.active"),
        )
        // ② 让位类三处（大标题、玻璃进度、折叠连接的摘除）都必须吃 heroActive。
        for (anchor in listOf(
            "largeTitle = if (heroActive) \"\" else if (svm.active) \"搜索\" else \"画廊\"",
            "scrollProgressOverride = if (heroActive) heroScrollProgress else null,",
            "if (heroActive) Modifier",
        )) {
            assertTrue("这一处没按首页收窄（还在让搜索态连坐）：$anchor", screen.contains(anchor))
        }
        // ③ 负向：折叠连接的摘除也不许再吃 heroPresent（搜索态要挂回去，否则卡收不进顶栏）。
        val code = codeLines(rel)
        assertTrue(
            "折叠连接的摘除还在吃 heroPresent（搜索态会被连坐 ⇒ 顶栏折不下去）",
            !code.any { it.contains("if (heroPresent) Modifier") },
        )
        // ④ heroPresent 仍要活在它两件本职上：头图自己摆不摆、系统栏图标翻不翻。
        assertTrue(
            "heroPresent 只剩 heroActive 一个用处 ⇒ 头图本身与系统栏图标会一起失灵",
            screen.contains("SettingsHeroSystemBarIcons(heroPresent = heroPresent"),
        )
    }

    /**
     * 有头图的那面墙**禁掉过度滚动**（2026-10-16，用户「钳制效果，禁止在设置了头图的页面使用过度滚动，
     * 不然效果不好」）。
     *
     * 机制走 `GalleryCardsGrid.disableOverscroll` → `overscrollEffect` 这条**组件参数**
     * （`LazyVerticalStaggeredGrid` 本身就收 `OverscrollEffect?`），不去包 CompositionLocal：
     * 一处开关、一处取舍，比往组合树上挂一层隐式状态好读也好删。
     */
    @Test fun `头图墙禁掉过度滚动`() {
        val screen = src("com/venera/compose/gallery/ui/GalleryScreen.kt")
        assertTrue("GalleryCardsGrid 缺 disableOverscroll 参数", screen.contains("disableOverscroll: Boolean = false,"))
        assertTrue(
            "没按开关取舍 overscrollEffect（要么恒 null 要么从不生效）",
            screen.contains("overscrollEffect = if (disableOverscroll) null else platformOverscrollEffect,"),
        )
        // ⚠️ 必须**无条件**先 remember 出来再取舍：把 `rememberOverscrollEffect()` 写进 if 分支
        // 就是条件调用 Composable（组合期就会崩），所以这一行必须独立存在。
        assertTrue(
            "rememberOverscrollEffect() 必须无条件先取出来（写进 if 分支 = 条件调用 Composable）",
            screen.contains("val platformOverscrollEffect = rememberOverscrollEffect()"),
        )
        val forYou = src("com/venera/compose/gallery/ui/GalleryForYouScreen.kt")
        assertTrue("有头图那面墙没把开关传下去", forYou.contains("disableOverscroll = hero != null,"))
    }

    /**
     * 头图**整屏通栏**（2026-10-16，用户「而且左右还是有空白」）。
     *
     * 设置页那头图摆在内容列**之外**（外/内双 Column），天然从屏幕最左到最右；画廊这边它是网格的
     * header item，会跟着吃到 `contentPadding` 的左右内缩（实测截图逐像素：左右各 26px ≈ 11dp，
     * 正是 `screenHorizontal` 那一档）。所以只能自己把那一圈还回去 —— 而"还"的手法**必须**
     * 是"报告宽仍是容器宽、只把绘制往两侧各拉 inset"：占位一变，网格那一行的高度与相邻列的
     * 对齐就跟着漂（这就是"吃内缩"的另一种写法，只是反向）。
     *
     * ⚠️ 不能用负 `padding` —— `Modifier.padding` 对负值有 `require` 检查，会直接崩。
     */
    @Test fun `头图整屏通栏`() {
        val rel = "com/venera/compose/gallery/ui/GalleryForYouScreen.kt"
        val forYou = src(rel)
        assertTrue(
            "头图没裹通栏 Box ⇒ 左右还是各留一圈内缩",
            forYou.contains("Box(Modifier.fullBleedHorizontal(tokens.spacing.screenHorizontal)) {"),
        )
        assertTrue(
            "通栏修饰符没定义",
            forYou.contains("private fun Modifier.fullBleedHorizontal(inset: Dp): Modifier"),
        )
        assertTrue(
            "通栏后报告的宽度必须是容器宽（占位一点不许变）",
            forYou.contains("layout(constraints.maxWidth, placeable.height) {"),
        )
        assertTrue("没把内容拉到左侧负偏移（等于只是量宽了、没真的画出去）", forYou.contains("placeable.place(-insetPx, 0)"))
        // 容器宽未知（无限约束）时不许硬算 —— 宁可少通栏也不要量出一个荒谬的宽度。
        assertTrue("缺 Constraints.Infinity 守卫", forYou.contains("Constraints.Infinity"))
        // 负向：通栏只许被头图用一处（定义 1 + 调用 1 = 2）。搜索条是个圆角条，左右本来就该有内缩。
        val hits = codeLines(rel).count { it.contains("fullBleedHorizontal") }
        assertTrue("通栏修饰符只该被头图用到（定义 1 + 调用 1 = 2），实际 $hits 处", hits == 2)
    }
}
