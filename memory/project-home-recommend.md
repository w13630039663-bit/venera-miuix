---
name: project-home-recommend
description: 首页「可能你感兴趣」推荐区的既定决策与两条硬约束：取数源必须是免登录可分页的 jm、冷启动要先铺上次退出前的数据而不是错误文案
metadata:
  type: project
---

**这个区域是什么**：首页分区 2.5，读「最近 30 天读得最多的题材桶」（`ReadingStatsManager.getTagStats`，页数加权、已过归一化）→ 每个题材各拉一页搜索 → round-robin 混排去重 10 本 → MD3 标准轮播展示。2026-09-21 首批落地，真机第一轮反馈 5 条后同批修完。

**用户逐条拍过板的取舍**（别再重开）：

- 取数源 = **禁漫天堂 `jm`**。中途曾改过 nhentai，又被用户改回 jm。判据是「免登录可搜 + 搜索支持分页与排序」，这样「换一批」才是真随机而不是把同一页重排。
- 轮播用 **material3 自带 `HorizontalMultiBrowseCarousel`**，用户明确说过「**不需要做真 3d，按照 md3 标准就行**」，也接受不引第三方库。之前给过 A/B/C 选项，他选 A（第一方 material3）。
- 刷新语义：启动应用 + 回到首页自动刷新；节流间隔用户从 60s 改成 **2 分钟**；「换一批」按钮不受节流。
- **冷启动不许铺错误文案**：用户原话「可以考虑下直接用上次退出应用前的数据」→ 退出前那批推荐落盘（`home_recommend_cache`），VM 初始化先读出来铺上，网络回来再换；失败/空态也**保留已有那批**，只挂 note 与「重试」。
- 刷新中的占位 = **灰骨架 + 呼吸脉冲**（复用既有 `VeneraShimmer`），不是转圈、不是假封面。
- 轮播这一行要**登记系统手势排除区**：全面屏侧滑返回会吃掉贴边一侧的横拖，用户的说法是「在这个位置屏蔽掉手势切换页面」。

**两条容易再踩的硬约束**：

1. 往内置默认源清单加源，**对已安装过的设备无效**（一次性 bootstrap 标记），必须做增量补装 —— 详见 [[project-js-source-assets-not-live]]。
2. JS 源是异步注册的，冷启动那一发拿不到源 —— 任何依赖源的功能都要给有界等待，别直接判「源不存在」。

**统计侧的配合**：`TagNormalizer` 的值级排除表补了语言系与汉化词（禁漫把语言塞在兜底 namespace `Tag:` 下，namespace 级排除拦不住），否则「中文」会当成题材进偏好榜并被这个区域当关键词去搜。作者类早在 namespace 级就排了。

相关：[[project-card-size-drivers]]、[[project-nav-entry-recomposition]]、[[reference-gradle-cache-sources-jars]]
