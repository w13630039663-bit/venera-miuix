package com.venera.compose.data.platform.android

import android.content.Context
import com.venera.compose.data.platform.PathProvider
import java.io.File

/**
 * Android 侧实现：dataRoot 走系统私有文件目录（filesDir），cacheRoot 走 cacheDir。
 * 两者由系统保证存在且可写；本类不引 androidx 之外的新依赖。
 */
class AndroidPaths(context: Context) : PathProvider {
    override val dataRoot: File = context.filesDir
    override val cacheRoot: File = context.cacheDir
}
