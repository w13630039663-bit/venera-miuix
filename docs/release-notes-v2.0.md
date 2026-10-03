# venera-miuix v2.0

基于 [Venera](https://github.com/venera-app/venera)（原作者 @wgh136，GPL-3.0）的**原生重写**：界面与运行时全部换成 Kotlin + Jetpack Compose，Flutter / Dart / Rust 退出依赖树，并保留了本分支原有的 **Miuix（HyperOS 风）** 观感与**内容过滤**体系。

## ⚠️ 升级前必读：本版签名与旧版不同

本版换用了新的正式签名证书（SHA-256 `62b89c491bd6fca4…`），与 v1.6.6 及更早**不是同一把**。Android 按签名判定"是不是同一个应用"，所以：

- **覆盖安装一定会失败**（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`），需要先卸载旧版；
- 卸载会连带清掉收藏、历史、下载、画廊数据。**请先在旧版里「设置 → 备份与同步」导出 `.venera` 归档**，装好 v2.0 后再导入；
- 归档里带得走的：收藏与历史、阅读统计、画廊关注名单与画廊收藏、插图收藏（元数据与地址，图片文件不带）；带不走的：下载好的章节文件、漫画源脚本、设置项与登录态。

包名仍为 `com.github.w13630039663bit.venera.miuix`，与原版 Venera 不同，可与之共存，但无法覆盖安装原版。

## 选择哪个安装包

| 文件名 | 适用设备 | 体积 |
| --- | --- | --- |
| `venera-miuix-2.0.apk` | 通用（含全部 ABI，不确定机型选这个） | ~13.7 MB |
| `venera-miuix-2.0-arm64-v8a.apk` | 绝大多数现代手机（64 位） | ~13.7 MB |
| `venera-miuix-2.0-armeabi-v7a.apk` | 老旧 32 位设备 | ~13.7 MB |
| `venera-miuix-2.0-x86_64.apk` | 模拟器 / 部分 Chromebook | ~13.7 MB |

> 原生重写后没有引擎的 `libflutter.so`，按 ABI 分包只省几十 KB，四个包体积基本一致 —— 直接下通用包即可。
> 本版 `versionCode` 统一为 `2000`、`versionName` 为 `2.0`（本分支版本号自 v2.0 重新起算）。**系统要求 Android 13（API 33）及以上**，目标 API 37。

校验和（SHA-256）：

```
5f734332213c5095a79f22c640e89c63450c1d97c4875c03254d3cbae94890f1  venera-miuix-2.0.apk
8a6cc56ea1af37f28b993ccfc921857d8cf31ba9e559b3333cc6d3e2f2db1b73  venera-miuix-2.0-arm64-v8a.apk
80b82046e255378e0e9f4fc9463dbc15100f3a676553739dc73df046c38f97e0  venera-miuix-2.0-armeabi-v7a.apk
2c0646878d1ed9db8abe8a10ad58d960174a4b87087cfd015fa1043599e4e36d  venera-miuix-2.0-x86_64.apk
```

## 本版主要变化（相比 v1.6.6-miuix）

### 底座

- **运行时换成 Compose**。启动不再等 Flutter 引擎与 isolate；阅读器、详情页、下载、设置全部原生实现；release 包开 R8 混淆 + 资源收缩。
- **源脚本照旧可用**。系统 WebView 承载脚本，shim 层复刻原版 API 面；官方 `index.json` 支持全部更新 / 单独更新 / 自定义仓库地址 / 本地导入调试 / 可视化编辑。另加 MangaDex、拷贝漫画、包子漫画三个原生 Kotlin 源，作为 JS 引擎异常时的兜底。
- **正确性判据下沉成纯 JVM 函数**：分级判定、图片混淆分块、章节完整性、归档编解码、标签切分、Cookie 可注册域链，共 92 个测试文件 / 749 个用例，不需要设备。

### 新增

- **画廊模块**（底栏第 4 位）：yande.re / Gelbooru / Safebooru 三站聚合，每日热门与「猜你喜欢」推荐墙、画师关注与跨站作品聚合、反向搜图、独立播放器与独立图片缓存预算 —— 与漫画侧完全隔离，自带标签词典。
- **Cloudflare 优选 IP**：全局 IP 表 + 适用域名表，覆盖主站与图床域名，带线路测速页。默认关闭、不自带任何 IP。
- **备份与同步扩容**：`.venera` 归档（格式 v5）现在覆盖画廊关注、画廊收藏、插图收藏地址；并可直接导入**官方 Venera 的 SQLite 归档**与 **PicaComic 的 `.picadata`**，逐条合并。
- **收藏与多选**：收藏页四个面板（本地收藏 / 网络收藏 / 插图收藏 / 画廊收藏）共用同一套长按多选与批量操作；收藏夹管理、批量删除 / 移动 / 复制。
- **外观**：顶栏大标题折叠 + 磨砂圆座图标按钮、分段控制器自持药丸、液态玻璃底栏、封面 → 详情共享元素转场、跨 Activity 系统级预测式返回。
- **设置页重设计**：hero 头图 + 名言卡 + 分组重组。

### 已知缺口（写明白，不混进功能表）

- **章节评论**面板、**追更**开关、**日志查看器**、**源分级逐源纠正**界面：代码在但入口未接，当前用不了。
- **Gelbooru 必须自备 API 凭据**（匿名取不到图）；**反向搜图**需自备 SauceNAO key（匿名过不去 Cloudflare）。
- **备份不含图片文件本身**：插图收藏只带元数据与地址，恢复后按地址现加载 —— 离线是裂图，部分源的地址带临时签名（几天后失效）。
- **WebDAV 是手动上传 / 恢复**，没有自动同步调度。
- 不做：Windows / Linux / macOS / iOS、EPUB / CBZ 直读、生物识别隐私锁、DoH。

完整清单见 [README 的「已知缺口与明确不做」](https://github.com/w13630039663-bit/venera-miuix/blob/compose-migration/README.md)。

## 版权与许可

- 本分支基于 **Venera**（GPL-3.0）重写，遵循相同许可：**GPL-3.0**，源码可见、可再分发，**严禁闭源分发**。
- 上游出处：[venera-app/venera](https://github.com/venera-app/venera)（原作者 @wgh136）。
- 本仓库为**非官方分支**，与上游作者无隶属关系；使用第三方图源与画廊站点请遵守其服务条款。
