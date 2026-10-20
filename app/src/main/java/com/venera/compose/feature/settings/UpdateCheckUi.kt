package com.venera.compose.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraDialog
import com.venera.compose.data.api.BusinessPorts
import com.venera.compose.data.update.ApkInstallLauncher
import com.venera.compose.data.update.AppUpdateChecker
import com.venera.compose.data.update.ChangelogBlock
import com.venera.compose.data.update.InstallOutcome
import com.venera.compose.data.update.LatestRelease
import com.venera.compose.data.update.ProjectChannel
import com.venera.compose.data.update.ReleaseAsset
import com.venera.compose.data.update.UpdateCheck
import com.venera.compose.data.update.UpdateDownload
import com.venera.compose.data.update.changelogBlocks
import com.venera.compose.data.update.pickApkAsset
import com.venera.compose.download.DownloadNotificationChannel
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 冷启动后多久才允许弹更新框（master 的 `checkUpdateUi(delay = true)` 同口径：不与首屏抢帧）。 */
private const val STARTUP_CHECK_DELAY_MS = 2_000L

/**
 * 弹层里长正文的滚动限高。380.dp **不是新造的数**：`reader/ChapterCommentsSheet.kt:163`
 * 的评论正文用的就是这个值，本仓"弹窗里的长文本"目前只有这一处先例，照它而不是再定一档。
 */
private val changelogMaxHeight = 380.dp

/** 进度条的粗细与圆角：照 `feature/DownloadScreen.kt:299-304` 那一条（5.dp / 3.dp）。 */
private val downloadBarHeight = 5.dp
private val downloadBarShape = 3.dp

/**
 * 「发现新版本」弹窗 —— 设置页手动检查与启动检查共用，**三态在一颗里**（2026-10-04 改造）。
 *
 * ## 原先那句「不在应用内自装」为什么不成立了
 *
 * 旧注释的理由是「本包与 master 分支用同一个 applicationId、不同签名，覆盖安装只会得到一句
 * 应用未安装」。签名这件事的权威读数是发布说明自己给的：v2.0.3 那版写着「可直接覆盖安装 v2.0 /
 * v2.0.2：同一把正式签名证书，versionCode 2002 → 2003」，四份产物的证书 SHA-256 逐字相同
 * （方案文档 §0.3 抄了那串）。也就是说**稳态下（本包的 release 装本包的 release）覆盖安装是通的**，
 * 那句注释管的是两种一次性场景 —— 从 Flutter 原版迁过来、以及自己 clone 构建的 debug 签名包
 * （`app/build.gradle.kts:39-73` 缺 `key.properties` 时确实回退 debug keystore）。
 * 这两种现在由 [InstallOutcome.SignatureMismatch] 在**装之前**说清楚，
 * 而不是拿它当理由把整条路封掉。
 *
 * ## 三态与两条分支
 *
 * 问（日志 + 更新 / 以后再说）→ 下（进度条 + 取消）→ 装（安装 / 稍后）；
 * 另外两条不是状态而是读数：安装被系统挡住（未授权、签名不同）与下载失败。
 * 每一条都要读得出下一步该干什么，失败那条必须带"哪个源、什么码"。
 *
 * 关闭对话框（点外面、返回）**不记**「以后再说」—— 只有那颗按钮记。
 * 下载进行中外层关闭也**不**取消：字节继续在跑，通知栏接着报，这才对得上"接入通知"。
 */
