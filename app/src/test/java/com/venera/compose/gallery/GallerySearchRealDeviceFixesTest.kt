package com.venera.compose.gallery

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GelbooruCredentialState
import com.venera.compose.gallery.domain.GalleryNoticeAction
import com.venera.compose.gallery.domain.GalleryNoticeKind
import com.venera.compose.gallery.domain.GalleryNoticeTone
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.galleryNotices
import com.venera.compose.gallery.ui.noticeTitle
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-16 真机反馈四件的尺子（用户 23:37 / 23:39 两张截图立案）。
 *
 * 四件里有两件是**行为**（G 站窗口定界的解析、缺席提示归类到哪一档），
 * 另两件是"这一路不许再接回来"的**接线**（键盘不再自动收条、整轮失败不退回首页）。
 * 接线那两条按本仓口径钉**整串表达式**，不钉标识符 —— 标识符看不见死分支，
 * 而"把 effect 留着但让它恒假"恰好是最省事的假修复形状。
 *
 * 每把负向尺都配一枚对照（同一符号在**该在**的那颗文件里仍在），
 * 否则"文件改名的那天"也会量出全绿。
 */
class GallerySearchRealDeviceFixesTest {

    private val galleryUi = "com/venera/compose/gallery/ui"

    /** 一颗文件的**代码行**（跳三种注释行）—— 注释里提到被撤的符号是历史交代，不算命中。 */
    private fun codeLines(dir: String, name: String): List<String> =
        RepoSources.kotlinFiles(dir)
            .first { it.name == name }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private fun areaCode() = codeLines(galleryUi, "GallerySearchArea.kt")
    private fun screenCode() = codeLines(galleryUi, "GalleryScreen.kt")

    /** ① 键盘收起那一路整条撤了：这颗文件里再没有"收条"这件事。 */
    @Test
    fun `键盘收起不再自动把搜索卡收成一条`() {
        // ⚠️ 只钉**调用形**（带点的那一串），不钉裸符号名 —— 裸名会把同前缀的
        // `GallerySearchCollapsedChips`（吸附胶囊层，本轮没动它）也算成命中，那是尺子自己的错。
        val banned = listOf("collapseToResults", "imeWasVisible", "shouldCollapseOnImeHidden(")
        val hits = areaCode().filter { line -> banned.any { line.contains(it) } }
        assertEquals("搜索卡里又接回了自动收条那一路：$hits", emptyList<String>(), hits)
        // 那颗判据件本身也不在源码树里了（整块进 `_trash/gallery-ime-collapse-2026-10-15/`）。
        assertTrue(
            "判据件回到了 gallery/domain",
            RepoSources.kotlinFiles("com/venera/compose/gallery/domain")
                .none { it.name == "GallerySearchCollapse.kt" },
        )
        // 对照：`collapseToResults` 本身没被撤走（宿主仍要用它），撤的只是"键盘"那一个调用方。
        assertEquals(
            "宿主那层的收条出口数量变了（判式钉的是『键盘这一路没了』，不是『这颗符号没了』）",
            2,
            screenCode().count { it.contains("collapseToResults") },
        )
        // 键盘仍然参与**高度**判据 —— 撤的是收条，不是"知道键盘在不在"。
        assertTrue(
            "列表限高不再看键盘了？这一条不是本轮要改的",
            areaCode().any { it.contains("WindowInsets.isImeVisible") },
        )
    }

    /** ② 收条只剩一个出口：系统返回。展开的入口仍是点卡与顶栏🔍。 */
    @Test
    fun `收条与展开的两枚出口各自钉死`() {
        // 逐字钉整串：只钉 `collapseToResults` 会放过"分支条件写反"那种形状。
        assertTrue(
            "系统返回那枚收条出口变了形状（现在它是唯一出口）",
            screenCode().any {
                it.contains("svm.mode == GallerySearchMode.INPUT && svm.filters.isNotEmpty() -> svm.collapseToResults()")
            },
        )
        assertTrue(
            "点卡重新展开那一路没了（收条之后用户只能靠顶栏🔍）",
            areaCode().any {
                it.contains("if (expanded) focusRequester.requestFocus() else svm.backToInput()")
            },
        )
    }

