package com.venera.compose.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 「离线下载」这条通知渠道的唯一定义处（2026-10-04 从 `DownloadManager` 里搬出来）。
 *
 * ## 为什么要搬
 *
 * 原先渠道是在 `DownloadManager` 的 `init` 里建的 —— 也就是**第一次用到那顆下载单例时**才建。
 * 应用内自更新那条链要复用同一条渠道（不新建第二条是 2026-10-04 的拍板），
 * 但一台从没下载过漫画的设备上 `DownloadManager` 可能从未被实例化过，
 * 于是 `notify()` 打到一条不存在的渠道上会被系统**静默丢掉** ——
 * 表现正是"下载在跑、通知栏什么都没有"，而这在本仓的叫法是假开关。
 * 所以取渠道这一步必须与下载管理器的生命周期无关，谁要用谁自己 `ensure`。
 *
 * id 与名称逐字保持原样（`venera_download` / 「离线下载」）：改 id 会在老设备上留下一条没人用的渠道，
 * 而用户已经在系统设置里对这条渠道做过的开关/静音设置也会跟着失效。
 */
object DownloadNotificationChannel {

    const val ID = "venera_download"

    /** 低打扰：进度通知不响不震（沿用原 `IMPORTANCE_LOW` + `setShowBadge(false)` 那一档）。 */
    fun ensure(context: Context) {
        val channel = NotificationChannel(ID, "离线下载", NotificationManager.IMPORTANCE_LOW).apply {
            description = "下载进度与完成状态"
            setShowBadge(false)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.createNotificationChannel(channel)
    }

    /**
     * Android 13+ 的通知运行时权限。**这里只回答有没有，不代替用户去要**。
     *
     * 全仓今天仍没有任何地方申请过它（Manifest:9 有声明，运行时零请求），
     * 所以未授予是常态而不是异常。要权限那一步发生在更新弹窗里用户点下「更新」之后
     * （见 `feature/settings/UpdateCheckUi.kt`），因为只有那一刻才有正当理由。
     */
    fun canPost(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    fun manager(context: Context): NotificationManagerCompat = NotificationManagerCompat.from(context)
}
