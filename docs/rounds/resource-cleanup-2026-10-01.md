# 无用资源清理 + 优化审计（2026-10-01）

> 范围：**只做"清理无用资源"与"优化审计"**。版本号 2.0、应用图标、MD3 适配由并行会话处理，本轮一行未碰。
> 与同日 `docs/rounds/code-audit-batch-p-2026-10-01.md`（批次 P）**不重叠**：那一份管取数状态判定、正则判据、注释三类、`BitmapSliceHelper`/`GalleryArtists`/`doubleTapZoom` 归档；这一份管资源/依赖/偏好僵尸键。两份合起来才是今天的全量。

---

## 一、本轮已清理（全部实测判据 + 构建验证）

| 项 | 判据（怎么确认是死的） | 处置 |
|---|---|---|
| `assets/translation.json`（39946 B） | 全树（含 34 个源脚本与 `venera-init.js`）**零消费者**；`venera-init.js:1217,1227` 里的 `translation={}` 是**每个源自带的 i18n 对象**，与这份上游 app 级字符串表无关 | 移到 `_trash/assets-2026-10-01/translation.json`（可逆），assets 从 46 → 45 个文件 |
| `pref_auto_crop_borders`（`autoCropBorders` + `setAutoCropBorders` + `KEY_AUTO_CROP_BORDERS`） | 声明文件外 0 引用；设置页灰行早已撤销（`feature/settings/ReaderSettings.kt:14` 明说不再出现） | 三处全删 |
| `pref_enable_doh`（`enableDoH` + `setEnableDoH` + 常量） | 0 引用；`NetworkSettings.kt:56-58` 早已注明"僵尸键 + DNS 行已删" | 三处全删 |
| `pref_local_favorites_first`（`localFavoritesFirst` + setter + 常量） | 0 引用；唯一提及是 `ComicDetailScreen.kt:1995` 的一句**注释**，而该注释宣称的"顺序由设置项决定"是假的——面板顺序实为硬编码（本地在 `:2035`、网络在 `:2071`） | 三处删 + 注释改为陈述真实行为 |
| `pref_comic_display_mode` 一族（`comicDisplayMode` + `setComicDisplayMode` + `KEY_COMIC_DISPLAY_MODE` + `MODE_BRIEF` + `MODE_DETAILED`） | 声明文件外 0 引用；原注释"列表页 AppBar 的切换按钮读写此键"**不实**（真实存储在 `comic_list_presentation/display_mode`，见 `data/prefs/ComicListPreferences.kt:9,18-20`） | 代码侧全删；**迁移能力未受损**——`ComicListPreferences.kt:14` 读旧键用的是**字符串字面量**而非我删掉的常量 |
| 死依赖 `androidx.paging`（`paging-compose` + `paging-runtime-ktx`） | `import androidx.paging` 全树 **0 处**；grep 到的 `Pager` 全是 `foundation.pager.HorizontalPager` | 从 `app/build.gradle.kts` 与 `libs.versions.toml`（含 `pagingCompose` 版本行）删除 |
| 死依赖 `app.cash.quickjs:quickjs-android` | 全树 0 处使用；真引擎一直是 `android.webkit.WebView`（`engine/VeneraJsEngine.kt:131,137,212`）。原注释称"QuickJsBridge 已标注待替换"**是误导**——仓库里从来没有 `QuickJsBridge` 这个类 | 依赖 + 那段过时注释一并删 |
| 死依赖 `okhttp-dnsoverhttps` + `VeneraNetworkClient.kt:11` 的未用 import | 只 import 从未 `builder.dns()`（原 `:20-21` 自陈"仅做接入决策"） | 依赖、import、以及类头注释里那两行 DoH 叙述一并撤；留一行说明"从未接线" |
| `app/build.gradle.kts:96` 重复声明的 `miuix.blur` | 同一依赖在 `:96` 与 `:129`（Miuix 块）各写一次 | 去掉前者，保留 Miuix 块内那一处 |

**净变化**：5 个文件，`+9 / −74` 行；assets 少 1 个文件 39 KB；依赖少 4 条。

---

