# 调色板控制取色（界面风格内）方案 · 2026-09-22

用户原话：「设置一个调色板，来控制壁纸的取色，放在界面风格里，选择自定义取色后展开如图所示的取色选择」。
参考截图三张：设置页折叠态（主题颜色来源 / 自定义主题颜色 #FA7298 / 高级配色 / 色彩风格 TonalSpot /
色彩标准 SPEC_2025 / 主题色：樱花粉 展开）、预设色板网格（5 列圆形渐变块 + 中文名 + 选中打勾）、
色彩风格下拉（TonalSpot / Neutral / Vibrant / Expressive / Rainbow / FruitSalad / Monochrome / Fidelity）。

## 一、本仓现状（已读代码，非推测）

- `feature/VeneraTheme.kt:57-65`：色板只有三条分支 ——
  `MIUIX` → miuix 原生 `light/darkColorScheme()`；`MD3` 且 Android 12+ → `dynamicDark/LightColorScheme(context)`
  （**系统壁纸取色，唯一入口**）；`MD3` 且 12 以下 → material3 静态色板。
  随后 `materialColors.toMiuixColors(nativeMiuix)` 把同一份色板喂给 Miuix 控件，所以**改种子色 = 两套控件一起改**。
- `feature/settings/AppearanceSettings.kt:55-68`：「界面风格」组现在只有一个二选一下拉
  （Miuix / MD3 · 壁纸动态取色）+ 一段说明文字。**新 UI 就挂在这个组里。**
- `data/prefs/VeneraPreferences.kt:109` 起是「外观与主题」偏好的既有落点。
- 依赖面：`gradle/libs.versions.toml` 里**没有任何种子色→色板生成库**。
  Android 自带的 `DynamicColors` 只能吃壁纸，不能喂自定义种子。

## 二、必须引入的东西

自定义种子要生成完整 MD3 色板，只有两条路：
1. **MaterialKolor**（`jordond/MaterialKolor`）—— 现成的 `DynamicScheme(seed, dark, variant, spec)`。
   截图里那八个 `Variant` 名字与 `SPEC_2025` **就是它的枚举**，说明参考 app 也用它，口径能对齐。
2. 自己接 `material-color-utilities` 手搭 Scheme —— 工作量与出错面都更大，不推荐。

→ 引第三方依赖需要显式点头。引入后要核它不会把 `material3 1.5.0-alpha22` 顶来顶去
（本仓 `libs.versions.toml:9-14` 已经为此写过一条警告注释）。

## 三、UI 形状（照截图）

「界面风格」组内，当风格 = MD3 时追加：

| 行 | 内容 |
| --- | --- |
| 取色来源 | 系统壁纸（默认，现状）／ 自定义色板 |
| 主题色：<名称> | 副标题「当前 #RRGGBB；点按展开预设色板」，右侧 展开/收起 |
| 预设色板（展开态） | 5 列圆形色块 + 下方中文名，选中一枚画勾；末尾留「自定义色值」入口可输入 `#RRGGBB` |

尺寸口径全部取自应用内既有 token，不造新数字：色块直径/间距/文字字号沿用
`VeneraSpacing` 与首页推荐区网格的同一套 `gridGap`；圆块用 `shape` token 的圆形。

## 四、待定（用户拍板）

1. **预设色板的取值来源**。截图里那 20+ 枚的名字与颜色必须有个权威出处，三条路：
   - 甲：用户告知这是哪个 app → 照抄它的常量表（逐字口径，最符合「不造数字」的规矩）；
   - 乙：从截图逐枚采样（已写扫描器验证可行，见下），名字照抄截图；
   - ~~丙：改用 MaterialKolor 官方示例/文档里的 preset 色板。~~ **丙是我编出来的错选项，见 §七**。
   **注意截图本身不完整**：第 5 行被通知条压住、下面还有没有行看不到，所以乙拿不到全表。
2. **作用范围**：~~自定义取色只在 MD3 风格下开放~~ → **已拍板：只在 MD3 下开放**。
3. **做到哪一层**：~~只做色板；还是连 Variant / SPEC 一起做~~ → **已拍板：只做色板**。
4. **依赖**：**已拍板：可以引 MaterialKolor**。

## 五、已做的可行性验证（截图采样）

写了个一次性扫描器（`%LOCALAPPDATA%\Temp\venera-guard-probe\SwatchScan.java`，JDK 单文件直跑，
连通域分割圆形色块），对色板截图实测：

