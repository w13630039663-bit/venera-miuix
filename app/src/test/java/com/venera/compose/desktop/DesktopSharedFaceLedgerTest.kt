package com.venera.compose.desktop

import com.venera.compose.testsupport.DesktopFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面**共享面**的账本：`:app` 借给 `:desktop` 的那几颗目录里，"哪些文件上得了桌面"必须是
 * 一份**对得上的账**，而不是 gradle 里写着、没人核过。
 *
 * ## 为什么 `:app` 那几套边界守卫管不到这件事
 *
 * `BusinessApiBoundaryTest` / `DebtReadinessTest` / `LayeringEdgeTest` 扫的是 `app/src/main`
 * 的**业务穿透**；而"一颗文件能不能进桌面编译面"是另一件事，`desktop/` 那边今天零判据。
 * 最坏的两个形态编译器都只说一半：
 * - 新增一颗带 `data.prefs` 的文件而忘了排除 ⇒ `:desktop:compileKotlin` 炸，但读数是一句
 *   `Unresolved reference`，说不出"这颗为什么上不去"；
 * - 那颗已经脱离 Android 了、排除行还挂着 ⇒ **一声不响**。清单只涨不消，最后没人敢碰。
 * 所以本条按"好消息也必须有人显式确认"的纪律**双向**钉（口径抄 `BusinessApiBoundaryTest` 的 E 条）。
 *
 * ## 判据是**禁引集合**，不是 `^import android`
 *
 * 反例是我自己量出来的第一遍：`GalleryImageLoader.kt` 只 import 了一句 `android.content.Context`，
 * 看起来"换个平台件就能上桌面"，而真正把它钉死的是它间接吃的 `data.prefs` + `data.network`
 * （`VeneraImageFetcher` / `ImageFetchCallFactory`）。只看 android import 会**一边**把它误判成
 * "该进却没进"、**一边**对这类间接绑定视而不见。
 */
class DesktopSharedFaceLedgerTest {

    @Test
    fun `禁引文件与排除清单必须双向对得上`() {
        // 2026-10-20：两处按 [DesktopFace.isExcluded] 走 —— 排除清单现在有两种写法
        // （裸文件名 / glob），而 glob 那一档解析器原先直接丢弃，导致"gradle 真排掉了、
        // 而账本里算作没排"的假红。判据的**意图**一字未改：双向对账，一条不许少。
        val all = DesktopFace.candidateFiles
        val offenders = all.filter { f -> FORBIDDEN.any { it.containsMatchIn(f.readText()) } }
        val names = all.map { it.name }.toSet()
        // 只拿**裸文件名**那一档对账 ②③：文件 glob 的覆盖范围由 `isExcluded` 判（①已经用了），
        // 而"清单里那颗名字在 srcDir 下不存在"这条只对裸名有意义（glob 本来就不该是文件名）。
        val excludes = DesktopFace.excludedFileNames

        val missing = offenders.filterNot { DesktopFace.isExcluded(it) }.map { it.name }.sorted()
        val stale = (excludes - offenders.map { it.name }.toSet()).filter { it in names }.sorted()
        val dead = (excludes - names).sorted()

        assertEquals(
            "① 有禁引却没点名排除（桌面编不过，而编译器说不清成因）：$missing\n" +
                "② 已无禁引成因却还排着（该摘行，摘了才算真共享）：$stale\n" +
                "③ 排除清单里的名字在 srcDir 下不存在（改名或删掉了没人摘行）：$dead",
            emptyList<String>(),
            missing + stale + dead,
        )
    }