    /**
     * ③ 整轮失败时**结果墙继续接管**，屏上落的是"这一轮没搜成"那块空态，不是首页。
     *
     * ⚠️ 同一颗文件里那两道**闸门**（滚动记录、自动续页）必须仍是窄的那把：
     * 把 `searchError` 也喂给续页判据，失败那一轮就会对着同一个坏请求无限重发
     * （`page` 停在 0 ⇒ `runSearch(page + 1)` 恒等于重跑第 1 页）。
     */
    @Test
    fun `失败轮接管内容区但绝不接管自动续页`() {
        assertTrue(
            "结果墙的接管判据里没有『这一轮已经说过失败』那一支 —— 用户会读成「搜不到就自己退回主页」",
            screenCode().any {
                it.contains("val searchWallActive = searching && (svm.page > 0 || svm.isSearching || svm.searchError != null)")
            },
        )
        val narrow = "val wallActive = svm.active && svm.filters.isNotEmpty() && (svm.page > 0 || svm.isSearching)"
        assertEquals(
            "那两道闸门被顺手改宽了（它们一宽，失败轮就变成无限自动重试）",
            2,
            // ⚠️ **整行相等**而不是 `contains`：扰动笔 Q3 量出来过一次假绿 ——
            // 往那一行**后面**接 ` || svm.searchError != null` 时，含窄串的判式照样数到 2，
            // 于是"改宽"这一族在尺子上完全隐形。这一族的口径本来就是"钉整串表达式"。
            screenCode().count { it == narrow },
        )
        assertTrue(
            "闸门那一行被就地加宽了（改宽 = 失败轮对着同一个坏请求无限重发）",
            screenCode().none { it.startsWith(narrow) && it != narrow },
        )
    }

    /** ④ 配了凭据却被站方拒：归到"去配置账号"那一档，而不是"重试"。 */
    @Test
    fun `凭据被拒那一档给的是设置页的出口`() {
        val reason = GelbooruCredentialState.REJECTED_HINT + "。年排行的窗口边界要靠这一笔定出来"
        val notice = galleryNotices(mapOf(GallerySite.GELBOORU to reason)).single()
        assertEquals(GalleryNoticeKind.CredentialRejected, notice.kind)
        assertEquals(GalleryNoticeTone.Warning, notice.tone)
        assertEquals(GalleryNoticeAction.ConfigureAccount, notice.action)
        val title = noticeTitle(notice)
        assertTrue("标题不许有换行：$title", !title.contains("\n"))
        assertTrue("标题超长，一行档只会读成省略号：「$title」(${title.length})", title.length <= 32)
        // 站方原话留在正文里：401 与 403 是两种事（改凭据 / 等风控缓过来），不许在归类时被丢掉。
        assertTrue("正文没带上站方原话：${notice.body}", notice.body.contains(GelbooruCredentialState.REJECTED_HINT))
    }

    /** ⑤ 归类判据是**前缀**，不是"含『凭据』二字"：措辞相近的别的报错不许被误归。 */
    @Test
    fun `只有那句唯一来源的凭据原话才算被拒`() {
        // 对照一：真·凭据那句（与④同源）确实归这一档。
        assertEquals(
            GalleryNoticeKind.CredentialRejected,
            galleryNotices(mapOf(GallerySite.GELBOORU to GelbooruCredentialState.REJECTED_HINT)).single().kind,
        )
        // 对照二：同样含"凭据"两个字、但不是那句原话的报错 —— 不许被关键词顺手吸走。
        assertEquals(
            GalleryNoticeKind.LegFailed,
            galleryNotices(mapOf(GallerySite.GELBOORU to "年排行：这一站的窗口要先把起止探出来，这一次没探成（凭据之外的事）")).single().kind,
        )
        // 对照三：没配账号那一档仍以它自己的标题起头，两档不串。
        assertEquals(
            GalleryNoticeKind.MissingAccount,
            galleryNotices(mapOf(GallerySite.GELBOORU to GallerySearchSource.NEEDS_ACCOUNT_REASON)).single().kind,
        )
    }

    // ── 2026-10-16 第二轮（用户 00:30 那张截图带回了原文读数，另加两格观感）──

    private fun chipCode() = codeLines("com/venera/compose/components/venera", "VeneraChip.kt")

