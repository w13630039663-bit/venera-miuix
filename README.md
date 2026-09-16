# Venera · 纯原生 Jetpack Compose 复刻（compose-migration 分支）

本分支把 Venera 从 Flutter 技术栈完整复刻为 **纯原生 Android Jetpack Compose**（Kotlin Coroutines/Flow + Miuix KMP 视觉 + OkHttp + QuickJS 源脚本），并保留一个 **Compose Multiplatform Desktop** 模块用于 PC 上零真机快速调 UI。

- 上游原版 (Flutter)：https://github.com/venera-app/venera （remote `upstream`）
- 本迁移基准 (Flutter + Miuix 风格)：https://github.com/w13630039663-bit/venera-miuix （`master` 分支 = 原版 Flutter 全量源码）

## 目录结构（实际现状，不是规划）

```text
venera-compose/
├── app/                                # Android 原生模块（复刻主战场）
│   └── src/main/java/com/venera/compose/
│       ├── MainActivity.kt             # 全部屏幕 (1814 物理行) —— 待拆分 feature 包
│       ├── components/                 # VeneraAmbientBackground / VeneraFloatingNavBar
│       ├── data/
│       │   ├── db/                     # VeneraDatabase(手写 SQLiteOpenHelper) + History/Favorite/ComicSource Dao
│       │   ├── prefs/                  # VeneraPreferences (SharedPreferences + StateFlow)
│       │   └── network/                # VeneraNetworkClient (OkHttp/DoH/代理) + PersistentCookieJar
│       ├── reader/                     # VeneraReaderScreen / ComicPageSource / ReaderZoomState / BitmapSliceHelper
│       └── source/                     # ComicSource 抽象 + mangadex/copymanga/baozi + js/QuickJsBridge
├── desktop/                            # Compose Multiplatform 桌面端（仅 UI 原型，MockData）
├── .reference/flutter-master/          # 原版 Flutter 只读参照 (143 .dart) + doc/ 协议 + assets/init.js
├── venera-migration-plan.md            # 总体规划 + 「现实校准」章节
└── venera-gap-analysis.md              # 逐页/逐模块差距清单与优先级
```

## 构建与运行

Android（JDK 17 + Android SDK）：

```powershell
$env:JAVA_HOME = "D:\jdk17\jdk-17.0.20.1+1"
.\gradlew.bat :app:assembleDebug
# 产物 app\build\outputs\apk\debug\app-debug.apk（实测 BUILD SUCCESSFUL，24.5 MB）
```

Desktop（JDK 21+）：

```powershell
.\gradlew.bat :desktop:run
```

需要再看原版实现时，随时从 git 对象里解出参照源码（本仓库含 `master` 分支）：

```powershell
git archive --format=zip -o ref.zip master lib assets doc shaders
Expand-Archive ref.zip -DestinationPath .reference\flutter-master
```

> 注意：`.reference/` 只读、已写入 `.git/info/exclude`，不要提交，也不要拿它当构建输入。

## 当前进度（以代码为准，非愿景）

| 阶段 | 状态 | 说明 |
| :--- | :--- | :--- |
| 1 数据与网络基石 | ✅ `00eb2b7` | 手写 SQLite/SharedPreferences/OkHttp+CookieJar（**不是** Room/DataStore） |
| 2 漫画源引擎 | ✅ `e2303bd` | 3 个硬编码源 + Ping 测速 + 聚合搜索 + 详情/阅读真连网 |
| 2.5 源脚本协议 | ❌ 未落地 | `QuickJsBridge` 零调用点，官方 33 个 `.js` 规则源覆盖 0/33 |
| 4 阅读器 | 🟡 地基已在 `a39829b` | 缺 RTL/LTR 翻页、双页拼合、预加载；切片器 `BitmapSliceHelper` 未接线 |
| 3 / 5 / 6 / 7 / 8 | ⬜ 未开始 | 逐项差距与优先级见 `venera-gap-analysis.md` |

