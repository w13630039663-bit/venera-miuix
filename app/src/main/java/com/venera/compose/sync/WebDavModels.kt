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
    val galleryFollowCount: Int,
    /**
     * 恢复了账号数据的漫画源数量（只有勾选了导出账号的归档才可能 > 0）。
     * 与 [imageFavoriteCount] 同样刻意**不给默认值**：漏传就是静默报成"一个源都没恢复"。
     */
    val sourceAccountCount: Int,
    /**
     * 归档里带了账号、但本机没装那个源因而跳过的数量。
     * 不说出来，用户看到的就是"导入成功，但登录态没回来"，只会以为备份是坏的。
     */
    val sourceAccountSkipped: Int
) {
    /** 跳过要说得出：静默少恢复几条，用户只会以为"屏蔽不知怎么失效了"。 */
    val skippedNotice: String?
        get() = buildList {
            if (guardRulesSkipped > 0) add("另有 $guardRulesSkipped 条屏蔽规则写法有误，没有恢复")
            if (foreignSkipped > 0) add("另有 $foreignSkipped 条来源未知（本机没装该源），没有导入")
            if (sourceAccountSkipped > 0) {
                add("另有 $sourceAccountSkipped 个源的账号因本机未安装该源而没有恢复")
            }
        }.takeIf { it.isNotEmpty() }?.joinToString("；")
}

/** 归档来源的口径，只用于结果文案。 */
object BackupOrigin {
    const val LOCAL = "本应用备份"
    const val VENERA = "官方 Venera 备份"
    const val PICA_COMIC = "PicaComic 备份"
}
