package com.venera.compose.sync

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份包与用户所选位置之间的搬运。
 *
 * 走 ContentResolver 流，因此导出/导入**不需要任何存储权限**：系统「保存为」面板与
 * 文件选择器给的是 uri，位置与文件名由用户当场决定。ZipFile 只能读真实文件，
 * 所以两端都先落一份到私有缓存再解包/打包，用完即删。
 */
object BackupTransfers {

    private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /** 导出面板里的默认文件名（用户可改）。扩展名与 BackupManager 产物保持一致。 */
    fun suggestedFileName(): String = "venera_backup_${timestampFormat.format(Date())}.venera"

    /** 备份包只含 history / favorite / stats / guard_rules 四张表，导入前先让用户知道覆盖面。 */
    suspend fun exportBackupTo(context: Context, dest: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val packed = BackupManager.getInstance(context).exportBackup().getOrThrow()
            try {
                context.contentResolver.openOutputStream(dest).use { out ->
                    requireNotNull(out) { "所选位置不可写" }
                    packed.inputStream().use { it.copyTo(out) }
                }
                displayName(context, dest)
            } finally {
                packed.delete()
            }
        }
    }

    suspend fun importBackupFrom(context: Context, src: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File(context.cacheDir, "import_backup_${System.currentTimeMillis()}.venera")
            try {
                context.contentResolver.openInputStream(src).use { input ->
                    requireNotNull(input) { "读不到所选文件" }
                    temp.outputStream().use { input.copyTo(it) }
                }
                BackupManager.getInstance(context).importBackup(temp).getOrThrow()
            } finally {
                temp.delete()
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull() ?: uri.lastPathSegment ?: "所选文件"
}
