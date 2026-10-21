package com.venera.compose.data.platform

/**
 * 六枚**纯声明**枚举从 `data/prefs/VeneraPreferences.kt` 搬来这里（2026-10-20）。
 *
 * ## 为什么搬
 *
 * `VeneraPreferences.kt` 原本是三合一：枚举声明 + 936 行偏好读写 + `getInstance(Context)`。
 * 其中 `getInstance(Context)` 与 `AndroidKeyValueStore` 是 Android 绑定，而**枚举与读写本体
 * 一行都不吃 Android** —— 底层的存取通道早在 `KeyValueStore` 那一步就收口了
 * （`VeneraPreferences.kt:65` 的原注释：「Android 侧是 SharedPreferences 的薄壳，
 * 桌面侧是一名一个 JSON 文件」）。
 *
 * 桌面要复用大屏 UI，就得读这几个枚举（`ui/tokens` 的 4 处、`components` 的 3 处）。
 * 而 `data/platform` 是**已经共享给 `:desktop`** 的那颗目录（`desktop/build.gradle.kts:28`
 * 的 srcDir，只排除其中的 android 子目录），枚举住这里，两端就都能读到，且谁都不必 import Android。
 *
 * ## 为什么不整颗文件搬过来
 *
 * `VeneraPreferences` 本体还留着 `getInstance(Context)`（Android 生产入口），
 * 整颗搬进来会让 `data/platform` 出现 `android.content.Context` —— 那颗目录的约定是
 * 「平台无关内核 + `android/` 子目录放实现」，文件头注释写得很清楚。
 * 所以只搬**零依赖的那部分**，本体留在原地，只改 import（逐字零语义变化）。
 *
 * ⚠️ 搬家而不是复制：这六枚在两处各定义一份会让 `AppearanceStyle` 出现两个不同的类型，
 * 编译器不会报错（同名不同包），运行时 `when` 会静默落错分支。所以 `VeneraPreferences.kt`
 * 里那几行是**删掉**，不是留着。
 */

/** 主题模式。 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK,
}

/** 外观风格（色板 / 形状 / 字号的唯一轴）。 */
enum class AppearanceStyle {
    MIUIX, MD3,
}

/** 底栏样式 —— 与 [AppearanceStyle] 正交，只管导航栏那一层。 */
enum class NavigationBarStyle {
    MD3, LIQUID_GLASS,
}

/**
 * 内容层的表面材质 —— 与 [AppearanceStyle]（色板/形状/字号）、[NavigationBarStyle]（只管底栏）
 * **正交的第三轴**（2026-09-29 拍板）。
 *
 * 为什么不塞进 `AppearanceStyle`：那一枚同时驱动色板派生、形状阶梯、字号阶梯三件事
 * （见 `feature/VeneraTheme.kt:63-98`），把材质并进去就等于强迫「开玻璃必然改色板与圆角」，
 * 而用户要的是材质能单独开关；分开还保得住「MD3 + 玻璃」与「Miuix + 玻璃」两种组合都成立。
 *
 * 默认 [SOLID] —— 这一条不是保守，是**零回归的判据本身**：默认关闭 ⇒ 升级后所有人看到的
 * 东西与今天逐像素相同，测试也钉这一点（`SurfaceMaterialPolicyTest.默认档是实色`）。
 */
enum class SurfaceMaterial {
    SOLID, LIQUID_GLASS,
}

/**
 * 色板来源。仅在 [AppearanceStyle.MD3] 下有意义 —— Miuix 风格用的是它自己设计好的固定色板。
 *
 * WALLPAPER = 系统壁纸动态取色（`DynamicColors`，Android 12+）；
 * CUSTOM = 用户选定的种子色，经 MaterialKolor 展开成整套 MD3 色板。
 */
enum class ThemeColorSource {
    WALLPAPER, CUSTOM,
}

/** 标签译文显示模式。SYSTEM = 由系统语言同时决定「译不译」与「译成简还是繁」。 */
enum class TagTranslationMode {
    SYSTEM, SIMPLIFIED, TRADITIONAL, OFF,
}
