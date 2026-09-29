package com.venera.compose.gallery.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * "现在这条网络计不计费" —— 动图「仅 Wi-Fi」那一档唯一的外部读数。
 *
 * 全仓此前没有任何 `ConnectivityManager` 用法（2026-09-29 复核过），所以这是新层，
 * 配套要在 manifest 补 `ACCESS_NETWORK_STATE`（普通权限，不弹运行时窗）。
 *
 * 判据不是"是不是 Wi-Fi 传输"而是"是不是**不计费**"：以太网/多数热点也算不计费，
 * 而 Wi-Fi 连上一个计费网络时算计费 —— 这一档要防的是"移动网络下偷跑流量"，
 * 用 capability 比 transport 更贴这个意图。
 *
 * **拿不到读数一律返回 false**（按计费处理）：没权限、没活动网络、系统没给 capabilities
 * 这三种情况下，宁可让动图停在首帧，也不替用户花流量。
 */
object GalleryConnectivity {

    fun isUnmetered(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