    /**
     * ⑥ × 的命中盒**由住它的那一处表态**，两档各是各的。
     *
     * 真机立案两轮：第一轮，`.combinedClickable` 挂在 16dp 的图标上，外面那圈 padding 不吃点击
     * ⇒ 真触达只有 16×16dp（用户「太小很难按到」）；第二轮，本鱼把 removable **一律**给 40dp，
     * 于是**已选条件那一排跟着长高**（用户「连同搜索tag一起变大了，你分开」）。
     * 所以这一把钉的不是"多大"，而是"每一处 removable 都必须自己说一句话" ——
     * 道数尺（`onRemoveClick` 与 `removeTouchSize` 各 2 处）就是防第三处悄悄继承某一档。
     */
    @Test
    fun `删除把手的触达尺寸由调用点各自表态`() {
        val chip = chipCode().joinToString("\n")
        assertTrue("组件上没有 `removeTouchSize` 这一枚了", chip.contains("removeTouchSize: Dp? = null,"))
        assertTrue(
            "× 的 clickable 又回到 16dp 的图标上了（命中区只有 256dp²）",
            chip.contains(".size(hit)\n.combinedClickable("),
        )
        val area = areaCode().joinToString("\n")
        // 历史那一条 = 按钮档（40dp 现成档，图标仍画 16dp）
        assertTrue("最近搜索那枚 × 的档位丢了", area.contains("removeTouchSize = tokens.spacing.iconButtonSize"))
        // 条件胶囊 = 旧形状（一排里的元素，不许把整排顶高）
        assertTrue("已选条件那一排又开始自己长高了", area.contains("removeTouchSize = null,"))
        // 道数对照：两处 removable 都表了态，一处都没多。
        assertEquals(
            "removable 的调用点与表态的道数不相等了（新增一处却没表态 = 悄悄继承某一档）",
            areaCode().count { it.contains("onRemoveClick =") },
            areaCode().count { it.contains("removeTouchSize =") },
        )
        assertTrue("removable 应当还是两处", areaCode().count { it.contains("onRemoveClick =") } == 2)
    }

    /** ⑦ 历史那枚 × 是**两步**：第一下只待确认，第二下才真删。 */
    @Test
    fun `最近搜索的删除不再一触即发`() {
        val joined = areaCode().joinToString("\n")
        assertTrue("第一下没进待确认态：$joined", joined.contains("armedIndex = index"))
        assertTrue(
            "第二下才真删（onRemove 直接挂在第一下了？）",
            joined.contains("armedIndex = -1\nonRemove(entry)"),
        )
        assertTrue("待确认没有视觉表达（用户读不出现在是「等一下要确认」）", joined.contains("if (armed) Icons.Outlined.Check else Icons.Outlined.Close"))
        assertTrue("超时不解除会留下一块说不清状态的紫块", joined.contains("delay(REMOVE_ARM_MS)"))
        assertTrue("待确认记的是下标（`GallerySearchEntry` 不是 data class，比对象会恒假）", joined.contains("val index = lineIndex * 2 + slot"))
    }

