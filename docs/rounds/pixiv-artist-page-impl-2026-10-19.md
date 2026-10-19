# pixiv 画师页 · 第一批实现（2026-10-19）

> 用户在 2026-10-19 拍板：「切三段时头部跟着滚走」「关注要接 pixiv」。
> 本轮开工时另有一个 agent（qoder `54b8c93d`）在**同一个仓库的另一个 worktree** 里改 pixiv 作品页，
> 所以这一轮的重点之一是**避开他**。本文记的是"做了什么 / 为什么这么切 / 还剩什么没接"。

---

## 0. 先说协作边界（这一节是给下一个人看的）

摸到的现场：

| 谁 | 在哪 | 在改什么 |
|---|---|---|
| 本仓主工作区 | `D:\venera-compose`（分支 `compose-migration`） | 我这一轮 |
| qoder 任务 `54b8c93d` | **`.worktrees\pixiv-viewer`**（分支 `fix/pixiv-viewer`，1269 个未提交改动） | 批 231 A–G：**pixiv 作品页**（`PixivWorkScreen`）+ 首页三节标题对齐 |
| qoder 任务 `dbe5cb16` | 同上 worktree | **Gelbooru** 历史期窗口（`GelbooruClient` / `GalleryRanking.searchQueryFor` / `RankingChip`） |

⇒ **物理隔离**：他在 worktree 里，我在主工作区，两边的改动不会互相覆盖
（除非最后合并同一分支，那时按文件对账）。

**我刻意没碰他的文件**：`PixivWorkScreen.kt`（20:54 刚写过）、`PixivWorkInfoPanel.kt`、
`PixivWorkPage.kt`、`GalleryPixivHome.kt`（他批 231-C 要改节头）。

**一处例外，必须记下来**：`GalleryArtistProfileScreen.kt` 里的**闪退修复**我改了 ——
那是本轮的主症状（点画师就崩），不改这一轮等于没做。而他那个 worktree 里这颗文件仍是
18:53 的版本，**合并时这颗会有冲突**，解法见 §4。

---

## 1. 做了什么

### 新增（4 颗，全新文件，不碰任何人的存量）

| 文件 | 作用 |
|---|---|
| `gallery/data/PixivUserHead.kt` | 头部模型：id / name / account / 头像两档 / **自设背景** / 简介 / 外链 / 关注数 / 会员位 |
| `gallery/data/PixivUserWorksParse.kt` | 两份答复的解析（`user/{id}?full=1`、`profile/all`、`profile/illusts`）+ `PixivArtistSection` |
| `gallery/ui/PixivArtistScreen.kt` | 这一屏（724 行，含 hero / 头部 / 数据行 / 三段 / 网格 / 页尾） |
| `test/.../PixivUserWorksParseTest.kt` | 14 条判据（解析 + 路由扫描 + 分级对表） |

### 改动（3 颗）

| 文件 | 改了什么 |
|---|---|
| `gallery/data/PixivClient.kt` | +3 条匿名腿：`userHead` / `profileAll` / `worksByIds` |
| `GalleryArtistProfileActivity.kt` | `site == PIXIV` 分派到 `PixivArtistScreen`；三枚 extra **不变** |
| `ui/tokens/Spacing.kt` | +4 枚 token（见 §2） |

### 修掉的那个闪退

`GalleryArtistProfileScreen.kt` 里 `GalleryRankings.searchQuery(...)` 原本在 `when (site)` **之外**
无条件先算，而 `suffix(PIXIV, ALL)` 走 `GallerySite.notWired()` ⇒ **抛** ⇒ 屏还没画出来就崩。
现在两道闸门：

1. **屏首提前 return**：`if (site == GallerySite.PIXIV) { …说明一句… ; return }`
2. **算式搬进分支**：`searchQuery` 只在 YANDERE / GELBOORU / SAFEBOORU 三支里各算一次，
   PIXIV 那一支改成 `return@LaunchedEffect`（**根本不发**，不是"发了没答上"）

第 2 条是必须的：分派做对之后，任何漏网调用点进来仍然不该炸。

---