- 能自动切出 23 枚色块，几何规整（列心 x≈129/334/539/744/949，行心 y≈927/1209/1491/1773/2051）。
- 校准点：`樱花粉` 块内像素均值 `#F07597`，而界面文字写的种子色是 `#FA7298` —— 差在色块是**渐变**
  且边缘与深色背景混合。**所以采样只能得到近似值，不能当作权威口径**。

## 七、丙选项作废：MaterialKolor 根本没有「官方 preset 色板」（2026-09-22 实测）

我当时是把「截图里那八个 Variant 与 SPEC_2025 就是 MaterialKolor 的枚举」顺势推成了
「所以它也应该有官方预设色板」。逐字查过仓库后**证伪**：

- 发布产物 `material-kolor` 的 `commonMain` 里只有 `DynamicColorScheme.kt` / `PaletteStyle.kt`，
  **没有任何命名预设色表**；全仓库 `res/**/colors.xml` 一个都没有（只有 launcher icon 与 strings）。
- 唯一像「预设」的东西在 **builder 演示 app** 里：
  `builder/shared/.../settings/model/ColorSettings.kt` 的 `_colors` —— 25 条 CSS 命名色
  （Deep Pink `#FF1493` / Lime `#00FF00` / Dodger Blue `#1E90FF` …），
  而且**含重复**（Dodger Blue 出现 3 次、Deep Pink 与 Hot Pink 各 2 次），明显是取色器的演示样本，
  不是 curated 色板；它也不在发布产物里。
- `builder/.../ui/home/model/ThemeColor.kt` 只是 `data class ThemeColor(title, swatchNumber, color)`，
  值不在那儿。

→ 所以「照抄 MaterialKolor 官方 preset」这条路**不存在**，不能拿它当口径。

### 7-1 已取到的替代权威口径：Material baseline 调色板

出处 = Google Flutter SDK `packages/flutter/lib/src/material/colors.dart`（本仓 master 就是 Flutter 项目，
口径同源）。逐字取到的 500 号主色：

`red #F44336` · `pink #E91E63` · `purple #9C27B0` · `deepPurple #673AB7` · `indigo #3F51B5` ·
`blue #2196F3` · `lightBlue #03A9F4` · `cyan #00BCD4` · `teal #009688` · `green #4CAF50` ·
`lightGreen #8BC34A` · `lime #CDDC39` · `yellow #FFEB3B` · `amber #FFC107` · `orange #FF9800` ·
`deepOrange #FF5722`（另有 brown/grey/blueGrey 与各色 accent 系，共 34 条 `PrimaryValue`）。

值全部官方，**只有中文名需要翻译**（翻译的是名字，不是数值）。

## 八、机制与色板表是可分的

「取色来源 + 种子色 → 整套 MD3 色板 + 手输 `#RRGGBB`」这一层不依赖预设表选哪份；
预设网格只是快捷入口（截图里也确实是「可直接使用取色器，也可输入 #RRGGBB 色值」两行并存）。
所以机制可以先做完并可用，预设表单独定稿替换，不留半成品。

## 六、改动面预估（拍板后才动）

| 文件 | 改什么 |
| --- | --- |
| `gradle/libs.versions.toml` / `app/build.gradle.kts` | 加 MaterialKolor |
| `data/prefs/VeneraPreferences.kt` | 取色来源 + 种子色（+ 可选 variant）三个偏好 |
| `feature/VeneraTheme.kt` | MD3 分支按取色来源分流 |
| `feature/settings/AppearanceSettings.kt` | 界面风格组内新增两行 + 展开态 |
| 新 `ui/tokens/ThemePresets.kt` | 预设色板常量表（名称 + 种子色） |
| 新单测 | 色值解析、预设表完整性、来源分流不串台 |

`VeneraTheme.kt` 与 `VeneraPreferences.kt` 属保护域，动手前单独要授权。

## 九、已拍板并落地（2026-09-22）

四项拍板：色板 = **BiliPai 的那张表**；依赖 = **可以引**；范围 = **只在 MD3 下生效**；层次 = **只做色板**。
机制层已装到模拟器（`:app:installDebug` Installed），单测 8 条全绿。

### 9-1 色板拿到了权威值，并且自带校准