    /**
     * 每颗被排出的文件都要说得出**被哪一族禁引挡住**。
     *
     * 与上面第②条看着重复，钉的是两种不同的漂：②管"禁引没了、行还挂着"，这条管
     * "有人往禁引集合里加/减一族"—— 那会让整张清单的口径一夜之间换个意思，
     * 而 `:desktop:compileKotlin` 照样绿（少一族判据不报错，只会让该排的没排上）。
     *
     * 2026-10-20：解析含 glob 的条目（`**` 那种点名子目录里单颗文件的写法）。
     * 那条 glob 在 [DesktopFace.candidateFiles] 里按**全路径**回查，而不是按裸文件名查表。
     */
    @Test
    fun `每颗被排出的文件都要给得出一条禁引成因`() {
        // 只核**文件级**排除项（裸文件名 + 文件 glob）。目录级 glob（`android` 那一族）
        // 不点名任何一颗具体文件，它的成因写在 gradle 里那两行注释上，
        // 由 `candidateFiles` 的 hasAndroidParent() 生效 —— 拉进来会让这条去找一颗
        // 名叫 `android` 的 .kt 而永远找不到（假红）。
        val fileEntries = DesktopFace.excludeEntries().filter { entry ->
            !entry.name.contains('*') || entry.name.endsWith(".kt")
        }
        // 2026-10-20 追加**第二类成因**（目标③ 挂 gallery/ui 时发现的）：
        //
        // 原先这条只认一种成因 —— "自己踩了禁引族"。但把 `gallery/ui` 挂进来时出现了一类
        // 真实情况：某颗文件**自己一行 Android 都不沾**，却 import 了同包里被排除的兄弟
        // （`GalleryForYouScreen` 要 `GalleryCardsGrid`、`GalleryViewerToolbar` 要分享条…）。
        // 那种文件单独挂进来照样编不过，成因是"**依赖了被排除的兄弟**"，不是"自己踩禁引"。
        //
        // 所以这里认两类，第三类一律红：
        //   A. 自己命中 `FORBIDDEN` 任一族；
        //   B. 自己没命中，但它**用到了**某个也在排除清单里的同包符号 ——
        //      判据是"把它挂进来的那一刻，它依赖的名字不在编译面里"。
        // 判据强度没有放松：A 之外多出来的每一个都必须在 B 里找到**具体的那一个名字**才算数，
        // 只是"依赖谁"这件事现在会被逐一写进报错文案里。
        //
        // ⚠️ B 认的是**标识符引用**而不是 `import` 行：Kotlin 同包引用**不需要 import**，
        // 而这一批的真实成因恰好全是同包（`GalleryForYouScreen` 用 `GalleryCardsGrid`、
        // `GalleryViewerToolbar` 用分享条 —— 都在同一个 `gallery.ui` 包里）。
        // 第一版按 `import` 查，于是"同包依赖"这一整类被判成零成因（假红）。
        //
        // ⚠️ 名单从 **gradle 清单**取而不是从 `candidateFiles` 反推：
        // 清单里有 `gallery/domain/GalleryNotices.kt` 这种**别的 srcDir** 的文件，
        // 只扫 `gallery/ui` 那棵树会漏掉它。
        val excludedStems = fileEntries
            .map { it.name.substringAfterLast('/').removeSuffix(".kt") }
            .toSet()
        // 名字 → 它属于哪个被排除的文件（用于报错时点名"依赖了谁"）
        val excludedSymbols: Map<String, String> = DesktopFace.candidateFiles
            .filter { it.name.removeSuffix(".kt") in excludedStems }
            .flatMap { f ->
                TOP_LEVEL_DECL.findAll(f.readText()).map { m ->
                    m.groupValues[1] to f.name
                }.toList()
            }
            .toMap()

        val unexplained = fileEntries.mapNotNull { entry ->
            val files = if (entry.name.contains('*')) {
                DesktopFace.candidateFiles.filter { matchesPattern(entry.name, it) }
            } else {
                DesktopFace.candidateFiles.filter { it.name == entry.name }
            }
            if (files.isEmpty()) {
                return@mapNotNull "${entry.name}（gradle 第 ${entry.lineNo} 行点了这颗，srcDir 里没有）"
            }
            val first = files.first()
            if (FORBIDDEN.any { it.containsMatchIn(first.readText()) }) {
                null
            } else {
                // B 类：找它**用到了**哪个被排除的兄弟（同包引用不带 import，所以查标识符）
                val text = first.readText()
                val self = first.name.removeSuffix(".kt")
                val dep = excludedSymbols.entries.firstOrNull { (sym, owner) ->
                    owner != first.name && Regex("\\b" + Regex.escape(sym) + "\\b").containsMatchIn(text)
                }
                if (dep != null) {
                    null
                } else {
                    "${DesktopFace.relOf(first)}（gradle 第 ${entry.lineNo} 行）"
                }
            }
        }
        assertTrue("这些排除项给不出一条禁引成因：$unexplained", unexplained.isEmpty())
    }

    /** 顶层声明名（`object` / `class` / `interface` / `fun` / `val`），用于 B 类成因判据。 */
    private val TOP_LEVEL_DECL = Regex(
        """(?m)^(?:internal\s+|private\s+|public\s+)?""" +
            """(?:object|class|interface|fun|val)\s+([A-Za-z_]\w*)""",
    )

    /** glob 条目是否覆盖某颗文件（只用来"找回那颗文件"，判据仍是禁引集合）。 */
    private fun matchesPattern(pattern: String, file: java.io.File): Boolean {
        val rel = runCatching {
            file.relativeTo(com.venera.compose.testsupport.RepoSources.root).path.replace('\\', '/')
        }.getOrNull() ?: return false
        val simple = pattern.removePrefix("**/")
        return rel.endsWith(simple)
    }

