# 备份范围扩展 · 漫画源账号（2026-10-04）

## 1. 本轮起因与现状核对

用户提出「优化导出数据功能，想让他包括漫画源账号设置」。回代码核对后的事实：

| 数据项 | 落点 | 本轮前是否在备份里 |
| --- | --- | :---: |
| 阅读历史 / 收藏 / 统计 / 屏蔽规则 / 插图收藏 / 画廊两块 | 见 `BackupManager` 那张表 | ✅ |
| **漫画源账号数据档**（`account` / `token` / `_cookies` / `_localStorage`） | `filesDir/comic_source/<key>.data` | ❌ |
| **源内设置**（`settings`，`saveSetting` 写的） | 同一份 `.data` 档 | ❌ |
| 源的启用 / 置顶 / 展示名 | `venera_sources` prefs 里那份元数据 | ❌ |
| 源脚本本身（`comic_source/<name>.js`） | 同目录 | ❌（**本轮也刻意不进**） |
| 全局 Cookie 罐（`venera_cookies`） | `PersistentCookieJar` | ❌（**本轮也不进**） |

顺带核实的一件事：`BackupManager` 的类注释与 README 此前都把「已装的漫画源」和「Cookie 登录态」并列写成不含项，但**没有区分**「源脚本（可重装）」与「账号数据（重装换不回来）」——
后者才是用户真正丢不起的那一份。本轮把这两件事在文档与实现里分开。

## 2. 本轮拍板（用户 2026-10-04 选定）

| # | 决定 | 取舍 |
| --- | --- | --- |
| 1 | **只带账号与设置，不带源脚本** | 脚本是可从仓库重装的可再生物；把它混进"账号"那一栏，会让"恢复登录态"被"要不要覆盖本机的源"这个无关问题拖住 |
| 2 | 导入**增量合并**：本机没装的源跳过并计数，装了的只覆盖归档里带了的键 | 与本仓收藏/历史那两路同一口径；整份 `.data` 覆盖会把本机上更新的字段抹回旧值 |
| 3 | 导出粒度**由用户在设置页单独选**，默认**不导出** | 包里含账密与 token，而归档是一份用户会随手分享出去的文件 |
| 4 | 全局 Cookie 罐**不进包** | 用户未选；且它会把所有站点凭据（含过盾态）写进同一个可分享的文件，来源比源账号那一栏杂得多 |

## 3. 归档格式

- 新增一个**可选** ZIP member `source_accounts.json`，是一个 JSONArray。
- `meta.json` 追加一个计数 `sourceAccountCount`（**追加在末尾** —— 中间插键会让 `CoreTableBackupTest` 那批逐字节锚点失真）。
- `version` **不动，仍是 5**。理由与上一轮同一条：这一栏是"有就认、没有就跳过"，不参与任何判据；版本号的用途只是"这份包是什么形状的记录"。升版本号会让旧版应用把新包判成"不认识"，而它本来就能安全地忽略这一栏。
- 一行的形状（键序固定）：

  ```json
  {"fileName":"ehentai.js","key":"ehentai","name":"E-Hentai","version":"1.4.2",
   "enabled":true,"pinned":false,"data":{"account":"ok","settings":{"domain":"exhentai.org"}}}
  ```

  - `fileName` 是**身份标识**（与 `InstalledSourceMeta` 同一口径）：`copy_manga.js` 与 `copy_manga_multi_accounts.js` 声明同一个 `key`，只有文件名分得开。
  - `data` **原样内嵌**成一个对象，不转字符串 —— 转字符串会让整份归档在文本编辑器里不可读，也让"少带了一个键"在肉眼层面藏起来。
  - 编码/解码全在 `sync/SourceAccountBackup`（纯 JVM，不碰磁盘与 Context），理由与 `GalleryBackupRows` / `FavoriteBackupRows` 同一条。

## 4. 四条实现上的硬口径

### 4.1 「没勾选」= 归档里**没有**这个 member，而不是一个空数组

这一条是安全判据而不是省字节。写一个空数组进去，导入侧就再也分不清：

- "这次导出没勾选账号" —— 不该动本机任何登录态；
- "勾选了，但当时一个源都没登录" —— 同样不该动。

两种含义不同的状态共用一个表示，日后任何一次误判都会静默改掉用户的登录态。所以 `exportSourceAccounts` 在 `NONE` 档**返回 null，整个 member 不写**。

