# 桌面画师单人页（D 那一件）· 方案 2026-10-07

用户 2026-10-05 那句「先把其他基础功能全部做好，先不做下载这些功能」之后，rounds 那份 §12 的缺口表里
只剩 **D 画师单人页** 属于"要新造一屏"那一类（其余是"等点头"三件与整族撤出的下载）。
本文件是开工单：口径来源、形状、边界、判据清单。

## 1. 为什么今天做得起（三件早就在桌面编译面上）

对岸 Android 那一屏（`app/.../gallery/ui/GalleryArtistProfileScreen.kt`）的取数算式**不是它自己造的**：

- `GalleryRankings.searchQuery(site, filters, ranking, anchor, todayUtc)` —— `gallery/domain/GalleryRanking.kt:189`
- `GalleryTagFilter(name)` / `GalleryRanking.ALL` —— 同面
- 每站取数 `boards.<site>.searchPosts(query, page, limit)` —— `GalleryPorts.boards`，桌面 `DesktopGalleryBoards` 已实现三站

⇒ 桌面这一屏**不需要新造任何一条口径**，只需要"把对岸那三行搬过来"。
按 §0 坑 ㉓ 的规矩：先看域里有没有现成算式，有就吃它，不手抄。

## 2. 四条口径（逐字回核对岸，不许桌面重定）

1. **query = 按名字搜这一位的标签**，并走 `ranking = ALL` 那一档：
   对岸注释钉着「`ALL` 本身就是全站按分数（两站后缀 `order:score` / `sort:score:desc` 都在 `GalleryRankings` 那一处）」，
   桌面不另造参数、不互抄写法。`anchor = null`（单人页不按日期锚）。
2. **一次取 18 张**：对岸 `SLOT_FETCH_SIZE = 18`（那颗文件里的 private 常量）⇒ 桌面自持一颗同名预算常量，
   值由判据回核那颗文件的那一行（现成口径，不造新数字）。
3. **发哪一站由目的地决定**：画师名单那一行本来就带着 `site`（`GalleryFollowedArtist` 的站），
   所以这一屏是**单腿** —— 与搜索屏"全部 = 三腿"不是一件事，读数不能共用"三站"那套词。
4. **"这一站没答上"与"这位只有这几张"是两句话**（对岸同一条纪律）：前者要在屏上说出来，
   后者说"这一位在这一站今天只有这些"。而"没取到别名 / 没取到外链"那两档**不说** —— 屏上永远不摆"这位没有 X"。

## 3. 形状

- 第三级目的地：`DesktopArtistTarget(site, name)`。主区在图库域里、该槽非空时画这一屏；
  段头左侧一枚「← 画师名单」把槽清 null（返回不落导航栈 —— 桌面没有那一层，见 §0 坑 ⑰ 那族）。
- 复用现成件，不新造第二份：墙面 `DesktopGalleryPostWall`、守卫 `DesktopGalleryWallPolicy`、
  分页持有件 `DesktopGalleryBatch` + `nextPagesForAppend`、读数那颗 `DesktopWallReadings.kt`、
  段头 `DesktopGalleryPageHeader` 同族形状。
- 入口两处：画师名单那一行恢复稿上那枚 `.go` 箭头（这次有目的地了），详情浮层画师那一栏同一下手。

## 4. 这一轮不做（点名）

- **别名解析**（批次 J 那份方案）：只在站方给 0 行时按 alias 换正名重搜 —— 那是另一件，
  本屏零张时**不猜**、不自动换名重搜。
- **外链 / credits**（pixiv、fanbox、Mastodon）：对岸那一屏的右侧读数，桌面这一版不摆，
  也不在屏上写"这位没有外链"。
- **关注/取关**：入口已在详情浮层，这一屏不摆第二份。
- 头像现取不做（这一屏的主人是图墙，不是那一位的资料）。

## 5. 判据清单（`DesktopArtistProfileTest`）

① policy 件不许自拼查询串（只许调 `GalleryRankings.searchQuery(`）
② 真跑：三个站各喂一次，`ALL` 档那两枚后缀必须真在结果里（对岸口径）
③ 18 那枚预算逐字等于对岸那颗文件的 `SLOT_FETCH_SIZE`
④ 单腿形状：目的地名单里只有一站 ⇒ 读数不能念"三站"
⑤ 失败 / 零张 / 未发车三句话各一句（不许塌成一句沉默）
⑥ 「这一屏没有画师单人页」那句随上架一起下架（禁串 + 对照：箭头真的构造 `DesktopArtistTarget`）
⑦ 零张时不自动换别名重搜（禁串点名"别名"不许进取数那一路）
⑧ 分页吃共享件（`nextPagesForAppend(`）

---

### 6. 图证状态（2026-10-07 批 20 更新）

§5 那格"本批没有图证"已作废：`--artist=<站点>/<标签>` 那颗取证种子落地，
`-Partist=yandere/anmi` 无人值守拍到那一屏（读数与帧见手册 §49 / rounds §25）。
仍未做的那一件是**续页**（段头那句"这一屏今天不接续页"仍然成立，撤它要等续页真接上）。
