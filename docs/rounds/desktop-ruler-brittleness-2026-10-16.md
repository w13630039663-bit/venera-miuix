# 第一百一十五批（桌面侧量具两处脆性）· 字节窗与行尾把人自己绊了一跤

日期：2026-10-16 ｜ 前置：批 113（rounds `desktop-shelf-reroll-2026-10-15.md`）
本篇只动 `desktop/src/test`，**`app/` 一颗不碰**（用户正在改那一侧）

## 0. 症状与归因（逐条现读，不靠猜）

桌面全量从 **2 失败**变 **4 失败**，本鱼只认领**新出现的那两枚**（另两枚是长期挂账的"回核 `app/`"对照尺子）：

| 红的那颗 | 念出来的话 | 真成因（现读） | 是不是缺陷 |
| --- | --- | --- | --- |
| `DesktopBlurCostProbeTest :: 步长得调大做正对照` | 构建脚本那一跳没把 step 传下去（三环的第三环） | 判式取 `substringAfter(锚点).take(700)` 的**字节窗**；`providers.gradleProperty("step")` 起于块内偏移 **688**，而那串字面量长 **22** ⇒ 窗尾切在 `providers.gradleProper` 中间（`desktop/build.gradle.kts:299` 现读那一行**一字没动**） | **量具坏**（并行入账给那块加了两条注释，把 `step` 推出窗外） |
| `DesktopComicLoadInfoTest :: cover 那一栏的键名回核 Android 侧` | 对岸详情那一段不再用 `cover` 这一栏了 ⇒ 桌面这一条要跟着改判 | 对岸 `app/…/source/js/JsComicSource.kt` 现在**整文件 CRLF**（1918 行 / 1918 个 CR），而这把尺子的正则写死 `"""\n\s*val desc` ⇒ 夹不到。归一后立刻匹配：`:464` 那格仍紧接 `:465` 的 `val desc` | **量具坏**（行尾敏感假红；语义一字未漂） |

同一次全量里还有**第三簇**，与本批无关、也**不归本批修**：5 枚红全在画廊 pixiv 那一路（`DesktopArtistProfileTest`、
`DesktopForYouPlanTest` ×2、`DesktopLatestTest`、`DesktopSearchHistoryTest`），念的都是"pixiv 该进轴 / 取数还没接线"，
而 `desktop/src/main/…/DesktopArtistsPolicy.kt` 与 `DesktopGallerySearchPane.kt` 此刻是 **未提交的"正在改"状态**
⇒ 那是并行那条线的半成品，本批**不碰**（碰了就是两双手互相踩，见手册 §9 并行会话那两条硬边界）。

## 1. 定档与修法（两把都只改量具，判据一字不减）

- **字节窗 → 语义单元 + 逐档点名**：改成"取到那颗任务闭包的结尾（顶格 `}`）"，再把四枚把手属性
  `hold` / `step` / `overlay` / `rounds` **各自点名一遍**。
  这一改**比原来更硬**：从前少传 `overlay` 或 `rounds` 没人管，现在少一枚就红；而注释怎么加都不会再截断。
- **回核前行尾归一**：`readText().replace("\r\n", "\n")` 再套那条正则。钉的还是同一件事（详情那一段读完 `cover`
  紧接着读 `desc`），只是不再把"文件用哪种行尾"当判据的一部分 —— 行尾属于编辑器/入账，不属于语义。

## 2. 改完之后的"这把尺子还咬得住吗"（不许只跑绿就交）

- ① 那一把：**做了真笔**。把 `desktop/build.gradle.kts` 里 `providers.gradleProperty("step").orNull?.let { "step=$it" },`
  那一行整行摘掉 ⇒ 该类 `failures="1"`（红，且红在该条判式）；随后逐字节还原（改前后 `sha256` 前缀同为 `af7a95f60223f3f9`）。
- ② 那一把：**没动 `app/`**（那是受保域且用户正在改），改做"负向自证"：把归一后的原文里 `val cover = res["cover"]` 换成
  `res["coverUrl"]` ⇒ 正则不再匹配 ⇒ 这条尺子对**键名**仍然敏感，不是被归一改成了恒真。
  ⚠️ 一处已知的软：这条正则只钉"至少一处相邻"，不钉"恰一处"——本次没改它（改它会牵动对岸语义的判断口径），留作记录。

- ⚠️ **本批没有起台架**（`_qa/b115_perturb.js` 不存在，明写在账上不糊过去）：两把尺子各配一枚证据 ——
  ① 是一笔真摘行（摘 ⇒ 该类红 ⇒ 逐字节还原并核 sha），② 是负向自证（被钉的键名换掉 ⇒ 正则不再匹配；那一侧是受保域，不许动）。
  只改量具不改进为时，"一笔真摘行 + 一枚负向自证"就是这一格的证明强度；要更硬的下一步是给"字节窗"这一形立一枚全仓扫描尺子
  （形状：`take(<数字>)` 之后紧跟 `.contains(`）—— 今天全仓有几处这类窗**没量过**，别假装只有这两把。

## 3. 回归读数

- 那两枚修好后各跑该类：`DesktopBlurCostProbeTest tests=24 failures=0`、`DesktopComicLoadInfoTest tests=9 failures=0`（EXIT=0）。
- 全量（`:desktop:cleanTest :desktop:test`）：`suites=171 tests_sum=1484 @Test=1484 files=179 failures=7`
  ⇒ 7 = 本批**不再贡献**红（0）+ 长期挂账 2 + 并行 pixiv 那一路 5。
- ⚠️ 记账一条纪律级读数：**第一次跑"全量"时 `:desktop:compileKotlin FAILED`（9 秒）而结果目录只剩 1 颗类**——
  那不是我这批改坏（我只动 `desktop/src/test`），而是并行那位当下正在改 main。⇒ **"全量"必须核它到底跑没跑**：
  `three_way_counts.js` 念 `suites=1` 就是证据，旧 XML 与半途 XML 都不算读数（本仓那条老规矩的又一形）。
