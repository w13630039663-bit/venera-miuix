package com.venera.compose.gallery.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.download.ComicStorageRoot
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把画廊的一条**原图/原片原样**存下来。
 *
 * ## 落点：`<本地漫画存储根>/图库`
 *
 * 用户 2026-09-26 的口径：跟着漫画库走，在同一层新建一个 `图库` 文件夹。
 * 默认漫画根是应用私有的 `files/downloads`，用户已把它设成
 * `/storage/emulated/0/Download/漫画`（设置 → 本地漫画存储路径），
 * 所以实际落点是 `/storage/emulated/0/Download/漫画/图库/<site>-<id>.<ext>`。
 *
 * ## 两条写入路径（先试 MediaStore，落点不被接受才直写文件）
 *
 * 1. **MediaStore 显式插入**。按 MIME 选 `MediaStore.Images` / `MediaStore.Video` 的
 *    collection —— **这条是 2026-09-26 修的那个 bug 的正面**：旧实现不问类型，
 *    一律 `MediaStore.Files.getContentUri("external")`（`content://media/external/file`），
 *    系统当场回 `Primary directory Pictures not allowed for content://media/external/file`，
 *    也就是用户截图里那条 toast：**图库的图一张都存不下来**。
 *    `file` 这个 collection 对顶级目录另有约束，而 Images/Video 宽松得多 ——
 *    真机 `content insert` 实测：`images/media` + `RELATIVE_PATH=Download/漫画/图库/` 直接成功。
 *    走 MediaStore 还有个附加好处：**即便父目录里有 `.nomedia`**（漫画根由
 *    [ComicStorageRoot.ensureRoot] 建过一枚，防离线漫画污染系统相册），显式插入的行
 *    仍然登记在册，系统相册收得到。
 * 2. **直接 File 写入**。落点换算不出公共存储相对路径（换到外置卡、或用户选了别处）时兜底。
 *    应用持有 `MANAGE_EXTERNAL_STORAGE`（漫画库本身就靠它写在公共存储上），所以写得进去。
 *    写完后 `scanFile` 通知媒体库 —— 这一条会被 `.nomedia` 挡住，所以它只是"尽力"，
 *    返回给 UI 的路径**始终是真实落点**，不拿"已存进相册"糊弄。
 *
 * ## 三条刻意的选择
 *
 * - **原样落字节**，不解码不重编码。全仓另外两条"保存图片"的路都是
 *   `bitmap.compress(JPEG, 95)` —— 那会把 png 的 alpha 洗掉、把原图降质。
 * - **存的永远是 `file_url` 那一档**，不是屏上正在看的 large 档：
 *   "下载"在用户心里就是"把这张原图拿走"，与 HD 开关无关。
 * - **失败一律交回异常并带成因**：静默 `false` 会让 Toast 说"已保存"而磁盘上什么都没有。
 */
object GallerySaver {