## 2. 四个数字都不是新造的

| token | 值 | 出处 |
|---|---|---|
| `pixivArtistAvatarSize` | 76dp | 横排那颗是 64dp，这一颗是"第二主视觉"（第一是 hero），且压在照片上 |
| `pixivArtistHeroMinHeight` | 190dp | 393dp 满幅 ÷ 190 ≈ **2.07:1**，与站方那张 1920×960（2:1）几乎同比例，`Crop` 几乎不裁 |
| `pixivArtistHeroMaxHeight` | 340dp | 840 ÷ 340 ≈ 2.47:1，与手机档同族；**定高会在 1891dp 上拉成 9.6:1**（`artistHeroHeightWide` 那条旧账） |
| `pixivFollowPillHeight` | 38dp | 比 `segmentedHeight`(48) 矮一档：这两枚收在内容里，不是页面级主行动 |

hero 的 scrim 直接用 `ImageOverlayColors.ScrimTop/ScrimBottom`（设置页 hero 那两档**固定黑**），
没新造色 —— 图是作者选的，亮度色相不可控，靠 scrim 浓度兜底而不是靠主题色。

---

## 3. 三处**故意**没做，理由都在代码里

### 3.1 收藏段只做了「未登录」那一档

`BookmarkLocked` 摆的是"为什么读不到 + 怎么才读得到"，**不摆重试**（假开关）。

为什么做不了更多：`www.pixiv.net/ajax/user/{id}/illusts/bookmarks` 今日实测 **400**
（换三个 user_id、三档 limit、两种 lang 全 400）⇒ 收藏**不能匿名读**；
要票据的 `app-api /v1/user/bookmarks/illust?user_id={他}` 本机没有有效票据，验不了形状。

### 3.2 关注仍然写本地，**没有**切站方

用户点名「关注要接 pixiv」，但这一轮**没有**接 —— 这是有意的：

- 写侧 `POST /v1/user/follow/add|delete` 是**会改变用户在站方账号状态**的调用，
  本机无有效票据，形状只能对着 pixivpy `aapi.py` 与 pixez 互证；
- 猜错的表现是"用户点了关注，站方没收到"，而屏上显示已关注 —— 那是替站方撒谎。

**但我把切换点留成了一处**：`PixivArtistScreen.kt` 的 `FollowPill` 注释直接写着
"接站方要加什么、为什么这轮不切"。真机验过票据腿之后，改那一处 + 加两条写腿即可。

> 顺带查清一个**今天就在的真缺陷**（写进规划 §5.5）：作品页那颗「关注」写本地账（键=画师名），
> 而首页「已关注」读站方（键=`user_id`）⇒ 作品页点了关注，首页那一栏不会多出这个人。
> 接站方正好把它修掉 —— 但那是下一轮的事。

### 3.3 粉丝数退「—」

`total_follow_users` 只有带票据的 `/v1/user/detail` 给。匿名档拿不到 ⇒ 摆「—」而不是 `0`：
0 是"这个人没有粉丝"的断言，而我们只是不知道。

---

## 4. 合并时怎么处理（给下一个人）

`GalleryArtistProfileScreen.kt` 这一颗两边都动过。合并口径：

1. **留我这边的两处**（屏首闸门 + 算式搬进分支）—— 那两处是修闪退的，删了会回到"点画师闪退"；
2. **他的改动照收** —— 我在那一颗文件里只动了 `LaunchedEffect` 那一个块和函数开头，
   他那颗文件 18:53 之后的改动都在别处，逐段合并即可；
3. 合完跑 `PixivUserWorksParseTest.路由` 那一条 —— 它扫的就是这两处（`searchQuery` 不许回来）。

`PixivClient.kt` 同理：我只在 `private fun url(...)` **之前**插了三个方法，是纯增量。

---

## 5. 未验证项（照实说）

