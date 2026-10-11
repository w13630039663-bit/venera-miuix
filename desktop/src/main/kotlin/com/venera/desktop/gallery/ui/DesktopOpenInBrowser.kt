package com.venera.desktop.gallery.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI

/**
 * 「交给系统浏览器打开」这一族（批 77 · 下半 从浮层那颗文件里搬出来，不是新写的）。
 *
 * ## 为什么这一批要搬
 *
 * 从前只有详情浮层用得到它，所以它写在那颗文件的角落。本批「用本机的一张图找」那一格
 * 的结果里也有「打开来源」—— 命中的那批大半是我们那两站之外的页，本机没有按站方编号
 * 开大图页的那一层（那一句逐字写在 [desktopReverseSiteRefReading] 里），能给的只有来源页。
 * 两处各写一遍 `Desktop.isDesktopSupported()` 与那句失败读数，就是"同一件事两把尺子"那一族
 * （浮层那颗文件自己的注释早就写着「这一枚与下面结果那一格共用同一个判定……两处各问一遍会漂」，
 * 只是从前真的只有一处在问）。
 *
 * ## 能力位与那枚按钮的形状
 *
 * [desktopCanBrowseExternally] 回答的是"这台机器给不给这条路由"，**不是**"这一张有没有地址"。
 * 界面按它决定摆不摆那一枚，而不是摆一枚按下去什么都不发生的按钮（批 54 立的那条口径，
 * 判据 `DesktopGalleryLocalReverseTest` ⑯ 钉的是这一枚表达式在全树只有一份出处）。
 */

/** 这台机器给不给"交给系统浏览器"这条路。无头、不支持、读不出支持位都算不给。 */
internal fun desktopCanBrowseExternally(): Boolean = runCatching {
    Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
}.getOrDefault(false)

/**
 * 开浏览器。成功回 null，失败回那句要摆上屏的话。
 *
 * ⚠️ 这条在 main 上是**零调用点起的第一枚** `java.awt.Desktop`：本仓公开错过一回把
 * "有路没接"写成"桌面做不到"，所以这里点名 —— AWT `Desktop.browse` 在 JVM 侧是可用的，
 * 缺的从来只是接线。它会把调用交给系统 shell，慢的时候是几百毫秒级，所以挪出组合线程。
 *
 * ⚠️ 地址那一枚**逐字交给系统**：这里不拼、不补协议、不改查询串，
 * 站方给的是什么就打开什么（改过的那一串就不是刚才屏上念的那一条了）。
 */
internal suspend fun openInBrowser(url: String): String? {
    if (url.isBlank()) return "这一张站方没给原图地址"
    return withContext(Dispatchers.IO) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
            .fold(onSuccess = { null }, onFailure = { "打开浏览器没成：${it::class.java.simpleName}" })
    }
}
