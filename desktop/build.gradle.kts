/**
 * R1-F 桌面化 S0-1 探针模块：验证 `Compose Desktop + compose-fluent + WindowStyler`
 * 这套版本矩阵能否与本仓的 `AGP 9.3.2 / Kotlin 2.4.10 / material3 钉版` 同仓共存。
 *
 * 判据（写死在方案文档 `docs/rounds/windows-port-feasibility-2026-10.md` 第七节）：
 * 本模块的窗口能起来，**同时** `:app` 的 APK 与 material3 解析结果一字不变。
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// 与 :app 解耦：桌面侧用 gradle 已自动装好的 JDK 21，不去碰系统那颗 JDK 26
kotlin { jvmToolchain(21) }

// PathProvider 的门面接口住在 :app（Android 模块），纯 JVM 模块依赖不了它 ——
// 沿用 :engine-probe 的共享源目录手法：同一份文件、不复制第二份，
// android/ 子包引用 android.content.Context，桌面侧编不进也无需编进，排除即可。
kotlin.sourceSets["main"].kotlin {
    srcDir("../app/src/main/java/com/venera/compose/data/platform")
    exclude("android/**")
    // Task 3：两端共享的建表 DDL。
    // Task 4a：`data/db` 的持久层核心已经全部脱离 android.*，整个目录进桌面编译，
    // 只剩三个文件点名排除（清单见下方 exclude）。
    // 为什么不用 include：KGP 的 sourceSet 过滤器对整个 source set 生效而非单条 srcDir，
    // 实测 include("**/SchemaSql.kt") 会把本模块自己的源文件一并滤掉且 BUILD SUCCESSFUL（静默假绿），
    // 故排除只能按文件名点着写；剩下三个文件脱离 Android（VeneraDatabase 挪进 data/platform/android、
    // 追更那颗等后台任务方案）后整段 srcDir 可以撤掉。
    srcDir("../app/src/main/java/com/venera/compose/data/db")
    exclude(
        "VeneraDatabase.kt",            // Android 接线处：SQLiteOpenHelper 薄壳（R20 允许它留在原位）
        "FollowUpdatesRepository.kt",   // 吃 ComicSourceManager（Android 侧网络/引擎那一坨）
        "FollowUpdatesWorker.kt",       // WorkManager，桌面侧本阶段不做后台追更
    )
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.components.uiToolingPreview)

    implementation(libs.compose.fluent.desktop)
    implementation(libs.window.styler)
    // 端到端最小闭环：真源脚本 + GraalJS 宿主 + 取图，全走 :engine-probe 那批共享 handler
    implementation(project(":engine-probe"))
    // 只是解析探针：桌面侧最终不需要 Miuix，这里量的是它能否与 fluent-desktop 同域共存
    implementation(libs.miuix.ui.desktop)
    // S0-7：现有 4 个玻璃消费点都先过 isRuntimeShaderSupported()，
    // 它在 desktop 返回什么决定"玻璃整段静默跳过"还是"真能画"
    implementation(libs.miuix.blur)
    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)
    // JsonKeyValueStore 的落盘格式（`data/platform` 的共享源里就用这一棵树，不再引第二套 JSON 库）。
    // 必须显式声明：`:engine-probe` 那份是 implementation，不透传到本模块的编译 classpath。
    implementation(libs.kotlinx.serialization.json)
    // Task 3：JdbcSqliteDatabase 的驱动。坐标版本照阶段 1 方案 D3 写死（已在 Maven Central 核到）。
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    // Task 4a 曾为 LocalFavoritesManager.folderToJson 加过 implementation(libs.gson)：
    // 该方法零生产调用方已删（R23），桌面编译面（data/platform + data/db 共享源）里再无 gson
    // 使用者，这颗依赖随之撤掉。

    // 阶段 1 起 :desktop 有 JVM 单测；栈跟仓库钉版一致（JUnit4，libs.junit = junit:junit:4.13.2），不引新测试框架
    testImplementation(libs.junit)
}

tasks.withType<Test> {
    useJUnit()
}

compose.desktop {
    application {
        // 打包对象从"探针窗"换成真正的最小闭环 App：
        // 要验的是 GraalJS 能不能活过 jlink 出来的运行时（S0-9 那次打的是 SpikeWindow，没覆盖到）
        mainClass = "com.venera.desktop.VeneraDesktopKt"

        // S0-9 打包分发探针：出可安装的 exe/msi，并显式带 jdk.accessibility
        // （Compose Desktop 的无障碍走 Java Access Bridge，缺这个模块则读屏软件全哑）
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            packageName = "venera-desktop-probe"
            packageVersion = "1.0.0"
            // 无障碍：Compose Desktop 走 Java Access Bridge，缺 jdk.accessibility 则读屏软件全哑。
            // jdk.unsupported：`sun.misc.Unsafe` 住在这个模块里，而 jlink 的自动模块推导看不出来
            // —— Truffle 的 NodeClassImpl 启动即 NoClassDefFoundError: sun/misc/Unsafe，
            // 打包跑第一次就把 GraalJS 打死（实测读数见 docs/rounds/windows-r1f-s0-spike-2026-10-02.md §七）。
            modules("jdk.accessibility", "jdk.unsupported")
            description = "R1-F 阶段 0 探针（非正式产物，只验打包链）"
            vendor = "venera-miuix"
            // 不带控制台时 exe 的 stdout 直接丢弃，打包后的启动证据就全看不到了。
            // 正式产物要改成写日志文件；这里是为了量"闭环在 jlink 运行时里到底起没起"。
            windows {
                console = true
            }
        }
    }
}

// S0-2 的 8 组件组合探针（`:desktop:run` 现在指向真 App，探针窗改走这个任务）
tasks.register<JavaExec>("spike") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.SpikeWindowKt"
}

// S0-7：玻璃能力三层读数（纯 stdout，不需要窗口）
tasks.register<JavaExec>("glass") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.GlassProbeKt"
}

// S0-8：条漫单列数千图的滚动帧时间与内存
tasks.register<JavaExec>("reader") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.ReaderProbeKt"
    // 堆上限写死，读数才可复现；-PdecodePerItem=true 跑"每项各自解码"的端到端档
    jvmArgs("-Xmx2g")
    args(providers.gradleProperty("decodePerItem").getOrElse("false"))
}

// 端到端最小闭环：真源 → GraalJS → 取图 → Fluent 窗口
//   ./gradlew :desktop:app -Pkey=jm -Pproxy=127.0.0.1:7890
//   加 -Pshot=_qa/desktop-jm.png 时它自截图后退出（无人值守取证）
tasks.register<JavaExec>("app") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.VeneraDesktopKt"
    workingDir = rootDir
    args(
        listOf(
            providers.gradleProperty("key").getOrElse("jm"),
            providers.gradleProperty("proxy").orNull?.let { "--proxy=$it" },
            providers.gradleProperty("shot").orNull?.let { "--shot=$it" },
        ).filterNotNull()
    )
}

// S0-5：中文 IME 探针窗（判据只能由真 IME 会话给出，所以单独一个可启动入口）
tasks.register<JavaExec>("ime") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.ImeProbeKt"
}

// S0-6a：不引 window-styler，自己用 FFM 调 DWM 设 Mica（要拿 HWND ⇒ 两个 add-exports 是探针的一部分）
tasks.register<JavaExec>("mica") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.MicaProbeKt"
    workingDir = rootDir
    jvmArgs(
        "--add-exports", "java.desktop/sun.awt=ALL-UNNAMED",
        "--add-exports", "java.desktop/sun.awt.windows=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
}
