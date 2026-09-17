# 上游 Venera 漫画源管理界面 —— 逐层复刻规格

> 上游仓库：`https://github.com/venera-app/venera`（Flutter，master @ `a0eba914`，2026-04-05）
> 核心文件：`lib/pages/comic_source_page.dart`（1263 行 / 42 KB）
> 本文目标：把上游界面拆到「能照着写」的粒度，供 Jetpack Compose 复刻。

---

## 0. 上游文件与依赖清单

| 文件 | 行数 | 职责 |
|---|---|---|
| `lib/pages/comic_source_page.dart` | 1263 | **源管理页本体**（页面 + 仓库清单 + 登录页 + 检查更新） |
| `lib/foundation/comic_source/comic_source.dart` | 403 | `ComicSourceManager` / `ComicSource` / `AccountConfig` 数据模型 |
| `lib/foundation/comic_source/parser.dart` | 1211 | JS 规则解析（产出 `ComicSource`） |
| `lib/foundation/comic_source/models.dart` | 478 | 漫画/章节等模型 |
| `lib/components/select.dart` | 259 | `Select` 下拉组件（select 型设置项用） |
| `lib/components/js_ui.dart` | 240 | `showInputDialog` / `showSelectDialog` 的 JS 桥实现 |
| `lib/components/message.dart` | 459 | `ContentDialog` / `showConfirmDialog` / `showLoadingDialog` / `showInputDialog` / `showSelectDialog` |
| `lib/components/pop_up_widget.dart` | 177 | `PopUpWidget` / `PopUpWidgetScaffold`（仓库清单容器） |
| `lib/components/button.dart` | 345 | `Button.filled/normal/outlined/text` / `Button.icon` |
| `lib/components/code.dart` | 360 | `CodeEditor`（编辑 .js 原文） |
| `lib/components/scroll.dart` | 376 | `SmoothCustomScrollView`（= `CustomScrollView` + 惯性物理） |

---

## 1. 页面整体骨架

```
Scaffold
└─ _Body (StatefulWidget，监听 ComicSourceManager 的 ChangeNotifier)
   └─ SmoothCustomScrollView            // 单一 CustomScrollView
      ├─ SliverAppbar("Comic Source", style: shadow)
      ├─ buildCard()                    // SliverToBoxAdapter：添加源区
      ├─ for source in ComicSource.all()  // SliverMainAxisGroup，每源一组
      │   ├─ SliverToBoxAdapter: 源标题行 ListTile
      │   ├─ SliverToBoxAdapter: 0.6px 分隔线
      │   ├─ SliverToBoxAdapter: Column(设置项)
      │   └─ SliverToBoxAdapter: Column(账号区)
      └─ SliverPadding(bottom: 安全区)
```

**关键设计判断（与你们的现状差异根源）**：
上游是**一条连续长列表**，源条目之间没有卡片、没有分组容器，
只有 `top:16` 的间距 + 一条 `0.6px outlineVariant` 底线。
所有源的设置项与账号操作**全部内联展开在列表里**，没有二级详情页。

---

## 2. 顶部「添加源」区（`buildCard`）

```
Column(crossAxisAlignment: start)
├─ ListTile(leading: Icons.dashboard_customize, title: "Add comic source")
│
├─ TextField(
│     hint: "URL",
│     border: UnderlineInputBorder,
│     contentPadding: EdgeInsets.symmetric(horizontal: 12),
│     suffix: IconButton(Icons.check) → handleAddSource(url),
│     onSubmitted: handleAddSource,
│   ).paddingHorizontal(16).paddingBottom(8)
│
├─ Wrap(spacing: 8, runSpacing: 8)
│  ├─ FilledButton.tonalIcon(Icons.article_outlined, "Comic Source list")
│  │     → showPopUpWidget(App.rootContext, _ComicSourceList(handleAddSource))
│  ├─ FilledButton.tonalIcon(Icons.file_open_outlined, "Use a config file")
│  │     → _selectFile()   // 选本地 .js
│  ├─ FilledButton.tonalIcon(Icons.help_outline, "Help")
│  │     → 打开 doc/comic_source.md
│  └─ _CheckUpdatesButton()
│        → FilledButton.tonalIcon(
│            icon: isLoading ? CircularProgressIndicator(18×18, strokeWidth 2) : Icons.update,
│            label: "Check updates")
│  ).paddingHorizontal(12).paddingVertical(8)
│
└─ SizedBox(height: 8)
```

### 2.1 添加源的三种入口行为

| 入口 | 行为 |
|---|---|
| URL 输入框 | `handleAddSource(url)`：按 `/` 切分取**最后一段**当 fileName → 拉取正文 → `addSource(js, fileName)` |
| 本地文件 | `_selectFile()`：文件选择器限 `.js` → `utf8.decode(bytes)` → `addSource(content, file.name)` |
| 仓库清单 | 见 §4 |

`addSource(js, fileName)` 内部：
```dart
var comicSource = await ComicSourceParser().createAndParse(js, fileName);
ComicSourceManager().add(comicSource);
_addAllPagesWithComicSource(comicSource);   // 把源的 explore/category/favorites/search 页注册进设置
appdata.saveData();
App.forceRebuild();
```

> `_addAllPagesWithComicSource` 会把源声明的
> `explorePages[].title` / `categoryData.key` / `favoriteData.key` / `searchPageData != null ? source.key`
> 分别并入 `appdata.settings` 的 `explore_pages` / `categories` / `favorites` / `searchSources`。
> 删除源时 `_validatePages()` 反向剔除这些键（否则会残留幽灵入口）。

---

## 3. 单个源条目（`_SliverComicSource`）—— 最核心部分

### 3.1 标题行 ListTile

