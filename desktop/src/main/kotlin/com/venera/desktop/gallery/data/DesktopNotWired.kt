package com.venera.desktop.gallery.data

/**
 * 桌面今天**没接**的两条路，各用一个有名字的异常承载。
 *
 * 为什么不返回 `Result.success(空表)` / `success(null)`：那三种状态在界面上必须是三句话 ——
 * "没取到"（网络/超时）、"没有"（站方确实零条）、"没接"（本机压根没这条端点）。
 * 前两种由既有链路如实交出，第三种一旦混进前两种，用户就会对着一个"这张画没有头像"的读数
 * 去找根本不存在的开关。名字点名缺席的那颗件，界面可以直接把它念出来。
 *
 * ⚠️ 这两枚是**本轮拍板的缺席**（Pixiv 不接入桌面；以图搜图那一路上面没有可上传的图片来源），
 * 不是"忘了接"。哪天真要接，先删掉这里的一颗，编译会带出所有需要改的消费点。
 */
class PixivNotWiredOnDesktop : IllegalStateException(
    "Pixiv 端点未接入桌面档（artworkAuthor / avatarUrl 这两路今天没有），" +
        "所以本行不代表该画师没有头像 —— 头像链会退回 fanbox / Mastodon 两路，最后才是首字母座",
)

class ReverseSearchNotWiredOnDesktop : IllegalStateException(
    "以图搜图未接入桌面档：SauceNAO 要的是\"把手上这张图交给站方比对\"，" +
        "而桌面今天既没有本地相册那一条源、也没有存图落点（GallerySaver 在排除清单里）",
)