## 二、审计查出但**故意没动**的（附理由，不是遗漏）

| 项 | 为什么没动 |
|---|---|
| `LocalFavoritesManager.kt` 的 7 个零调用方法：`comicExists:186`、`editTags:506`、`folderComics:135`、`folderToJson:595`、`unlinkFolderFromNetwork:570`、`onRead:520`、`countUpdates:663` | **该文件此刻正被并行会话改动**（`git status` 显示 M）。删方法要连带 `FollowUpdatesRepository.kt:158` 的委托链，撞车风险高于收益 |
| `moveFavoriteAfterRead`（`VeneraPreferences.kt:353`）+ `setMoveFavoriteAfterRead` | 读点在上一条那个**无调用者**的 `onRead()` 里，写点全仓 0 处 → 双重死链。但它与 `LocalFavoritesManager` 绑在一起决策：要么补 UI + 接阅读器，要么整链删。半删会留下"键没了、读点还在"的中间态 |
| `FavoritesViewModel.kt:217 toggleFavorite` | 保护域（ViewModel），且 `FavoritesViewModel.kt` 此刻是 M 状态 |
| `ComicSourceManager.kt` 的 9 个零调用方法（`deleteJsSource:440`、`registerSource:469`、`getSourceByFileName:480`、`resetRepoUrl:519`、`sourceFilePath:680`、`getAllExplorePages:1129`、`getAllCategories:1175`、`searchAggregatedStream:835`、`cookieFieldsOf:1369`） | 保护域 `source/`，未申请豁免 |
| `HostCircuitBreaker.kt:44 isOpen` | 保护域 `data/network/`；熔断器本体在用，只有这个方法没人读 |
| 仅被单测引用的 4 个：`GalleryHandoff.kt:51 handoffCoverage`、`GallerySearchContext.kt:163 isStaleContext`、`FavoriteModels.kt:48 parseTime`、`ImageHeaderPolicy.kt:119 publish` | "有测试无生产调用点"有两种正解（补接线 / 连测试一起撤），属功能决策不属清理 |
| `assets/licenses/` 三个 LICENSE（17 KB + 11 KB + 1 KB） | 无代码消费者，但它们是**法律归属静态文件**（ehtagtranslation / ffdkj-danbooru-tags / miuix-liquid-glass）。仓库里没有"开源许可"页面——**要不要做那个页面是产品决定，不是垃圾** |
| `jm.js:886-902` 与 `ImagePipelinePolicy.kt:208-235` 的 JM 分块双实现 | 实测两侧口径**已一致**（Kotlin `:218` 注释与 jm.js 公式相符），是"脚本路径 vs 原生路径"的固有重复，不是缺陷。动它要重跑真机对拍 |
| `_qa/`（**358 MB**）、`_probe/`（17 MB）、`_trash/`（3 MB） | 已被 `.gitignore:38-40` 忽略，**不影响仓库交付**；里面是真实图站截图与探针产物，按纪律不入库也不由我删 |

**顺带纠正 `docs/rounds/code-audit-batch-p-2026-10-01.md` §七.2 的一条过期陈述**：它写"`_probe/`、`_qa/`、`_trash/` 仍未进 `.gitignore`"——实测 `.gitignore:38-40` 三行都在（还带一段解释注释），该条已不成立。

---

## 三、优化审计：值得排下一轮的（按性价比排序，只报事实不给方案）

