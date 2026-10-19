# 第九轮：下拉切源 / 玻璃材质跟轴 / 画师裸圆头像 / 排行榜图列（2026-10-17）

用户这一轮带了三张稿图、说了六件事（前五条一次提，第六条是追加）：

1. 「源切换改成下拉选择」—— 从双格分段器改成一颗胶囊 + 下拉菜单；
2. 「搜索框和源切换器都要做成我们已经有的液态玻璃材质，但是要在全局切换材质保留切换原来的 md3 样式」；
3. 「关注画师和排行榜都改成图列这种」；
4. 「只显示头像」；
5. 「推荐画师合并到发现和已关注供切换」「排行榜也按照图列优化 ui」；
6. **追加**：「图库那边的关注画师列也要同步更改，不要只改 pixiv 那边的」。

---

## 一、源切换：分段器 → 下拉

`GalleryHomeDomainChips` 整段换成 `GalleryHomeDomainDropdown`（`GalleryPixivHome.kt`）。
旧实现是「两格等宽 + `drawBehind` 画滑块 + 前缀和算选中格位置」，删干净；新实现是
`Surface(胶囊) + DropdownMenu(条目)`，菜单条目里选中那一项染 `primary`，点未选中项才回调
（`if (option != selected) onPick(option)` —— 点当前项只关菜单，不重取）。

**为什么下拉不再需要 m3 的分段器语义**：稿上它就是一颗带 `▾` 的胶囊，宽度跟着字走，
不再铺满整行。旧分段器那套"等宽铺满 + 滑块动画"在这里反而是错的信息（它暗示"两档同等重要、
随时可切"，而稿上它是一颗入口）。

⚠️ 锚点改名后 `GalleryHomeSectionsTest` 与 `UiAuditG3Test` 两处判据一起改（判据里那些
「道数」断言不变：切换器那一圈外框仍与搜索条共用同一条 `segmentedHeight` 带）。

## 二、材质：玻璃是**跟着全局轴走**，不是写死

三条一起写在同一处（判据钉住四条同时在场）：

```kotlin
color = if (veneraGlassEnabled()) Color.Transparent else tokens.color.surfaceContainerHigh,
shadowElevation = veneraSurfaceShadowElevation(tokens.elevation.attached),
modifier = Modifier.veneraGlassSurface(VeneraGlassRole.CHIP, tokens.shape.extraLarge)
```

- `veneraGlassEnabled()` 只读**材质轴**（`SurfaceMaterial`），与 `AppearanceStyle`（MIUIX/MD3）无关
  ⇒ 用户要的"全局切回材质时保留原来的 md3 样式"是同一句话：材质 SOLID 时这三件套全部退场，
  回到 `surfaceContainerHigh` 实色胶囊，一个字都不用为 MD3 单独写分支。
- **`shadowElevation` 必须过 `veneraSurfaceShadowElevation(...)`**：玻璃面自己不许带投影
  （miuix `Surface` 把投影挂在外层 `graphicsLayer` 上，卡底透明时那层黑会透出来 —— 2026-10-16 定案）。
- 角色取 `VeneraGlassRole.CHIP` → `Tier.CONTROL`（真模糊 + 细描边），与搜索条同一个档。

## 三、画师：裸圆头像 + 两档切换（pixiv 侧）

- `PixivArtistRail`：从「88dp 方卡」改成**裸圆头像 + 一行名字**。56dp 脸 + 8dp 间距 ⇒ 一屏 5 位
  （方卡那档只有 4 位，与稿上"左右滑看更多"对得上）。
- 新增 `PixivArtistTab`（`FOLLOWING("已关注")` / `DISCOVER("发现")`）+ `PixivArtistTabRow`（复用 `PixivPillRow`）。
  「关注画师」与「推荐画师」两节合并成一节「画师」。
- **切 Tab 不取数**：两条腿（`/v1/user/following`、`/v1/user/recommended`）在 `GalleryScreen` 里
  同时取、各存各的（`pixivFollowing` / `pixivRecommendedUsers`），切 Tab 只是换"摆哪一份"。
  做成"切过去才取"会先空一帧，而那一帧在屏上读起来像"没有关注的人"。
- 点一位画师 → 打开他在站方的用户页（Custom Tab 优先，退回裸 VIEW）。本仓没有 pixiv 画师主页那一屏，
  而现造一屏要再接一条 `/v1/user/illusts` —— 在那一屏存在之前，"打开站方页面"是唯一诚实的动作。

## 四、排行榜：横滑 + 名次角标

- 收起态从 `chunked(3)` 三列网格改成 `LazyRow` 定宽卡（一屏露出第三张的一半 ⇒ "右边还有"看得出来）；
  展开态仍是网格（用户已经明说要"看全部"）。
