pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
// 让 gradle 能按需自动下载 toolchain（`:engine-probe:bench -Pjdk=25` 量 GraalJS 的 JIT 差值要用）
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    }
}

rootProject.name = "venera-compose"
include(":app")
// R1-F 桌面化 S0-1 探针：只做加法，:app 不引用它的任何东西
include(":desktop")
// S0-3 探针：GraalJS 顶替 WebView 跑真源脚本（复用 :app 的 engine handler 源文件）
include(":engine-probe")
// A0 探针（docs/rounds/desktop-reuse-android-ui-2026-10-18.md §6）：一颗粒真正的 KMP 模块，
// 两端各引一次，只回答"`:shared` 能否同时被 androidJvm 与 jvm 依赖并起窗"这一个问题。

