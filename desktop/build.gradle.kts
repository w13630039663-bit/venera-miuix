plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    application
}

dependencies {
    implementation("top.yukonga.miuix.kmp:miuix-ui-desktop:0.9.4-rc01")
    implementation("org.jetbrains.compose.runtime:runtime-desktop:1.12.0-rc01")
    implementation("org.jetbrains.compose.runtime:runtime-saveable-desktop:1.12.0-rc01")
    implementation("org.jetbrains.compose.ui:ui-desktop:1.12.0-rc01")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:1.12.0-rc01")
    implementation("org.jetbrains.compose.animation:animation-desktop:1.12.0-rc01")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.150.1")
}

application {
    mainClass.set("com.venera.compose.desktop.MainKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<AbstractCopyTask> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

distributions {
    main {
        contents {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }
}