    /**
     * ⑧ 结果态那张卡：**满宽 + 走材质轴**，且尾巴上带一枚读数槽。
     *
     * ── 2026-10-16 第三轮，用户口径又变了：「设成全宽吧，可以在搜索框里加入其他信息」──
     *
     * 前两轮在这件事上走过的路（都记着，免得下次再绕）：
     *  - 第一轮套顶栏吸附层那一档（34%）→ 真机把第二枚胶囊切了半截；
     *  - 第二轮取它的**补数**（66%）+ 挂右 → 不切了，但带两个隐性代价：
     *    ① 展开/收起时**横向跳一下**（66% ↔ 满宽），"同一块面在长高"这条契约在宽度上断了；
     *    ② 右侧仍是一块无信息区。
     * 本轮用户直接给了答案：**满宽**。遮挡交给 `GalleryScreen.searchResultsInset` 那条稳定空白带 ——
     * 宽度不再兼任"少挡一点"（两个目标压在一个数上，两边都做不到位）。
     *
     * 满宽腾出来的那一块改成**读数槽**（`SearchResultReadout`）：这一条是"带着条件往下看图"时
     * 唯一还在屏上的话，而它的两个兄弟读数（页尾的「已摆出 N 张」、展开区里的缺席原因）
     * 要么得滚到墙底、要么收条之后整块不画。
     *
     * 材质那条**不变**（本轮用户又单点了一次「莫名其妙的黑了」，成因在玻璃那一层，
     * 处置写在 `VeneraGlass.PanelAlphaLight`，判据见下面 ⑧b）：色仍两态同一句、
     * 玻璃仍挂 `veneraGlassSurface(PANEL)`。
     */
    @Test
    fun `结果态那张卡满宽带读数槽且两态同一材质口径`() {
        val joined = areaCode().joinToString("\n")
        // 满宽：那张卡不再按吸附层那把尺子夹自己（夹回 66% 会横向跳、右侧留白）。
        assertTrue(
            "结果态那张卡又开始按吸附层那把尺子收窄了（满宽是用户 2026-10-16 拍板的）",
            !joined.contains("(1f - tokens.spacing.collapsedChipWidthFraction)"),
        )
        // 起排口径：两态都从左侧；"挂右"那一支连同它的分支一起撤掉。
        assertTrue(
            "起排口径没有收成常量 Start",
            joined.contains("horizontalAlignment = Alignment.Start,"),
        )
        assertTrue(
            "「收起挂右」那一支还在（满宽之后它已无意义）",
            !joined.contains("horizontalAlignment = if (expanded) Alignment.Start else Alignment.End,"),
        )
        // 读数槽：必须存在，且报的数是**调用方喂的**（与页尾同一把尺子），不许在卡里自己数。
        assertTrue("结果态尾巴上没有读数槽", joined.contains("SearchResultReadout("))
        assertTrue(
            "读数槽没吃调用方给的 resultCount（自己数一份就会与页尾分叉）",
            joined.contains("resultCount = resultCount"),
        )
        assertTrue("读数槽不再预警「有腿缺席」", joined.contains("svm.legFailures.isNotEmpty()"))
        // 材质轴：两态**同一句**底色 —— 实色档不透明铺它；玻璃档置透明，样品由 veneraGlassSurface 采。
        assertTrue(
            "那张卡的底色不再吃材质轴了（切到液态玻璃它就不会透）",
            joined.contains(
                "color = if (veneraGlassEnabled()) Color.Transparent else tokens.color.surfaceContainerHigh",
            ),
        )
        // 负向：伪透那一档不许回来 —— 它就是"切实色仍半透明"的假开关本体。
        assertTrue(
            "那张卡又用 tagContainerAlpha 伪透了：它绕过材质轴，" +
                "而且 0.90 只透 10% —— 读不出玻璃、却把卡色染成底下那张图的颜色",
            !joined.contains("tokens.current.tagContainerAlpha"),
        )
        assertTrue(
            "玻璃档没挂 veneraGlassSurface(PANEL)：切到液态玻璃时这张卡不会有真模糊（同一块面会在两档分成两样）",
            joined.contains("veneraGlassSurface(VeneraGlassRole.PANEL"),
        )
        // 被限高切断的那一枚必须渐隐收口（用户第三张截图上正是那半枚胶囊）。
        assertTrue(
            "被限高切断的内容又硬切了（最下面那枚胶囊会被切成一半）",
            joined.contains(".fadeBottomEdge(tokens.spacing.space6)"),
        )
        // 对照组：这把尺子咬得动 —— 把两档现成 token 名喂进同一份文本，必须都抓得住。
        val selfControl = joined + "\nwidthIn(max = 0.dp)\ncolor = Color.White\n"
        assertTrue("负向尺自己就是恒真的", selfControl.contains("widthIn(max"))
    }

    /**
     * ⑧b 面板档玻璃在**浅色**下的容器不透明度必须单列一档，不许再与普通卡片共用 0.22。
     *
     * ⚠️ 本用例的**判据本身没变**（面板档要单列一档、且真的接进 `when`），但 2026-10-16 二次复核
     * 时订正了它当初的**归因**：这一档**不是**"消灰块"的开关。灰块（用户两次报的「莫名其妙地黑了」
     * 与「还是有这种色块」）的真凶是平台投影从透明卡底透出来，判据在下面 ⑧c。
     * 反证：把 0.22 抬到 0.86 之后灰块**一点没退** —— 投影画在容器色之后，调前者动不了后者。
     *
     * 这一档现在站得住的理由是"浅色下透换不到分层"：`surfaceContainerHigh`(L≈0.81) 与氛围层顶部
     * (L≈0.79) 几乎同亮度，0.22 算出来的抬亮只有 +1.5，面板会与页面背景分不开。见
     * `VeneraGlass.PanelAlphaLight` 那段订正后的注释。
     */
    @Test
    fun `面板档玻璃浅色下的不透明度单列一档`() {
        val glass = RepoSources.linesOf("com/venera/compose/components/venera")
            .filter { it.first.endsWith("VeneraGlass.kt") }
            .joinToString("\n") { it.third }
        val panelAlpha = Regex("""private const val PanelAlphaLight = ([0-9.]+)f""")
            .find(glass)?.groupValues?.get(1)?.toFloatOrNull()
        val containerAlpha = Regex("""private const val ContainerAlphaLight = ([0-9.]+)f""")
            .find(glass)?.groupValues?.get(1)?.toFloatOrNull()
        assertNotNull("找不到 ContainerAlphaLight（本用例的前提变了，先看那一档还在不在）", containerAlpha)
        assertNotNull("没有 PanelAlphaLight：面板档又被并回普通卡片那一档了", panelAlpha)
        assertTrue(
            "面板档的不透明度 $panelAlpha 没有高过普通卡片那一档 $containerAlpha：" +
                "浅色下容器色与氛围层几乎同亮度，低 alpha 算出来的分层只有 +1.5，" +
                "面板会与页面背景肉眼分不开 —— 用户会读到『卡片不见了』",
            panelAlpha!! > containerAlpha!!,
        )
        // 接线也要在：档位判断必须真的把 PANEL 分出去，否则常量加了也没人读。
        assertTrue(
            "PANEL 那一支没接进 veneraGlass：常量加了没人读",
            glass.contains("role == VeneraGlassRole.PANEL -> PanelAlphaLight"),
        )
    }

