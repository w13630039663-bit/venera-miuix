package com.venera.compose.feature.settings

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.venera.compose.components.rememberComicListDisplayMode
import com.venera.compose.data.api.BusinessPorts
import com.venera.compose.feature.SearchViewModel
import com.venera.compose.source.ComicSourceManager

/**
 * 对照 explore_settings.dart：漫画卡片 → 页面 → 屏蔽 → 搜索与默认值。
 *
 * 2026-09-19 设置审计后：默认搜索目标、启动页面、章节默认倒序从灰行转正；
 * 删掉 7 条需要新配置链的灰行（卡片大小、卡片收藏/历史徽章、三类页面排序存储、
 * 自动语言筛选）。2026-09-30 用户拍板"未实现项连标题一起撤"，
 * 评论关键词屏蔽这条也一并删掉了（原先在底部「尚未实现」折叠区里）。
 * 完整清单见 settings-audit-2026-09.md。
 */
@Composable
internal fun ExploreSettings(onBack: () -> Unit, onSources: () -> Unit, onKeywords: () -> Unit) {
    val context = LocalContext.current
    // 这一页横跨三个偏好域（默认搜索目标 / 启动页面 / 章节顺序），所以按域各取一份，
    // 而不是回去抓那颗 101 枚成员的实现类。
    val comicPrefs = remember(context) { BusinessPorts.of(context).comicPrefs }
    val appearancePrefs = remember(context) { BusinessPorts.of(context).appearancePrefs }
    val readerPrefs = remember(context) { BusinessPorts.of(context).readerPrefs }
    val sources by ComicSourceManager.getInstance(context).sourcesFlow.collectAsState()
    var mode by rememberComicListDisplayMode()
    val defaultTarget by comicPrefs.defaultSearchTarget.collectAsState()
    val startPage by appearancePrefs.startPage.collectAsState()
    val reverseChapters by readerPrefs.reverseChapterOrder.collectAsState()
    // 已装源可能很多，SettingsSelect 的对话框列表本身可滚动。
    val targetOptions = remember(sources) {
        listOf("" to "不预设，进去时保持上次选的") +
            listOf(SearchViewModel.KEY_ALL to "全部源") +
            sources.map { it.key to it.name }
    }
    SettingsPage("探索", onBack, largeTitle = "探索与卡片", heroSubtitle = "漫画源 · 关键词屏蔽") {
        SettingsGroup("漫画卡片") {
            SettingsSelect("漫画卡片显示模式", mode, listOf("detailed" to "详细（单列）", "brief" to "简洁（双列）"), { mode = it })
        }
        SettingsGroup("页面") {
            SettingsAction("搜索源", "启用或停用漫画源", onClick = onSources)
        }
        SettingsGroup("屏蔽") {
            SettingsAction("关键词屏蔽", "管理标题关键词", onClick = onKeywords)
        }
        SettingsGroup("搜索与默认值") {
            SettingsSelect(
                "默认搜索目标", defaultTarget, targetOptions, comicPrefs::setDefaultSearchTarget,
                summary = "进搜索页时先选好的源，不会自动开始搜索。",
            )
            SettingsSelect(
                "启动页面", startPage,
                listOf(
                    "HOME" to "首页", "FAVORITES" to "我的收藏",
                    "SEARCH" to "搜索与发现", "GALLERY" to "画廊", "EXPLORE" to "探索",
                ),
                appearancePrefs::setStartPage,
                summary = "下次打开应用时停在哪个标签页。",
            )
            // 2026-09-23 历史降回二级页 → 这一项同时撤掉「历史」选项：留着它就是假开关
            // （选了之后启动仍回首页）。老用户存的 "HISTORY" 不在上面的表里，
            // 启动侧按 Navigation.kt 的 getOrDefault(HOME) 回落首页，
            // 设置这行则显示现成的「未识别的已保存值：HISTORY」（SettingsComponents.kt:218），
            // 不做静默改写 —— 用户改过的偏好被动了要说得出来。
            SettingsToggle("默认倒序排列章节", reverseChapters, readerPrefs::setReverseChapterOrder,
                summary = "进详情页时的默认顺序，页面上的正序/倒序按钮还能临时改。")
        }
    }
}
