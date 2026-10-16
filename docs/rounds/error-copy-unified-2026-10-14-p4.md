# 错误文案收口：源异常 → 屏上文案的唯一出口（2026-10-14 p4）

> 触发：真机截图三张 —— 拷贝漫画偶发弹出 `The coroutine scope left the composition`；
> 包子漫画的错误态念 `Invalid status code: 403` 与「源站防护或网络波动」。
> 用户原话：「换个我们已经做好的统一错误弹窗」「说'人话'，安抚情绪，可以带有一些表达这些错误的表情，
> 但不能包括代码，全面排查下全部报错信息」。

---

## 一、根因（两条，各自独立）

### 1. 取消被当成错误上屏

`CancellationException` **继承自 `Exception`**。所有写成 `catch (e: Exception)` 的地方都会把它一起收走，
于是「用户自己切走了页面 / 切了源 / 按了刷新」被读成「加载失败」。

`The coroutine scope left the composition` 这句本身在**全仓源码与全部缓存 `.aar` 里都搜不到**
（`~/.gradle/caches/modules-2/files-2.1` 全量扫过，零命中）—— 它是 Compose 运行期生成的句子。
能确定的是：它必须是被某个 `catch (e: Exception)` 兜住后写进了 error 字段。

**处理**（两道闸，缺一不可）：
- `catch (e: CancellationException) { throw e }` 前置到每一处；`JsComicSource.getExplorePages` /
  `loadExplorePage`、探索页两个 effect、`ComicDetailViewModel.load` / `loadComments` / `sendComment`
  与阅读器章节加载全部补齐。
- `isCancellation(t)` 防御式守卫 + `humanizeSourceError` 里的 `CANCELLATION_HINTS` 分支：
  即使漏进翻译层，也只给通用句，内部话永远上不了屏。

### 2. 源脚本的 message 是写给开发者的

源脚本抛的是 `Invalid status code: 403` / `Invalid status code: 210` / `未登录` / `is not a function`。
这些在**源管理页**是排查凭据，在**阅读路径**上是实现笔记漏到界面 —— 用户看不懂 210 是什么，
只知道自己被拦住了。

同一颗源的同一个 210，改造前在四个页面是四种说法：探索页「加载失败，请稍后重试」、
详情页直接念 `Invalid status code: 210`、搜索页「检索异常：…」、网络收藏「加载收藏失败：…」。
**收口到一处**是这一轮的主要收益。

---

## 二、新层：`source/SourceErrorCopy.kt`

屏上文案的**唯一出口**。三条硬规矩写在文件头：

1. **绝不上屏原始 message**。翻不出来给通用句，原文只进日志。
   （旧口径「翻不出来原样保留便于排查」已废除：真机上换来的是一屏黑话，排查靠 logcat 不靠用户截图。）
2. **先说人话，再说下一步**。每条都要能回答「为什么」和「我该干什么」。
3. **不甩锅**。源站的问题说成源站的问题（「跟你没关系」），用户设备的问题给一条能自检的动作。

### 分支表（判定顺序即优先级，顺序有讲究）

| 序 | 判据 | 屏上文案 |
|---|---|---|
| 1 | `CANCELLATION_HINTS`（含 **`left the composition`**） | 通用句（**不该走到这**） |
| 2 | `LOGIN_HINTS`：login expired / 未登录 / 請先登錄 / unauthorized … | 登录状态过期了 🔑 到「漫画源」里重新登录一下就好 |
| 3 | `401` / `403`，且 `loginable == true` | 这个源要先登录才能看 🔒 到「漫画源」里登录一下，回来就能用了 |
| 4 | `401` / `403`，且 `loginable == false` | 这个源暂时没放行这次请求 🚧 过一会儿再试，或换一颗源 |
| 5 | `210` / `429` / `RATE_LIMIT_HINTS` | 访问得有点急，源站让歇一会儿 ⏳ 等几分钟再来 |
| 6 | `500/502/503/504` / `server error` | 源站自己出了点状况 🛠 跟你没关系，过会儿再来 |
| 7 | `404` / not found / 找不到 | 这个源可能改版了，内容找不到了 🧩 换一颗源试试 |
| 8 | `NETWORK_HINTS` | 网络好像没连上 📡 看一眼连接，再试一次 |
| 9 | `TIMEOUT_HINTS` | 等了好久没等到回应 😴 检查下网络，或者稍后再试 |
| 10 | `PARSE_HINTS`（含 `is not a function`） | 这个源跟不上源站的改版了 🧩 换一颗源，或等它更新 |
| — | else | 这次没能取到内容 🥲 稍后再试，或换一颗源 |

