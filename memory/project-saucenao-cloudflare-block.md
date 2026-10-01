---
name: project-saucenao-cloudflare-block
description: 画廊以图搜图接 SauceNAO 的 Cloudflare 卡点：真根因是过盾页把"看见 cf_clearance"当通关导致 121ms 假通关腰斩挑战；含已排除成因与仍未证实的 __cf_bm 假设
metadata:
  type: project
---

2026-09-27 落地画廊「以图搜图」（SauceNAO）。代码与 UI 都在（见 `docs/rounds/gallery-reverse-search-saucenao-2026-09.md`），**但一次成功的结果至今从未拿到过** —— 卡在 Cloudflare。

**真根因（已定位并改）**：`CloudflareBypassActivity.checkCookies` 从前只要 `CookieManager.getCookie(url)` 里**出现 `cf_clearance=` 就宣布通关并 `finish()`**。两条事实叠起来必然假通关：
1. `cf_clearance` 在**端挑战页上就会下发**，真正生效要等 JS 跑完、页面跳回目标之后；
2. WebView 的 CookieManager 是**持久**的，上一次会话留下的旧 `cf_clearance` 在 `onPageStarted` 那一刻就已经在串里。

后果：验证窗口开进去 **121ms 就自己关掉**，挑战被腰斩，重试带的是一枚从未激活的 cookie → 同一个 403 永远重复。修法是把判据换成"**页面已离开挑战页**"（title 不再是 `Just a moment...` / `Attention Required!` / `Checking your browser` / `Cloudflare`）。

**⚠️ 一个差点踩进去的坑**：判据不能复用 composable 里那个 `pageTitle` —— 它的**初值是"安全验证中..."**，不含任何挑战特征，拿它当"已离开挑战页"会当场复现同一个误判。所以要一个初值为空串的原始字段（`lastPageTitle`）。凡"用某个文案状态当判据"，先看它的初值像不像"通过"。

**已排除的成因（别再试）**：
- **换 UA**：匿名直连三种 UA（移动 Chrome 串 / `Venera/1.0 (Android)` / 不带 UA）全部 `403` + `server: cloudflare` + body `Just a moment...`。
- **没配 API Key**（成因 A）：**2026-09-27 用户实测推翻** —— 他填了免费 Key 仍然 403。所以"Breadboard 没 Key 就拒绝发起"只是它的产品选择，不是我们的病因。（代码里目前仍保留"没 Key 不发"这一道，措辞已按实测改过。）

**仍未证实（成因 B）**：日志原话 `Saved 1 cookies ... (includes cf_clearance)` —— Cloudflare 正常同时下发 `cf_clearance` **与 `__cf_bm`**，只回收一枚的话重试必被再挡。改点在 `data/network/CloudflareBypassManager.kt`。**属保护域，未授权不要动。**

**2026-09-28 进展：那句日志本身就是没验过的断言。** `cf_clearance` 是 **HttpOnly**，
而取证走的是 `CookieManager.getCookie()` —— 按规范它取不到 HttpOnly 值。
所以"日志说包含了"与"过盾后仍 403"必有一个是真的，光看日志无法判。
本轮装了**只读取证探针**（`CfBypassEvidence`，不改任何判定）：记实际取到的 **cookie 名字清单**
（只记名字，值含会话凭据）、`cf_clearance` 在不在、以及**过盾后那一笔重试的 HTTP code（两档都记** ——
只记失败就没法证明"曾经通过过"）；`getCookie` 整串为空单独一档（那是 WebView 与 OkHttp 两套存储根本没通，
与"通着但 HttpOnly 取不到"是两条病因）。本机应用层 logcat 读不到，所以落文件：
`adb exec-out run-as com.github.w13630039663bit.venera.miuix cat files/cf_bypass_evidence.txt`。
**2026-09-30 复查：探针仍然一条读数都没有。** `adb exec-out run-as <pkg> cat files/cf_bypass_evidence.txt`
回 `No such file or directory` ⇒ 装包之后用户没触发过一次盾流程，**这一层还判不了，别当"已验过"**。
要它出数必须请用户走一次「以图搜图」（我这边设备只读，不代点页面）。
**拿到数之后这一层应整体撤掉**；真凶确认后要动的是 `Cookie.Builder().domain(host).path("/")`
那三处丢失（Secure/path/作用域）。

**How to apply:**
- 判"某个请求能不能过盾"只看两件事：**有没有打 `NoInteractiveBypassTag` / `ImageFetchTag`**，以及响应满不满足 `code in {403,503}` + `server` 含 cloudflare + body 含挑战标记这三条。判定**按响应内容、不限域名**。`ImageFetchTag` 是另一件事（同时关掉过盾**和**域名熔断），别合并理解。
- 交互式过盾内部是 `runBlocking { await() }` 且**没有超时** —— 这就是 2026-09-26 永久转圈的成因。任何允许它弹的调用方，都必须**在自己这一侧加墙钟上限**，且把网络放在**不归 viewModelScope 管**的独立作用域里（parked 的线程到不了可取消点，协程超时对它无效）。
- **收紧过盾判定是共享改动**：`CloudflareBypassActivity` / `Interceptor` 全应用 33 个漫画源共用。改完要请用户顺手开一个漫画源确认没变慢或卡住（判据收紧后，原本"假通关秒关"的场景会真的停在窗口里等）。
- **这个组件从前只报结论、不报判据**，于是"过盾成功了却还是 403"完全没有可查的东西 —— 我们那次是靠两次时间戳之差（121ms）倒推出假通关的。再动它之前先确认判据日志（title / 有无 clearance / verdict）与"重试仍被拒"那一行都还在；**给没有可观测性的组件补判据日志，应当与修它同批做**。
- 相关：[[project-gallery-module-isolation]]、[[feedback-degrade-paths-must-fail-loud]]、[[project-gallery-data-ceilings]]、[[feedback-probe-numbers-self-audit]]
