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
import java.util.zip.ZipFile

/**
 * 备份包与用户所选位置之间的搬运。
 *
 * 走 ContentResolver 流，因此导出/导入**不需要任何存储权限**：系统「保存为」面板与
 * 文件选择器给的是 uri，位置与文件名由用户当场决定。ZipFile 只能读真实文件，
 * 所以两端都先落一份到私有缓存再解包/打包，用完即删。
 *
 * 导入一侧要认**三种**归档，它们的扩展名彼此重叠而内容物毫无关系，详见 [importArchive]。
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

    /**
     * 选取一个备份文件导入。
     *
     * 三种归档都从这里进（本仓 `.venera` / 官方 Venera `.venera` / PicaComic `.picadata`），
     * 靠**内容**分派 —— 本仓与官方 Venera 的扩展名**完全一样**，看后缀必然认错。
     */
    suspend fun importBackupFrom(context: Context, src: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File(context.cacheDir, "import_archive_${System.currentTimeMillis()}")
            try {
                context.contentResolver.openInputStream(src).use { input ->
                    requireNotNull(input) { "读不到所选文件" }
                    temp.outputStream().use { input.copyTo(it) }
                }
                importArchive(context, temp)
            } finally {
                temp.delete()
            }
        }
    }

    /**
     * 识别归档来源并分派给对应的导入器。
     *
     * 判据只看 ZIP 里有什么，不看扩展名：
     * - 有 `meta.json` ⇒（本应用）`BackupManager` 的 JSON 备份；
     * - 有 `local_favorite.db` / `history.db` ⇒ 官方 Venera 或 PicaComic 的 SQLite 归档；
     * - 都没有 ⇒ 不是备份文件，**在动库之前**就报错。
     */
    private suspend fun importArchive(context: Context, archive: File): BackupSummary {
        val entries = runCatching { entryNames(archive) }.getOrElse {
            throw IllegalArgumentException("这个文件不是压缩包：${it.message}")
        }
        return when {
            entries.contains("meta.json") ->
                BackupManager.getInstance(context).importBackup(archive).getOrThrow()

            entries.any { ForeignArchiveImport.isForeignArchiveEntry(it) } ->
                ForeignArchiveImport.getInstance(context).importArchive(archive).getOrThrow().toSummary()

            else -> throw IllegalArgumentException(
                "不认识这个文件：既不是本应用的备份，也不是 Venera / PicaComic 的归档"
            )
        }
    }

    private fun entryNames(file: File): List<String> = ZipFile(file).use { zip ->
        buildList {
            val entries = zip.entries()
            while (entries.hasMoreElements()) add(entries.nextElement().name)
        }
    }

    /**
     * 导入结果的统一文案。
     *
     * 设置页与云同步页是两个入口，但用户看到的应当是同一件事 —— 各写一套迟早会分叉，
     * 尤其是"跳过了几条"这种必须说出口的信息。零值不占版面（"统计 0 条"没有意义）。
     */
    fun describeResult(summary: BackupSummary): String {
        val head = if (summary.origin == BackupOrigin.LOCAL) "恢复完成" else "已从 ${summary.origin} 导入"
        val parts = buildList {
            add("历史 ${summary.historyCount} 条")
            add("收藏 ${summary.favoriteCount} 部")
            if (summary.folderCount > 0) add("收藏夹 ${summary.folderCount} 个")
            if (summary.statsCount > 0) add("统计 ${summary.statsCount} 条")
            if (summary.guardRulesCount > 0) add("屏蔽规则 ${summary.guardRulesCount} 条")
        }
        return "$head：${parts.joinToString("，")}" + (summary.skippedNotice?.let { "。$it" } ?: "")
    }

    private fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull() ?: uri.lastPathSegment ?: "所选文件"
}