- **编译没过**：`gradlew` 被沙箱挡住（`~/.gradle/wrapper/.../gradle-9.7.1-bin.zip.lck 拒绝访问`），
  用户也说了"不用编译和出包"。所以这一轮是**静态对账**：括号平衡、枚举一致、
  引用到的 API 逐个回核（`wideScreenLayoutMode` / `contentMaxWidthFor` /
  `rememberImageWallColumnCount` / `VeneraTopBarPill` / `GalleryPostCard(authorAvatar=)` /
  `GalleryFlyIn.capture` / `GalleryViewerQueue.set` / 4 枚新 token / `ImageOverlayColors` 成员）。
- **对账中发现并修掉的三个真错**：
  1. `PixivArtistTab` 与 `GalleryPixivHome.kt:423` 的**同名枚举冲突** ⇒ 改名 `PixivArtistSection`；
  2. `PixivSectionRow` 原本想复用 `PixivArtistTabRow` —— 那名字也被占了 ⇒ 自建一颗，
     只在注释里说明"形状同源"；
  3. 原本想走 `ports.pixiv`（`GalleryPixivWorks`）—— 那三个方法**不在契约上**，
     改成直取 `PixivClient.getInstance(context)`（三跳全匿名，与该件红线一致）。
- **真机未验**：hero 背景图在 `i.pximg.net` 的取图（`ImageHeaderPolicy` 的 `pximg.net`
  Referer 规则已覆盖，但这一档是 `c/1920x960…` 的裁切路径）、三段切换的实际观感、
  24 枚一批的续页手感。

---

## 6. 下一轮接着做的顺序

1. **先验票据腿**（`user/detail` / `user/bookmarks/illust` / `follow/add|delete` 四条），
   用真机会员账号逐条看一遍形状；
2. 收藏段接上（已验之后 `BookmarkLocked` 换真墙）；
3. 关注切站方（`FollowPill` 那处注释就是施工点）；
4. 粉丝数接 `user/detail`；
5. 等 qoder 的批 231 落地后再做导航接线（`GalleryScreen` 两处 `onOpenUser`、
   `GalleryCardCaption` 头像可点）—— **那两颗文件他正在动，本轮刻意没碰**。

---

## 7. 接手 qoder 批 231 现场（2026-10-19 晚）

用户：「你看下 qoder 那个 agent 还有啥没做？帮他做完吧」。

### 7.1 先说一个差点写错的判断

我第一次读的是**他 worktree 里的旧快照**（`fix/pixiv-viewer`，HEAD 停在 `cecae29`），
看到 `PixivAxis` 19 处、`PAGES_HORIZONTAL` 2 处还在，差点得出"他标完成但代码没改"的结论。

**主工作区不是那样。**回头量主工作区：`PAGES_HORIZONTAL` **0 处**、
`PixivAxis` 只剩测试里那句"不许回来"的钉、"多图浏览方向"只剩测试注释。

⇒ **他的 A~D 在主工作区是真落地的**，而且判据齐（`GalleryPixivWorkPageWiringTest` 里
`横档模式整档撤干净，注释里也不许留名字`、`顶栏按钮换成全站那颗玻璃座，采样源三件一起接`、
`屏底摘要条整颗撤掉，一档共用同一套 chrome` 都在）。

**教训**：同一个仓库的多 worktree 里，**不能拿一个 worktree 的状态去判断另一个的进度**。
任务账上的 completed 要对着**主工作区**核，不是对着他那个分支的 HEAD 核。

### 7.2 逐条点对账

| 批 | 任务账 | 主工作区实况 | 我做了什么 |
|---|---|---|---|
| 231-A 撤横档 | completed | ✅ 已落地（`PAGES_HORIZONTAL` 0 处，判据在） | 未动 |
| 231-B 顶栏模糊 | completed | ✅ 已落地（`VeneraTopBarBackdrop` + `blurBackdropSource` + 玻璃座） | 未动 |
| 231-C 节头对齐 | completed | ✅ 已落地（`MiuixSectionHeader` 7 处、`wallAdjacent` 10 处） | 未动 |
| 231-D 动作行/标签 | completed | ✅ 已落地（`PixivWorkActionRow` + `FlowRow`） | 未动 |
| 231-E hero 转场 | pending | ❌ **两端都断** | **接上了（见 7.3）** |
| 231-F 评论腿 | pending | ❌ 前提被证伪 | **不写代码（见 7.4）** |
| 231-G 取不到图 | in_progress | 他手上 | 未动（避免撞车） |

