package com.venera.sharedprobe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * 安卓侧那颗入口：`am start -n <应用包名>/com.venera.sharedprobe.ProbeActivity` 能把它调出来。
 *
 * 它声明在**本模块自己的** manifest 里（`src/androidMain/AndroidManifest.xml`），靠 manifest 合并
 * 进 `:app` —— 所以 `:app` 只加一行 `implementation(project(":shared-probe"))`，
 * 不动 `app/src/main/AndroidManifest.xml`，也不动任何现有页面（§6.4 的口径）。
 *
 * `exported=true` 是为了让 adb 从 shell 那侧起得来（本机真机取证只用 `am start`，不碰鼠标）。
 * 这一颗随探针模块一起存在、一起删除，A1 建正式 `:shared` 时不带走它。
 */
class ProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Hello() }
    }
}