```
ListTile(
  title: Row[
    Text(source.name, style: ts.s18),
    SizedBox(width: 6),
    Container(                                  // 版本胶囊
      padding: h8 v2,
      decoration: (surfaceContainer, radius 8),
      child: Text(source.version, fontSize: 13),
    ),
    if (hasUpdate)                              // hasUpdate = availableUpdates[key] 存在且语义化版本更大
      Tooltip(message: newVersion,
        child: Container(
          padding: h6 v2,
          decoration: (primaryContainer, radius 8),
          child: Text("New Version", fontSize: 13),
        )).paddingLeft(4),
  ],
  trailing: Row(min)[
    Tooltip("Edit")   IconButton(Icons.edit_note)  → edit(source),
    Tooltip("Update") IconButton(Icons.update)     → update(source),
    Tooltip("Delete") IconButton(Icons.delete)     → delete(source),
  ],
)
```

**注意：上游每个源只有 3 个操作图标（编辑 / 更新 / 删除）。**
没有启用开关、没有置顶、没有上下移、没有测速、没有「设为活跃」。

### 3.2 分隔线

```
Container(
  margin: EdgeInsets.symmetric(horizontal: 8),
  decoration: Border(bottom: BorderSide(color: outlineVariant, width: 0.6)),
)
```

### 3.3 设置项渲染（`buildSourceSettings()`）

取设置的顺序（重要）：
```dart
var settingsMap = source.getSettingsDynamic() ?? source.settings;
// getSettingsDynamic() 会实时从 JS 求值 ComicSource.sources.$key.settings，
// 以支持「用 getter 动态返回设置项」的源；失败则回落到解析时缓存的 settings。
if (source.data['settings'] == null) source.data['settings'] = {};
```

按 `type` 分四路，**全部是内联 `ListTile`，没有弹窗**（只有 input 的编辑动作才弹窗）：

| type | 渲染 | 落库 |
|---|---|---|
| `select` | `ListTile(title: title.ts(key), trailing: Select(current, values))` | `data['settings'][key] = options[i]['value']` → `saveData()` → `setState` |
| `switch` | `ListTile(title, trailing: Switch(current, onChanged))` | `data['settings'][key] = v` |
| `input` | `ListTile(title, subtitle: Text(current, maxLines 1, ellipsis), trailing: IconButton(Icons.edit))` | 弹 `showInputDialog(initialValue, inputValidator: RegExp(validator))` → `data['settings'][key] = value` |
| `callback` | `_CallbackSetting`: `ListTile(title: title.ts(key), trailing: Button.normal(buttonText.ts(key), isLoading))` | 调用 `settings[key].callback([])`，返回 Future 时按钮转 loading |

**`select` 的 current 解析逻辑**（容易写错的地方）：
```dart
var current = source.data['settings'][key];
if (current == null) {
  // 用 default 在 options 里反查显示文本
  for (var option in options) if (option['value'] == default) { current = option['text'] ?? option['value']; break; }
} else {
  current = options.firstWhere((e) => e['value'] == current)['text'] ?? current;
}
```

> 上游在标题上做了**源级翻译**：`title.ts(source.key)`。
> 即源可以在 `source.translations` 里声明自己的语言包，覆盖 UI 文案。

### 3.4 账号区（`_buildAccount()`）

```
if (source.account == null) return;              // 无账号配置的源，整块不渲染

if (!isLogged)  // isLogged = data["account"] != null
  ListTile(
    title: Text("Log in"),
    trailing: Icon(Icons.arrow_right),
    onTap: → context.to(_LoginPage(config: source.account!, source: source))
             // 之后 saveData() + setState()
  )

if (isLogged)
  for (item in source.account!.infoItems)        // ★ infoItems 可自定义 builder
    if (item.builder != null) item.builder!(context)
    else ListTile(title: item.title.tl, subtitle: item.data == null ? null : Text(item.data!()), onTap: item.onTap)

  if (source.data["account"] is List)            // 仅账号密码登录的源才有 Relogin
    ListTile(
      title: "Re-login",
      subtitle: "Click if login expired",
      trailing: loading ? CircularProgressIndicator(24×24, strokeWidth 2) : Icon(Icons.refresh),
      onTap: → account.login(account[0], account[1]) → 失败 showMessage / 成功 showMessage("Success")
    )

  ListTile(
    title: "Log out",
    trailing: Icon(Icons.logout),
    onTap: → data["account"] = null; account.logout(); saveData(); notifyStateChange(); setState
  )
```

> **`AccountInfoItem.builder`（`WidgetBuilder`）是最容易被漏掉的能力**：
> 源可以往账号区插入**任意自定义 Widget**（如显示头像、套餐剩余额度、开关等）。
> Compose 里需要等价物：一个「由源提供的 UI 片段」接口，
> 例如 `fun accountExtraWidget(context, source): @Composable () -> Unit`。

---

## 4. 仓库清单（`_ComicSourceList`）—— PopUpWidget 而非 Dialog

```
PopUpWidgetScaffold(title: "Comic Source", body: ListView.builder)
  itemCount = (json?.length ?? 1) + 1

  index == 0  → 顶部「Repo URL」卡片
    Container(margin: h8 v8, border: 0.6 outlineVariant, radius 8)
    └─ Column(start)
       ├─ ListTile(leading: Icons.source_outlined, title: "Repo URL")
       ├─ TextField(controller: repoUrl, hint: "URL",
       │             border: UnderlineInputBorder,
       │             contentPadding: h12,
       │             onChanged: → changed = true)
       │     .paddingHorizontal(16).paddingBottom(8)
       ├─ Text("The URL should point to a 'index.json' file").paddingLeft(16)
       ├─ Text("Do not report any issues related to sources to App repo.").paddingLeft(16)
       ├─ SizedBox(8)
       ├─ Row(end)[ TextButton("Help"), FilledButton.tonal("Refresh" → load()), SizedBox(16) ]
       └─ SizedBox(16)

  index == 1 && json == null → Center(CircularProgressIndicator(24×24, strokeWidth 2))

  其余 index（已 index--）→
    var key = json[index]["key"];
    trailing = currentKey.contains(key)                       // ← 按 key 判定已安装
        ? Icon(Icons.check, size: 20).paddingRight(8)
        : Button.filled(Text("Add"), onPressed: ...).fixHeight(32)
    subtitle = "$version\n$description"   // description 为空则只有 version
    ListTile(title: Text(json[index]["name"]), subtitle: Text(subtitle), trailing: trailing)
```

