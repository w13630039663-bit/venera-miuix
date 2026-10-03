# S2-B · 桌面 UI 壳与节的唯一事实源（一份可以独立干完的计划）

> 你是谁、在干什么：你是**同时开工的两个执行方之一（B）**，只做"桌面首页的两级导航骨架 + 节的注册表 +
> pane 六项上屏（缺的四项显式标『未实现』）"这一半。另一半（A）在另一个 git worktree 里同时做桌面接线，
> 你们**不共享任何一颗文件**。
> 上级方案：`~/.qoder-cn/plans/wistful-inlet-snipe.md` · 上一批读数：`docs/rounds/windows-gallery-home-s1-2026-10-03.md`
> 你的对岸计划：`docs/rounds/windows-gallery-home-s2a-wiring-2026-10-03.md`（只读，别改它）
> 设计稿（视觉的唯一出处）：`docs/designs/windows-gallery-home-touhou-2026-10-03.html`

## 0. 硬约束（违反一条就等于这批白做）

1. **物理与逻辑分离**：Android 端 UI 代码零改动 —— `app/src/main/java/com/venera/compose/{feature,gallery/ui,reader,components}/`
   与 `MainActivity.kt` **一行都不许进 diff**。你可以**读**它们（抄形状、核口径），不可以改。
2. **严禁在桌面 `import android.*`**（含不带 import 的内联 FQN `android.util.Xxx.yyy(...)`，S1 就是被这句绊过）；
   **严禁两端 UI 互相耦合** —— 桌面要哪块 UI 就在自己壳里写一颗，或把纯判据件下沉到 `gallery/domain`，
   **不许跨端引 Composable**。
3. **降级路径宁可错慢不可静默交错**：缺席必须被说出来；不许"看起来正常但实际少了一半"。
4. **不许有假开关**：一个看起来能配、实际恒真/恒假的控件，比没有控件更糟（本仓已因此返工过）。
5. **UI 文案不许带 AI 味**：星号、反引号、内部黑话、实现笔记一律不进界面；未实现灰行连标题按下面的形状规范写。
6. **设备只读**：真机与模拟器由用户点页面，执行方只截图读日志。

## 1. 地基（都已就位，别重做）

- 契约在桌面编译面里（S1 落的）：`com.venera.compose.gallery.data.GalleryPorts` 及 12 颗契约、
  `GalleryBoards` / `BoardSource` / `YandeReBoard` / `ScoredBoard`。
- android-free 的编排件：`com.venera.compose.gallery.domain.GalleryDailyFeed(boards)`，
  `suspend fun loadDaily(seed): Result<Daily>`，`Daily` 带 `merged` / `failures` / `date` / `pools`。
- 桌面已有的件：`com.venera.desktop.platform.DesktopPaths`。
- 桌面窗口默认档 **1080×760** 在 `desktop/src/main/kotlin/com/venera/desktop/VeneraDesktop.kt:207`，
  **不许动**；系统 title bar 也不动。
- ⚠️ **A 的接线件不在你的编译面上**：`DesktopGalleryPorts` 由 A 在另一个 worktree 里写。
  所以你的 pane 一律**以参数吃 `GalleryPorts`**（Composable 形参），**不要**在 UI 里调 `GalleryPorts.of(...)`。
  这一条就是你们两个能并行又不互相等的原因：契约是共享的、可编译的，实现件是对岸的。

## 2. 你的交付物（全在 `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/`，一颗不许越界）

### 2.1 节的唯一事实源

`DesktopGalleryHomeSections.kt` —— 形状照 `app/.../gallery/ui/GalleryHomeSections.kt:73-97`（key 常量 + `ORDER`）
与 `:482-522`（`when(key)` 渲染）：

- `ORDER` **恰含六项**，次序钉死：`每日热门`、`正在关注的画师`、`历史`、`下载`、`最新`、`排行榜`。
- **项数恒 6，不跟数据变**：某项今天没内容 ⇒ 那一行还在，只是走"未实现"形状。**绝不做成"数据到货才插项"**
  —— 那是本仓在 Android 侧踩过的锚定漂移的桌面版（返回被打回默认值 / 落地闪一下，同一根因）。
- `when(key)` **不留 `else`**：新增 key 忘了登记必须编译不过，而不是静默不渲染。
- 每一项的形状：`contentProvider == null` 与 `reason != null` **恒等**（有内容件 XOR 原因，二选一，不许中间态）。

