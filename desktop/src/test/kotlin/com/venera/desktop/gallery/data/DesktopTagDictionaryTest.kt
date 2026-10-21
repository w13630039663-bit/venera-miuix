package com.venera.desktop.gallery.data

import com.venera.compose.gallery.domain.GalleryTagCategory
import com.venera.compose.gallery.domain.GalleryTitleTag
import com.venera.desktop.gallery.ui.DesktopSourceTree
import com.venera.desktop.gallery.ui.FakeHttpEngine
import com.venera.desktop.platform.FakePaths
import com.venera.desktop.platform.JdbcSqliteDatabase
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第四十批：把离线词典带上桌面 —— 三枚译名生产者第一次真跑。
 *
 * ## 这一批补的是哪一格
 *
 * 批 38 接了标签墙那一格的译名、批 39 接了关注栏与补全那排，三处消费点全绿而屏上**一个译名都没多出来**，
 * 因为生产端 [DesktopGalleryTagLexicon] 的 `translations` / `artistNames` / `titleTags` 是抛桩，
 * 理由写在批 38 的类注释里：Android 那颗 `GalleryTagDictionary` 吃 `Context.assets`，上不了桌面编译面。
 * 本批量完的读数是（2026-10-08 探针，逐项量的不是推断）：
 * - 真词典 `app/src/main/assets/gallery_tags_79415.sqlite` 3,257,344 B，表 `tags` **79,415 行、其中 71,423 行带 cn**；
 * - 档位分布 0=24,903 / 1=25,526 / 3=5,817 / 4=22,714 / 5=455；
 * - sqlite-jdbc 的 `open_mode=1` **是真只读**（写句被 `SQLITE_READONLY` 挡回），`mode=ro` 那形也拦得住；
 * - 随手 40 枚屏上真会读的标签：**33 枚在表里、33 枚都有译名**（漏的 7 枚是 Gelbooru 自己的词形与画师名）；
 * - classpath 两种形状都枚举得出根目录条目：jar 走 `JarURLConnection.jarFile`、exploded 走父目录 `listFiles`
 *   （`classLoader.getResource("")` 在 jar 形状实测回 null ⇒ 不能当锚点，必须钉在一颗**随包资源**上）。
 *
 * ## 为什么判据几乎全走真库
 *
 * 这一族的失败形状是"读出来是空的，而空与'词典就是没这些词'在屏上长得一样"。
 * 拿造的小库验"能查"没有意义（本仓已经有那颗 `TagDictionaryStoreTest` 用临时库钉读法），
 * 所以这里全部钉**真资产**：包内枚举得到、落盘成真实路径、只读打开、真名字回真译名。
 */
class DesktopTagDictionaryTest {

    /** ① 包内枚举 + 按**行数**取最新（不是字典序：`79415` 字典序大于 `100000`，而它更旧）。 */
    @Test
    fun 包内词典枚举得到且按名字里的行数取最新() {
        val names = (DesktopTagDictionaryAsset.classpathListing() as DesktopDictRead.Hit).value
        assertTrue("classpath 上枚举不出任何 $DESKTOP_DICT_PREFIX*.sqlite（构建脚本的 include 没带上？）：$names", names.isNotEmpty())
        assertTrue("枚举出来的必须都是那个形状：$names", names.all { it.startsWith(DESKTOP_DICT_PREFIX) && it.endsWith(".sqlite") })
        // 纯函数那一半：数字序而不是字典序
        val picked = DesktopTagDictionaryAsset.newest(
            listOf("gallery_tags_79415.sqlite", "gallery_tags_100000.sqlite", "gallery_tags_999.sqlite"),
        )
        assertEquals("gallery_tags_100000.sqlite", picked)
        // 认不出行数的名字排最后（-1），不能让它在字典序里赢过带行数的真资产
        assertEquals("gallery_tags_79415.sqlite", DesktopTagDictionaryAsset.newest(listOf("gallery_tags_x.sqlite", "gallery_tags_79415.sqlite")))
        assertNull("空清单不许凭空造一颗出来", DesktopTagDictionaryAsset.newest(emptyList()))
    }

