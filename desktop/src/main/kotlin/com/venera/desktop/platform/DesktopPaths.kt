package com.venera.desktop.platform

import com.venera.compose.data.platform.PathProvider
import java.io.File
import java.io.IOException

/**
 * 桌面侧的数据目录门面：唯一来源是 `%LOCALAPPDATA%\venera`。
 *
 * S0-9 的判据是"数据落 `%LOCALAPPDATA%`"。这条要么成立要么当场炸，
 * 不能"取不到就退回临时目录"—— 退回 tmp 等于把判据假通过，且用户数据会在清临时目录时蒸发。
 */
class DesktopPaths private constructor(override val dataRoot: File, override val cacheRoot: File) : PathProvider {
    companion object {
        /** envProvider 可注入：判据是"拿不到 LOCALAPPDATA 就抛"，那就必须能在测试里造出拿不到的情形。 */
        fun create(envProvider: (String) -> String? = System::getenv): PathProvider {
            val base = envProvider("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("环境里没有 LOCALAPPDATA，桌面数据目录无从谈起")
            val root = File(base, "venera")
            val cache = File(root, "cache")
            if (!root.mkdirs() && !root.isDirectory) throw IllegalStateException("建不出目录 $root")
            val probe = File(root, ".write-probe")
            try {
                probe.writeText("ok")
                // "写得进却读不出"不设文件系统注入缝：writeText 成功后回读内容仍对不上，真机构造不出来，为它引抽象不值。
                if (probe.readText() != "ok") throw IllegalStateException("$root 写得进却读不出")
            } catch (e: IOException) {
                // 真机实证：打包 exe 在探测写入上抛过 FileNotFoundException（拒绝访问），错误面统一收敛为 IllegalStateException
                throw IllegalStateException("探测读写失败 $root：${e.message}", e)
            } finally {
                probe.delete()
            }
            return DesktopPaths(root, cache)
        }
    }
}
