package com.venera.compose.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 「选择追更收藏夹」弹层。
 *
 * 从 `FollowUpdatesScreen` 里搬出来共用：收藏页「更新」段与追更页设置是同一个动作，
 * 两处各写一份就会在"取消时要不要清空追更夹"这种细节上分叉。
 */
@Composable
internal fun FollowFolderPicker(
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
    vm: FollowUpdatesViewModel = viewModel(),
) {
    val folders by vm.folderList().collectAsState(initial = emptyList())

    com.venera.compose.components.PredictiveBackOverlay(onDismiss = onDismiss) {
        Card(modifier = Modifier.padding(horizontal = 28.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "选择追更收藏夹",
                    fontSize = 17.sp,
                    fontWeight = VeneraTokens.type.weightSemibold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "选中的收藏夹会每天自动检查一次更新。",
                    fontSize = VeneraTokens.type.sectionTitle,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    folders.forEach { folder ->
                        val isCurrent = folder == current
                        Text(
                            text = if (isCurrent) "$folder ✔" else folder,
                            fontSize = 15.sp,
                            color = if (isCurrent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPick(if (isCurrent) null else folder)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                ) { Text("取消") }
            }
        }
    }
}
