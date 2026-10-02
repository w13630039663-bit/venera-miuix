/**
 * S0-3 探针：桌面侧（纯 JVM）用 GraalJS 跑**真源脚本**，判据见
 * `docs/rounds/windows-port-feasibility-2026-10.md` 第七节 S0-3。
 *
 * 复用 `:app` 里刚去 Android 化的那 6 个 handler 源文件（同一批文件、同一份实现，
 * 不复制第二份 ⇒ 不会有口径漂移）。唯一排除的是 `VeneraJsEngine.kt` —— 它是
 * WebView 载体，桌面侧由本模块的 `DesktopJsHost` 顶替。
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(21) }

kotlin.sourceSets["main"].kotlin {
    srcDir("../app/src/main/java/com/venera/compose/engine")
    exclude("VeneraJsEngine.kt")
}

dependencies {
    // GraalJS：唯一满足"支持 async/await + ES2022"的 JVM 内嵌引擎（Rhino/Nashorn 已因无 await 出局）
    implementation("org.graalvm.polyglot:polyglot:25.4.4.1.1")
    // js-community 是 POM 聚合构件，Gradle 的第四段是 classifier 而非 type ⇒ 必须显式声明 artifact
    implementation("org.graalvm.polyglot:js-community:25.4.4.1.1") {
        artifacts { artifact { type = "pom"; extension = "pom"; classifier = "" } }
    }

    implementation(libs.okhttp)
    // jm 的页图实测是 WebP（RIFF....WEBP/VP8），JVM 的 ImageIO 原生不认 —— 桌面图片管线要外挂解码器
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.15.2")
    implementation(libs.gson)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

// 代理从命令行传：-Pproxy=127.0.0.1:7890（本机直连这些域名全不通）
val probeArgs: (String) -> List<String> = { key ->
    listOf(key) + providers.gradleProperty("proxy").orNull?.let { listOf("--proxy=$it") }.orEmpty()
}

tasks.register<JavaExec>("nhentai") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.ProbeMainKt"
    args = probeArgs("nhentai")
    workingDir = rootDir
}

tasks.register<JavaExec>("jm") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.ProbeMainKt"
    args = probeArgs("jm")
    workingDir = rootDir
}

// S0-4 判据里点名的源：ehentai（exhentai 那半边正是 CF + 登录双挡）
tasks.register<JavaExec>("ehentai") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.ProbeMainKt"
    args = probeArgs("ehentai")
    workingDir = rootDir
}

// S0-4：JVM OkHttp 直撞已知 CF 站点，不过盾能不能出数据
tasks.register<JavaExec>("cf") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.CfProbeKt"
    args = listOfNotNull(providers.gradleProperty("proxy").orNull?.let { "--proxy=$it" })
    workingDir = rootDir
}

// 运行期 JDK 对 GraalJS 的差值：-Pjdk=21 / -Pjdk=25（25 由 gradle 自动下载）
val benchJdk = providers.gradleProperty("jdk").getOrElse("21").toInt()
tasks.register<JavaExec>("bench") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.JitBenchKt"
    workingDir = rootDir
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(benchJdk)
    }
    args = listOfNotNull(providers.gradleProperty("proxy").orNull?.let { "--proxy=$it" })
}