### 7.3 231-E：接上的那一半与**没接**的那一半

**接上了（这一批真正的缺口）**：墙那两排卡**从不回写落点** ——
`PixivWorkCard` 只调 `onOpen(work.post)`，`noteCardBounds` 一次都没调过。
于是作品页离场时 `cardBoundsOf` 查什么都查不到，返回永远是"整页抬回去"。

改动两处：

1. **墙侧**（`GalleryPixivHome.kt` · `PixivWorkCard`）：`onGloballyPositioned` 回写
   `GalleryFlyIn.noteCardBounds(work.post.viewerKey, bounds)`，带"变了才写"闸门，
   `DisposableEffect` 里 `forgetCardBounds` 摘掉。
2. **作品页侧**（`PixivWorkScreen.kt`）：`exitTargetOrNull()` 按**作品键**查落点，
   返回键接上它并在 logcat 记一句"有落点/没落点"。

**两端同一把键这件事是这一批最容易静默写错的地方**：墙回写若用 `uid`、
作品页查 `viewerKey`，两边各自都"写对了"，只是永远查不到 —— 不报错、不崩溃。
所以我立了两条判据把它钉住（`墙回写的落点与作品页查询的键是同一把 viewerKey`、
`作品页离场要按同一把键取落点`），含"误用 uid"的负向断言。

**没接的那一半（如实说）**：**离场动画本身没画**。
`GalleryFlyIn.origin` 的 setter 是 `private`，唯一写入口 `capture(view, bounds)` 必然要
截一块像素（PixelCopy）；返回时屏上是我们自己的画面、起点不是一张卡，**没有可截的那一块**。
硬塞落点要么改交接件的封装（那颗跨三屏共用），要么伪造一次 capture —— 两条都是
"为这一屏好看去动别人的地基"，不做。

所以这一批交付的是"**查哪儿**"（可被判据验证、真机 logcat 可读），
"**怎么画**"留给离场动画那一套接进来时补。⚠️ 用户在真机上看到的仍是"整页抬回去"。

### 7.4 231-F：前提被证伪，所以不写代码

任务要求"端点与参数必须真验一次才能上线（不许凭印象写路径）"。我验了：

| 形状 | 结果 |
|---|---|
| `www.pixiv.net/ajax/illust/{id}/comments?limit=5&offset=0` | **404** |
| `www.pixiv.net/ajax/illust/comments?illust_id=…` | **404** |
| `www.pixiv.net/ajax/illusts/comments?illust_id=…` | **404** |
| `www.pixiv.net/ajax/illust/{id}/comments?lang=zh` | **404** |

与仓内 2026-10-18 的记录一致：「评论列表匿名四形状 404 ⇒ 那一节写明走登录腿、
拿不到整块撤下不摆空壳」。而作品页**已经是这个处理**（`PixivWorkScreen.kt:137` 那条注释
写着"站方只给得到数，屏上念数的地方只有一处"）。

⇒ **这一条的正确交付是"不写"**：再写一遍就是重复既有实现，摆一排空壳更是本仓反复修掉的假件。
用户 2026-10-19 也确认了这个口径。

### 7.5 这一轮仍然没验证的

- **编译没过**（Gradle 被沙箱挡在 `~/.gradle/wrapper/...zip.lck`），静态对账：
  三颗文件括号平衡、`exitTargetOrNull` 有真调用（不是死代码）、
  判据关心的四正两负全部成立、我先前凭印象写的 `maskedOf` / `GalleryFlyIn.origin =` 已清除。
- **真机没跑**：墙→作品页的去程本来就是好的（`openPages` 自截画面），
  这一批改的是**返回程的落点来源**，屏上暂时看不出变化 —— 变化要等动画接上，
  或者看 logcat 那句 `返回程落点=…`。

---

## 8. 全量收尾（2026-10-19 深夜 · 用户「你全做完吧」）

