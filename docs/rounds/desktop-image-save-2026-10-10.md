# 批 85 · 桌面图片落盘 + 首页「下载」那一行点亮（2026-10-10）

排期入口在 `remaining-plan-and-reuse-2026-10-10.md` §4（这一批是 A 档第 1 行的**后一半**）。
`app/` 一字未动、未提交、未推；交付包那一格见 §6。

## 1. 这一格到底缺哪一层（题面当场改窄过一次）

排期表最初把这一批写成"接 Coil 单例 + 落盘"两件事。写下来不到一小时就现量掉一半：
**取图那层早在批 79 就装好了** —— `gallery/data/DesktopImagePipeline.kt:62-65` 就是
`SingletonImageLoader.setSafe { … OkHttpNetworkFetcherFactory … }`，而 `DesktopCoilBytes.kt:33` 已在吃同一份单例。
所以本批真正落下的只有三样：

1. **一颗桌面自持的存图件**（对岸 `GallerySaver.kt` 因 MediaStore / ContentValues / MediaScannerConnection
   整颗在 `desktop/build.gradle.kts:107` 的排除清单里 —— 挡它的是**平台服务**，不是"算落点 + 起名字 + 让重名"那三段判据）；
2. **浮层那排动作里的一枚「存到本机」**（第六枚，与批 54 那枚「复制图片」相邻）；
3. **一颗枚举读侧**，让首页「下载」那一行不再挂「未实现」。

三条定案（都在下面 §2 的落点里兑现）：

- **落点是 `数据根/downloads/图库`，不是 Coil 那份盘缓存。** 批 79 量过缓存在
  `%LOCALAPPDATA%\Temp\coil3_disk_cache`：键是地址哈希、文件名不是那张图的名字、容量闸随时清 ——
  用户按「存到本机」要的是一只**数得出、删得掉、重启还在**的目录。形状照对岸那句口径（漫画存储根 + `图库`）。
- **算落点这一颗只算不建。** 建目录那一笔住在真往下写的那一刻；首页那一屏只是去看一眼，
  看一眼就在盘上多出一只目录 = **读数自己造了自己的证据**。同一族纪律的先例是
  `DesktopDatabasePorts.dbFileAt` 刻意不走 `PathProvider.subDir`（那颗顺手 mkdirs）。
- **存的永远是原图那一档，复制的是屏上正在摆的那一档。** 这两枚按钮挨在一起、共用同一颗取字节件，
  而**地址参数刻意不同轨**：两处若共用一枚，用户切了预览档就会把降档的那一张当成原图存下来。

⚠️ 命名档今天恒取对岸的默认档 `SITE_ID`（`<站名键>-<号>.<扩展名>`）。**不读那枚 `pref_gallery_save_naming`**：
桌面设置屏今天没有它的写侧（能改的只有那八枚），给一枚没人写得动的键配消费者，就是本仓那一族"看起来能配其实恒假"。
同理不设"自定义下载目录"那一条 seam —— 那枚键（`pref_gallery_download_path`）在桌面同样没有写侧，
而"由调用点递进来的参数"这种缝一旦开出来，永远只有一个值喂过它。

## 2. 六处落点

| 层 | 文件 | 落下的东西 |
|---|---|---|
| 存图层 | `desktop/…/gallery/data/DesktopImageSaver.kt`（**新建**） | `desktopGallerySaveDir`（纯算不建）/ `desktopSaveUniqueName`（`名字 (1).ext` 让位）/ `desktopSaveImageFileName`（**逐字转交**共享那颗 `GallerySettingsModel.fileName`，档位 `SITE_ID`）/ `desktopSaveImageUrl`（只认 `fileUrl`）/ 四种失败各一句 / `desktopSaveImageWrite`（先让重名 → 写 → **从盘上回读字节数**）/ `desktopSaveImageRun`（字节走 `desktopCoilBytes`，成了与没成都念一行 `D_存图`）/ `desktopListSavedImages`（枚举读侧，四态） |
| 界面 · 动作 | `gallery/ui/DesktopGalleryDetailPanel.kt` | 形参多两枚**必填**：`saveDir: File`、`autoSaveImage: Boolean`（覆盖层在 provider 外面 ⇒ 不给默认值，第三次踩同一格）；`val fireSaveImage` 那一枚 lambda 由按钮与种子**共用**；「正在取原图那一档并写到本机」那一句在等的时候上屏 |
| 界面 · 那一屏 | `gallery/ui/DesktopDownloadsPane.kt`（**新建**） | 表头四态各一句 + 一行一句（名字 / 真实字节数 / 本机时区的时刻）；全文件只出现**一次**读盘，且不许出现 `mkdirs` |
| 页条 | `gallery/ui/DesktopGalleryHomeSections.kt` | sealed 多一档 `Downloads`；「下载」那一行 `content` 点上、`reason` 撤；body 多一枚 `saveDir: File` 形参并分岔到那一屏；四处"别的域不许串"的 `error` 分支跟着排这一档（`DesktopGalleryDomainPanes.kt` ×4、`DesktopGallerySettingsPane.kt` ×1） |
| 集成方 | `VeneraDesktop.kt` / `gallery/ui/VeneraDesktopApp.kt` | `--save` 判读 → `detailPanel(detail, reverse, copy, save)` → `gallerySaveDir = desktopGallerySaveDir(paths)` 递进应用根；`D_落点` 那行的种子清单多 `"save"` |
| 参数层 | `ForensicFlags.kt` / `desktop/build.gradle.kts` | `SAVE` 登记进 `KNOWN`；`copyNote` 与"存"并成一颗 `overlaySeedNote(detail, copy, save)`（两枚同给时**一句**点名两枚，屏上只多开一扇窗 —— 见下面那条改判）；`providers.gradleProperty("save")` 那一跳（同一颗纪律第六次） |

