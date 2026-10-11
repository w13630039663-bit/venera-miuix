# 桌面第五批：墙上那三枚偏好接上消费点（2026-10-04）

范围：`desktop/`。**Android 侧零改动**（`git status --short app/` 空）。分支 `compose-migration`，未提交。

---

## 1. 这批做什么

第四批量出一条前提（手册 §18.1）：桌面那 12 枚画廊偏好**几乎零消费**，所以"做设置页"
等于摆一屏假开关。当时把顺序改成「先补三枚接得住的消费点，再谈写侧」，就是这批：

| 档 | 键 | 之前 | 现在 |
|---|---|---|---|
| 列数 | `pref_gallery_columns`（AUTO / TWO / THREE） | 桌面**恒按宽度**推 | AUTO 仍按宽度；TWO / THREE 是用户显式覆盖 |
| 预览清晰度 | `pref_gallery_preview`（PREVIEW / LARGE） | 卡上写死 `post.previewUrl`，选了 LARGE 也不动 | 走共享那颗 `GallerySettingsModel.wallUrl` |
| AI 角标 | `pref_gallery_ai_badge` | 桌面根本没画过角标 | 站方打过 AI 标且开关开着才摆 |

接完之后 12 枚的账：4 枚有消费点（本批 3 + 第四批的 `blockAi`）、6 枚等桌面大图页、2 枚给常量。

## 2. 三枚都只许"转交判据"，不许桌面写第二份

这是这批唯一的设计约束，也是它能在桌面编译面内做到的原因 —— 三颗判据**本来就都在共享面上**：

- 列数：`GallerySettingsModel.gridColumns(mode, adaptiveColumns)`。两半颗各有各的主人：
  AUTO 那半颗要吃"内容区实测宽"，只有 composable 侧量得出来，所以自适应归桌面
  （`DesktopGalleryMetrics.imageWallColumnCount`）；TWO / THREE 的语义归共享那颗，
  桌面这边只许转交、**不许自己写 `GalleryColumnMode.TWO -> 2`**。
- 地址：`GallerySettingsModel.wallUrl(post, quality)`。它管着两条桌面容易抄错的判据 ——
  "LARGE 取不到就退回 preview，**不给空地址**"与"视频条目永远只吃静帧"
  （后者在 Android 侧是一条真机闪退的成因：`sample_url` 空串会兜底成原片 mp4）。
- AI 判定：`GalleryAi.isAiMarked(post.tagList)`（站方标签，不靠画风推断）。

三颗新落点都提成纯函数放在 `DesktopGalleryWallPolicy.kt`（165 行），所以能被单测直接取值：

```kotlin
internal fun desktopWallColumnCount(contentWidthDp: Float, mode: GalleryColumnMode): Int
internal fun tileImageRequest(post: GalleryPost, masked: Boolean, quality: GalleryPreviewQuality): String?
internal fun aiBadgeFor(post: GalleryPost, badgeOn: Boolean): Boolean
const val AI_BADGE_TEXT = "AI"
```

`tileImageRequest` 把"要不要发起这一次取图"与"取哪一档"收在同一颗函数里，
于是**遮挡优先于清晰度**这条顺序是数据形状（`masked → null`），不是 `if` 的排列。

## 3. 三枚档一次收下、逐张传给卡位

`DesktopGalleryPostWall(wall, columnMode, quality, aiBadge, …)` 一次收下三枚，再往下传。
**卡位里不许各自去 `collectAsState`**：那样一面墙上会有几十次订阅，
而"同一面墙上第 1 张与第 20 张不同档"恰恰是最难看出来的错。

三枚参数一律**必填、不给默认值**（与 `startDomain` 同一条理由：带默认值时漏传不报错，
而"漏传"落下来的形状正好是"同一份设置在某一屏上静默不生效"）。
搜索墙那一处原来是位置参 `DesktopGalleryPostWall(wall, Modifier.fillMaxWidth())` ——
新参数插在第二位让它**编译不过**，这就是必填换来的东西。

## 4. 判据 +7（`DesktopWallPreferenceWiringTest`，135 行）与红→绿的顺序

先写测试，第一次跑是**编译期红**（`Unresolved reference 'desktopWallColumnCount'` 等）——
那证明这三颗判据此前不存在。实现纯函数后 ①②③⑥ 绿、⑦ 仍红（卡与三面墙还没接线），
接线后全绿。顺序记下来是因为它区分了两种红：**API 不存在**与**行为没接上**。