### 2.2 四项「未实现」的呈现规范（这条是用户对既有口径的**定点豁免**，照做，别自己发挥）

既有口径是"未实现灰行连标题全撤"（2026-09-30 定的），本次**首页 pane 这四行是显式批准的例外**，条件三条：

- 沿用稿上的 `.nav.off` + `.lock` 形状：文字 `#8B8B8B`、图标 `#5E5E5E`、行尾一枚 **10.5px 药丸写「未实现」**。
- 完整原因写在**同一行下方 caption**（12px、`maxLines=2`），句子里**必须点名缺席的那颗件**，
  不许写"敬请期待"。
- **整行 `selected = false`、`onClick = null`**；若 Fluent `menuItem` 给不出 disabled，就在 `onClick` 里
  只做"展开原因"这一件真事 —— **绝不 no-op**。
- **计数徽标一律删**（稿上的 `1.2k` / `286` / `2` / `3,412` 全不带）：没有 store 就没有数。
- 单独一颗可复用件：`DesktopUnimplementedRow.kt`。
- 四行各自"变可点"的判据（现在写死未实现，将来点亮要同时满足，判据原文抄进注释，别只写结论）：
  - 历史：存在 `gallery/data/GalleryHistoryStore.kt`（或 `data/db` 里一张 gallery 历史表）**且**注册表有 `HISTORY`
    **且**有内容件 —— 三者同时。
  - 下载：`GallerySaver` 落点从 MediaStore 改为 `PathProvider` 目录 **且**存在一颗枚举读侧。
  - 最新：`BoardSource.searchPosts` 有可用排序参数**且**三站中 ≥2 站真返回。
  - 排行榜：`GalleryBoards` 上有跨站统一取榜成员 **且**"yande.re 固定 40 条、忽略分页"这条天花板在 UI 上被说出来
    （否则它点亮那一刻就是"看起来还有更多"的假读数）。

### 2.3 两项有内容的 pane

- `DesktopDailyPane.kt`：吃 `GalleryPorts`，取数走 `GalleryDailyFeed(ports.boards).loadDaily(seed)`。
  **`failures` 非空必须在界面上说出来**（缺席站的名单逐字来自 `failures.keys`，不许自己数）。
  列数**按 `imageWallColumnCount` 的表达式算，不许照稿抄 5**：默认 1080 窗口下
  `1080 − rail 48 − pane 224 − imageWallHorizontalPadding()` 再 `ceil(/200)` ⇒ **4 列**（这条由你的用例钉死）。
- `DesktopFollowedArtistsPane.kt`：**稿上的「热门画师」改名「正在关注的画师」**（Pixiv 本轮不接，
  仓库里从来没有热门画师榜，硬造就是假读数）。判据用已共享的 `domain/GalleryFollowedArtists.rowOf(follows, favorites)`，
  卡面次序 = 真头像 > 名下最近收藏 > 首字母座。
  空名单文案写「还没有关注画师 · 关注是本地动作」，**不写"0 位热门"**。

### 2.4 桌面自持的尺寸口径

`DesktopGalleryMetrics.kt` —— rail **48**、pane **224** 在桌面的唯一出处。

口径账要算清（写进文件注释）：Android **没有 rail**，所以 rail 48 **没有可漂的对岸**，是桌面自持的一颗数；
真会漂的只有 **pane 224**（对岸 = `app/.../components/WideScreenPolicy.kt:60` 的 private `ExpandedSideBarWidth`）
与**每列 200**（`:130` 表达式里的除数）。这两处由 `DesktopWidthCaliberDriftTest` 钉。
⚠️ **`WideScreenPolicy.kt` 一行不改**（原计划要往它加 `railWidth()`，那是动 Android UI 基建，已撤）。
桌面与 Android 各持一份 224 是**受机器看管的重复**，不是自由重复。

### 2.5 你的测试（`desktop/src/test/kotlin/com/venera/desktop/gallery/ui/` 与 `.../architecture/`）

JUnit4，不引新框架。

