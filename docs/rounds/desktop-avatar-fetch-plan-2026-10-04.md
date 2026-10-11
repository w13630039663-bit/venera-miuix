# 桌面第十批 ②：画师头像现取 · 开工单（2026-10-04，闸门已定）

用户 2026-10-04 拍板：**闸门 = 只给当前视口里的位取**（每人两笔，滚出视口不再发，取到就落盘）。
本文件是**自含**的开工单：新会话不必读历史也能直接动手。缺陷根因与读数在手册 §24.8。

---

## 1. 要修的是什么（一句话）

桌面画师那一屏屏上写着三档「本机存过的真头像 > 名下最近一张收藏 > 首字母座」，
而**第一档今天永远命中不了**：桌面只 `peek` 从不写。

## 2. ⚠️ 先钉死的一条：键必须同轨

地址档的键是 `(site, name, source)` —— **source 是键的一部分**
（`app/.../gallery/domain/GalleryArtistAvatars.kt:48-49` `lookup` 走 `GalleryArtistCreditsKey.of(...)`）。

- 桌面读侧今天传的是 `source = ""`（`DesktopArtistsPolicy.kt` 的 `desktopArtistFaces`，调用点交空串）。
- **所以写侧也必须用 `""`**：`avatars.remember(site, name, "", url)`。
  否则修完取数还是读不到（就是本缺陷的第二版）。
- 为什么桌面用空串当规范键而不是真出处 URL：桌面数据目录（`%LOCALAPPDATA%\venera`）里
  这一族今天只有列表档，**没有"面板里那位"的语境**；而 `FollowedArtistRef(sites, name, previewUrl, count)`
  根本不带出处 URL（`gallery/domain/GalleryFollowedArtists.kt:23-28`）。
  拿真 URL 当键需要列表里先有它 —— 而"有没有它"正是这条链要去问的。
- ⚠️ 探针对象的 source（fanbox / Mastodon 那个 URL）与**键的 source 是两回事**，别混。
  这条要在代码注释里写一句，并在判据里钉（见 §5 判据 ③）。

## 3. 取数链（每人两笔，顺序固定）

```
danbooruArtistCredits(name)            ← 第 1 笔：拿这位的出处 links
  → GalleryArtistAvatarProbe.fanboxCreator(link.url)   ← 纯函数，gallery/domain，桌面编译面内
  → ?: fanboxCreator(sourceFallback)
  → ?: mastodonLookup(sourceFallback)
  → ?: null（没有可探的端点 ⇒ 直接落字母座，不发第 2 笔）
probeAvatarUrl(endpoint)               ← 第 2 笔：取地址
  → 非空才 avatars.remember(site, name, "", url)
```

- 取用面：`GalleryArtistDirectory`（`gallery/data/GalleryBusinessApi.kt:167-180`），
  桌面实现 `desktop/.../DesktopGalleryArtistDirectory.kt` —— **已接好、不在排除清单里**。
- `pixivAvatarUrl` / `pixivArtworkAuthor` 桌面**刻意不转**（交 `PixivNotWiredOnDesktop`，既有拍板）
  ⇒ 这一族今天只有 fanbox 与 Mastodon 两路，屏上那句不许写成"去站方取头像"这种含混说法。
- 端点构造器是纯函数（`gallery/domain/GalleryArtistAvatarProbe.kt:55,75`），**能真跑取值**，别扫源码。
- fanbox 那一路**必须带 Origin**（既有账，`GalleryArtistProbeClient.kt:21` 的注释写着为什么）——
  端点对象里已经带 `origin` 字段，别在桌面另造请求。

## 4. 视口闸门怎么落

今天 `DesktopArtistsPage.kt:56` 是 `Column(...verticalScroll...)` + `forEach` 摆行 ——
**这种形状拿不到"哪些位在视口里"**。所以：

1. 那一屏改成 `LazyColumn(state = listState)`，每行 `key = { "artist:${it.name}" }`；
2. 可见集从 `listState.layoutInfo.visibleItemsInfo` 现算（它是快照状态，会随滚动自己更新）；
3. `LaunchedEffect(visibleNames)` 只对**没查过**的名字发车；已查过的（含"查过、没有"）
   记在一张 `resolved: MutableStateMap<String, String?>` 里，**滚回来不重发**；
4. 失败与"没有可探端点"都要落 `resolved[name] = null`（否则每次滚动都重试 = 无限发请求）；
5. ⚠️ 首页那一栏（横卡 8 位）**不做现取**：它本来就按 `ROW_SIZE`=8 截断，
   八位全在视口里等于"进来就发 16 笔"，与闸门的精神相反。那一栏继续只 `peek`，
   屏上那句要说明是"这一栏只读本机已有的"。

## 5. 判据（先红一次，TDD）

| # | 钉什么 | 判法 |
|---|---|---|
| ① | `desktopAvatarProbePlan(links, sourceFallback)` 的四种形状：只有 fanbox link / 只有 Mastodon link / 只有 sourceFallback / 三样都没有 ⇒ `null` | **真跑**（纯函数） |
| ② | 有 links 时**优先用 links 里那一条**，不是 fallback | 真跑 |
| ③ | **键同轨**：写侧 `remember(site, name, ""` 与读侧 `peek(site, name, ""` 那一枚参数必须同为空串 | 扫 `DesktopArtistsPage.kt` / `DesktopFollowedArtistsPane.kt` 代码行，两条都判 + 对照组（两个符号都得在） |
| ④ | 视口闸门在位：那一屏必须是 `LazyColumn` 且可见集来自 `visibleItemsInfo`；**整屏 forEach 现取**那种形状不许存在 | 扫源码：`visibleItemsInfo` 在、`forEach { .*probe` 不在 |
| ⑤ | 查过的不重发：`resolved` 那张表必须被读（`name in resolved` 之类），且失败也写表 | 扫源码 + 对照组 |
| ⑥ | 屏上那句已换：旧的「这一屏不主动去站方取头像」不许再出现，新的必须点名"只给看得见的几位去取一次"与"pixiv 这一路桌面没接" | 扫源码（`codeText` 跳注释），**一次列出全部违例**（手册 §23.3 那条规矩） |
| ⑦ | 首页那一栏不许被顺手接上现取 | 扫 `DesktopFollowedArtistsPane.kt`：`probeAvatarUrl` 零命中 |

