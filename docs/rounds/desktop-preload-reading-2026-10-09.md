# 批 56 · 预取那一族第一次有读数（2026-10-09）

`app/` 零改动、零新依赖。改动全在 `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/`：
`DesktopGalleryPreloadPolicy.kt`（两颗新读数件）+ `VeneraDesktopApp.kt`（两行接进那颗 `LaunchedEffect`）。

## 1. 补的是什么

那枚开关（`galleryPreload` 三档）批 47 接上了消费点、批 49 教会它"发 → 等取消 → 停手"，而 stdout 上关于它的字**是零**：
翻一页排了几张、连播开着时那一批有没有被收手，全看不见。这一族偏偏最容易"说了而没做"（`enqueue` 不吃组合那层的取消）。
现在两行：

- `D_预取 档=NEXT 排=1 取=SAMPLE` —— 排在"空不空"那一步**之前**，所以 OFF 与"开了但没货"是两句话；
- `D_预取 收手=1（这一批不再要了）` —— 排在 `dispose()` **之后**，先念后停就是拿请求当结果。

## 2. 判据 6 颗（`DesktopPreloadReadingTest`）+ 扰动 8 道

| 扰动 | 改了什么 | 红在哪 |
| --- | --- | --- |
| P1 | 发那行去掉 `档=$mode` | ① `DesktopPreloadReadingTest.kt:35`（有两行撞在一起） |
| P2 | `planned == 0` 改 `planned < 0` | ② :45（零张那句实话没了） |
| P3 | 收手那行去掉 `=$disposed` | ③ :57（停了几笔读不出） |
| P4 | 给读数加一枚 `hint: String = ""` 形参 | ④ :67（形参里能带出地址） |
| P5 | 发那行挪到 `urls.isEmpty()` 之后 | ⑤ :76（空批静默） |
| P6 | 收手那行挪到 `dispose()` 之前 | ⑤ :80（拿请求当结果） |
| P7 | 在集成方多调一次 `desktopPreloadUrls(` | ⑤ :85（对照组：调用点恰好一处） |
| P8 | 在集成方直接 `println("D_预取 手拼一句")` | ⑥ :92（第二份口径） |

八道全红在指的那一行，还原后整套 734 / 0 失败 / 0 跳过（三方计数对上）。

## 3. 本批唯一一条改判：一颗旧尺子被合法的第二处咬到

`DesktopGalleryPreloadTest` ⑧ 从前数**整颗文件里 `tier: DesktopDetailTier` 出现次数 == 1**。
新加的读数件形参**也叫 tier、也必须是同一枚档位**（读数若不念屏上那一档，"我预取了大图档"与"屏上是预览档"就分不出来），
于是它红在 `expected:<1> but was:<2>` —— 而它要防的"预取按设置推、屏上按共用档"并没有发生。
改法：**只切计划那颗自己的签名**（从 `internal fun desktopPreloadUrls(` 切到第一个 `):`），
判式吃的是"这一颗按哪一档取"这件事，不再管旁件叫什么。文件级那颗负向钉（`quality: GalleryPreviewQuality` == 0）原样留着。
⇒ 这是坑 ㉖ 那一族的**第十二次**：计数式锚点会被同族里合法的第二处喂饱或咬到，接线类判据要钉到"哪一颗件的签名/哪一次调用"这一层。

## 4. 活体读数（两跑，窗口捕获，用完即关）

- 跑一（连播关，`-Pdetail=1 -Pviewer=1`）：`D_预取 档=NEXT 排=1 取=SAMPLE` 一行；帧上那一档高亮的正是「大图」，
  与 `取=SAMPLE` 对得上，`relatedTransientWindows` 空 ⇒ 没有 Error 框（批 47 那枚"Coil 在桌面要 coroutines-swing"的崩溃族，这一批又动了同一层，所以图证不是可选项）。
- 跑二（连播开 3 秒）：75 秒里 55 行，成对出现，stdout 次序是 **发(新一页) → 收手(旧那批)** ——
  效应重起时新体先跑、旧体的 `finally` 后到，两行**不保证相邻**，grep 时按"两行各自成对"数，别按配对数。
- 副产品一条：`pref_gallery_autoplay_sec` 写成 JSON 字符串 `"3"` 会让应用在 `main` 里抛
  `IllegalStateException: 键 … 不是数值（实际存的是 "3"）` 并拒绝启动（`JsonKeyValueStore.numberContent`）。
  念得清楚、没静默降级，这一条按原样收下；**给 KV 夹具要写数值**。跑完已还原备份。

## 5. 交付包

`_qa/desktop-exe-2026-10-09-b51/` 与构建产物逐字节对齐（同步只补了一枚重命名的 skiko 包，旧的那枚进 `_trash/desktop-exe-b56/`）；
`app.classpath` 100 条 / 缺文件 0 / 孤立 jar 0；应用 jar **4,199,951 B**。

## 6. 数字（现量）

734 判据 / 95 类 / 0 失败 / 0 跳过；`desktop/src/main` 82 颗 / 16,177 行；`app/` 侧改动 0 处；取证窗口残留 0 枚。
