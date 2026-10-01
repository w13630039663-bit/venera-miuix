# 本地漫画存储目录自选 + 导入导出位置自选（2026-09-22）

需求（用户原话拆解）：
1. 设置 › 应用 › **应用与数据** 里的「本地漫画存储路径」改成**用户自己选择的目录**（当前是只读展示 + 点击复制）。
2. 「导入数据 / 导出数据」也改成**用户自己选择的位置**（当前两行只是跳到云备份页）。
3. **下载管理**必须跟着同一个存储路径走，不能各写各的。

## 已定机制（用户逐条点选）

- 选目录机制：**系统目录选择器（`ACTION_OPEN_DOCUMENT_TREE`）+ 真实路径直写 + 「所有文件访问」授权**。
  换算链：tree uri 的 documentId（`primary:Download/漫画` / `<uuid>:/path` / `raw:/storage/...`）→ 真实
  `File` 路径（`Environment.getExternalStorageDirectory()` 或 `StorageManager.storageVolumes` 按 uuid 匹配）。
  **判定可写不信权限位，而是真写一个探针文件**：`probeWritable()` 建目录 → 写 `.venera_write_probe` → 读回 → 删除。
  探针失败即**不切换**，弹「需要所有文件访问权限」引导（`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`，
  data=`package:`；该 intent 抛异常时退化到 `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION` 总列表页）。
- 已下载内容处理：**切换时问** —— 弹框显示旧目录里的漫画数/章节数，两个按钮「移动到所选目录」/「仅对新下载生效」。

### 为什么不走纯 SAF（用户选项里被否的那条）

`ACTION_OPEN_DOCUMENT_TREE` 拿到的持久授权**只能通过 `ContentResolver`/`DocumentFile` 落地**，
`java.io.File` 在 Android 11+ 分区存储下对公共目录一律不可写。而本项目的下载链全是 `File` 语义：
断点续下要 `length()` + 文件头魔数校验、`.tmp` → `renameTo` 原子落地、`listFiles` 递归扫描、
CBZ 走 `ZipFile(File)`。改成纯 SAF 要把 DownloadManager + LocalComicManager 整层抽象成两套实现，
且大库扫描改走 provider 查询会明显变慢 —— 改动面和回归风险最大，且界面上只能显示目录名不能显示真实路径。
故取「真实路径 + 一次系统授权」。`MANAGE_EXTERNAL_STORAGE` 现状仍受支持、官方文档无弃用说明，
它明确允许对公共目录使用标准文件系统调用；代价是 Play 政策门槛（**侧载不受影响**，本包走 releases 直发 APK）。

## 实施清单（本轮已落地）

| # | 改动 | 文件 |
|---|---|---|
| 1 | 存储根单一事实源：`resolve/defaultDir/tasksFile/ensureRoot/probeWritable/resolveTreePath/rejectReason/counts/migrate` | `download/ComicStorageRoot.kt`（新增） |
| 2 | 偏好键 `comicStoragePath`（空串=内置），默认值保持历史位置 | `data/prefs/VeneraPreferences.kt` |
| 3 | 下载根改读 `resolve()`（每次现读，不必重启）；**任务清单固定留内置目录**；新增 `relocateTasks(old,new)` 重写 `directoryPath` 前缀 | `download/DownloadManager.kt` |
| 4 | 本地书架扫描根与 CBZ 导入落点改读 `resolve()`，导入前 `ensureRoot` 注入 `.nomedia` | `download/LocalComicManager.kt` |
| 5 | 路径行 → 选目录全流程（换算 / 守卫 / 探针 / 权限引导 / 迁移确认 / 改回内置 / 回前台重测 / 保留复制路径） | `feature/settings/AppSettings.kt` |
| 6 | 导入导出共用搬运链：`exportBackupTo(uri)` / `importBackupFrom(uri)` / `suggestedFileName()` | `sync/BackupTransfers.kt`（新增） |
| 7 | 导出=系统「保存为」(`CreateDocument("application/octet-stream")`)，导入=`OpenDocument`，两行不再只是跳转；云备份页导入改调同一 helper | `feature/settings/AppSettings.kt`、`feature/SyncBackupScreen.kt` |
| 8 | `MANAGE_EXTERNAL_STORAGE` 声明（带"为什么需要、何时才引导"的注释） | `AndroidManifest.xml` |
| 9 | 新增「存储目录自选与导入导出位置自选」章节 + 修正上一节关于导出落点的表述 | `FREEZE-STATEMENT.md` |

