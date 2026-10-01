package com.venera.compose.gallery.domain

/**
 * 「上滑收起顶栏与底栏」的判据。
 *
 * ## 触发口径（用户 2026-09-30 拍板，同日纠正方向）
 *
 * - **上滑**（手指向上滑 = 往下浏览新内容）：累计位移超过阈值就把两栏收走；
 * - **下滑**（手指向下滑 = 往列表开头回滚）：**出现任何向下的位移就立刻展开**，不要求累计到某个值 ——
 *   用户的口径是"往回滑立刻回来"，把它也做成"累计多少才回来"会让回滚迟钝，
 *   而"想看看我在哪一页"是一个立刻要答案的动作；
 * - **回到顶部 / 切走 Tab / 离开页面**：一律复位成显示（由调用方调用 [reset]）。
 *
 * ## 符号约定（从库源码量的，不是推的）
 *
 * **负的 delta = 手指向上滑（往下浏览）；正的 delta = 手指向下滑（回滚）。**
 *
 * 出处是 `androidx.compose.material3` 1.5.0-alpha22 的 `AppBar.kt`：
 * `ExitUntilCollapsedScrollBehavior.onPreScroll` 写着 `// Don't intercept if scrolling down.`
 * 配 `if (available.y > 0f) return Offset.Zero`，而它收起靠 `heightOffset += available.y` 往**负**走
 * （`heightOffset ∈ [heightOffsetLimit(负), 0]`，0 = 展开）；`EnterAlwaysScrollBehavior` 的 KDoc 是
 * "collapse when the nested content is **pulled up**"，同样靠负 delta 收。
 * 也就是说 Compose 这一层的 scroll delta 符号跟"内容偏移量增大为正"**相反**。
 *
 * ⚠️ **这一条曾经写反**，而且反得很有迷惑性：旧版本这里认定"正的 delta = 手指向上推内容 = 下滑"，
 * 于是判据做成了"上滑立刻展开、下滑累计收起" —— 与用户要的正好调头。
 * 配套的 7 条单测当时是**按这条错约定写的**，所以它们一直绿着把缺陷钉在屏上，
 * 直到真机反馈「我之前设置有个下滑收起顶底栏，搞反了，应该是上滑收起」。
 * 记在这里是为了那条一般化的教训：**判据层的绿，只等于它钉的那个方向是绿的。**
 *
 * ## 为什么阈值是"滑过搜索框的位置"
 *
 * 用户给的原话是"滑过搜索框的位置后主动收起"。搜索框（常驻 chrome 那条入口条）
 * 在顶栏之下、高度是个常量（`galleryHomeChromeHeight()`），所以"滑过它"约等于
 * 这个数值的像素量。做成可传入的参数而不是写死一个数：调用方把真实高度换算成 px 传进来，
 * chrome 改了高度这里跟着改，**不出现"滑过了却没反应"**。
 *
 * ## 为什么是纯对象而不是 composable 里的几个变量
 *
 * 本项目没有 Robolectric，composable 一行都测不到。而这一段的每一条判据都会**静默出错**：
 * 阈值方向搞反（下滑才收起）、不复位（切走 Tab 回来栏没了）、把"没滚动"当"回滚"
 * （一进页面栏就收走）—— 三种都在屏上看着像"卡了一下"，不会报错。所以判据落在这里、
 * 配单测（见 `GalleryChromeHidePolicyTest`）。
 */
object GalleryChromeHidePolicy {

    /**
     * 累计上滑多少像素才收栏（默认值，调用方通常传 chrome 的实测高度）。
     *
     * 这个默认值只在调用方拿不到 chrome 高度时兜底：约 72dp @2x。
     * **优先传真实高度** —— 用户的原话是"滑过搜索框的位置"，那是个具体的高度。
     */
    const val TRIGGER_DISTANCE_PX: Float = 144f

    /**
     * 一次滚动事件之后的新状态。
     *
     * @param hidden 现在收没收。
     * @param accumulated 当前累计的**上滑**位移（向下滑会把它清回 0）。
     * @param delta 这一笔的位移，**负的 = 手指向上滑（往下浏览）、正的 = 手指向下滑（回滚）**，
     *   与 Compose nested scroll 的 `available.y` 同符号（依据见类头注）。
     * @param threshold 收栏阈值（调用方传 chrome 高度换算的 px）。
     */
    fun afterScroll(hidden: Boolean, accumulated: Float, delta: Float, threshold: Float): Pair<Boolean, Float> {
        // 向下滑（回滚）：**立即展开并把累计清零**。不要求累计到某个值（见类头注）。
        //
        // 注意判据是 `delta > 0` 而不是 `>= 0`：0 位移（手指按住不动、或者惯性衰减到 0）
        // 不该被当成"回滚"，否则一次向上滚动途中会被自己反复展开、变成抖动。
        if (delta > 0f) return false to 0f

        // 已经收着时继续上滑：保持收着，累计值不再增长（它已经没用了）。
        if (hidden) return true to accumulated

        // 上滑是负位移，累计的是它的量级。
        val next = accumulated - delta
        return if (next >= threshold) true to next else false to next
    }

    /**
     * 复位（回到顶部 / 切 Tab / 离开页面）。
     *
     * 单独一个函数而不是让调用方写 `false to 0f`：这个"对"是**必须成对出现的**，
     * 只清 `hidden` 不清累计值，下一次上滑会带着上一轮的累计瞬间触发 —— 那是
     * "刚滑一点点栏就没了"的成因，而屏上看不出是这里的问题。
     */
    fun reset(): Pair<Boolean, Float> = false to 0f
}
