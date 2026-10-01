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
    /**
     * 没恢复进来的屏蔽规则条数（写法编译不过的那些）。刻意**不给默认值**：
     * 漏传不报错的参数，下一笔恢复就会静默变成"一条也没跳过"。
     */
    val guardRulesSkipped: Int,
    val timestamp: Long
) {
    /** 跳过要说得出：静默少恢复几条，用户只会以为"屏蔽不知怎么失效了"。 */
    val skippedNotice: String?
        get() = if (guardRulesSkipped > 0) "另有 $guardRulesSkipped 条屏蔽规则写法有误，没有恢复" else null
}
