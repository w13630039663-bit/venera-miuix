package com.venera.compose.feature.settings

import androidx.compose.runtime.*
import com.venera.compose.components.rememberComicListDisplayMode

/** 对照 explore_settings.dart：漫画卡片 → 页面 → 屏蔽 → 搜索与默认值。 */
@Composable
internal fun ExploreSettings(onBack: () -> Unit, onSources: () -> Unit, onKeywords: () -> Unit) {
    var mode by rememberComicListDisplayMode()
    SettingsPage("探索", onBack) {
        SettingsGroup("漫画卡片") {
            SettingsSelect("漫画卡片显示模式", mode, listOf("detailed" to "详细（单列）", "brief" to "简洁（双列）"), { mode = it })
            UnsupportedSetting("漫画卡片大小", "当前列表未提供缩放偏好")
            UnsupportedSetting("在漫画卡片上显示收藏状态", "当前没有可配置的收藏标记开关")
            UnsupportedSetting("在漫画卡片上显示阅读历史", "当前没有可配置的历史标记开关")
        }
        SettingsGroup("页面") {
            UnsupportedSetting("探索页面", "当前没有页面筛选及排序存储")
            UnsupportedSetting("分类页面", "当前没有页面筛选及排序存储")
            UnsupportedSetting("网络收藏页面", "当前没有页面筛选及排序存储")
            SettingsAction("搜索源", "在漫画源管理中启用或停用源；暂不支持独立搜索源排序", onClick = onSources)
        }
        SettingsGroup("屏蔽") {
            SettingsAction("关键词屏蔽", "管理标题关键词规则", onClick = onKeywords)
            UnsupportedSetting("评论关键词屏蔽", "评论过滤器尚未接入规则存储")
        }
        SettingsGroup("搜索与默认值") {
            UnsupportedSetting("默认搜索目标", "搜索目标目前仅在搜索会话中选择，尚无启动默认值偏好")
            UnsupportedSetting("自动添加语言筛选", "搜索尚无持久化语言筛选策略")
            UnsupportedSetting("启动页面", "启动导航尚未提供可配置偏好")
            UnsupportedSetting("默认倒序排列章节", "当前仅支持详情页会话内排序")
        }
    }
}
