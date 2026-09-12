import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_miuix/miuix.dart';
import 'package:flutter_reorderable_grid_view/widgets/reorderable_builder.dart';
import 'package:local_auth/local_auth.dart';
import 'package:url_launcher/url_launcher_string.dart';
import 'package:venera/components/components.dart';
import 'package:venera/foundation/app.dart';
import 'package:venera/foundation/appdata.dart';
import 'package:venera/foundation/cache_manager.dart';
import 'package:venera/foundation/comic_source/comic_source.dart';
import 'package:venera/foundation/content_guard.dart';
import 'package:venera/foundation/favorites.dart';
import 'package:venera/foundation/js_engine.dart';
import 'package:venera/foundation/local.dart';
import 'package:venera/foundation/log.dart';
import 'package:venera/network/app_dio.dart';
import 'package:venera/utils/data.dart';
import 'package:venera/utils/data_sync.dart';
import 'package:venera/utils/io.dart';
import 'package:venera/utils/translations.dart';
import 'package:yaml/yaml.dart';

part 'reader.dart';
part 'explore_settings.dart';
part 'blocking_settings.dart';
part 'setting_components.dart';
part 'appearance.dart';
part 'local_favorites.dart';
part 'app.dart';
part 'about.dart';
part 'network.dart';

/// 用 Miuix 主题包住设置内容。
///
/// Miuix 组件一律通过 [MiuixTheme.of] 取色，而它在**没有祖先时会回退到浅色
/// 默认值** —— 深色模式下会画出浅色卡片，必然穿帮。这里按 App 当前亮度注入
/// 一套 MiuixThemeData，让设置页跟随系统/用户选择。
///
/// 只包设置页，不包 App 根部：其余页面没有 Miuix 组件，包了也是空转。
Widget _withMiuixTheme(BuildContext context, Widget child) {
  return MiuixTheme(
    data: MiuixThemeData.of(Theme.of(context).brightness),
    child: child,
  );
}

/// 设置页是否使用 Miuix 画风（在「外观」里切换）。
bool get _useMiuixStyle => appdata.settings['settingsStyle'] == 'miuix';

class SettingsPage extends StatefulWidget {
  /// [embedded] 为 true 时表示本页是作为底部导航的一个标签页嵌入的，
  /// 其外层（NaviPane）已经提供了标题栏与系统状态栏留白，
  /// 因此不再绘制自己的返回按钮与标题，避免出现双标题、双留白。
  const SettingsPage({this.initialPage = -1, this.embedded = false, super.key});

  final int initialPage;

  final bool embedded;

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  int currentPage = -1;

  ColorScheme get colors => Theme.of(context).colorScheme;

  bool get enableTwoViews => context.width > 720;

  // 「About」已不再作为独立分类：它的内容（图标/版本/检查更新/项目链接）
  // 搬到了分类列表顶部的 [_AboutSection]，一进设置就能看到。
  final categories = <String>[
    "Explore",
    "Blocking & Filtering",
    "Reading",
    "Appearance",
    "Local Favorites",
    "APP",
    "Network",
  ];

  final icons = <IconData>[
    Icons.explore,
    Icons.filter_alt_outlined,
    Icons.book,
    Icons.color_lens,
    Icons.collections_bookmark_rounded,
    Icons.apps,
    Icons.public,
  ];

  /// 分类徽章配色（每类一色，34px 圆角方块，appearance 页同款风格）。
  final badgeColors = <Color>[
    Colors.blue,
    Colors.redAccent,
    Colors.green,
    Colors.purple,
    Colors.orange,
    Colors.cyan,
    Colors.teal,
  ];

  @override
  void initState() {
    currentPage = widget.initialPage;
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return _withMiuixTheme(
      context,
      Material(
        // 背景由根部 AppBackground 绘制（沉浸式背景/壁纸全局生效）。
        color: Colors.transparent,
        child: buildBody(),
      ),
    );
  }

