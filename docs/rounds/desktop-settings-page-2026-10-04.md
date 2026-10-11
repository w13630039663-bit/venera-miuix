# 桌面第七批：设置页写侧 + 12 枚偏好的台账（2026-10-04）

范围：`desktop/`。**Android 侧零改动**（`git status --short app/` 空）。分支 `compose-migration`，未提交。

判据 266 → **276**（`:desktop:test` 276/0/0 实跑，35 颗类）。

---

## 1. 这批做什么

第四批量出的前提（手册 §18.1）：桌面那 12 枚画廊偏好几乎零消费，所以"做设置页"等于摆一屏假开关。
第五批补掉三枚消费点，第六批把搜索域接进无人值守取证。这批是那条链的最后一环：

- **写侧**：`DesktopGalleryPreferences` 长出四道写口（`setGalleryColumnMode` /
  `setGalleryPreviewQuality` / `setGalleryBlockAi` / `setGalleryAiBadge`），每道都是**落盘 + 推流**两步。
- **那一屏**：`DesktopGallerySettingsPane` 四行 —— 两枚档位用单选、两枚开关用 `Switcher`，
  改完那面墙立刻重算，没有"保存"这一步。
- **`SETTINGS` 域转正**：从三行坦白页变成一行真页面。七枚导航域现在是 **5 枚真能进 + 2 枚坦白页**
  （剩「发现」「画师」）。
- **台账**：`DesktopGallerySettingsSpec` 一份纯数据清单，把 12 枚每一枚的下落记死。

## 2. 这批唯一的设计约束：让"假开关"在结构上长不出来

设置页最容易做的坏样子是"摆一排控件，其中几颗按下去什么都不发生"。防它不靠"我记得只摆了能用的"，
靠一份**每一枚都要占一格**的账：

| 归处 | 枚数 | 屏上表现 |
|---|---|---|
| `editable` | 4 | 真能改，且改完立刻被那面墙读走 |
| `notOnScreen` | 6 | 逐颗点名「桌面没有大图页，这一枚今天没有对象可改」 |
| `STRUCTURAL_ABSENT` | 2 | 连点名的地方都没有（桌面走侧栏，没有那两条栏可收） |

**4 + 6 + 2 = 12，且这 12 个名字必须就是 `GalleryPreferences` 接口上抽出来的那 12 个**（③ 那条加法）。
少一枚、重一枚、把缺席的说成能改的，都会红。那六枚是**屏上念出来的**而不是干脆不提 ——
不提就等于"看起来这一屏把画廊设置管全了"，那是另一种假。

## 3. 屏上那一行与写口之间的那条线（⑥，这批最要紧的一条）

已有的判据各管一头：`DesktopGalleryPreferencesTest` ⑩ 管"只有那四枚有写口"，
Spec ①②③ 管"账没漏"。**中间那一段此前没人管** —— 完全可以做出这样一屏：四行都摆齐了、
账也平，但 `onClick` 里没调那道写口，按下去什么都不发生，而所有判据照样全绿。

所以 `Editable` 多记一格 `setter`（这一行按下去要调的那道写口），⑥ 拿它回核三样：

1. 写口在 `DesktopGalleryPreferences` 上**真存在**且是一元方法；
2. 写口收的值类型 **就是** `pref` 那枚流吐的类型（反射读泛型实参，原始类型先折算成包装类型 ——
   `StateFlow<Boolean>` 读到的是 `java.lang.Boolean` 而写口收原始 `boolean`，不对齐会把对的说成错的）；
3. 这一屏的代码里**真调着** `settings.<setter>(`。

扰动实验：把 `galleryBlockAi` 的 `setter` 指到 `setGalleryColumnMode` ⇒ ⑥ 红在
「写口收 class GalleryColumnMode，而 galleryBlockAi 吐 class java.lang.Boolean —— 这两头不是同一枚档」。
两枚布尔档互相指过去**不会**红（类型相同、且屏上确实调着那两个名字）—— 这是这条判据的已知边界，
真机上一按才知道，而按鼠标是禁的（手册 §8）。

## 4. compose-fluent 那两枚控件的参数名（17 处编译错换来的）

桌面用的是 compose-fluent v0.1.0，它的控件**不叫 Material 那套名字**，而且两枚之间不对称：

| 控件 | 真实参数名 | 猜错的名字 |
|---|---|---|
| `RadioButton` | `selected` / `onClick` / `label` | `checked` / trailing lambda |
| `Switcher` | `checked` / `onCheckStateChange` | `onCheckedChange` |

仲裁者不是我的记忆也不是 README，是 **`javap -v` 里的 `d2` 字符串数组**（Kotlin 元数据把参数名表
存在那里）。命令与读法已写进手册 §16.2。

