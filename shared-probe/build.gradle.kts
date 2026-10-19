/**
 * A0 探针模块（方案文档 `docs/rounds/desktop-reuse-android-ui-2026-10-18.md` §6）。
 *
 * 只回答一个问题：**`:shared` 形态的 KMP 模块能否同时被 `:app`（androidJvm）与 `:desktop`（jvm）
 * 依赖并起窗**。不回答任何 UI 设计问题，不迁移任何现有代码 —— commonMain 里只有 `Hello()`。
 *
 * 四条判据在 §6.3，读数在 §6.5。红了就整颗删掉（`settings.gradle.kts` 一行 + 两端各一行依赖 + 这棵树）。
 *
 * ## 探针抓到的第一条 `[术]`（写在这里而不是写在文档脚注里，因为它会咬住 A1）
 *
 * `com.android.library` + `org.jetbrains.kotlin.multiplatform` **在 AGP 9 起互斥**，起手就红：
 * > The 'com.android.library' plugin is not compatible with the 'org.jetbrains.kotlin.multiplatform'
 * > plugin since AGP 9.0. Solution: Replace ... with the 'com.android.kotlin.multiplatform.library' plugin
 * （读数原文：`sh gradlew :shared-probe:compileKotlinMetadata`，2026-10-18，AGP 9.3.2）
 *
 * ⇒ 将来建正式 `:shared` 时**没有** "library 模块 + kotlin-android" 那条老路可走，只能吃这颗插件。
 *
 * 为什么 commonMain 的两颗靶子写死字面版本而不进 `libs.versions.toml`：
 * 目录里那些行是**两端共享的钉版**（`androidx.compose.material3 = 1.5.0-alpha22` 那颗还背着一次
 * `NoSuchMethodError` 事故的注释），探针要量的正是"换了坐标会不会把对岸顶走"，与既有钉版混进同一棵
 * 树会把读数和裁决缠在一起。量完若要留下，再进目录。
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    // 与 :desktop 同一颗 JDK（gradle 自管的 Temurin 21），不去碰系统那颗
    jvmToolchain(21)

    jvm()

    // 安卓侧的入口与清单在这颗块里配（AGP 的 KMP 插件把 `android` 挂在 `kotlin` DSL 上，
    // manifest 默认位置是 `src/androidMain/AndroidManifest.xml`，源码在 `src/androidMain/`，
    // 两处都是本目录今天的实际形状 —— 没有 `sourceSets["main"].manifest.srcFile(...)` 那一行老写法）
    android {
        namespace = "com.venera.sharedprobe"
        compileSdk = 37
        minSdk = 33
    }

    sourceSets {
        commonMain.dependencies {
            // ── 2026-10-19 隔离实验（用户题面："电脑板能不能先不影响安卓那边的编译"）──
            // 三颗 compose 不再吃插件访问器（`compose.runtime` 等），改成**逐字钉版**，
            // 钉的就是 `:app` 今天实际解析到的那一版 1.12.0（`_qa/a0/baseline_app_deps.txt` 的读数）。
            // A0 判据 4 那格红的成因正是访问器：插件 1.12.1 会把对岸整棵 androidx.compose 抬到 1.12.1。
            implementation("org.jetbrains.compose.runtime:runtime:1.12.0")
            implementation("org.jetbrains.compose.ui:ui:1.12.0")
            implementation("org.jetbrains.compose.foundation:foundation:1.12.0")
            // 靶子 1：JetBrains 的 material3。§2.3 查实它与 androidx `1.5.0-alpha22` 在同一个
            // atomic group 里；基线读数（`_qa/a0/baseline_app_deps.txt:887`）显示 :app 今天已经
            // 经由 material-kolor 把它带上 runtimeClasspath —— 所以量的不是"能不能出现"，
            // 而是"共享模块吃它之后 :app 的解析结果动没动"。
            implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
            // 靶子 2：lifecycle 的 KMP 版。A0 读数里它是第二个抬版源（JetBrains 那族 2.9.6 → 2.11.0），
            // 所以这一档钉回基线那版 2.9.6，而不是 A0 第一次用的 2.11.0。
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6")
            // Miuix 本体：§2.1 查实它是六平台同版本单一 KMP 构件，安卓侧走 `-android`、
            // 桌面侧走 `-desktop`，都由 Gradle 按 variant 自动挑，这里只写主坐标。
            implementation(libs.miuix.ui)
        }
        androidMain.dependencies {
            // 只给 `ProbeActivity` 用（安卓侧那颗可启动入口），:app 本来就带同一颗。
            implementation(libs.androidx.activity.compose)
        }
    }
}