### 实施中定下的三个细节（不显然，别再改回去）

- **落盘存的是 `absolutePath` 而不是 `canonicalPath`**：绑定挂载机型上 `/storage/emulated/0` 规范化后可能变成用户认不出来的 `/mnt/...`。
  准入判定里两种形态都比一遍，避免"存 raw、判 canonical"造成的误拒。
- **`CreateDocument` 的 MIME 用 `application/octet-stream` 而不是 `application/zip`**：部分 DocumentsUI 会按 MIME 给文件名补 `.zip`，把 `.venera` 改掉。
- **点击链路全在协程里**：`counts()` 要递归数目录、`probeWritable()` 要写盘，都不能在组合期或点击回调线程上直接跑。

## 硬约束与刻意的取舍（不做静默降级）

- **换根不换任务清单**：`download_tasks.json` 恒在 `filesDir/downloads/`。否则一次改目录就让进行中的队列整体失联。
- **目录守卫 `rejectReason()` 显式拒绝**：整块存储根（`.nomedia` 会糊满全盘、根被误删风险）、`Android/` 及其下
  任何位置（系统保护区，SAF 也给不了）。拒绝理由是 Toast 原文给用户，不静默改路径。
- **探针失败绝不切换**，也绝不「退回内置目录继续下」——那是把用户以为存到自己目录的数据偷偷写到别处。
- **所选根不可写时不自动回落**：路径行如实显示「不可写：所有文件访问权限未授予或已被撤销」，下载会真的失败并报错。
- **「仅对新下载生效」的代价讲明白**：旧目录内容不再出现在本地书架，`isChapterDownloaded` 也转 false，
  再点下载会重新拉取。弹框里就是这句原文。
- **tree uri 换算不出真实路径时**（OEM 私有 provider、`MediaProvider` 之外的虚拟根）如实报错并说明原因，
  不退化成「写不进也先存 uri」的假开关。
- 迁移用 `renameTo`，跨分区降级 `copyRecursively` 后**逐源目录**删源；任一目录失败即停止并抛错，
  绝不留「两边各有一半」还报成功。

## 本轮主动不做

- 本地漫画页的 **CBZ 导出**（仍走 cacheDir + 系统分享面板）：不在本次需求里，要改成自选位置可下轮单独提。
- 下载管理页顶部**显示当前存储路径**：`目录`信息已在设置页路径行如实显示，避免两处文案漂移。
- 备份文件内容范围不变（仍 history / favorite / stats / guard_rules 四张表）——见 09-22 已记录的既有缺陷，
  偏好、已装漫画源、下载队列本来就不在包内，本轮不扩。
- 不做「多根并扫」：用户选的是「切换时问我」，双根扫描会把删除/已下载判定复杂化。

## 尚未证实（必须真机，设备未连）

1. ColorOS 的目录选择器能否选到目标公共目录；`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` 带
   `package:` data 在 ColorOS 上是否直接落到本应用开关（该 intent 部分定制 ROM 会抛异常，已写退化分支但**未实测**）。
2. 外置 SD 卡（`<uuid>:` 卷）换算路径 + 探针是否通过（SAF 对 SD 卡的 docId 各家实现差异大）。
3. 迁移在 12+ 部 / 上百章规模下的耗时与中断行为（copyRecursively 无进度反馈，本轮只保证「失败不静默」）。
4. 导出经 `CreateDocument` 在 ColorOS 面板里能否新建文件夹（这是「自选目录」体验的一半）。
