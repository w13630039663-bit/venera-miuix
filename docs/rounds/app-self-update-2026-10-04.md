# 2026-10-04 应用内自更新（检测 → 日志弹窗 → 下载 APK 带进度 → 拉起安装 → 通知）

用户一句话给了五件事：内置更新检测、检测到弹窗、写清楚更新日志、点击更新后自动下载 APK 带进度条、下载好跳转安装并接入通知。

目标端沿上一条 standing 决策 = **只 Android 手机（`:app`）**，桌面并行线不动。
方案在 chat 里分三段呈过并获批（"可以，按你自行推荐的做就行"），本文既是方案也是账。
按 architectural 档走：批 1 纯函数与用例先行 → 批 2 下载与校验 → 批 3 唤起安装 → 批 4 接 UI 与回写。

---

## 0. 实测读数（动代码之前先量，六条都是后来拍板的依据）

### 0.1 这条流**已经存在一半**

| 已有 | 出处 |
|---|---|
| 发布通道常量（slug / releases / `releases/latest` API） | `data/update/AppUpdateChecker.kt:19-24` |
| 逐段数字比较（容 `v` 前缀、`-miuix` 后缀、`1.7` vs `1.7.0` 段数不齐） | `AppUpdateChecker.kt:89-111` |
| 启动检查 24 小时节流 + 开关（默认 false，对齐 master `appdata.dart:238`） | `AppUpdateChecker.kt:52-54`、`VeneraPreferences.kt:429-446` |
| 「发现新版本」弹窗 + 手动检查 + 启动检查宿主 | `feature/settings/UpdateCheckUi.kt`、`AboutSection.kt:108-121`、`MainActivity.kt:40` |

所以用户要的**「检测 + 弹窗」其实在了**，缺的是后三段。

### 0.2 缺的三段，逐条判式（负结论也给判式）

- `REQUEST_INSTALL_PACKAGES`：**零声明**。判式 `grep -rni REQUEST_INSTALL_PACKAGES app desktop --include=*.xml --include=*.kt` → 0 命中。
- `res/xml/file_paths.xml`：只有三条 `cache-path`（`shared_images/ exports/ backups/`），**没有下载落点**。
- `android.app.DownloadManager`：**零使用**。判式 `grep -rn "android\.app\.DownloadManager" app/src desktop engine-probe --include=*.kt --include=*.kts` → exit=1。
- **无前台服务**：Manifest 无 `<service>`，`grep -iE "FOREGROUND_SERVICE|<service"` → 0。
- `POST_NOTIFICATIONS` 有声明（`AndroidManifest.xml:9`）但**全仓零运行时申请**，现状是 `DownloadManager.kt:99-105` 拿 `checkSelfPermission` 静默跳过。

### 0.3 `UpdateCheckUi.kt:27-28` 那句「不在应用内自装」的前提**已过期**

原文由是：「本包与 master 分支（Flutter 版）用同一个 applicationId、不同签名，应用内覆盖安装只会得到一句"应用未安装"」。
v2.0.3 的发布说明自己写着（本机 `api.github.com` 实拉）：

> **可直接覆盖安装** v2.0 / v2.0.2：同一把正式签名证书，`versionCode` 2002 → 2003，无需卸载，收藏、历史、下载、画廊数据都在。
> 本版四份产物的签名证书 SHA-256 均为 `62b89c491bd6fca41beb25f0baafc80cfad865397302d37694df1fda6a69846b`

即：**稳态（本包的 release → 本包的 release）覆盖安装是成立的**，那句注释管的是两种一次性场景 ——
① 从 Flutter 原版迁过来；② 自己 clone 构建的包（`app/build.gradle.kts:39-73`：缺 `key.properties` 时**回退 debug keystore**，并 `logger.warn` 明写「产物可侧载验证，不可用于分发」）。
这两种情形系统安装器只会回一句「应用未安装」，所以本批改做的一件事是**把它变成有声的提示**（批 3 的签名预检），
而不是拿那句过期注释继续当"做不到"的依据。

### 0.4 一次 `releases/latest` 就带着我要的全部东西（不必加第二次请求）

