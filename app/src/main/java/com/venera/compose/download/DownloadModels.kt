package com.venera.compose.download

import kotlinx.serialization.Serializable

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED
}

@Serializable
data class DownloadTask(
    val taskId: String, // "${sourceKey}_${comicId}_${chapterId}"
    val sourceKey: String,
    val comicId: String,
    val comicTitle: String,
    val comicCover: String,
    val chapterId: String,
    val chapterTitle: String,
    val chapterOrder: Int,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val downloadedPages: Int = 0,
    val totalPages: Int = 0,
    val speedText: String = "",
    val errorMsg: String? = null,
    val directoryPath: String = "",
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
)

data class LocalComic(
    val id: String,
    val title: String,
    val author: String = "",
    val coverPath: String = "",
    val sourceName: String = "",
    val chapterCount: Int = 0,
    val totalPages: Int = 0,
    val rootPath: String = "",
    val isExternal: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class LocalChapter(
    val chapterId: String,
    val title: String,
    val order: Int,
    val pageCount: Int,
    val folderPath: String,
    val pageFiles: List<String> = emptyList()
)