## 5. 判据 +10

| 类 | 变化 | 新增钉什么 |
|---|---|---|
| `DesktopGallerySettingsSpecTest` | 新建 **6** | ① 四枚键名逐枚回核偏好件（对照组：不存在的键名不许命中）② 六枚缺席逐枚回核接口 ③ 那条加法 4+6+2=12 且等于接口抽出的 12 枚 ④ 缺席措辞不许是时间表 ⑤ 设置域转正且只有一行 ⑥ §3 那条线 |
| `DesktopGalleryPreferencesTest` | 7 → **10** | ⑧ 同实例写完立刻读到新值 ⑨ 换新实例从盘上重开还在（这两条把"落盘 + 推流"两步分别钉住）⑩ 反射：**只有那四枚**能有 `setGallery*`，`hide_*` 两枚不许有 |
| `DesktopPageChromeTest` | +1 | 「界面文案不许带 markdown 痕迹」—— 扫 `gallery/ui/` **整个目录**的代码行，星号强调与反引号包词都算违例 |

两条既有守卫因 SETTINGS 转正而**改判据**（不是改实现）：域缺席数 3 → 2、`isWired` 数 4 → 5，
用例名从「四域真能进」改成「图库 漫画 收藏 搜索 设置五域真能进」。

## 6. 图证（窗口捕获，`occlusionSafe=true`）

取证条件：`-PstartDomain=SETTINGS` 起窗（第六批那颗参数第一次用在设置域上），
本机 prefs 文件写 `{"pref_gallery_columns":"TWO","pref_gallery_block_ai":true,"pref_gallery_ai_badge":false}`。

屏上读数逐一对上：两列 selected（自适应/三列未选）、站方小图 selected（预览档没写所以是默认）、
屏蔽 AI 那把 on、AI 角标那把 off、六枚缺席各带一句"差的是哪一层"。
**这一张同时是"从盘上读回"的证据** —— 那三枚值是启动时从 prefs 文件读出来的，不是内存里现写的。

上一张（同域、修串之前）副标题画的是字面星号：`这一屏只管**墙上**那四枚档`。

## 7. 四条过程读数（比这批本身更值钱）

1. **界面串里漏进写作记号，是一类缺陷而不是笔误**。`Text` 不解析 markdown，所以星号与反引号会
   **原样摆在屏上**。这批从截图里抓到第一条，加判据时扫整目录又抓到三条同类：
   `` `.domain` ``、`` `gallery_favorites.json` ``、`` `-` 开头是排除 `` —— 都是我自己写的。
2. **名单式判据会把红花在拼写上**。那颗 markdown 判据最初列了 7 个文件名，其中
   `DesktopPageChrome.kt` **根本不存在**，`check(isFile)` 直接抛 —— 红得没有信息量，
   而且那份名单盖不住没列进去的文件。改成 `DesktopSourceTree.desktopUiSources()` 扫整目录后，
   它立刻抓出名单外的三条。新增的 `desktopUiSources()` 与旧的 `desktopUiSource(name)` 分工：
   点名某一颗用后者，管"这一片都不许怎样"用前者。
3. **`node` 的字符串替换只改第一个匹配，值会重复出现时能把回滚做成第二次破坏**。
   §3 那次扰动的回滚把 `galleryColumnMode` 行的写口改错了（因为扰动之后
   `setter = "setGalleryColumnMode",` 在文件里出现两次），全跑一遍才发现 1 红。
   **扰动与回滚都要带上下文的多行串**（这次是 `pref =` + `key =` + `setter =` 三行），
   或者直接用 Edit 工具。已复绿并核过四行配对。
4. **台账类判据要写成"能跑的加法"而不是文本扫描**。③ 那条不是"数一下注释里写了几枚"，
   而是从对岸接口正则抽出 12 个成员名与账上三份求并集比对 —— 接口加了第 13 枚而桌面没登记，
   它会红；桌面自己多登记一枚不存在的，它也会红（两组对照组各自钉着）。

## 8. 下一批的输入

1. **`addRule` 仍零调用点**（手册 §20.1.1）。桌面屏蔽规则今天只能靠 prefs 那把总开关，
   逐条规则没有入口 —— 与设置页同一族，屏上该念一句"这一屏没有逐条规则"而不是不提。
2. **「发现」「画师」两域**：各自的 `wired` 清单在 §5 那批就核过，差的只是那一屏。
3. `galleryAnimated` 桌面给 `ALWAYS` 是登记过的刻意分档，等桌面大图页（P5）时重新判一次。
4. `-Pshot` 那一环仍拿不到 PNG（两类反例，手册 §16.4）；看屏一律走窗口捕获。