## 6. 收尾要跑的验证

- `./gradlew :desktop:test`（不许接管道，落文件后读退出码）
- `./gradlew :desktop:packageUberJarForCurrentOS`
- 图证：`./gradlew :desktop:app -PstartDomain=ARTISTS -Pproxy=127.0.0.1:7890` 后台起 →
  窗口捕获（`get_window_state`，`occlusionSafe=true`，核标题里的 `#<pid>`）；
  取证夹具用 `probe_*` 名字、镜像进 `_trash/` 再撤（可逆）。
  ⚠️ 夹具字段要回读那颗 `@Serializable` 类，别照记忆写（手册 §23.6 那条"验过了其实是空态"）。
- Android 侧零改动：`git status --short app/` 必须为空。本批**不新建共享件**，
  只读 `gallery/domain` 里已有的两颗纯函数件 ⇒ 不动冻结面。

## 7. 不在本批

- pixiv 那一路接入（`DesktopGalleryArtistDirectory` 里那两行注释写着怎么换）；
- 头像地址过期与刷新策略（`resolvedAt` 已有的截断档 `MAX_ENTRIES` 够用）；
- 详情面板 P0（另一条线，方案在 `docs/rounds/desktop-detail-panel-plan-2026-10-04.md`，已获批）。

---

## 8. 落地读数与**三处改定了本单的说法**（2026-10-04 收尾回写；全账在手册 §25）

本单已落地：`:desktop:test` **327/0/0（44 颗类）**、`:app:test` **884/0/0（116 颗类）**、uber jar 见手册 §25.6。
判据 ①–⑧ 全部先红后绿，扰动做了四处：③（键换字面量）、⑤（`resolved` 换空表）、⑧（失败也落档）、
原子写那批的 ④⑤（塞回一行 `tmp.renameTo(file)`）—— 每处都红在自己那条，全部已还原。
⚠️ 本单落地时**动了 `app/` 三颗 store**（用户授权，见本节末尾那条 ⚠️），所以"`git status --short app/` 空"
这条本批**不再成立**，提交要按轮拆成两笔。

1. **§3 那条链的第 1 笔换了**：不是 `danbooruArtistCredits(name)`，而是**按站分**的
   `YANDERE → boards.yandere.artistLinks` / 另两站 → `artists.danbooruArtistUrls`
   （与 Android 首页那一栏 `artistAvatarOnSite` 同一张对表）。
   两个理由：credits 多要的那枚 `otherNames` 头像用不上；而 yande.re 的人拿 danbooru 去问 = **问错站**。
2. **§3 那句"非空才 remember"改成"只有定论才 remember"**（判据 ⑧ 六个分支）：
   请求本身失败**不落档**。桌面这一屏的读者会反复进出视口，"失败也算答案"在这边付的代价与 Android 不同 ——
   落一次档就把那位永久判成"没头像"，而屏上"没头像"与"没走通"长成同一个字母座。
3. **端点派生多一档"链接表上的 Mastodon"**，而且是**量出来才加的**：桌面 `source` 恒空串 ⇒
   Android 那三档里 Mastodon 只吃 `source` 的两档在桌面是死路。实测 yande.re 的 `mignon`：
   urls 里只有 `https://www.fanbox.cc/@mignon`（`www` 子域被 `fanboxCreator` 明确拒）与
   `https://pawoo.net/@mignon` ⇒ 不加那一档，这一位在桌面**一颗都取不到**。
   跑完盘上落下 `https://img.pawoo.net/accounts/avatars/000/011/914/original/4a996ef71632bc0c.jpg`。

⚠️ **§6 的图证没拿到**：`-Pshot=` 两次都被自家判式判成无效（中心点落在用户的 Chrome 上，本窗没压过去）。
这一屏今天有的证据是"盘上落址 + 二次启动零请求零重写"两条真进程读数，**没有像素**。

⚠️ **撞出一条跨端的既存缺陷，并已按授权修掉**（手册 §25.5）：
`File.renameTo` 在 Windows **不覆盖已有文件**（jshell 实测 `RENAME_TO_OVER_EXISTING=false`），
所以 `GalleryArtistAvatarStore:83` / `GalleryArtistFollowsStore:113` / `GalleryFavoritesStore:238`
那三处"先写 tmp 再改名"的原子写在桌面**从第二笔起全丢**（Android 侧 POSIX rename 会替换，所以一直没露）。
本批第一次跑就是**两位画师连落**：`wanplus` 的定论写成了，紧接着 `mignon` 的**真地址**改名失败 ——
上面那条"一次只放一位"的绕行就是这么来的（绕行仍然有效，但缺陷已修，不必再绕）。
修法：三颗都换成 `Files.move(…, REPLACE_EXISTING)`；配 `SharedStoreAtomicWriteTest` 五条用例
（三条真跑"连写两笔再读回盘"、两条"回不去"的尺子，其中一条扫整个 `gallery/data`）。
Android 侧全套同步跑过：**884/0/0**。⇒ **本批 `git status --short app/` 不空**（就这三颗），提交时与桌面改动分两笔。