### 4.2 「只导出账号」按**顶层键名**过滤，`_localStorage` 算账号

`.data` 档里账号类与设置类混在同一个 JSON 对象里。过滤判据是键名而不是源：源脚本五花八门，只有这组键名是引擎约定的。

`_localStorage` **算账号**：网页登录抓下来的 `accessToken` 就存在那里（`ccc.js` 取它解析 JWT）。不算进去的话，那类源会出现"登录成功了但 token 没带过来"，恢复后仍是未登录，而没有任何异常指过来。

### 4.3 恢复必须走 `JsSourceDataStore` 那一层，不能直接 `writeText`

`account` 是 `isLogged()` 读的东西，而 `JsSourceDataStore` 有内存 `cache`。写盘不换内存的话，「导入报成功」与「源里还是没登录」会同时成立，要等下次冷启动才看见 —— 与 `BackupManager` 顶部记的那次收藏事故（写进一张没人读的表）是同一类根因。

读的那一侧则相反：`sourceDataFileText` **刻意读文件**而不是问 `dataStore` 的缓存，导出要的是"此刻盘上是什么"这一份快照。

### 4.4 数值收成 `Double`，与 `JsSourceDataStore` 的 plain gson 对齐

`decodeSourceData` 把 JSON 数字收成 `Double` 而不是 gson 的 `LazilyParsedNumber`。理由是那份 Map 的唯一归宿就是 `JsSourceDataStore` 里的 **plain gson**（`gson.fromJson(text, Map)` / `gson.toJson(map)`），而 plain gson 解数字给的就是 `Double`。

留一个 `LazilyParsedNumber` 在 Map 里，两边类型不一致 —— 一边 `Double`、一边 `LazilyParsedNumber`，`==` 判 `false` 而没有任何异常。这条是被单元测试**逼出来**的：最初写的是 `asNumber`，往返用例立刻红了。

## 5. 真机第一次导出就崩了：`baozi.data: open failed: ENOENT`

首版在真机上点「导出数据」直接弹红字：

```
导出失败：/data/user/0/com.github.wang.../files/comic_source/baozi.data: open failed: ENOENT
```

### 根因：档名规则被抄了两份，其中一份是错的

`JsSourceDataStore` 的档名规则是 **`<源 key>.data`**，而 key 来自源脚本里的 `key = "..."` 声明，**与脚本文件名经常不同**。首版在备份那一栏自己又写了一遍规则，按「脚本文件名去扩展名」猜：

| 脚本 | 声明的 key | 首版猜的档名 | 实际档名 |
| --- | --- | --- | --- |
| `baozi.js` | `baozi` | `baozi.data` | `baozi.data` ✓ |
| `komiic.js` | `Komiic` | `komiic.data` | `Komiic.data` ✗ |
| `copy_manga_multi_accounts.js` | `copy_manga` | `copy_manga_multi_accounts.data` | `copy_manga.data` ✗ |

更致命的是那条路径在档不存在时**回落成一个同样不存在的 `File`**，调用方紧接着 `readText()` 就抛。而 `baozi.js` 这类"装了但从没存过数据"的源**必然没有档** —— 于是只要装了任意一个这种源，**整个导出都会失败**，与用户有没有勾选账号无关（勾不勾都要走这一栏）。实测两台设备上都命中：模拟器上是 `baozi.js`，真机上是 `nhentai.js`。

### 修法：规则收成唯一事实源 + 单源失败不拖垮整包

1. **档名规则抽成 `engine/JsSourceDataStore.kt` 的顶层函数 `sourceDataFileName(sourceKey)`**，三个写入点（`getSourceMap` / `persist` / `evict`）与备份那一栏全部走它。谁再改档名，四处一起变。
2. **`sourceDataFileText(key)` 交回 `null` 而不是一个不存在的 `File`**，"没存过"与"读不出来"都收敛成"这一栏没有数据"，由 `decodeSourceData(null)` 给空对象。
3. **备份那一栏逐源 `runCatching`**：单个源的档坏掉/权限异常/内容非法只跳过它并记一条 warn，**不让整包导出失败**。这一栏是附属物，历史/收藏/统计那九栏才是主体 —— 一个源的账号读不出来不该让用户"什么都导不出来"。

顺带把接口从 `sourceDataFileText(fileName)` 改成 `sourceDataFileText(key)`：`fileName` 这个参数本身就是那个错误的抽象（它诱导实现去按文件名猜），而档名只由 key 决定。

