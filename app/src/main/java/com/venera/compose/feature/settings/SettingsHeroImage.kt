package com.venera.compose.feature.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页 hero 头图 / 名言卡小图的**拷贝落盘链**。
 *
 * 为什么必须拷贝而不是存 content URI：相册可能删图、URI 授权可能失效，
 * persisted URI 在那两种情况下都会变成裂图 —— 拷一份进 `filesDir` 是唯一
 * 能保证「设置过就一直显示」的做法。代价是占一份私有存储，所以换图时
 * 旧文件必须立刻删掉（同类目只保留最新一张，见 [FILE_BASENAME]）。
 *
 * 文件名固定为 `image.<ext>`：不是「内容寻址」而是「槽位寻址」——
 * 一张头图没有历史版本的意义，按槽位覆盖能让「换图 = 写文件 + 删旧扩展名残留」
 * 两步走完，不需要任何清理扫描。
 */
internal object SettingsHeroImageStore {

    /** 头图槽位（首页与全部子页共用一张）。 */
    const val KIND_HERO = "hero"

    /** 名言卡左侧小图槽位。 */
    const val KIND_QUOTE_AVATAR = "quote_avatar"

    /** 同一类目下的固定文件名主干；换图即覆盖。 */
    const val FILE_BASENAME = "image"

    /**
     * 由文件头嗅探图片扩展名。为什么不信任 `GetContent` 给的文件名：
     * provider 返回的 display name 可能是空的、可能是 `.bin`，而落盘扩展名
     * 决定了 Coil 能不能凭后缀选对解码器（本地文件不走 content type）。
     *
     * 只认三种最常见格式；认不出的退回 `.jpg` —— Coil 对本地文件会再嗅一次，
     * 扩展名只是第一道提示，宁可普通也不要存成无法解码的 `.bin`。
     */
    fun sniffExtension(head: ByteArray): String {
        if (head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()) {
            return "jpg"
        }
        if (head.size >= 8 &&
            head[0] == 0x89.toByte() && head[1] == 0x50.toByte() && head[2] == 0x4E.toByte() && head[3] == 0x47.toByte()
        ) {
            return "png"
        }
        if (head.size >= 12 &&
            head[0] == 0x52.toByte() && head[1] == 0x49.toByte() && head[2] == 0x46.toByte() && head[3] == 0x46.toByte() &&
            head[8] == 0x57.toByte() && head[9] == 0x45.toByte() && head[10] == 0x42.toByte() && head[11] == 0x50.toByte()
        ) {
            return "webp"
        }
        if (head.size >= 6 && head[0] == 0x47.toByte() && head[1] == 0x49.toByte() && head[2] == 0x46.toByte()) {
            return "gif"
        }
        return "jpg"
    }

    /** 类目目录：`filesDir/settings/<kind>/`。 */
    fun dirFor(context: Context, kind: String): File = File(context.filesDir, "settings/$kind")

    /**
     * 把已落盘路径还原成可显示的文件。
     *
     * 三种情况都必须返回 null（页面据此走「未设置」分支，绝不显示裂图）：
     * 路径为空 / 指向的文件已被清掉（用户清了应用缓存目录之外的数据、卸载重装）/
     * 文件存在但是空的。**绝不**把 prefs 里的路径当真 —— 那只是一张收据。
     */
    fun resolve(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) return null
        return file
    }

    /**
     * 把 `uri` 指向的图片拷进类目槽位，返回绝对路径；任何一步失败都返回 null
     * （调用方负责 toast，这里不打日志不抛异常 —— 选一张图失败了不该崩任何东西）。
     *
     * 落盘前会删掉同类目下旧扩展名的残留（用户先选了 .png 又换 .jpg 时，
     * prefs 路径会指向新文件，旧 .png 不删就成了永远读不到的死存储）。
     */
    suspend fun copyToPrivateDir(context: Context, uri: Uri, kind: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = dirFor(context, kind)
                dir.mkdirs()
                val head = ByteArray(16)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val read = input.read(head)
                    require(read >= 4) { "图片流太短，读不出文件头" }
                    val ext = sniffExtension(head.copyOf(read))
                    val target = File(dir, "$FILE_BASENAME.$ext")
                    context.contentResolver.openInputStream(uri)?.use { source ->
                        target.outputStream().use { out -> source.copyTo(out) }
                    }
                    if (target.length() <= 0L) error("落盘后是空文件")
                    // 旧文件（其他扩展名的残留）在新文件写成功**之后**删：先删后写，
                    // 写一半失败会把上一次还能显示的图也赔进去。
                    (dir.listFiles().orEmpty()).filter { it.name != target.name }.forEach { it.delete() }
                    target.absolutePath
                } ?: error("打不开所选图片")
            }.getOrNull()
        }

    /** 清掉一个类目的整张图（目录里只有一个槽位，全删即可）。 */
    fun clear(context: Context, kind: String) {
        dirFor(context, kind).listFiles().orEmpty().forEach { it.delete() }
    }
}

/**
 * 「选一张图 → 拷进私有槽位 → 写 prefs」三步的公共入口。
 *
 * 三个调用方（外观页两行 + hero 卡上的相机钮 + 名言卡小图）共用这一份，
 * 失败 toast 的文案也只在这里出现一次。返回一个零参启动函数，调用方挂在自己的 onClick 上。
 */
@Composable
internal fun rememberSettingsImagePicker(
    kind: String,
    onSaved: (path: String?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = SettingsHeroImageStore.copyToPrivateDir(context, uri, kind)
            if (path == null) {
                Toast.makeText(context, "图片读取失败，换一张试试", Toast.LENGTH_SHORT).show()
            }
            onSaved(path)
        }
    }
    return { launcher.launch("image/*") }
}
