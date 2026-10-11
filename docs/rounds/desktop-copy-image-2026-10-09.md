# 第五十四批：浮层那一排多一枚「复制图片」（2026-10-09）

## 1. 这一格补的是什么

浮层那排动作今天有四枚（收藏 / 复制链接 / 在浏览器打开原图 / 找同一张图），而把一张图交给**另一个应用**
只有「复制链接」这一条 —— 对面要图的时候，一条 URL 粘不出东西。写位图是纯 JDK
（`Toolkit.systemClipboard` + 一枚只交位图的 `Transferable`），与那颗「复制链接」用的是同一份剪贴板
⇒ 不加依赖、不动共享面、不动 Android 侧。

复制的是**屏上正在摆的那一档**（`url` 就是浮层顶部那一张吃的地址），不是原图档：
复制一张没在屏幕上出现过的图，用户看不出自己复制了什么。动图复制的是 `ImageIO` 解出来的那一帧位图 ——
剪贴板那头（`DataFlavor.imageFlavor`）本来就是位图，没有"带帧的图"这一档。

## 2. 落地面

| 件 | 做什么 |
| --- | --- |
| `gallery/data/DesktopCoilBytes.kt` | **新建**：`desktopCoilBytes(context, url)` —— 批 51 那颗"先读快照、冷缓存再让同一份单例取一趟"的算式从播放器里抽出来，播放器与复制这一路共用 |
| `gallery/ui/DesktopCopyImage.kt` | **新建**：四种成因各一句、成功/失败各一行读数、那颗只交位图的 `Transferable`、`desktopCanCopyImage()` |
| `gallery/ui/DesktopAnimatedImage.kt` | 改吃那颗共用件，自带的两份取字节删掉 |
| `gallery/ui/DesktopGalleryDetailPanel.kt` | `canCopyImage` 那一档：支持就摆按钮，不支持就把那句说明摆出来；按钮与种子共用 `fireCopyImage` |
| `VeneraDesktopApp.kt` / `VeneraDesktop.kt` / `ForensicFlags.kt` / `build.gradle.kts` | `-Pcopy=` 那一跳四段（构建 → 判读 → 应用根必填 `startCopy` → 浮层 `autoCopyImage`） |
| `DesktopCrashReading.kt` | 批 53 那三颗原子抽出来（类型 / 一行且脱敏的消息 / 栈顶帧），失败读数复用，不写第二份 |

取证种子 `--copy=1` 与 `--reverse` 同一族：那一枚按钮在浮层里，所以它会**顺带把浮层摆出来**，
而那一句实话（`copyNote`）只在浮层本来不会开时念。接管判据与反搜共用**同一颗** `detailPanel(detail, reverse, copy)`
——「哪枚种子会替用户开窗」是同一件事，分两处判就要在参数同给时漂出两种答案。

## 3. 判据：新增 9 颗，共 721 颗 / 93 类 / 0 失败

① 四种失败各一句且两两不同；② 成功那行念得出字节与尺寸而**结构上**带不出地址（形参里就没有 URL）；
③ 摆不摆只看能力位，不支持时那句说明上屏；④ 取字节全树只一处（数 `openSnapshot(`）；
⑤ 按钮与种子共用同一枚 lambda（钉 `LaunchedEffect(post.uid) { fireCopyImage() }` 整串，形状照批 36）
+ 交位图只一处（数 `imageFlavor`）+ 没 import JDK 内部剪贴板件；⑥ `-Pcopy=` 三跳都在；
⑦ 这一格不碰凭据件；⑧ 失败也念得出一行（含栈顶、含"没抛东西时念没有"、含复用脱敏）；
⑨ 失败只有一颗出口（`Refused` 在 `refuse` 之外被构造就红）。

另在 `ForensicFlagsTest` 加 3 颗：`--copy` 登记 + 接管四格、`copyNote` 那四格、以及**批 53 漏的那一条**
（`--crash` 没登记进 `KNOWN` ⇒ 带它起窗时 stdout 同时念「参数已判读」与「未识别的参数」，两句互相打脸）。

扰动跑了八次，各红各的尺子：并句 / 换前缀 / 撤能力位 / 重述一份 `openSnapshot` / 种子自写一份 /
删构建那一跳 / `imageFlavor` 用两处 / 绕过 `refuse` 出口 —— 每次都只红它该红的那一颗。

## 4. 四条量出来的真相（都是先前没登记的）

