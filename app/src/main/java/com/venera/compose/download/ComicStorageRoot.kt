package com.venera.compose.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import com.venera.compose.data.prefs.VeneraPreferences
import java.io.File

/**
 * 本地漫画存储根目录的唯一事实源。
 *
 * 下载写入、书架扫描、设置页显示三处必须读同一个根，否则会出现「设置里说的目录
 * 和真正落盘的目录不是一回事」。
 */
object ComicStorageRoot {

    /** 未自选目录时的默认根：应用私有内部存储，随应用卸载而清理。 */
    fun defaultDir(context: Context): File = File(context.filesDir, "downloads")

    /**
     * 任务清单恒定存放处：换存储根不能让进行中的队列整体失联，
     * 所以它始终留在私有目录，不跟随漫画数据搬家。
     */
    fun tasksFile(context: Context): File = File(defaultDir(context), "download_tasks.json")

    /** 当前生效的存储根（偏好为空时=默认根）。只做路径解析，不建目录、不回落。 */
    fun resolve(context: Context): File {
        val configured = VeneraPreferences.getInstance(context).comicStoragePath.value
        return if (configured.isBlank()) defaultDir(context) else File(configured)
    }

    /** 目录不存在时创建根与 .nomedia（防离线漫画污染系统相册）。 */
    fun ensureRoot(dir: File): Boolean {
        if (!dir.exists() && !dir.mkdirs()) return false
        runCatching { File(dir, ".nomedia").let { if (!it.exists()) it.createNewFile() } }
        return dir.isDirectory
    }

    /**
     * 可写性判定不信权限位，而是真写一个探针文件再读回删除：
     * 分区存储下「有授权」和「写得进去」不等价，且用户随时可在系统设置里撤销。
     */
    fun probeWritable(dir: File): Boolean = runCatching {
        if (!dir.exists() && !dir.mkdirs()) return false
        val probe = File(dir, ".venera_write_probe")
        probe.writeText("probe")
        val ok = probe.length() == 5L
        probe.delete()
        ok
    }.getOrDefault(false)

