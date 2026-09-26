package com.venera.compose.feature.settings

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.venera.compose.components.rememberComicListDisplayMode
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.feature.SearchViewModel
import com.venera.compose.source.ComicSourceManager

/**
 * 对照 explore_settings.dart：漫画卡片 → 页面 → 屏蔽 → 搜索与默认值。
 *
 * 2026-09-19 设置审计后：默认搜索目标、启动页面、章节默认倒序从灰行转正；
 * 删掉 7 条需要新配置链的灰行（卡片大小、卡片收藏/历史徽章、三类页面排序存储、
 * 自动语言筛选）；评论关键词屏蔽移入「尚未实现」折叠区。
 * 完整清单见 settings-audit-2026-09.md。
 */
@Composable
internal fun ExploreSettings(onBack: () -> Unit, onSources: () -> Unit, onKeywords: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { VeneraPreferences.getInstance(context) }
    val sources by ComicSourceManager.getInstance(context).sourcesFlow.collectAsState()
    var mode by rememberComicListDisplayMode()
    val defaultTarget by prefs.defaultSearchTarget.collectAsState()
    val startPage by prefs.startPage.collectAsState()
    val reverseChapters by prefs.reverseChapterOrder.collectAsState()
    // 已装源可能很多，SettingsSelect 的对话框列表本身可滚动。
    val targetOptions = remember(sources) {
        listOf("" to "不预设（进入时保持上次选择）") +
            listOf(SearchViewModel.KEY_ALL to "全部源") +
            sources.map { it.key to it.name }
    }
    SettingsPage("探索", onBack, largeTitle = "探索与卡片") {
        SettingsGroup("漫画卡片") {
            SettingsSelect("漫画卡片显示模式", mode, listOf("detailed" to "详细（单列）", "brief" to "简洁（双列）"), { mode = it })
        }
        SettingsGroup("页面") {
            SettingsAction("搜索源", "在漫画源管理中启用或停用源；暂不支持独立搜索源排序", onClick = onSources)
        }
        SettingsGroup("屏蔽") {
            SettingsAction("关键词屏蔽", "管理标题关键词规则", onClick = onKeywords)
        }
        SettingsGroup("搜索与默认值") {
            SettingsSelect(
                "默认搜索目标", defaultTarget, targetOptions, prefs::setDefaultSearchTarget,
                summary = "进入搜索页时预选的源，只影响初始选择，不自动发起搜索。",
            )
            SettingsSelect(
                "启动页面", startPage,
                listOf(
                    "HOME" to "首页", "FAVORITES" to "我的收藏",
                    "SEARCH" to "搜索与发现", "GALLERY" to "画廊", "EXPLORE" to "探索",
                ),
                prefs::setStartPage,
                summary = "下次冷启动时停留的主标签。",
            )
            // 2026-09-23 历史降回二级页 → 这一项同时撤掉「历史」选项：留着它就是假开关
            // （选了之后启动仍回首页）。老用户存的 "HISTORY" 不在上面的表里，
            // 启动侧按 Navigation.kt 的 getOrDefault(HOME) 回落首页，
            // 设置这行则显示现成的「未识别的已保存值：HISTORY」（SettingsComponents.kt:218），
            // 不做静默改写 —— 用户改过的偏好被动了要说得出来。
            SettingsToggle("默认倒序排列章节", reverseChapters, prefs::setReverseChapterOrder,
                summary = "作为进入作品详情页时的初值；页面上的「正序/倒序」按钮仍可临时改。")
        }
        SettingsFutureGroup {
            UnsupportedSetting("评论关键词屏蔽", "评论过滤器尚未接入 content_guard_rules 规则存储。")
        }
    }
}