**Add 的 URL 解析（源清单支持相对路径）**：
```dart
var url = json[index]["url"];
if (url == null || !url.toString().isURL) {
  var listUrl = appdata.settings['comicSourceListUrl'];
  if (listUrl.replaceFirst("https://","").replaceFirst("http://","").contains("/")) {
    url = listUrl.substring(0, listUrl.lastIndexOf("/") + 1) + fileName;   // 同目录拼接
  } else {
    url = '$listUrl/$fileName';
  }
}
```

**容器样式（`PopUpWidget`）**：
- `barrierColor: Colors.black54`、可点背景关闭、`transitionDuration: 350ms`、`FadeTransition(Curves.ease)`
- 屏宽 > 500dp：居中弹层，**宽 500、高 = 屏高 × 0.9、圆角 12**，暗色下加 `Colors.white.withAlpha(50)` 阴影
- 屏宽 ≤ 500dp：**全屏**
- `PopUpWidgetScaffold` 顶栏：高 `56 + 状态栏`，`Icons.arrow_back_sharp` + 标题（22sp, w500）；
  滚动离顶时顶栏叠加 `surfaceTint.withAlpha(20)` 背景

> 与你们现状的关键差异：**上游仓库清单里的 Repo URL 是「可编辑 + 可 Refresh」的输入框**，
> 不是只读说明文字。用户能随时换仓库源。

---

## 5. 检查更新（`_CheckUpdatesButton` + `checkComicSourceUpdate`）

```dart
static Future<int> checkComicSourceUpdate() async {
  if (ComicSource.all().isEmpty) return 0;
  var res = await AppDio().get<String>(appdata.settings['comicSourceListUrl']);
  if (res.statusCode != 200) return -1;
  var list = jsonDecode(res.data!) as List;
  var versions = <String, String>{};
  for (var source in list) versions[source['key']] = source['version'];
  var shouldUpdate = <String>[];
  for (var source in ComicSource.all()) {
    if (versions.containsKey(source.key) && compareSemVer(versions[source.key]!, source.version))
      shouldUpdate.add(source.key);
  }
  if (shouldUpdate.isNotEmpty) ComicSourceManager().updateAvailableUpdates(updates);
  return shouldUpdate.length;
}
```

UI 反馈三态：`-1` → `showMessage("Network error")`；`0` → `showMessage("No updates")`；`>0` → `showUpdateDialog()`

`showUpdateDialog()`：
- `ContentDialog(title: "Updates", content: Text("name: version" 按行拼接))`，单个 `FilledButton("Update")`
- 确认后：`showLoadingDialog(message: "Updating", withProgress: true)` →
  逐个 `await ComicSourcePage.update(source, false)` → `loadingController.setProgress(current / total)`

> **注意版本比对用的是 `key` 而不是 fileName。**
> 这正是 `copy_manga` / `copy_manga_multi_accounts` 共用 key 时上游的灰色地带
> （两个文件只会匹配到同一个远程版本条目）。你们改用 fileName 是更严谨的做法 —— 这点建议保留。

---

## 6. 源的三个操作

### 6.1 删除 `delete(source)`
```dart
showConfirmDialog(
  title: "Delete",
  content: "Delete comic source '@n' ?".tlParams({"n": source.name}),
  btnColor: colorScheme.error,
  onConfirm: () {
    File(source.filePath).delete();
    ComicSourceManager().remove(source.key);
    _validatePages();       // 清掉 explore_pages/categories/favorites 里的幽灵键
    App.forceRebuild();
  },
);
```

### 6.2 编辑 `edit(source)`
- **桌面端**：`Process.run("code", [filePath])` 用 VS Code 打开，再弹 `AlertDialog("Reload Configs")`（cancel / continue）
- **移动端**：`context.to(_EditFilePage(filePath, onExit))`
  ```
  Scaffold(appBar: Appbar("Edit"))
    body: Column[
      Container(height: 0.6, color: outlineVariant),
      Expanded(CodeEditor(initialValue, onChanged)),
    ]
  // dispose() 时写回文件并回调 onExit（onExit 内部 reload() + setState）
  ```
  > Compose 里等价物：一个全屏文本编辑页（等宽字体 + 行号可选），退出时保存并重载源。

### 6.3 更新 `update(source)`
```dart
if (!source.url.isURL) { showMessage("Invalid url config"); return; }
ComicSourceManager().remove(source.key);            // ★ 先移除，避免解析中途被并发读到半成品
var controller = showLoadingDialog(barrierDismissible: false, onCancel: () => cancel = true);
try {
  var res = await AppDio().get<String>(source.url,
      options: Options(responseType: ResponseType.plain, headers: {"cache-time": "no"}));
  if (cancel) return;
  controller?.close();
  await ComicSourceParser().parse(res.data!, source.filePath);
  await io.File(source.filePath).writeAsString(res.data!);
  ComicSourceManager().availableUpdates.remove(source.key);
} catch (e) { ... }
await ComicSourceManager().reload();
App.forceRebuild();
```

---

## 7. 登录页（`_LoginPage`）—— 独立整页，不是弹窗

