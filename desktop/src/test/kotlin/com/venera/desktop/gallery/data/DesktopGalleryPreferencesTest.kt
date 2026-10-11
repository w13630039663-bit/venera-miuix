package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.gallery.domain.GalleryAnimatedMode
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GalleryViewerBackdrop
import com.venera.desktop.gallery.ui.DesktopSourceTree
import com.venera.desktop.platform.FakePaths
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面侧那 12 枚画廊偏好的对账。
 *
 * ## 这颗类为什么现在必须存在
 *
 * `DesktopGalleryPreferences.kt` 的类头注与键名区都在承诺一件事 ——
 * 「改这里之前先改那里，否则 `DesktopGalleryPreferencesTest` 会红」。而**这颗类此前从来没有存在过**
 * （`desktop/src/test/` 下没有 `gallery/data/` 这一档目录，全仓也搜不到类声明），
 * 于是那句"会红"是一张开不出来的支票：键名与默认档两头重述，漂了没人管。
 * 本批桌面又真的开始消费其中一枚（`galleryBlockAi` 进了墙上那一关，见 §18），
 * 所以支票要兑现成用例，不是把注释里那句承诺删掉了事。
 *
 * ## 两条腿：文本回核 + 真跑
 *
 * 文本回核（①②③⑦）钉的是"重述会不会漂"，**必须带对照组** —— 抽取器没抽到东西时
 * `contains` 的循环会恒真，所以 ① 先断言抽出来正好 10 枚。
 * 真跑（④⑤⑥）拿临时目录上的空存储与新实例各读一遍，钉的是解码口径与那两枚常量的行为。
 *
 * 两个漂移方向各有各的腿：**对岸**（Android）把默认档改了由 ② 抓，**桌面这边**把默认档改漂了
 * 由 ④ 抓 —— ② 只看对岸的文本，抓不到桌面侧的单方面改动，别以为一条就够。
 */
class DesktopGalleryPreferencesTest {

    private fun prefs(dir: File) = DesktopGalleryPreferences(FakePaths(dir))

    private fun tmp(tag: String) = File(System.getProperty("java.io.tmpdir"), "r1f-gp-$tag-${System.nanoTime()}")

