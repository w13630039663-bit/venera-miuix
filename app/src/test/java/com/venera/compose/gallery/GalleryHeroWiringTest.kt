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
        val heroAt = body.indexOf("hero?.invoke()")
        val chromeAt = body.indexOf("chrome?.invoke()")
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
        assertTrue("搜索卡落点必须仍只等于 topBarFloor", screen.contains("val searchOverlayTop = topBarFloor"))
    }
}
