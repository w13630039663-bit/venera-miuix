# 批 78 · 词典读不出不再算成「这批名字里没有」（2026-10-09）

**一句话**：离线词典那三枚生产者，从「读不出 → null → 契约口压成空集，全程一个字不念」改成
**三态各归各的、降级必出声、屏上那句跟着念是哪一态**；顺带把代价探针归属行那句只念生效值的
`轮次=8` 补成 `轮次=8（请求=夹过（请求=12→8））` 那一形（§1 最后那一格）。

## 1. 这一格从前缺的是哪一笔

批 40 立的形状是「三枚读口交可空，`null` = 我们这侧没读到，空表 = 这批名字里确实没有」，
听起来已经把两件事分开了，实际**分在了错的那一层**：

| 层 | 从前的写法 | 压掉了什么 |
|---|---|---|
| `DesktopTagDictionaryAsset.classpathNames()` | 枚举失败 `.getOrNull()` → `?: emptyList()` | 「我们数不出来」被喂给下游当成「清单是空的 = 包里没带」 |
| `DesktopGalleryTagDictionary.query()` | 库没打开与查询抛了都交 `null` | 「包里没带」与「带了而读不动」同一句 |
| `DesktopGalleryTagLexicon` 两枚契约口 | `… ?: emptySet()` / `… ?: emptyMap()` | 恒空返回**不出声**，日志里一个字都没有 |
| `desktopArtistLineNotice` 的 `NotAnswered` | 只有一句「离线词典里也没有这一串标签」 | 词典根本没被读过时，这是一句没根据的声称 |

四格合起来的表现：词典坏掉时屏上与日志里全都写着「这些图没译名 / 词典里也没有」，
而真相可能是「包残缺」或「盘上那颗读不动」—— 本仓最忌的**静默交错**换了个更礼貌的说法。

## 2. 契约不在改动面上（受保域一个字都没动）

`GalleryTagLexicon` 那三枚的返回类型写在 `app/src/main/java/com/venera/compose/gallery/data/GalleryBusinessApi.kt:64`
（`translations` 交非空 `Map`、`artistNames` 交非空 `Set`、`titleTags` 交可空）。那是受保域，本批**零改动**
（`git status --porcelain -- app/` = 0 行），判据 ⑨ 逐字钉着那三行签名。

于是三态只能住在桌面自持的那一侧，形状是：

```kotlin
internal sealed interface DesktopDictRead<out T> {
    data class Hit<out T>(val value: T) : DesktopDictRead<T>          // 读到了（value 可以是空集）
    data class Miss(val why: DesktopDictWhy) : DesktopDictRead<Nothing>
}
internal sealed interface DesktopDictWhy {
    data object Absent : DesktopDictWhy                              // 包里没带
    data class Unreadable(val error: Throwable) : DesktopDictWhy     // 有而读不动，带着那一个异常
}
```

`Miss : DesktopDictRead<Nothing>`（协变）是有意的：中间那一层（`query()` 拿到 `Miss` 之后）
原样交回而不用重造类型 —— 「把失败重造成另一种失败」正是这一批要禁的动作。

## 3. 三处落点

- **资产口**：`classpathNames()` → `classpathListing(): DesktopDictRead<List<String>>`。
  锚点不在 / `jar` 读不动 / 目录列不出 / 协议不认 ⇒ 全部交 `Miss(Unreadable(带路径的那句))`；
  枚举成功（哪怕清单是空的）⇒ `Hit`。新增纯那一形 `assetOf(listing)`：**没有**（`Absent`）与
  **数不出**（`Unreadable`）在这里分岔，判据 ③ 逐格喂。`presence()` 拆成
  `presence(listing)` 纯映射 + 无参那枚取真清单（批 58 那句 `D_能力 … 词典=` 的三态从此才真是三态：
  从前 `Unreadable` 那一格在枚举失败时**永远走不到**）。
- **词典口**：三枚成员一律交 `DesktopDictRead`；`denied` 记住第一次没读到的**是哪一态**（原来只有
  一枚 `openFailed` 布尔），之后每次原样交回同一态而不重试到无限。
- **契约口**：`Hit` 直交；`Miss` 那一支**先念一句再降级** ——
  `D_词典 枚=译名|画师档|标题档 态=包里没有` / `… 态=读失败 型=<异常类型> 因=<压成一行并打过码的那句>`，
  走 `DesktopGalleryFailureLog`（首次出声、后续「同一句已第 N 次」），所以墙上刷几百次也不会把这句埋成噪声。
  另加桌面自持的一枚 `artistNamesRead()`（不进契约）交回**整个三态**，面板据此把兜底那句念成能支持的那一句。
- **屏上那句**：`desktopArtistLineNotice` 多收一枚 `dictionary: DesktopDictWhy?`（`null` = 读过而没命中），
  `NotAnswered` 那一分岔出三句；`Unlabeled` 与两形 `Names` 逐字不变。

