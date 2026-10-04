package com.venera.compose.data.api

import com.venera.compose.data.update.ReleaseAsset
import com.venera.compose.data.update.UpdateDownload
import kotlinx.coroutines.flow.StateFlow

/**
 * 应用内更新那一笔下载的出口（`data/api/` 的契约层，2026-10-04）。
 *
 * ## 为什么 UI 不直接拿那颗单例
 *
 * 因为本仓的分层守卫把「UI 里出现 `X.getInstance(...)`」判成违规，而且判语写得很直白：
 * **要开口就加契约，别直连**（`BusinessApiBoundaryTest` 的断言 B 与 E，白名单只许缩短）。
 * 这条链落地时确实撞上了那两条 —— 撞对了，说明守卫还在工作，所以这里是加端口而不是加白名单。
 *
 * ## 面上只有三件
 *
 * [state] 给界面读，[start] 与 [cancel] 给按钮按。装包那一步不在这里 ——
 * 它要的是 `Context` 与系统授权，留在 `ApkInstallLauncher`，界面直接调那个对象
 * （它不是单例、也不在实现类后缀表上，所以既不是这条守卫的口径，也不欠一份契约）。
 *
 * [start] 收的是**已经挑好的那一份产物**（`pickApkAsset` 的结果），而不是整条发布记录：
 * "选哪一份"是纯函数该答的问题，界面答它、把答案递进来，这一层就只管下载本身。
 */
interface UpdateDownloadQueue {

    /** 这一笔下载的状态。恒为同一个实例的流（适配器交回单例，不新建）。 */
    val state: StateFlow<UpdateDownload>

    fun start(asset: ReleaseAsset)

    /** 用户点取消：停下来、删掉半截文件、撤掉通知。 */
    fun cancel()
}