@Composable
internal fun UpdateAvailableDialog(
    release: LatestRelease,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val tokens = VeneraTokens
    val prefs = remember { BusinessPorts.of(context).comicPrefs }
    // 下载那一面走契约口，不在界面里 `getInstance`（判语：要开口就加契约，别直连）。
    val queue = remember { BusinessPorts.of(context).updates }
    val download by queue.state.collectAsState()

    val apk = remember(release) { pickApkAsset(release.apks, Build.SUPPORTED_ABIS.toList()) }
    var blocked by remember { mutableStateOf<InstallOutcome?>(null) }
    var startAfterPermission by remember { mutableStateOf(false) }

    // 通知权限只在用户点下「更新」那一刻问一次；答完（给不给）都照常开下 ——
    // 不给只是通知栏少一条，不该把更新本身扣住。
    val askNotification = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        if (startAfterPermission) {
            startAfterPermission = false
            apk?.let(queue::start)
        }
    }

    val beginDownload: () -> Unit = {
        val target = apk
        if (target != null) {
            if (DownloadNotificationChannel.canPost(context)) {
                queue.start(target)
            } else {
                startAfterPermission = true
                askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val downloading = download is UpdateDownload.Running || download is UpdateDownload.Verifying

    VeneraDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "发现新版本 ${release.version}",
        confirmText = when {
            blocked is InstallOutcome.NeedsUnknownSources -> "去授权"
            blocked != null -> null
            download is UpdateDownload.Ready -> "安装"
            download is UpdateDownload.Verifying -> "正在校验"
            download is UpdateDownload.Failed -> "重试"
            downloading -> null
            apk == null -> null
            else -> "更新"
        },
        onConfirm = {
            when {
                blocked is InstallOutcome.NeedsUnknownSources -> openUnknownSourcesSettings(context)
                download is UpdateDownload.Ready -> {
                    val file = (download as UpdateDownload.Ready).file
                    blocked = ApkInstallLauncher.launchInstall(context, file).takeIf { it != InstallOutcome.Launched }
                }
                else -> beginDownload()
            }
        },
        dismissText = when {
            blocked is InstallOutcome.NeedsUnknownSources -> "知道了"
            blocked != null -> "去发布页下载"
            download is UpdateDownload.Running -> "取消下载"
            download is UpdateDownload.Verifying -> null
            download is UpdateDownload.Ready -> "稍后"
            download is UpdateDownload.Failed -> "去发布页下载"
            apk == null -> "关闭"
            else -> "以后再说"
        },
        onDismiss = {
            val outcome = blocked
            when {
                outcome is InstallOutcome.NeedsUnknownSources -> blocked = null
                outcome != null -> openReleases(context)
                download is UpdateDownload.Running -> queue.cancel()
                download is UpdateDownload.Failed -> openReleases(context)
                // 已经下好了的"稍后"：文件留着，通知栏那条还在，所以不记「以后再说」。
                download is UpdateDownload.Ready -> onDismiss()
                else -> {
                    prefs.ignoredUpdateVersion = release.version
                    onDismiss()
                }
            }
        },
        buttonsEnabled = !downloading,
        content = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = downloadLine(apk, download),
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                )
                val running = download as? UpdateDownload.Running
                if (running != null) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(tokens.spacing.space4),
                    )
                    val done = running.downloadedBytes
                    val total = running.totalBytes
                    LinearProgressIndicator(
                        progress = { if (total > 0L) (done.toFloat() / total).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(downloadBarHeight)
                            .clip(RoundedCornerShape(downloadBarShape)),
                        color = tokens.color.primary,
                        trackColor = tokens.color.surfaceVariant,
                    )
                }
                ChangelogBody(release.changelog)
                blocked?.let { outcome ->
                    Text(
                        text = installBlockedText(outcome),
                        fontSize = tokens.type.body,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(top = tokens.spacing.space4),
                    )
                }
            }
        },
    )
}

/**
 * 顶部那一行状态话术。
 *
 * 刻意把"有没有核对过"写进这句话：拿不到官方指纹时（`digest` 缺失 → 只走官方直链），
 * 一句通用的"下载完成"会把两种不同的可信度盖成一样。
 */
private fun downloadLine(apk: ReleaseAsset?, download: UpdateDownload): String = when {
    apk == null -> "这一版没有适合本机的安装包，请到发布页手动选择"
    download is UpdateDownload.Running -> {
        val done = formatMib(download.downloadedBytes)
        val total = if (download.totalBytes > 0L) " / ${formatMib(download.totalBytes)}" else ""
        val speed = if (download.speedBytesPerSec > 0L) " · ${formatMib(download.speedBytesPerSec)}/秒" else ""
        "正在下载 $done$total$speed" + if (download.viaMirror) "，官方直链不通，改走加速镜像" else ""
    }
    download is UpdateDownload.Verifying -> "已下载 ${formatMib(apk.size)}，正在校验文件"
    download is UpdateDownload.Ready ->
        if (download.verified) "已下载并校验完成，可以安装"
        else "已下载，但发布页没有提供校验信息"
    download is UpdateDownload.Failed -> "没能下载成功：${download.reason}"
    else -> "安装包 ${formatMib(apk.size)}，来自 GitHub 发布页"
}

