package com.venera.compose.data.platform

/**
 * 不落用户界面的诊断上报口。
 *
 * 存在的理由很窄：`GalleryArtistAvatarStore` 有两条 `Log.w`（头像地址档没写成 / 档读不出来已另存），
 * 报的是"这次改动可能覆盖用户数据"这一类**不许静默**的事。Android 侧走 `android.util.Log`，
 * 桌面没有对应物；直接把这两条删掉图省事，就成了本仓最忌的静默交错 —— 所以抽一颗口。
 *
 * ⚠️ 只有下面这两枚成员，各自都有真调用点：[warn] 是头像地址档那两条；[info] 是
 * `GalleryTagCategories` 那句"分类页没取到，退到离线词典兜底"（它原先写的是内联 FQN
 * `android.util.Log.i`，S1 把那颗文件放上桌面编译面时才浮出来）。
 * 补成员之前先问"有没有调用点"——本仓反复踩过的形状是"有接口、零消费方"。
 */
interface Logger {

    fun warn(tag: String, message: String)

    fun info(tag: String, message: String)
}
