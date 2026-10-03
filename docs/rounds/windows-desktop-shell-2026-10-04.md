# 桌面多域骨架 —— 结构说明与认领路线

> 2026-10-04，提交 `ee91f51`（12 files, +1319/−405）。接 `20b3cc6`（图库首页视觉）之后。
> 起因是一句「先把整个应用的框架做好」。这一份是**骨架的地基说明**：下一批之后每一个
> 桌面端任务都落在这张图上，所以它比轮次日志更该常驻。

---

## 一、动手前量到的三件事（结论先于代码）

框架之所以要重建，不是因为"页面少"，是因为量出三件**比缺页面更严重**的事：

### 1. 桌面端此前只有一个画面

`VeneraDesktop()` 根组合里约 300 行状态（`cards` / `covers` / `session` / `open` / `pages` /
`pageStatus` / `showFavorites` / `favTree` / `favStatus` …），三个 `LaunchedEffect` 忙着往里写，
而这批东西**一个消费点都没有** —— 最后一行只渲染 `DesktopGalleryHome(ports)`。

逐个引用数下来，**每一条的全部引用都是自己赋值给自己**。`ff0757e` 之前与之后都是这样
（那笔只拆了外层 `NavigationView`，43 行改动）。

问题不止是"写了没用"，是**它在真花钱**：`main()` 一开口就装载源脚本（`sources/jm.js`
38626 字节）、拉探索页、**串行拉 30 张封面图**，喂一串从不参与布局的变量。
用户能感觉到的只有"启动有点慢"，看不到那三十次出网是白跑的。

### 2. rail 是**假导航**

`DesktopGalleryRail` 收的 `selected` 由调用方**写死**成 `DesktopGalleryDomain.GALLERY`。
于是点另外两枚只会往主区底部吐一句缺席说明，**当前域从来不变**。

这是本仓最忌的形状：**看得见七个格子、看得见 hover 反馈，点下去哪里也没去**。
比"没有这个按钮"更坏 —— 因为它主动许了一个没兑现的诺。

### 3. 缺席有**两档**，混成一句就是伪造

逐域核实桌面真数据源（不是凭印象）后的账，见下表。结论是**五枚缺席域的取数链全部已接线**，
缺的都是界面。只写一句「暂不支持」会把"接好了但没画"念成"这台机器上没有" —— 后者是假的。

---

## 二、骨架结构

```
VeneraDesktopApp(ports, comic)                    ← 应用根（VeneraDesktopApp.kt）
└─ Column
   ├─ DesktopGalleryTopBar          跨三栏（稿 `.tb` 在 `.body` 之外，`:340`）
   └─ Row
      ├─ rail   48   七枚域         底色 #1C1C1C（比 pane 暗一档 = 层级差本身）
      ├─ pane  224   当前域的页条    底色 #272727；**七域共用这一处宽度**
      └─ 主区         当前域当前页
```

**分发有两级，界线很清楚**：

| 级 | 谁分 | 依据 |
|---|---|---|
| 域 | `VeneraDesktopApp.DesktopDomainBody` | `DesktopDomain`（七枚） |
| 页 | `DesktopGalleryHomeBody`（图库域）/ `DesktopComicBody`（漫画域） | `DesktopGallerySectionContent`（六档） |

两条路**不许串**：图库域的 body 收到漫画域的行就 `error`（附出处），靠 `when` 穷尽检查顶出来。
另开第二颗行模型是本仓判过负的"第二套布局系统"在更小尺度上的重演，所以
`DesktopGalleryHomeRow` 是**全桌面共用**的一颗。

### 七枚的形状照稿 `:354-364`

```
图库 发现 搜索 收藏 画师     ← 一组，组内 4dp
  ── 8dp ──                  ← `:359`，叠在 4dp 之上 = 12dp，够看出是分组
漫画
  ── 弹性留白 ──             ← `:361` 的 `.spacer`，把「设置」推到底
（鸟居 / 博麗神社）           ← `:362-363`，装饰层，本批不做
设置                         ← 贴底
```

---

## 三、七域的账（这张表是认领路线图的依据）

「已接线」一列全部**逐颗核实过接口归属**，不是印象：

| 域 | 缺席性质 | 取数链（桌面） | 缺的是 | 认领成本 |
|---|---|---|---|---|
| **图库** | — | 全接线 | — | 已完成（pane 六项） |
| **漫画** | — | `EngineSession` + `LocalFavoritesManager` | — | 已完成（探索 + 收藏夹） |
| 搜索 | 缺界面 | `BoardSource.searchPosts(tags,page,limit)`（三站 board 继承）、`searchTags(term)`、`GallerySearch.tagBudget/fitstagBudget` | 结果墙 + `GallerySearchViewModel`（在 `gallery/ui`，桌面没挂那颗 srcDir） | **低**：取数全通，差输入框 + 一面墙 |
| 收藏 | 缺界面 | `GalleryFavorites.favorites: StateFlow`、`GalleryFavoritesStore`（本机 JSON）、toggle/removeAll/clear | 一面墙（当前**消费数 0**） | **低**：`GalleryFavorite.toPost(site)` 已在 |
| 画师 | 缺界面 | `GalleryArtistFollows.follows`、`GalleryArtistDirectory.danbooruArtistUrls/ArtistCredits/probeAvatarUrl` | 画师墙 + 单人页 | 中：详情页要接 `GalleryArtistProfile` |
| 设置 | 缺界面 | `GalleryPreferences` 12 枚 StateFlow（`DesktopGalleryPreferences`）、`GalleryContentGuard.rules/nsfwMaskMode`（直连 `GuardRuleStore`） | 设置页 + **写侧** | 中：桌面今天 getter-only，改不动 |
| 发现 | 缺界面 | `GalleryForYouMerge`（`gallery/domain`，**整颗已在桌面编译面**）、`GalleryMerge.isDisplayable/dedupKeys` | ForYou 一屏 + `GalleryForYouViewModel` | 中高：分页 + 跨页去重的状态机 |

