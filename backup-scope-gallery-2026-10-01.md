# 备份范围扩展 · 画廊与插图收藏（2026-10-01）

## 1. 本轮起因与现状核对

用户提出「看下现在的备份功能，加上画廊关注画师、收藏页面、阅读统计」。逐项回代码核对后的事实：

| 数据项 | 落点 | 本轮前是否在备份里 |
| --- | --- | :---: |
| 阅读历史 + 已读进度 | `venera_core.db` · `comic_history` | ✅ history.json |
| 漫画收藏（条目 / 收藏夹 / 顺序 / 网络夹绑定） | `local_favorite.db` | ✅ favorites.json + favorite_folders.json |
| **阅读统计** | `venera_core.db` · `reading_stats` | ✅ **stats.json，已经在备份里** |
| 屏蔽规则 | `content_guard_rules` | ✅ guard_rules.json |
| 画廊关注画师 | `filesDir/gallery_artist_follows.json` | ❌ |
| 画廊收藏（收藏页「画廊」那一栏） | `filesDir/gallery_favorites.json` | ❌ |
| 插图收藏（漫画单页收藏） | `favorite_images` 表 | ❌（当初刻意排除） |

三条待补的、以及一条"以为缺其实不缺"的，都在上表。**「阅读统计」不需要动**：`StatsScreen` 与首页摘要卡的每一个读数（总时长、累计页数、Top 10、近 14 天、连续天数、题材分布）都是从 `reading_stats` 现算的（`stats/ReadingStatsManager.kt:77,136,171,223,332`），归档把这张表整表带走，统计页就整页复现。

顺带发现一处**说明与实际不符**：`BackupManager.kt` 的类注释里写"排除插图收藏的理由是库里只登记了本地路径"，但 `favorite_images` 建表时就有 `image_url NOT NULL`（`VeneraDatabase.kt:106`）。它真正带不走的东西是 `local_path` 指向的那份**去混淆后的位图副本**，注释在本轮一并改正。

## 2. 本轮拍板（用户 2026-10-01 选定）

| # | 决定 | 取舍 |
| --- | --- | --- |
| 1 | 画廊**关注名单 + 画廊收藏**都进备份 | 收藏那条连地址、尺寸一起带走（存的就是图本身的地址，不是站方签发的临时签名），换机后收藏页能离线铺出来 |
| 2 | 画师**头像地址档**（`gallery_artist_avatars.json`）**不进** | 它是可随时重取的解析结果、不是用户攒下的关系，且当初就刻意与名单分档；代价是新设备第一次打开关注面板要重发那 2~4 笔 JSON |
| 3 | 插图收藏**进元数据 + URL，图片文件不带** | 恢复后按 `image_url` 现加载：禁漫那批画质受降采样影响、EH 那批临时签名几天就 403、离线时是裂图。不带图的理由是备份包体积会随收藏张数线性膨胀（每页是整页原图），WebDAV 上传跟着垮 |

明确不进、且不是遗漏的：画师头像地址档、画廊 Feed 缓存与标签词典 SQLite（两者都可再生）、已装漫画源、偏好设置、Cookie 登录态、下载任务与下载下来的图片。

## 3. 归档格式

- 新增三个 ZIP member：`gallery_follows.json`、`gallery_favorites.json`、`image_favorites.json`，各自是一个 JSONArray。
- `meta.json.version` 由 **4 → 5**，并在 meta 里补三个计数（`galleryFollowCount` / `galleryFavoriteCount` / `imageFavoriteCount`），导出后不看库也能知道这份包吃了什么。
- **向后兼容**：导入侧对缺失的 member 取空数组，v4 及更早的包照样全量恢复（只是不含画廊）；反向（旧版应用读 v5 包）也不会炸，因为它根本不查这三个 member。所以版本号的用途只是"这份包是什么形状的记录"，不参与判据。
- 字段口径：
  - `gallery_follows.json` / `gallery_favorites.json` 的字段名**与 filesDir 上那两份磁盘档逐字一致** —— 归档直接复用 store 里那个 `@Serializable` 类做编解码，不另立一套列名。理由：22 个字段的映射表手写一遍就会漂移，而漂移的表现是"恢复出来的卡少一块信息"，没有任何异常会指过来。
  - `image_favorites.json` 走列名直译（`SELECT comic_id, comic_title, source_name, chapter_title, page_index, image_url, created_at`），**故意不选 `id` 与 `local_path`**：前者是设备本地自增主键，后者换台机器就指向一个不存在的文件，而 UI 的取图口径是 `localPath.ifBlank { imageUrl }`（`FavoriteImagesScreen.kt:288,462`）—— 带上它等于把"能按 URL 加载"这条活路堵死。落库时 `local_path` 写空串。

## 4. 一条正确性硬约束：必须走各自的单例

本轮三块数据里有两块**不是 SQLite**，而是 `GalleryFavoritesStore` / `GalleryArtistFollowsStore` 的内存 `StateFlow` + 一份 JSON 文件。写盘若绕过 store 直接改文件，屏上读的是内存那份，恢复结果就是"导入报成功、画廊还是空的"——与 `BackupManager` 顶部记录的收藏那次事故（写了一张没人读的表）同一条根因：**恢复要写进运行期真正被读的那个事实源，并且让持有它的单例知道值变了**。

