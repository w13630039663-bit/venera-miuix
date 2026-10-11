# 桌面详情面板（大图页）怎么做 · 方案与代价（2026-10-04，等拍板）

输入：用户给的设计稿（`图库·今日` 那一版，右侧一枚覆盖式详情面板，页底写着
「已选 1 项 · 详情浮层在位（**内容区未重排**）」）+ 一轮只读地基调研（每条证据都带 `路径:行号`）。

⚠️ 这份是**方案**，不是完工记录。里面三处要用户拍板，我在 §6 单独列出来。

---

## 1. 先说结论：这一屏的瓶颈不是"面板"，是"卡片点不动"

桌面今天**没有任何一处能进详情**：

- `DesktopGalleryPostTile.kt:92` 那颗卡的签名里**没有 `onClick`**，手册 §11 那条"卡位上不摆像素"
  的裁决（`DesktopDailyPane.kt:54`）就是它今天不可点的出处；
- 页码 `4/8` 那一排的数据源 `GalleryViewerQueue` 整个住在 `gallery/ui`，**不在桌面编译面**
  （`app/.../gallery/ui/GalleryViewerQueue.kt:26-41`）。

所以无论选哪一档形状，第一批要还的债都是同一笔：**"这一批卡"要在桌面侧有一份可寻址的持有件**
（哪几张、当前第几张、从哪一屏来的）。这笔债与第十批 ③（发现域续页）要的那颗**分页状态持有件是同族**
—— 建议合成一颗，别造两颗。

## 2. 好消息：取数链今天已经通了

`BoardSource.fetchById(id)` 三站都有，桌面适配器也已经 override，只是**零调用点**：

- `gallery/data/GalleryBusinessApi.kt:127` `suspend fun fetchById(id: Long): Result<GalleryPost?>`
- `YandeReClient.kt:70` / `GelbooruClient.kt:130` / `SafebooruClient.kt:78`（三颗都不在排除清单里）
- `desktop/.../DesktopGalleryBoards.kt:39,55,66` `override suspend fun fetchById(...) = client.fetchById(id)`

⇒ **面板可以零额外请求打开**：墙上那张卡本来就带着一条完整的 `GalleryPost`
（`gallery/data/GalleryPost.kt:33-140`，22 个字段），点开不必再问站方一次。
这一条决定了"覆盖浮层"这一档是**便宜**的。

## 3. 稿上那六格，今天能填几格

| 格 | 能不能填 | 出处 / 缺口 |
|---|---|---|
| 来源 | ✅ | `post.site.displayName`；Android 那侧同格在 `GalleryInfoSheet.kt:428` |
| 分辨率 | ✅ | `post.width` × `post.height`（`:431`） |
| 格式 | ✅（但 Android 那侧**没有**这一格） | `post.fileExt`（三颗 client 都从 `file_url` 推：`YandeReClient.kt:274` 等） |
| 大小 | ⚠️ **两站有一站恒缺** | `post.fileSize`；Gelbooru 的站方 JSON 根本没有 `file_size`（`GelbooruClient.kt:89,449` 明写"→ 0"）⇒ 那一站必须念"这一站没给"，**不许念 `0 MB`**（本仓口径：`0` 算假读数） |
| 上传时间 | ❌ | 三颗 post DTO **都没解 `created_at`**，`GalleryPost` 里没这字段 ⇒ 这一格今天填不出来，只能整格不摆 |
| 本地状态 | ⚠️ 只有"已收藏" | `GalleryFavoritesStore.contains(uid)`（`:160`），桌面复用 `DesktopGalleryFavorites.kt:20-26`；**"已下载"两端都没有判据**（`GallerySaver.kt:228-235` 只在重名时 `exists()`，无对外查询口，且那颗件被排除在桌面编译面外） |

稿上另外三样东西同样是**结构性没有**，不是"还没接"：

- **标题**（`海渊图书馆 · 第 4 页`）：`GalleryCardTitle.titleOf(tagList, labels)` 在
  `labels.isEmpty()` 时直接回 null（`gallery/domain/GalleryCardTitle.kt:76-77`），
  而桌面的 `titleTags` 恒 null（`DesktopGalleryTagLexicon.kt:45-48`）。