用户暂停了 qoder 那边并给了全量权限，要求把剩下的做完。**编译与测试这一轮真跑了**，结论如下。

### 8.1 qoder 批 231 逐条终局

| 批 | 终局 | 依据 |
|---|---|---|
| 231-A 撤横档 | ✅ 已完成（他做的） | `PAGES_HORIZONTAL` 0 处；判据 `横档模式整档撤干净` |
| 231-B 顶栏模糊 | ✅ 已完成（他做的） | `VeneraTopBarBackdrop` + `blurBackdropSource` + 玻璃座 |
| 231-C 节头对齐 | ✅ 已完成（他做的） | `MiuixSectionHeader` 7 处同一把尺 |
| 231-D 动作行/标签 | ✅ 已完成（他做的） | `PixivWorkActionRow` + `FlowRow` |
| 231-E hero 转场 | 🟡 **两端接线接上了，动画未画** | 见 7.3 |
| 231-F 评论腿 | ⛔ **前提被证伪，不写** | 匿名四形状 404（7.4） |
| 231-G 取不到图 | ✅ **已完成**（他做的） | 适配层 `workDetail(id, bearer)` 已按"登录走 app-api、匿名兜底"分流 |

⇒ **批 231 没有剩下的活了**，除了那个我明确没做的离场动画（改共用交接件，需单独拍板）。

### 8.2 这一轮我补完的（画师页那块）

| 项 | 状态 |
|---|---|
| 收藏段（票据腿 + 四种成因分开念 + 续页） | ✅ 做完 |
| 点头像进画师页（首页横排 / 搜索分栏 / 墙卡署名头像） | ✅ 做完 |
| 收藏段续页（游标 = 站方 `next_url`） | ✅ 做完 |

**收藏段的三档文案**（改之前只有"未登录"一档）：
- 未登录 → 有出口的空态（不摆重试）；
- 票据取不到 / 401 / 403 → 站方原话 + 可重试；
- 登录着、答上了、空 → 「这一位没有公开收藏」。

**一个设计判断**：收藏段的卡，作者是**别人** —— 所以那一段的署名头像点进去是**那位原作者**的画师页；
插画/漫画段的头像**不挂点击**（点了等于原地不动，那是假把手）。

### 8.3 编译与测试：真跑了

```
:app:compileDebugKotlin            BUILD SUCCESSFUL
:app:testDebugUnitTest             1628 tests, 0 failed   BUILD SUCCESSFUL
:desktop:test                      BUILD SUCCESSFUL
```

### 8.4 编译/测试抓到的 6 个真错（静态对账全都没抓到）

前一轮我说"静态对账过了"，这一轮一编译就露馅 —— 值得逐条记下来：

| # | 错误 | 性质 |
|---|---|---|
| 1 | `GalleryPixivHome` 缺 `GallerySite` import + `openGalleryArtistProfile` 全限名解析不到 | 编译错 |
| 2 | `LOAD_MORE_AHEAD` 重复声明（同包 `GalleryScreen` 已有 `internal`） | 编译错 |
| 3 | 测试仍在用旧名 `PixivArtistTab`（改名 `PixivArtistSection` 时漏了测试） | 编译错 |
| 4 | **`bookmarkCount.public.illust` 是单数**，我用了 `"illusts"` ⇒ 收藏数恒 null 且**不报错** | 逻辑错（静默） |
| 5 | 判据"不引用 searchQuery"把**注释**也算进去了（解释病名即得病） | 判据错 |
| 6 | `aiType=1` 我断言成 `true`，实际 1 = **非 AI**（2 才是 AI） | 断言错 |

外加一条**架构违规**（被 `BusinessApiBoundaryTest` 抓住）：
我让画师屏直连 `PixivClient.getInstance(context)`，而本仓守卫钉着「UI 不许直连实现类，**白名单只许缩短**」。
正确解法是**加契约**（`GalleryPixivWorks` 上新增 `userHead` / `profileAll` / `worksByIds`），
不是往白名单塞一行 —— 白名单一旦能变长，那条纪律就只剩自我约束了。

