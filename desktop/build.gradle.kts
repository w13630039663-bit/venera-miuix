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
    // Task 4b：4b 从 8 颗业务文件里抽出的持久层内核（ReadingStatsStore / FavoriteImagesStore /
    // GuardRuleStore / TagDictionaryStore / CoreTableBackup / JsonValues）也放进了 `data/db`，
    // 所以它们**不需要新增 srcDir** 就自动进桌面编译面，`:desktop:test` 因此能拿真
    // JdbcSqliteDatabase + 临时 `.db` 跑它们的行为。
    // 那 8 颗业务文件本身**没有**搬进来，各自的原因为（详见 4b 报告，不许假摘）：
    //  - sync/BackupManager、sync/ForeignArchiveImport：cacheDir 落点/解包、ZipFile、logcat，
    //    BackupManager 还要画廊两个 store 与 FavoriteImagesManager（Bitmap）的单例；
    //  - stats/ReadingStatsManager：标签字典与繁简表要 Context；
    //  - security/guard/ContentGuardManager：源级预设表在 assets 里，另有 StartupTrace 与偏好；
    //  - feature/favoriteimages/FavoriteImagesManager：android.graphics.Bitmap 落盘 + filesDir；
    //  - gallery/data/GalleryTagDictionary：词典库是 assets 复制出来的真实路径（Context.assets）；
    //  - gallery/data/GallerySaver、reader/VeneraReaderScreen：唯一的 ContentValues 是 MediaStore
    //    的 ContentResolver 写入，按本轮口径（平台服务不碰）一行未动。
    // 为什么不用 include：KGP 的 sourceSet 过滤器对整个 source set 生效而非单条 srcDir，
    // 实测 include("**/SchemaSql.kt") 会把本模块自己的源文件一并滤掉且 BUILD SUCCESSFUL（静默假绿），
    // 故排除只能按文件名点着写；剩下三个文件脱离 Android（VeneraDatabase 挪进 data/platform/android、
    // 追更那颗等后台任务方案）后整段 srcDir 可以撤掉。
    srcDir("../app/src/main/java/com/venera/compose/data/db")
    // 摘（= 撤掉某行的 exclude）的判据两条：这颗进桌面能编译，且真有桌面代码调用它。
    // 4b-2 逐颗核过（2026-10），三颗都不满足第一条，全留：
    //  - VeneraDatabase.kt：直接 import android.database.sqlite.SQLiteOpenHelper 与 android.content.Context，
    //    桌面编不过；它唯一的 Android 生产引用点是 data/platform/android/AndroidDatabasePorts.kt（那颗本来
    //    就被上面的 exclude("android/**") 挡在桌面编译面外）。桌面侧没有任何代码调用它。
    //    4a §七.3 预告过的收口（整个文件挪进 data/platform/android/，让 data/db 目录对桌面全透明）
    //    前提已凑齐、但那是 Android 接线面的文件搬迁，不在本段的决策面里，留在这里写明。
    //  - FollowUpdatesRepository.kt：构造吃 android.content.Context，并依赖 source/ComicSourceManager
    //    （那坨直接 import android.util.LruCache 的 Android 网络/引擎层）；桌面调用方是 0
    //    （全仓唯一使用点 feature/FollowUpdatesViewModel.kt，属 :app）。
    //  - FollowUpdatesWorker.kt：androidx.work（WorkManager/PeriodicWorkRequest），桌面没有后台任务
    //    调度面，本阶段桌面不做后台追更；调用方同样在 :app 侧。
    exclude(
        "VeneraDatabase.kt",
        "FollowUpdatesRepository.kt",
        "FollowUpdatesWorker.kt",
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
    // Task 4b：`data/db` 的持久层 JSON（JsonValues / CoreTableBackup）替代原来的 android `org.json`
    // ——后者桌面侧根本没有。生产调用方是真的：`sync/BackupManager` 导出与恢复归档的九个 member、
    // `sync/ForeignArchiveImport` 读 PicaComic 的 `sync_data`、`security/guard/ContentGuardManager`
    // 读 assets 的源级预设表，全走这一层。
    // （Task 4a 曾为已删的 `folderToJson` 加过它又撤掉；这次带进来的是有生产调用方的那份。）
    implementation(libs.gson)

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
