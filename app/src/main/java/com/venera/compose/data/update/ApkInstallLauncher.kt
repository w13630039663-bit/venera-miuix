package com.venera.compose.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.venera.compose.data.platform.CacheDirs
import java.io.File

/** 一次"唤起系统安装器"的结果。四个分支各有不同的下一步，不许折成一个布尔。 */
sealed interface InstallOutcome {
    /** 安装器已经起来，剩下的是用户在系统页上那一下确认。 */
    data object Launched : InstallOutcome

    /** 还没拿到「安装未知应用」授权。这时**没有**任何东西被安装 —— 调用侧要去引导，不能报成功。 */
    data object NeedsUnknownSources : InstallOutcome

    /** 下载到的包与本机已装的包不是同一把签名：覆盖安装必然失败，先卸载才有机会。 */
    data object SignatureMismatch : InstallOutcome

    /** 连安装器都没起来（FileProvider 拒绝该路径、没有处理 package-archive 的应用等）。 */
    data class Failed(val reason: String) : InstallOutcome
}

/**
 * 把落盘的 APK 交给系统安装器。
 *
 * ## 为什么单独一颗
 *
 * 下载那一层只管"字节对不对"，这一层只管"这台机器能不能把它装进去"，两者的失败形状完全不同：
 * 前者是网络与哈希，后者是 FileProvider 路径表与两类系统授权。混在一起写，
 * 表现就是"点了更新没反应"这类分不清断在哪一环的报告。
 *
 * ## 三个都必须有声的分支
 *
 * 1. **未授予「安装未知应用」**（`canRequestPackageInstalls()` 为 false）。直接 `startActivity`
 *    装包意图在这台机器上不会弹安装页，也不抛异常 —— 写下去就是一枚假开关。所以这里交回
 *    [InstallOutcome.NeedsUnknownSources]，由 UI 把用户带到系统那一页去。
 * 2. **签名不同**。`UpdateCheckUi` 原先那句"不在应用内自装"就是被它挡出来的（见方案文档 §0.3）。
 *    现在改成先比对再说话：读已装包与下载包各自的签名证书字节，明确不同就直说"需要先卸载，
 *    收藏与历史会一起丢，请先导出备份"，而不是让用户收到系统的一句「应用未安装」。
 * 3. **FileProvider 不认这个路径**。这条洞的形状本仓结过案（`file_paths.xml:10-14` 的 G-2）：
 *    写文件永远成功，`getUriForFile` 才抛。所以落点名字只有 [CacheDirs.APP_UPDATE] 这一处出处，
 *    并由 `CacheDirsFileProviderContractTest` 与 XML 逐字对账。
 *
 * ## 读不出证书时**不拦**
 *
 * `getPackageArchiveInfo` 在个别 ROM 上对 archive 交不回 `signingInfo`。那种情况交回"未知"，
 * 照常唤起安装器 —— 系统自己会拒，而且拒得比我们准。
 * 反过来（因为读不到就判定"可能不匹配"而拒绝安装）会在一台本来能正常更新的机器上
 * 把这条链掐死，那是拿我们的观测缺口去换掉功能。
 */
object ApkInstallLauncher {

    private const val NOTIFICATION_INSTALL_REQUEST_CODE = 0x5A01

    /** APK 落点：`cacheDir/app_update/`，与 `res/xml/file_paths.xml` 那一行成对。 */
    fun apkDir(context: Context): File =
        CacheDirs.inCache(context.applicationContext.cacheDir, CacheDirs.APP_UPDATE)

    fun canRequestInstalls(context: Context): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    /** 系统「安装未知应用」授权页。应用无法替用户点这一下，只能把人送到那儿。 */
    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /**
     * 装包意图。`FLAG_GRANT_READ_URI_PERMISSION` 是必需的那一位：
     * 安装器是**另一个进程**，没有这次临时授权它读不到我们私有 cache 里的那个文件。
     */
    fun installIntent(context: Context, apk: File): Intent = Intent(Intent.ACTION_VIEW).apply {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        setDataAndType(uri, INSTALL_APK_MIME)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * 通知"点按安装"用的 PendingIntent。
     *
     * 用 `getActivity` 而不是广播：这条意图的目标是系统安装页，走 Activity 才拿得到
     * URI 临时读授权（广播接收者拿不到 grant，这是装不上那条最常见的错形）。
     * `FLAG_IMMUTABLE` 在 minSdk 33 上是恒可加的那一位，不再做版本分叉。
     */
    fun pendingInstallIntent(context: Context, apk: File): PendingIntent = PendingIntent.getActivity(
        context,
        NOTIFICATION_INSTALL_REQUEST_CODE,
        installIntent(context, apk),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun launchInstall(context: Context, apk: File): InstallOutcome {
        if (!apk.isFile) return InstallOutcome.Failed("安装包不在了：${apk.name}")
        if (signatureKnownAndDifferent(context, apk)) return InstallOutcome.SignatureMismatch
        if (!canRequestInstalls(context)) return InstallOutcome.NeedsUnknownSources
        return runCatching {
            context.startActivity(installIntent(context, apk))
            InstallOutcome.Launched
        }.getOrElse { InstallOutcome.Failed(it.javaClass.simpleName + ": " + (it.message ?: "无详情")) }
    }

    /** 只有"两边都读得出且不相等"才是签名不同；任何一侧读不出都算未知（见类头的说明）。 */
    internal fun signatureKnownAndDifferent(context: Context, apk: File): Boolean {
        val installed = installedSigner(context) ?: return false
        val archive = archiveSigner(context, apk) ?: return false
        return !installed.contentEquals(archive)
    }

    private fun installedSigner(context: Context): ByteArray? = runCatching {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        info.signingInfo?.signerBytes()
    }.getOrNull()

    private fun archiveSigner(context: Context, apk: File): ByteArray? = runCatching {
        // minSdk 33，所以只走新那个重载，不留一份永远进不去的旧分支。
        val info = context.packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
        info?.signingInfo?.signerBytes()
    }.getOrNull()

    /**
     * 取签名证书字节。两支里选 `apkContentsSigners`：
     * 单签名的包系统建议改读另一支（`apkContentCertificates`，注意它是 `Certificate` 而不是
     * `Signature`，类型不同、不能与这一支混进同一个比较），但对本层的判式来说
     * "已装的那把" 与 "下载的那把"**只要同一支可读且相等**就够了 —— 两支都取只会让比较不同类。
     * 交不回字节（V1-only 包、或该 ROM 对 archive 不给 signingInfo）时交回 null，
     * 调用侧按"未知"处理：照常唤起安装器，让系统去判（见类头那条"读不出时不拦"）。
     */
    private fun SigningInfo.signerBytes(): ByteArray? =
        apkContentsSigners?.firstOrNull()?.toByteArray()
}

/** 安装意图要的 MIME 字符串。与解析层那个判式同名不同物：那边是"这条产物是不是 APK"，这边是"交给谁能装"。 */
private const val INSTALL_APK_MIME = "application/vnd.android.package-archive"
