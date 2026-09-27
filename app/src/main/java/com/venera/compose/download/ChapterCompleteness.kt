package com.venera.compose.download

/** 一章在磁盘上的离线完成态。 */
internal enum class ChapterOffline {
    /** 没有这一章的离线内容。 */
    NONE,

    /** 有，但**只下一半** —— 不能当完整离线章用。 */
    PARTIAL,

    /** 齐了。 */
    COMPLETE,
}

/**
 * 一章的离线完成态判据 —— **纯函数**，输入全来自 `chapter.json` 与目录扫描结果。
 *
 * 为什么要单独一层：旧判据是"目录里有图就算下完"
 * （`DownloadManager.isChapterDownloaded`），而 `chapter.json` 是在**开下载之前**就写的，
 * 于是"下到第 5 页用户取消/失败"留下的一堆前缀页，被三处当成完整离线章消费：
 * 详情页挂绿色「已下载」徽章、批量下载把它从默认选中里排除、阅读器直接拿本地文件当整章读
 * —— 一本**静默截断**的书，而且因为"已下载"，用户永远不会重下它。
 *
 * ⚠️ 两个标记都没有 = 老数据，必须**保持今日语义**（有图即算完）。
 * 否则这次改动会把设备上所有现存离线章一夜之间变成"未下载"，
 * 用户看到的是"我的离线书全没了 / 又要重下一遍"—— 那是用一个 bug 换另一个更响的 bug。
 * （同一类迁移坑在 `relocateTasks` 的注释里有记录。）
 */
internal fun chapterOfflineState(
    expectedPages: Int?,
    complete: Boolean,
    pageCount: Int,
): ChapterOffline {
    if (pageCount <= 0) return ChapterOffline.NONE
    if (complete) return ChapterOffline.COMPLETE
    // expectedPages 为 null 或 <=0 都算"标记不可信"：前者是没写过的老数据，
    // 后者是页列表都没取到就写了 chapter.json（那条路径当场就失败返回了）。
    val expected = expectedPages?.takeIf { it > 0 } ?: return ChapterOffline.COMPLETE
    return if (pageCount >= expected) ChapterOffline.COMPLETE else ChapterOffline.PARTIAL
}
