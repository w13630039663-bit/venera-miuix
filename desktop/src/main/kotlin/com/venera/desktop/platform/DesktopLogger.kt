package com.venera.desktop.platform

import com.venera.compose.data.platform.Logger

/**
 * `:desktop` 侧的 [Logger]。
 *
 * 存在的理由是**不许静默**：共享面里那两颗文件把"这次改动可能覆盖用户数据"（头像地址档没写成 /
 * 档读不出来已另存）与"分类页没取到，退到离线词典兜底"这两类事交给 [Logger] 上报。
 * Android 侧走 logcat，桌面没有对应物 —— 直接删掉那两句就成了本仓最忌的静默交错，
 * 所以这里落 stdout，与桌面既有的 `D_*` 读数一个形状（启动那几行 `D_接线`/`D_资源` 走的就是 stdout）。
 *
 * ⚠️ 只打这两枚成员给的字符串，**不许**在这一层加"顺手把 URL/请求头也带上"的便利：
 * 凭据与站点会话材料不进日志是硬约束，而调用方传进来的 message 已经是我们允许上报的全部内容。
 */
object DesktopLogger : Logger {

    override fun warn(tag: String, message: String) {
        println("$WARN_PREFIX [$tag] $message")
    }

    override fun info(tag: String, message: String) {
        println("$INFO_PREFIX [$tag] $message")
    }

    private const val WARN_PREFIX = "D_警告"
    private const val INFO_PREFIX = "D_日志"
}