### 2.1 一处刻意的改判：`copyNote` → `overlaySeedNote`

批 54 那句原名 `copyNote(detail, copy)`，形状是"每枚种子各念各的一句"，两枚同给时 stdout 出两句。
本批把 `--save` 并进**同一颗**函数（`overlaySeedNote(detail, copy, save)`）而不是新立一颗 `saveNote`：
那两枚按钮住在**同一颗浮层里**，"为谁多开了这一扇窗"是同一件事，分两颗判就会在两枚同给时念出两句
各自只提一枚的话 —— 同一件事只许一把尺子那条（批 14 立的）。
⚠️ 这笔改动会让**历史 stdout 的形状对不上**：批 54 之后、本批之前那几趟取证里，`--copy` 那一行念的是旧那句
（`… 要按的那一枚按钮在详情浮层里 …`）。旧记录不改，本批之后的记录念新那句。
⚠️ `--reverse` 那一句今天**仍单独住在 `reverseNote` 里**（它的话术带着批 35"这一格下面才出结果"那层专属信息）。
并进来是另一批改判，本批不动那句的形状 —— 这笔账挂到 §6。

## 3. 起窗那两帧（决定性那一格：盘上真多了一颗文件）

两趟各起一次窗，数据目录隔离在 `LOCALAPPDATA=D:\venera-compose\_qa\b85_local`（`--no-daemon` 起，
env 才递得进被 fork 的那台 JVM —— 挂 daemon 时它继承的是 daemon 自己的旧 env，那一格真人那份 `%LOCALAPPDATA%\venera` 会被写脏）。
起跑前数窗 `count=0`，两趟各自拍完 `Stop-Process`，收尾复数仍是 **0**；两帧的 `relatedTransientWindows=[]` ⇒ 没有 `title:"Error"` 的从属窗。

### 3.1 第一趟：按一次「存到本机」

`./gradlew --no-daemon :desktop:app -Pproxy=127.0.0.1:7890 -Psave=1`（stdout 逐字，节选）：

```
D_数据目录 D:\venera-compose\_qa\b85_local\venera（写入回读已验）
D_落点 域=图库(启动) 行=每日热门 浮层=关(换域或换页自动收) … 种子=detail+save
D_落点 域=图库(启动) 行=每日热门 浮层=开(墙上点卡) 查看器=开 … 种子=detail+save
D_存图 落盘 文件=safebooru-12349956.jpg 字节=102739 目录=D:\venera-compose\_qa\b85_local\venera\downloads\图库
```

- `种子=detail+save` —— `--save` 自己没开浮层，是 `detailPanel` 那四枚判据替它开的，而清单念得出**为谁**开的（§2.1 那颗函数）；
- 那一行 `D_存图` 三格全从落盘那颗文件现取：`字节=102739` 与 `ls -l` 独立量到的 **102739 B** 对得上（不是请求参数里的那个数）；
- 站方地址一个字都没进读数（那一条走的是 safebooru 的 `file_url`，而念出来的只有名字、字节与目录）。

帧（WGC，`Venera Desktop R1-F 最小闭环 #50204`，`occlusionSafe: true`）读到四样：

