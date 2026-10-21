package com.venera.desktop

import com.venera.engineprobe.EngineSession

/**
 * 桌面二级页路由。
 *
 * ## 为什么不用导航库
 *
 * 这一批只有两层（详情 → 阅读器），真正要的行为是"点进去、按返回回来"。
 * 导航库会附赠一堆此刻用不上的约定（深链、进程恢复、回栈序列化），
 * 而引进来之后每一处都要给出对应的实现才算数 —— 那正是本仓反复记过的
 * "接了一堆空壳"。所以这里就是一个 sealed 接口 + 一个 `remember`。
 *
 * ## 为什么 `Reader` 里要带 `info` 与 `episode`
 *
 * 阅读器需要两样东西才能取页：这一本的 `comicId`（要从 `info` 里拿章节表之外的元信息）
 * 和这一话的 `Episode`。把它们塞进路由，切屏回来时不必重新问一遍源站。
 */
internal sealed interface DesktopRoute {

    /** 停在底栏某一格上（`currentTab` 决定是哪一格）。 */
    data object Tab : DesktopRoute

    /**
     * 源管理。
     *
     * 为什么不放进底栏：安卓底栏也只有 5 格（首页/收藏/搜索/画廊/探索），
     * 源管理在安卓是**设置里的一项**。桌面这一批还没有设置屏，所以先做成路由，
     * 由探索屏顶栏那个入口进 —— 不硬塞进底栏去凑数。
     */
    data object SourceManage : DesktopRoute

    /** 漫画详情。`sourceKey` 要带着：`EngineSession.details()` 前必须先 `load(sourceKey)`。 */
    data class Detail(
        val comicId: String,
        val title: String,
        val cover: String,
        val sourceKey: String,
    ) : DesktopRoute

    /** 阅读器。`info` 用它的 `title`/`cover` 写历史，`episode` 用来取页表。 */
    data class Reader(
        val comicId: String,
        val title: String,
        val cover: String,
        val sourceKey: String,
        val episode: EngineSession.Episode,
    ) : DesktopRoute
}


/**
 * 探索屏/详情/阅读器默认用哪一颗源。
 *
 * 值取 `"jm"`：与 `VeneraDesktop.kt` 里 `D_启动 起始=jm` 那条读数一致，
 * 且 `app/src/main/assets/sources/jm.js` 确实存在。
 *
 * ⚠️ 这是**桌面自持的临时决定**，不是从安卓抄的：安卓由用户偏好决定起始源，
 * 桌面还没有"选源"的 UI。等源管理界面接上，这个常量要改成读偏好 ——
 * 到那时散在各处的硬编码就会漂，所以现在就收成一颗。
 */
internal const val DESKTOP_EXPLORE_SOURCE: String = "jm"
