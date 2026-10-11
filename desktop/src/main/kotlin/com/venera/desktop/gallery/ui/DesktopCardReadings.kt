package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost

/**
 * 卡底与 hero 那几行"读数"的口径 —— 全站唯一出处。
 *
 * ## 为什么单独一颗件
 *
 * 同一句话今天在三处各拼一遍（卡底 `DesktopGalleryPostTile`、hero 的大位、hero 的"被挡那三条"）。
 * 三份算式的差别不会报错，只会**漏改那一份继续念假读数** —— 这一批要修的正是这种形状。
 *
 * ## `0` 在这仓里从来不是一个数，是一句需要出处的事
 *
 * [GalleryPost.score] 是非空 `Int`、缺省 0（`GalleryPost.kt:46`），而 Gelbooru **真会给 `"score":0`**
 * （实测原文 `GelbooruClient.kt:349`）⇒ "零分"与"这一站没给这个数"在屏上是同一句话。
 * 同一条口径在仓库里早有先例：`favCount` 干脆是 `Int?`（`:53`）、Android 那张表用 `fileSize > 0`
 * 挡着（`GalleryInfoSheet.kt:437`）、分辨率取不到就**整格不摆**（`:429`）。
 * 标签那半同族：`tags` 缺省空串 ⇒ `tagList` 空既可能是"站方真没给"也可能是"这条没解出来"，
 * 所以「标签 0 枚」同样不摆。两半都没有 ⇒ **整行不摆**。
 */
internal fun desktopCardMetricLine(post: GalleryPost): String? =
    listOfNotNull(
        desktopScoreFragment(post),
        "标签 ${post.tagList.size} 枚".takeIf { post.tagList.isNotEmpty() },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

/**
 * 分数那一小段，单独露出来是因为 hero 的"屏上的头三条"那一格里**只有分数**没有标签
 * （三条各带一串标签就没法在一枚小卡里读）。
 *
 * ⚠️ 这一颗存在的另一件事：让"零分不摆"这条判据**只有一处**。三处各写一遍 `score > 0`
 * 的话，漏改的那一处就继续念「分数 0」—— 而那正是本批要修的形状。
 */
internal fun desktopScoreFragment(post: GalleryPost): String? = "分数 ${post.score}".takeIf { post.score > 0 }

/**
 * 一次头像现取的**三态**。
 *
 * 为什么要三态而不是"地址或 null"：两件事完全不一样 ——
 * 站方**没有**这一档（定论，可以落档、以后不必再问）与**没问到**（不是定论，
 * 落档就等于替用户宣布"这位画师没有头像"，而且这一屏再也不问）。
 * 第十批 ② 定的原则是「只有定论才落档」，那颗把失败压成 `null` 的写法把它退化了。
 */
internal sealed interface DesktopFaceProbe {
    /** 取到了。 */
    data class Found(val url: String) : DesktopFaceProbe

    /** 够得着的路都走完了，站方确实没有这一档 —— **这是定论**。 */
    data object DefinitiveNone : DesktopFaceProbe

    /** 没问到（网络、凭据、站方没答）。**这不是定论**。 */
    data class Failed(val message: String) : DesktopFaceProbe
}

/**
 * 把一次探测的结果记进两张表：**定论落档**、**试过的记名**。
 *
 * 两张都要，少一张各出一族毛病：
 * - 只记定论 ⇒ 来回滚动时"没问到"的那几位每帧重发（无限请求）；
 * - 把失败也落进定论表 ⇒ 一次抖动 = 永久字母座。
 * 所以 [DesktopFaceProbe.Failed] 只进 `tried`：这一屏内不再发，换一次列表（`readout` 变 ⇒
 * 两张表都重建）就允许再问。
 */
internal fun desktopFaceProbeRecord(
    probed: MutableMap<String, String?>,
    tried: MutableSet<String>,
    name: String,
    probe: DesktopFaceProbe,
) {
    when (probe) {
        is DesktopFaceProbe.Found -> probed[name] = probe.url
        DesktopFaceProbe.DefinitiveNone -> probed[name] = null
        is DesktopFaceProbe.Failed -> Unit
    }
    tried += name
}

/**
 * 三态各自要不要在屏上留一句。
 *
 * ⚠️ 失败那句**只进日志**、不上屏：一屏几十位画师，一位网络抖动就在屏上留一行红字，
 * 读的人只会以为"这一域出了事"。所以这里给的是**给日志用**的那句（含成因头一段，便于报障），
 * 而屏上保持字母座 —— "没问到"与"没有"在屏上同形是刻意的，两者的差别记在日志与这份注释里。
 */
internal fun desktopFaceProbeNotice(probe: DesktopFaceProbe): String = when (probe) {
    is DesktopFaceProbe.Found -> ""
    DesktopFaceProbe.DefinitiveNone -> "没有"
    is DesktopFaceProbe.Failed -> "没取到：${probe.message}"
}