**教训**：静态对账能抓括号与命名，抓不到**键名单复数**、**取值语义**、**架构纪律**这三类。
以后凡"这一轮要交付代码"，编译 + 全量测试必须真跑，不能拿静态对账代替。

### 8.5 我对别人文件的两次最小侵入（如实记）

1. `app/src/test/.../PixivAppParseTest.kt`（**未跟踪文件，他的**）：缺 `import org.junit.Assert.assertFalse`，
   导致**整个测试编译**过不去。我补了那一行 import，没动别处。
2. `GalleryPixivWorkPageWiringTest.kt`：追加了 231-E 的两条判据（任务 12 那种"立红判据"的做法）。

### 8.6 仍然没做的（明确交出去）

- **231-E 的离场动画**：`GalleryFlyIn.origin` setter 是 private、唯一写入口 `capture()` 必须截像素，
  返回时没有可截的那一块。要接就得改那颗跨三屏共用的交接件 —— 值得单独拍一次板，
  不是我该顺手改的。**当前真机行为：返回仍是"整页抬回去"**（logcat 有落点读数可查）。
- **关注改站方**：写侧 `follow/add|delete` 会改用户站方账号状态，本机无有效票据验不了形状。
- **真机验证**：以上全部是编译 + 单测级验证，**没有上机**。

---

## 9. 真机事故：`illust_id` 写成 `id`（2026-10-19 深夜）

用户截图：**作品页每一个作品都报** `pixiv 返回 400: {"error":{"user_message":"",…}}`。

### 9.1 根因

`PixivAppClient.illustDetail` 把作品号发成了 **`id`**，而站方要的是 **`illust_id`**：

```kotlin
// 改前 —— 站方回 400，且 user_message 是**空串**
.addQueryParameter("id", illustId.toString())
// 改后（与 pixivpy aapi.py:illust_detail 逐字一致）
.addQueryParameter("illust_id", illustId.toString())
```

这条腿的头注本来就写着「**本机没有这一腿的真读数**」（不带票据现量回 400，
而我们不拿用户的票据做本地取证），所以参数名是照别处猜的 —— 这一次猜错了。

### 9.2 为什么这个错特别阴

| 现象 | 后果 |
|---|---|
| `user_message` 返回**空串** | 屏上那句"400"不带任何能指向参数名的线索 |
| 匿名腿是**好的** | 那条走 `ajax/illust/{id}`，号在路径里、没有参数名 ⇒ 不登录的人永远复现不了 |
| 只有**登录态**走 app-api | 一个人一旦登录，整个作品页全废 —— 而他看到的是"我明明登录了" |

### 9.3 修的四处

1. `PixivAppClient.illustDetail`：`id` → `illust_id`（附上为什么不是 `id` 的注释）。
2. **新增 `PixivAppApiParamsTest`**：钉住"app-api 任何一处都不许再用 `id` 作作品号参数名"。
   这是**形状级**判据 —— 将来谁在哪条腿再写一次 `id`，当场红掉，
   而不是等到某个登录用户把整个作品页点成 400。
3. 顺手接了 **批 231-F 评论腿**（见 §10）—— 同样用 `illust_id`，一并被那把尺子罩住。
4. `PixivCommentPage` / `PixivComment` / `PixivAppParse.illustComments`：评论数据模型与解析。

---

## 10. 批 231-F 评论腿：做完了（不是"不写"）

上一轮我说"匿名四形状 404 ⇒ 不写代码"。用户回「我这边是已经登录的了」——**这句话是对的**，
我上一条结论的适用范围被我画错了：

- 匿名确实拿不到（四形状 404，这一条我实测过，没错）；
- 但 **app-api 带票据是拿得到的**，而我上轮只用匿名去证，等于用一个已知不成立的输入
  去否掉整条腿。

### 这一轮做的