1. 动作那一排**六枚**齐：收藏 / 复制链接 / 复制图片 / **存到本机** / 在浏览器打开原图 / 找同一张图；
2. 按钮下面那句「**已存到本机：safebooru-12349956.jpg（102739 字节）**」—— 屏上与 stdout 同一份状态；
3. 档位那一排停在「**大图**」而存下去的是**原图那一档**（102739 B、右侧信息表那行「文件大小 0.1 MB」）——
   这就是 §1 第三条定案在帧上的形状：**屏上摆的那一档与存下来的那一档不是一回事**，两枚按钮各取一档；
4. 左侧 pane 里「历史」那一行仍挂着「未实现」药丸与那句说明，而「下载」那一行**没有药丸了**（定点豁免没被顺手撤，撤的是点亮了的那一枚）。

### 3.2 第二趟：换一次进程，只看那一目录

`./gradlew --no-daemon :desktop:app -Pproxy=127.0.0.1:7890 -PstartSection=section-downloads`
⇒ `D_落点 域=图库(启动) 行=下载 … 种子=无`（**没带任何动作种子**，那一屏只是去看一眼）。

帧（`#5156`）读到三样：

- 表头「**本机存过 1 张 · 合计 100.3KB · 存在 D:\venera-compose\_qa\b85_local\venera\downloads\图库**」；
- 一行「**safebooru-12349956.jpg · 102739 字节 · 2026-10-10 19:34**」；
- 「下载」那一行是选中态（紫渐变 + 行首指示条）而**没有**「未实现」药丸。

⚠️ 这一趟是**另一台进程**（pid 5156，第一趟是 50204）读到的 —— 所以"落盘"不是那一趟内存里的自述：
`ls -l` 在两趟之间量到同一颗文件同一串字节（102739 B，mtime 19:34）✓。
"合计 100.3KB" 与 "102739 字节" 是同一颗件的两个口径（表头走 `desktopByteLabel`，行里念真数），不是两份账。

## 4. 现量与 RED→GREEN

- **RED 第一次**（新建 `DesktopImageSaverTest` 13 条）：`:desktop:compileTestKotlin` 报一串
  `Unresolved reference`（`desktopSaveImageWrite` / `DesktopSaveImageCause` / `desktopListSavedImages` …），
  即"这颗件还不存在"那一档失败，不是拼错。
- **GREEN 第一次**：`--tests "*DesktopImageSaverTest*"` → XML `tests="13" failures="0" errors="0"`，
  mtime 当场核过（旧文件不算读数那条）。
- **RED 第二次**（新建 `DesktopSaveImageSeedTest`）：编译面报 `Downloads` / `overlaySeedNote` /
  `desktopSavedHeaderReading` / `detailPanel` 少 `save` 那一档 —— 全是要新落的东西。
- **第一次全量**：`1062 tests completed, 3 failed`。三处红的都**不是实现**：
  ① 本鱼把 `startSave = save == true` 钉在**应用根**那颗文件上，而那一步其实住在**集成方**（`VeneraDesktop.kt`）
  ⇒ 锚点要钉在真正做那一步的文件上，改成钉 `main` 并补一条"应用根形参表里得有 `startSave: Boolean,`"；
  ②③ `DesktopStaleClaimsTest` 那两把"未实现枚数"的尺子（⑥⑦）报 `expected:<2> but was:<1>` ——
  这正是它们**该**报的：那一行的账从今天起是一行（只剩「历史」）。改账：`HomeSections` 那句注释
  由"那二行未实现"改成"那一行未实现"，两把尺子跟着现数改判，并各补一条对照
  （「下载」那一行不许再回到未实现那一族 / 全树再出现"二行未实现"就红）。
- **最后一次全量**（台架还原之后那一趟，mtime 19:31 为证）：**1063 用例 / 136 颗类 / 0 失败 / 0 错误**，
  三方口径对得上（`@Test` = XML `tests=` 之和 = `<testcase>` 数 = 1063，测试文件 143 颗）；
  判据目录 **0 处漂移**（表行=136 表合计=1063 = XML 两枚数，含 §10 那三枚）；
  `desktop/src/main` **101 → 103 颗 / 20,572 → 21,038 行**（+2 颗新建件、+466 行）；
  `git diff --stat -- app/` **空**（对岸一字未动）；两帧起窗残留窗 **0** 枚、真人那份 `%LOCALAPPDATA%\venera` 未碰。
- ⚠️ 两件**没做**的事不许被这笔账盖掉：① 漫画章节下载仍没有入口（本批交的是那颗件 + 目录 + 那一屏）；
  ② 交付包没为这一批重打 —— 理由与"怎么补"都写在 §6。

## 5. 台架（尺子咬不咬得住）

`sh _qa/perturb_driver.sh b85 14` —— 每一笔改坏一处、跑**全量** `:desktop:test`、按身份戳出报告、再还原。
起手 `node _qa/b85_perturb.js check` 先要求"14 笔各命中一处、终点 0 处"（这道闸本批一次都没挡下东西 ——
写扰动前逐条自问过"`to` 是不是 `from` 的子串"那一课）。