    private companion object {
        /**
         * 禁引集合：上了桌面就得重写第二份的那些绑定。
         *
         * ## ⚠️ 2026-10-20 改判（写法 A，用户裁决「按照大屏端复用」）
         *
         * 这里原先有一条 `^import androidx\.` —— 把**整个** androidx 命名空间当作
         * "Android 运行时"禁掉。那条在"桌面要装一套 Android 运行时"的语境下是对的，
         * 但对 Compose Multiplatform **不成立**：`androidx.compose.*` 是 CMP 的包名，
         * 桌面侧用的就是同一批（`org.jetbrains.compose.*` 的产物里包名仍是 `androidx.compose.*`）。
         * 于是本轮把 `components/`（48 颗）接进桌面时，那 54 颗文件**全被判成"该排除而未排除"**，
         * 而它们其实一颗都编得过 —— 判据把"共享成功"读成了"漂移"。
         *
         * 改判后的口径**更准而不是更松**：
         *  - 仍禁 `android.*`（Android SDK 本体，真没有桌面对应物）；
         *  - 仍禁 `coil`（**故意**保留：`coil3` 是跨平台的，但 `components/` 走的是
         *    `UxPlatformServices.imageLoader()` 拿实例，不该直接 import 平台取法）；
         *  - 仍禁 `data.prefs` / `data.network` / `*.android.*`（间接绑定，口径未变）；
         *  - **新禁** `androidx.activity` / `androidx.lifecycle` / `androidx.core`
         *    （这三族是真·Android 宿主：`PredictiveBackHandler`、`ViewModel`、`WindowCompat`
         *    —— `PredictiveBack.kt` 正是被头一条挡住的那颗）；
         *  - 放行 `androidx.compose.*`（CMP 跨平台，两端同包名）。
         *
         * 一句话：禁的是**平台宿主运行时**，不是**Compose 的包名**。
         */
        val FORBIDDEN = listOf(
            Regex("^import android\\.", RegexOption.MULTILINE),
            // 真·Android 宿主三族（逐族点名，不用 `^import androidx\\.` 一把抓 ——
            // 那一把会把 `androidx.compose.*` 也扫进来，理由见上）。
            Regex("^import androidx\\.activity", RegexOption.MULTILINE),
            Regex("^import androidx\\.lifecycle", RegexOption.MULTILINE),
            Regex("^import androidx\\.core", RegexOption.MULTILINE),
            // `coil` 族**不是**一刀切禁掉 —— 2026-10-20 收紧成逐族点名。
            //
            // 原条目是 `^import coil`，本意（见 `desktop/build.gradle.kts` 那段注释）是挡住
            // "看起来只 import 了一句 Context、真正被间接绑死"的那类文件：
            // `GalleryImageLoader.kt` 吃 `data.prefs` 的 `VeneraImageFetcher` +
            // `data.network` 的 `ImageFetchCallFactory`。那两族禁引在上面两条，仍然在。
            //
            // 但 `coil3.compose.AsyncImage` / `coil3.ImageLoader` / `coil3.compose.SubcomposeAsyncImage`
            // 是**跨平台**的（`io.coil-kt.coil3` 本身是 KMP 库，桌面侧也在用它取图，
            // 见 `desktop/build.gradle.kts` 的 `libs.coil.compose`）。一刀切会把
            // `VeneraCover` / `ComicTileLayout` / `CoverHeroBackdrop` / `RichCommentContent`
            // 四颗正常的跨端组件误判成"该排除"。
            //
            // 真正该禁的是**取平台单例**那一条路（`coil3.imageLoader` 扩展 / `SingletonImageLoader.get(context)`）——
            // 那条已经统一收口到 `UxPlatformServices.imageLoader()`，共享面不该再直接碰。
            Regex("^import coil3\\.(imageLoader|SingletonImageLoader)", RegexOption.MULTILINE),
            // `coil3.gif`：`AnimatedImageDecoder` 背后是 `android.graphics.Bitmap` + `ImageDecoder`，
            // 桌面侧没有那颗件。命中点是 `gallery/data/GalleryAnimationGate.kt`（动图闸门），
            // 而那颗本来就按裸文件名排除着 —— 2026-10-20 把 `^import coil` 收窄后，
            // 这条是补回来的**那一段真正的手臂**（不是把整族再禁回去：
            // `coil3.compose.AsyncImage` 一类跨平台件必须放行）。
            Regex("^import coil3\\.gif", RegexOption.MULTILINE),
            Regex("^import com\\.venera\\.compose\\.data\\.prefs\\.", RegexOption.MULTILINE),
            Regex("^import com\\.venera\\.compose\\.data\\.network\\.", RegexOption.MULTILINE),
            Regex("^import com\\.venera\\.compose\\.[a-z0-9_.]*\\.android\\.", RegexOption.MULTILINE),
            // AGP 生成的资源索引类（`R.drawable.*` / `R.string.*`）。桌面侧没有这套资源管线，
            // 它由 `:app` 的 `processDebugResources` 生成 ⇒ 共享面 import 它必炸。
            // 2026-10-20 登记：`components/venera/VeneraGallerySourceMark.kt` 是唯一命中点
            // （四颗站点位图），已按 glob 排除；报错文案会指向那一行。
            Regex("^import com\\.venera\\.compose\\.R\\b", RegexOption.MULTILINE),
        )
    }
}
