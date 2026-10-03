package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.data.platform.PathProvider
import com.venera.desktop.platform.DesktopHttpEngine

/**
 * 桌面画廊业务 API 的接线句柄。
 *
 * `GalleryPorts.of(handle: Any?)` 收的是不透明句柄，Android 侧那颗是 `Context`；桌面这边
 * 刻意做成一个**有名有姓**的形状，而不是把 `Any?` 再往下传一层：
 * - [paths] 决定所有落盘位置（磁盘档、缓存、KV 文件），
 * - [engine] 决定所有出网位置。
 * 这两枚就是画廊这一侧对平台的全部要求 —— 少一枚就接不出 `GalleryPorts`，多一枚就说明有人在
 * 往句柄里塞别的东西（那是第二份装配账的开头）。
 *
 * 没有 `storeFactory` 那一枚：`GalleryStoreFactory.open(name)` 要的只是 `PathProvider`，
 * 传进来等于给同一个存储留两个可能的来源。
 */
data class DesktopGalleryHandle(
    val paths: PathProvider,
    val engine: HttpEngine,
) {
    companion object {
        fun of(paths: PathProvider): DesktopGalleryHandle =
            DesktopGalleryHandle(paths, DesktopHttpEngine(paths))
    }
}