```
Scaffold(appBar: Appbar(title: ''))
└─ Center > Container(padding 16, maxWidth 400)
   └─ AutofillGroup > Column(min)
      ├─ Text("Login", fontSize: 24)
      ├─ SizedBox(32)
      ├─ if (config.cookieFields == null) TextField("Username", OutlineInputBorder,
      │        enabled: config.login != null, autofillHints: [username]).paddingBottom(16)
      ├─ if (config.cookieFields == null) TextField("Password", obscureText,
      │        enabled: config.login != null, autofillHints: [password],
      │        onSubmitted → login()).paddingBottom(16)
      ├─ for (field in config.cookieFields) TextField(field, obscureText,
      │        enabled: config.validateCookies != null).paddingBottom(16)
      ├─ if (login == null && cookieFields == null)
      │     Row[Icon(Icons.error_outline), SizedBox(8), Text("Login with password is disabled")]
      │  else
      │     Button.filled(isLoading, "Continue") → login()
      ├─ SizedBox(24)
      ├─ if (loginWebsite != null) TextButton("Login with webview")
      ├─ SizedBox(8)
      └─ if (registerWebsite != null)
            TextButton(Row[Icon(Icons.link), SizedBox(8), Text("Create Account")])
```

### 7.1 两种登录分支

| 分支 | 条件 | 行为 |
|---|---|---|
| 账号密码 | `config.login != null` | 校验非空 → `login(username, password)`，成功 `pop()`，失败 `showMessage` |
| Cookie 直填 | `config.validateCookies != null` | 收集 `cookieFields` 的值 → `validateCookies(cookies)`，成功则 `data['account'] = 'ok'` |

### 7.2 网页登录 `loginWithWebview()` —— **这块你们目前是缺的**

```dart
void validate(InAppWebViewController c) async {
  if (config.checkLoginStatus != null && config.checkLoginStatus!(url, title)) {
    var cookies = (await c.getCookies(url)) ?? [];
    var items = await c.webStorage.localStorage.getItems();
    var mappedLocalStorage = { for (var i in items) if (i.key != null) i.key!: i.value };
    source.data['_localStorage'] = mappedLocalStorage;
    await source.saveData();
    SingleInstanceCookieJar.instance?.saveFromResponse(Uri.parse(url), cookies);
    success = true;
    config.onLoginWithWebviewSuccess?.call();
    App.mainNavigatorKey?.currentContext?.pop();
  }
}

await context.to(() => AppWebview(
  initialUrl: config.loginWebsite!,
  onNavigation: (u, c) { url = u; validate(c); return false; },
  onTitleChange: (t, c) { title = t; validate(c); },
));
if (success) { source.data['account'] = 'ok'; source.saveData(); context.pop(); }
```

**三个必须抓住的点**：
1. 内嵌 WebView 打开 `loginWebsite`
2. 每次**导航变化或标题变化**都调 `checkLoginStatus(url, title)` 判定是否登录成功
3. 命中后**同时抓 `cookies` 和 `localStorage`** 并落库：
   - cookies → 全局 CookieJar
   - localStorage → `source.data['_localStorage']`（很多源靠它取 token）

> 你们现在的实现是「用系统浏览器打开链接」，**既不判定登录成功、也不回抓 cookie/localStorage**，
> 所以 `copy_manga` / `picacg` 这类必须网页登录的源**实际上登不进去**。这是本次复刻要重点补的能力。

---

## 8. 数据契约（源持久化格式）

**文件路径**：`{App.dataPath}/comic_source/{key}.data`（JSON）

```jsonc
{
  "settings": { "<settingKey>": <value> },        // 四类设置项的值
  "account": ["user", "pass"] | "ok" | null,      // List = 账密登录；"ok" = cookie/webview 登录
  "_localStorage": { "<k>": "<v>" }               // 网页登录抓取的 localStorage 快照
}
```

**`ComicSourceManager` 内存态**：
- `_sources: List<ComicSource>` —— 顺序 = `comic_source/` 目录枚举顺序（**无排序/无启用位**）
- `_availableUpdates: Map<sourceKey, newVersion>`
- 初始化：`doInit()` 遍历目录，逐个 `ComicSourceParser().parse(content, absolutePath)`
- `reload()`：清空 + 重置 JS 侧 `ComicSource.sources = {}` + 重新 `doInit()` + `notifyListeners()`

---

## 9. 组件规格（复刻必需的样式细节）

### 9.1 `Select`（select 型设置项）
```
Container(border: 1px outlineVariant, radius 4)
└─ InkWell(onTap: 弹菜单)
   └─ Row(min)[
        ConstrainedBox(minWidth: minWidth - 32)[Text(current, ts.s14)],
        SizedBox(8),
        Icon(Icons.arrow_drop_down, color: primary),
      ].padding(h12 v4)

showMenu(
  elevation: 3,
  color: light ? #F6F6F6 : #1E1E1E,
  useRootNavigator: true,
  constraints: minWidth = maxWidth = 触发体宽,
  position: RelativeRect.fromLTRB(dx, dy + h + 2, dx + h + 2, dy),
  items: [PopupMenuItem(height: mobile ? 46 : 40, child: Text(e))],
)
```

### 9.2 `Button`
| 类型 | 背景 | 文字色 | 说明 |
|---|---|---|---|
| `filled` | `primary` | `onPrimary` | hover 时 0.9 透明度 |
| `normal` | `surfaceContainer` | `onSurface` | hover 时 0.9 + 阴影 |
| `outlined` | 透明 + 0.6px `outlineVariant` 描边 | `primary` | |
| `text` | 透明 | `primary` | hover 背景 `outline.toOpacity(0.2)` |

公共：`radius 16`、`minWidth 76`、`minHeight 32`、`padding h16`、动画 160ms；
loading 时内部换成 `CircularProgressIndicator(strokeWidth 1.8, 16×16)`。