- **相关标签**：全仓没有 `related_tags` 的来源端点，也没有消费点。
- **画师"作品 3,408"**：Android 那侧也没有这一读数。

⇒ 一句话：**这稿是一张"数据面超出天花板"的稿**。要么按能量到的格改稿，要么接受面板上有三格永远写着
"这一站没给" —— 后者是本仓最忌的形状，建议前者。

## 4. 四个动作按钮的可行性分级

| 动作 | 档 | 说明 |
|---|---|---|
| 收藏 | ✅ **现成** | `GalleryFavoritesStore.toggle`（`:170`，吃 `PathProvider`，纯 JSON 落盘），桌面已有读侧件 |
| 原图（在浏览器打开） | 🟡 **有路没接** | 桌面 main 今天**零** `java.awt.Desktop` 调用点 —— 但 JVM 侧 `Desktop.getDesktop().browse(URI)` 是可用的。⚠️ 别把它写成"桌面做不到"（这条本仓公开错过一回） |
| 下载（另存为） | 🟡 **有路没接，但要造一件** | AWT `FileDialog` / `JFileChooser` 可落盘；Android 那侧那颗 `GallerySaver` 走 MediaStore + `contentResolver`（`:84,118-136`），在排除清单里 ⇒ 桌面要**另造一颗落盘件**，不是摘行就完事 |
| 分享 | ❌ **今天没有对应物** | Android 那侧是 `FileProvider` + `Intent.ACTION_SEND`（`GalleryImageShare.kt:36-60`）。桌面"分享"能做的其实只有"复制链接到剪贴板"（AWT `Toolkit.getDefaultToolkit().clipboard`，同样零调用点）⇒ **建议改稿为「复制链接」**，别留一枚语义含糊的"分享" |

## 5. 三档形状，与各自的代价

| 档 | 形状 | 代价 | 与既有账的冲突 |
|---|---|---|---|
| **A 右侧覆盖浮层**（稿上那档） | 主区外面套一层 `Box`，面板 `align(TopEnd)` + 固定宽 + `OverlayScrim` 压住左半边 | 最小：`DesktopDailyHero.kt:143-152` 已经是这个形状的近似物，色 token `DesktopTheme.kt:139 OverlayScrim` 现成；只需给 `DesktopGalleryMetrics` 加一枚 `detailWidth`（口径要按 §"观感尺寸用现成口径" 那条，别造新数字） | 主区今天**没有可叠层的 Box**（`VeneraDesktopApp.kt:148-155` 直接 `weight(1f)` 填满）⇒ 要动骨架那一颗，`DesktopShellLayoutTest` 那 5 条几何判据要跟着改判据 |
| **B 独立窗口** | `DialogWindow` 另开一扇 | 中 | **玻璃/模糊跨不了独立窗口**（`project-miuix-popup-host-and-glass-window-limits` 同族：桌面玻璃采不到第二扇窗），且取证那条路要按 PID 找两扇窗 |
| **C 主区替换**（Android 那侧的满屏播放器形状） | 点卡后整块主区换成详情 | 大：等于把 Android 的 Activity 语义搬成桌面的一层导航 | 会撞上"导航条目会重建组合"那一族（返回时滚动位置、remember 丢失），且稿上明说"内容区未重排" ⇒ **与稿不符** |

**建议 A**：它与稿一致、取数链已通、且不需要新的窗口层。

## 6. 三处等拍板

1. **点卡这件事本身**：桌面卡今天刻意不可点（"卡位上不摆像素"那条裁决）。要详情面板就得先推翻它 ——
   这是**可见地改变用户屏幕**，且与那条旧裁决正面冲突，要用户点头。
2. **稿上那三格填不出来的（标题 / 相关标签 / 作品数）与"上传时间"**：改稿（去掉）还是留格写"这一站没给"？
   建议改稿 —— 留一格常年的"没给"与本仓"宁可少摆不可摆错"那条对立。
3. **三档取图算式（fast/large/file）搬不搬到共享面**：算式现在在 `GalleryPostScreen.kt:1668-1735`
   （`gallery/ui`，不在桌面编译面）。要么**搬进 `gallery/domain`**（= 动 Android 侧的文件，属冻结面，要豁免一次），
   要么桌面另立一颗（= 两份算式会漂，而漂的表现是"两端选到不同档的图"）。
   建议搬 —— 但这一步单独拍。