所以三个 store/manager 各加一个恢复入口，落盘 + 更新内存 + 返回**真正新增**的条数：

- `GalleryArtistFollowsStore.restore(incoming)` —— 合并去重沿用 `GalleryArtistFollows.dedupe/sorted`（同一条留**较早**那次关注，不重掷时间戳）。
- `GalleryFavoritesStore.restore(incoming)` —— 按 `uid` 合并，本机已有的不覆盖（保留本机那次 `saved_at`）；站点键认不出的行当场摘掉，与 `readFromDisk()` 同一条判据。
- `FavoriteImagesManager.restoreBackupRows(rows)` —— 按 `image_url` 去重（`isFavorited` 用的就是这个键），`local_path` 落空串，`created_at` 用归档里的原值。导出侧配 `exportBackupRows()`，用显式七列而不是 `SELECT *`。

落盘失败的口径在这轮收紧（只收紧恢复这条路，普通收藏/关注保持旧行为）：

- 画廊两个 store 的 `persist` 原本把失败包在 `runCatching` 里丢掉。普通点一颗心失败只是下次冷启动要重新点，无所谓；一次导入几十条失败是实打实的损失。所以 `restore()` 改成**先落盘、写成了才换内存那份列表**，落不进盘就抛 `IOException` —— 不留「屏上当帧就有了、重启就没」的半截状态，也不留"报成功但只在内存里"的假成功。
- `SQLiteDatabase.insert` 失败是**返回 -1 而不是抛异常**（只打日志），所以插图收藏那一路显式判 `< 0` 并在事务里抛出 = 整笔不提交。

## 5. 结果文案与页面口径要跟着改

恢复了哪些新东西必须说得出，否则用户没法判断这份包吃没吃进去：

- `BackupSummary` 加三个计数，**刻意不给默认值**（沿用 `guardRulesSkipped` 那条：漏传不报错的参数，下一笔恢复就静默变成 0）。
- `BackupTransfers.describeResult` 在对应条数 > 0 时才拼进文案（零值不占版面这条已有判据）。
- `SyncBackupScreen` 里 WebDAV 恢复成功那句原本硬编码"历史 X 条，收藏 Y 部"，与设置页各写一套 —— 统一改走 `describeResult`，否则新增的两栏永远只在一个入口出现。
- `AppSettings` 导出/导入两条 summary 文案、`SyncBackupScreen` 归档卡副标题、`BackupManager` 类注释的"不含"清单、README 的「备份与同步」与「带条件的能力」两节。

## 6. 外部归档那条路不动

`ForeignArchiveImport`（官方 Venera / PicaComic 归档）只认漫画侧的收藏与历史，画廊是本分支独有的模块，上游没有任何对应物，所以外部归档这一路**结构上不可能**带画廊数据。它的 `toSummary()` 只是把三个新计数显式传 0。

## 7. 验证（2026-10-01 实跑）

- `:app:compileDebugKotlin` 通过；`:app:testDebugUnitTest` 全量 **749 条 / 0 失败 / 0 错误**（92 个测试文件）；`:app:assembleDebug` **BUILD SUCCESSFUL**（38 tasks，3 executed / 35 up-to-date）。
- 新增判据测试：`GalleryBackupRowsTest` 9 条（往返、站点键认错、`id=0` 摘除、缺档 vs 坏档、少字段按默认值补齐）、`ImageFavoriteBackupRowsTest` 5 条（导出不含 `id`/`local_path`、无地址不收、数字类型收拢、时刻缺失退当前时间）、`BackupTransfersResultTextTest` 增 3 条（三个新计数非零必出、零值不占版面、插图那句"图不带"）。
- 两个新测试**没有**登记进 `_probe/l0/run-judgment-tests.sh`：`GalleryFavorite` 与 `GalleryFavoritesStore` 同文件，而那个文件 import `android.content.Context`，纯 JVM 单跑编不过。判据层里能单跑的那一半（`GalleryArtistFollows` 的合并去重）本来就已登记。
- **真机未验**：导入后关注面板与画廊收藏页是否**不经重启**当帧补齐；恢复出来的插图收藏那批按 `image_url` 能不能真显示出图（这是本轮已知的最可能失望点，尤其在禁漫与 EH 两路上）。

## 8. 顺带修正与遗留

- `BackupSummary.folderCount` 在**本仓自有归档**这条恢复路径上一直没传（走默认值 0），所以设置页从来没报过"收藏夹 N 个" —— 本轮补传。外部归档那条一直有传。
- `SyncBackupScreen` 云端恢复成功的 Toast 原本硬编码"历史 + 收藏"两项，与设置页各写一套；本轮统一走 `describeResult`。
- 遗留一个没有证据的问题（**不在本轮范围，需要实测再定**）：官方 Venera 归档里的 `image_favorites` 表仍不导入，理由写的是"形状对不上"。本仓自己那张 `favorite_images` 现在能按地址导入了，官方那份是否也有可用的地址列、能不能同样映射，需要拿到一份真机导出的官方归档实测后再判，不凭表名推断。