### 9.3 `ContentDialog`（所有对话框的统一容器）
```
Dialog(
  shape: radius 8 + (暗色时 outlineVariant 描边，亮色无描边),
  insetPadding: width < 400 ? h4 : h16,
  elevation: 2,
  backgroundColor: surface,
)
└─ AnimatedSize(200ms, topCenter)
   └─ IntrinsicWidth > ConstrainedBox(maxWidth: 600, minWidth: min(400, width - 16))
      └─ SingleChildScrollView > Column(start)[
           title != null ? Appbar(leading: IconButton(Icons.close), title: Text(title), 透明背景) : shrink,
           content,
           SizedBox(16),
           Row(end)[...actions].paddingRight(12),
           SizedBox(16),
         ]
```

### 9.4 `showInputDialog`
`ContentDialog` + `[可选 108 高图片]` + `TextField(OutlineInputBorder, errorText)` + `Button.filled("Confirm")`
- `inputValidator`（`RegExp`）不匹配 → `errorText = "Invalid input"`
- `onConfirm` 返回非 null → 把返回的字符串当 `errorText` 显示（不关闭）

### 9.5 `showSelectDialog`
`ContentDialog` + 居中 `Select(minWidth: 156)` + `TextButton("Cancel")` + `FilledButton("Confirm")`
（未选中时 Confirm 置灰）

---

## 10. 与当前 Compose 实现的差异对照

### 10.1 你们**多出来**的功能（上游没有）

| 功能 | 上游 | 说明 |
|---|---|---|
| 启用 / 禁用开关 | ❌ 无 | 上游靠 `searchSources` 设置在**搜索页**选择参与源 |
| 置顶 / 上移 / 下移 | ❌ 无 | 上游顺序 = 目录枚举顺序 |
| 连通性测速 + 延迟显示 | ❌ 无 | |
| 「设为活跃」源 | ❌ 无 | |
| 一键安装全部官方源 | ❌ 无 | 上游只有「Check updates」全量更新有更新的源，没有「全量安装」 |
| 批量安装进度条 | 部分（仅批量更新时有进度） | |

> ⚠️ **这与交接文档里「1:1 还原原版源管理系统（拖拽排序置顶、启用禁用）」的描述矛盾** ——
> 上游**根本没有**这些。需要你确认是「严格 1:1 去掉」还是「上游为底 + 保留增强」。

### 10.2 你们**缺失**的功能（上游有，建议必须补）

| 缺失项 | 影响 |
|---|---|
| **网页登录内嵌 WebView + cookie/localStorage 回抓** | 高 —— 依赖网页登录的源（copy_manga、picacg 等）实际登不进去 |
| **单源「更新」按钮**（按 `source.url` 重下） | 高 —— 现在只能从仓库清单更新 |
| **单源「编辑」**（改 .js 原文） | 中 —— 调试自定义规则必需 |
| **「检查更新」独立按钮 + 弹窗列出可更新项 + 进度** | 中 |
| **Repo URL 可编辑 + Refresh** | 中 —— 现在仓库地址是写死的常量 |
| **`AccountInfoItem.builder` 自定义账号区 Widget** | 中 —— 部分源靠它展示套餐/额度/开关 |
| **多语言 `.tl` + 源级 `.ts(sourceKey)`** | 低（但影响文案一致性） |
| **`Select` 用原生下拉菜单**（非自定义 Dialog） | 低（体验差异） |
| **`getSettingsDynamic()` 动态设置项** | 低 —— 支持 getter 动态返回设置 |
| **删除源时清理 explore/category/favorites 幽灵键** | 中 —— 否则设置里残留失效入口 |

---

## 11. 复刻工作清单（按优先级）

- [ ] **P0** 网页登录：内嵌 WebView + `checkLoginStatus` 轮询 + cookie/localStorage 回抓落库
- [ ] **P0** 单源操作三件套：编辑（.js 原文）/ 更新（按 url 重下）/ 删除（含幽灵键清理）
- [ ] **P0** 页面骨架改为**连续列表**：去掉卡片，改 `ListTile` + 0.6px 分隔线 + top16 间距（决策待定，见 §10.1）
- [ ] **P1** Repo 清单改为 PopUp 容器 + Repo URL 可编辑 + Refresh
- [ ] **P1** 检查更新按钮 + 版本弹窗 + 带进度批量更新
- [ ] **P1** `Select` 改为原生下拉菜单样式（border 1px outlineVariant / radius 4 / arrow_drop_down primary）
- [ ] **P2** `AccountInfoItem.builder` 等价物
- [ ] **P2** `inputValidator` 正则校验接入
- [ ] **P2** 源级翻译 `.ts(sourceKey)`

---

## 附：`_.ts(sourceKey)` 与 `.tl` 的含义

- `.tl` = translate（全局 UI 文案翻译，跟随 App 语言）
- `.ts(sourceKey)` = translate-scoped（**该源专属的语言包**，读 `source.translations[lang][text]`）

复刻时若不做多语言，至少要让「源提供的文案」原样显示，不要被全局语言包改写。

---

## 12. 实施记录与上游事实校正（2026-09-16 二次核实）

以下四条是**逐行读过上游源码**后确认的事实，用于纠正早期推断：

### 12.1 `AccountInfoItem.builder` 是**死代码** —— 本项目无需复刻

```dart
// comic_source.dart:334-343
const AccountConfig(
  this.login, this.loginWebsite, this.registerWebsite, this.logout,
  this.checkLoginStatus, this.onLoginWithWebviewSuccess, this.cookieFields, this.validateCookies,
) : infoItems = const [];      // ← 恒为空列表

// comic_source_page.dart:1017
for (var item in source.account!.infoItems) { ... }   // ← 永不执行
```

