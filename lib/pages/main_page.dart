import 'package:flutter/material.dart';
import 'package:flutter_miuix/miuix.dart';
import 'package:venera/foundation/appdata.dart';
import 'package:venera/pages/categories_page.dart';
import 'package:venera/pages/search_page.dart';
import 'package:venera/pages/settings/settings_page.dart';
import 'package:venera/utils/translations.dart';

import '../components/components.dart';
import '../foundation/app.dart';
import 'explore_page.dart';
import 'favorites/favorites_page.dart';
import 'home_page.dart';

class MainPage extends StatefulWidget {
  const MainPage({super.key});

  @override
  State<MainPage> createState() => _MainPageState();
}

class _MainPageState extends State<MainPage> {
  late final NaviObserver _observer;

  GlobalKey<NavigatorState>? _navigatorKey;

  void to(Widget Function() widget, {bool preventDuplicate = false}) async {
    if (preventDuplicate) {
      var page = widget();
      if ("/${page.runtimeType}" == _observer.routes.last.toString()) return;
    }
    _navigatorKey!.currentContext!.to(widget);
  }

  void back() {
    _navigatorKey!.currentContext!.pop();
  }

  /// 除设置标签页之外的页面数量（Home / Search / Favorites / Explore /
  /// Categories）。
  static const int _kContentPageCount = 5;

  /// 页面列表必须**每次 build 动态构建**，不能缓存成字段：
  /// 「外观 → 设置入口位置」切换后 App.forceRebuild 只会 markNeedsBuild
  /// （不重建 State），若列表还是旧值，pageBuilder 会索引越界或与标签错位。
  List<Widget> get _pages => <Widget>[
        const HomePage(),
        const SearchPage(
          key: PageStorageKey('search'),
        ),
        const FavoritesPage(
          key: PageStorageKey('favorites'),
        ),
        const ExplorePage(
          key: PageStorageKey('explore'),
        ),
        const CategoriesPage(
          key: PageStorageKey('categories'),
        ),
        if (!_settingsInTopRight)
          const SettingsPage(
            key: PageStorageKey('settings'),
            embedded: true,
          ),
      ];

  @override
  void initState() {
    _observer = NaviObserver();
    _navigatorKey = GlobalKey();
    App.mainNavigatorKey = _navigatorKey;
    index = int.tryParse(appdata.settings['initialPage'].toString()) ?? 0;
    // 设置入口搬到右上角后底部只剩 4 个标签；用户若此前把启动页设成了
    // 「设置」，索引会越界 —— 收回最后一个有效页。
    final maxIndex = _settingsInTopRight ? _kContentPageCount - 1 : _kContentPageCount;
    if (index > maxIndex) index = maxIndex;
    super.initState();
  }

  List<PaneItemEntry> get _paneItems => <PaneItemEntry>[
        PaneItemEntry(
          label: 'Home'.tl,
          icon: Icons.home_outlined,
          activeIcon: Icons.home,
        ),
        PaneItemEntry(
          label: 'Search'.tl,
          icon: Icons.search_outlined,
          activeIcon: Icons.search,
        ),
        PaneItemEntry(
          label: 'Favorites'.tl,
          icon: Icons.local_activity_outlined,
          activeIcon: Icons.local_activity,
        ),
        PaneItemEntry(
          label: 'Explore'.tl,
          icon: Icons.explore_outlined,
          activeIcon: Icons.explore,
        ),
        PaneItemEntry(
          label: 'Categories'.tl,
          icon: Icons.category_outlined,
          activeIcon: Icons.category,
        ),
        if (!_settingsInTopRight)
          PaneItemEntry(
            label: 'Settings'.tl,
            icon: Icons.settings_outlined,
            activeIcon: Icons.settings,
          ),
      ];

  var index = 0;

  /// 设置入口是否放在首页右上角；为 false 时仍是底部的一个标签页。
  /// 在「设置 → 外观」里切换（改动后 App.forceRebuild 会重建本页）。
  bool get _settingsInTopRight =>
      appdata.settings['settingsEntry'] == 'topRight';

  /// 首页右上角的 Miuix 设置按钮。
  Widget _buildSettingsAction() {
    return MiuixTheme(
      // MiuixTheme.of 在没有祖先时会回退到**浅色**，深色模式下按压遮罩会
      // 发白。这里按 App 当前亮度注入，仅作用于这一颗按钮。
      data: MiuixThemeData.of(Theme.of(context).brightness),
      child: MiuixIconButton(
        onPressed: () => to(() => const SettingsPage(), preventDuplicate: true),
        child: Icon(
          Icons.settings_outlined,
          size: 22,
          color: Theme.of(context).colorScheme.onSurface,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    // 运行中切换「设置入口位置」后标签数量会变（5 ↔ 4）。forceRebuild 不
    // 重建 State，这里的 index 可能指向已消失的设置标签 —— 先钳回有效范围。
    final maxIndex = _paneItems.length - 1;
    if (index > maxIndex) index = maxIndex;
    return NaviPane(
      // 入口位置切换会增删标签：key 变化强制重建 NaviPane 的 State，
      // 让 PageController 按新的 initialPage 重新起步，避免旧页码越界。
      key: ValueKey('navPane-${_settingsInTopRight ? 'topRight' : 'navBar'}'),
      initialPage: index,
      observer: _observer,
      navigatorKey: _navigatorKey!,
      paneItems: _paneItems,
      onPageChanged: (i) {
        setState(() {
          index = i;
        });
      },
      paneActions: [
        // 首页右上角：设置入口。
        if (index == 0 && _settingsInTopRight)
          PaneActionEntry(
            label: 'Settings'.tl,
            iconWidget: _buildSettingsAction(),
            onTap: () => to(() => const SettingsPage(), preventDuplicate: true),
          ),
      ],
      pageBuilder: (index) {
        return _pages[index];
      },
    );
  }
}
