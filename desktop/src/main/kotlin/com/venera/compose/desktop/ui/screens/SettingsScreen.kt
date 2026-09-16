package com.venera.compose.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class SettingCategory(
    val title: String,
    val description: String,
    val icon: String,
    val badgeColor: Color
)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val settingCategories = listOf(
        SettingCategory("探索与主页配置", "自定义主页卡片流与探索数据源", "🧭", Color(0xFF2196F3)),
        SettingCategory("内容屏蔽与过滤", "标签屏蔽、屏蔽词与画师过滤", "🛡️", Color(0xFFE91E63)),
        SettingCategory("阅读器体验", "仿真翻页、瀑布流模式、双指手势缩放", "📖", Color(0xFF4CAF50)),
        SettingCategory("外观与动效", "MIUI 质感规范、高刷弹簧插值、深色模式", "🎨", Color(0xFF9C27B0)),
        SettingCategory("本地存储与缓存", "图片离线缓存、导出漫画包、清理缓存", "📦", Color(0xFFFF9800)),
        SettingCategory("网络与代理", "分流代理配置、Cloudflare 验证策略", "🌐", Color(0xFF009688))
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 顶部关于卡片 (About Card)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MiuixTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "V",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Venera Compose",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "版本 1.0.0 · 纯原生 MIUIX 极速架构",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { },
                            content = { Text("检查更新", fontSize = 12.sp) }
                        )
                        Button(
                            onClick = { },
                            content = { Text("GitHub 源码", fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        // 2. 设置功能分类列表
        item {
            SmallTitle(text = "系统设置分类")
        }

        items(settingCategories.size) { index ->
            val cat = settingCategories[index]
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(cat.badgeColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = cat.icon, fontSize = 18.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cat.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = cat.description,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }

                    Text(
                        text = "›",
                        fontSize = 20.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
            }
        }
    }
}