- 名次不再拼进标题（`"01 · ${title}"`），改成左上角**角标**；标题回到纯 `work.title`。
  序号取站方给的 `rank`（1..500），不是"这一排里的第几枚" —— 展开、翻页、去重之后两者不等，
  拿位次冒充排名就是编读数。

## 五、图库站同步（用户追加那条）

`GalleryHomeSections.kt` 的 `GalleryArtistRow`：

| 项 | 旧 | 新 |
|---|---|---|
| 一格 | `VeneraCard` 88dp 方卡 | 裸 `Column`，宽 = `artistAvatarSize`(56dp) |
| 手势 | `VeneraCard` 的 `onClick`/`onLongClick` | `combinedClickable` + `VeneraPressFeedback.indication` |
| 卡里读数 | 名字 + 「[站标] N 张收藏」 | 只有名字（caption 档，居中） |
| 溢出 | — | 节头右边「另有 N 位」（真数，未动） |

- 单击仍**直接搜**、长按仍**进介绍页**（2026-09-30 拍板 2 那条不动）⇒ 必须是 `combinedClickable`。
- 卡面撤了 ⇒ 按压反馈得自己挂。不挂就是"按下去没回话"，这一栏 2026-10-16 刚修过同一种病。
- **「N 张收藏」连同站标一起撤了**：56dp 一格装不下第二行读数（旧 88dp 方卡的内容宽 72dp 才勉强够），
  硬塞会把名字挤成省略号。这两条读数没有消失 —— 长按进的介绍页上有站点徽标，张数随搜索结果一起看。
- 两枚令牌因此退役（只减不增）：`artistAvatarSlotWidth`(88dp)、`artistSourceMarkSize`(14dp)。
  `artistAvatarSize` 现在既是**脸的直径**也是**格的宽度**。
- 图库站没有"推荐用户"那条腿 ⇒ **不套两 Tab**，只统一视觉。

## 六、这一轮修掉的一把坏量具

`GalleryPixivHomeWiringTest.画师那一格是两档切换，两栏数据同时在场` 一直是红的，而**代码是对的**：

```kotlin
val from = sections.indexOfFirst { it.value.contains(...) }   // ← 过滤后列表里的「位置」
val body = sections.filter { it.index in from..to }           // ← 比的是「源码行号」
```

`codeLines` 返回 `IndexedValue`，`indexOfFirst` 给的是位置，`filter` 比的是行号 —— 这个文件注释多，
位置比行号小一大截，切出来的区间是空的，第一条断言就红在一个空串上。修法：一路走 `.index`，
锚点也改钉 `when` 分支那两行（带 `->`），否则又会先撞上 `galleryHomeSectionOrder` 里那两行声明。

**教训（可复用）**：凡"先找锚点再切一段"的判据，锚点与区间必须用**同一套坐标**；
`List.indexOfFirst` 与 `IndexedValue.index` 混用是这一类判据最隐蔽的假红/假绿来源。

## 七、判据与验证

- 新增：`GalleryHomeSectionsTest.图库站的关注画师那一行与 pixiv 同形：裸圆头像，不再是方卡`
  （按**函数体**切段，不按文件切 —— 同一颗文件里「每日热门」仍在用 `VeneraCard`，按文件切会被它喂饱）。
- 改：`GalleryPixivHomeWiringTest`（量具修正 + 锚点改 `->`）、`GalleryHomeSectionsTest`（下拉锚点）、
  `UiAuditG3Test`（分段器那条整条重写成"下拉 + 材质跟全局那一轴"）、
  `GalleryPixivAccountWiringTest`（同源 `authState`）、`GallerySearchRealDeviceFixesTest`（投影赋值点 2→3）。
- 全量：`:app:testDebugUnitTest` 1425 条全绿；`:app:compileDebugKotlin` 无警告新增。
- 包：`D:\venera-arm64-debug-2026-10-17-p9.apk`（103,589,480 B）。

## 八、没做的 / 留给下一轮

- **真机自验未做**（2026-09-26 令：agent 侧到"编译 + 单测 + 出 APK"为止）。需要用户上手看的三处：
  ① 下拉菜单在玻璃材质下的弹层底色（`DropdownMenu` 是 m3 表面，不跟玻璃）；② 图库站/ pixiv 两排
  头像的**视觉高度差**（前者只有名字一行、后者也只有一行，理论上等高，但字号档不同）；
  ③ 排行榜展开态仍是网格，收起态横滑 —— 两态之间的高度跳变。
- pixiv 画师主页那一屏仍未做（现在跳站方页面顶替）。
- `TokenBudgetFrozenTest` 三个上限未动（本轮没新增裸 dp/sp/Color）。
