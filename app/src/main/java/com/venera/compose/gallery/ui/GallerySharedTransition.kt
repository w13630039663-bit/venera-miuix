package com.venera.compose.gallery.ui

/**
 * 画廊「预览行 ↔ 每日热门二级页」那对封面共享元素的 key。
 *
 * 只做这一对，是因为它是画廊里**唯一**两端都在 NavHost 内、且两端都拿得到同一个 `uid` 的一对：
 * 首页主墙 / 搜索墙 / 收藏墙的对面是**跨 Activity** 的大图页，而 compose animation 1.12
 * 没有跨窗口的共享元素 API（查证过程与"别再重开"的结论记在记忆
 * 「封面共享元素转场现状与硬约束」）。那条路走的是 [GalleryFlyIn] 自己递两样输入
 * （起点那一帧 + 起点矩形），不是共享元素。
 *
 * 身份一律取 `GalleryPost.uid`（`"${site.routeKey}:$id"`）而**不是 `id`**：两站各自编号，
 * 同 id 是两张不同的图，撞 key 会让飞行体落到另一站的图上。
 *
 * 前缀与漫画侧那套（`ComicSharedTransition.coverKey` 的 `"cover-$sourceKey-$comicId"`）**刻意不同形**：
 * 同屏两边若同形会互相认领飞行体。
 *
 * 本文件刻意保持纯 Kotlin（不 import Compose），这样它能脱离 gradle 单跑
 * （`_probe/l0/run-judgment-tests.sh`）。用例见 `GallerySharedTransitionTest`。
 */
fun galleryCoverKey(uid: String): String = "gallery-cover-$uid"