    /**
     * 取一条的**原档字节**（`file_url`），不落盘。
     *
     * 给「分享」用：与 [save] 是同一条取字节链路、同一档（原图/原片），
     * 所以分享出去的东西和保存下来的**是同一份**，不会出现"分享的和保存的不一样"。
     * 单独抽出来是因为分享只需要字节、不需要算落点/重名/MediaStore 那一串。
     *
     * 0 字节同样抛错（OkHttp 这条路上它是"成功但空 body"）。
     */
    suspend fun fetchBytes(context: Context, post: GalleryPost): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = VeneraNetworkClient.getInstance(context).downloadBytes(post.fileUrl)
                if (bytes.isEmpty()) throw IOException("站方给了 0 字节")
                bytes
            }
        }

    /** @param path 真实落点的绝对路径（给 UI 如实报位置）；@param bytesSaved 实际落盘字节数。 */
    data class Saved(val path: String, val bytesSaved: Long)

    suspend fun save(context: Context, post: GalleryPost): Result<Saved> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = VeneraNetworkClient.getInstance(context).downloadBytes(post.fileUrl)
            // 0 字节在 OkHttp 这条路上是"成功但空 body"，不能当保存成功。
            if (bytes.isEmpty()) throw IOException("站方给了 0 字节")

            val mime = mimeOf(post)
            val dir = GallerySaveTarget.directory(context)
            // 重名由我们**先**处理掉（`name (1).jpg`）：MediaStore 自己也会改名，
            // 但它改完的名字我们只能再查一次才拿得到，"存到哪了"就会变成一句猜的话。
            val name = GallerySaveTarget.uniqueName(dir, "${post.site.routeKey}-${post.id}.${post.fileExt.ifBlank { "bin" }}")
            val path = saveToAlbum(context, dir, name, mime, bytes)
                ?: saveToFile(context, dir, name, mime, bytes)
            Saved(path, bytes.size.toLong())
        }
    }

    /**
     * 走 MediaStore。落点不被系统接受（换算不出相对路径 / 插入返回 null / 抛参数错）时返回 null，
     * 由调用方直写文件 —— **只有"落点被拒"才回落**；占位行建好了却写不进字节属于真失败，
     * 那种情况清掉占位行后照原样抛错，不能悄悄换个地方写。
     */
    private fun saveToAlbum(
        context: Context,
        dir: File,
        name: String,
        mime: String,
        bytes: ByteArray,
    ): String? {
        val relative = GallerySaveTarget.mediaStoreRelativePath(dir) ?: return null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (mime.startsWith("video/")) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val uri: Uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IOException("打不开相册的写入流")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (e: Exception) {
            // 半途失败要清掉那条占位记录，否则相册里留一张 0 字节的"坏图"。
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return File(dir, name).absolutePath
    }

    /** 直写文件兜底。目录建不出来或写不进去就如实抛错，不换地方藏。 */
    private fun saveToFile(
        context: Context,
        dir: File,
        name: String,
        mime: String,
        bytes: ByteArray,
    ): String {
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("目标目录建不出来：${dir.absolutePath}")
        }
        val file = File(dir, name)
        file.outputStream().use { it.write(bytes) }
        // 尽力通知媒体库（`.nomedia` 之下会被忽略，成功与否都不影响"文件已落盘"这个事实）。
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
        }
        return file.absolutePath
    }

    /**
     * MIME 按**站方给的扩展名**映射。
     *
     * 未知扩展名退回 `application/octet-stream` 而不是猜一个 `image/jpeg`：
     * MediaStore 会照我们声明的类型记账，报错的类型就是相册里一张打不开的"图"。
     */
    internal fun mimeOf(post: GalleryPost): String = when (post.fileExt.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        else -> "application/octet-stream"
    }
}

/**
 * 下载落点：**本地漫画存储根 + `图库`**。
 *
 * 只做路径换算与建目录，不碰字节 —— 落地点与"怎么写进去"（MediaStore / File）
 * 是两件事，混在一起就会出现"为了用 MediaStore 而偷偷改目录"。
 */
internal object GallerySaveTarget {

    const val DIR_NAME = "图库"

    /** 恒定落点。漫画根为空时 `ComicStorageRoot.resolve` 会给应用私有默认根。 */
    fun directory(context: Context): File = File(ComicStorageRoot.resolve(context), DIR_NAME)

    /**
     * 目录换成 MediaStore 的 `RELATIVE_PATH`（形如 `Download/漫画/图库`）。
     *
     * **只在主外部存储下才返回非空**：外置卡的相对路径属于另一个 volume，
     * 拿 `VOLUME_EXTERNAL_PRIMARY` 去写它会落到内置卡的某个同名字段上 —— 那是"存了但不在你要的地方"。
     * 绑定挂载下设 `/storage/emulated/0` 与 `/mnt/...` 两种形态都存在，两种都比一次。
     */
    fun mediaStoreRelativePath(dir: File): String? {
        val root = runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrNull() ?: return null
        val candidates = listOf(dir.absolutePath, runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath))
        for (path in candidates.distinct()) {
            if (path != root && !path.startsWith(root + File.separator)) continue
            val relative = path.removePrefix(root).trim(File.separatorChar)
            if (relative.isEmpty()) continue
            return relative
        }
        return null
    }

    /** 重名时按 `名字 (1).jpg` 让位，与 MediaStore 自己的命名习惯一致。 */
    fun uniqueName(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        val suffix = if (ext.isEmpty()) "" else ".$ext"
        var index = 1
        while (true) {
            val candidate = "$base ($index)$suffix"
            if (!File(dir, candidate).exists()) return candidate
            index++
        }
    }
}