    /**
     * ⑧c 玻璃面**不许**带平台投影（2026-10-16，用户「浅色模式下还是有这种色块」）。
     *
     * ## 成因（逐层核过 miuix `basic/Surface.kt:128-146`）
     *
     * 库把投影挂在 `Modifier.graphicsLayer(shadowElevation, shape, clip = false)` 上，
     * 而那一格在**调用方 modifier 之内**：
     * `.veneraGlassSurface(...)` 在外、`graphicsLayer(shadowElevation)` 在内。
     * Compose 外层先画 ⇒ 真实层序 **① 模糊 → ② 容器色 → ③ 平台投影 → ④ 子元素**。
     * 不透明底的 Surface 上 ③ 被 ② 盖住看不见；玻璃档 ② 是透明的，③ 那层黑剪影就透出来了。
     *
     * ## 读数（4 张真机截图的连通域分割）
     *
     * 两块带投影的玻璃面都等于 `surfaceContainerHigh × 0.785`（入口药丸 `#CDBBB9`、
     * 搜索卡 `#C1B3B2`/`#C9B0AD`/`#BCB0AE`），逐通道吻合 ±1 ⇒ 那层黑不透明度 ≈ 0.215，
     * 且**与背景无关**（页面底亮度差一倍，卡色只动 ±9）。
     * 铁证是同一张卡里**胶囊亮、胶囊周围的卡底灰** —— ③ 在 ④ 之下才能长成这样。
     *
     * ## 判据为什么钉"调用点不许出现裸值"而不是钉那个函数
     *
     * 只钉函数存在的话，"函数写好了但没接线"也会全绿，而那正是这次 bug 的形状
     * （`shadowElevation = tokens.elevation.attached` 从 2026-10-16 第一版起就在那儿）。
     * 所以钉的是 `gallery/ui` 里**每一处** `shadowElevation` 赋值都必须过按档口。
     */
    @Test
    fun `玻璃面的投影必须按档取且玻璃档归零`() {
        val glass = RepoSources.linesOf("com/venera/compose/components/venera")
            .filter { it.first.endsWith("VeneraGlass.kt") }
            .joinToString("\n") { it.third }
        assertTrue(
            "没找到 veneraSurfaceShadowElevation 的按档实现（玻璃档必须归零、实色档照旧）",
            glass.contains("if (veneraGlassEnabled()) 0.dp else solidElevation"),
        )

        val assignments = RepoSources.kotlinFiles(galleryUi)
            .flatMap { f -> f.readLines().map { f.name to it.trim() } }
            .filter { (_, l) -> !l.startsWith("//") && !l.startsWith("*") && !l.startsWith("/*") }
            .filter { (_, l) -> l.startsWith("shadowElevation =") }
        assertTrue(
            "gallery/ui 里一处 shadowElevation 赋值都没抓到 —— 尺子自己失效了，" +
                "先确认这个写法还在（原样写 `tokens.elevation.attached` 就是被咬的那一种）",
            assignments.isNotEmpty(),
        )
        val raw = assignments.filter { (_, l) -> !l.contains("veneraSurfaceShadowElevation(") }
        assertEquals(
            "这些面把投影原值直接交给了 Surface：卡底透明时那层黑会从卡底透出来（×0.785）$raw",
            emptyList<Pair<String, String>>(),
            raw,
        )
        // 对照：两个已知挂玻璃的 Surface 仍在、且都走了按档口；否则上面那条会因"文件改名"而恒真。
        val joined = assignments.joinToString("\n") { it.second }
        assertTrue(
            "入口条的投影赋值不见了（对照失效，上面那条负向尺随时会变成恒真）",
            joined.contains("veneraSurfaceShadowElevation(tokens.elevation.attached)"),
        )
        assertEquals(
            "走按档口的赋值点数量变了（本轮修的是入口条 + 搜索卡两处；新增玻璃面要同步改这把尺子）",
            2,
            assignments.count { it.second.contains("veneraSurfaceShadowElevation(") },
        )
    }