  Widget buildBody() {
    if (enableTwoViews) {
      return Row(
        children: [
          SizedBox(
            width: 280,
            height: double.infinity,
            child: buildLeft(),
          ),
          Container(
            height: double.infinity,
            decoration: BoxDecoration(
              border: Border(
                left: BorderSide(
                  color: context.colorScheme.outlineVariant,
                  width: 0.6,
                ),
              ),
            ),
          ),
          Expanded(
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 200),
              transitionBuilder: (child, animation) {
                return LayoutBuilder(
                  builder: (context, constrains) {
                    return AnimatedBuilder(
                      animation: animation,
                      builder: (context, _) {
                        var width = constrains.maxWidth;
                        var value = animation.isForwardOrCompleted
                            ? 1 - animation.value
                            : 1;
                        var left = width * value;
                        return Stack(
                          children: [
                            Positioned(
                              top: 0,
                              bottom: 0,
                              left: left,
                              width: width,
                              child: child,
                            ),
                          ],
                        );
                      },
                    );
                  },
                );
              },
              child: buildRight(),
            ),
          )
        ],
      );
    } else {
      return buildLeft();
    }
  }

  Widget buildLeft() {
    return Material(
      color: Colors.transparent,
      child: Column(
        children: [
          // 系统状态栏留白。底栏模式下 NaviPane 的顶栏已消费该 padding，
          // 此处从 MediaQuery 读到的值会是 0；侧栏模式下（无顶栏）才是真实高度。
          // 因此两种情况直接沿用 MediaQuery 的值都是安全的。
          SizedBox(
            height: MediaQuery.of(context).padding.top,
          ),
          if (!widget.embedded) ...[
            SizedBox(
              height: 56,
              child: Row(children: [
                const SizedBox(
                  width: 8,
                ),
                Tooltip(
                  message: "Back",
                  child: IconButton(
                    icon: const Icon(Icons.arrow_back),
                    onPressed: context.pop,
                  ),
                ),
                const SizedBox(
                  width: 24,
                ),
                Text(
                  "Settings".tl,
                  style: ts.s20,
                )
              ]),
            ),
            const SizedBox(
              height: 4,
            ),
          ],
          Expanded(
            child: buildCategories(),
          )
        ],
      ),
    );
  }

  /// 打开某个分类：宽屏就地切换右栏，窄屏推进二级页。
  void _openCategory(int id) {
    if (enableTwoViews) {
      setState(() => currentPage = id);
    } else {
      context.to(() => _SettingsDetailPage(pageIndex: id));
    }
  }

  Widget buildCategories() {
    Widget buildItem(String name, int id) {
      final bool selected = id == currentPage;

      Widget content = AnimatedContainer(
        key: ValueKey(id),
        duration: const Duration(milliseconds: 200),
        width: double.infinity,
        height: 46,
        padding: const EdgeInsets.fromLTRB(12, 0, 12, 0),
        decoration: BoxDecoration(
          color: selected ? colors.primaryContainer.toOpacity(0.36) : null,
          border: Border(
            left: BorderSide(
              color: selected ? colors.primary : Colors.transparent,
              width: 2,
            ),
          ),
        ),
        child: Row(children: [
          Icon(icons[id]),
          const SizedBox(width: 16),
          Text(
            name,
            style: ts.s16,
          ),
          const Spacer(),
          if (selected) const Icon(Icons.arrow_right)
        ]),
      );

      return Padding(
        padding: enableTwoViews
            ? const EdgeInsets.fromLTRB(8, 0, 8, 0)
            : EdgeInsets.zero,
        child: InkWell(
          onTap: () => _openCategory(id),
          child: content,
        ).paddingVertical(4),
      );
    }

    // Miuix：关于区 + 一张卡片包住所有分类行（卡片内每行自带按压反馈
    // 与右箭头，靠间距而非分隔线区分）。
    if (_useMiuixStyle) {
      return ListView(
        padding: const EdgeInsets.only(bottom: 16),
        children: [
          const _AboutSection(),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: MiuixCard(
              child: Column(
                children: [
                  for (var i = 0; i < categories.length; i++)
                    MiuixArrowPreference(
                      title: categories[i].tl,
                      // 彩色图标徽章：每类一色的圆角方块（iOS 设置风格）。
                      startAction: Container(
                        width: 34,
                        height: 34,
                        decoration: BoxDecoration(
                          color: badgeColors[i].withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Icon(
                          icons[i],
                          size: 20,
                          color: badgeColors[i],
                        ),
                      ),
                      onClick: () => _openCategory(i),
                    ),
                ],
              ),
            ),
          ),
        ],
      );
    }

    return ListView(
      padding: EdgeInsets.zero,
      children: [
        const _AboutSection(),
        for (var i = 0; i < categories.length; i++)
          buildItem(categories[i].tl, i),
      ],
    );
  }

  Widget buildRight() {
    if (currentPage == -1) {
      return const SizedBox();
    }
    return Navigator(
      onGenerateRoute: (settings) {
        return PageRouteBuilder(
          pageBuilder: (context, animation, secondaryAnimation) {
            return _buildSettingsContent(currentPage);
          },
          transitionDuration: Duration.zero,
        );
      },
    );
  }

  Widget _buildSettingsContent(int pageIndex) {
    return switch (pageIndex) {
      0 => const ExploreSettings(),
      1 => const BlockingSettings(),
      2 => const ReaderSettings(),
      3 => const AppearanceSettings(),
      4 => const LocalFavoritesSettings(),
      5 => const AppSettings(),
      6 => const NetworkSettings(),
      _ => throw UnimplementedError()
    };
  }

}

class _SettingsDetailPage extends StatelessWidget {
  const _SettingsDetailPage({required this.pageIndex});

  final int pageIndex;

  @override
  Widget build(BuildContext context) {
    return _withMiuixTheme(
      context,
      Material(
        color: Colors.transparent,
        child: _buildPage(),
      ),
    );
  }

  Widget _buildPage() {
    return switch (pageIndex) {
      0 => const ExploreSettings(),
      1 => const BlockingSettings(),
      2 => const ReaderSettings(),
      3 => const AppearanceSettings(),
      4 => const LocalFavoritesSettings(),
      5 => const AppSettings(),
      6 => const NetworkSettings(),
      _ => throw UnimplementedError()
    };
  }
}
