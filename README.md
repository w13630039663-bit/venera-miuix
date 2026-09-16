# Venera - Compose Multiplatform (Android & Desktop 原生迁移原型)

本分支 (compose-migration) 是 Venera 从原有 Flutter 技术栈向 **Jetpack Compose + Compose Multiplatform (CMP)** 原生重构的迁移架构分支。

## 🌟 核心特性与优势

1. **极致流畅度与超低延迟**
   - 移除 Flutter 中间渲染与 JNI 桥接开销，直接运行在原生 Android 渲染管道与 Compose 响应式树上。
   - 彻底解决列表快速滑动和进退详情页时的微卡顿与掉帧。

2. **原生级 MIUIX 设计语言**
   - 采用 	op.yukonga.miuix.kmp:miuix-ui 原生 MIUI 风格组件库。
   - 还原 MIUI 标志性的超级弹簧回弹物理动效 (MIUI Spring Physics)、平滑连续曲率圆角卡片 (Continuous Corner Curves)、流体式搜索栏展开收起。

3. **原生系统级手势与转场 (Predictive Back & Shared Transition)**
   - Android 14+ 预测性返回手势 (PredictiveBackHandler)：在侧滑返回过程中，整个详情页面跟随手指进度等比例缩放与微调透明度，松手丝滑返回上一层。
   - 共享元素连续转场 (SharedTransitionScope)：封面图无缝放大进详情页、缩回列表。

4. **双端共享架构 (Android + Desktop)**
   - **pp/**：Android 原生 Jetpack Compose 模块，支持手机、平板自适应布局。
   - **desktop/**：Compose Multiplatform Desktop 模块，基于 Skiko DirectX / Vulkan 硬件加速，支持在 Windows PC 桌面端免真机即时调试与运行。

---

## 📁 模块目录结构

`	ext
venera/
├── app/                               # Android 原生 Jetpack Compose 模块
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/venera/compose/
│           └── MainActivity.kt        # 包含 6 大主 Tab 完整页面与详情页流体交互
├── desktop/                           # Compose Multiplatform 桌面端模块
│   └── src/main/kotlin/com/venera/compose/desktop/
│       ├── Main.kt                    # PC 桌面端入口 (可独立运行)
│       ├── models/Models.kt           # 实体模型与模拟数据源
│       └── ui/
│           ├── components/Components.kt # 通用 MIUI 风格卡片、角标、骨架与组件
│           └── screens/               # 各 Tab 页面完整还原 (Home/Search/Explore/Favorites/Categories/Settings)
├── gradle/
│   └── libs.versions.toml             # Gradle Version Catalog 依赖统一管理
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
`

---

## 🚀 编译与运行

### 1. 运行桌面端 (Windows PC)
桌面端需要 JDK 21+ 环境：
`ash
./gradlew :desktop:run
`

### 2. 编译 Android 端 APK
Android 端构建需要 Android SDK 与 JDK 17：
`ash
./gradlew :app:assembleDebug
`
生成的 APK 路径：pp/build/outputs/apk/debug/app-debug.apk
