package com.venera.compose.data.platform.android

import android.util.Log
import com.venera.compose.data.platform.Logger

/** Android 侧实现：逐枚原样转 `Log.w` / `Log.i`，不吞、不降级成 println。 */
object AndroidLogger : Logger {

    override fun warn(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun info(tag: String, message: String) {
        Log.i(tag, message)
    }
}
