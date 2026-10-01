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
    val timestamp: Long,
    /** 这份数据从哪种归档恢复来的。用户可能一次导三种文件，结果里得说得出是哪种。 */
    val origin: String = BackupOrigin.LOCAL,
    /** 恢复到的收藏夹数量。 */
    val folderCount: Int = 0,
    /**
     * 因**来源认不出**而没有导入的条数（只可能出现在官方 Venera / PicaComic 归档里）：
     * 归档带着本机没装的漫画源，其 `type` 是个反查不到 key 的 Dart 哈希。
     * 沉默地少导入几条，用户只会以为"备份是坏的"。
     */
    val foreignSkipped: Int = 0,
    /**
     * 恢复到的插图收藏张数（只有元数据与地址，那份去混淆的位图不在归档里）。
     * 与 [guardRulesSkipped] 一样刻意**不给默认值**：漏传不报错的新参数，
     * 下一笔恢复就会静默报成"一张也没进来"。
     */
    val imageFavoriteCount: Int,
    /** 恢复到的画廊收藏条数。 */
    val galleryFavoriteCount: Int,
    /** 恢复到的关注画师数。 */
    val galleryFollowCount: Int
) {
    /** 跳过要说得出：静默少恢复几条，用户只会以为"屏蔽不知怎么失效了"。 */
    val skippedNotice: String?
        get() = buildList {
            if (guardRulesSkipped > 0) add("另有 $guardRulesSkipped 条屏蔽规则写法有误，没有恢复")
            if (foreignSkipped > 0) add("另有 $foreignSkipped 条来源未知（本机没装该源），没有导入")
        }.takeIf { it.isNotEmpty() }?.joinToString("；")
}

/** 归档来源的口径，只用于结果文案。 */
object BackupOrigin {
    const val LOCAL = "本应用备份"
    const val VENERA = "官方 Venera 备份"
    const val PICA_COMIC = "PicaComic 备份"
}