`infoItems` 被硬编码成 `const []`，全仓库没有任何地方给它赋值。
因此 `for (item in infoItems)` 与 `AccountInfoItem.builder` **永远触发不到**。

> 结论：§10.2 里「`AccountInfoItem.builder` 自定义账号区 Widget」一项**从缺口中移除** ——
> 上游自己都做不到。本项目的 `JsComicSource.getAccountInfo()` 会真的从 JS 读取
> `s.account.infoItems[]`（含 `title` / `data()`），是**上游能力的超集**。

### 12.2 `onReceivedTitle` 在 compileSdk 37 已从 `WebViewClient` 移除

核实方式（可复现）：

```bash
# 直接在 SDK 桩源码里查签名
python -c "import zipfile;z=zipfile.ZipFile(r'<SDK>\platforms\android-37.0\android-stubs-src.jar');\
print([l for l in z.read('android/webkit/WebViewClient.java').decode().splitlines() if 'onReceivedTitle' in l])"
# → []    （WebViewClient 已无此方法）
```

- `WebViewClient` 在 API 37 里**没有** `onReceivedTitle`（实测 `'onReceivedTitle' overrides nothing.`）
- 该回调现在只存在于 **`WebChromeClient`**
- 另：`WebSettings.setDatabaseEnabled` 在 API 35 起废弃（localStorage 靠 `domStorageEnabled` 即可）

> 复刻网页登录时，标题变化信号必须挂 `WebChromeClient.onReceivedTitle`；
> 只挂 `WebViewClient` 会编译失败。

### 12.3 `inputValidator` 是 **hasMatch（子串匹配）**，不是全匹配

```dart
// message.dart:402-406
if (inputValidator != null && !inputValidator.hasMatch(controller.text)) {
  setState(() => error = "Invalid input");
  return;                      // ← 不关闭弹窗
}
```

Kotlin 对应物是 `Regex.containsMatchIn(text)`，**不是** `Regex.matches(text)`。
用错会让「邮箱/域名类」宽松正则的源无法保存设置。

### 12.4 `availableUpdates` 官方按 **key** 索引，本项目改为 **fileName**

官方 `checkComicSourceUpdate()` 用 `versions[source['key']] = source['version']` 建表。
而官方 index.json 有 33 条记录却只有 **32 个唯一 key**：

| fileName | key |
|---|---|
| `copy_manga.js` | `copy_manga` |
| `copy_manga_multi_accounts.js` | `copy_manga` |

于是这两个文件会匹配到同一份远端版本，其中一个必然被误判为「有新版本」。
本项目改用 fileName 作键（`SourceRow.latestVersion`），属于**有意的严谨化偏离**。

---

## 13. 本轮实施状态

| 项 | 状态 | 落地位置 |
|---|---|---|
| P0 网页登录（内嵌 WebView + cookie/localStorage 回抓） | ✅ 完成 | `WebLoginScreen.kt`、`ComicSource.kt`、`JsComicSource.kt`、`ComicSourceManager.kt`、`ComicSourceViewModel.kt` |
| P0 单源「编辑 / 更新 / 删除」 | ✅ 完成 | `SourceEditScreen.kt`、`ComicSourceManager.saveSourceText/updateSource`、`uninstallSource`（幽灵态清理） |
| P0 页面骨架改连续列表（去卡片） | ⏸ 按决策暂缓 | 见 §10.1，用户选择「先补缺失，布局暂不动」 |
| P1 仓库清单 Repo URL 可编辑 + Refresh | ✅ 完成 | `ComicSourceManager.repoUrl/setRepoUrl`、`ComicSourceScreen` 官方清单弹窗 |
| P1 检查更新按钮 + 版本弹窗 + 批量更新进度 | ✅ 完成 | `ComicSourceManager.checkUpdates`、`ComicSourceViewModel.checkUpdates/updateAllAvailable` |
| P1 `Select` 改原生下拉菜单样式 | ⏸ 待定 | 现状为自定义 Dialog，功能等价、样式不同 |
| P2 `AccountInfoItem.builder` | ❌ 移除（上游死代码） | 见 §12.1 |
| P2 `inputValidator` 正则校验 | ✅ 完成 | `ComicSourceScreen` Input 编辑弹窗（`containsMatchIn`） |
| P2 源级翻译 `.ts(sourceKey)` | ⏸ 未做 | 低优先 |
| — 删除源时清理幽灵键 | ✅ 完成 | `uninstallSource` + `JsSourceDataStore.evict()`（新增，修内存缓存不失效的缺陷） |

### 附带修复的两个真实缺陷

1. **`JsSourceDataStore` 内存缓存不失效**：卸载源只删磁盘文件，`cache`/`defaultSettings`
   仍在内存里，重装同一源会读到残留 settings/account（幽灵登录态）。新增 `evict()`。
2. **`installJsSource` 的 `fetchSourceScript` 地址拼接**：原实现
   `repoUrl.substringBeforeLast('/') + "/" + fileName` 在仓库地址为裸域名时
   会拼出 `https:/fileName`。改为对齐官方的分支判断（`concatRepoFileName`）。

---

## 14. 真机缺陷修复（ehentai 搜索 / picacg 登录）

### 14.1 `search` 的 options 恒为空数组 —— ehentai 报 `"undefined" is not valid JSON`

**报错逐字复现**：`ehentai.js:488` 是 `let category = JSON.parse(options[0]);`，
旧实现 `JsComicSource.search()` 硬编码传 `[]` → `options[0] === undefined`
→ `JSON.parse(undefined)` → `SyntaxError: "undefined" is not valid JSON`。

**官方语义（必须严格照搬）**