```
tag=v2.0.3-miuix  published=2026-10-03T19:41:02Z  body=3070 字符
assets(5):
  output-metadata.json                    1934 B  application/octet-stream
  venera-miuix-2.0.3-arm64-v8a.apk    14430084 B  application/vnd.android.package-archive  dl=9
  venera-miuix-2.0.3-armeabi-v7a.apk  14427242 B  …  dl=1
  venera-miuix-2.0.3-x86_64.apk       14430745 B  …  dl=0
  venera-miuix-2.0.3.apk              14480157 B  …  dl=3      ← universal
asset 字段表含 **digest**：`sha256:e6f6ecb5cb4f1d8a12123d1b3b9e88c52317677405590a5f5927f063d01696b4`
release 字段表含 body / tag_name / assets / published_at
```

`digest` 这一条决定了整件事的安全形状：**镜像下载可被检出**。

### 0.5 加速镜像可达性实测（HEAD，`--max-time 12 -L`）

| 前缀 | 读数 |
|---|---|
| `https://gh-proxy.com/` | http=200 type=`application/vnd.android.package-archive` time=1.19s |
| `https://ghfast.top/` | http=200 同上 time=1.09s |
| `https://ghproxy.net/` | http=200 同上 time=1.69s |
| `https://mirror.ghproxy.com/` | **http=000**（连不上）→ **不进内置表** |
| `https://github.com/`（官方） | http=200 time=0.87s |

本机直连 GitHub 通，**但设备侧不是本机**，所以回退链照建；`type` 与 `Content-Length` 都可能被镜像说谎，
所以真判据是落盘后的 sha256（见 1.2 硬规则 2）。

### 0.6 本功能要碰的八处既有文件**都不在冻结名单**

`FREEZE-STATEMENT.md:8-15` 的 FROZEN 表是 8 颗：UnifiedExploreScreen / SourceSectionScreen / SearchScreen /
ContentGuardManager / HistoryScreen / FavoritesScreen / NetworkFavoritesScreen / HomeScreen。
本批要动的 `AppUpdateChecker.kt`、`UpdateCheckUi.kt`、`AboutSection.kt`、`PreferencesApi.kt`、`VeneraPreferences.kt`、
`AndroidManifest.xml`、`file_paths.xml`、`CacheDirs.kt` **无一在表内 ⇒ 不需要冻结豁免**。

### 0.7 现成可复用件（不造新数字、不起第二套）

| 要用的 | 现成出处 |
|---|---|
| 通知渠道（复用不新建） | `download/DownloadManager.kt:83-96`，id `venera_download`，名「离线下载」，`IMPORTANCE_LOW`，`setShowBadge(false)` |
| 进度通知形状 | `DownloadManager.kt:108-127`：`setOngoing(true)` + `setProgress(total, done, false)` |
| 完成态通知形状 | `DownloadManager.kt:130-149`：换图标 + `setAutoCancel(true)` |
| 确定态进度条 | `feature/DownloadScreen.kt:295-300`：`LinearProgressIndicator(progress = { progress })` |
| 弹窗件（双后端） | `components/venera/VeneraControls.kt:427` `VeneraDialog`，miuix 侧 `WindowDialog` :442 |
| 为什么不能用 `OverlayDialog` | `feature/settings/SettingsComponents.kt:64-67` 原文：设置页那条链没有 miuix Scaffold ⇒「点了没反应的假弹窗」 |
| 长正文滚动限高 | `reader/ChapterCommentsSheet.kt:163` = `heightIn(max = 380.dp)`（**照这个口径，不造新数**） |
| FileProvider authority | `AndroidManifest.xml:212-220` = `${applicationId}.fileprovider` |
| cacheDir 目录名单一出处 | `data/platform/CacheDirs.kt` + 成对性测试 `CacheDirsFileProviderContractTest.kt` |
| 共享 HTTP client（代理/优选 IP 天然生效） | `data/network/VeneraNetworkClient.kt:44-51`（代理 :79-86、优选 IP :92-98） |

**构建口径**：`minSdk 33 / targetSdk 37 / compileSdk 37`（`app/build.gradle.kts:26,33-34`）⇒
API 33+ 通知必须运行时授予，本批在用户点「更新」那一刻申请一次。

---

## 1. 拍板（四条，全部按推荐档）

| # | 决策点 | 拍板 |
|---|---|---|
| 1 | APK 下载源 | **官方直链优先，失败回退镜像 + sha256 校验** |
| 2 | 更新日志呈现 | **极简 Markdown 子集渲染**（不引第三方库） |
| 3 | 通知接入 | **下载进度 + 下载完成点击继续安装**（复用 `venera_download` 渠道） |
| 4 | 「以后再说」记账 | **记住跳过的版本号**，出了更高版本才恢复提醒 |

