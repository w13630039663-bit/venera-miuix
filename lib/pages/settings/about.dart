part of 'settings_page.dart';

/// 「关于」区块 —— 已整合进设置首页，不再单独占一个分类。
///
/// 原先它是设置里的第 7 个分类页；按要求把它的全部内容搬到设置首页：
/// 应用图标 + 版本 + 简介 + 检查更新 + 启动时检查开关 + 项目链接，
/// 一屏内就能看到，不必再点进二级页。
///
/// 更新检查的两个函数（[checkUpdate] / [checkUpdateUi]）留在本文件：
/// `init.dart` 启动时也会调 [checkUpdateUi]。
class _AboutSection extends StatefulWidget {
  const _AboutSection();

  @override
  State<_AboutSection> createState() => _AboutSectionState();
}

class _AboutSectionState extends State<_AboutSection> {
  bool isCheckingUpdate = false;

  void _checkUpdate() {
    setState(() {
      isCheckingUpdate = true;
    });
    checkUpdateUi().then((value) {
      if (mounted) {
        setState(() {
          isCheckingUpdate = false;
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return _useMiuixStyle ? _buildMiuix() : _buildClassic();
  }

  Widget _buildMiuix() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
      child: Column(
        children: [
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(28),
            child: const SizedBox(
              width: 96,
              height: 96,
              child: Image(
                image: AssetImage("assets/app_icon.png"),
                filterQuality: FilterQuality.medium,
              ),
            ),
          ),
          const SizedBox(height: 10),
          MiuixText(
            "V${App.version}",
            fontSize: 16,
            fontWeight: FontWeight.w500,
          ),
          const SizedBox(height: 2),
          MiuixText(
            "Venera is a free and open-source app for comic reading.".tl,
            fontSize: 13,
            color: MiuixTheme.of(context).colors.onSurfaceVariantSummary,
          ),
          const SizedBox(height: 12),
          MiuixCard(
            colors: Theme.of(context).brightness == Brightness.dark
                ? MiuixCardColors(
                    color: context.colorScheme.surfaceContainerHigh,
                    contentColor: context.colorScheme.onSurface,
                  )
                : null,
            child: Column(
              children: [
                MiuixArrowPreference(
                  title: "Check for updates".tl,
                  endActions: <Widget>[
                    MiuixButton(
                      onPressed: isCheckingUpdate ? null : _checkUpdate,
                      minHeight: 32,
                      insideMargin: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 6,
                      ),
                      child: MiuixText(isCheckingUpdate ? "Checking".tl : "Check".tl),
                    ),
                  ],
                  onClick: isCheckingUpdate ? null : _checkUpdate,
                ),
                MiuixSwitchPreference(
                  title: "Check for updates on startup".tl,
                  value: appdata.settings['checkUpdateOnStart'],
                  onChanged: (v) {
                    setState(() {
                      appdata.settings['checkUpdateOnStart'] = v;
                    });
                    appdata.saveData();
                  },
                ),
                MiuixArrowPreference(
                  title: "Github",
                  endActions: const <Widget>[
                    Icon(Icons.open_in_new, size: 18),
                  ],
                  onClick: () => launchUrlString(kProjectUrl),
                ),
                MiuixArrowPreference(
                  title: "Original project (venera)".tl,
                  endActions: const <Widget>[
                    Icon(Icons.open_in_new, size: 18),
                  ],
                  onClick: () => launchUrlString(kUpstreamUrl),
                ),
                MiuixArrowPreference(
                  title: "Telegram",
                  endActions: const <Widget>[
                    Icon(Icons.open_in_new, size: 18),
                  ],
                  onClick: () => launchUrlString("https://t.me/venera_release"),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildClassic() {
    return Column(
      children: [
        SizedBox(
          height: 112,
          width: double.infinity,
          child: Center(
            child: Container(
              width: 112,
              height: 112,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(136),
              ),
              clipBehavior: Clip.antiAlias,
              child: const Image(
                image: AssetImage("assets/app_icon.png"),
                filterQuality: FilterQuality.medium,
              ),
            ),
          ),
        ).paddingTop(16),
        Column(
          children: [
            const SizedBox(height: 8),
            Text(
              "V${App.version}",
              style: const TextStyle(fontSize: 16),
            ),
            Text("Venera is a free and open-source app for comic reading.".tl),
            const SizedBox(height: 8),
          ],
        ),
        ListTile(
          title: Text("Check for updates".tl),
          trailing: Button.filled(
            isLoading: isCheckingUpdate,
            onPressed: _checkUpdate,
            child: Text("Check".tl),
          ).fixHeight(32),
        ),
        _SwitchSetting(
          title: "Check for updates on startup".tl,
          settingKey: "checkUpdateOnStart",
        ),
        ListTile(
          title: const Text("Github"),
          trailing: const Icon(Icons.open_in_new),
          onTap: () {
            launchUrlString(kProjectUrl);
          },
        ),
        ListTile(
          title: Text("Original project (venera)".tl),
          trailing: const Icon(Icons.open_in_new),
          onTap: () {
            launchUrlString(kUpstreamUrl);
          },
        ),
        ListTile(
          title: Text("Telegram (upstream)".tl),
          trailing: const Icon(Icons.open_in_new),
          onTap: () {
            launchUrlString("https://t.me/venera_release");
          },
        ),
      ],
    );
  }
}

/// 本分支（venera-miuix）的仓库坐标 —— 检查更新、文档跳转、问题反馈都以它为准。
///
/// 上游原项目是 venera-app/venera（GPL-3.0），本分支在其基础上重构与优化，
/// 因此**代码与文档链接一律指向本仓库**，避免用户按上游的版本号去下载
/// 与本分支不匹配的安装包。
///
/// 只改这三行即可切换到别的仓库（其余代码不依赖具体仓库名）。
const kProjectSlug = "w13630039663-bit/venera-miuix";
const kProjectRepoUrl = "https://github.com/$kProjectSlug";
const kProjectUrl = "$kProjectRepoUrl/tree/master";
const kProjectReleasesUrl = "$kProjectRepoUrl/releases";

/// 上游原项目（保留版权声明与出处）。
const kUpstreamUrl = "https://github.com/venera-app/venera";

/// 拉取远端最新版本号（形如 "1.6.3"）。
///
/// 两条通道，任一条成功即可：
/// 1. jsDelivr 上的 `pubspec.yaml` —— 有 CDN 缓存、无频率限制，是主通道；
/// 2. GitHub Releases API —— 拿 tag_name，覆盖"改了版本号但还没写进 pubspec"
///    的情况。缺点是有 60 次/小时的匿名频率限制，所以只作兜底。
Future<String?> _fetchRemoteVersion() async {
  try {
    var res = await AppDio().get(
      "https://cdn.jsdelivr.net/gh/$kProjectSlug@master/pubspec.yaml",
    );
    if (res.statusCode == 200) {
      var data = loadYaml(res.data);
      var version = data["version"];
      if (version is String && version.isNotEmpty) {
        return version.split("+").first;
      }
    }
  } catch (e) {
    Log.error("Check Update", "jsdelivr failed: $e");
  }
  try {
    var res = await AppDio().get(
      "https://api.github.com/repos/$kProjectSlug/releases/latest",
      options: Options(headers: {"Accept": "application/vnd.github+json"}),
    );
    if (res.statusCode == 200 && res.data is Map) {
      var tag = res.data["tag_name"];
      if (tag is String && tag.isNotEmpty) {
        return tag.replaceFirst(RegExp(r"^[vV]"), "").split("+").first;
      }
    }
  } catch (e) {
    Log.error("Check Update", "github api failed: $e");
  }
  return null;
}

Future<bool> checkUpdate() async {
  var remote = await _fetchRemoteVersion();
  if (remote == null) {
    return false;
  }
  return _compareVersion(remote, App.version);
}

Future<void> checkUpdateUi([bool showMessageIfNoUpdate = true, bool delay = false]) async {
  try {
    var value = await checkUpdate();
    if (value) {
      if (delay) {
        await Future.delayed(const Duration(seconds: 2));
      }
      showDialog(
          context: App.rootContext,
          builder: (context) {
            return ContentDialog(
              title: "New version available".tl,
              content: Text(
                      "A new version is available. Do you want to update now?"
                          .tl)
                  .paddingHorizontal(16),
              actions: [
                Button.text(
                  onPressed: () {
                    Navigator.pop(context);
                    launchUrlString(kProjectReleasesUrl);
                  },
                  child: Text("Update".tl),
                ),
              ],
            );
          });
    } else if (showMessageIfNoUpdate) {
      App.rootContext.showMessage(message: "No new version available".tl);
    }
  } catch (e, s) {
    Log.error("Check Update", e.toString(), s);
  }
}

/// return true if version1 > version2
///
/// 逐段比数字。容错处理：允许 "v" 前缀、"-beta" 之类的预发布后缀、
/// 以及段数不一致（1.7 与 1.7.0）—— 远端 tag 是用户自己打的，
/// 不能让一次格式随意直接抛异常把「检查更新」打断。
bool _compareVersion(String version1, String version2) {
  List<int> parse(String version) {
    return version
        .split(RegExp(r"[-+]"))
        .first
        .split(".")
        .map((e) => int.tryParse(RegExp(r"^\d+").stringMatch(e) ?? "") ?? 0)
        .toList();
  }

  var v1 = parse(version1);
  var v2 = parse(version2);
  var length = v1.length > v2.length ? v1.length : v2.length;
  for (var i = 0; i < length; i++) {
    var a = i < v1.length ? v1[i] : 0;
    var b = i < v2.length ? v2[i] : 0;
    if (a > b) return true;
    if (a < b) return false;
  }
  return false;
}