    /** 桌面文件里重述的键名（const 名 → 键串），从源码现抽，不在测试里再抄一份常量。 */
    private fun desktopKeys(): List<Pair<String, String>> =
        Regex("""const val (KEY_[A-Z_]+) = "(pref_[a-z_]+)"""")
            .findAll(DesktopSourceTree.codeText(DESKTOP_PREFS))
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

    /** ① 十枚键名逐字回核 Android 侧，且带"抽取器真的抽到了"这一条对照。 */
    @Test
    fun 键名逐枚能在Android侧找到同一句() {
        val android = DesktopSourceTree.codeText(ANDROID_PREFS)
        val keys = desktopKeys()
        // 对照组：桌面这边正好重述 10 枚（12 枚里那两枚 hide_* 给常量、不重述键名 —— 见 ⑦）。
        // 少了就是有人新增了键却没登记，多了就是有人把不该重述的键抄了过来。
        assertEquals(
            "桌面重述的键名数量变了（现在是 ${keys.size} 枚：${keys.joinToString { it.second }}）—— " +
                "新增一枚偏好要同时决定「桌面读不读它」，读了才重述键名",
            10, keys.size,
        )
        for ((name, key) in keys) {
            assertTrue("桌面重述的键串 \"$key\"（$name）在 Android 侧找不到同一句", android.contains("\"$key\""))
        }
    }

    /** ② 默认档逐枚回核（`galleryAnimated` 那枚是**已登记的刻意分档**，走 ③）。 */
    @Test
    fun 默认档逐枚与Android侧同一句() {
        val android = DesktopSourceTree.codeText(ANDROID_PREFS)
        val shared = listOf(
            "KEY_GALLERY_COLUMNS" to "GalleryColumnMode.AUTO",
            "KEY_GALLERY_PREVIEW" to "GalleryPreviewQuality.PREVIEW",
            "KEY_GALLERY_KEEP_SCREEN_ON" to "true",
            "KEY_GALLERY_VOLUME_KEY" to "false",
            "KEY_GALLERY_AUTOPLAY_SEC" to "0",
            "KEY_GALLERY_PRELOAD" to "GalleryPreloadMode.NEXT",
            "KEY_GALLERY_BACKDROP" to "GalleryViewerBackdrop.GLASS",
            "KEY_GALLERY_BLOCK_AI" to "false",
            "KEY_GALLERY_AI_BADGE" to "true",
        )
        for ((androidKey, default) in shared) {
            assertTrue(
                "Android 侧那枚的默认档不再是 $default（$androidKey）—— 桌面这边照旧写着同一档，两边已经漂了",
                android.contains("$androidKey, $default)"),
            )
        }
        // 对照：这 9 枚的桌面侧默认档也要真在桌面文件里（有人改成硬编码别的值就该红，而不是恒真）。
        val desktop = DesktopSourceTree.codeText(DESKTOP_PREFS)
        assertTrue("桌面侧没在 readEnum 的默认档位置写 GalleryColumnMode.AUTO", desktop.contains("GalleryColumnMode.AUTO"))
        assertTrue("桌面侧没在默认档位置写 GalleryPreviewQuality.PREVIEW", desktop.contains("GalleryPreviewQuality.PREVIEW"))
    }

    /**
     * ③ `galleryAnimated` 是**登记过的**分档：Android 默认 `WIFI_ONLY`，桌面默认 `ALWAYS`。
     *
     * 这一条不是漂移，是拍板（`DesktopGalleryPreferences.kt` 类注释里那条：`WIFI_ONLY` 的判据吃
     * `ConnectivityManager`，那颗在桌面排除清单里）。但它必须被**钉住**：将来谁把桌面也改成
     * `WIFI_ONLY`，桌面上就等于"永远静帧"，而那不是任何人的本意。
     */
    @Test
    fun animated那枚是登记过的分档而不是漂移() {
        val android = DesktopSourceTree.codeText(ANDROID_PREFS)
        val desktop = DesktopSourceTree.codeText(DESKTOP_PREFS)
        assertTrue(
            "Android 侧 galleryAnimated 的默认档变了（原本是 WIFI_ONLY）—— 桌面那枚是**照着它**登记的刻意分档，" +
                "对岸一动这里要重新判一次桌面该给哪档",
            android.contains("KEY_GALLERY_ANIMATED, GalleryAnimatedMode.WIFI_ONLY)"),
        )
        assertTrue("桌面侧 galleryAnimated 不再是 ALWAYS 了", desktop.contains("KEY_ANIMATED, GalleryAnimatedMode.ALWAYS)"))
        val dir = tmp("animated")
        assertEquals(GalleryAnimatedMode.ALWAYS, prefs(dir).galleryAnimated.value)
        dir.deleteRecursively()
    }

    /** ④ 空存储（用户从没进过设置页）下，十枚读出的就是默认档 —— 真跑，不看文本。 */
    @Test
    fun 空存储下逐枚落到默认档() {
        val dir = tmp("empty")
        val p = prefs(dir)
        assertEquals(GalleryColumnMode.AUTO, p.galleryColumnMode.value)
        assertEquals(GalleryPreviewQuality.PREVIEW, p.galleryPreviewQuality.value)
        assertTrue("keepScreenOn 默认开：看图看到一半屏幕黑了，观感上等同于图没加载出来", p.galleryKeepScreenOn.value)
        assertEquals(false, p.galleryVolumeKeyTurn.value)
        assertEquals(0, p.galleryAutoPlaySec.value)
        assertEquals(GalleryPreloadMode.NEXT, p.galleryPreload.value)
        assertEquals(GalleryViewerBackdrop.GLASS, p.galleryBackdrop.value)
        assertEquals(false, p.galleryBlockAi.value)
        assertEquals(true, p.galleryAiBadge.value)
        dir.deleteRecursively()
    }

    /** ⑤ 解码口径照 Android：存枚举名，认不出来就退默认档，**不许退成 null 或抛**。 */
    @Test
    fun 枚举存坏名字退默认档而存对名字要真生效() {
        val dir = tmp("enum")
        JsonKeyValueStore(PreferenceKeys.PREFS_NAME, FakePaths(dir)).put(
            DesktopGalleryPreferences.KEY_PRELOAD,
            "TOMORROW",
        )
        assertEquals(
            "存了个认不出来的枚举名（TOMORROW）—— Android 侧那一半是退默认档，桌面退成别的就等于同一份 prefs 两端不同解",
            GalleryPreloadMode.NEXT, prefs(dir).galleryPreload.value,
        )
        JsonKeyValueStore(PreferenceKeys.PREFS_NAME, FakePaths(dir)).put(
            DesktopGalleryPreferences.KEY_PRELOAD,
            GalleryPreloadMode.OFF.name,
        )
        assertEquals("存了合法档位却没读回来", GalleryPreloadMode.OFF, prefs(dir).galleryPreload.value)
        dir.deleteRecursively()
    }

    /**
     * ⑥ 那两枚 hide_* 在桌面**不参与**：给的是常量，存储里写 true 也不动。
     *
     * 为什么值得跑一遍：类注释说"给常量而不是给一颗能写却没人读的键"。这一句要能红一次才算数 ——
     * 若哪天有人接了个写口却没接读口，屏上就会出现一颗按下去什么都不发生的开关（本仓最忌的假开关）。
     */
    @Test
    fun hide两枚给常量而存储写了true也不生效() {
        val dir = tmp("hide")
        val store = JsonKeyValueStore(PreferenceKeys.PREFS_NAME, FakePaths(dir))
        store.put("pref_gallery_hide_top_bar", true)
        store.put("pref_gallery_hide_bottom_bar", true)
        val p = prefs(dir)
        assertEquals("桌面没有顶栏可收（P5 拍板走侧栏），这一枚必须恒 false", false, p.galleryHideTopBar.value)
        assertEquals("桌面没有底栏可收（P5 拍板走侧栏），这一枚必须恒 false", false, p.galleryHideBottomBar.value)
        dir.deleteRecursively()
    }

    /**
     * ⑦ 不参与的那两枚**不许把键名抄过来**：一个字节都不读的键名就是会漂的死键。
     *
     * 对照组在同一个文件里：参与的那十枚的键串（拿 `pref_gallery_block_ai` 作样）必须在。
     * 只有"该出现的真出现、不该出现的真不出现"两条同时成立，这条负向断言才不是一颗空枪。
     */
    @Test
    fun 不参与的两枚不重述键名而参与的重述着() {
        val text = DESKTOP_PREFS.readText()
        assertTrue("桌面无故重述了 hide_top_bar 的键名 —— 那两枚给的是常量，抄键名就是留一颗死键", !text.contains("pref_gallery_hide_top_bar"))
        assertTrue("桌面无故重述了 hide_bottom_bar 的键名 —— 同上", !text.contains("pref_gallery_hide_bottom_bar"))
        assertTrue("对照组失效：连参与的那枚（pref_gallery_block_ai）都没在这颗文件里 —— 抽取或路径坏了", text.contains("pref_gallery_block_ai"))
    }

    /**
     * ⑧ 八枚 setter 写下去，**同一个实例**要立刻读到新值（流也得跟着走）。
     *
     * 这一条钉的是设置页的"改完就生效"：Android 那侧 `setGalleryXxx` 是"写 store + 更新那条流"两步，
     * 少第二步的结果是"存下来了，但屏上要重启才变" —— 用户会以为那一颗没反应。
     */
    @Test
    fun 写侧同实例立刻读到新值() {
        val dir = tmp("write")
        val p = prefs(dir)
        p.setGalleryColumnMode(GalleryColumnMode.THREE)
        // StateFlow.value 就是那条流的当前值 —— 屏上 collectAsState 读的是同一个，
        // 所以"写后 .value 变了"这句话本身就等于"那一墙会重算"。
        assertEquals("流没跟着更新 —— 屏上那一墙不会重算", GalleryColumnMode.THREE, p.galleryColumnMode.value)
        p.setGalleryPreviewQuality(GalleryPreviewQuality.LARGE)
        assertEquals(GalleryPreviewQuality.LARGE, p.galleryPreviewQuality.value)
        p.setGalleryBlockAi(true)
        assertEquals(true, p.galleryBlockAi.value)
        p.setGalleryAiBadge(false)
        assertEquals(false, p.galleryAiBadge.value)
        // 批 46 的第五枚：写进去要立刻从**同一条流**读回（集成方那段连播读的就是这条）
        p.setGalleryAutoPlaySec(10)
        assertEquals(10, p.galleryAutoPlaySec.value)
        // 越界的两端夹在对岸那条区间里 —— 写口的另一半不是只有档位按钮
        p.setGalleryAutoPlaySec(999)
        assertEquals(30, p.galleryAutoPlaySec.value)
        p.setGalleryAutoPlaySec(-5)
        assertEquals(0, p.galleryAutoPlaySec.value)
        // 批 52 的第七枚：动图。写 NEVER 要立刻从**同一条流**读到 NEVER ——
        // 集成方那颗播放器吃的就是这条流（`GalleryMotion.animates` 的实参），慢一拍就是"关了还在动"。
        p.setGalleryAnimated(GalleryAnimatedMode.NEVER)
        assertEquals("流没跟着更新 —— 大图那一屏会照旧逐帧播", GalleryAnimatedMode.NEVER, p.galleryAnimated.value)
        p.setGalleryAnimated(GalleryAnimatedMode.ALWAYS)
        assertEquals(GalleryAnimatedMode.ALWAYS, p.galleryAnimated.value)
        // 批 61 的第八枚：那层底。浮层里那一层 `background` 吃的就是这条流，慢一拍就是"换了档底不动"。
        p.setGalleryBackdrop(GalleryViewerBackdrop.BLACK)
        assertEquals("流没跟着更新 —— 大图那一屏的底不会重画", GalleryViewerBackdrop.BLACK, p.galleryBackdrop.value)
        dir.deleteRecursively()
    }

    /** ⑨ 换一个实例读回（模拟重启应用）—— 写侧必须真落盘，不是只活在内存里。 */
    @Test
    fun 写侧真落盘重启后还在() {
        val dir = tmp("persist")
        prefs(dir).apply {
            setGalleryColumnMode(GalleryColumnMode.TWO)
            setGalleryBlockAi(true)
        }
        val reopened = prefs(dir)
        assertEquals("换了实例就读回默认档 —— 那这一屏改的东西重启即丢", GalleryColumnMode.TWO, reopened.galleryColumnMode.value)
        assertEquals(true, reopened.galleryBlockAi.value)
        // 没被写过的两枚仍该是默认档（不能因为写了一枚就把整本表当成"已配置"）
        assertEquals(GalleryPreviewQuality.PREVIEW, reopened.galleryPreviewQuality.value)
        dir.deleteRecursively()
    }

    /**
     * ⑩ 桌面这边**只有那八枚**有写口。
     *
     * 为什么值得反射来钉：多出来的 setter 不会让任何现有测试变红，它只会在那天被接进设置页时
     * 变成一颗"能改但没人读"的假开关 —— 与本仓最忌的那个形状同源。
     * 今天有消费点的是八枚（列数 / 清晰度 / 屏蔽 AI / AI 角标，见手册 §19；连播是批 46 接的，
     * 预取是批 47 接的，动图那枚的**读口**一直是通的、**消费点**是批 51 那颗播放器，写口随批 52 补上；
     * 第八枚 backdrop 的读口从来就通，消费点与写口一起由批 61 补上 —— 那一层底今天真吃那枚档）。
     * 要再加一枚，先加消费点，再改这一条。
     */
    @Test
    fun 只有那八枚有写口() {
        val setters = DesktopGalleryPreferences::class.java.methods
            .map { it.name }
            .filter { it.startsWith("setGallery") }
            .sorted()
        assertEquals(
            "写口数目变了：$setters —— 加一枚写侧之前先答得出「这一枚今天被谁读」",
            listOf(
                "setGalleryAiBadge",
                "setGalleryAnimated",
                "setGalleryAutoPlaySec",
                "setGalleryBlockAi",
                "setGalleryColumnMode",
                "setGalleryPreload",
                "setGalleryPreviewQuality",
                // 批 61 加的第八枚：消费点是 `DesktopGalleryViewer` 里那一层底（走 `desktopBackdropColor`），
                // 屏上那一行与四档标签在 `DesktopGallerySettingsSpec` —— 这一条按它自己写的规矩改：先有消费点。
                "setGalleryBackdrop",
            ).sorted(),
            setters,
        )
        // 对照组：那两枚给常量的档**不该**有写口（有就是"能写却恒假"）。
        assertTrue("hide 那两枚不该有写口", setters.none { it.contains("Hide") })
    }

    companion object {
        private val DESKTOP_PREFS = File(
            DesktopSourceTree.repoRoot,
            "desktop/src/main/kotlin/com/venera/desktop/gallery/data/DesktopGalleryPreferences.kt",
        )
        private val ANDROID_PREFS = DesktopSourceTree.androidSource("data/prefs/VeneraPreferences.kt")
    }
}