`parser.dart::_loadSearchData()`：
- 解析 `optionList[i].options`（形如 `"0-Doujinshi"`），**空串或不含 `-` 的项跳过**；
- 以**第一个** `-` 切分：键 = 前段，值 = 后段（`split.removeAt(0)` + `join("-")` 等价写法）；
- `defaultVal = element['default'] == null ? null : jsonEncode(element['default'])`。

`comic_source.dart::SearchOptions`：
- `String get defaultValue => defaultVal ?? options.keys.firstOrNull ?? ""`。

`search_page.dart::useDefaultOptions()`：
- `options = searchOptions.map((e) => e.defaultValue).toList()`，最终 `jsonEncode(options)` 传给源。

⇒ **每个元素要么是 `null`，要么是「JSON 编码后的字符串」**：
- `multi-select` 源拿到 `'["0","1"]'`（ehentai 正是 `JSON.parse` 它）
- `select` 源拿到 `'"value"'`（**含引号**，官方就是这样，保持一致）
- 无 `default` 时回退到 **options 首个插入键**（可能是空串，如 `"-<none>"` 的键）

**实现落地**：`venera-shim.js` 新增 `_veneraOptionValues(optionList)`。
⚠️ 必须**手动维护插入顺序**数组，不能用 `Object.keys()` —— JS 会把 `"0".."9"`
这类整数键提前，而官方是 Dart `LinkedHashMap`，两者顺序不同。

**影响面**：33 个源里约 **20 个**在 search/category 读 `options[0..3]`，
其中 `ykmh.js:305`、`manhuagui.js:791`、`kavita.js:297` 还会 `.split(","/"-")`
→ 空数组时直接 TypeError。**这是"搜索搜不到"的一大来源。**

**顺带修复的分页缺陷**：官方 `search.loadNext(keyword, options, next)` 的 `next`
来自搜索页状态（第 1 页 `null`，之后传上一页的 `res.next`）。
旧实现恒传 `null` → 第 2 页起**永远重复第 1 页**。
新增 `JsComicSource.nextTokenCache`（键 = `keyword + 页码`），未命中退化为 `null`。

### 14.2 Gson 默认丢弃 map 里的 null —— 判空链路失效

**实证（gson 2.11.0，单文件程序）**

| 调用 | 输出 |
|---|---|
| `Gson().toJson(mapOf("__result__" to null))` | `{}` |
| `GsonBuilder().serializeNulls().create().toJson(同左)` | `{"__result__":null}` |

**链路**：JS shim 收到 `"{}"` → 无 `__result__` 键 → 返回 `_deserializeResult({})` = `{}`
→ 官方 `loadSetting` 的判空 `res !== null && res !== undefined` **被绕过** → 不回落 `default`。

**仿真对照**（`bridge-harness.js`）：

| serializeNulls | `loadSetting('base_url')` | `loadData('token')` | `!token` |
|---|---|---|---|
| false（旧） | 正常（有 default，不走回落分支） | `{}` | **false** ❌ |
| true（新） | 正常 | `null` | **true** ✅ |

**修复**：`VeneraJsEngine` 新增 `bridgeGson = GsonBuilder().serializeNulls().create()`，
所有 `__result__` / `__error__` 信封序列化统一改用它。

**受影响的是「键不存在」的 `loadData` 判空**，例如 `comic_walker.js`（`if (!token)` 决定是否先取
token）、`ccc.js`、`copy_manga.js`、`manhuagui.js`（`mhg_cookie`）。

> ⚠️ **注意别误判**：picacg 的 4 个设置**都有 `default`**，Kotlin `loadSetting` 会直接返回默认值，
> 根本不经过 JS 的回落分支 —— 所以此 bug **不是** picacg 登录失败的根因。

### 14.3 picacg「无法登录」的取证结论

无设备条件下建了 Node 端到端仿真台（`vm.runInThisContext` 载入真实 shim/init/源文件 +
完整模拟 Kotlin 桥），结论：

| 验证项 | 结果 |
|---|---|
| 真实源 JS 调 `account.login()` → **真实 API** | `HTTP 400 {"error":"1004","message":"invalid email or password"}` |
| 推论 | **签名 / header / 请求格式全部被服务端接受**（签名错会返回 1005 一类） |
| `getAccountInfo` 探针（脚本从 Kotlin 源码抽取，非手抄） | picacg `hasAccount=true`、`supportsPasswordLogin=true` → **登录入口会出现** |
| ehentai 探针 | `loginWebsite=…Login&CODE=00`、`cookieFields=[ipb_member_id, ipb_pass_hash, igneous, star]` ✅ 印证 §12 的字段名修复 |
| 账号落库契约 | 与上游一致：`data["account"] = [account, pwd]`（`parser.dart:214`）；网页/Cookie 登录置 `"ok"`（`comic_source_page.dart:1229/1285/1303`） |

⇒ picacg 失败只剩两种可能，**都不是我们这侧的逻辑 bug**：
1. **手机侧网络到 `picaapi.picacomic.com` 不通**（PC 侧探测可达，但 PC ≠ 手机网络）；
2. **账密本身不对** —— picacg 脚本对任何非 200 都 `throw 'Failed to login'`，
   **把服务端真实原因吞掉了**。这是上游脚本自身限制，为保持"与官方一致"未改动。

**另注**：picacg 的 `search` / `explore` 都需要登录态；未登录时服务端返回 401，
脚本会走 `account.reLogin()` → 抛 `Not logged in`。
所以「可用性测试」对 picacg 在**未登录时必然失败**，属预期行为。

**为下一轮取证加的钩子**：`ComicSourceViewModel` 增 `TAG = "VeneraSourceOp"`，
登录失败与可用性测试失败均 `Log.w` → `adb logcat -s VeneraSourceOp` 直接看原因。

---

## §14 picacg「400 / 无法登录」定案（**修正上一节的推断**）

