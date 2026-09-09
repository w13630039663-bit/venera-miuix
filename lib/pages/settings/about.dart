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
                  onClick: () =>
                      launchUrlString("https://github.com/venera-app/venera"),
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
            child: Text("Check".tl),
            onPressed: _checkUpdate,
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
            launchUrlString("https://github.com/venera-app/venera");
          },
        ),
        ListTile(
          title: const Text("Telegram"),
          trailing: const Icon(Icons.open_in_new),
          onTap: () {
            launchUrlString("https://t.me/venera_release");
          },
        ),
      ],
    );
  }
}

Future<bool> checkUpdate() async {
  var res = await AppDio()
      .get("https://cdn.jsdelivr.net/gh/venera-app/venera@master/pubspec.yaml");
  if (res.statusCode == 200) {
    var data = loadYaml(res.data);
    if (data["version"] != null) {
      return _compareVersion(data["version"].split("+")[0], App.version);
    }
  }
  return false;
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
                    launchUrlString(
                        "https://github.com/venera-app/venera/releases");
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
bool _compareVersion(String version1, String version2) {
  var v1 = version1.split(".");
  var v2 = version2.split(".");
  for (var i = 0; i < v1.length; i++) {
    if (int.parse(v1[i]) > int.parse(v2[i])) {
      return true;
    }
    if (int.parse(v1[i]) < int.parse(v2[i])) {
      return false;
    }
  }
  return false;
}