private fun installBlockedText(outcome: InstallOutcome): String = when (outcome) {
    InstallOutcome.NeedsUnknownSources ->
        "请先允许本应用安装应用。授权后返回这里点安装。"
    InstallOutcome.SignatureMismatch ->
        "安装包与当前版本签名不一致。需要先卸载旧版，卸载会删除收藏和历史，请先导出备份。"
    is InstallOutcome.Failed -> "系统安装页没起来：${outcome.reason}"
    InstallOutcome.Launched -> ""
}

private fun openUnknownSourcesSettings(context: Context) {
    val ok = runCatching {
        context.startActivity(ApkInstallLauncher.unknownSourcesSettingsIntent(context))
    }.isSuccess
    if (!ok) Toast.makeText(context, "打不开系统授权页，请在设置里手动允许「安装未知应用」", Toast.LENGTH_LONG).show()
}

private fun openReleases(context: Context) {
    val ok = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ProjectChannel.RELEASES_URL)))
    }.isSuccess
    if (!ok) Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
}

/**
 * 更新日志本体。Markdown 的记号已在 [changelogBlocks] 里剥掉，这里只决定"哪种块用哪一档现成字号"，
 * 不为日志另造字阶（层级靠字重与既有档位拉开，与 `feature/settings` 其他长文本同一口径）。
 */
@Composable
private fun ChangelogBody(changelog: String) {
    val tokens = VeneraTokens
    val blocks = remember(changelog) { changelogBlocks(changelog) }
    if (blocks.isEmpty()) return
    Column(
        modifier = Modifier
            .padding(top = tokens.spacing.space4)
            .fillMaxWidth()
            .heightIn(max = changelogMaxHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        blocks.forEach { block ->
            when (block) {
                is ChangelogBlock.Heading -> Text(
                    text = block.text,
                    fontSize = if (block.level == 1) tokens.type.sectionTitle else tokens.type.itemTitle,
                    fontWeight = if (block.level == 1) tokens.type.weightBold else tokens.type.weightSemibold,
                    color = tokens.color.textPrimary,
                    modifier = Modifier.padding(top = tokens.spacing.space4),
                )
                is ChangelogBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2)) {
                    Text(text = "·", fontSize = tokens.type.body, color = tokens.color.textTertiary)
                    Text(
                        text = block.text,
                        fontSize = tokens.type.body,
                        color = tokens.color.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
                is ChangelogBlock.Quote -> Text(
                    text = block.text,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(start = tokens.spacing.space4),
                )
                is ChangelogBlock.Paragraph -> Text(
                    text = block.text,
                    fontSize = tokens.type.body,
                    color = tokens.color.textPrimary,
                )
            }
        }
    }
}

/**
 * 启动时检查更新的宿主，挂在 `VeneraTheme` 之下、`VeneraComposeApp()` 之外 ——
 * 这样全应用只有一个弹窗位，也不必去改导航层。
 *
 * 静默路径只在"确实有新版本、而且用户没跳过这一版"时打扰（判据 [AppUpdateChecker.shouldAnnounce]）。
 */
@Composable
internal fun StartupUpdateHost() {
    val context = LocalContext.current
    val prefs = remember { BusinessPorts.of(context).comicPrefs }
    var shown by remember { mutableStateOf<LatestRelease?>(null) }

    LaunchedEffect(Unit) {
        if (!AppUpdateChecker.shouldCheckOnStartup(prefs)) return@LaunchedEffect
        // 先占坑再发请求：断网或被限流时，不该下次冷启动又自动重试一遍
        prefs.lastUpdateCheckAt = System.currentTimeMillis()
        delay(STARTUP_CHECK_DELAY_MS)
        val result = AppUpdateChecker.check(context) as? UpdateCheck.Available ?: return@LaunchedEffect
        if (AppUpdateChecker.shouldAnnounce(result.remoteVersion, prefs.ignoredUpdateVersion)) {
            shown = result.release
        }
    }

    shown?.let { release -> UpdateAvailableDialog(release) { shown = null } }
}