## 7. 建议的分期（拍板后按批走）

| 期 | 内容 | 依赖 |
|---|---|---|
| **P0** | 只读面板：点卡 → 右侧覆盖层，摆得出那四格（来源/分辨率/格式/已收藏）+ 标签墙 + 「在浏览器打开原图」+「复制链接」+ 收藏开关 | §6 的第 1、2 条拍板；A 档骨架 |
| **P1** | 三档换图（预览/大图/原图）+ 面板内翻页（← / → 与页码） | §6 第 3 条；那颗"这一批"的持有件（与 ③ 合成一颗） |
| **P2** | 下载（另存为） | 桌面侧新造一颗落盘件 |
| **P3** | 把能接的三枚偏好接上：`galleryAutoPlaySec`（连播，纯判据 `GalleryViewerPolicies.kt:29-30` 已在共享面）、`galleryAnimated`（桌面已钉 ALWAYS 档，改这一枚要动那条登记）、`galleryBackdrop` 的 BLACK/DARK_GRAY/WHITE 三档 | P1 |
| 不做 | `galleryKeepScreenOn`（判据本体是 WindowManager flag）、`galleryVolumeKeyTurn`（桌面没有音量键那一问的对象）、`galleryBackdrop` 的 **GLASS** 档（桌面无窗口级实时模糊，`GlassProbe.kt:15-16` 只量到 runtime shader 支持） | — |

⚠️ P3 那三枚接上之后，`DesktopGallerySettingsSpec` 里那 6 枚 `Absent` 会变成 **3 能接 / 3 仍不适用** ——
那笔加法（`4 + 6 + 2 = 12`）要跟着改，判据 ③ 会红一次，那是**该红**。

---

## 8. §6 第 3 条的前提量掉了（2026-10-05，P1 开工前）

那条拍板写的是：「三档取图算式（fast/large/file）搬不搬到共享面 —— 算式现在在
`GalleryPostScreen.kt:1668-1735`，要么搬进 `gallery/domain`（动 Android 侧冻结文件，要豁免一次），
要么桌面另立一颗（两份算式会漂）。**建议搬**」。

P1 开工前去把那颗区间读了，**两半各错一次**：

| 那一区里实际有什么 | 能不能搬 | 桌面要不要 |
|---|---|---|
| `galleryFastRequest(context, url)` —— 地址是**形参**，里面**没有判据** | 没东西可搬 | 不要（桌面 `AsyncImage(model = 地址串)`） |
| `galleryLargeRequest` 里的 `.data(if (isVideo) videoPosterUrl else largeUrl)` + 动/静**分内存键**、视频**分键**那两套 cache key | cache key 那半**搬不动**：`ImageRequest` / `Context` / `CachePolicy` 全是 Android 类型，而 `gallery/domain` 在桌面编译面上 | 不要：桌面没有那套显式键，也就不需要分键 |
| `galleryFileRequest` 的 `.data(post.fileUrl)` + `size(4096)` + `Precision.INEXACT` + `memoryCachePolicy(READ_ONLY)` | 解码上限那半**搬不动**（同上一条理由） | 不要 |

而**真正会漂的那半，早就在共享面上了**：`gallery/domain/GallerySettingsModel.kt:69` 的
`wallUrl(post, quality)` —— 视频永远只吃静帧、`LARGE` 取不到退 `previewUrl`、不给空地址，
三条判据都在那颗函数里，桌面 `tileImageRequest`（`DesktopGalleryWallPolicy.kt:122`）第五批起就在吃它。
Android 墙上那一格也在吃同一颗（`GalleryScreen.kt:1638`）。

⇒ **不搬、不动 `app/`、不开冻结豁免。** 桌面面板要"三档换图"，缺的只有**第三档的地址**，
而那一档是一颗字段（`post.fileUrl`），没有判据可漂。P1 于是改成：
桌面写一颗**转交件**（三档 → 地址），其中两档**逐字转交** `wallUrl`，第三档取 `fileUrl`，
并加一条判据钉住"视频只吃静帧"这一族不许在桌面重述。
这条拍板当初担心的"两端选到不同档的图"，实际防线一直在 `wallUrl` 那一颗，不在这次没做成的搬迁里。