    /**
     * SAF 目录选择器返回的 tree uri 换算成真实文件系统路径。
     *
     * documentId 形态：`primary:Download/漫画`（主存储）、`1234-ABCD:/path`（外置卷）、
     * `raw:/storage/emulated/0/...`（部分 ROM 直接给原始路径）。换算不出真实路径的
     * 虚拟根返回 null，由调用方如实报错，绝不退化成「先记下 uri 再静默写别处」。
     *
     * 返回的是 absolutePath 而非 canonicalPath：部分机型 `/storage/emulated/0` 是绑定挂载，
     * 规范化后可能变成用户完全认不出来的 `/mnt/...` 形态，也会让落盘位置与文件管理器里
     * 看到的路径对不上。准入判定里再按 canonical 比一次，防的就是这种形态差。
     */
    fun resolveTreePath(context: Context, treeUri: Uri): String? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val sep = docId.indexOf(':')
        if (sep < 0) return@runCatching null
        val volume = docId.substring(0, sep)
        // 不提前吃掉前导斜杠：`raw:` 后面就是完整绝对路径，
        // 而 File(卷根, "/Download/漫画") 本身也能正确拼接。
        val relative = docId.substring(sep + 1)
        when {
            volume.equals("raw", ignoreCase = true) ->
                if (relative.startsWith(File.separator)) File(relative).absolutePath else null

            volume.equals("primary", ignoreCase = true) ->
                File(Environment.getExternalStorageDirectory(), relative).absolutePath

            else -> storageVolumePath(context, volume)?.let {
                File(it, relative).absolutePath
            }
        }
    }.getOrNull()

    private fun storageVolumePath(context: Context, uuid: String): String? {
        val manager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return null
        val fallbackRoot = File("/storage/$uuid")
        return manager.storageVolumes
            .firstOrNull { runCatching { it.uuid }.getOrNull()?.equals(uuid, ignoreCase = true) == true }
            ?.let { runCatching { it.directory?.absolutePath }.getOrNull() }
            ?: fallbackRoot.takeIf { it.isDirectory }?.absolutePath
    }

    /**
     * 所选目录的准入判定，返回拒绝理由（null=可接受）。
     *
     * 整块存储根会把 .nomedia 糊满全盘并让用户误删系统级目录；`Android/` 是系统保护区，
     * 即便用户能选到也不该当漫画库。
     */
    fun rejectReason(path: String): String? {
        val file = File(path)
        if (!file.isAbsolute) return "所选目录路径无法解析：$path"
        // raw 与 canonical 两种形态都比：绑定挂载下二者不同，只比一种会误判成「不在公共存储下」
        val forms = listOf(path, runCatching { file.canonicalPath }.getOrDefault(path)).distinct()
        val roots = forms.flatMap { volumeRootCandidates(it) }.distinct()
        if (roots.none { root -> forms.any { isUnder(root, it) } }) {
            return "只能选择公共存储或外置卡下的目录，当前路径：$path"
        }
        if (roots.any { root -> forms.any { it == root } }) {
            return "不能选择整块存储根目录，请进入一个子文件夹"
        }
        if (roots.any { root -> forms.any { isDirectChild(it, root, "android") } }) {
            return "Android 目录是系统保护区，不能用作漫画库目录"
        }
        return null
    }

    private fun isUnder(root: String, path: String): Boolean =
        path == root || path.startsWith(root + File.separator)

    private fun isDirectChild(path: String, root: String, name: String): Boolean =
        isUnder(root, path) &&
            path.removePrefix(root + File.separator)
                .substringBefore(File.separator)
                .equals(name, ignoreCase = true)

    private fun volumeRootCandidates(path: String): List<String> {
        val primary = listOfNotNull(
            runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrNull(),
            runCatching { Environment.getExternalStorageDirectory().canonicalPath }.getOrNull(),
        )
        // 外置卡挂载点形如 /storage/1234-ABCD，其本身就是一个卷根
        val secondary = if (path.startsWith("/storage/") && !path.startsWith("/storage/emulated")) {
            listOf("/storage/" + path.removePrefix("/storage/").substringBefore(File.separator))
        } else emptyList()
        return (primary + secondary).filter { it.isNotBlank() }
    }

    /** 当前根里已有的漫画数与章节数，用于切换前的迁移确认文案。 */
    fun counts(dir: File): Pair<Int, Int> = runCatching {
        if (!dir.isDirectory) return@runCatching 0 to 0
        var comics = 0
        var chapters = 0
        dir.listFiles { f -> f.isDirectory }?.forEach { sourceDir ->
            sourceDir.listFiles { f -> f.isDirectory }?.forEach { comicDir ->
                if (File(comicDir, "comic_info.json").exists()) {
                    comics++
                    chapters += comicDir.listFiles { f -> f.isDirectory }?.size ?: 0
                }
            }
        }
        comics to chapters
    }.getOrDefault(0 to 0)

    /**
     * 把旧根下的源目录整体搬到新根。跨分区 rename 失败时降级 copy 再删源；
     * 任一目录失败即抛出，绝不留下「两边各有一半」还报成功。
     */
    fun migrate(oldDir: File, newDir: File): Result<Int> = runCatching {
        require(oldDir.absolutePath != newDir.absolutePath) { "新旧目录相同，无需迁移" }
        require(ensureRoot(newDir)) { "目标目录不可用：${newDir.absolutePath}" }
        val sourceDirs = oldDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty()
        var moved = 0
        for (srcDir in sourceDirs) {
            val target = File(newDir, srcDir.name)
            if (target.exists()) throw IllegalStateException("目标目录已存在同名项：${target.absolutePath}")
            if (!srcDir.renameTo(target)) {
                // copyRecursively 失败返回 false 而不抛异常，不判就会两边各有一半还报成功
                if (!srcDir.copyRecursively(target, overwrite = false)) {
                    throw IllegalStateException("复制到 ${target.absolutePath} 失败，旧目录保持原样未动")
                }
                if (!srcDir.deleteRecursively()) {
                    throw IllegalStateException("已复制到 ${target.absolutePath}，但旧目录删不掉，请手动清理")
                }
            }
            moved++
        }
        moved
    }
}
