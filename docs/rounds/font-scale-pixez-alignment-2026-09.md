# 字体口径对齐：从 pixez-miuix 取 miuix 官方字阶（2026-09）

研究对象 <https://github.com/137458/pixez-miuix>（compose 多平台 + miuix 的 Pixiv 客户端）。
目的：查清它的「字体」到底是什么，并把可取之处落到本项目。

## 1. 结论先说：它没有字体文件，它用的是 miuix 官方字阶

证据（本会话实测，不是推断）：

- 仓库 `compose-miuix/` 模块 340 个 .kt 中，**没有任何 `.ttf/.otf/.woff`**；
  全仓库唯一的字体二进制在 `archive/flutter-v1/assets/fonts/iconfont.ttf`（1.8KB 图标字，旧 Flutter 版残留）。
- 340 个 .kt 里 `FontFamily` 只出现 1 次：
  `shared/src/commonMain/.../ui/components/MarkdownText.kt:227` → `fontFamily = FontFamily.Monospace`（代码块用等宽）。
- `composeApp/src/androidMain/res/values/themes.xml` 不设 `android:fontFamily`，
  postSplashScreenTheme 直接吃 `@android:style/Theme.Material.NoActionBar`。
- 主题入口 `ui/navigation/RootContent.kt:190` 只写 `MiuixTheme(controller = themeController)`，
  **不传 textStyles**，即用库默认。

所以它的字型（typeface）就是**宿主系统默认 sans-serif**。

对照本项目：`app/src/main` 下同样零字体文件，`res/` 与 Manifest 同样不声明 fontFamily。
→ **字型这一层两边本来就一致，没有东西可装、可换、可引入。**（一加 PJZ110 上两边都落到 OEM 默认字体。）

真正有差别的是**字号阶梯的取值方式**。

## 2. 它怎么「用」字体：全部走 `MiuixTheme.textStyles.<角色>`

按调用点计数（`grep -rhoE "MiuixTheme\.textStyles\.[A-Za-z0-9_]+"`）：

```
54 body2   46 footnote1   26 body1   13 footnote2   12 title4
 8 title3    5 title2      4 headline2  2 headline1  1 title1  1 subtitle  1 button
```

组件层也一致：85 个文件 `import top.yukonga.miuix.kmp.basic.Text`，**material3 Text 零次**。
miuix `basic/Text.kt` 的默认值是 `style = LocalTextStyles.current.main`（17.sp），不传就走它。

这套做法的效果：**字号永远跟随库，不靠人记数字**，所以不会出现本项目这种「自己写一版 MIUIX 阶梯、
结果一半数字库里没有」的漂移。

## 3. miuix 0.9.4-rc01 官方字阶原值（本项目同版本）

来源：`~/.gradle/caches/.../miuix-ui-android-0.9.4-rc01-sources.jar` →
`commonMain/top/yukonga/miuix/kmp/theme/TextStyles.kt`（逐行读，非猜）。

| 角色 | 官方值 | 备注 |
|---|---|---|
| main | 17.sp | |
| paragraph | 17.sp | 唯一带 `lineHeight = 1.2f.em` 的角色 |
| body1 / headline2 | 16.sp | |
| body2 | 14.sp | |
| button / headline1 | 17.sp | |
| footnote1 | 13.sp | |
| footnote2 | 11.sp | 全局最小值 |
| subtitle | 14.sp **Bold** | 层级靠字重不靠字号 |
| title1 / title2 / title3 / title4 | 32 / 24 / 20 / 18.sp | |

官方尺寸全集 = **{11, 13, 14, 16, 17, 18, 20, 24, 32}**。库内不出现 10/12/15/22/30。
`TextStyles` 里**不含任何 fontFamily**，进一步印证第 1 节：字型交给平台。

## 4. 本项目 MIUIX 阶梯的偏差与处置

`app/src/main/java/com/venera/compose/ui/tokens/Typography.kt` 的 `MiuixTypography`（当前磁盘值，
经 `git status` 确认与 HEAD 一致、mtime 22:34），10 个角色里 **4 个取值不在官方集合内**：

| 角色 | 磁盘原值 | 处置 | 官方依据 |
|---|---|---|---|
| screenTitle | 24 | 不变 | title2 = 24 |
| itemTitle | 17 | 不变 | main = 17 |
| **body** | **15** | → **14** | body2 = 14 |
| caption | 13 | 不变 | footnote1 = 13 ✔ 已是官方值 |
| **overline** | **12** | → **11** | footnote2 = 11（库内最小档，无 12） |
| sectionTitle | 14 | 不变 | body2 = 14 |
| chevron | 16 | 不变 | body1 = 16 |
| **display** | **30** | → **32** | title1 = 32 |
| **statNumber** | **22** | → **20** | title3 = 20 |
| badge | 11 | 不变 | footnote2 = 11 ✔ 已是官方值 |

即：本轮只动 4 个数（15→14、12→11、30→32、22→20），其余 6 个本来就落在官方集合里。

`caption` 与 `overline`/`badge` 的取法说明（避免下次重开）：
`caption` 在本项目承担 113 处次要说明文字，角色对应 miuix 自己的 footnote1（它同样用 46 处）；
`overline` 与 `badge` 同归 footnote2（11），因为库内没有第三个小号档。
**body 14 与 caption 13 只差 1sp 并非失误**，而是 miuix 本层就只有 body2 14 与 footnote1 13 两档，
pixez 也是两档并用；层级由 `weightMedium/weightBold` 拉开，
与官方 subtitle（14 + Bold）的做法同构。

默认外观是 MIUIX（`data/prefs/VeneraPreferences.kt:99`，`readEnum(KEY_APPEARANCE_STYLE, AppearanceStyle.MIUIX)`），
所以这一档改动在真机上是**直接可见**的。

## 5. 本次不做、留作拍板的两项

1. **MD3 阶梯不动。** 本轮研究对象是 miuix 口径，MD3 分支（`Md3Typography`）里
   sectionTitle 13 / chevron 18 / statNumber 20 / badge 10 同样不是 Material3 官方值，
   但那要按 Material3 的 scale 单独核一遍，不与本批混做。
2. **不做「调用点全量迁到 `MiuixTheme.textStyles`」（pixez 式机制对齐）。** 现有 token 调用点约 380 处，
   另有 **150 处裸 `.sp` 绕过 token**（`grep "[0-9]\+\.sp"` 排除 tokens 目录后计数，A3.1 规则事实上已破；
   热点：StatsScreen 22 / FollowUpdatesScreen 16 / ComicDetailScreen 14）。
   真按 pixez 做法迁移会顺带逼着这 150 处一起收口，属大范围改动，需单独授权。