上一节把 picacg 失败归因于「网络不通 或 账密错」。**实测证明两个都不对。**

### 真实根因

**picaapi 服务端按出口 IP + 时间窗限流**，返回：

```json
{"code": 400, "error": "1023", "message": "too many requests"}
```

picacg.js 对任何非 200 一律 `throw 'Failed to login'`（登录）或
`throw 'Invalid status code: ' + res.status`（搜索），于是这个 1023 被压成了
用户看到的「登录失败」与「不可用 —— Invalid status code: 400」。

### 取证链（全部实测）

| # | 实验 | 结果 | 推论 |
|---|---|---|---|
| 1 | 在 **App 内活引擎**求值 `buildHeaders()` | `signature` 非空 string、`accept`/`base_url` 正确、`Convert.hmacString` 正常 | 我们生成的请求是完整的 |
| 2 | 篡改 signature 内容 / 删掉 signature | 篡改 → `200`；缺失 → `400 必須更新！` | 签名**只校验存在性**，问题不可能在签名算法 |
| 3 | App 内包裹 `Network.post` 录制真实请求 | `POST /comics/advanced-search?page=1` → `400 1023`；`POST /auth/sign-in` → `400 1023` | 400 的真实原因是限流 |
| 4 | **同刻双出口对照**：电脑 Node 直连 vs 手机 App 内 | 电脑 → `400 1004 invalid email or password`（被接受）；手机 → `400 1023`（被限） | 限流按**出口 IP**，与代码无关 |
| 5 | 交替发「官方常量 `defaultUuid`」与「随机 uuid」共 6 次 | 全部 `1004`（窗口已过） | `app-uuid` 与限流**无关** |

### 结论

picacg 能否使用取决于**手机出口 IP 是否被限流**（常见于共享代理 / 运营商 NAT 出口）。
换网络、更换代理出口，或等窗口过去即可；**不需要改动 picacg.js**（保持与官方一致）。

### 顺带修出的两个真缺陷

1. **`delay` / `setTimeout` 无限递归**（高影响）
   `venera-init.js:19` 用函数声明覆写全局 `setTimeout` 走桥的 delay 通道，
   而 `venera-shim.js` 的 delay 分支又调 `window.setTimeout`（此时已被覆写）
   → `sendMessage → setTimeout → sendMessage → …` 爆栈。
   受影响源：`copy_manga` / `copy_manga_multi_accounts` / `hot_manga` / `mxs`，
   且全在重试与限速等待路径上。
   修法：shim 在 init.js 之前捕获原生实现 `_nativeSetTimeout`，delay 分支只走它。
2. **失败提示无信息量**
   新增 `SourceHttpDiagnostics`（HTTP 层记录 4xx/5xx 的服务端原文，环形 16 条 + 20s 时间窗）
   与顶层函数 `explainSourceFailure()`（状态码 → 可执行建议），
   接入「可用性测试」与「登录失败」两条提示；`JsHttpHandler` 增 `VeneraHttp` logcat 日志。

### 已上机验证的效果

真机点 picacg「连通性测试」，logcat：

```
W VeneraHttp:     HTTP 400 POST .../comics/advanced-search?page=1 :: { "code":400, "error":"1023", "message":"too many requests" }
W VeneraSourceOp: test failed: picacg -> Invalid status code: 400（服务端 400: too many requests）｜服务端限流，请稍后重试；若走代理，多半是代理出口 IP 被限
```

⇒ 用户不再看到没有信息量的裸状态码。

---

## 附：OkHttp 与官方 Dart HttpClient 的行为差异（必读）

复刻源系统时，源脚本与 init.js 都是官方原文件，所以问题几乎都出在
我们重写的原生侧。以下差异均已造成过真实故障：

| # | 官方 Dart 语义 | OkHttp 行为 | 后果 |
|---|---|---|---|
| 1 | autoUncompress=true 始终解压 | 调用方一旦自定义 Accept-Encoding 就不解压 | 拿到 1F 8B 二进制，JSON.parse 报 Unexpected token |
| 2 | Uint8List 传给 JS 是真 ArrayBuffer | 我们的桥返回 __bytes_base64__ 对象 | new Uint8Array(obj).length===0，hexEncode 空串，AES key 为空 |
| 3 | Dio 按 header 的 Content-Type 编码 data | BridgeInterceptor 用 body.contentType() 覆盖同名 header | form 数据被当 JSON，登录报「字段不能留空」 |
| 4 | _convert 的 default 分支返回 value | 我们曾返回 null | 未支持类型被吞成 null |
| 5 | random 的 int 版范围是左闭右开 | 曾写成闭区间含 max | 源脚本当数组下标会越界 |

**两个必须记住的 OkHttp 反直觉点：**

- BridgeInterceptor 会无条件用 body.contentType() 覆盖 Content-Type header，
  所以「源脚本声明的 Content-Type 说了算」必须显式实现。
- 只有调用方没设 Accept-Encoding 时才自动解压；且解压成功后会
  移除 Content-Encoding 头，所以判据是：头还在就说明还没解压。

### 本轮修复后扫描结果（6 个源，build 00:18）

| 源 | 结果 |
|---|---|
| jm | 可用，45 条（原为 JSON 解析失败） |
| ehentai | 可用，25 条（原为 No url provided） |
| nhentai | 可用，25 条 |
| baozi | 超时 |
| Komiic | 400，响应体为 EOF |
| picacg | 400（1023 限流，已确认是服务端） |

后三个均非代码问题：PC 侧对 cn.baozimhcn.com 与 komiic.com 的 TLS 握手
全部成功，而手机侧一个超时、一个收到 EOF。EOF 是 Go 系代理（Clash/V2Ray 等）
连接上游失败时的典型响应，不是业务 JSON，说明手机代理对部分域名不稳定。
