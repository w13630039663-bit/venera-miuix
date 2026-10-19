# minSdk 33 → 30（Android 13 → Android 11）可行性研究

> 只做研究，未改动任何构建配置或源码。结论落点：`app/build.gradle.kts:33`。

## 一句话结论

**技术上可以降到 30，但有一个第三方库是硬阻塞，且它是主动依赖、不是传递依赖。**

---

## 1. 现状

| 项 | 值 | 位置 |
|---|---|---|
| minSdk | **33**（Android 13） | `app/build.gradle.kts:33` |
| targetSdk / compileSdk | 37 | `app/build.gradle.kts:26,34` |
| 安装身份 | `com.github.w13630039663bit.venera.miuix` | `app/build.gradle.kts:32` |

注释口径：`minSdk` 与 master（Flutter 版 venera-miuix）逐字一致，本包要接管该发布线。

---

## 2. 唯一的硬阻塞：`miuix-blur` minSdk = 33

把本地 Gradle 缓存里 **189 个 AAR 的 `AndroidManifest.xml` 全部读出 minSdkVersion**，分布如下：

```
minSdk=14:  35 aar
minSdk=18:   1 aar
minSdk=19:   8 aar
minSdk=21:  50 aar
minSdk=23:  83 aar
minSdk=24:  11 aar
minSdk=33:   1 aar   ← 只有这一个
```

那唯一一个 minSdk=33 的就是 **`top.yukonga.miuix.kmp:miuix-blur-android:0.9.4-rc01`**。

其余与本项目直接相关的依赖全部 ≤ 24：

| 依赖 | minSdk |
|---|---|
| androidx.core / core-ktx（1.16.0/1.18.0） | 19–23 |
| androidx.activity / activity-compose | 21–23 |
| androidx.lifecycle（2.9.x / 2.11.0） | 21–23 |
| androidx.navigation-compose（2.10.1） | 24 |
| androidx.media3（exoplayer / ui） | 23 |
| io.coil-kt.coil3（3.6.2，含 gif / network-okhttp） | 23 |
| me.saket.telephoto（0.19.0） | 21 |
| androidx.browser（1.8.0，Custom Tabs） | 19 |
| **top.yukonga.miuix.kmp:miuix-ui-android** | **24** |
| top.yukonga.miuix.kmp:miuix-core / icons / shader / squircle | 24 |
| com.materialkolor:material-kolor | 21 |
| io.github.kyant0:backdrop（官方液态玻璃） | 21 |
| io.github.fornewid:material-motion-core | 21 |

**它不是传递依赖。** `miuix-ui-android` 的 pom 只依赖 `miuix-core` + `miuix-squircle`，**不含 blur**。
blur 是本项目在 `app/build.gradle.kts:161` 主动写下的 `implementation(libs.miuix.blur)`。

### 为什么锁在 33

上游源码 `miuix-blur/build.gradle.kts` 里是**硬编码字面量**，不可配置：

```kotlin
android {
    minSdk = 33        // ← 写死
    namespace = "${BuildConfig.LIBRARY_ID}.blur"
}
```

对比同仓库其它模块：`BuildConfig.MIN_SDK = 24`（`build-logic/.../BuildConfig.kt`），
即 miuix 主 UI 库只要 24，**只有 blur 这一个模块被单独抬到 33**。

技术原因在 `RenderEffectCompat.android.kt`：androidMain 直接用
`android.graphics.RenderEffect.createRuntimeShaderEffect` / `createChainEffect` / `createBlendModeEffect`，
这些是 API 31/33 引入的平台能力。

### 版本历史（确认"换低版本"这条路）

| miuix 版本 | blur 模块 minSdk |
|---|---|
| v0.8.8 及更早 | 23（blur 还在 `miuix` 主模块内，`BuildConfig.MIN_SDK = 23`） |
| v0.9.0 | **31** |
| v0.9.1 | **32** |
| v0.9.2 | **33** |
| v0.9.3 / 0.9.4 / 0.9.4-rc01 | 33 |

每个小版本抬一级，**没有任何已发布版本低于 31**。所以"降到 0.9.x 里的某个版本"最多降到 31，到不了 30。

---

## 3. 应用侧代码：基本已做好降级，但有 4 处要复核

应用自己**没有**成为阻塞项。已存在的版本分支都是安全写法：

| 位置 | 处理 |
|---|---|
| `feature/Navigation.kt:568-571` | lens 折射判 `>= TIRAMISU`，否则走另一条渲染路径 |
| `feature/VeneraTheme.kt:72-76` | 壁纸取色判 `>= S`，否则退 MD3 静态色板 |
| `feature/settings/AppearanceSettings.kt:86,103-105` | `< S` 时如实显示"此版本拿不到壁纸动态色板" |
| `data/network/ImagePipelinePolicy.kt:379-384` | `BitmapRegionDecoder` 双构造分支 |
| `reader/VeneraReaderScreen.kt:2323` | 判 `>= Q` |

### 需要复核的 4 处