## 6. 参数没给默认值的两处（沿用既有口径）

`BackupSummary` 新增 `sourceAccountCount` / `sourceAccountSkipped`，两个都**刻意不给默认值**。沿用 `guardRulesSkipped` / `imageFavoriteCount` 那条判据：漏传不报错的新参数，下一笔恢复就会静默报成 0。

`sourceAccountSkipped` 与 `BackupTransfers.describeResult` 的关系同 `foreignSkipped`：因本机未装该源而没恢复的条数必须报到屏上，否则用户只会以为备份是坏的。

## 7. 外部归档那条路不动

`ForeignArchiveImport`（官方 Venera / PicaComic 归档）的 `toSummary()` 把两个新计数显式传 0。两边的凭据存储形状不同（官方是 `cookie.db` / `cookies.db`，本仓是 `<key>.data`），且跨应用搬凭据本来就不该做 —— 这不是"没做映射"。

## 8. 验证（2026-10-04 实跑）

- `:app:compileDebugKotlin` 通过；`:app:testDebugUnitTest` + `:desktop:test` 全量 **2257 条 / 0 失败 / 0 错误**（275 个测试文件，`--rerun-tasks` 强跑）；`:app:assembleDebug` **BUILD SUCCESSFUL**。
- 新增判据测试 `SourceAccountBackupTest` **13 条**：键序锚点、`data` 内嵌而非字符串、空 data 写 `{}` 而不是省键、只导账号时设置被摘掉而四个账号键一个不少、全量导出时一个键都不动、`_localStorage` 算账号、数字按 plain gson 收成 `Double`、坏档交空对象而不抛、缺 member 与坏 member 是两件事、缺 `fileName` 的行跳过、缺状态键默认**已启用**、编码再解码逐字段一致、写回档的文本形状 + 两个字面量（member 名与 meta 键）稳定。
- `BackupTransfersResultTextTest` 由 6 条增至 **9 条**：新增"恢复了几个源的账号必出""因本机未装该源而跳过的账号要说得出""账号跳过与屏蔽规则跳过互不顶掉"，并在 `零值不占版面` 一条里补上「漫画源账号」。
- 新增 `engine/SourceDataFileNameTest` **4 条**，专钉那条崩过的规则：档名由 key 决定、`komiic.js` 的 key 是大写 `Komiic`、两个脚本共用一个 key 时共用同一份档、key 里的路径分隔符被剥掉（不让第三方脚本把档写到数据目录之外）。
- **真机实测（OnePlus PJZ110 / arm64-v8a）**：装上修复版后，把「导出内容 · 漫画源账号」设为**登录状态 + 源内设置**再导出 —— 不再报错，落盘 `venera_backup_20261012_232157.bin` **25532 字节**。解包核对：
  - 10 个成员齐全，`meta.json` 的 `sourceAccountCount` = **4**；
  - `source_accounts.json` 里 4 个源（`ehentai` / `jm` / `picacg` / `nhentai`）各一行，`jm` 与 `picacg` 的 `account` 与 `token` 都在；
  - **`nhentai.js` 没有 `.data` 档**，如实出一行空 `data` 而不是让导出失败 —— 这正是崩过的那一类。
  - 崩溃前的对照：同一台机器上失败时留下的是 **0 字节**的 `venera_backup_20261012_225810.bin`。
- **仍未验**：导入后源页的登录态是否**不经重启**当帧刷新（`restoreSourceData` 走的是 `saveData`，理论上换了内存缓存，但源页读的是 `refreshConfigBundles` 那条路）；换机导入后依赖 `_localStorage` 的那几个源实际能不能用。

## 9. 遗留

- **导出这一栏没有"仅导出选中的源"的细粒度**：今天是全量（所有已装源）或只导账号两档。源特别多而只想搬其中一两个时，多出来的登录态是静默的额外暴露面。
- **凭据是明文的**：本仓归档没有任何加密层，本轮也没加。"默认不导出"是当前唯一的防线；用户勾选后那份文件跟一份导出的密码表等价。
- **`sourceDataFileName` 只剥路径分隔符，不做别的净化**：key 是第三方脚本给的字符串，理论上可以含 `..`、空格、非 ASCII 之类。当前只保证"不会写到数据目录之外"，落进数据目录内的怪名字不影响功能（档名只用于读写，不参与展示）。