**⚠️ 6 必须排在 8/9 之前**：`504 Gateway Timeout` 同时含 `timeout`，
顺序反了就会被读成「用户网络慢」，把源站的锅甩回给用户。
`SourceErrorCopyTest.服务端五叉明确说不怪用户` 就是钉这一条的。

### `loginable` 从哪来

`SourceAccountInfo.hasAccount`（源脚本里声明了 `account` 块）。`getAccountInfo()` 内部是**同步**
`engine.evaluate()`，主线程调用会卡满 30 秒，所以：

- 新增 `ComicSourceManager.sourceSupportsLogin(key)`：**挂起函数**，内部 `withContext(IO)`，
  结果按 key 缓存（`account` 块是源脚本的静态声明，运行期不会变）；
- `registerSource` / `deleteSource` 清缓存；
- 契约侧同步开口：`SourceCatalog.sourceSupportsLogin`（第 9 枚）+ `AndroidSourceCatalog` 转发。

---

## 三、全量盘点：哪些报错信息动过，哪些故意不动

### 动过（用户可见）

| 页面 / 文件 | 处数 | 处理 |
|---|---|---|
| `feature/explore/UnifiedExploreScreen.kt` | 3 | 走 `humanizeSourceError` + 取消 rethrow |
| `feature/explore/SourceSectionScreen.kt` | 1 | 同上（分类下钻页） |
| `feature/SearchViewModel.kt` | 4 | 筛选 / 聚合检索 / 单源检索 / 加载更多 |
| `feature/NetworkFavoritesViewModel.kt` | 7 | 文件夹 / 收藏 / 加载更多 / 建夹 / 删夹 |
| `feature/ComicDetailViewModel.kt` | 8 | 详情 / 网络收藏夹 / 点赞 / 评分 / 预览图 / 评论 / 发评 / 章节页 |
| `feature/ComicDetailScreen.kt` | 1 | 行内错误横幅 → `VeneraDialog`（见第四节） |
| `feature/FavoritesViewModel.kt` | 3 | 本地夹建 / 改名 / 删 → 中性句 |
| `feature/LocalComicScreen.kt` | 4 | 导入 / 读文件 / 分享 / 导出 → 中性句 |
| `feature/SyncBackupScreen.kt` | 6 | 导入 / WebDAV 连接·同步·恢复 / 分享 / 导出 / 还原 |
| `feature/settings/AppSettings.kt` | 3 | 备份导出 / 导入 / 存储目录切换 |
| `feature/settings/NetworkSettings.kt` | 1 | 代理生效失败 |
| `feature/settings/PreferredIpSettings.kt` | 1 | 探活 |
| `feature/settings/PreferredIpSpeedTestScreen.kt` | 1 | 测速 |
| `feature/sourcemanage/ComicSourceScreen.kt` | 2 | 读本地文件 / Cookie 登录 |
| `feature/sourcemanage/ComicSourceViewModel.kt` | 6 | 执行回调 / 重登 / 仓库安装 / 更新 / 链接安装 / 本地导入 |
| `feature/sourcemanage/GalleryAccountCard.kt` | 2 | 开浏览器 / 配置 Key |
| `feature/sourcemanage/WebLoginScreen.kt` | 1 | 登录回调失败 |
| `components/ComicCardContextMenu.kt` | 1 | 长按收藏（本地库） |
| `reader/ChapterCommentsSheet.kt` | 2 | 章评加载 / 发表 |
| `reader/VeneraReaderScreen.kt` | 4 | 章节加载（源）/ 存相册 / 插图收藏 / 分享 |
| `gallery/ui/GalleryDailyScreen.kt` | 1 | 日榜整轮失败 |
| `gallery/ui/GalleryScreen.kt` | 1 | 一级墙整轮失败 |
| `gallery/ui/GalleryForYouViewModel.kt` | 1 | For You 整轮失败 |
| `gallery/ui/GalleryPostScreen.kt` | 2 | 单条加载 / 保存 |
| `gallery/ui/GalleryInfoSheet.kt` | 1 | 开浏览器 |
| `gallery/ui/GalleryImageShare.kt` | 3 | 分享落盘 / chooser / 取图 |
| `gallery/ui/GalleryReverseViewModel.kt` | 1 | 反搜（去掉 `javaClass.simpleName` 回退） |
| `download/DownloadManager.kt` | 3 | 任务兜底 / 章节页列表 / 单页重试 3 次 |
| `data/update/ApkInstallLauncher.kt` | 1 | 唤起安装（去掉类名前缀） |
| `data/update/ApkDownloader.kt` | 1 | 候选线路失败（去掉 `类名: message`） |
| `data/db/FollowUpdatesRepository.kt` | 1 | 追更检查失败 |
| `engine/SourceHttpDiagnostics.kt` | 1 | `explainSourceFailure` 改「人话在前」 |