1. **`reader/VeneraReaderScreen.kt:1910-1914`**（锐化）
   直接调 `android.graphics.RuntimeShader` + `createRuntimeShaderEffect`（API 33），**没有 SDK 版本门**。
   但被 `runCatching{}.getOrNull()` 包住，调用方 `:575-583` 有完整降级：
   返回 null → 不挂 effect + `Log.w` + Toast「本机不支持锐化，已只应用色彩调整」。
   ⚠️ 需验证 `runCatching` 在 API 30 上确实能兜住
   `NoClassDefFoundError`（它能接住 `Throwable`，理论上可以，但**未经真机验证**）。
   缓解：该功能默认关闭（`enhanceEnabled` 默认 false），只有用户手动开增强才会走到。

2. **`AndroidManifest.xml:26` `android:enableOnBackInvokedCallback="true"`**
   API 33+ 属性，低版本系统直接忽略，无害。但预测式返回动画在 API 30 上表现不同，
   `memory/project-navigation-predictive-back-facts.md` 记的 5 个观察点需重新在 API 30 上确认。

3. **`AndroidManifest.xml:9` `POST_NOTIFICATIONS`**
   API 33 才需要运行时权限。当前代码 `DownloadNotificationChannel.kt:47` 用
   `ContextCompat.checkSelfPermission`、`UpdateCheckUi.kt:107-123` 用
   `RequestPermission` 合约——两者在 API 30 上都自动降级为"已授予"，**无需改动**。

4. **`AndroidManifest.xml:21` `MANAGE_EXTERNAL_STORAGE`**
   API 30 引入。本项目的下载链是 `File` 语义（`docs/rounds/storage-path-selection-2026-09.md:21`
   已记录分区存储下公共目录不可写），恰好 30 起有这条官方通道，**降级反而更宽松**。

---

## 4. 如果要做，有哪几条路

### 路 A：接受 30 + 在 API <33 机器上关掉玻璃
把 `miuix.blur` 依赖保留（它只是让 minSdk 抬到 33 的那个 manifest 声明），
但 minSdk 写 30 时 **AGP manifest merger 会直接报错**——
依赖 minSdk 高于 app minSdk 是构建失败，不是运行期降级。

所以路 A 实际上要**先把 blur 依赖拆掉**，等同路 B。

### 路 B：抽出 blur 依赖，自绘降级路径（改动最大）
- 删 `implementation(libs.miuix.blur)`
- 命中面极大：`VeneraGlass.kt`、`VeneraTopAppBar.kt`、`VeneraTopBarPill.kt`、
  `VeneraTopBarBackdrop.kt`、`InteractiveHighlight.kt`、`ComicDetailScreen.kt`、
  `FavoritesScreen.kt`、`GalleryScreen.kt`、`GalleryForYouScreen.kt`、
  `NetworkFavoritesScreen.kt`、`FavoriteImagesScreen.kt`、`SettingsHome.kt`、
  `VeneraAmbientBackground.kt`、`GalleryFavoritesBody.kt`…
  约 14 个文件、68 处 `top.yukonga.miuix.kmp.blur.*` 引用
- 每处都要补 `isRuntimeShaderSupported()` 门 + 非玻璃降级样式
- `docs/收藏页双栏分屏方案_2026-09.md:184` 已写过参考降级样式：
  `< Android 13` 时退回半透明底 + 描边
- ⚠️ 这不是"改个数字"，是**一次 UI 渲染层重构**

### 路 C：分 ABI / 分渠道出两个包
同一份源码出 minSdk 33 与 minSdk 30 两个 APK。
构建层可以（`splits` 已在用），但要维护两条发布线，
且 `versionCode` 需区分，与"接管 master 发布线"的目标冲突。**不推荐**。

### 路 D：不动 minSdk，确认需求动机
如果动机是"覆盖更多老设备"，先确认是否真有这批用户：
`applicationId` 与 master 一致，master 的分发渠道决定了实际用户分布。
Android 11/12 设备占比需先有数据再决定是否值得走路 B。

---

## 5. 建议

**先问清楚为什么需要 11，再决定动不动。**

- 若目标是"顺手把兼容面做宽"→ 建议维持 33。路 B 是一次 14 文件级的渲染层重构，
  而项目当前处在冻结/交付节奏（`docs/FREEZE-STATEMENT.md`），风险收益比不划算。
- 若有明确的用户数据或渠道要求支持 11/12 → 走路 B，但建议**先做技术验证**：
  把 blur 依赖摘掉后单编一版，量一下 UI 降级的真实观感，再评估工作量。

## 6. 未验证项（需真机或构建环境）

- 本次**未能跑通构建**：本机 Gradle wrapper 锁文件
  `gradle-9.7.1-bin.zip.lck` 为 stale 残留且沙箱拒绝删除；
  直接调解压好的 `gradle.bat` 则报
  `Failed to load native library 'native-platform.dll'`（沙箱限制）。
  已尝试改 `minSdk = 30` 触发构建，**未取得构建输出**，改动已还原
  （还原后文件 SHA256 与改前备份逐字节一致）。
- 因此下列结论来自**静态证据**（AAR manifest 直读 + 上游源码 + 应用源码分支分析），
  未经编译期确认：AGP manifest merger 对 `app minSdk 30 < 依赖 minSdk 33` 的确切报错文案、
  `runCatching` 在 API 30 上对 `NoClassDefFoundError` 的实际兜住行为、
  摘掉 blur 依赖后的编译错误全貌。
