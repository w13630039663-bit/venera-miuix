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

    /**
     * 下载队列。
     *
     * 与源管理同一族：它在安卓不是底栏那一格（安卓把它藏在漫画域首页的一行里），
     * 桌面这批没有那一层域首页，所以做成二级页路由，由探索屏顶栏的入口进。
     *
     * 为什么这一屏**必须**进得来：第一百零五批起它只读不驱动的那一格已经改过（泵常驻），
     * 而用户要看得见"排了几笔、跑到第几页、哪一笔失败了"才停得下手。
     * 从前它只有代码没有门 —— 等于那一屏没做。
     */
    data object Queue : DesktopRoute

    /**
     * 本机已下载。
     *
     * 与 `Queue` 同一族、同一个入口位置（探索屏顶栏）：安卓把它藏在漫画域首页的一行里，
     * 桌面这批没有那一层域首页，所以做成二级页路由。
     *
     * 为什么这一档**必须**存在：读侧（数盘上那一棵树）、删除那两步（武装 → 再按才真删 + 回读盘定档）、
     * 补齐那一跳（只入队而不动盘）、以及"什么时候该再读一次盘"那一枚键，四件事从批 97 起就都有判据盯着，
     * 而当前这条渲染路里**没有一屏**把它们摆出来 —— 旧的那一屏属于已被拆掉的 Fluent 平行线。
     * 于是"盘上有什么 / 缺哪几页 / 怎么补 / 怎么删"用户今天看不见也改不了。这一批把门开上。
     */
    data object Downloads : DesktopRoute

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
 * **2026-10-21 主人裁决（A 条）**：值从 `"jm"` 改成 `"copy_manga"`。
 * 理由是内容安全，不是技术：安卓预设表里
 * `app/src/main/assets/source_content_warning.json` 明写着
 * `"jm": { "level": "nsfw" }`，而桌面默认遮罩档是 `OFF`（照安卓抄，
 * `DesktopGalleryContentGuard.kt:138`）⇒ 两件事叠加，成人封面就裸铺在探索屏上了。
 * `copy_manga` 在同一份表里是 `level: "safe"`（"拷贝漫画：综合向"），
 * 且 `app/src/main/assets/sources/copy_manga.js`（1108 行）确实带 `explore`，
 * 所以换过去不会换来一片空屏。
 *
 * ⚠️ 这条换的只是**桌面这一颗常量**，有三件事它管不到，别读成"安全侧都做完了"：
 *  1. **默认遮罩档仍按主人裁决保持 `OFF`**（不改，以免与安卓 `ContentGuardManager.kt:82-89`
 *     分叉）⇒ 这一格收口靠的是"源头换成 safe 站"，不是靠遮罩。
 *  2. **历史与收藏里已存在的 `jm` 条目不受影响** —— 它们仍按 `sourceKey` 各自的值走，
 *     只有**新**进探索/详情/阅读器的那一路跟着这颗常量换。
 *  3. **`VeneraDesktop.kt:199` 那枚 `startKey = "jm"` 不是这一颗** —— 它只服务
 *     无人值守取证那一路（`runAutofavEvidence` 与 `D_启动 起始=` 那句读数），
 *     与用户看的探索屏无关，所以主人这次裁决**不牵动它**。
 *     （旧注释写过"值取 jm 与 `D_启动 起始=jm` 一致"，那是把两枚旋钮说成一枚，本轮改掉。）
 *
 * ⚠️ 这是**桌面自持的决定**，不是从安卓抄的：安卓由用户偏好决定起始源，
 * 桌面还没有"选默认源"的 UI。源管理屏本轮已经接上入口，可那一屏现在只有
 * 「启停 + 遮罩档 + 屏蔽 AI」三格，**没有"设为默认源"这一格** ⇒ "改成读偏好"仍欠着；
 * 到时散在各处的硬编码会漂，所以现在仍收成这一颗。
 */
internal const val DESKTOP_EXPLORE_SOURCE: String = "copy_manga"