| 用例 | 判据 |
|---|---|
| `DesktopGalleryHomeSectionsTest` | ①`ORDER` 恰含六 key、次序钉死；②**项数恒 6 不跟数据变**（把 follows/favorites 清空再算一次，仍是 6）；③每 key 二选一：`contentProvider == null` XOR `reason != null`，出现"两者都非空/都空"即红；④四行未实现的 `reason` 句子里必须点名缺席件（逐条断言含指定关键词）；⑤四行 `onClick == null` 或只做展开，**断言不存在 no-op 分支** |
| `DesktopWidthCaliberDriftTest` | **只读**文本核对：`app/.../components/WideScreenPolicy.kt` 里 `224.dp` / `72.dp` / 每列 `200` 三处口径；并 grep 桌面源里这三个字面量各 ≤1 处（除 `DesktopGalleryMetrics.kt` 自身）。另钉"默认 1080 窗口 ⇒ 4 列"的推导 |
| `DesktopUiIsolationTest` | **双向**：`desktop/src/main` 零 `com.venera.compose.(feature\|gallery\.ui\|reader\|components)` import；`app/src/main` 零 `com.venera.desktop` import。（S1 的 `DesktopNoAndroidImportTest` 已经管 `android.*`，这条管的是**跨端 UI**，两把尺子不许并成一把。） |

## 3. 验证

```bash
./gradlew :desktop:test
./gradlew :desktop:compileKotlin     # 任务名不是 compileDebugKotlin
git diff --name-only                 # 只允许出现 §2 点名的路径；app/ 下必须 0 颗
```

⚠️ gradle 别接管道（`| tail`/`| grep` 会吞退出码，后台通知也会再吞一次）。
并发提醒：与 A 各跑各的 gradle，共享 `~/.gradle`，偶发 `Timeout waiting to lock` 不是代码问题，重跑即可。

**出图（`:desktop:app --shot`）这一批不归你验**：`VeneraDesktop.kt` 的接线与注册表切换归集成，
你的件此刻还没有消费点。你的验收面是上面三条 + 用例本身。

## 4. 所有权与禁区

**你拥有**：`desktop/src/main/kotlin/com/venera/desktop/gallery/ui/**`；
`desktop/src/test/kotlin/com/venera/desktop/gallery/ui/**`；
`desktop/src/test/kotlin/com/venera/desktop/architecture/DesktopUiIsolationTest.kt`。

**你禁止碰**：
- `desktop/build.gradle.kts`（冻结：srcDir / exclude / 依赖都由 S1 落好，你不需要新的；要新依赖 ⇒ 停下来报告）。
- `desktop/src/main/kotlin/com/venera/desktop/VeneraDesktop.kt`（归集成：`:386-515` 那三个手写分支改注册表驱动、
  以及 `DesktopGalleryPorts.install()` 那一行，都在集成 commit 里落。你交付的 pane 根要写成
  一个可被一行调用的 Composable，并把"集成要改哪两处"写进你的提交说明）。
- `desktop/.../gallery/data/**`、`desktop/.../platform/DesktopHttpEngine.kt`、`DesktopLogger.kt` —— A 的。
- `app/**` 全部（**只读**）、`docs/**` 除你自己那份说明文档、`FREEZE-STATEMENT.md`
  （pane「未实现」那条豁免由集成方按流程记，你只需在提交说明里把三条前提写全）。
- S1 落的三颗守卫（`DesktopSharedFaceLedgerTest` / `DesktopNoAndroidImportTest` / `testsupport/DesktopFace.kt`）
  —— 改判式=改尺子；它们红时先怀疑自己的代码。

**你的提交**：`feat(desktop): S2-B …`，正文含：六项 `ORDER` 与四行未实现的原文案、rail/pane 口径账、
`DesktopWidthCaliberDriftTest` 读到的三个数、以及"集成要动的两处"清单。不要 push，不要合并。

## 5. 完工判据

1. `:desktop:test` 绿，新增用例名与条数在提交说明里列全。
2. `git diff --name-only` 与 §4 的"你拥有"清单逐条对得上；`app/` 下越界 **0 颗**。
3. `ORDER` 六项齐、四行带「未实现」+ caption 点名缺席件、**没有任何计数徽标**、**没有 no-op 控件**。
4. `DesktopUiIsolationTest` 与 `DesktopWidthCaliberDriftTest` 两条都是**有 teeth 的**：
   各自做一次"故意改坏 ⇒ 用例如期红 ⇒ 还原"的实测，并把红的那一句原文抄进提交说明。
