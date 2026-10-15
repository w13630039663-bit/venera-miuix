package com.venera.compose.gallery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.venera.compose.data.platform.CacheDirs
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySaver
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 分享一条**原档**（图或视频），并把单页链接一并带上。
 *
 * 从大图页工具条那一段整体搬过来（2026-10-04）：墙上长按也要同一个动作，
 * 复制一份两份迟早漂 —— 而这两份的差别只会体现在"哪一边的失败没说话"上。
 * 防重入（`sharing` 那面旗）与震动反馈**留在调用方**：那是各自主宿的 UI 状态，
 * 搬进来说不清是谁的旗。
 *
 * 三条口径原样沿用既有裁决：
 *
 * - **取原档**（`GallerySaver.fetchBytes`），与「保存」那条路同源同档 ——
 *   分享出去的和保存下来的必须是同一份。
 * - **原样落字节，不解码不重编码**：不用阅读器那条 `bitmap.compress(JPEG, 95)`，
 *   那会洗掉 png 的 alpha 并把原图降质（`GallerySaver` 顶部注释已明令禁止这条路）。
 * - **MIME 按站方扩展名**（`GallerySaver.mimeOf`），未知扩展名退 `application/octet-stream`
 *   而不是猜一个。
 *
 * 视频条目**照原片分享**（mp4/webm），不换成"分享链接" —— 那枚动作在两种条目上
 * 长得一样、行为却不同，用户会以为按钮坏了。视频原片实测 16~26 MB，分享前要真下载一次
 * （不命中 Coil 缓存），所以**所有失败都必须出声**：静默失败读起来就是"按了没反应"。
 */
internal suspend fun shareGalleryPost(context: Context, target: GalleryPost) {
    GallerySaver.fetchBytes(context, target)
        .onSuccess { bytes ->
            // 类型**写死成 Uri**，并且用 getOrElse 而不是 onFailure：
            // `runCatching{…}.onFailure{…}` 交出的是 `Result<Uri>` 而**不是** `Uri`，
            // 于是下面那句 `putExtra(EXTRA_STREAM, uri)` 静默挑中了
            // `putExtra(String, Serializable)` 那个重载（kotlin.Result 声明了 Serializable）——
            // 编译通过、真机当场 "Parcelable encountered IOException writing serializable
            // object (name = kotlin.Result)"，分享**一次都没有成功过**。
            // 显式类型 + getOrElse 让这一类错落在编译期，而不是落在用户手上。
            // 同一族陷阱见 [cacheShareUri]：那颗的返回类型也**写死成 Uri?**，
            // 批量那一路（[shareGalleryPosts]）就是从这儿分出去的。
            val uri: Uri = cacheShareUri(context, target, bytes) ?: run {
                Toast.makeText(context, "分享没准备好 🥲 换一张，或先保存再分享", Toast.LENGTH_LONG).show()
                return@onSuccess
            }
            runCatching {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = GallerySaver.mimeOf(target)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "${target.site.displayName} #${target.id} ${target.pageUrl}",
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "分享图片"))
            }.onFailure { e ->
                android.util.Log.w("VeneraDebug", "gallery share chooser failed", e)
                Toast.makeText(context, "分享没打开 🥲 先保存到相册再分享", Toast.LENGTH_SHORT).show()
            }
        }
        .onFailure { e ->
            android.util.Log.w("VeneraDebug", "gallery share fetch failed", e)
            Toast.makeText(context, "这张图没取下来 🥲 稍后再试一次", Toast.LENGTH_LONG).show()
        }
}

/**
 * **批量**分享：一次 chooser，把选中的每一条原档一起交出去。
 *
 * 为什么不能拿单张那颗在循环里按 N 次：`startActivity(createChooser(...))` 一次开一扇系统面板，
 * 选 5 张就是连开 5 扇，而循环不会等第一扇关。Android 对多文件的正解是
 * `ACTION_SEND_MULTIPLE` + `EXTRA_STREAM` 装 `ArrayList<Uri>` —— 一扇面板、N 份附件。
 *
 * 只有 1 张时**退回单张那条路**：两条系统面板长得不同，而单张那一路的行为已经在大图页上验过，
 * 不为一枚批量钮去动它。
 *
 * 每一份都要先现取原档（视频实测 16~26 MB），所以带不上的必须数出来并说一次：
 * 少一份而屏上没说法，读起来就是"按了没反应"。
 */
internal suspend fun shareGalleryPosts(context: Context, targets: List<GalleryPost>) {
    if (targets.isEmpty()) return
    if (targets.size == 1) {
        shareGalleryPost(context, targets.single())
        return
    }
    val uris = ArrayList<Uri>(targets.size)
    val links = StringBuilder()
    var notCarried = 0
    for (target in targets) {
        val bytes = GallerySaver.fetchBytes(context, target).getOrNull()
        val uri = bytes?.let { cacheShareUri(context, target, it) }
        if (uri == null) {
            notCarried++
            continue
        }
        uris.add(uri)
        links.appendLine("${target.site.displayName} #${target.id} ${target.pageUrl}")
    }
    if (uris.isEmpty()) {
        Toast.makeText(context, "这几张都没取下来 🥲 稍后再试一次", Toast.LENGTH_LONG).show()
        return
    }
    runCatching {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            // MIME 取第一条那一档：混合类型（png + mp4）时系统面板自己按附件挑，
            // 这里猜一个反而会把视频那几份压成 image/*。
            type = GallerySaver.mimeOf(targets.first())
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_TEXT, links.toString().trimEnd())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "分享图片"))
    }.onFailure { e ->
        android.util.Log.w("VeneraDebug", "gallery batch share chooser failed", e)
        Toast.makeText(context, "分享没打开 🥲 先保存到相册再分享", Toast.LENGTH_LONG).show()
    }
    if (notCarried > 0) {
        Toast.makeText(
            context,
            "$notCarried 张没取下来，这次带上了 ${uris.size} 张",
            Toast.LENGTH_LONG,
        ).show()
    }
}

/**
 * 把一份**已经取到手**的原档落进缓存目录，换成外部应用读得到的 Uri。
 *
 * 失败给 null 且**不出声** —— 说哪一句由调用方按自己那一档定（单张说"换一张"，
 * 批量说"第几张没带上"）。这里出声会变成两份真相。
 *
 * 落盘要在 IO 上：视频原片实测 16~26 MB，跟在 Main.immediate 的续点上写就是拿主线程写几十兆，
 * 能卡出 ANR。
 */
private suspend fun cacheShareUri(context: Context, target: GalleryPost, bytes: ByteArray): Uri? =
    runCatching {
        withContext(Dispatchers.IO) {
            val dir = CacheDirs.inCache(context.cacheDir, CacheDirs.SHARED_IMAGES)
            val name = "${target.site.routeKey}-${target.id}.${target.fileExt.ifBlank { "bin" }}"
            val file = File(dir, name)
            file.outputStream().use { it.write(bytes) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }.getOrElse { e ->
        android.util.Log.w("VeneraDebug", "gallery share cache write failed", e)
        null
    }