### 1.1 件划分（六颗，各自单一职责，四颗脱离 gradle 能单跑）

| 件 | 职责 | 接口 | 依赖 |
|---|---|---|---|
| `ReleaseFeed` | `releases/latest` 的 JSON → `LatestRelease` | `parseLatestRelease(json): LatestRelease?` | 纯 |
| `ApkAssetPicker` | 按 ABI 选那一份 APK | `pickApkAsset(assets, supportedAbis): ReleaseAsset?` | 纯 |
| `DownloadCandidates` | 一条官方 + 内置镜像的候选序列 | `candidatesFor(officialUrl, sha256OrNull): List<String>` | 纯 |
| `UpdateChangelog` | Markdown 子集 → 块序列 | `changelogBlocks(md): List<ChangelogBlock>` | 纯 |
| `ApkDownloader` | 流式落盘 + 边写边散列 + 校验 + 状态流 | `StateFlow<UpdateDownload>` | 唯一有 IO |
| `ApkInstallLauncher` | FileProvider URI → 系统安装器 | `launchInstall(context, file): InstallOutcome` | 平台 |

`LatestRelease(tagName, version, changelog, assets: List<ReleaseAsset>)`；
`ReleaseAsset(name, size, sha256: String?, url)` —— 只收 `content_type == application/vnd.android.package-archive`，
`output-metadata.json` 由这道门挡掉（实测它的类型是 `application/octet-stream`）。

### 1.2 两条硬规则（写进注释，未来别被"顺手统一"掉）

1. **元数据永远只从 `api.github.com` 取。** 让镜像参与给版本号或给 digest = 让被校验的一方出具校验凭证，
   那条 sha256 就白写了。下载**字节**可以走镜像，**事实**不行。
2. **digest 缺失 → 候选表塌成"只剩官方"。** 宁可错慢不可静默交错：没有可信哈希时，
   绝不把 14 MB 的可执行包交给第三方域名字节，也不写"看起来能装"的兜底。
   UI 侧要如实说一句「这次没拿到官方校验值，只走官方直链」。

### 1.3 刻意不做的四件事

- **不做前台服务**：要 `FOREGROUND_SERVICE` + 服务类型声明 + MIUI 侧的额外坑。
  代价如实说是：进程被系统回收时下载中断，重进页面可重试；「App 划掉也能接着下」不在本批。
- **不做 Range 断点续传**：14 MB，失败重来比把校验逻辑写复杂更划算（续传会让"边写边喂散列"变成两段拼接）。
- **不做静默/自动安装**：Android 不允许无人确认的安装，装了就是假开关。
- **不引入 `versionCode` 比较**：现有 tag 数字段口径已对齐 master，够用；
  真要拿 `output-metadata.json` 里的 per-split versionCode，得先证明 tag 口径会误判，目前没有证据。

### 1.4 一处命名刻意

新件避开 `Manager|Store|Client|Repository|Preferences|Dao|Breaker|Account|Resolver|Converter` 这些后缀 ——
`BusinessApiBoundaryTest` 的实现类扫描面由那张后缀表导出（`:276`），而白名单**只许缩短不许变长**（`:120`）。
`Checker`（`AppUpdateChecker` 已有的先例）、`Downloader`、`Launcher`、`Feed`、`Picker` 都不在后缀表内。

---

## 2. 批 1：取数与判定层

用例先行（L0，无 Robolectric）：`ReleaseFeed` / `ApkAssetPicker` / `DownloadCandidates` / `UpdateChangelog` /
`ignoredUpdateVersion` 判据 + `isNewer` 既有用例保持。fixture 直接用 0.4 那份**实拉 JSON 的真实形状**，不凭记忆造。

`AppUpdateChecker.check()` 的载荷从 `Available(remoteVersion)` 扩成带 `changelog` 与 `apk` 的形态；
调用点两处（`AboutSection.kt:65`、`UpdateCheckUi.kt:70`）一并接。

## 3. 批 2：下载、校验、通知

落点 `cacheDir/app_update/`（`CacheDirs.APP_UPDATE` 新常量 + `file_paths.xml` 一行 + 成对性测试从三条扩到四条 ——
这条洞的历史形状是"写入永远成功、`getUriForFile` 才抛、而 catch 把它吞了"，见 `file_paths.xml:10-14` 的 G-2）。

