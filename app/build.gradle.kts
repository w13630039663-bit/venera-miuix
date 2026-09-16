plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.venera.compose"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.venera.compose"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
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

    // Miuix Compose
    implementation(libs.miuix.ui)

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