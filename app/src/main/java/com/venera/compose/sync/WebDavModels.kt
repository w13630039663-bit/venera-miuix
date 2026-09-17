package com.venera.compose.sync

data class WebDavConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val remotePath: String = "/Venera/",
    val autoSync: Boolean = false
)

data class WebDavFileItem(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean
)

data class BackupSummary(
    val historyCount: Int,
    val favoriteCount: Int,
    val statsCount: Int,
    val guardRulesCount: Int,
    val timestamp: Long
)
