package com.venera.compose.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * WebDAV 同步协调管理器 (S7)
 *
 * 核心特性：
 * 1. 自动打包本地备份并同步至云端
 * 2. 命名规范: `epochDay-timestamp.venera`
 * 3. 云端自动滚动淘汰（仅保留最近 10 份，避免占满网盘空间）
 * 4. 从云端拉取指定备份并即刻无损还原
 */
class WebDavSyncManager private constructor(private val context: Context) {

    private val tag = "WebDavSyncManager"
    private val backupManager = BackupManager.getInstance(context)
    private val prefs = context.getSharedPreferences("venera_webdav_prefs", Context.MODE_PRIVATE)

    fun getConfig(): WebDavConfig {
        return WebDavConfig(
            serverUrl = prefs.getString("server_url", "") ?: "",
            username = prefs.getString("username", "") ?: "",
            password = prefs.getString("password", "") ?: "",
            remotePath = prefs.getString("remote_path", "/Venera/") ?: "/Venera/",
            autoSync = prefs.getBoolean("auto_sync", false)
        )
    }

    fun saveConfig(config: WebDavConfig) {
        prefs.edit()
            .putString("server_url", config.serverUrl)
            .putString("username", config.username)
            .putString("password", config.password)
            .putString("remote_path", config.remotePath)
            .putBoolean("auto_sync", config.autoSync)
            .apply()
    }

    /**
     * 上传当前数据到 WebDAV 云端
     */
    suspend fun uploadToWebDav(): Result<String> = withContext(Dispatchers.IO) {
        val config = getConfig()
        if (config.serverUrl.isBlank()) {
            return@withContext Result.failure(Exception("请先在设置中配置 WebDAV 服务器地址"))
        }

        val client = WebDavClient(config)
        val connRes = client.testConnection()
        if (connRes.isFailure) {
            return@withContext Result.failure(Exception("WebDAV 连接失败: ${connRes.exceptionOrNull()?.message}"))
        }

        // 1. 本地生成备份
        val backupRes = backupManager.exportBackup()
        if (backupRes.isFailure) {
            return@withContext Result.failure(backupRes.exceptionOrNull() ?: Exception("生成备份失败"))
        }
        val file = backupRes.getOrNull()!!

        // 2. 规范命名与上传: epochDay_timestamp.venera
        val epochDay = System.currentTimeMillis() / 86400000L
        val fileName = "${epochDay}_${System.currentTimeMillis()}.venera"
        val uploadRes = client.uploadFile(fileName, file)
        if (uploadRes.isFailure) {
            return@withContext Result.failure(uploadRes.exceptionOrNull() ?: Exception("上传云端失败"))
        }

        // 3. 清理云端超过 10 份的旧备份
        try {
            val listRes = client.listBackups()
            if (listRes.isSuccess) {
                val backups = listRes.getOrNull() ?: emptyList()
                if (backups.size > 10) {
                    val sorted = backups.sortedBy { it.lastModified }
                    val toDelete = sorted.take(backups.size - 10)
                    for (delItem in toDelete) {
                        client.deleteFile(delItem.name)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Clean old backups failed", e)
        }

        prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
        Result.success("备份已成功同步至云端: $fileName")
    }

    /**
     * 从 WebDAV 下载并还原最新备份
     */
    suspend fun restoreFromWebDav(remoteFileName: String? = null): Result<BackupSummary> = withContext(Dispatchers.IO) {
        val config = getConfig()
        if (config.serverUrl.isBlank()) {
            return@withContext Result.failure(Exception("请先配置 WebDAV 服务器"))
        }

        val client = WebDavClient(config)
        val targetName = if (remoteFileName.isNullOrBlank()) {
            val listRes = client.listBackups()
            if (listRes.isFailure || listRes.getOrNull().isNullOrEmpty()) {
                return@withContext Result.failure(Exception("云端未找到任何有效的备份文件"))
            }
            listRes.getOrNull()!!.maxByOrNull { it.lastModified }?.name
                ?: return@withContext Result.failure(Exception("找不到可用备份"))
        } else {
            remoteFileName
        }

        val localDest = File(context.cacheDir, "webdav_download_$targetName")
        val dlRes = client.downloadFile(targetName, localDest)
        if (dlRes.isFailure) {
            return@withContext Result.failure(dlRes.exceptionOrNull() ?: Exception("下载备份失败"))
        }

        val restoreRes = backupManager.importBackup(localDest)
        localDest.delete()
        restoreRes
    }

    /**
     * 获取远程所有备份列表
     */
    suspend fun getRemoteBackups(): Result<List<WebDavFileItem>> = withContext(Dispatchers.IO) {
        val config = getConfig()
        if (config.serverUrl.isBlank()) return@withContext Result.failure(Exception("未配置 WebDAV"))
        val client = WebDavClient(config)
        client.listBackups()
    }

    companion object {
        @Volatile
        private var INSTANCE: WebDavSyncManager? = null

        fun getInstance(context: Context): WebDavSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebDavSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