## 4. 探针那句轮次读数

`BlurCostProbe.kt`：`rounds` 的判读抽成纯函数 `roundsRequestReading(raw)`，归属行念
`轮次=<生效值>（请求=<原样=3 | 夹过（请求=12→8） | 读不出（请求=abc） | 未给（默认 1）>）`。
判据 ㉔ 四格各喂一种并钉「四句各异」—— 留在 `main()` 里就没有尺子（手册 §0 那一条）。

## 5. 十条尺子与十笔扰动

新颗 `DesktopDictionaryThreeStateTest.kt`（9 条）：① 真包里的清单交 `Hit` 而 `presence` 落 `InPackage`；
② `presence(listing)` 三格各一种输入（含「认不出行数的资产名」那一格）；③ `assetOf` 三态各一种输入；
④ **负向**：那颗文件里不许再出现 `?: emptyList()` / `?: emptyArray()` / `.getOrNull()`；
⑤ 三枚读口真跑各交一次命中（且命中的空与非空分开）；⑥ 库读不动 ⇒ `Miss(Unreadable)` 且成因点名落点；
⑦ 契约口三枚降级各念一声（捕 stdout）；⑧ 措辞单源 + 三态三句各异 + 凭据不落纸（`api_key=***`）；
⑨ 契约那三行签名逐字不动 + 桌面侧不许留 elvis 合并 + 出声恰三处。
`DesktopArtistFallbackTest.kt` +2（⑧ 那一句分三态、⑨ 面板取到态并递进去：次数 + 次序 + 对照组）；
`DesktopBlurCostProbeTest.kt` +1（㉔）。

扰动台架 `_qa/b78_perturb.js`（10 笔，跑法 `sh _qa/perturb_driver.sh b78 10`）：
P1 枚举失败退成空清单 / P2 数不出并进「包里没有」/ P3 没带并进读失败 / P4 译名降级不出声 /
P5 措辞抄第二份 / P6 兜底三句并回一句 / P7 面板取了态而不递 / P8 轮次不念请求值 /
P9 把夹过的当原样 / P10 失败原话不打码。

## 6. 两条工具坑（本批自己踩的）

- **`?.let` / elvis 那种「类型看着对而智能转换不进去」**：`asset` 在 `if (asset is Miss) return asset`
  之后于 `runCatching { …asset.value… }` 里读 `.value` 报 Unresolved —— 智能转换不越过闭包。
  正解是把 `Miss` 先取成局部 val（`val miss = listed as? DesktopDictRead.Miss`），命中那一支显式
  `(listed as DesktopDictRead.Hit).value`，别指望编译器替我记得。
- **public 件吃 internal 类型**：`DesktopDictRead` 是 internal，三枚成员与 `classpathListing()`
  所在那颗类/对象就得一起退成 internal（Kotlin 的『public function exposes internal return type』是
  编译错误而不是警告）；桌面测试源集吃得到 internal（Gradle 的 friend 机制），所以三态没逼着把契约改宽。

## 7. 量到的读数（收尾现数）

- 判据三方对数：`suites=105 tests_sum=838 testcase=838 @Test=838 files=112 failures=0`
  （批 77 · 下半是 826 / 104 ⇒ 本批 +12 条 / +1 颗：新颗 9 条 + 画师兜底那颗 +2 + 代价探针那颗 +1）。
- 判据目录对账：`node _qa/check_ruler_catalog.js` → `表行=105 表合计=838；XML 类=105 XML 合计=838 对得上：0 处漂移`。
- 扰动台架：`sh _qa/perturb_driver.sh b78 10` → **10/10 各红在正主上**，逐笔还原，
  还原后全量复跑 `EXIT=0`、仍 838 / 105 / 0，六颗被改过的源码事后 grep 无残留。
  ⚠️ 附带一条没预料到的账：P4（译名降级不出声）与 P5（措辞抄第二份）除了咬住本批的新尺子，
  还当场让「桌面 main 里零调用的 internal 件」那把**机械**尺子红起来 ——
  常量 `DESKTOP_DICT_MEMBER_*` 只被那句出声用到，出声一撤它就成为孤儿。那把尺子是批 27 那族做成的，
  本批它是替我干活而不是被绕过。
- `app/` 受保域：`git status --porcelain -- app/` = 0 行。
- 交付包 `_qa/desktop-exe-2026-10-09-b78/`：`sync_pkg.js` 报 `copied=3 moved_to_trash=2`（两枚改名的 jar
  是**移到** `_qa/_trash_b78/` 不是删掉）；`check_cfg.js` 报 `classpath=100 missing=0 orphan_jars=0`；
  模块 jar `desktop-a6b4cb88d4079641ca8efc6c83f6f99.jar` = **4,366,579 B**，`unzip -l` 量到
  `DesktopDictRead$Hit` / `$Miss` / `DesktopDictWhy$Absent` / `$Unreadable` 等六颗新类真在包里。
