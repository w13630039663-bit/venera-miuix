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
    alias(libs.plugins.kotlin.serialization)
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
    //    （那坨直接 import android.util.LruCache 的 Android 网络/引擎层）；桌面调用方是 0。
    //    :app 侧使用点是**两处**：feature/FollowUpdatesViewModel.kt 与同清单里的 FollowUpdatesWorker.kt
    //    （Worker 里 `FollowUpdatesRepository.getInstance(applicationContext).updateFolder`）。
    //  - FollowUpdatesWorker.kt：androidx.work（WorkManager/PeriodicWorkRequest），桌面没有后台任务
    //    调度面，本阶段桌面不做后台追更；调用方同样在 :app 侧。
    exclude(
        "VeneraDatabase.kt",
        "FollowUpdatesRepository.kt",
        "FollowUpdatesWorker.kt",
    )

    // S1（Windows 图库首页）：画廊的**取数层与判据层**进桌面编译面。
    // 三颗目录必须一起点名，因为它们互相指：`gallery/domain` 的编排（`GalleryDailyFeed`）
    // 吃 `gallery/data` 的契约（`GalleryBoards`），而那颗契约的返回类型里有
    // `security/guard` 的 `GuardRule` 与 `data/db` 之外的词汇表 —— 只共享其中一颗编不过，
    // KGP 又不许按单文件挑（上面那段"include 的坑"说的就是这件事）。
    //
    // `security/guard` 里今天只有 `ContentGuardManager.kt` 上不了桌面（assets 预设表 + 偏好 +
    // StartupTrace），剩下三颗是零 import 的纯声明，正是 S1 把 `GuardRule` / `AiTagKeys`
    // 从冻结那颗文件里搬出来的原因（豁免记录见 FREEZE-STATEMENT.md 末尾）。
    //
    // ⚠️ 排除清单的判据是**禁引集合**，不是 `^import android`。反例就在我自己量过的第一遍：
    // `GalleryImageLoader.kt` 只 import 了一句 `android.content.Context`，看起来"换个平台件
    // 就能上桌面"，真正把它钉死的是它间接吃的 `data.prefs` + `data.network`
    // （`VeneraImageFetcher` / `ImageFetchCallFactory`）。只看 android 会一边把它误判成
    // "该进却没进"、一边对这类间接绑定视而不见，所以两份账都在
    // `DesktopSharedFaceLedgerTest` 里按禁引集合对。
    srcDir("../app/src/main/java/com/venera/compose/gallery/data")
    srcDir("../app/src/main/java/com/venera/compose/gallery/domain")
    srcDir("../app/src/main/java/com/venera/compose/security/guard")
    // 逐颗点名，每颗的成因各不相同（写在这里而不是"一张总理由"，是因为撤排除时要按颗重判）。
    exclude(
        // Android 接线那半边：包名没变、只是搬进 `android/` 子目录，
        // 上面的 `exclude("android/**")` 已经把三颗目录里的 android 子目录全挡掉了；
        // 这里再点名一次不是为了生效，是为了让清单读得完整 —— 有人把接线搬出子目录时会红。
        //
        // —— 以下七颗是 gallery/data 里的 Android 面 ——
        // 解码闸门：整颗是 coil3 的 Extras/Decoder 管线，桌面侧取图那一层今天还没接 coil。
        "GalleryAnimationGate.kt",
        // ConnectivityManager + NetworkCapabilities：平台网络状态句柄，桌面要另一套判据。
        "GalleryConnectivity.kt",
        // 日榜在途缓存：read(app)/write(app,…) 的形状被 gallery/ui 的 ViewModel 直接拿着。
        "GalleryFeedCache.kt",
        // 推荐页在途缓存：同上，两颗是同一族。
        "GalleryForYouCache.kt",
        // 取图器：coil3 + data.prefs + data.network（不是 android import 挡的，是间接依赖挡的）。
        "GalleryImageLoader.kt",
        // 存图到相册：MediaStore/ContentValues/Environment，平台服务，本轮口径明确不碰。
        "GallerySaver.kt",
        // 标签词典：库文件是 assets 复制出来的真实路径，且走 platform.android 的开库口子。
        "GalleryTagDictionary.kt",
        // —— 以下四颗是"实现类留在 Android、契约与窄接口进桌面"的那一组 ——
        // Gelbooru 账号：Context + AndroidKeyValueStore；客户端只认它实现的 GelbooruCredentials。
        "GelbooruAccount.kt",
        // SauceNao 账号：同上，客户端只认 SauceNaoCredentials 那一枚 apiKey。
        "SauceNaoAccount.kt",
        // 日榜取数的 Android 门面：唯一存在理由是 gallery/ui 那两处 getInstance(context) 零改动，
        // 编排本体在 GalleryDailyFeed.kt（那颗才是共享面）。
        "GalleryFeedSource.kt",
        // 内容守卫实现：assets 里的源级预设表 + VeneraPreferences + StartupTrace。
        // 桌面屏蔽判据直连 data/db 的 GuardRuleStore，不需要这颗（那是 4b 抽出来的内核）。
        "ContentGuardManager.kt",
    )
}

// Task 6：源脚本**随包分发**。桌面链路真用得上的只有这三类（逐个点名，不整个目录搬）：
//  - `sources/*.js` —— EngineSession.load 取的那份源脚本；
//  - `venera-init.js` / `venera-shim.js` —— DesktopJsHost 装载序列的前两颗。
//     （派单口径 R39 只列了 init，读数在案：DesktopJsHost.kt:102 `readText(venera-shim.js)`
//      是无条件执行的，不带它包内装载必崩，所以按"装载路径确实用到"这条判据把它一起带上。）
// **不带** `opencc.txt`：它唯一的消费点是 `app/…/data/tags/ChineseVariantConverter.kt`
//   （`data/tags` 不在本模块 srcDir 的编译面里），桌面侧今天一行都不读它。
// 同理不带 `tags.json` / `tags_tw.json` / `gallery_tags_79415.sqlite` / `source_content_warning.json`
// / `licenses/` / `sources/index.json`（源清单是枚举 `sources/` 得来的，不是读这份索引）。
// include 的坑与上面 kotlin 源集同一条：过滤器对**整个 resources 源集**生效，本模块今天
// 没有自己的 `src/main/resources`，将来加了必须往这张清单里补一颗，否则会被**静默滤掉**。
sourceSets["main"].resources.srcDir("../app/src/main/assets")
sourceSets["main"].resources.include(
    "sources/*.js",
    "venera-init.js",
    "venera-shim.js",
)

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

    // `data/platform/HttpEngine.kt` 在上面的共享 srcDir 里，而它签名上的 `OkHttpClient` 是 okhttp 的类型
    // —— 不显式声明就编不过。必须显式：`:engine-probe` 那份是 implementation，不透传到本模块编译 classpath。
    // 注意这是"接口签名需要它的类型"，不等于桌面已经接好了一条真路（桌面侧实现件与它一起落在 S2）。
    implementation(libs.okhttp)

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
//   加 -Pautofav=1 / -Pfavcheck=1 走收藏取证两跑（写 / 读回，只为读数存在，见 VeneraDesktop.kt）
// Task 6 第 4 件：这两个开关**把值一起传下去**（`--autofav=<值>`），由程序判读 ——
//   原来只传"给没给"，`-Pautofav=0` 也会点亮，是个假开关形状。现在 0/false/off 是关，且照样打读数。
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
            providers.gradleProperty("autofav").orNull?.let { "--autofav=$it" },
            providers.gradleProperty("favcheck").orNull?.let { "--favcheck=$it" },
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
