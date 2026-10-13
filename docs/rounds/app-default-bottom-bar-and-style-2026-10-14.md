# 两枚默认档（液态玻璃底栏 / MD3 取色）现量，与随之撤掉的三处旧说法（2026-10-14）

> 用户点名：「应用默认的导航栏样式就是液态玻璃悬浮底栏，界面风格默认使用 md3 取色」。
> 只动 `app/` 与两份 README；`desktop/**` 一条没碰（那一边现在正被另一路改动占着）。

## 一、先量现值：这两枚**已经是默认**

| 轴 | 磁盘默认 | 出处 | 已有尺子 |
|----|----------|------|----------|
| 界面风格 | `AppearanceStyle.MD3` | `data/prefs/VeneraPreferences.kt:414` | `AppDefaultPreferenceValuesTest.默认界面风格是 MD3` |
| 底栏样式 | `NavigationBarStyle.LIQUID_GLASS` | `data/prefs/VeneraPreferences.kt:419` | `AppDefaultPreferenceValuesTest.默认底栏是液态玻璃悬浮栏` |

两枚都随 `cd0e1bd`（手册行 164「三枚默认档改判」那一批）落地，README 中英两份第 50/163 行也已写着"默认 Material 3"。
**所以本轮没有新的默认值改动**——真正要办的是：那一批改完磁盘默认，屏上与文档还留着三条旧话。

## 二、抓到并改掉的四处旧说法

1. **设置页选项标签指着错的档**。`feature/settings/AppearanceSettings.kt:126` 原为
   `"MD3" to "Miuix 悬浮栏（默认）"` —— 行 164 把默认换成液态玻璃后这句原样留着，
   屏上从此写着一条与磁盘默认相反的断言，而它比 `VeneraPreferences` 那行更接近用户眼睛。
   现改为挂在 `Liquid Glass 液态玻璃（默认）` 上。
2. **两份 README 声称底栏有第三档**。`README.md:165/188` 与 `README.en.md:165/188` 写
   「液态玻璃 / MD3 悬浮 / 实色三选一」，而 `NavigationBarStyle` 只有 `MD3` / `LIQUID_GLASS` 两值
   （`VeneraPreferences.kt:26-28`）。`git log -S "NavigationBarStyle.SOLID" -- app/` **零命中**
   ⇒ 第三档不是被删掉的，是**从来没存在过**。两处都改成"两选一"，英文那份同步。
3. **玻璃那档没写它的生效前提**。折射用 RuntimeShader，要 Android 13（TIRAMISU）以上 ——
   `Navigation.kt:511-512` 就是这个条件，低版本不建 backdrop、退回普通悬浮栏。
   默认既然是玻璃，这条就得在设置页念出来（照那段代码与它的注释写，不是推断），
   并说明**设置值照样保留**。
4. **策略层那条 KDoc 也还写着旧默认**。`components/WideScreenPolicy.kt:112` 的
   「`MD3`（默认）→ 宽窗换侧栏」是 2026-10-12 那一批写的，当时确实成立；
   行 164 之后默认换成玻璃，这句只对着下一个读代码的人说话（不上屏），
   所以改成"这句『默认』已过期 + 玻璃那档标成今天的新默认"，
   并保留原句「与改动前逐像素一致」——那半句仍然成立。

## 三、默认档的作用范围（沿用行 164 那条口径，没变）

`readEnum(key, default)` 只在**盘上从没写过这一项**时给默认。
所以装过旧版且拨过「导航栏样式 / 界面风格」的用户，升级后仍读自己那份旧值——
不迁移、不覆盖。要在真机上确认某台机器读到的是哪个值，只读取证：
`adb shell run-as <包名> cat shared_prefs/venera_preferences.xml` 里找 `pref_navigation_bar_style`
与 `pref_appearance_style` 两个键名（有键=用户设过，无键=吃新默认）。

## 四、判据

`AppDefaultPreferenceValuesTest` 从 6 颗增至 9 颗（同一篇里，不新开文件）：

| 新尺子 | 钉什么 |
|--------|--------|
| `设置页底栏的默认字样挂在真的那一档上` | 扫 `AppearanceSettings.kt` 的代码行，锚点 `Miuix 悬浮栏` **必须只命中一行**（同屏另一处合法命中会把存在性判式喂饱）；反向两断言：不许带 `（默认`、玻璃那档必须带 |
| `底栏选项表与枚举同宽` | 选项表 `to` 计数 == 2，且 `NavigationBarStyle.values().size` == 2 —— 第二半是防"枚举加了档而选项表没跟" |
| `两份 README 的底栏那一行只写两档且默认是液态玻璃` | 逐份按锚点 `**底栏**` / `**bottom bar**`（各只 1 行）取那一行，不许出现「三选一」或 `/ solid`，且必须点名液态玻璃；文件找不到就 `check` 抛错，不静默通过 |

扰动 4 笔（每笔都带身份戳，跑前删旧结果 XML、跑后核 mtime）：

| 笔 | 改动 | 结果 |
|----|------|------|
| P1 | `（默认）` 挪回 Miuix 那档 | 1 红 —— `设置页底栏的默认字样…`，报的就是那条断言 |
| P2+P3 | 撤掉 `（默认）` 并塞进 `"SOLID" to "实色"` 第三档 | **2 红**且各自归对：`底栏选项表与枚举同宽`（条目数 3）+ `设置页底栏的默认字样…`（玻璃档缺 marker） |
| P4 | README.md 写回「/ 实色三选一」 | 1 红 —— 消息里带 `README.md` 与整行原文 |
| 还原 | 三处逐串核对 + `.marker` 残留清理 | 单类 9/0，全量复跑绿 |

## 五、读数

- 单类：`--tests "*AppDefaultPreferenceValuesTest*"` = **9 用例 / 0 失败 / 0 错误**（改前 6 颗也全绿）。
- 全量：`:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` = **BUILD SUCCESSFUL**，
  现量 **133 类 / 1082 用例 / 0 失败 / 0 错误**（上一批 1079 ⇒ +3）。
- 交付：`app/build/outputs/apk/debug/app-universal-debug.apk` **101,736,844 B**、
  `app/build/outputs/apk/release/app-universal-release.apk` **14,713,973 B**；
  两枚 `Signer #1 certificate SHA-256 digest` 都是
  `62b89c491bd6fca41beb25f0baafc80cfad865397302d37694df1fda6a69846b`（CN=venera-miuix）
  ⇒ debug 包仍可直接覆盖官方包，不用卸载重装。
- 包内 dex（`unzip classes*.dex` 后按串计数）：
  「Liquid Glass 液态玻璃（默认）」**1**、「折射要 Android 13 以上的系统能力」**1**、
  被撤的「Miuix 悬浮栏（默认）」**0**、中途那句「图形着色器」**0**、对照组「大图页背景」**1**。