- 图证那一跑见 §8（改判与残留数一并记在那里）。

## 8. 图证：把包里那份词典换成一颗读不动的

单测能证明三态各归各的，证不了**真包在真盘上**降级时屏上与日志里各是什么。所以起窗那一跑刻意不做"健康的
词典"那一格（那一格批 40 到批 77 一直有读数），而是给一个**外部状态**：数据根 `_qa/b78_local` 里预先摆一颗
50 字节的假 `venera/db/gallery_tags_79415.sqlite`。`materialize` 的口径是"目标已存在就原样交回、不重复制"，
于是那颗假文件被原样只读打开 ⇒ SQLite 当场拒（`file is not a database`）——
这正是"包里有货而盘上那颗读不动"的真实形状。

那一跑（PID 5788，帧 `_qa/b78_frame_dict_degraded.png`，923,468 B）stdout 念的三行是这一批的正读数：

```
D_能力 剪贴板=有通路 词典=79415行
D_警告 [DesktopGalleryTagDictionary] 查画廊标签词典失败：查询失败：SELECT … FROM tags WHERE name IN (?,…)：
    [SQLITE_NOTADB] File opened that is not a database file (file is not a database)（同一句已第 2 次）
D_警告 [DesktopGalleryTagLexicon] D_词典 枚=译名 态=读失败 型=IllegalStateException 因=查询失败：…（同上）
```

**同一份日志里两件事第一次分得开**：`词典=79415行` 说的是**包里带了货**（classpath 那一层，与盘上那颗无关），
`态=读失败` 说的是**盘上那颗读不动**。从前只有前者会念（后者一个字都没有），而屏上的表现是"这些标签就是没译名"。
帧上那一排标签（`animal_ears` / `bunny_girl` / `thighhighs` …）确实全是原词，与读数一致；
底部那句"有译名的摆成「译名 (原名)」"仍在（它说的是口径，不是"这批一定有译名"，所以不算假话）。

⚠️ **那句「离线词典这次没读出来」不在第 3 跑里**：它是 `NotAnswered` 那一档的分支，要一张**站方那一页
没答上标签分类**的卡才摆得出来，而第 3 跑落在 yande.re 那张卡上、站方答上了 ⇒ 屏上走的是另一档。
第 4 跑（`--start-domain=SEARCH --query=1boy --detail=1 --viewer=0`，PID 20592，
帧 `_qa/b78_frame_notanswered.png`，1,005,417 B）撞到了：stdout 先念
`D_日志 [GalleryTagCategories] 取 https://safebooru.donmai.us/posts/12346240 的标签分类失败，退到离线词典兜底：
分类页没有可识别的 tag-type 类名`，屏上那一栏随后念的就是新那一句 ——

> 画师这一栏这次没取到：Safebooru 那一页没答上标签分类，**离线词典这次没读出来**

同一帧里还能核到两件事：那一排标签全是原词（`1boy` / `azrael_(fate)` / `braid` …，没有一枚带「译名 (原名)」），
以及批 77 下半那枚「用本机的一张图找」仍在原位没被这次改动挤掉。
旧那一句「离线词典里也没有这一串标签」在这一格**不再出现** —— 那正是本批要下架的声称（我们没查过）。

## 9. 三条工具坑（本批自己踩的，都进手册 §0 那一族）

- **`.ps1` 里写中文注释会炸解析器，而且炸得"像逻辑错了"**：`_qa/b78_kill.ps1` 第一版是纯 ASCII、
  按命令行匹配 ⇒ 它把**我自己的几颗 bash shell** 一起杀了（后台任务当场 failed）。
  改成按 `Name` 收口时顺手加了两行中文注释，脚本再跑就又开始匹配 bash.exe ——
  真因不是那句 `Where-Object`，是 PS 5.1 把无 BOM 的 UTF-8 当 GBK 读，中文注释把解析吃歪了。
  正解：`.ps1` 一律 ASCII-only（这条本仓早就记过，本批是**在改脚本时又忘了一次**）。
- **参数没到手时，屏上会替你说一句假话**：第一版 `_qa/b78_run.ps1` 把参数交给 launcher 的方式没生效
  （`& $launcher $argList`），而 launcher 的默认种子（`--detail=1 --viewer=1`）就原样留着 ⇒
  那一跑"查看器开着"被我读成"产品行为"。现在那颗脚本先把**交出去的参数列表**写进 `<log>.args.txt`，
  再看 launcher 自己那句 `Startup seed …` 回显 —— 两行对上才算"参数真到了"。
- **智能转换不越过闭包 / public 吃 internal 返回类型**：见 §6。