| 笔 | 改坏的那一处（等价于哪种回归） | 结果：红在哪条 |
|---|---|---|
| P1 | 算落点顺手 `subDir`（看一眼就建目录 = 读数自造证据） | `DesktopImageSaverTest :: 算落点不建目录` ✅ |
| P2 | 重名不让位（第二张把第一张洗掉） | `第二次存不覆盖第一张` + `重名让位`（两条，✅ 同一处缺陷的两面） |
| P3 | 桌面自己拼一次文件名 | `文件名转交共享那颗`（"没转交共享那颗"）✅ |
| P4 | 命名档没取对岸默认档（换 `ORIGINAL`） | `文件名转交共享那颗`（`逐字转交 expected:<[yandere-]123.jpg>`）✅ |
| P5 | 报的字节数拿请求参数而不是回读盘上那颗 | `字节数从盘上回读` ✅（行为两数相等，**只有锚点咬得住**这一格） |
| P6 | 0 字节那句与"没取到字节"撞成一句 | `四种失败各念一句` ✅ |
| P7 | 站方没给原图地址时不再判成没地址 | `取地址只认原图那一档` ✅ |
| P8 | 读数多递一枚 `post`（"结构上拿不到地址"变假） | `读数带不出地址`（形参表整串那条）✅ |
| P9 | 种子自己写一份发车（量的不是用户那条路） | `按钮与种子共用同一枚 lambda` ✅ |
| P10 | 构建脚本那一跳漏掉（`-Psave=1` 不报错也不做事） | `五层接线都在` ✅ |
| P11 | 那一屏顺手 `mkdirs` | `那一屏不建目录` ✅ |
| P12 | `KNOWN` 漏登记 `--save` | `参数登记过` ✅ |
| P13 | `detailPanel` 漏 `save` 那一档 | `带按钮在浮层里所以要接管浮层` ✅ |
| P14 | **等价改写**：`takeIf { isNotBlank() }` → `if (isBlank()) null else …` | **要求不红，确实一笔没红**（报告原话：「这是正确输出」）✅ |

收尾：还原后再跑一趟全量 `EXIT=0`（1063 / 136 / 0 失败），`node _qa/b85_perturb.js check` 复跑仍是"14 笔各命中一处"，
且 14 条终点串在树里 grep 全为空 ⇒ **没有残留改坏**。

⚠️ 两条如实记的边界：① P5 那一格**没有行为判据可咬**（写成功时两个数天然相等），它靠的是锚点 + `readBackSize(file: File)` 的形参形状；
② `NO_BYTES` 那一档（缓存没有、补取也没成）今天**没有单测** —— 它要 Coil 与网络，桌面单测里构造不出来，
只有那四句各自不同这一条间接守着。这一格的正解与批 54 同一处：要么起窗量它，要么把取字节那一层做成可注入的缝（后者会牵动那颗共用件，本批没做）。

## 6. 下一件与这一批欠的

- 下一件 = **批 86**：归档直读（zip4j 或 commons-compress 二选一）+ 备份导出打包。与本批同一族
  （字节进出磁盘），而本批那颗件已经是它的落盘地基。
- 这一批欠下的三格，都在这张表里点名而不是散在对话里：
  ① **章节下载**（A 档第 1 行的**前一半**）：`desktopSaveImageWrite(dir, post, bytes)` 只吃一枚 `dir` 与一串字节，
  缺的是"取一整话的页 → 逐页落盘 → 进度与取消"那一圈，以及那一屏的入口；
  ② **命名档与自定义目录**：两枚键（`pref_gallery_save_naming` / `pref_gallery_download_path`）在桌面都没有写侧，
  要接就得**先给设置屏加那两行**（第八枚、第九枚），别让一枚没人写得动的键先长出消费者；
  ③ **`--reverse` 那一句并进 `overlaySeedNote`**：三枚浮层种子共用一颗实话函数，剩最后一枚没并。
- ⚠️ 交付包那一格：本批**没重打包**。理由要说清 —— 新落的这一路只在 `--save` 真被递进进程时改行为，
  而 `-Psave` 那一跳住在 `:desktop:app` 的 JavaExec args 里；包内那条路（launcher 直接收 `--save=1`）**量过能递**
  （批 78 那趟就是走 launcher 传参数的），所以这一格的正解是"下一批重打包时顺手 `-Psave=1` 起一趟，
  核 `D_存图` 与包内落点目录"，不是"现在重打一遍"。
