package com.venera.compose.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 本地漫画库管理服务 (S6)
 *
 * 核心特性：
 * 1. 自动扫描已完成下载的漫画
 * 2. 导出标准 CBZ 归档文件
 * 3. 导入外部 CBZ/ZIP 漫画
 * 4. 本地漫画删除与存储释放
 */
class LocalComicManager private constructor(private val context: Context) {

    private val tag = "LocalComicManager"
    private val downloadsRootDir = File(context.filesDir, "downloads")

    /**
     * 扫描本地已下载的所有漫画
     */
    suspend fun getLocalComics(): List<LocalComic> = withContext(Dispatchers.IO) {
        val results = mutableListOf<LocalComic>()
        if (!downloadsRootDir.exists()) return@withContext results

        val sourceDirs = downloadsRootDir.listFiles { f -> f.isDirectory } ?: return@withContext results
        for (srcDir in sourceDirs) {
            val comicDirs = srcDir.listFiles { f -> f.isDirectory } ?: continue
            for (cDir in comicDirs) {
                val infoFile = File(cDir, "comic_info.json")
                var title = cDir.name
                var coverUrl = ""
                var author = ""
                var sourceKey = srcDir.name

                if (infoFile.exists()) {
                    try {
                        val json = JSONObject(infoFile.readText())
                        title = json.optString("title", title)
                        coverUrl = json.optString("cover", "")
                        sourceKey = json.optString("sourceKey", sourceKey)
                    } catch (_: Exception) {}
                }

                // 统计有效章节与图片
                val chapterDirs = cDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name } ?: emptyList()
                var totalPages = 0
                var firstCoverFile: File? = null

                val validChapters = mutableListOf<LocalChapter>()
                for (chDir in chapterDirs) {
                    val images = chDir.listFiles { f ->
                        f.isFile && (f.extension == "jpg" || f.extension == "png" || f.extension == "webp") && f.length() > 0
                    }?.sortedBy { it.name } ?: emptyList()

                    if (images.isNotEmpty()) {
                        if (firstCoverFile == null) {
                            firstCoverFile = images.first()
                        }
                        totalPages += images.size

                        val chInfoFile = File(chDir, "chapter.json")
                        var chTitle = chDir.name
                        var chId = chDir.name
                        var chOrder = 0
                        if (chInfoFile.exists()) {
                            try {
                                val json = JSONObject(chInfoFile.readText())
                                chTitle = json.optString("title", chTitle)
                                chId = json.optString("chapterId", chId)
                                chOrder = json.optInt("order", 0)
                            } catch (_: Exception) {}
                        }

                        validChapters.add(
                            LocalChapter(
                                chapterId = chId,
                                title = chTitle,
                                order = chOrder,
                                pageCount = images.size,
                                folderPath = chDir.absolutePath,
                                pageFiles = images.map { it.absolutePath }
                            )
                        )
                    }
                }

                if (validChapters.isNotEmpty()) {
                    results.add(
                        LocalComic(
                            id = cDir.name,
                            title = title,
                            author = author,
                            coverPath = if (coverUrl.isNotBlank()) coverUrl else (firstCoverFile?.absolutePath ?: ""),
                            sourceName = sourceKey,
                            chapterCount = validChapters.size,
                            totalPages = totalPages,
                            rootPath = cDir.absolutePath,
                            isExternal = false,
                            updatedAt = cDir.lastModified()
                        )
                    )
                }
            }
        }
        results.sortedByDescending { it.updatedAt }
    }

    /**
     * 获取指定本地漫画的章节列表
     */
    suspend fun getLocalChapters(comic: LocalComic): List<LocalChapter> = withContext(Dispatchers.IO) {
        val root = File(comic.rootPath)
        if (!root.exists()) return@withContext emptyList()

        val chapterDirs = root.listFiles { f -> f.isDirectory }?.sortedBy { it.name } ?: return@withContext emptyList()
        val list = mutableListOf<LocalChapter>()

        for (chDir in chapterDirs) {
            val images = chDir.listFiles { f ->
                f.isFile && (f.extension == "jpg" || f.extension == "png" || f.extension == "webp") && f.length() > 0
            }?.sortedBy { it.name } ?: emptyList()

            if (images.isNotEmpty()) {
                val chInfoFile = File(chDir, "chapter.json")
                var chTitle = chDir.name
                var chId = chDir.name
                var chOrder = 0
                if (chInfoFile.exists()) {
                    try {
                        val json = JSONObject(chInfoFile.readText())
                        chTitle = json.optString("title", chTitle)
                        chId = json.optString("chapterId", chId)
                        chOrder = json.optInt("order", 0)
                    } catch (_: Exception) {}
                }

                list.add(
                    LocalChapter(
                        chapterId = chId,
                        title = chTitle,
                        order = chOrder,
                        pageCount = images.size,
                        folderPath = chDir.absolutePath,
                        pageFiles = images.map { it.absolutePath }
                    )
                )
            }
        }
        list
    }

    /**
     * 导出为标准 CBZ 格式
     */
    suspend fun exportToCbz(
        comic: LocalComic,
        targetFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val root = File(comic.rootPath)
            if (!root.exists()) return@withContext Result.failure(Exception("漫画目录不存在"))

            val chapters = getLocalChapters(comic)
            val allImages = mutableListOf<Pair<String, File>>() // entryPath -> File

            for (ch in chapters) {
                val safeChTitle = ch.title.replace(Regex("[/\\\\:*?\"<>|]"), "_")
                val files = ch.pageFiles.map { File(it) }
                for (f in files) {
                    allImages.add("${safeChTitle}/${f.name}" to f)
                }
            }

            if (allImages.isEmpty()) {
                return@withContext Result.failure(Exception("无可导出的图片"))
            }

            val total = allImages.size
            if (targetFile.exists()) targetFile.delete()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(targetFile))).use { zos ->
                for ((index, pair) in allImages.withIndex()) {
                    val (entryPath, file) = pair
                    val entry = ZipEntry(entryPath)
                    zos.putNextEntry(entry)
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                    onProgress((index + 1).toFloat() / total)
                }
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(tag, "Failed to export CBZ", e)
            Result.failure(e)
        }
    }

    /**
     * 导入外部 CBZ 或 ZIP 格式的漫画包
     */
    suspend fun importCbz(archiveFile: File, customTitle: String? = null): Result<LocalComic> = withContext(Dispatchers.IO) {
        try {
            if (!archiveFile.exists()) return@withContext Result.failure(Exception("导入文件不存在"))

            val title = customTitle?.ifBlank { null } ?: archiveFile.nameWithoutExtension
            val safeTitle = title.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val targetDir = File(File(downloadsRootDir, "imported"), safeTitle).apply {
                if (!exists()) mkdirs()
            }

            val zip = ZipFile(archiveFile)
            val entries = zip.entries()
            var pageIdx = 1

            // 单章导入目录
            val chapterDir = File(targetDir, "0001_全一话").apply { if (!exists()) mkdirs() }

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val nameLower = entry.name.lowercase()
                if (nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || nameLower.endsWith(".webp")) {
                    val ext = nameLower.substringAfterLast('.')
                    val outFileName = "${pageIdx.toString().padStart(4, '0')}.$ext"
                    val outFile = File(chapterDir, outFileName)

                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    pageIdx++
                }
            }
            zip.close()

            // 写入漫画元数据
            File(targetDir, "comic_info.json").writeText(
                JSONObject().apply {
                    put("id", safeTitle)
                    put("title", title)
                    put("sourceKey", "imported")
                    put("updateTime", System.currentTimeMillis())
                }.toString()
            )

            // 写入章节元数据
            File(chapterDir, "chapter.json").writeText(
                JSONObject().apply {
                    put("chapterId", "1")
                    put("title", "全一话")
                    put("order", 1)
                }.toString()
            )

            val comic = LocalComic(
                id = safeTitle,
                title = title,
                sourceName = "本地导入",
                chapterCount = 1,
                totalPages = pageIdx - 1,
                rootPath = targetDir.absolutePath,
                isExternal = true
            )
            Result.success(comic)
        } catch (e: Exception) {
            Log.e(tag, "Failed to import CBZ", e)
            Result.failure(e)
        }
    }

    /**
     * 删除本地漫画文件
     */
    suspend fun deleteLocalComic(comic: LocalComic): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = File(comic.rootPath)
            if (root.exists()) {
                root.deleteRecursively()
            }
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete local comic", e)
            false
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: LocalComicManager? = null

        fun getInstance(context: Context): LocalComicManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalComicManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
