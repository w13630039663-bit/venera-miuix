package com.venera.compose.feature.settings

import androidx.compose.runtime.*
import com.venera.compose.components.rememberComicListDisplayMode

/**
 * 对照 explore_settings.dart：漫画卡片 → 页面 → 屏蔽 → 搜索与默认值。
 *
 * 2026-09-19 设置审计后删掉 7 条不打算做的灰行（卡片大小、卡片收藏/历史徽章 ×2、
 * 三类页面排序存储、自动语言筛选）—— 它们各需要一套新配置链，不是"差一个键"。
 * 留下的灰行都属于近期可补：评论屏蔽接现有守卫、默认搜索目标/启动页/章节倒序各只差一个偏好。
 * 完整缺口见 settings-audit-2026-09.md。
 */
@Composable
internal fun ExploreSettings(onBack: () -> Unit, onSources: () -> Unit, onKeywords: () -> Unit) {
    var mode by rememberComicListDisplayMode()
    SettingsPage("探索", onBack) {
        SettingsGroup("漫画卡片") {
            SettingsSelect("漫画卡片显示模式", mode, listOf("detailed" to "详细（单列）", "brief" to "简洁（双列）"), { mode = it })
        }
        SettingsGroup("页面") {
            SettingsAction("搜索源", "在漫画源管理中启用或停用源；暂不支持独立搜索源排序", onClick = onSources)
        }
        SettingsGroup("屏蔽") {
            SettingsAction("关键词屏蔽", "管理标题关键词规则", onClick = onKeywords)
            UnsupportedSetting("评论关键词屏蔽", "评论过滤器尚未接入规则存储")
        }
        SettingsGroup("搜索与默认值") {
            UnsupportedSetting("默认搜索目标", "搜索目标目前仅在搜索会话中选择，尚无启动默认值偏好")
            UnsupportedSetting("启动页面", "启动导航尚未提供可配置偏好")
            UnsupportedSetting("默认倒序排列章节", "当前仅支持详情页会话内排序")
        }
    }
}