    /** ② 只读打开的那条形是**真只读**：查得到，而写句必须炸。 */
    @Test
    fun 只读打开拒写而照常查得到() {
        val file = File.createTempFile("dict40-readonly-", ".db")
        try {
            JdbcSqliteDatabase(file).use { fresh ->
                fresh.exec("CREATE TABLE tags (name TEXT PRIMARY KEY, category INTEGER, cn TEXT) WITHOUT ROWID;")
                fresh.exec("INSERT INTO tags VALUES (?, ?, ?)", "blue_eyes", 0, "蓝瞳")
            }
            JdbcSqliteDatabase.openReadOnly(file).use { ro ->
                val rows = ro.query("SELECT cn FROM tags WHERE name = ?", "blue_eyes")
                assertEquals(1, rows.size)
                assertEquals("蓝瞳", rows.single().string("cn"))
                val failure = runCatching { ro.exec("INSERT INTO tags VALUES (?, ?, ?)", "x", 0, "y") }.exceptionOrNull()
                assertNotNull("只读连接居然吃得下写句：那条 open_mode 是装饰", failure)
                assertTrue(
                    "失败消息该留住 SQLite 那句 READONLY（它是我们只读判据的读数）：${failure!!.message}",
                    failure.message.orEmpty().contains("READONLY", ignoreCase = true),
                )
            }
        } finally {
            file.delete()
        }
    }

    /** ③ 落点由**资产名**推出（换一批词典 = 换个名字 = 必然重复制），落盘幂等且字节与包内一致。 */
    @Test
    fun 副本按资产名落盘且不重复制不覆盖活档() {
        val asset = DesktopTagDictionaryAsset.assetOf(DesktopTagDictionaryAsset.classpathListing()).valueOrNull()
            ?: error("包内没有词典资产，这一批的读数不成立")
        val paths = FakePaths(Files.createTempDirectory("dict40-").toFile())
        val first = DesktopTagDictionaryAsset.copyFileAt(paths, asset)
        assertEquals("落点必须带资产名（里面是行数）：$first", asset, first.name)
        assertEquals("副本落 $DESKTOP_DICT_DB_DIR/ 那一层", "db", first.parentFile.name)

        val copy = DesktopTagDictionaryAsset.materialize(paths, asset)
        assertTrue("没落出副本：$copy", copy.isFile)
        // 副本与**仓库里那颗资产**逐字节对得上：classpath 那份就是 processResources 从它拷来的，
        // 所以这一条量的是"复制没截断"（截断的库能打开成功而在查的时候炸，是最难查的那一型）。
        val repoAsset = File(DesktopSourceTree.repoRoot, "app/src/main/assets/$asset")
        assertTrue("仓库里没有那颗资产：${repoAsset.path}", repoAsset.isFile)
        assertEquals("副本字节数与仓库里那份资产不一致（复制截断了？）", repoAsset.length(), copy.length())
        // 已存在就不动它：把副本截断，再调一次不该悄悄重下（"存在即跳过"那颗判据的另一半）
        copy.writeText("")
        DesktopTagDictionaryAsset.materialize(paths, asset)
        assertEquals("存在着的副本被重写了 —— 落盘不幂等", 0L, copy.length())
    }