### 故意不动

- **源脚本编辑器**（`ComicSourceViewModel.saveSourceText`）：保存失败要在编辑器里**原地展示 JS 语法错误**，
  原文就是用户要改的东西 —— 这不是「漏到界面」，是工具本身。
- **`SourceHttpDiagnostics.explainSourceFailure` 里的技术细节**：调用点只有「源可用性测试」与
  「源登录失败」，都在**源管理这颗诊断屏**上。改法是**人话在前、原始报文收进「技术细节」括号**，
  而不是删掉 —— 用户到那儿就是为了排查，原始报文是他能贴给源作者的唯一凭据。
  **如果这一条也要一并去掉，说一声即可。**
- 引擎内部日志（`VeneraJsEngine` / `JsHttpHandler` / `JsSourceDataStore` / `VeneraImageLogger`）、
  `data/platform` 的异常包装、`gallery/domain/GalleryLegGuard`：**只进日志/内部流**，不上屏。

---

## 四、详情页：行内错误横幅 → 统一弹窗

真机读数：错误横幅插在 `LazyColumn` 里，会把后面那块**模糊封面背景顶下去**（用户原话
「背景的模糊被顶下去了」）。

改法：
- 删掉 `key = "detail-error"` 那条 `Surface` + `Icons.Outlined.ErrorOutline` 的行内条目；
- 改在根 `Box` 末尾挂 `VeneraDialog`，`show` 由 `detailError != null && liveDetails == null` 决定，
  配一枚 `detailErrorSeen` 闩（`remember(detailState.error)`），关掉后不再自动弹第二次；
- 弹窗两个动作：「再试一次」→ `viewModel.load(comic)`；「先看看别的」→ 只关闭。
- 章节目录的空态标题同步改成「章节目录还没读出来」/「再试一次」。

---

## 五、验证

- `:app:compileDebugKotlin` ✅
- `:app:testDebugUnitTest` ✅ （见下方跑数）
- `:app:assembleDebug` ✅

### 新增/调整的单测

- **新增** `app/src/test/java/com/venera/compose/source/SourceErrorCopyTest.kt`（15 条）：
  - 判据 1：`210` / `Invalid` / `status code` / `Exception` / `undefined` / `java.` / 方括号 一律不上屏；
  - 判据 2：登录 / 403（支持登录 vs 不支持登录）/ 限流 / 网络 / 超时 / 5xx / 改版 各自有下一步，
    且**不支持登录的 403 不许说成「网络」**（包子漫画那次就是这么误诊的）；
  - 判据 3：`CancellationException` 与 `left the composition` 都判为取消，包在 cause 链里也认；
    普通异常不误判；取消漏进翻译层只给通用句；
  - 兜底：`null` / `""` / `"   "` / 未知串一律 `GENERIC`（旧口径「原样保留」已废除）。
- **调整** `ExplorePolicyTest.kt`：删掉 3 条断言已删函数 `friendlySourceErrorMessage` 的旧用例
  （其中一条还钉着「未知 message 原样保留」的旧口径）。
- **对账** `BusinessApiBaseline.kt` / `docs/rounds/business-api-boundary-2026-10.md`：
  `SourceCatalog` 8 枚 → 9 枚。

### 仍未做

真机回归未跑（MuMu 模拟器本轮没起）。需要真机确认的三点：
1. 拷贝漫画偶发错误是否已变成弹窗 + 人话；
2. 详情页错误弹窗弹出时，模糊背景不再被顶下去；
3. 包子漫画的 403 是否落到「要先登录」而不是「源站防护/网络波动」。