1. **AWT 没有公开的 `ImageSelection`**。那枚现成的类住在 `sun.datatransfer`（内部件、没导出），
   编译器直接报 Unresolved reference。公开面上写图到剪贴板只剩自持一枚 `Transferable` 这一条。
   尺子跟着从"数 `ImageSelection(`"改成"数 `imageFlavor`"，并加一条负向钉：不许 import 那个内部包。
2. **`setContents(contents, owner)` 里 `owner = null` 是合法的**（Adoptium 21.0.12.1 的
   `sun.awt.datatransfer.SunClipboard` 只查 `contents == null` 那一格）。这一条推翻了我动手前的推断，
   也把「复制链接」那枚按钮一直传 `null` 这件事从"看着像 bug"量成了"不是 bug"。
3. **这台机器的 agent 侧环境整个碰不到系统剪贴板**，三条独立工具链同一天各量一遍：
   `clip.exe` 回「拒绝访问」（exit 1）、PowerShell `Get-Clipboard`/`[Windows.Forms.Clipboard]::SetText` 全抛、
   AWT 八次尝试（文本与图各三、带可见 Frame 再各一）恒 `IllegalStateException: cannot open system clipboard`
   @ `WClipboard.java:-2`。而 `GetOpenClipboardWindow()` 与 `GetClipboardOwner()` 都是 0 ⇒ **不是别的进程占着不放**，
   是本环境没有剪贴板访问权。⇒ 「图真进了剪贴板」这一跳**只能由用户自己点一次量**（手册 §17 记着这一条），
   同一句话也适用于那枚「复制链接」。屏上那句与 stdout 那一行已经把"到了 OS 门口被拒"念清楚了。
4. **失败没有读数 = "没跑到"与"跑到了而被拒"打不掉**。第一版只有成功打读数，于是屏上念了
   「这台机器没给出写图片到剪贴板的路：IllegalArgumentException」而 stdout 一字没有 —— 是那一帧图证
   把这个缺口抓出来的（`D_` 行里根本没有这一格）。⇒ 加了 ⑧⑨ 两颗尺子与那一行读数。

## 5. 顺手补的两枚（都是"登记不全"那一族）

- `--crash` 补进 `KNOWN`（批 53 三跳都接了，唯独没登记，见上面第 3 段末尾）。
- `D_落点` 的 `种子=` 那一格从前只列 `detail/viewer/viewerZoom`，`--reverse` 从没列进去 ⇒
  带 `--reverse` 起窗时那一行念「种子=无」而参数判读早念过一句"已给"。现在 reverse 与 copy 都在，
  这一跑读到的是 `种子=detail+copy`。

## 6. 数字（先回代码量过再写）

判据 721 颗 / 93 类 / 0 失败 / 0 跳过（三本账对得上：XML `tests=` 之和 = `<testcase>` 数 = 裸 `@Test` 数）；
`desktop/src/main` 82 颗文件 / 16,065 行；`desktop.jar` 4,191,329 B；`app/` 侧改动 0 处；取证窗口残留 0 枚。

## 7. 交付包同步与两格顺手量到的

`_qa/desktop-exe-2026-10-09-b51/` 换成本轮那颗 jar，并**用用户那条路起过两回**（双击那颗 .cmd、不带参数）：
两帧都拍到浮层那排五枚（收藏 / 复制链接 / **复制图片** / 在浏览器打开原图 / 找同一张图）。两处顺手记下的：

- **换 jar 要连 `app/*.cfg` 一起换** —— 那份 cfg 按**文件名**列 classpath，而 jpackage 给应用 jar 的名字带内容哈希。
  只换 jar 起不来，只换 cfg 会拿旧字节跑；旧的挪进 `_trash/desktop-exe-2026-10-09-b51-appjar/`（可逆）。
  ⚠️ 另一条：**只改注释也会换掉那颗 jar**（class 里带行号表）—— 本轮改完两处注释后 jar 从 4,191,318 变 4,191,329，
  于是重打一次再同步，而不是"反正只是注释"。另：`packageExe` 在本机失败（要 WiX），交付包走 `:desktop:createDistributable`。
- 第二帧落到的那一张是 **MP4**（格式 MP4、1920×1920）：那一档的字节 `ImageIO` 解不出位图 ⇒ 按下去念的是
  「字节拿到了而解不出位图，剪贴板那头要的就是位图」那一句。**那不是假话也不是缺陷** ——
  剪贴板这一侧确实没有"视频"这一档，而那一句把原因说清了（"四种成因各一句"那条纪律就是为这种格子留的）。
