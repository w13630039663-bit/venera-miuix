plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.venera.compose"
    // Android 13 (API 33) 起可安装，按 Android 17 (API 37) 编译并声明目标版本。
    // 不设置 maxSdk，避免阻止后续 Android 版本安装；各系统行为仍需真机回归。
    compileSdk = 37

    defaultConfig {
        applicationId = "com.venera.compose"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // S8: release 无正式证书时用 debug keystore 兜底签名，
        // 保证 R8 产物可直接侧载验证；发布正式版时替换为生产 keystore
        create("release") {
            val dbg = signingConfigs.getByName("debug")
            storeFile = dbg.storeFile
            storePassword = dbg.storePassword
            keyAlias = dbg.keyAlias
            keyPassword = dbg.keyPassword
        }
    }

    buildTypes {
        release {
            // S8: 开启 R8 混淆 + 资源收缩（keep 规则见 proguard-rules.pro，
            // 覆盖 WebView JS 桥 / Gson 反射模型 / kotlinx.serialization 路由）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 保留行号映射：崩溃堆栈可经 mapping.txt 还原，便于线上排错
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // S8: 统一 so 架构（真机 arm64 为主，兼容 32 位与模拟器）
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    lint {
        // R8/资源收缩后的告警不阻塞构建，报告落 build/reports
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        // S8: JVM 单测中 android.util.Log 等框架方法返回默认值而非抛异常，
        // 使纯逻辑（JM 分块计算等）可不依赖模拟器直接测试
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose 基础
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.animation)
    implementation(libs.miuix.blur)
    // 官方液态玻璃库（Kyant0/AndroidLiquidGlass）：底栏的 blur + lens + vibrancy 由它提供
    implementation(libs.backdrop)
    // Backdrop 的 Capsule / RoundedRectangularShape（lens 的 shape 参数需要）
    implementation(libs.kyant.shapes)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // S0 地基：导航栈 / ViewModel / 分页
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.paging.runtime)

    // 图片：Coil 3 + OkHttp 引擎（按域注入防盗链头）
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.telephoto.zoomable.image.coil)

    // Miuix Compose
    implementation(libs.miuix.ui)

    // S5-5 追更：周期检查任务
    implementation(libs.androidx.work.runtime.ktx)

    // 网络与解析
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.gson)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.android)

    // 类型安全导航的路由参数序列化（navigation-compose 2.8 要求）
    implementation(libs.kotlinx.serialization.json)

    // 脚本引擎：现有 quickjs-android 无 Promise 微任务泵，S1 将换成 Zipline；
    // 目前仅用于保留 .js 规则文件解析能力（QuickJsBridge 已标注待替换）。
    implementation(libs.quickjs.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}