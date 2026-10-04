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
            val uri: Uri = runCatching {
                // 落盘要在 IO 上：视频原片实测 16~26 MB，
                // 跟在 Main.immediate 的续点上写就是拿主线程写几十兆，能卡出 ANR。
                withContext(Dispatchers.IO) {
                    val dir = CacheDirs.inCache(context.cacheDir, CacheDirs.SHARED_IMAGES)
                    val name = "${target.site.routeKey}-${target.id}.${target.fileExt.ifBlank { "bin" }}"
                    val file = File(dir, name)
                    file.outputStream().use { it.write(bytes) }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
            }.getOrElse { e ->
                Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_LONG).show()
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
                Toast.makeText(context, "分享没打开：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        .onFailure { e ->
            Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_LONG).show()
        }
}
