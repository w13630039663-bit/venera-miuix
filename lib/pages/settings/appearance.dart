part of 'settings_page.dart';

class AppearanceSettings extends StatefulWidget {
  const AppearanceSettings({super.key});

  @override
  State<AppearanceSettings> createState() => _AppearanceSettingsState();
}

class _AppearanceSettingsState extends State<AppearanceSettings> {
  @override
  Widget build(BuildContext context) {
    return SmoothCustomScrollView(
      slivers: [
        SliverAppbar(title: Text("Appearance".tl)),
        SelectSetting(
          title: "Theme Mode".tl,
          settingKey: "theme_mode",
          optionTranslation: {
            "system": "System".tl,
            "light": "Light".tl,
            "dark": "Dark".tl,
          },
          onChanged: () async {
            App.forceRebuild();
          },
        ).toSliver(),
        SelectSetting(
          title: "Theme Color".tl,
          settingKey: "color",
          optionTranslation: {
            "system": "System".tl,
            "red": "Red".tl,
            "pink": "Pink".tl,
            "purple": "Purple".tl,
            "green": "Green".tl,
            "orange": "Orange".tl,
            "blue": "Blue".tl,
          },
          onChanged: () async {
            await App.init();
            App.forceRebuild();
          },
        ).toSliver(),
        SelectSetting(
          title: "Navigation Bar".tl,
          settingKey: "navBarStyle",
          optionTranslation: {
            "classic": "Classic".tl,
            "floating": "Floating Glass".tl,
            "frosted": "Frosted".tl,
          },
          onChanged: () async {
            App.forceRebuild();
          },
        ).toSliver(),
        // 设置页画风：Miuix（小米 HyperOS 风格卡片）/ 经典 Material。
        SelectSetting(
          title: "Settings Style".tl,
          settingKey: "settingsStyle",
          optionTranslation: {
            "miuix": "Miuix".tl,
            "classic": "Classic".tl,
          },
          onChanged: () async {
            App.forceRebuild();
          },
        ).toSliver(),
        // 设置入口位置：首页右上角按钮，或底部导航栏的一个标签。
        SelectSetting(
          title: "Settings Entry".tl,
          settingKey: "settingsEntry",
          optionTranslation: {
            "topRight": "Top Right Corner".tl,
            "navBar": "Navigation Bar".tl,
          },
          onChanged: () async {
            App.forceRebuild();
          },
        ).toSliver(),
      ],
    );
  }
}
