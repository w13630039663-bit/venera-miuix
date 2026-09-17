package com.venera.compose.data.db

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker
import com.venera.compose.data.prefs.VeneraPreferences
import java.util.concurrent.TimeUnit

/**
 * 追更周期任务（S5-5）。
 *
 * 用 `androidx.work` 的 `PeriodicWorkRequest` + 唯一命名工作：
 * 追更正是「每天一次、可被用户手动触发、允许系统择机执行」的 workload。
 */
class FollowUpdatesWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = VeneraPreferences.getInstance(applicationContext)
        val folder = prefs.followUpdatesFolder.value ?: return Result.success()
        if (folder.isBlank()) return Result.success()

        return try {
            FollowUpdatesRepository.getInstance(applicationContext).updateFolder(folder)
            Result.success()
        } catch (e: Exception) {
            // 周期任务失败不重试到崩溃：交给下一次周期
            Result.success()
        }
    }
}

/** 追更任务的注册与取消入口。 */
object FollowUpdatesScheduler {

    private const val WORK_NAME = "venera_follow_updates"

    /** 开启每日追更检查（已存在则保持，不重复排队）。 */
    fun enable(context: Context) {
        val request = PeriodicWorkRequestBuilder<FollowUpdatesWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }
}
