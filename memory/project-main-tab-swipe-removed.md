---
name: project-main-tab-swipe-removed
description: 2026-09-29 用户点名撤掉"主页面内容区左右滑动切 tab"（Navigation.kt 保护域的第一次豁免）；底栏自己那条拖动必须保留；同日第二次豁免改了切 Tab 的转场方向（撤手势≠撤动画）
metadata:
  type: project
---

**主页面内容区不再支持左右滑动切主 Tab。** 这条手势是用户 2026-09-29 当场点名要撤的，
实现（`feature/TabSwipePager.kt` + `Navigation.kt` 里 NavHost 上那一处 `.tabSwipePager(...)`
+ 首页轮播的 `Modifier.tabSwipeExcluded()`）已从源码树移除，镜像在仓库 `_trash/tab-swipe-pager-2026-09-29/`。

**Why:** 用户原话「取消所有主页面的任意位置左右滑动切换页面导航栏的，但是要保留导航栏的左右滑动」。
内容区那条横滑与页面内自己的横滑（首页推荐轮播、收藏三段、画廊两页、大图页左右翻）在同一片区域抢手势，
"我只是想滑一下这块内容，怎么整个 App 换了页"是长期抱怨点。

**How to apply:**
- **底栏那条拖动切 tab 不是漏网的功能，是刻意保留的另一套实现**：
  `components/backdrop/VeneraLiquidGlassNavBar` 里的 `DampedDragAnimation` → `onTabDragEnd` → `gotoTab`。
  看到"内容区不能滑了"不要顺手把它加回来 —— 撤掉它需要用户重新授权。
- `Navigation.kt` 是记录在案的保护域（`FREEZE-STATEMENT.md` 顶部那条）。这次撤手势是**一次点名豁免**，
  不是一般性许可：`VeneraNavTab` 枚举顺序、路由映射表、顶栏齿轮入口仍然一个字不许顺手改。
- 旧文档/注释里凡是写"枚举顺序就是左右横滑翻页顺序"的地方都已改写；再看到这种句子说明是新写的，按本条更正。

**同日第二次豁免：撤的是手势，切 Tab 的动画反而要加。** 用户紧接着（2026-09-29 第四轮）点名
「从首页切到收藏，页面从右侧滑入；反向从左滑出；Tab 顺序与滑动方向一一对应」。
落点是 `NavHost` 的 `enterTransition`/`exitTransition`：两端**都是主 Tab** 时走整页
`slideIn/OutHorizontally`（方向按 `VeneraNavTab.entries.indexOf` 差值），其余目的地仍走
shared axis X —— 列表→详情那条不能被整页横推盖掉（封面在按自己的曲线飞）。
**别把这两件事当同一条**：看到"Tab 之间是整页横滑"不是旧手势回归，看到"内容区滑不动"也不是动画漏了。

相关：[[project-navigation-predictive-back-facts]]、[[project-gallery-module-isolation]]、[[reference-venera-workflow-docs]]