`jay3-yy/BiliPai` → `design-system/.../core/theme/Color.kt` 的 `ThemeColors` + `ThemeColorNames`
（25 条按下标对齐的表）。**樱花粉 = `0xFFFA7298`，与用户截图上那行 `#FA7298` 逐字相同** ——
这既证明表找对了，也证明表里存的是**种子色本身**而不是网格渐变的采样值，
所以 §五 那条"采样只能近似"的顾虑到此关闭，不必再走采样这条路。
截图里第 20~24 枚（琥珀金/暖阳橙/可可棕/雾霭蓝灰/晨曦粉）正是被通知条压住的那几行。

落地时把两条表合成一条 `ThemeSeedPreset(name, argb)`（`ui/tokens/Color.kt` 末尾）——
BiliPai 那边靠单测钉住两表等长，合成之后错位在结构上不可能发生。
色值字面量放在 `Color.kt` 是为了守住该文件开头那条规则：「除本文件外，任何 UI 代码不得出现 `Color(0xFF...)`」。

### 9-2 与 BiliPai 的三处有意分歧

| 点 | BiliPai | 这里 | 为什么 |
| --- | --- | --- | --- |
| 持久化 | `theme_color_index`(Int) + `md3_custom_color_hex`(String) 两个键 | 单键 `pref_theme_seed_color`(ARGB Int) | 两个键是两份真相，会互相打脸（手输完又显示旧下标的名字）。名字改成**按 ARGB 反查**，查不到就叫「自定义」 |
| MaterialKolor | 4.1.1 | **5.0.1** | Maven Central 当前 release；解析后 `androidx.compose.material3:1.3.1 -> 1.5.0-alpha22`，没顶本仓 material3（已跑 `:app:dependencies` 核过，见 catalog 里那条警告） |
| 取色入口 | 设置页常驻 | 只在 `界面风格 = MD3` 时出现 | 拍板结论；Miuix 那套是设计好的固定色板，给了入口不生效就是假开关 |

### 9-3 落地面

| 文件 | 改了什么 |
| --- | --- |
| `gradle/libs.versions.toml` / `app/build.gradle.kts` | `com.materialkolor:material-kolor:5.0.1`，放在 material3 声明**之后** |
| `ui/tokens/Color.kt` | `ThemeSeedPreset` + `ThemeSeedPresets`（25 条、`Columns=5`、`DefaultArgb`=经典蓝、`nameOf`） |
| `data/prefs/VeneraPreferences.kt` | `ThemeColorSource{WALLPAPER,CUSTOM}` 枚举 + 两个偏好 + 两个 setter |
| `feature/VeneraTheme.kt` | MD3 分支加 `colorSource == CUSTOM -> rememberDynamicColorScheme(Color(seedArgb), isDark)`，排在壁纸分支**之前** |
| `feature/settings/AppearanceSettings.kt` | 取色来源下拉 + 「主题色：<名字>」展开条 + 5 列色板网格 + `#RRGGBB` 手输 |
| 新单测 `ThemeSeedPresetTest` | 校准值、表完整性（重名/空值/非实色）、5 列整行、hex 往返、`#RGB`/8 位/非十六进制一律拒 |

顺带比 BiliPai 多做对的一点：勾的颜色按 `color.luminance()` 选黑或白 ——
日光黄 `#FFEB3B` 底上放白勾等于看不见。色块直径 36.dp 与 5 列取自 BiliPai 同一段代码，
间距用应用内既有 `gridGap`，没有造新数字。

### 9-4 还没做 / 不算验证通过

1. **设备观感未看**：只做到「编译 + 单测 + 已安装」。展开态长什么样、切种子色整页有没有跟着换、
   Miuix 控件是否同步取色，都要用户在模拟器上点一遍（我只截图读数）。
2. **色彩风格 Variant / 色彩标准 SPEC 两个下拉**：本轮按拍板不做。默认走 MaterialKolor 的
   `PaletteStyle.TonalSpot` + 其默认 spec。
3. **壁纸取色的刷新**：BiliPai 为此写了 `rememberSystemWallpaperRefreshToken`
   （`addOnColorsChangedListener` + `ACTION_WALLPAPER_CHANGED` + `ON_RESUME` 复读 + 二次补刷），
   因为回调可能早于 Monet RRO 生效。本仓现在只有「不要只按 Context 缓存」那条评论，没有这套 token ——
   属既有缺口，本轮没碰。
4. **`WallpaperPaletteStore` 那条路已确认与主题无关**：BiliPai 那个文件是给首页卡片玻璃着色用的
   （Coil 解码 + `androidx.palette` 切 5 条带），主题的壁纸色走的是 AndroidX `dynamicXxxColorScheme`。
   别把它当"壁纸取色"的实现照搬。