    /** ④ `translations` 真跑：命中的回译名、没命中的**不出现**、名字带引号与 `!` 也查得到（不拼 SQL 那一半）。 */
    @Test
    fun 真词典回真译名而库里没有的不出现() {
        val dictionary = DesktopGalleryTagDictionary(FakePaths(Files.createTempDirectory("dict40-t-").toFile()))
        // 三枚都是 2026-10-08 探针在真表里量到的行（带撇号与叹号的那种形状顺带钉"参数绑定"）
        val found = dictionary.translations(
            listOf("blue_eyes", "grabbing_another's_arm", "bang_dream!", "这一串不会是标签"),
        ).valueOrNull()
        assertNotNull("词典没读到（我们的故障，不是这批没译名）：$found", found)
        assertEquals("蓝瞳", found!!["blue_eyes"])
        assertEquals("抓他人手臂", found["grabbing_another's_arm"])
        assertEquals("BanG_Dream!", found["bang_dream!"])
        assertNull("库里没有的名字不许被补成一行空译名", found["这一串不会是标签"])
        assertEquals("命中的就该只有那三枚", 3, found.size)
        // 键一律小写：消费点按 tag.lowercase() 取（desktopTagDisplayLabel）
        assertEquals("蓝瞳", dictionary.translations(listOf("BLUE_EYES")).valueOrNull()?.get("blue_eyes"))
    }

    /** ⑤ 跨批合并没错：一批 1,200+ 枚（走 4 批）仍要命中原样回来、不混进不存在的名字。 */
    @Test
    fun 跨批合并不错位也不漏() {
        val dictionary = DesktopGalleryTagDictionary(FakePaths(Files.createTempDirectory("dict40-c-").toFile()))
        val names = (1..40).flatMap { dictionary.translations(listOf("blue_eyes")).valueOrNull()!!.keys } + (1..1200).map { "没有这个词$it" }
        val found = dictionary.translations(names).valueOrNull()
        assertNotNull("整批交回没读到就是'我们的查询炸了'被说成'这些图没译名'", found)
        assertEquals("切批后该仍命中那一枚真标签", "蓝瞳", found!!["blue_eyes"])
        assertTrue("结果里混进了不存在的名字：${found.keys.filter { it.startsWith("没有这个词") }}", found.keys.none { it.startsWith("没有这个词") })
    }