| # | 钉什么 |
|---|---|
| ① | 列数 AUTO 跟宽走（1168→6、300→3），而 TWO/THREE 覆盖不跟（1168→2/3、300→2） |
| ② | 桌面只许调 `gridColumns`，出现本地 `GalleryColumnMode.TWO ->` 就红（对照组：`imageWallColumnCount` 必须在） |
| ③ | 遮挡档优先于清晰度档：两档 quality 下 `masked` 都给 `null` |
| ④ | LARGE 档真换了档（给 `largeUrl`）；卡里不许再出现 `post.previewUrl`（对照组改拿一行必然存在的代码，见 §6） |
| ⑤ | AI 角标：开关关 / 站方没打标 / 标签为空 三档都不摆 |
| ⑥ | `AI_BADGE_TEXT` 与 Android 那颗 `GalleryCornerPill("AI"…)` 逐字同串（对照组：那颗文件里要有 `buildGalleryWall`） |
| ⑦ | 三面墙都吃这三枚（对照组：三面墙都还在调 `DesktopGalleryWallPolicy.of`） |

另加两次**回归扰动**（证明 ①②④ 抓的是"以后有人改坏"，不只是"当初没写"）：
把 `desktopWallColumnCount` 退回只按宽度 ⇒ ①② 同红；把卡里的地址取回 `post.previewUrl` ⇒ ④ 红。
两次都改回后复绿。**③ 在第二次扰动下仍绿** —— 因为那次扰动保留了 `masked → null`，
这正是 ③ 与 ④ 分工的证据。

## 5. 图证（窗口捕获，`occlusionSafe=true`）

取证条件：本机 `%LOCALAPPDATA%\venera\prefs\venera_preferences.json` 写
`{"pref_gallery_columns":"TWO","pref_gallery_preview":"LARGE"}`，跑完**已删回原状**（本轮前它不存在）。

- 同一域同一时刻：墙从 6 列变 **2 列**，每张明显清楚一档（LARGE = 站方 sample 档）。
  段头仍念「精选推荐 进屏 40 张 · yande.re 20 张、Safebooru 20 张」。
- **AI 角标这一枚没有图证**：日榜那一轮里没有站方打 AI 标的条目，而搜索框是 UI 输入 ——
  键盘/鼠标自验是禁的（手册 §8）。所以它今天只有 ⑤⑥ 两颗单测。
  要把这条图证补上，缺的是一条 `-Pquery=` 取证参数（见 §7）。

## 6. 两条过程读数（比这批本身更值钱）

1. **`codeText` 跳注释，所以对照组不能拿一个只活在注释里的词作样**。
   ④ 最初写的对照组是"文件里必须出现 `previewUrl`"—— 而接线之后那个词只剩注释里还有，
   跳完注释就没了，对照组自己变红。改成拿一行必然存在的代码（`post.site.displayName`）作样。
   与手册 §4.1 同族：**负向判据的对照组必须落在代码行上**。
2. **刚起窗时 WGC 可能回一帧过期画面**。这批第一次抓 #20668 抓到的是**上一轮那扇窗的
   设置坦白页**（同一 id、bounds 还是旧的 48,48），第二次抓才是本域的墙（bounds 已变 104,49）。
   差一点就把"起始域参数失效"当成新缺陷报出去。**判据：起窗后至少抓两次，比对 bounds 与内容**；
   只凭第一帧下结论不算读数。已把这条写进手册 §16.4。

## 7. 下一批的输入

1. `-Pquery=` 取证参数：让搜索域能被无人值守取证（顺带补上 AI 角标那条图证）。
2. 设置页写侧：今天可以摆的只有那 4 枚（列数 / 清晰度 / AI 角标 / 屏蔽 AI），
   另外 6 枚**要么等桌面大图页（P5）要么就不摆** —— 摆出来就是假开关，宁缺。
3. `galleryAnimated` 那枚桌面给 `ALWAYS` 是登记过的刻意分档，等 P5 时重新判一次。
4. 桌面屏蔽规则写侧 `DesktopGalleryContentGuard.addRule` 仍零调用点，与设置页同一批解。
