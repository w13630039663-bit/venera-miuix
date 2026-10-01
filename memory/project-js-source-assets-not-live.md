---
name: project-js-source-assets-not-live
description: 改 app/src/main/assets/sources/*.js 对已安装该源的设备无效 —— 运行期加载的是 files/comic_source/ 下的副本，assets 默认清单只在首次启动解包一次
metadata:
  type: project
---

Venera 的 JS 规则源在运行期**不是**从 `app/src/main/assets/sources/` 加载的。`ComicSourceManager.loadInstalledJsSources()` 用 `parser.parseFile(File(sourceDir, m.fileName))` 读取 `files/comic_source/<name>.js`（`sourceDir = File(context.filesDir, "comic_source")`）；assets 只在 `KEY_BOOTSTRAPPED` 未置位且已安装源为空时解包默认源（原为 `baozi.js` / `copy_manga.js` / `manga_dex.js` 三个，2026-09-21 起加了 `jm.js`），之后 app 升级**不会**再把 assets 里的改动同步过去。

**两条同族的"对新装设备才生效"陷阱（2026-09-21 一次踩两条）**：

1. 只往默认源清单 `defaultFiles` 加一个源，**这台设备上永远不会出现它** —— 因为 bootstrap 是一次性的（`KEY_BOOTSTRAPPED` + 列表非空即跳过）。要么做增量补装（只补本机没有的，且仍要尊重用户主动删除标记），要么明确告诉用户这条只对全新安装生效。
2. JS 源是在 manager 构造里 `scope.launch { loadInstalledJsSources() }` **异步**注册的，冷启动任何一发 `getSource(key)` 都可能拿到 null。**别拿这个 null 去下"源未启用"的结论**（首页推荐区第一版就是这么在启动瞬间显示「当前未启用该源」的），要给有界等待窗口（那里用的 10s）。

**Why:** 2026-09-19 调查禁漫天堂卡片标签时，给 `assets/sources/jm.js` 加了崩溃守卫并装机验证，结果毫无变化 —— 设备上 `files/comic_source/jm.js` 是 09-18 的旧副本（38344 字节 vs 仓库 38618），我改的那 274 字节根本没进运行路径。差点把"改动无效"误判成"改动不解决问题"。

**How to apply:** 任何改动 `assets/sources/*.js` 的工作，验证前必须先确认设备上那份副本：
`adb shell run-as com.venera.compose wc -c files/comic_source/<key>.js` 与仓库 `wc -c` 比对。
要让改动在设备上生效，只能（a）走 app 的源更新 UI —— 但那会从 CDN 拉官方脚本**覆盖**本地改动，只适合验证"未修改的官方行为"；（b）经用户同意后，用 `run-as ... sh -c 'cat > files/comic_source/<key>.js'` 覆盖运行副本（先备份）。
纯 Kotlin 侧的诊断（如解析处的探针）不受此限制，优先用 Kotlin 侧通道拿数据。
另外：这台设备（一加 PJZ110 / ColorOS）的 logcat **拿不到应用自己的 Java 日志**（`VeneraJS`/`VeneraDebug`/`JsComicSource` 全 0 条，历史存档 `.logcat-after.txt` 同样为 0），诊断只能写文件后用 `run-as cat` 读。

**源脚本的网络请求不走 OkHttp 桥。** `VeneraJsEngine` 里没有 fetch 的 `@JavascriptInterface` 实现 —— JS 的 `fetch` 由 WebView 自己的网络栈发出，因此：
- 源 API 的响应**不会**出现在 `cache/venera_http_cache`（那 100MB OkHttp 缓存里只有图片域名与部分 Kotlin 侧请求），想靠读缓存拿接口原始 JSON 是死路；
- 引擎虽有 `_venera.log` 桥，但它落到 `Log.d`，同样被上面那条 ColorOS 过滤吃掉；
- 所以拿"接口到底返回什么字段"只有 Kotlin 侧写文件探针一条可靠路子。注意 `JsComicSource` 里 **`context` 是执行脚本的函数**，不是 Android Context —— 取文件目录要用 `engine.context.filesDir`。

**但这条"只能 Kotlin 写文件"是 ColorOS 真机的限制，模拟器上不适用（2026-09-22 实测）。** 源脚本里的 `console.log` 由 `assets/venera-init.js` 定义成 `log('info','JS Console',content)`，在 **emulator-5554 上能正常进 logcat**。所以在模拟器上探"某源的接口对象到底有哪些字段"，最省事的办法是给设备副本打一行探针：

1. `run-as <pkg> cat files/comic_source/<key>.js` 拉下来（Windows 下会翻成 CRLF，先 `sed 's/\r$//'`），与仓库 `assets/sources/<key>.js` diff 确认副本没被改过；
2. 只加一行 `console.log("PROBE keys=" + Object.keys(x).join(","))`（**只打键名不打值** —— 值是成人语料，进 logcat 再被我读到会触发模型侧内容过滤，见 [[project-subagent-search-hazards]]）；
3. `adb push` 到 `/data/local/tmp/` 再 `run-as <pkg> cp` 进 `files/comic_source/`（`adb push` 不能直写应用私有目录）；
4. `am force-stop <pkg>` 让源重新解析（源只在启动时读一次）；
5. **由用户去点页面**，我只 `logcat -d` 读（见 [[feedback-device-qa-read-only]]）。

恢复：把第 1 步拉下来的原件推回去。**删掉文件不会自动恢复** —— 默认清单的解包判据是 DB 里的 installed 元数据（`if (name in installedFiles) continue`），元数据还在就不会重新解包，只会把该源标成失效。

**判断"端口有没有丢源逻辑"的便宜办法**：`run-as cat files/comic_source/<key>.js` 拉下来与仓库 `assets/sources/<key>.js` 做 diff（两边都先 `sed 's/\r$//'`）。2026-09-19 用这招确认了 `jm.js` 与官方逐字一致，从而把"禁漫天堂卡片标签少"定性为上游限制而非迁移缺陷。