    /**
     * ⑬ 查询一律走切批那颗（**形状**钉死，因为这台驱动量不出差别）。
     *
     * 2026-10-08 探针在真库上逐个数过：`IN (?,…)` 的占位符 999 / 1,000 / 1,200 / 5,000 / 20,000 /
     * 32,766 / 32,767 / 40,000 / **100,000 全部成功**（驱动报的库版本 3.53.4）。
     * 也就是说 `TagDictionaryStore` 与 Android 那颗注释里写的"999 是变量上限，超了直接抛"
     * **在这台驱动上根本没有对象** ⇒ 把 `rowsForInChunks` 换成 `rowsFor`（扰动 p8）在这一批的用例里全绿。
     * 那条上限是 SQLite 的**编译期选项**（`SQLITE_MAX_VARIABLE_NUMBER`，旧默认 999、3.32 起默认 32766），
     * 换驱动或换构建就会回来，而它坏掉的表现仍然是"这批名字都没译名"—— 屏上看不见的东西只能由形状钉住。
     */
    @Test
    fun 查询走切批那颗而不是把整批塞进一条IN() {
        val code = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryTagDictionary.kt"),
        )
        assertTrue("查询没走分块那颗", code.contains("rowsForInChunks(names, CHUNK)"))
        assertEquals("又出现了一条不分块的 rowsFor( 调用：$code", 0, code.split("rowsFor(").size - 1)
        // 对照组：扫的是这颗文件而不是读空（读空会让上面两条恒真）。
        assertTrue("对照组失效：这份里已经没有分块常量了", code.contains("const val CHUNK = 400"))
    }

    /** ⑥ 库打不开 ⇒ 三枚都交"没读到 + 因"（批 78 起不再是 null），而那句读数必须出声，不能静默成空表。 */
    @Test
    fun 打不开时交带因的那一态且那句读数不许静默() {
        // dataRoot 指到一颗**普通文件**下面 ⇒ db/ 建不出来，这是"我们这侧的故障"的真实形状
        val blocker = File.createTempFile("dict40-blocker-", "")
        val paths = FakePaths(File(blocker, "db"))
        val dictionary = DesktopGalleryTagDictionary(paths)
        val captured = ByteArrayOutputStream()
        val previous = System.out
        System.setOut(PrintStream(captured, true, "UTF-8"))
        val reads = mutableListOf<DesktopDictRead<*>>()
        try {
            reads += dictionary.translations(listOf("blue_eyes"))
            reads += dictionary.artistNames(listOf("blue_eyes"))
            reads += dictionary.titleTags(listOf("blue_eyes"))
        } finally {
            System.setOut(previous)
            blocker.delete()
        }
        for (read in reads) {
            assertTrue("打不开却交成了别的形状（空表就是把我们的故障说成用户没干活）：$read", read is DesktopDictRead.Miss)
            assertTrue("打不开被说成了『包里没有』：$read", (read as DesktopDictRead.Miss).why is DesktopDictWhy.Unreadable)
        }
        val spoken = captured.toString(StandardCharsets.UTF_8)
        assertTrue("一句读数都没打（静默交错）：$spoken", spoken.contains("D_警告"))
        assertTrue("那句读数必须点名落点，否则查不到成因：$spoken", spoken.contains(blocker.absolutePath.replace('\\', '/')) || spoken.contains(blocker.path))
    }

    /** ⑦ `artistNames` 只认站方表里 `category = 1` 的那批；画师档在真表里 25,526 行。 */
    @Test
    fun 画师档只交category1的那些() {
        val dictionary = DesktopGalleryTagDictionary(FakePaths(Files.createTempDirectory("dict40-a-").toFile()))
        val (artistName, generalName) = samplePairFromDictionary(GalleryTagCategory.ARTIST, GalleryTagCategory.GENERAL)
        val artists = dictionary.artistNames(listOf(artistName, generalName)).valueOrNull()
        assertNotNull("词典没读到：$artists", artists)
        assertEquals("只该把画师档那枚交出来", setOf(artistName.lowercase()), artists)
        assertTrue("通用档被当成画师了：$artists", artists!!.none { it.equals(generalName, ignoreCase = true) })
    }

    /**
     * ⑧ `titleTags` 的档位表与 Android 侧同一套（真表数据驱动，不钉文案）：
     * 作品/角色/画师三档无条件进（没译名就用原词），通用档**只在有译名时**进，元数据档(5)永远不进。
     *
     * ⚠️ 每个档位**有译名与没译名两形都要抽**：只抽 `cn IS NOT NULL` 的那几行时，
     * "通用档无条件进"这一改（2026-10-08 扰动 p11）在样例上一行都看不出来 —— 全绿的判据替一条改坏了的
     * 规则作保（手册 §0 坑 ⑮ 那一族：样例绕过了判式）。
     */
    @Test
    fun 标题档四进一退按真表的档位判() {
        val dictionary = DesktopGalleryTagDictionary(FakePaths(Files.createTempDirectory("dict40-i-").toFile()))
        val byCategory = listOf(
            GalleryTagCategory.GENERAL,
            GalleryTagCategory.ARTIST,
            GalleryTagCategory.COPYRIGHT,
            GalleryTagCategory.CHARACTER,
            GalleryTagCategory.METADATA,
        ).associateWith { category ->
            sampleRows(category, cnNull = false) + sampleRows(category, cnNull = true)
        }
        assertTrue(
            "画师档里抽不到'没有译名'的那些行，这一条就退化成只看有译名的形状" +
                "（真表 7,992 行 NULL cn 全在画师/角色那几档里，通用档按构造恒带译名）",
            sampleRows(GalleryTagCategory.ARTIST, cnNull = true).isNotEmpty(),
        )
        val names = byCategory.values.flatten().map { it.name }
        val titles = dictionary.titleTags(names).valueOrNull()
        assertNotNull("没读到 ⇒ 整批交出去（不许交半份）", titles)
        for ((category, rows) in byCategory) {
            for (row in rows) {
                val tag = titles!![row.name.lowercase()]
                val expected = when (category) {
                    GalleryTagCategory.COPYRIGHT, GalleryTagCategory.CHARACTER, GalleryTagCategory.ARTIST -> true
                    GalleryTagCategory.GENERAL -> row.cn != null
                    else -> false
                }
                assertEquals(
                    "category=$category 名字=${row.name} cn=${row.cn} 的进出判据反了（Android 同一条写在 GalleryTagDictionary.titleTags）",
                    expected,
                    tag != null,
                )
                if (tag != null) {
                    assertEquals(category, tag.category)
                    assertEquals("有译名用译名、没有就原词", row.cn ?: row.name, tag.label)
                }
            }
        }
        assertTrue(
            "元数据档(5)在真表里有 455 行，这一批一条都没抽到 ⇒ 抽样失效",
            byCategory.getValue(GalleryTagCategory.METADATA).isNotEmpty(),
        )
    }

    /**
     * ⑭ 通用档"只在有译名时进"那一支钉**整串表达式**（真表里量不出来，只能钉形状）。
     *
     * 随包的这份表按构造就不会有"通用档而 cn 为 NULL"的行（构建脚本截的是"有中文译名 或 画师档"），
     * 所以把那一支改成无条件（扰动 p11）在 ⑧ 的样例上一条都不会红 ——
     * 与 ⑬ 同一族：屏上与样例都看不见的差别，只有形状钉得住。
     * 锚按表达式而不是按标识符列名（手册 §0 坑 ㉔：只数名字的话，死分支也绿）。
     */
    @Test
    fun 通用档那一支的表达式钉住而不是只数档位名() {
        val code = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryTagDictionary.kt"),
        )
        assertTrue(
            "通用档那一支不再是'有译名才进'那一形（摆出 shirt_lift 这种原词当标题是 Android 明确否掉的形状）",
            code.contains("GalleryTagCategory.GENERAL -> cn?.let { GalleryTitleTag(label = it, category = row.category) }"),
        )
        assertTrue("三档那一支不再用原词兜底", code.contains("GalleryTitleTag(label = cn ?: row.name, category = row.category)"))
        // 对照组：这三串都住在 when 里而不是别处的说明里。
        assertEquals("那一形出现了不止一处（档位表被复制了两份）：", 1, code.split("cn?.let { GalleryTitleTag").size - 1)
    }

    /**
     * ⑨ 三枚抛桩摘了：`DesktopGalleryTagLexicon` 不再交空集，而那句「未随包分发」的假话不许留在源码里。
     *
     * 批 38 量的是"桌面拿不到词典"，本批量的是"拿得到" —— 同一件事换了读数，屏上与日志里那两句话就得跟着换，
     * 而旧那句现在**反了**（词典现在就在包里）。这条钉的是旧说明（`DesktopStaleClaimsTest` 那一族的第 ㉒ 枚）。
     */
    @Test
    fun 词典上口不再交空而旧那句未分发不许留() {
        val source = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryTagLexicon.kt"),
        )
        assertTrue("translations 还在直接交空映象", source.contains("dictionary.translations("))
        assertTrue("artistNames 还在直接交空集", source.contains("dictionary.artistNames("))
        assertTrue("titleTags 还在直接交 null", source.contains("dictionary.titleTags("))
        assertEquals("「未随包分发」那句假话还留着（$source）", 0, source.split("未随包分发").size - 1)
        // 对照组：这颗文件真在被扫（读空会让上面四条恒真）
        assertTrue("对照组失效：扫到的不是词典那一颗文件", source.contains("class DesktopGalleryTagLexicon"))
    }

    /** ⑩ 契约那一侧的形状：`artistNames` 拿不到时交空集而不是 null（Android `?: emptySet()` 同形）。 */
    @Test
    fun 契约口在库坏时仍按Android那形降级() {
        val blocker = File.createTempFile("dict40-blocker2-", "")
        val lexicon = DesktopGalleryTagLexicon(
            DesktopGalleryHandle(FakePaths(File(blocker, "db")), FakeHttpEngine()),
        )
        val translations = runBlocking { lexicon.translations(listOf("blue_eyes")) }
        val artists = runBlocking { lexicon.artistNames(listOf("blue_eyes")) }
        val titles = runBlocking { lexicon.titleTags(listOf("blue_eyes")) }
        blocker.delete()
        assertTrue("契约的 translations 不带 null，库坏了就该交空映象", translations.isEmpty())
        assertTrue(artists.isEmpty())
        assertNull("titleTags 的 null 是'库没打开'，与'这批名字里没有'分开", titles)
    }

    /**
     * ⑪ 构建脚本那一跳：include 清单没带上那颗资产，判读层再对也读不到（`-Pdetail=` 当年就漏在这一跳）。
     *
     * 这条与 `DesktopKeyNavigationTest` ⑨ 是同一族：**扫构建脚本**。classpath 上有没有那颗资源是
     * `processResources` 的输出决定的，而它的输入就是这张 include 清单 —— 只扫消费端永远看不出漏了这一跳。
     */
    @Test
    fun 构建脚本把词典资产带进include清单() {
        val gradle = java.io.File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        val block = gradle.substringAfter("resources.include(").substringBefore(")")
        assertTrue(
            "include 清单没带 `gallery_tags_*.sqlite`（词典进不了包，桌面只能报'没打开'）：$block",
            block.contains("gallery_tags_*.sqlite"),
        )
        // 对照组：读到的确实是那颗清单而不是文件里另一处 "include("（读空会让上面那条恒真）。
        assertTrue("对照组失效：这一截不像 resources.include 的那颗清单", block.contains("sources/*.js"))
    }

    /**
     * ⑫ 批 38/39 接的那三处消费点仍从词典口取名字，而**名字从哪儿来**也要钉住
     * （生产端换成真跑了，接线一格都不许漂；只钉调用点的话，递一个空名单过去也是"接好了"）。
     */
    private data class Sample(val name: String, val cn: String?, val category: Int)

    /** 命中才有值；两形"没读到"交 null（这一族的三态由 `DesktopDictionaryThreeStateTest` 钉）。 */
    private fun <T> DesktopDictRead<T>.valueOrNull(): T? = (this as? DesktopDictRead.Hit)?.value

    /** 从真表里按档位抽几行；[cnNull] 挑"有译名"或"没译名"那一片（档位分布实测 0/1/3/4/5 都有货）。 */
    private fun sampleRows(category: Int, limit: Int = 2, cnNull: Boolean? = null): List<Sample> {
        val cnClause = when (cnNull) {
            true -> " AND cn IS NULL"
            false -> " AND cn IS NOT NULL"
            null -> ""
        }
        val asset = DesktopTagDictionaryAsset.assetOf(DesktopTagDictionaryAsset.classpathListing()).valueOrNull()
            ?: error("包内没有词典")
        val copy = DesktopTagDictionaryAsset.materialize(
            FakePaths(Files.createTempDirectory("dict40-s-").toFile()), asset,
        )
        JdbcSqliteDatabase.openReadOnly(copy).use { db ->
            return db.query(
                "SELECT name, cn, category FROM tags WHERE category = ?$cnClause ORDER BY name LIMIT $limit",
                category,
            ).map { Sample(it.string("name")!!, it.string("cn"), it.long("category").toInt()) }
        }
    }

    private fun samplePairFromDictionary(left: Int, right: Int): Pair<String, String> {
        val a = sampleRows(left, 1).single()
        val b = sampleRows(right, 1).single()
        return a.name to b.name
    }

    private companion object {
        const val DESKTOP_DICT_PREFIX = "gallery_tags_"
        const val DESKTOP_DICT_DB_DIR = "db"
    }
}
