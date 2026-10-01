package com.venera.compose.gallery.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.venera.compose.gallery.domain.GalleryChromeHidePolicy

/**
 * 「画廊上滑收起顶栏 / 底栏」的**跨层状态**。
 *
 * ## 为什么需要这么一层
 *
 * 触发在**页内**（画廊那一屏的滚动），但两个消费点分属两层：
 * - **顶栏**在页内（本项目顶栏是页内自治的，`VeneraTopAppBar` 由各页自己挂）；
 * - **底栏在壳层**（`Navigation.kt` 那两条 `VeneraLiquidGlassNavBar` /
 *   `VeneraFloatingNavBar` 覆盖在 NavHost 之上，页面根本够不着它）。
 *
 * 所以"页内算出来的收没收起"必须能被壳层读到。形状照 [GallerySearchHandoff] 的先例：
 * 一个对象 + 快照字段（**不是**普通 `var`）。用快照是因为壳层与页面在**同一组合树**里
 * 但不同分支：普通 `var` 的写入不会让壳层那一支重组，底栏就会卡在旧状态，
 * 而那表现为"底栏有时收有时不收"，最难查。
 *
 * ## 为什么两栏各一枚开关（而不是一枚管两栏）
 *
 * 用户 2026-09-30 明确要"分别添加个下滑收起顶栏和底栏的设置选项"（这句里的"下滑"当天就被
 * 更正成"上滑"了 —— 用户要的是**浏览时收起**，判据的符号依据见 [GalleryChromeHidePolicy] 类头注）：
 * 有人只想要"看图时底栏让位、顶栏留着看标题"，有人反过来。两枚开关互不联动。
 *
 * ## 为什么读的是偏好、写的却是页内
 *
 * 两枚开关是**偏好**（存在 `VeneraPreferences`，跟着用户走），但"此刻收没收"是**瞬时状态**
 * （离开画廊就该恢复）。所以：开关由画廊页从偏好读进来写进这里，`hidden` 由滚动写。
 * 壳层只读 [hidden] 与两枚开关，不自己判断 —— 判断只该有一处。
 *
 * ## ⚠️ 壳层必须带"只在画廊 Tab 生效"这道闸
 *
 * 否则在画廊收了底栏、切到首页它就永久不见了。判据在 `Navigation.kt` 里
 * （`currentTab == GALLERY` 且这条路由确实是画廊），见那里的注释。
 */
object GalleryChromeAutoHide {

    /** 「上滑收起顶栏」开关。默认关 —— 新能力不改变现有观感（与外观材质轴默认实色同一条惯例）。 */
    var topEnabled by mutableStateOf(false)

    /** 「上滑收起底栏」开关。默认关，理由同上。 */
    var bottomEnabled by mutableStateOf(false)

    /**
     * 此刻两栏收没收。
     *
     * 页内在滚动里写它（[GalleryChromeHidePolicy] 给出判定），壳层据此偏移底栏。
     * **两个开关都关着时它恒为 false**（由页内保证：开关关了就不写 true）。
     */
    var hidden by mutableStateOf(false)

    /**
     * 离开画廊（切 Tab / 进二级页 / 组合销毁）时复位。
     *
     * 必须复位：底栏是壳层的，`hidden` 留在 true 会让用户在别的 Tab 上也看不到底栏 ——
     * 而那时画廊那一屏早已不在组合里，没有任何人会去清它。
     */
    fun reset() {
        hidden = false
    }
}
