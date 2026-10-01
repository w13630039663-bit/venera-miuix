---
name: project-cold-start-attribution
description: 冷启首页卡顿的实测归因（2026-09-30）：卡主要是 debug 包不能 AOT 的形态成本，release 天然 260-304ms；用户优化计划里 A2/A3/A5/B2/C1/C2 六条全部被量掉，只落了"网络引擎装配移出主线程"一条；基线画像实测无收益已撤
metadata:
  type: project
---

2026-09-30 用插桩 + atrace + simpleperf 在 PJZ110 上把"冷启首页卡顿"量到底，结论与用户给的《性能优化计划》几乎相反：

- **`VeneraApp.onCreate` 全程只有 74–95ms**（不是计划说的"罪魁，~60% 收益"）。分段：网络引擎 61–77ms（其中 `PersistentCookieJar.loadFromPrefs` 33–50ms、`buildClient` 14–24ms）、内容守卫 **5ms**、源管理器 8–13ms（三个内置源构造 **1ms**）。
- **主线程那 1280ms 窗口里 running=1147ms / 等 CPU=4ms / 阻塞=128ms**；simpleperf 显示 **82.7% 周期在 `libart.so`**（解释执行 + JIT tiering + dex 校验 + 类加载），`libhwui.so` 只 0.8%。⇒ **debug 包不能 AOT 的打包形态成本**，`cmd package compile -m speed -f` 一条命令就把 TotalTime 从 ~1050ms 压到 744–813ms。
- **release 实测 TotalTime 260–304ms、99th 125–150ms、50th 11–32ms** —— 计划要治的"99th 650–700ms 单帧阻塞"在真用户形态里不存在。
- 被量掉并撤下的条目（别再重做）：A2 守卫装载时机（5ms，且首帧窗口 +378~412ms 主线程内会把它**搬进首帧**；`rules` 流被两张 FROZEN 探索屏收集，纯延迟会给它们空规则表）、A3（1ms）、A5（QuickJS 抢 CPU 被 E1 实验证伪：推迟 4s 后 first traversal 886/919/921 vs 基线 902ms）、B2（首帧前没有导航）、C1（key 不影响首次组合）、C2（`getAllComics` 157–331ms 但库里 **0 行**，是首次开库成本；且计划点名的 `favoritesManager.favorites` **不存在**）。
- **只落了一条**：`VeneraNetworkClient` 的 cookieJar/prefs/client 改 lazy + `VeneraApp` 里 IO 线程预热 ⇒ 主线程 61–77ms 降到 2–3ms、onCreate 全程降到 27–28ms（TotalTime 只 −20ms，噪声边缘，别当收益说）。`UserAgentPolicy.init` 刻意留构造期（CF 过盾写 UA 的路径不保证先碰过 client，未 init 会静默丢持久化）。
- **基线画像**：机制走通（手写 `src/main/baseline-prof.txt` 不需新依赖也不需 macrobenchmark；profgen 在 SDK `cmdline-tools/latest/bin`），但按"新装用户第一次冷启"实测 261–302ms vs 未加画像 260–304ms ⇒ **无收益已撤**（文件在 `_trash/baseline-prof.measured-no-gain.txt`，重生成方法在其头部注释）。原因：release 装包时 ART 本来就在用库 AAR 合并出的 16KB 画像做 speed-profile。

**Why:** 这份读数花了一整轮插桩+装机+采样才拿到；下一次有人再拿"冷启卡"开药方时，先按这个分母判断，别在 debug 包上优化业务代码。

**How to apply:** 谈冷启先问"量的是哪一包"。debug 上函数级采样只属于 debug；release 上应用自己的 `Log` 被 `app/proguard-rules.pro:74` 的 `-assumenosideeffects` 整条抹掉，只能拿 `am start -W` + `dumpsys gfxinfo`。读数与 trace 原件留在 `_qa/startup-baseline/`。挂账未做：release 侧"切页+快滑"复测、收藏页快滑那 2 次 200ms 尖峰（要动 FROZEN 屏结构）、`EmojiCompatInit` 占启动窗口 3.3% CPU。相关：[[project-compose-jank-audit]]、[[project-venera-qa-device]]、[[feedback-design-review-then-code]]
