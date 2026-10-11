package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.domain.GallerySaveNaming
import com.venera.compose.gallery.domain.GallerySettingsModel
import coil3.PlatformContext
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * 把屏上这一张的**原图字节**落到一条用户数得出的路径上（批 85）。
 *
 * ## 这一格缺的到底是哪一层
 *
 * `GallerySaver`（对岸那颗存图件）整颗在桌面排除清单里（`desktop/build.gradle.kts:107`），
 * 挡它的是 MediaStore / ContentValues / MediaScannerConnection 那一族**平台服务**，
 * 而不是"算落点 + 起名字 + 让重名"这三段判据。桌面这条路上没有相册登记这件事，
 * 所以自持一颗**只写字节**的件；三段判据里能共享的两段（命名、字节来源）都走共享件，不重述。
 *
 * ## 与批 54 那颗「复制图片」的三处同族、一处更严
 *
 * - 同族：取字节走 [desktopCoilBytes]（冷缓存先取一趟那段教训只许住在一处）；
 *   四种失败各一句；按钮与取证种子共用同一枚 lambda。
 * - **更严**：读数里连异常 message 都不带，只带**类型名**。批 54 那一路念的是位图解码，
 *   失败消息里通常是尺寸；这一路的失败消息十有八九就是那串站方地址（`api_key`/`user_id` 都在里面），
 *   而这一格每次都要念一句给人看的东西 —— 所以 [DesktopSaveImageOutcome.Refused] 的字段是
 *   `errorType: String?` 而不是 `Throwable?`：**结构上递不出整串消息**，比"记得脱敏"硬。
 *
 * ## 落点为什么不是 Coil 那份盘缓存
 *
 * 批 79 量过：Coil 的磁盘缓存在 `%LOCALAPPDATA%\Temp\coil3_disk_cache`，键是地址的哈希、
 * 文件名不是那张图的名字，而且**随时会被容量闸清掉**。用户按「存到本机」要的是
 * 一只数得出、删得掉、重启还在的目录 —— 那是缓存与落盘的区别，不是实现细节。
 */

/** 落点里那一层的名字，与对岸 `GallerySaveTarget.DIR_NAME` 同串（判据 ② 回核那句）。 */
internal const val DESKTOP_GALLERY_DIR_NAME = "图库"

/**
 * 存图落点：`数据根/downloads/图库`。
 *
 * ⚠️ 这颗**只算不建**：建目录那一笔住在 [desktopSaveImageWrite] 里（真要写下去才建），
 * 而首页「下载」那一屏只是去看一眼 —— 看一眼就在盘上多出一只目录，等于读数自己造了自己的证据
 * （与 `DesktopDatabasePorts.dbFileAt` 刻意不走 `PathProvider.subDir` 同一条理由）。
 */
internal fun desktopGallerySaveDir(paths: PathProvider): File =
    File(File(paths.dataRoot, "downloads"), DESKTOP_GALLERY_DIR_NAME)

/**
 * 重名让位：`名字 (1).jpg`。
 *
 * 对岸那一段住在被排除的 `GallerySaver.kt` 里（`GallerySaveTarget.uniqueName`），桌面拿不到那颗函数，
 * 只能各持一份 —— 所以"两颗必须同形"这件事由判据 ④ 拿**对岸源码文本**钉着，而不是靠注释里那句"照抄"。
 */