`ApkDownloader` 状态机：`Idle → Downloading(done, total, speed) → Verifying → Ready(file) | Failed(reason)`。
- 写 `*.part`，散列相等才 `rename`；任何失败路径**先删 `.part`** 再换下一个候选（否则会拿半截文件去装）。
- 候选耗尽才 `Failed`，且 `reason` 要带**实际读数**（哪个候选、HTTP 码、还是散列不符），不写"下载失败"这种没法行动的话。
- 单例自持 `CoroutineScope(SupervisorJob() + Dispatchers.IO)`（`DownloadManager` 同形状），所以离开设置页仍在跑。
- 下载前清同目录旧包；完成后保留（安装器还要读），下次下载前清。
- 通知：复用 `venera_download`；进度 `setProgress(totalBytes.toInt(), done.toInt(), false)`（这条粒度是**字节**，
  与漫画那条"页/张"粒度不同，所以是各写各的一处 `notify`，不共用那两个函数）；完成态 contentIntent = 安装的 `PendingIntent.getActivity`。

## 4. 批 3：唤起安装

`ACTION_VIEW` + `setDataAndType(uri, "application/vnd.android.package-archive")` + `FLAG_GRANT_READ_URI_PERMISSION`，
URI 走 `${applicationId}.fileprovider`。
- `canRequestPackageInstalls() == false` → 引导 `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`，**不假装成功**。
- 签名预检：`getPackageArchiveInfo(file, GET_SIGNING_CERTIFICATES)` 的证书字节 vs 已安装包证书字节；
  不同 → 明说「这台机器上的包与发布包不是同一把签名，覆盖安装装不上，需先卸载；请先用设置页导出备份」。

## 5. 批 4：弹窗三态与回写

一颗 `VeneraDialog` 三态：Prompt（滚动日志，限高 380.dp）/ Downloading（`LinearProgressIndicator` + `8.4 MB / 13.8 MB · 1.2 MB/s`）/
Ready 与 Failed（Failed 保留「重试」与「去浏览器下载」两条出口）。
「以后再说」→ `prefs.ignoredUpdateVersion = remoteVersion`；`AboutSection` 仅在**有忽略值时**多一行「清除已忽略的版本」。
撤掉 `UpdateCheckUi.kt:27-28` 那句过期注释，换成 0.3 的新裁决与依据。

---

## 6. 验证口径与待真机

- 每批：`./gradlew :app:compileDebugKotlin` → `./gradlew :app:testDebugUnitTest`，**不接管道**取退出码
  （`> log 2>&1; echo "EXIT=$?"`），用例总数用**落盘的 `.js` 脚本**统计（内联 node 里的 `\d` 会退化成恒假正则，
  本轮就差点数出"834 颗"里的用例数是 0）。
- 基线 = 109 类 834 颗 0 失败；本批新增用例数在下面这张表回写。
- **待真机读数**（本环境按分工不代操作设备）：
  1. 弹窗三态在 miuix / MD3 两档下的按钮摆位与日志滚动；
  2. 通知栏进度是否真的在动、完成态点一下能否直接进系统安装页（`PendingIntent` + URI 授权这一步**只有真机能证**）；
  3. 「安装未知应用」未授予时是否停在引导页而不是无声失败；
  4. 签名预检：拿本机 debug 构建（回退签名）对线上 release 包，应出那句"需先卸载"而不是系统的一句「应用未安装」；
  5. 设备侧 GitHub 直链不通时，是否如实报"哪个候选、什么码"并自动换下一个；
  6. 14 MB 下载期间离开设置页、回到前台后进度是否还在。

## 7. 总账（落地后回写）

| 批 | 内容 | 提交 | 结果 |
|---|---|---|---|
| — | 本文（方案与实测账） | `f5faa92` | 已交 |
| 1 | `ReleaseFeed` / `ApkAssetPicker` / `DownloadCandidates` / `UpdateChangelog` / `ignoredUpdateVersion` + 5 颗用例文件 | `e4cc272` | 已交，33 颗用例 |
| 3 | `ApkInstallLauncher` + `app_update` 落点（`CacheDirs` / `file_paths.xml` / 成对性测试四条）+ `REQUEST_INSTALL_PACKAGES` | `86cbb68` | 已交 |
| 2 | `ApkDownloader`（流式 + 边写边喂散列 + 候选回退）+ 渠道定义搬出 + 9 颗流内核用例 | `1170af6` | 已交 |
| 4 | 弹窗三态 + 「以后再说」记账 + 手动检查强弹 + 清除忽略那一行 | `115de22` | 已交 |