| 件 | 内容 |
|---|---|
| `PixivAppClient.illustComments` | `GET /v1/illust/comments`，`illust_id` + `include_total_comments=true`；翻页只认 `next_url` |
| `PixivComment.kt` | `PixivComment` / `PixivCommentPage` 两个模型 |
| `PixivAppParse.illustComments` | 解析：`user:{}`（删号）照样交、`parent_comment:{}` = 没有父评论、有 N 条却认不出 ⇒ 抛 |
| `GalleryPixivWorks` + 适配器 | 契约加一条、Android 侧转发 |
| 作品页 UI | `PixivCommentsState` **四态封闭类型** + `PixivCommentsSection` + `PixivCommentRow` |
| `PixivCommentParseTest` | 6 条判据 |

### 四态而不是"列表 + 错误串"

未登录 / 在取 / 取失败 / 取到了 —— **四句不同的话**。折成两个变量就会出现
"有错但列表非空"这种说不清的组合（那时该念错还是该念列表？）。

顺带一条：评论**不做无限下拉**（第一页十条够"顺带看一眼"；要看全部去站方网页，
动作行那枚「在浏览器里看评论」就是那条出口）—— 这一屏的主轴是图，不是评论区。

---

## 11. 批 231-E 离场动画：也做完了

上一轮我说"必须改共用交接件，不做"。这句话**也不准确**：我当时只查了
`GalleryFlyIn.origin` 的 setter 是 private 就下了结论，没往下看一层。
实际上仓里**早就备好了两层共享判据**：

- `domain/GalleryExitExpansion.kt` —— 整帧算式（**有单测逐位钉着**）；
- `domain/GalleryExitGesture.kt` —— 跟手返回的状态机（四道门闩）。

只缺**渲染**那一层。所以这一轮只补了一颗新件 `ui/GalleryExitFlyBody.kt`
（把 `GalleryPostScreen` 里那 55 行 `drawBehind` 抽出来，**没有改动那一屏的任何一行**），
再把作品页接上：

```
exitTargetOrNull()  →  exitTarget 拿墙上那一格
captureRegion(整屏) →  截本屏此刻这一帧
exitFly.animateTo(1) →  弹簧（与进场同档 StiffnessMediumLow）
```

三条退路都守住：**没有落点 / 截不到帧 ⇒ 直接 finish**，不把"没能飞"演成"飞完了"。

---

## 12. 本轮验证与产物

```
:app:compileDebugKotlin   BUILD SUCCESSFUL
:app:testDebugUnitTest    1637 tests, 0 failed   BUILD SUCCESSFUL
:app:assembleDebug        BUILD SUCCESSFUL
```

产物（`app/build/outputs/apk/debug/`，三个 ABI + universal，各 106.7 MB）：
`app-arm64-v8a-debug.apk` / `app-armeabi-v7a-debug.apk` /
`app-x86_64-debug.apk` / `app-universal-debug.apk`。

签名沿用 debug 那一档挂的**正式证书**（`build.gradle.kts` 里
`if (hasReleaseSigning) signingConfig = release`，而 `key.properties` 在位）——
所以可以直接覆盖已装的正式包，不用卸载重装（阅读统计、收藏、已下载的本子都留着）。

### 编译/测试抓到的错（这一轮 3 个）

| # | 错误 |
|---|---|
| 1 | `return@back` —— 挂在 `val` 上的 lambda 没有可 return 的标签，改成 `if/else` |
| 2 | `PixivAppApiParamsTest` 把 `illust_id` 的出现次数写死成 1，但评论腿也是 `illust_id` ⇒ 改成 `>= 1` |
| 3 | 评论"认不出"那条断言的字面量与抛错文案不一致（文案写的是"认不出 id 与正文"，断言找"认不出"） |

### 仍未验证的

- **评论腿没上过真机**（本机无有效票据）。端点形状取自 pixivpy `aapi.py`，
  `PixivCommentParseTest` 用抄来的样本钉住解析，但**答复真身**要你上机看一次。
- 离场动画同样没上机：截帧 + 弹簧 + 落点这条链在单测里跑不到
  （`exitExpansion` 那层有单测，但 PixelCopy 与窗口动画没有）。
- `PixivAppParseTest`（他人未跟踪文件）我补过一行 `assertFalse` import，
  那一行是为让测试能编译，不是功能改动。
