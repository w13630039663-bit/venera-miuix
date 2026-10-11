# 批 76 · 漫画卡那颗「收藏」按得动了（2026-10-09）

## 这一格是什么

盘点（`C:\Users\leimi\.qoder-cn\plans\clever-mesa-quail.md` §1）里排第一的那一格：**写链整条在，屏上没有入口**。

- 共享面 `LocalFavoritesManager.addComic`（抛=那一夹不存在、返回 `false`=这本已经在里面）早就在桌面编译面里；
- 三态那三句也**逐字照 Android 的 `ComicCardContextMenu` 抄好了** —— 而唯一的调用点是 `VeneraDesktop.kt` 里 `--autofav` 那段取证路径（旧的私有 `addFavorite`）。

于是漫画域那一屏：探索看得到、收藏夹进得去，**存不进东西**。用户点不到的那颗钮不会自己报错，所以这一族活了很久。

## 改了什么

| 件 | 动作 |
|---|---|
| `gallery/ui/DesktopComicFavorite.kt`（新） | 三态从"返回一句 String"改成 `DesktopComicFavoriteOutcome`（`Added` / `Already` / `Failed(reason, error)`）；三句上屏的话、点击那一行读数、没成那一行读数各成纯函数；写那一笔走 `withContext(Dispatchers.IO)` 且吃**懒装载那颗**（`favorites()`），不自取 `getInstance` |
| `gallery/ui/DesktopComicPane.kt` | 卡角一枚 `ComicFavoritePill`（`OverlayScrim` 圆座 + 10.sp，压在封面上那一角 —— 与画廊卡那两枚角标同位置口径，因此不动 `estimatedGridHeight` 那笔高度账）；按下去起协程写库，结果落进 `favoriteOutcome` 并**摆上屏**（失败那态用 `AccentBeni`） |
| `VeneraDesktop.kt` | 旧的私有 `addFavorite` 撤了，取证段改按同一枚 `desktopAddComicFavorite(::favorites, …)`；退出码从"消息串前缀"改成认 `Failed` 那一态（`!message.startsWith("收藏失败")` 那种字符串判法一并下架） |

常驻而不是 hover 才出现：桌面没有"悬停=可点"的暗示，藏起来的入口等于没有入口。

## 读数（真机，走的是屏上同一枚动作）

```
D_收藏 点击 源=jm id=591677 -> 已收藏到「默认」      D_收藏 命中=2   退出码 0
D_收藏 点击 源=jm id=591677 -> 这本已经在「默认」里了  D_收藏 命中=2   退出码 0
```

第二笔是**同一本再按一次**：条数没涨（`命中=2` 不变），而那句与"没成"分得开 —— 这正是这一批要修的那个观感。
第三态（`Failed`）**只有判据级读数**：真机上那一夹存在，`addComic` 没理由抛，本批没造第二枚假夹去撞它。

屏上：起窗 `--start-domain=COMIC` 拍到卡角那排「收藏」（`_qa/b76_card_crop.png`，原分辨率局部）；
关窗后残留 0。**交付包也起了一次**（`_qa/desktop-exe-2026-10-09-b76/launch-venera.ps1 --start-domain=COMIC --proxy=…`）：
读数 `D_落点 域=漫画(启动) 行=探索 … 种子=detail+viewer`，帧上卡角那颗【收藏】在（`_qa/b76_pkg_card_crop.png`），
包内 `jar -tf` 数到 `DesktopComicFavorite` 7 条 ⇒ 用户双击的那一颗就是这一批那颗。关窗残留 0。
那句**结果**（已收藏 / 已在 / 失败）按禁鼠标令只能由用户自己按一次才算验过 —— 挂在 §17 第 2 项第 ⑤ 枚。

⚠️ 那一帧的右半被 QQ 的窗口压住了（`activate_window` 报 `isForeground:true / zOrder:0` 而 QQ 仍画在上面 ——
就是那条「alwaysOnTop 位上了也不算压过去」的反方向版本）。卡角那一片是 Venera 自己的像素，结论不受影响，
但**"窗口在前台"不等于整块内容没被盖**，读帧前先扫一眼是不是处处都是自家像素。

## 尺子：9 条，五笔扰动各咬各的

`DesktopComicFavoriteTest`（9 条）＋ 台架 `_qa/b76_perturb.js`（`sh _qa/perturb_driver.sh b76 5`）：

| 假动作 | 红了哪条 |
|---|---|
| P1 药丸摆着而 `clickable` 换成空修饰 | ③ 卡那一排有收藏那颗 |
| P2 「已在里面」塌成「已收藏到」 | ① 三态各一句 + ② 两端同词 + ⑦ 三态读数互不相同 |
| P3 照着对岸补一行 `author = card.title` | ⑤ 落库字段不许编 |
| P4 落库了而不念没成那一行 | ⑦ 写口两步（落库+出声）＋ 顺带被零调用点那把抓到（`desktopComicFavoriteRefusalReading` 成了仅测试引用） |
| P5 整枚入口撤下（本批修的那一格的原始形状） | ③ 出现次数 2→1 |

扰动全跑完的还原态复跑：**794 判据 / 101 类 / 0 失败**（`_qa/three_way_counts.js` 三方对得上），判据目录 0 处漂移。

## 台架自己那一处洞（顺手补了）

P2 第一跑只报了两条 RED，而判据 ① 明明也该红。查下来是**报表**吞的：
`b74_perturb.js` 抄下来的 `report` 用 `<failure message="([^"]{0,140})"` 捕消息，
超 140 字的失败消息**整条匹配不到** ⇒ 台架念"这笔没红"，而尺子其实咬了。
改成 `([^"]*)`（显示时再截）。⚠️ 这一族的形状是：**量具的输出截断不等于被测对象没说话** ——
以后凡是"扰动没红"的读数，先问报表的捕获上限。

## 没做什么

- 卡整枚的 `onClick`（点开一本漫画 → 详情 → 阅读）不在本批，那是批 80–82 那一整块；
- 卡上没摆"这本是否已收藏"的亮灭态：那要按 id 查一遍默认夹（`getAllComics` 全表），本批没做，
  所以那颗钮**恒写着「收藏」**，按下去才说真话。这句在 §17 的"要不要接"清单外面，先记着；
- 收藏面板那一屏仍然只读（批量清空/删除是批 77）；
- `app/` 本轮零改动。