**验证读数**：`:app:compileDebugKotlin` EXIT=0；`:app:testDebugUnitTest` 全量 **115 类 / 876 颗 / 0 失败 / 0 错误**
（基线 109 类 834 颗 ⇒ 新增 6 类 42 颗）；`:desktop:compileKotlin` EXIT=0，且产物 `CacheDirs.class` 里实测含
`app_update` 这个串（不是"应该能编"，是查过）。统计口径用落盘的 `.js` 脚本，内联 node 里那条恒假正则差点数出"0 用例"。

### 7.1 与设计不同的三处（都是当场被事实改的，不是走样）

1. **提交顺序反了**：批 3（安装器）先于批 2（下载器）。因为下载完成那条通知的 `contentIntent`
   就是安装意图，反过来写会得到一个悬空引用。
2. **多了一层契约口**（`data/api/AppUpdateApi.kt` 的 `UpdateDownloadQueue` + `BusinessPorts.updates`）。
   设计里没有这一颗 —— 它是分层守卫逼出来的：界面里写 `ApkDownloader.getInstance(context)` 同时打红了
   `BusinessApiBoundaryTest` 的断言 B（实现类取用白名单只许缩短）与断言 E（静态取用出处必须可数），
   而 E 的判语原文是「**要开口就加契约，别直连**」。所以加端口，不往白名单添行。
   适配器按既有纪律写：现读单例、每次交回**同一个实例的流**（换成新建实例，界面 collect 的与后台写的
   就不是同一条，进度会恒停在 0）。
3. **通知渠道的定义搬了家**（`download/DownloadNotificationChannel`）。原先那条渠道在 `DownloadManager`
   的 `init` 里建，也就是"这台设备用过漫画离线下载"才存在；复用它的应用内更新若碰上没用过的设备，
   `notify()` 打到不存在的渠道上会被系统**静默丢掉** —— 表现就是"下载在跑、通知栏什么都没有"。
   id 与名称逐字未动（改 id 会在老设备上留一条没人用的渠道，并丢掉用户已做过的开关）。
   **一处如实说明**：渠道描述文案从「漫画离线下载进度与完成状态」改成「下载进度与完成状态」，
   而 `createNotificationChannel` 不更新已存在渠道的属性 —— 所以老设备上看到的仍是旧描述，
   只有新装设备是新描述。这是系统行为，不是漏做。

### 7.2 明确没做与本批做不到的

- **前台服务 / Range 断点续传 / 无人确认的自动安装**（§1.3 的四不）。
  代价如实说是：进程被系统回收时这一笔下载中断，重进页面可重试；「App 划掉也能接着下」不在本批。
- **桌面端一行未动**（沿 `:app` only 的既有决策）。
- **镜像表会不会过期**：本批不预测，靠读数说话 —— 每个候选失败时都会把**域名 + HTTP 码/异常名**
  写进失败原因与 log，下一轮照着换那一条，不靠记忆。
- 签名读不出时（个别 ROM 对 archive 不给 `signingInfo`）**不拦安装**：那是我们的观测缺口，
  拿它去拒一次正常更新，等于在能用的机器上把这条链掐死。

### 7.3 待真机读数（本环境按分工不代操作设备）

1. 三态弹窗在 miuix / MD3 两档下的按钮摆位、日志滚动是否顺（`380.dp` 限高在 6.7 寸屏上的观感）；
2. 通知栏进度是否真在动，**完成态点一下能否直接落到系统安装页**（`PendingIntent` + FileProvider
   临时读授权这一步只有真机能证）；
3. 未授予「安装未知应用」时是否停在引导页，而不是点「安装」无声无息；
4. 拿本机 debug 构建（缺 `key.properties` ⇒ 回退 debug keystore）对线上 release 包，
   应出"签名不同、要先卸载、请先备份"那句，而不是系统的一句「应用未安装」；
5. 设备侧 GitHub 直链不通时，是否如实报出"哪个候选 + 什么码"并自动换下一个（这一步顺便验证镜像表还是不是活的）；
6. 14 MB 下载期间离开设置页、回前台后进度是否还在；把 App 划掉则应当中断且不留半截文件；
7. 同一版本点「以后再说」后，冷启动不再弹；出更高一版应当重新弹；设置页那一行「已跳过版本 …」应当只在跳过之后出现。

