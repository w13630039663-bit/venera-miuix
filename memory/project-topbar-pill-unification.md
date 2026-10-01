---
name: project-topbar-pill-unification
description: 顶栏图标按钮统一成磨砂圆座（2026-09-30 批次 H）的四条拍板、回落面，以及被实况推翻的两条前提（网络收藏不是独立屏、它的内联切换钮恒不渲染）
metadata:
  type: project
---

2026-09-30 用户提「所有页面顶栏的按钮都改成收藏/网络收藏右上角那种带模糊、带动画的样式」。
落成组件 `components/venera/VeneraTopBarPill.kt`（配方数值在代码里，不在这里重复）。

## 四条拍板（AskUserQuestion，全选推荐项）

1. **统一面 = 走 `VeneraTopAppBar` 的那批屏**；**自绘 chrome 的 5 处不动**（阅读器顶胶囊岛、
   封面查看、追更页、源编辑、网页登录）。**Why:** 玻璃采的是**录制层**不是屏幕像素，
   那 5 处根本没有采样层，硬套只会得到一层假磨砂 —— 用户宁缺不假（同 [[feedback-degrade-paths-must-fail-loud]]）。
2. **顶栏每颗都磨砂**（每屏 1~4 颗），内容区图标按钮**不动**。**Why:** 内容区一屏二十颗，
   个个带外壳会把层数与观感都撑爆；那里保持"只染色不模糊"。
3. **视觉 40dp + 触摸区 48dp** 是刻意的不对齐。**Why:** 上一轮刚因"按钮看起来小了"被退回过一次，
   这条是硬约束，不能再缩（见 [[feedback-mirror-official-values]] 里"口径要分部件类型"）。
4. 形变动画只挂到**有双态的那颗**（布局切换）上，不给返回键硬造 morph。

## 被实况推翻的两条前提（我自己在方案文档里写的，别照它再推一遍）

- **「收藏和网络收藏右上角那颗是同一实现的两处调用」= 假的**：当时只有一份实现，是 `FavoritesScreen`
  里的**私有件**；网络收藏用的是另一件、无背板无 morph。现已收口成一份，私有复制件删除。
- **「网络收藏要给它的列表接采样层」= 不需要的**：`AndroidNetworkFavoritesScreen` **只有一个调用点**
  （`FavoritesScreen` 的 `FavoritesMode.Network` 页），且传的是 `showLayoutToggle = false`
  ⇒ 它自己那颗内联切换钮**今天在应用里根本不渲染**，页面右上角一直是收藏页顶栏那颗。
  **How to apply:** 判"某个入口要不要接线"之前先跑一遍**调用点清单**（`git ls-files` + 逐文件数符号），
  别拿"文件名看起来是一屏"当它是独立屏 —— 网络收藏是收藏页的一个模式。

## 两处至今仍是"有意如此"

- **追更页那颗只有可见回落**（半透明底 + 描边 + 同一套动画），不是磨砂。它其实有 `rememberTopBarBackdrop()`，
  但没有 `VeneraTopAppBar` 那层栏级背板 —— 单独给一颗浮在自绘标题行上的磨砂与全站观感不一致，故不补 provide。
  下次有人报"追更页按钮没磨砂"，这是拍板不是漏改。
- **角标参数没进组件**：批准方案里 `VeneraTopBarPill` 签名带 `badge: String?`，实现时按 YAGNI 去掉了 ——
  两处带角标的调用点本来就是"外层 Box 叠 Surface 角标"且已互相对齐。角标要收进组件需重新拍板。
- 顶栏这一族**本来就没有 MD3 后端**（`VeneraTopAppBar` 无条件套 miuix `TopAppBar`），圆座沿用同一前提，
  没有引入新的单后端面 —— 别把它当"迁移没做完"。

相关：[[project-miuix-widget-counterparts]]、[[project-favorites-secondary-row-in-chrome]]、
[[project-glass-chrome-inline-area-rules]]、[[project-segmented-pills-style]]