internal fun desktopSaveUniqueName(dir: File, name: String): String {
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

/**
 * 文件名。判据住在共享面那颗 [GallerySettingsModel.fileName]（它自己带着用例），
 * 桌面这里只递档位：命名档取**对岸的默认档** [GallerySaveNaming.SITE_ID]。
 *
 * ⚠️ 为什么不读那枚 `pref_gallery_save_naming`：桌面设置屏今天没有它的写侧（能改的只有那八枚），
 * 读一枚没人写得动的键就是给它配一个消费者 —— 而那枚键的真实值只有对岸或手工改盘才能变。
 * 少读一枚档不是缺口，屏上那句「已存到本机」报的是**真落点**，用户看得见名字。
 */
internal fun desktopSaveImageFileName(post: GalleryPost): String =
    GallerySettingsModel.fileName(post, GallerySaveNaming.SITE_ID, null)

/**
 * 存的是**原图那一档**（[GalleryPost.fileUrl]），与屏上正在看的哪一档无关 ——
 * 口径照对岸那句："下载"在用户心里就是把这张原图拿走。
 * 站方没给原图地址时交回 null：那时连目录都不该碰，更不能去拼一个地址。
 */
internal fun desktopSaveImageUrl(post: GalleryPost): String? = post.fileUrl.takeIf { it.isNotBlank() }

/** 存不成的四种成因，**各一句**（「没地址 / 没字节 / 0 字节 / 写不进去」是四句话）。 */
internal enum class DesktopSaveImageCause {
    /** 站方没给这一条的原图地址（与「这一张被分级挡着」不是一件事，挡着那一格由调用点先拒）。 */
    NO_FILE_URL,

    /** 字节取不回来：缓存没有它，而那趟"先取一趟"也没成（第三句话，见 [desktopCoilBytes]）。 */
    NO_BYTES,

    /** 字节到手而长度是 0 —— 那是"成功但空 body"，不能报成已存好。 */
    ZERO_BYTES,

    /** 目录建不出来 / 写不进去 / 写完回读不到：三样都在盘上，成因都由异常类型点名。 */
    DISK,
}

/**
 * 落成了带得出**盘上那颗文件的实际字节数**（不是请求参数里的那个数 —— 半截失败时两个数不一样）；
 * 没落成带得出是哪一种，外加异常类型名（只在真抛了东西那一档非空）。
 */
internal sealed interface DesktopSaveImageOutcome {
    data class Saved(val name: String, val path: String, val bytesOnDisk: Long) : DesktopSaveImageOutcome

    data class Refused(val cause: DesktopSaveImageCause, val errorType: String?) : DesktopSaveImageOutcome
}

/** 四句失败说明（与 [DesktopSaveImageCause] 一一对应，判据 ⑥ 钉着两两不许相同）。 */
internal fun desktopSaveImageFallback(cause: DesktopSaveImageCause): String = when (cause) {
    DesktopSaveImageCause.NO_FILE_URL -> "这一张站方没给原图地址，本机没什么可存的"
    DesktopSaveImageCause.NO_BYTES -> "这一张的字节没取回来（缓存里没有，补取那一趟也没成）"
    DesktopSaveImageCause.ZERO_BYTES -> "站方给了 0 字节，这一张没往盘上落"
    DesktopSaveImageCause.DISK -> "落点写不进去，这一张没存成（目录或磁盘不给写）"
}

/** 上屏那一句（成功报名字与真实字节数，失败报成因，真抛了东西就把类型名跟着念）。 */
internal fun desktopSaveImageScreenReading(outcome: DesktopSaveImageOutcome): String = when (outcome) {
    is DesktopSaveImageOutcome.Saved -> "已存到本机：${outcome.name}（${outcome.bytesOnDisk} 字节）"
    is DesktopSaveImageOutcome.Refused ->
        desktopSaveImageFallback(outcome.cause) + (outcome.errorType?.let { "：$it" } ?: "")
}

/** stdout 那一行：成了与没成都念得出（"没跑到"与"跑到了而被拒"不能同一个样）。 */
internal fun desktopSaveImageStdoutReading(outcome: DesktopSaveImageOutcome): String = when (outcome) {
    is DesktopSaveImageOutcome.Saved ->
        "D_存图 落盘 文件=${outcome.name} 字节=${outcome.bytesOnDisk} 目录=${File(outcome.path).parent}"

    is DesktopSaveImageOutcome.Refused ->
        "D_存图 未落盘 因=${outcome.cause.name} 说明=${desktopSaveImageFallback(outcome.cause)} " +
            "异常=${outcome.errorType ?: "没有"}"
}

/**
 * 写字节那一段：**先让重名、再写、再回读**。
 *
 * 只吃 [File] 与字节，不碰网络也不碰 Coil，所以这一段在单测里是真跑得动的（判据 ⑧⑨⑩⑪）。
 * 任何一步失败都交回 [DesktopSaveImageOutcome.Refused] 而不是原样落回、更不是换个地方偷偷写。
 */
internal fun desktopSaveImageWrite(dir: File, post: GalleryPost, bytes: ByteArray): DesktopSaveImageOutcome {
    if (bytes.isEmpty()) return DesktopSaveImageOutcome.Refused(DesktopSaveImageCause.ZERO_BYTES, null)
    val written = runCatching {
        if (!dir.exists() && !dir.mkdirs()) throw IOException("目标目录建不出来")
        val name = desktopSaveUniqueName(dir, desktopSaveImageFileName(post))
        val file = File(dir, name)
        file.writeBytes(bytes)
        file
    }
    val target = written.exceptionOrNull()
        ?.let { return DesktopSaveImageOutcome.Refused(DesktopSaveImageCause.DISK, it::class.java.simpleName) }
        ?: written.getOrThrow()
    val saved = runCatching {
        DesktopSaveImageOutcome.Saved(name = target.name, path = target.absolutePath, bytesOnDisk = readBackSize(target))
    }
    return saved.getOrElse { return DesktopSaveImageOutcome.Refused(DesktopSaveImageCause.DISK, it::class.java.simpleName) }
}

/**
 * 从**盘上那颗文件**取字节数，取不到或取回 0 都抛。
 *
 * 形参里只有 [File] —— 这是刻意的：那段算式结构上拿不到"请求参数里的字节数"，
 * 所以"报的数是回读的"这件事不是靠注释，而是那颗件里根本没有别的数可报。
 */
private fun readBackSize(file: File): Long {
    val size = Files.size(file.toPath())
    if (size == 0L) throw IOException("落盘后回读是 0 字节")
    return size
}

/**
 * 按一次「存到本机」：问地址 → 取字节（走那颗共用件，冷缓存先取一趟）→ 写字节 → 念一行。
 *
 * ⚠️ 地址取的是**原图那一档**而不是屏上正在摆的那一档，这一点与批 54 那枚「复制图片」**刻意不同轨**：
 * 复制要的是"刚看见的那一份"，存要的是"这张图本来的样子"（对岸那句口径）。两处若共用同一个地址参数，
 * 用户切了预览档就会把降档的那一张当成原图存下来。
 */
internal suspend fun desktopSaveImageRun(
    context: PlatformContext,
    dir: File,
    post: GalleryPost,
): DesktopSaveImageOutcome {
    val url = desktopSaveImageUrl(post)
        ?: return announce(DesktopSaveImageOutcome.Refused(DesktopSaveImageCause.NO_FILE_URL, null))
    val bytes = desktopCoilBytes(context, url)
        ?: return announce(DesktopSaveImageOutcome.Refused(DesktopSaveImageCause.NO_BYTES, null))
    return announce(desktopSaveImageWrite(dir, post, bytes))
}

/** 唯一的出口：先念那一行，再把结果交回屏上。 */
private fun announce(outcome: DesktopSaveImageOutcome): DesktopSaveImageOutcome {
    println(desktopSaveImageStdoutReading(outcome))
    return outcome
}

/** 落点上已存的一张：名字、真实字节数、最后改动时刻（毫秒，界面上自己格式化）。 */
internal data class DesktopSavedImage(val name: String, val bytes: Long, val modifiedAtMillis: Long)

/**
 * 枚举读侧的四种状态。
 *
 * ⚠️ 目录不存在（还没存过任何一张）与目录在而里面空着（存过又被删了）是**两句话**，
 * 而"读不出"是第三句（`listFiles()` 返回 null 是真的 I/O 失败，不许并进"空着"那一档）。
 */
internal sealed interface DesktopSavedListing {
    data class Missing(val dir: String) : DesktopSavedListing

    data class Empty(val dir: String) : DesktopSavedListing

    /** 读不出那一档照样带得出目录，因为屏上那句要指名去哪儿看。 */
    data class Unreadable(val dir: String) : DesktopSavedListing

    data class Listed(val dir: String, val items: List<DesktopSavedImage>) : DesktopSavedListing
}

/** 落点上有什么：只数文件、新的在前、字节数从盘上取。这一颗也不建目录（[desktopGallerySaveDir] 同一条）。 */
internal fun desktopListSavedImages(dir: File): DesktopSavedListing {
    if (!dir.isDirectory) return DesktopSavedListing.Missing(dir.absolutePath)
    val files = dir.listFiles() ?: return DesktopSavedListing.Unreadable(dir.absolutePath)
    val items = files.filter { it.isFile }.map { file ->
        DesktopSavedImage(
            name = file.name,
            bytes = runCatching { Files.size(file.toPath()) }.getOrDefault(0L),
            modifiedAtMillis = file.lastModified(),
        )
    }.sortedByDescending { it.modifiedAtMillis }
    return if (items.isEmpty()) DesktopSavedListing.Empty(dir.absolutePath) else DesktopSavedListing.Listed(dir.absolutePath, items)
}