    /**
     * ⑧d `fadeBottomEdge` 里的 `DstIn` 必须与**离屏层**成对、且离屏层在前。
     *
     * 2026-10-16 12:51 用户原话「但是这下下面有黑边了」—— 那一轮刚把玻璃面的灰块修掉，
     * 露出来的就是这一条：`drawWithContent { drawContent(); drawRect(DstIn) }` **不带离屏层**时，
     * `DstIn` 的 dst 是"当前画布上已经画过的一切"，不是"这一段自己刚画的内容"。于是渐变把
     * 卡底之下的氛围层与图墙也一起乘 alpha 抹掉，抹到 0 就透到窗口背景 = 一条**纯黑带**。
     *
     * 真机读数（浅色玻璃）：卡内 `#F5DDDD`(L=0.763) → 渐变起 `#E9D4D3` → 单调压暗
     * → `#0F0B08`(L=0.004) → 下一像素回 `#F4E8E8`。单调那一段正是 `Brush.verticalGradient`，
     * 而终点是**纯黑**而不是"卡底之下那张图" —— 这一条就把"`DstIn` 穿到窗口层"钉死了。
     *
     * 判据钉的是**成对 + 顺序**，不是"文件里出现过这两个符号"：光有离屏层却没接进这条链、
     * 或 DstIn 写在离屏层之前，都是同一个 bug 的两种形状。
     */
    @Test
    fun `底部渐隐必须先开离屏层再 DstIn`() {
        val body = areaCode()
            .dropWhile { !it.startsWith("private fun Modifier.fadeBottomEdge(") }
            .take(45)
        assertTrue(
            "找不到 fadeBottomEdge 的函数体（本用例的前提变了，先看它还在不在）",
            body.isNotEmpty() && body.first().startsWith("private fun Modifier.fadeBottomEdge("),
        )
        val offscreenAt = body.indexOfFirst { it.contains("CompositingStrategy.Offscreen") }
        val dstInAt = body.indexOfFirst { it.contains("blendMode = BlendMode.DstIn") }
        assertTrue(
            "fadeBottomEdge 没有开离屏层：`DstIn` 的 dst 会变成整张画布，" +
                "渐隐会穿透卡底抹到窗口背景 —— 真机读数是一路单调压暗到 #0F0B08 的纯黑带",
            offscreenAt >= 0,
        )
        assertTrue("fadeBottomEdge 不再用 DstIn 了（改成盖色块会盖出一道比卡底更实的带子）", dstInAt >= 0)
        assertTrue(
            "离屏层排在 DstIn **之后**：那样它罩不住那条链，等于没开",
            offscreenAt < dstInAt,
        )
        // 对照：两条链都还在（负向尺不该因"整块被删"而恒真）。
        assertTrue("渐隐没接到最近搜索那个容器上", areaCode().any { it.contains(".fadeBottomEdge(") })
    }

    /** ⑨ 「根据你的收藏」那一行标题撤了，两条推荐本身**仍在**（那是真入口，不顺手砍）。 */
    @Test
    fun `收藏推荐那行标题不再上屏而入口仍在`() {
        val hits = RepoSources.allMainLines()
            .filter { (_, _, line) ->
                val t = line.trim()
                !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*") &&
                    t.contains("text = \"根据你的收藏\"")
            }
            .map { (rel, no, _) -> "$rel:$no" }
        assertEquals("那行标题又回到代码行上了：$hits", emptyList<String>(), hits)
        // 对照：撤的是标题，不是那两条 —— 点下去仍是"换站 + 装条件 + 开搜"。
        val joined = areaCode().joinToString("\n")
        assertTrue("推荐行整块被顺手砍掉了（用户点名的只有那一行标题）", joined.contains("RecommendationRows("))
        assertTrue("推荐行的出口断了", joined.contains("onPick(site, tags)"))
    }
}