⚠️ **「收藏」与「漫画域的收藏夹」不是同一份数据**：
前者 `GalleryFavoritesStore`（`gallery_favorites.json`），后者 `LocalFavoriteDatabase`（本机 SQLite）。
两边条数不相等是正常的，把两个域的收藏数念成同一个数就是假读数。

---

## 四、四条取舍（都写了为什么不选另一条）

1. **缺席域可点，缺席页在主区；图库域 pane 里那四行未实现行不可点。**
   看着像自相矛盾，其实不是：那四行是"**同一域内部**的某一页还没做"，域本身是通的、
   把它们撤了也说得通；rail 上七枚是**并列**关系，缺席域不给立足点的话，用户只能观察到
   "点了又弹回图库"，看不到为什么。
2. **`paneKeys` 是 `域 → key` 的表，不是一颗共享 `selectedKey`。**
   共用一颗的代价：在图库翻到「历史」、去漫画转一圈回来，高亮停在「每日热门」而 body 是另一页 ——
   具体表现取决于哪个 write 先到。
3. **`desktopDomainRows` 特意不标 `@Composable`。**
   为了让判据能直接取值而不是扫源码。**改一行注释就能绕过正则，但绕不过一次取值。**
4. **坦白页刻意不居中。** 居中的空态会把"这一屏没东西"变成版面设计的一部分，
   而它要说的恰恰相反：**东西是有的，界面没有**。

---

## 五、判据（`DesktopDomainNavigationTest`，10 条）

**能真跑的一律真跑**（`DesktopDomain` 与 `desktopDomainRows` 是同包可见的枚举 / 纯函数）：

| # | 断言 | 为什么这么钉 |
|---|---|---|
| ① | 七枚次序照稿 | `isInTopGroup`/`precedesSpacer` 是**序数比较**，插一枚设置就掉到半空 |
| ② | `content` 与 `reason` 同真同假 | "既能渲染又有理由"会渲染出一句从没念出口的缺席 |
| ③ | 缺席短句 ≤30 字且 `wired` 非空 | 30 字是 `DesktopUnimplementedRow` 的 `maxLines=2` 实测预算，超了被省略号吃掉 |
| ④ | 禁「敬请期待/暂不支持/开发中/TODO」 | 那描述的是时间表，不是缺席的东西 |
| ⑤ | 七域各自有页条、key 不重复 | 空 pane 让"这个域有没有东西"变成一次猜测 |
| ⑥ | 图库与漫画 `absence == null` | 防回滚：临时挂回缺席说明 = 那三十行 IO 又变回从不消费 |
| ⑦ | `selected = domain` 且不出现写死常量 | 假导航的机械防线 |
| ⑧ | `paneWidth` 在应用根只引一次 | 一处一次矛盾 = 七套布局的开始 |
| ⑨ | 旧根组合已删 + 那批状态变量不在入口文件 + `--autofav` 还在 | 启动空跑是本批最贵的读数，但取证不能跟着打死 |
| ⑩ | `DisposableEffect(session)` + `onDispose { session?.close() }` | key 写 `Unit` 的话"重取"一次漏一颗 GraalJS context |

旧判据改了两条引用（`DesktopGalleryHome.kt` 已删）：`DesktopShellLayoutTest` ④⑤ 改扫
`VeneraDesktopApp.kt`，⑤ 的搜索位判法从"那句话没被删成空串"**升级**成"点了域真的换了"
—— 一句提示会被删空，一次导航不会。

---

## 六、下一批候选（按"能不能直接接线"排序）

| 优先 | 做什么 | 理由 |
|---|---|---|
| 1 | **画廊收藏域**（`GalleryFavorites` → 墙） | 取数全通、`toPost` 已在、当前消费数 0，是五枚缺席域里最便宜的一枚 |
| 2 | **搜索域**（输入 + 结果墙） | 三站 `searchPosts` 全通；缺输入控件与一面墙 |
| 3 | 设置页 | 读数全在，但**写侧要新建**（桌面今天 getter-only），是本批唯一"接了也不能改"的域 |
| 4 | `.pane` 的「以图搜源 / 管理组 / panefoot」+「最近浏览」节 | 都在"桌面没有对应能力或没有目标页"这一范畴 |
| 5 | 装饰层五件（鸟居 / 樱花 / 注连绳 / 縦排「博麗神社」/ 网点） | 稿上纯装饰，本仓明确排在功能之后 |
| 挂起 | **「精选摘要模块」** | S4 `:117` 那句"稿上最上面那一条"在 736 行权威稿与 748 行旧快照里**双向查无"摘要"二字**，缺口记载本身有歧义，等用户指认 |

---

## 七、读数

| 通道 | 结果 |
|---|---|
| `:desktop:compileKotlin` | BUILD SUCCESSFUL |
| `:desktop:test` | **224 用例 / 0 失败**（本批 +10） |
| `:app:testDebugUnitTest --rerun-tasks` | **818 / 0**，基线 814 ⇒ **+4 来自并行会话**的 `SourceExplorationNativeEntryTest`；本批对 `app/` 的改动数 **0** |
| 冻结清单交集 | 0 |
| 视觉 | 真开窗自截图（1440×900）：初始域=图库；rail 七枚齐全、设置贴底；切搜索/画师落坦白页且清单逐颗点名；切漫画真出网（源 jm，6 分区 72 条 / 出图 30 张） |