1. **`getSharedPreferences` 散在 13 个文件**，10 个以上独立 xml（`venera_preferences` / `venera_cookies` / `venera_ua_policy` / `comic_list_presentation` / `comic_source_metrics` / `home_recommend_cache` / `venera_gallery_search` / `venera_guard_prefs` / `venera_source_copy_manga` / `venera_webdav_prefs`）。本轮清完后**显示模式的双存储已收敛成一份**（`comic_list_presentation`），这是本轮唯一消掉的重复实现。
2. **十个文件的体量已经影响可改性**：`ComicDetailScreen.kt` 2355、`VeneraReaderScreen.kt` 2188（手势 + 状态 + 网络 + 分享落盘同处一文件）、`GalleryScreen.kt` 1878、`SearchScreen.kt` 1864、`JsComicSource.kt` 1856、`GalleryPostScreen.kt` 1820、`GallerySearchArea.kt` 1620、`ComicSourceScreen.kt` 1611、`ComicSourceManager.kt` 1466、`FavoritesScreen.kt` 1325。批次 P 已明确"大文件本轮不拆"，这条仍然挂着。
3. **10 条编译期弃用告警**（每次构建都刷）：`rememberModalBottomSheetState` ×5（`GalleryInfoSheet.kt:190`、`VeneraReaderScreen.kt:1213,1327,1536`）、`Icons.Outlined.DriveFileMove` → AutoMirrored（`FavoritesScreen.kt:409`）、`LocalClipboardManager`（`GallerySearchArea.kt:817`）、`@UnstableApi` 对 media3 无效 ×2（`GalleryVideoViewer.kt:64,167`）、`when` 已穷尽仍写 `else`（`VeneraReaderScreen.kt:1665`）。其中 3 处在保护域（reader），要豁免才动得了。
4. **`material-kolor` 只有一个调用点**（`VeneraTheme.kt:70`，且只在 `themeColorSource=CUSTOM` 分支）；`material-motion-core` 只在 `Navigation.kt` 4 处；`media3` 只在 1 个文件 15 处。都不是死依赖，但**为极小面积各背一个库**，值得在包体审计时一并权衡。
5. **`androidx.work` 是活的但只承载一个功能**：`VeneraApp.kt:56-63` 的 `followUpdatesFolder.collect{}` 会调 `.enable()/.disable()`。⚠️ 这一点**纠正我先前那版评估里的说法**（我说过"追更调度者全仓无调用点=半接线"，那是把 `FollowUpdatesScheduler` 与 `onRead` 两条链混了）；追更的调度侧是接通的，缺的是**页面入口**（`feature/FollowUpdatesScreen.kt:56` 无导航入口）。
6. `Log.x()` 全仓 171 处、`android.util.Log` 直用 61 处——无临时 tag 残留，但没有统一收口，日志级别靠各处自决。

---

## 四、验证记录

```
基线（改动前，含并行会话 2212 行未提交改动）：
  :app:compileDebugKotlin            BUILD SUCCESSFUL   exit=0
本轮清理后，强制重跑（--rerun-tasks，不吃缓存）：
  :app:compileDebugKotlin            通过
  :app:testDebugUnitTest             通过
  :app:assembleDebug                 BUILD SUCCESSFUL in 2m25s   exit=0
  产物：app/build/outputs/apk/debug/  4 个 APK（arm64-v8a / armeabi-v7a / universal / x86_64），17:09
```

一次诚实记录：中途 `:app:assembleDebug` 报过一次 BUILD FAILED，重跑即全绿且所有任务 UP-TO-DATE——**没能定位到那次失败的原因**（当时的 `tail -6` 把报错截掉了）。时间点与并行会话存盘窗口重合，倾向是撞上了别人半保存的文件，但这是推测不是结论。另外那条"Background command completed (exit code 0)"的通知是外层 `echo` 的退出码，不是 gradle 的——**看后台任务通知会漏掉真实失败**，必须读输出文件。

未做真机 QA：本轮删的都是零引用代码与零消费者资产，装机后需回归的只有两处**有用户可见风险**的点——① 收藏面板的本地/网络分区顺序（注释改过，代码没改）；② 首次启动的列表展示模式迁移（老设备上 `pref_comic_display_mode` 的值仍应被 `ComicListPreferences` 读走）。

## 五、未提交

本轮改动**没有 commit**（工作树里还有并行会话的 28 个 M 文件，混在一起提交会分不清归属）。要提交时建议按轮拆：本轮 = `app/build.gradle.kts`、`gradle/libs.versions.toml`、`data/prefs/VeneraPreferences.kt`、`data/network/VeneraNetworkClient.kt`、`feature/ComicDetailScreen.kt`、`assets/translation.json`（删除）+ 本文件。`FREEZE-STATEMENT.md` 本轮**没碰**（它也是 M 状态，追加会撞车）。
