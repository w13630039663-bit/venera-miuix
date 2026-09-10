part of 'components.dart';

/// 底部导航栏的呈现风格。
///
/// - [classic]：venera 原有的实心底栏，内容不穿过。
/// - [floating]：MIUIX 悬浮液态玻璃胶囊，内容从其下方穿过并被折射。
/// - [frosted]：MIUIX 固定毛玻璃底栏。
enum NavBarStyle { classic, floating, frosted }

class PaneItemEntry {
  String label;

  IconData icon;

  IconData activeIcon;

  PaneItemEntry({
    required this.label,
    required this.icon,
    required this.activeIcon,
  });
}

class PaneActionEntry {
  String label;

  /// 普通图标按钮用的图标；提供 [iconWidget] 时可省略。
  IconData? icon;

  /// 自定义控件（例如 Miuix 风格的按钮）。非空时优先于 [icon] 渲染，
  /// 由控件自身处理点击与按压反馈。
  Widget? iconWidget;

  VoidCallback onTap;

  PaneActionEntry({
    required this.label,
    this.icon,
    this.iconWidget,
    required this.onTap,
  });
}

class NaviPane extends StatefulWidget {
  const NaviPane({
    required this.paneItems,
    required this.paneActions,
    required this.pageBuilder,
    this.initialPage = 0,
    this.onPageChanged,
    required this.observer,
    required this.navigatorKey,
    super.key,
  });

  final List<PaneItemEntry> paneItems;

  final List<PaneActionEntry> paneActions;

  final Widget Function(int page) pageBuilder;

  final void Function(int index)? onPageChanged;

  final int initialPage;

  final NaviObserver observer;

  final GlobalKey<NavigatorState> navigatorKey;

  @override
  State<NaviPane> createState() => NaviPaneState();

  static NaviPaneState of(BuildContext context) {
    return context.findAncestorStateOfType<NaviPaneState>()!;
  }

  /// 「内容页接管系统返回」登记处。
  ///
  /// 为什么需要它：系统返回（含边缘返回手势）最先到达**根 Navigator**
  /// （WidgetsApp.didPopRoute → root.maybePop），而只有挂在**根路由**上的
  /// PopScope 能拦住它。内容页（如搜索页结果态）的 PopScope 挂在内嵌
  /// Navigator 自己的路由上，拦不住系统返回 —— 那种情况下根路由会被直接
  /// 弹出、整个 App 退出。
  ///
  /// 内容页需要独占返回（返回 = 退回上一状态，而不是退出）时把这里置
  /// `true`：根级 PopScope 会拒绝弹出根路由，改为把返回转交内嵌 Navigator，
  /// 最终落到内容页自己的 PopScope 上由它处理。用完整务必置回 `false`
  /// （内容页 dispose 时也要）。
  static final ValueNotifier<bool> contentBackOverride =
      ValueNotifier<bool>(false);
}

typedef NaviItemTapListener = void Function(int);

class NaviPaneState extends State<NaviPane>
    with SingleTickerProviderStateMixin {
  bool _canPop = true;

  late int _currentPage = widget.initialPage;

  int get currentPage => _currentPage;

  set currentPage(int value) {
    if (value == _currentPage) return;
    _currentPage = value;
    widget.onPageChanged?.call(value);
  }

  void Function()? mainViewUpdateHandler;

  /// 页面横向滑动控制器：主内容用 [PageView] 承载，
  /// 支持左右滑动切换 tab，悬浮胶囊的高亮条实时跟随手势。
  late final PageController pageController = PageController(
    initialPage: widget.initialPage,
  );

  late AnimationController controller;

  final _naviItemTapListeners = <NaviItemTapListener>[];

  void addNaviItemTapListener(NaviItemTapListener listener) {
    _naviItemTapListeners.add(listener);
  }

  void removeNaviItemTapListener(NaviItemTapListener listener) {
    _naviItemTapListeners.remove(listener);
  }

  static const _kBottomBarHeight = 58.0;

  /// 固定毛玻璃底栏的模糊半径（dp）与着色不透明度。
  /// 对齐 pixez-miuix 的 20dp；不透明度取 0.85 而非其 0.96，
  /// 以便肉眼能真正看到毛玻璃质感（0.96 时几乎等同实心底栏）。
  static const _kFrostedBarBlurRadius = 20.0;

  static const _kFrostedBarTintAlpha = 0.85;

  static const _kFoldedSideBarWidth = 72.0;

  static const _kSideBarWidth = 224.0;

  static const _kTopBarHeight = 48.0;

  // ── 悬浮液态玻璃底栏（liquid_glass_easy LiquidGlassTabBar）布局参数 ──

  /// 胶囊（面板）总高度（dp）。56dp 紧凑档 —— 与官方 64dp 同一几何
  /// 机制（padding 6 / grow 12 / 等比放大），整体按比例收小一号。
  static const _kGlassBarHeight = 56.0;

  /// 胶囊左右外边距（dp）。
  static const _kGlassBarHorizontalPadding = 24.0;

  /// 悬浮栏最大宽度，对齐 pixez 的 FLOATING_BAR_MAX_WIDTH_DP = 540。
  static const _kGlassBarMaxWidth = 540.0;

  /// 无系统手势条时的底部留白，对齐 pixez `20.dp`。
  static const _kGlassBarBottomPaddingNoInset = 20.0;

  /// 有系统手势条时叠加在 inset 之上的留白，对齐 pixez `8.dp`。
  static const _kGlassBarBottomPaddingWithInset = 8.0;

  NavBarStyle get navBarStyle {
    switch (appdata.settings['navBarStyle']) {
      case 'classic':
        return NavBarStyle.classic;
      case 'frosted':
        return NavBarStyle.frosted;
      default:
        return NavBarStyle.floating;
    }
  }

  /// 液态玻璃底栏距屏幕底部的留白。
  /// 有系统手势条时在其上叠 8dp，否则固定 20dp —— 对齐 pixez 的
  /// `navBarBottomPadding = 8.dp + inset ?: 20.dp`。
  double get _glassBarBottomPadding {
    final inset = MediaQuery.of(context).viewPadding.bottom;
    return inset != 0
        ? _kGlassBarBottomPaddingWithInset + inset
        : _kGlassBarBottomPaddingNoInset;
  }

  double get bottomBarHeight => switch (navBarStyle) {
        NavBarStyle.floating => _kGlassBarHeight + _glassBarBottomPadding,
        NavBarStyle.frosted => MiuixNavigationBarDefaults.itemHeight +
            MediaQuery.of(context).viewPadding.bottom,
        NavBarStyle.classic =>
          _kBottomBarHeight + MediaQuery.of(context).padding.bottom,
      };

  void onNavigatorStateChange() {
    onRebuild(context);
  }

  void updatePage(int index) {
    for (var listener in _naviItemTapListeners) {
      listener(index);
    }
    if (widget.observer.routes.length > 1) {
      widget.navigatorKey.currentState!.popUntil((route) => route.isFirst);
    }
    if (currentPage == index) {
      return;
    }
    // 按压反馈：玻璃 pill 落位（点按/拖动吸附）时给一次轻震动。
    // 桌面端 HapticFeedback 是无操作调用，无需区分平台。
    HapticFeedback.selectionClick();
    if (App.isMobile && pageController.hasClients) {
      // 滑动容器模式下交给 PageView 动画，currentPage 由
      // onPageChanged 在动画过程中同步更新。
      pageController.animateToPage(
        index,
        duration: const Duration(milliseconds: 320),
        curve: Curves.easeOutCubic,
      );
    } else {
      setState(() {
        currentPage = index;
      });
      mainViewUpdateHandler?.call();
    }
  }

  /// PageView 滑动/动画过程中页码变化时同步 UI（顶栏标题、高亮条等）。
  void _onPageViewChanged(int index) {
    if (index == currentPage) return;
    setState(() {
      currentPage = index;
    });
    mainViewUpdateHandler?.call();
  }

  @override
  void initState() {
    controller = AnimationController(
      duration: const Duration(milliseconds: 250),
      lowerBound: 0,
      upperBound: 3,
      vsync: this,
    );
    widget.observer.addListener(onNavigatorStateChange);
    super.initState();
  }

  @override
  void dispose() {
    controller.dispose();
    pageController.dispose();
    widget.observer.removeListener(onNavigatorStateChange);
    super.dispose();
  }

  double targetFormContext(BuildContext context) {
    var width = MediaQuery.of(context).size.width;
    double target = 0;
    if (width > changePoint) {
      target = 2;
    }
    if (width > changePoint2) {
      target = 3;
    }
    return target;
  }

  double? animationTarget;

  void onRebuild(BuildContext context) {
    double target = targetFormContext(context);
    if (controller.value != target || animationTarget != target) {
      if (controller.isAnimating) {
        if (animationTarget == target) {
          return;
        } else {
          controller.stop();
        }
      }
      controller.animateTo(target);
      animationTarget = target;
    }
  }

  @override
  Widget build(BuildContext context) {
    onRebuild(context);
    final mq = MediaQuery.of(context);
    final sideInsets = (App.isMobile && mq.orientation == Orientation.landscape)
        ? EdgeInsets.only(
            left: math.max(mq.viewPadding.left, mq.systemGestureInsets.left),
            right: math.max(mq.viewPadding.right, mq.systemGestureInsets.right),
          )
        : EdgeInsets.zero;
    return _NaviPopScope(
      action: () {
        if (App.mainNavigatorKey!.currentState!.canPop()) {
          App.mainNavigatorKey!.currentState!.maybePop();
        } else {
          SystemNavigator.pop();
        }
      },
      popGesture: App.isIOS && context.width >= changePoint,
      child: AnimatedBuilder(
        animation: controller,
        builder: (context, child) {
          final value = controller.value;
          Widget content = Stack(
            children: [
              Positioned(
                left: _kFoldedSideBarWidth * ((value - 2.0).clamp(-1.0, 0.0)),
                top: 0,
                bottom: 0,
                child: buildLeft(),
              ),
              Positioned.fill(
                left:
                    _kFoldedSideBarWidth * ((value - 1).clamp(0, 1)) +
                    (_kSideBarWidth - _kFoldedSideBarWidth) *
                        ((value - 2).clamp(0, 1)),
                child: buildMainView(),
              ),
            ],
          );
          if (sideInsets != EdgeInsets.zero) {
            content = Padding(
              padding: sideInsets,
              child: content,
            );
          }
          return content;
        },
      ),
    );
  }

  Widget buildMainView() {
    return HeroControllerScope(
      controller: MaterialApp.createMaterialHeroController(),
      child: ValueListenableBuilder<bool>(
        valueListenable: NaviPane.contentBackOverride,
        builder: (context, overridden, child) {
          return PopScope(
            // 两种情况下都不允许根路由直接弹：内嵌栈还有页面（_canPop ==
            // false），或内容页登记了返回接管（如搜索页结果态）。统一在回调
            // 里把返回转交内嵌 Navigator：它会询问自身栈顶路由（内容页挂在
            // 那上面的 PopScope 就此接手）。
            canPop: _canPop && !overridden,
            onPopInvokedWithResult: (didPop, result) {
              if (didPop) {
                return;
              }
              widget.navigatorKey.currentState?.maybePop(result);
            },
            child: child!,
          );
        },
        child: NotificationListener<NavigationNotification>(
          onNotification: (NavigationNotification notification) {
            final bool nextCanPop = !notification.canHandlePop;
            if (nextCanPop != _canPop) {
              setState(() {
                _canPop = nextCanPop;
              });
            }
            return false;
          },
          child: Navigator(
            observers: [widget.observer],
            key: widget.navigatorKey,
            onGenerateRoute: (settings) => AppPageRoute(
              preventRebuild: false,
              builder: (context) {
                return _NaviMainView(state: this);
              },
            ),
          ),
        ),
      ),
    );
  }

  Widget buildMainViewContent() {
    if (App.isMobile) {
      // 左右滑动切换页面；各页自带 PageStorageKey，滚动位置可恢复。
      return PageView(
        controller: pageController,
        onPageChanged: _onPageViewChanged,
        children: [
          for (var i = 0; i < widget.paneItems.length; i++)
            widget.pageBuilder(i),
        ],
      );
    }
    return widget.pageBuilder(currentPage);
  }

  Widget buildTop() {
    return Material(
      // 背景由根部 AppBackground 绘制；不透明 surface 会在壁纸/氛围光
      // 上形成黑块（off 模式下根部画 surface，观感不变）。
      color: Colors.transparent,
      child: Container(
        padding: const EdgeInsets.only(left: 16, right: 16),
        height: _kTopBarHeight,
        width: double.infinity,
        child: Row(
          children: [
            Text(
              widget.paneItems[currentPage].label,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const Spacer(),
            for (var action in widget.paneActions)
              Tooltip(
                message: action.label,
                child: action.iconWidget ??
                    IconButton(
                      icon: Icon(action.icon),
                      onPressed: action.onTap,
                    ),
              ),
          ],
        ),
      ),
    );
  }

  Widget buildBottom() {
    final style = navBarStyle;
    if (style == NavBarStyle.classic) {
      return _buildClassicBottomBar();
    }
    if (style == NavBarStyle.floating) {
      return _buildLiquidBottomTabs();
    }
    return _buildMiuixBottomBar();
  }

  /// venera 原有的实心底栏。
  Widget _buildClassicBottomBar() {
    return Material(
      textStyle: Theme.of(context).textTheme.labelSmall,
      elevation: 0,
      child: Container(
        height: _kBottomBarHeight,
        decoration: BoxDecoration(
          border: Border(
            top: BorderSide(
              color: Theme.of(context).colorScheme.outlineVariant,
              width: 1,
            ),
          ),
        ),
        child: Row(
          children: List<Widget>.generate(widget.paneItems.length, (index) {
            return Expanded(
              child: _SingleBottomNaviWidget(
                enabled: currentPage == index,
                entry: widget.paneItems[index],
                onTap: () {
                  updatePage(index);
                },
                key: ValueKey(index),
              ),
            );
          }),
        ),
      ),
    ).paddingBottom(MediaQuery.of(context).padding.bottom);
  }

  /// MIUIX 固定毛玻璃底栏（frosted 模式）。
  Widget _buildMiuixBottomBar() {
    final colorScheme = Theme.of(context).colorScheme;
    final items = <Widget>[];
    for (var i = 0; i < widget.paneItems.length; i++) {
      final entry = widget.paneItems[i];
      final selected = currentPage == i;
      items.add(
        MiuixNavigationBarItem(
          key: ValueKey(i),
          selected: selected,
          onPressed: () => updatePage(i),
          icon: Icon(
            selected ? entry.activeIcon : entry.icon,
            size: MiuixNavigationBarDefaults.iconSize,
          ),
          label: entry.label,
        ),
      );
    }
    return _buildFrostedBar(colorScheme, items);
  }

  /// 悬浮液态玻璃底栏 —— 用 liquid_glass_easy 的「单块玻璃」自组装。
  ///
  /// 此前用 `LiquidGlassTabBar.withImpeller`：其 pill 尺寸/手势模型在
  /// 公开 API 里均不可配，遂弃用，直接用库底层的 [LiquidGlassLens]
  /// 自组装：
  ///
  /// - 玻璃由 shader 实时采样身后的页面内容（模糊+折射+色差散射）；
  /// - `touch:` 挂库官方 [LiquidGlassFlex] 软体物理 —— **按压时整条
  ///   胶囊自然膨胀放大、拖动时整体沿手指软体跟随、松手回弹抖动**，
  ///   正是需求的整栏反馈，由库驱动不再自研；
  /// - 选中高亮 = 官方 iOS morph pill：静止收在栏内，按下/拖动时等比放大；
  /// - 高亮条位移由本组件弹簧跟踪：点按飞行、按住横滑跟手、松手吸附；
  /// - 内容层 [GestureDetector] opaque：胶囊区域起手的点击/横滑都被
  ///   本层消费，底下的 PageView 永远收不到 —— 屏蔽胶囊区横滑切页。
  Widget _buildLiquidBottomTabs() {
    final mq = MediaQuery.of(context);
    final barW = math.min(
      _kGlassBarMaxWidth,
      mq.size.width - _kGlassBarHorizontalPadding * 2,
    );
    // 安全区叠加：有手势条 8dp，无手势条 20dp（对齐 pixez）。
    final baseMargin = mq.viewPadding.bottom != 0
        ? _kGlassBarBottomPaddingWithInset
        : _kGlassBarBottomPaddingNoInset;
    return _GlassFloatingBar(
      entries: widget.paneItems,
      selectedIndex: currentPage,
      onSelected: updatePage,
      width: barW,
      height: _kGlassBarHeight,
      bottomInset: baseMargin + mq.padding.bottom,
    );
  }

  Widget _buildFrostedBar(ColorScheme colorScheme, List<Widget> items) {
    final sigma = _kFrostedBarBlurRadius * 0.45;
    return RepaintBoundary(
      child: ClipRect(
        child: BackdropFilter(
          filter: ui.ImageFilter.blur(sigmaX: sigma, sigmaY: sigma),
          child: MiuixNavigationBar(
            colors: MiuixNavigationBarColors(
              background:
                  colorScheme.surface.withValues(alpha: _kFrostedBarTintAlpha),
              floatingBackground: colorScheme.surface,
              content: colorScheme.onSurface,
              divider: colorScheme.outlineVariant,
            ),
            showDivider: true,
            children: items,
          ),
        ),
      ),
    );
  }

  Widget buildLeft() {
    final value = controller.value;
    const paddingHorizontal = 12.0;
    return Material(
      child: Container(
        width:
            _kFoldedSideBarWidth +
            (_kSideBarWidth - _kFoldedSideBarWidth) * ((value - 2).clamp(0, 1)),
        height: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: paddingHorizontal),
        decoration: BoxDecoration(
          border: Border(
            right: BorderSide(
              color: Theme.of(context).colorScheme.outlineVariant,
              width: 1.0,
            ),
          ),
        ),
        child: Column(
          children: [
            const SizedBox(height: 16),
            SizedBox(height: MediaQuery.of(context).padding.top),
            ...List<Widget>.generate(
              widget.paneItems.length,
              (index) => _SideNaviWidget(
                enabled: currentPage == index,
                entry: widget.paneItems[index],
                showTitle: value == 3,
                onTap: () {
                  updatePage(index);
                },
                key: ValueKey(index),
              ),
            ),
            const Spacer(),
            ...List<Widget>.generate(
              widget.paneActions.length,
              (index) => _PaneActionWidget(
                entry: widget.paneActions[index],
                showTitle: value == 3,
                key: ValueKey(index + widget.paneItems.length),
              ),
            ),
            const SizedBox(height: 16),
          ],
        ),
      ),
    );
  }
}

class _SideNaviWidget extends StatelessWidget {
  const _SideNaviWidget({
    required this.enabled,
    required this.entry,
    required this.onTap,
    required this.showTitle,
    super.key,
  });

  final bool enabled;

  final PaneItemEntry entry;

  final VoidCallback onTap;

  final bool showTitle;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final icon = Icon(enabled ? entry.activeIcon : entry.icon);
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 12),
        height: 38,
        decoration: BoxDecoration(
          color: enabled ? colorScheme.primaryContainer : null,
          borderRadius: BorderRadius.circular(12),
        ),
        child: showTitle
            ? Row(
                children: [icon, const SizedBox(width: 12), Text(entry.label)],
              )
            : Align(alignment: Alignment.centerLeft, child: icon),
      ),
    ).paddingVertical(4);
  }
}

class _PaneActionWidget extends StatelessWidget {
  const _PaneActionWidget({
    required this.entry,
    required this.showTitle,
    super.key,
  });

  final PaneActionEntry entry;

  final bool showTitle;

  @override
  Widget build(BuildContext context) {
    final icon = Icon(entry.icon);
    return InkWell(
      onTap: entry.onTap,
      borderRadius: BorderRadius.circular(12),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 12),
        height: 38,
        child: showTitle
            ? Row(
                children: [icon, const SizedBox(width: 12), Text(entry.label)],
              )
            : Align(alignment: Alignment.centerLeft, child: icon),
      ),
    ).paddingVertical(4);
  }
}

class _SingleBottomNaviWidget extends StatefulWidget {
  const _SingleBottomNaviWidget({
    required this.enabled,
    required this.entry,
    required this.onTap,
    super.key,
  });

  final bool enabled;

  final PaneItemEntry entry;

  final VoidCallback onTap;

  @override
  State<_SingleBottomNaviWidget> createState() =>
      _SingleBottomNaviWidgetState();
}

class _SingleBottomNaviWidgetState extends State<_SingleBottomNaviWidget>
    with SingleTickerProviderStateMixin {
  late AnimationController controller;

  bool isHovering = false;

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant _SingleBottomNaviWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.enabled != widget.enabled) {
      if (widget.enabled) {
        controller.forward(from: 0);
      } else {
        controller.reverse(from: 1);
      }
    }
  }

  @override
  void initState() {
    super.initState();
    controller = AnimationController(
      value: widget.enabled ? 1 : 0,
      vsync: this,
      duration: _fastAnimationDuration,
    );
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: CurvedAnimation(parent: controller, curve: Curves.ease),
      builder: (context, child) {
        return MouseRegion(
          cursor: SystemMouseCursors.click,
          onEnter: (details) => setState(() => isHovering = true),
          onExit: (details) => setState(() => isHovering = false),
          child: GestureDetector(
            behavior: HitTestBehavior.translucent,
            onTap: widget.onTap,
            child: buildContent(),
          ),
        );
      },
    );
  }

  Widget buildContent() {
    final value = controller.value;
    final colorScheme = Theme.of(context).colorScheme;
    final icon = Icon(
      widget.enabled ? widget.entry.activeIcon : widget.entry.icon,
    );
    return Center(
      child: Container(
        width: 64,
        height: 28,
        decoration: BoxDecoration(
          borderRadius: const BorderRadius.all(Radius.circular(32)),
          color: isHovering ? colorScheme.surfaceContainer : Colors.transparent,
        ),
        child: Center(
          child: Container(
            width: 32 + value * 32,
            height: 28,
            decoration: BoxDecoration(
              borderRadius: const BorderRadius.all(Radius.circular(32)),
              color: value != 0
                  ? colorScheme.secondaryContainer
                  : Colors.transparent,
            ),
            child: Center(child: icon),
          ),
        ),
      ),
    );
  }
}

class NaviObserver extends NavigatorObserver implements Listenable {
  var routes = Queue<Route>();

  int get pageCount {
    int count = 0;
    for (var route in routes) {
      if (route is AppPageRoute) {
        count++;
      }
    }
    return count;
  }

  @override
  void didPop(Route route, Route? previousRoute) {
    routes.removeLast();
    notifyListeners();
  }

  @override
  void didPush(Route route, Route? previousRoute) {
    routes.addLast(route);
    notifyListeners();
  }

  @override
  void didRemove(Route route, Route? previousRoute) {
    routes.remove(route);
    notifyListeners();
  }

  @override
  void didReplace({Route? newRoute, Route? oldRoute}) {
    routes.remove(oldRoute);
    if (newRoute != null) {
      routes.add(newRoute);
    }
    notifyListeners();
  }

  List<VoidCallback> listeners = [];

  @override
  void addListener(VoidCallback listener) {
    listeners.add(listener);
  }

  @override
  void removeListener(VoidCallback listener) {
    listeners.remove(listener);
  }

  void notifyListeners() {
    for (var listener in listeners) {
      listener();
    }
  }
}

class _NaviPopScope extends StatelessWidget {
  const _NaviPopScope({
    required this.child,
    this.popGesture = false,
    required this.action,
  });

  final Widget child;
  final bool popGesture;
  final VoidCallback action;

  static bool panStartAtEdge = false;

  @override
  Widget build(BuildContext context) {
    Widget res = child;
    if (popGesture) {
      res = GestureDetector(
        onPanStart: (details) {
          if (details.globalPosition.dx < 64) {
            panStartAtEdge = true;
          }
        },
        onPanEnd: (details) {
          if (details.velocity.pixelsPerSecond.dx < 0 ||
              details.velocity.pixelsPerSecond.dx > 0) {
            if (panStartAtEdge) {
              action();
            }
          }
          panStartAtEdge = false;
        },
        child: res,
      );
    }
    return res;
  }
}

class _NaviMainView extends StatefulWidget {
  const _NaviMainView({required this.state});

  final NaviPaneState state;

  @override
  State<_NaviMainView> createState() => _NaviMainViewState();
}

class _NaviMainViewState extends State<_NaviMainView> {
  NaviPaneState get state => widget.state;

  @override
  void initState() {
    state.mainViewUpdateHandler = () {
      setState(() {});
    };
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    var shouldShowAppBar = state.controller.value < 2;

    // floating 模式：底栏是悬浮胶囊，内容必须延伸到屏幕底部、从玻璃下方
    // 穿过（否则玻璃背后没有内容可折射，且视觉上底栏"挡住"内容）。
    // classic / frosted 模式：底栏通栏固定，内容仍需预留高度。
    final isFloating = shouldShowAppBar && state.navBarStyle == NavBarStyle.floating;

    final mq = MediaQuery.of(context);
    // floating 模式把底栏高度并入内容区的 bottom padding，
    // 让页面滚动列表的末尾项可以完整滚出底栏区域。
    // top 置 0：顶栏（buildTop().paddingTop(padding.top)）已消费状态栏
    // 高度，若保留，各 tab 页里自己加的 paddingTop(padding.top) 会变成
    // 双倍 —— 这就是「内容离顶栏特别远」的根因。二级页在 Navigator
    // 层（本子树之外），仍拿到真实 padding，不受影响。
    final contentPadding = (isFloating
            ? mq.padding.copyWith(bottom: mq.padding.bottom + state.bottomBarHeight)
            : mq.padding)
        .copyWith(top: 0);

    // 内容层：撑满全屏；floating 模式不预留底栏占位。
    final content = Column(
      children: [
        if (shouldShowAppBar) state.buildTop().paddingTop(context.padding.top),
        Expanded(
          child: MediaQuery(
            data: mq.copyWith(padding: contentPadding),
            child: AnimatedSwitcher(
              duration: _fastAnimationDuration,
              child: state.buildMainViewContent(),
            ),
          ),
        ),
        if (shouldShowAppBar && !isFloating)
          SizedBox(height: state.bottomBarHeight),
      ],
    );

    if (!shouldShowAppBar) {
      return content;
    }

    // 底栏悬浮叠加在内容之上 —— 这是玻璃能取到身后内容的前提。
    // 原先底栏在 Column 里独立占位，内容根本不会从它下面经过。
    // 液态玻璃/毛玻璃都直接通过 BackdropFilter 捕获 Stack 下层的页面
    // 内容（引擎自动处理），无需额外的 BackdropLayer 包装。
    //
    // floating 模式（LiquidGlassTabBar.withImpeller）是一个自包含的
    // 全屏覆盖层（内部 body 为 SizedBox.expand），必须给满屏约束；
    // 胶囊在覆盖层内的位置由库根据 margin/alignment 自行摆放。
    return Stack(
      children: [
        Positioned.fill(child: content),
        if (isFloating)
          Positioned.fill(child: state.buildBottom())
        else
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: state.buildBottom(),
          ),
      ],
    );
  }
}

/// 自组装悬浮液态玻璃底栏（替代 `LiquidGlassTabBar.withImpeller`）。
///
/// ## 为什么不用 `LiquidGlassTabBar.withImpeller`
/// 库的 TabBar 把 pill 位移/形变动画全部封装在内部，手势模型（按住
/// 100ms 才判定拖动）、pill 尺寸（`pillExtraHeight: 36` 硬编码）都无法
/// 从公开 API 调整，无法满足"拖动 1:1 跟手 + 图标渐变 + 自定泡泡尺寸"
/// 的需求 —— 改用底层 [LiquidGlassLens] 自组装，动画与几何全部自己控。
///
/// ## 本方案：胶囊透镜 + iOS morph pill 高亮
/// - 胶囊 = 一块 [LiquidGlassLens]（shader 实时采样身后页面：模糊 +
///   折射 + 色差散射）；
/// - 整栏触控反馈 = 库官方 [LiquidGlassFlex] 软体物理（挂在
///   [LiquidGlassLens.touch]）：按压时整条胶囊均匀膨胀放大、拖动时
///   整体沿手指软体跟随、松手欠阻尼回弹 —— 不再自研弹簧；
/// - 选中高亮 = 库官方 morph pill（几何/弹簧照抄
///   `LiquidGlassAnimatedNavBar`）：
///   静止 = (cellWidth, cellHeight) 收在栏内；按下/拖动/飞行时**等比**
///   放大到 (栏高 + pillGrowHeight 12) 的等比尺寸，宽高比不变（只长高
///   不变宽会变成竖长条）；X / Y 两根独立弹簧（ζ 0.6 / 0.7）过冲后落回，
///   材质另有一根 4 倍刚度的快弹簧；落位时允许压过静止尺寸再回弹一次；
///   移动中叠加加速度形变（scaleX = 1+d、scaleY = 1−d，上限 ±12%）；
///   形状为 Apple 胶囊（cornerRadius = 高度/2）；位移由本组件弹簧跟踪
///   （点按飞行、按住拖动跟手、松手吸附）；
/// - 内容层 [GestureDetector] opaque：胶囊区域起手的点击 / 横滑全部
///   被消费，底下 PageView 收不到 → 屏蔽该区域的横滑切页。
class _GlassFloatingBar extends StatefulWidget {
  const _GlassFloatingBar({
    required this.entries,
    required this.selectedIndex,
    required this.onSelected,
    required this.width,
    required this.height,
    required this.bottomInset,
  });

  final List<PaneItemEntry> entries;

  final int selectedIndex;

  final ValueChanged<int> onSelected;

  /// 胶囊宽度（不含水平外边距，已由调用方算好）。
  final double width;

  /// 胶囊高度。
  final double height;

  /// 胶囊距屏幕底边的间距（含安全区 inset）。
  final double bottomInset;

  @override
  State<_GlassFloatingBar> createState() => _GlassFloatingBarState();
}

class _GlassFloatingBarState extends State<_GlassFloatingBar>
    with SingleTickerProviderStateMixin {
  // ── 玻璃外观（缓存：build 每帧重建，避免重复构造）──
  // 明暗双配方，依据库 ADAPTIVITY.md：「玻璃要与其背景『气味相投』——
  // 深色背景上应是烟熏黑（smoked）而非乳白（milky）」，官方深色示例
  // glassColor = Color(0x33000000)。浅色保持原半透明白配方不变。
  LiquidGlassStyle? _cachedBarStyle;
  Brightness? _cachedBarStyleBrightness;

  LiquidGlassStyle _glassStyleOf(BuildContext context) {
    final b = Theme.of(context).brightness;
    if (_cachedBarStyle != null && _cachedBarStyleBrightness == b) {
      return _cachedBarStyle!;
    }
    final dark = b == Brightness.dark;
    final style = LiquidGlassStyle(
      // 形状照抄库官方导航栏配方
      // （styled/liquid_glass_styled_nav_bar.dart:613）：全圆角胶囊
      // （cornerRadius = 栏高一半）+ 光学描边（OpticalBorder）——
      // borderSolidity 让边缘有一道实心轮廓，栏体与页面内容之间有明确
      // 分界，不会「糊成一片」。
      shape: LiquidGlassShape.roundedRectangle(
        cornerRadius: 28,
        borderWidth: 1.2,
        lightIntensity: 1.1,
        lightDirection: 80,
        borderType: const OpticalBorder(
          borderSaturation: 1.2,
          ambientIntensity: 1.0,
          borderSolidity: 0.35,
        ),
      ),
      appearance: LiquidGlassAppearance(
        // 浅色 tint 用库官方导航栏取值 Color(0x16FFFFFF)
        // （styled_nav_bar.dart:638、tab_bar.dart:178）。
        // 注意：库注释写的「white, alpha 22」指的是 **alpha 字节 = 22**
        // （≈8.6% 白），不是 22% —— 这里此前误写成
        // Colors.white.withValues(alpha: 0.22)（= alpha 字节 56，约 22%），
        // 比官方白了 2.5 倍，所以整条栏发白、看不见下层内容。
        color: dark
            ? const Color(0x33000000) // 深色：烟熏黑（官方深色示例）
            : const Color(0x16FFFFFF),
        blur: const LiquidGlassBlur(sigmaX: 2, sigmaY: 2),
        // 库官方「接触阴影」（nav_bar_style.dart:331 的 _defaultShadow）：
        // 一圈柔和、略向外扩散的暗环，让玻璃读起来是**浮在内容之上**，
        // 而不是和页面糊在一起。跟随 flex 形变一起缩放/回弹。
        shadow: const LiquidGlassShadow(blur: 9, opacity: 0.3, inset: 0),
      ),
      refraction: const LiquidGlassRefraction(
        distortion: 0.07,
        distortionWidth: 28,
        chromaticAberration: 0.002,
      ),
    );
    _cachedBarStyleBrightness = b;
    return _cachedBarStyle = style;
  }

  // ── 高亮胶囊材质：静止端 ↔ 抬起端，按材质抬起进度连续插值 ──
  // 官方 _resolveStyle 用**同一个 lens** 在两端之间插值，不是两个 widget
  // 交叉淡入淡出。两端取值（官方 LiquidGlassNavBarMotionPill）：
  //
  //   静止端 restStyle 默认：0x26FFFFFF（≈15% 白）、borderWidth 0（无 rim）、
  //     折射 0 —— 也就是静止时是一颗**扁平的半透明胶囊**，没有凸镜感，
  //     正好等于 hand-over 之后那颗静态 pill，交接时不会跳色。
  //   抬起端 _defaultStyle：0x1CFFFFFF（≈11% 白）、blur 1.5、
  //     cornerRadius = 高度一半（Apple 胶囊）、borderWidth 0.6、
  //     lightIntensity 1.3、折射 0.12 / 带宽 18 / 色散 0.002。
  //
  // 于是"拖动时有色散和发光、静止时没有"是材质随抬起进度长出来的结果，
  // 而不是一直挂着的强参数 —— 之前把色散/发光写死在静止态，所以不动也
  // 显得又亮又花。
  LiquidGlassStyle _buildPillStyle() {
    final t = _liftM.clamp(0.0, 1.0);
    final w = _pillW;
    final h = _pillH;
    final b = Theme.of(context).brightness;
    if (_cachedPillStyle != null &&
        _cachedPillStyleW == w &&
        _cachedPillStyleH == h &&
        _cachedPillStyleT == t &&
        _cachedPillStyleB == b) {
      return _cachedPillStyle!;
    }
    _cachedPillStyleW = w;
    _cachedPillStyleH = h;
    _cachedPillStyleT = t;
    _cachedPillStyleB = b;
    // 静止端 tint：浅色 = 官方 0x26FFFFFF（15% 白）；深色 = 0x1FFFFFFF
    // （12% 白）—— 烟熏黑栏体上需要更淡的乳白才不会显得发白发闷。
    // 抬起端 0x1CFFFFFF 两端通用（质感主要来自 blur/rim/折射）。
    final restColor =
        b == Brightness.dark ? const Color(0x1FFFFFFF) : const Color(0x26FFFFFF);
    return _cachedPillStyle = LiquidGlassStyle(
      shape: LiquidGlassShape.continuousRoundedRectangle(
        // Apple 胶囊：圆角 = 高度一半，随抬起自动保持胶囊轮廓。
        cornerRadius: h / 2,
        borderWidth: 0.6 * t,
        lightIntensity: 1.3,
        lightDirection: 80,
      ),
      appearance: LiquidGlassAppearance(
        color: Color.lerp(
          restColor,
          const Color(0x1CFFFFFF),
          t,
        )!,
        blur: LiquidGlassBlur(sigmaX: 1.5 * t, sigmaY: 1.5 * t),
      ),
      refraction: LiquidGlassRefraction(
        distortion: 0.12 * t,
        distortionWidth: 18 * t,
        chromaticAberration: 0.002 * t,
      ),
    );
  }

  // ── 整栏软体物理（库驱动）：──
  // grip 0 → 按压膨胀均匀居中（最"自然"，不会向某一角拱）；holdScale 5%
  // → 按压放大可感知；tapScale 2% → 点按一瞬微弹；lockAxis horizontal →
  // 拖动时高度锁死，绝不变厚/上抬；releaseDamping 低 → 松手软回弹。
  late final LiquidGlassFlex _flex = const LiquidGlassFlex(
    stretch: 14,
    squeeze: 0.7,
    lean: 0.5,
    grip: 0,
    compressInward: true,
    holdScale: 0.05,
    tapScale: 0.02,
    maxPull: 48,
    lockAxis: Axis.horizontal,
    advanced: LiquidGlassFlexAdvanced(
      childFollow: 0.7,
      refractionBoost: 0.25,
      stiffness: 330,
      damping: 26,
      releaseDamping: 16,
    ),
  );

  // ── 高亮条弹簧状态 ──
  late final Ticker _ticker = createTicker(_onTick);
  Duration _lastTick = Duration.zero;

  /// 高亮条当前中心 x（逻辑 px）。
  double _pillX = 0;

  /// 高亮条当前速度。
  double _pillV = 0;

  /// 弹簧目标中心 x。
  double _pillTargetX = 0;

  /// 每个 cell 的宽度（首次 build 从约束解析）。
  double _cellW = 0;

  // 高亮胶囊样式缓存（尺寸/抬起进度/明暗变化时才重建，避免每帧新建 Style）。
  LiquidGlassStyle? _cachedPillStyle;
  double? _cachedPillStyleW;
  double? _cachedPillStyleH;
  double? _cachedPillStyleT;
  Brightness? _cachedPillStyleB;

  // ── 拖动状态 ──
  bool _dragging = false;

  /// 手指正在按住（未拖走）：胶囊保持抬起。
  bool _tapped = false;

  /// 胶囊是否处于抬起态（官方 _lifted）。点按/长按抓取瞬间置 true，一直
  /// 保持到**落位**为止 —— 松手不等于结束，落位才是结束。
  bool _lifted = false;

  /// 抬起进度：X / Y 两根**独立**弹簧（官方 _liftX / _liftY）。两轴阻尼比
  /// 不同（ζ 0.6 / 0.7），过冲量也不同，所以生长不是一次单调的等比缩放，
  /// 而是宽比高多冲一点、晚落一点 —— 官方原话：这点分歧正是让生长不像
  /// 普通 scale-up 的原因。
  double _liftX = 0;
  double _liftXV = 0;
  double _liftY = 0;
  double _liftYV = 0;

  /// 材质抬起进度（官方 _lift）：比尺寸弹簧刚得多（4 倍刚度），所以玻璃
  /// 质感瞬间到位，而尺寸还在慢慢抖动。
  double _liftM = 0;
  double _liftMV = 0;

  /// 加速度形变（官方 deviation）：scaleX = 1+d、scaleY = 1−d，上限
  /// ±_kMaxDeformation。给拖动/飞行加果冻般的挤压拉伸。
  double _dev = 0;

  /// 落下时是否已经被压到静止尺寸以下（官方 restBounce）。压过一次再回弹
  /// 到静止尺寸就收尾 —— 落位因此带一下轻微的"蹲"，不会生硬刹住。
  bool _bounceX = false;
  bool _bounceY = false;

  /// 本次行程的起点 x，用于算行程进度（官方 _travelFrom）。
  double _travelFrom = 0;

  /// 拖动起点（手指 local x 与 pillX 的差值基线）。
  double _dragBase = 0;

  /// 拖动起点的手指 x。
  double _dragStartX = 0;

  /// 手指是否还在原点附近（判定拖没拖走，辅助图标高亮预览）。
  double _dragAccum = 0;

  // 拖动/吸附用弹簧常数（k, damping）：拖动中高刚度近 1:1 跟手；
  // 松手/点按低刚度带轻微过冲的 settle。
  static const double _kDragStiffness = 1500;
  static const double _kDragDamping = 60;
  static const double _kSettleStiffness = 380;
  static const double _kSettleDamping = 26;

  // ── 高亮胶囊几何 + 抬起弹簧：照搬库官方 LiquidGlassAnimatedNavBar ──
  // 源码依据（liquid_glass_animated_nav_bar.dart build / _onTick）：
  //   pillRest   = Size(layout.pillWidth, layout.cellHeight)
  //   liftedH    = layout.height + pillGrowHeight        // 默认 12
  //   pillLifted = Size(liftedH * (pillWidth / cellHeight), liftedH)
  //
  // 关键点（上一版错在这里）：抬起是**等比放大**——宽度按同一个比例一起
  // 变大，宽高比保持不变。上一版只给高度加 36、宽度纹丝不动，于是胶囊
  // 一按压就从横胶囊被拉成竖长条，正是"瘦高竖长"的来源。
  //
  // 另：36（layout.pillExtraHeight）是**静态** pill 的常量，动画 morph
  // pill 用的是 pillGrowHeight = 12，不要混用。
  static const double _kBarInnerPadding = 6.0;

  /// 抬起后比栏高多出的高度（官方 pillGrowHeight）。
  static const double _kPillGrowHeight = 12.0;

  /// 抬起弹簧：官方 _kLiftStiffness / _kLiftDampingX(ζ0.6) / Y(ζ0.7)。
  static const double _kLiftStiffness = 250.0;
  static const double _kLiftDampingX = 19.0;
  static const double _kLiftDampingY = 22.1;

  /// 材质弹簧：官方 _kMaterialStiffness / _kMaterialDamping（4 倍刚度）。
  static const double _kMaterialStiffness = 1000.0;
  static const double _kMaterialDamping = 63.3;

  /// 形变量上限（官方 motion.maxDeformation）。
  static const double _kMaxDeformation = 0.12;

  /// 形变的响应时间常数（官方 motion.responseTime）。
  static const double _kDeviationTau = 0.18;

  /// 静止尺寸（官方 pillRest）：一格宽 × cellHeight，收在栏内。
  double get _restW => _cellW;

  double get _restH => widget.height - _kBarInnerPadding * 2;

  /// 抬起后的高度（官方 liftedH = 栏高 + growHeight）。
  double get _liftedH => widget.height + _kPillGrowHeight;

  /// 抬起后的宽度：与高度**同比例**放大（官方 pillLifted.width）。
  double get _liftedW =>
      _restH <= 0 ? _restW : _liftedH * (_restW / _restH);

  /// 当前（未叠加形变）的尺寸 = rest + (lifted - rest) × 各轴抬起进度。
  double get _pillW => _restW + (_liftedW - _restW) * _liftX;

  double get _pillH => _restH + (_liftedH - _restH) * _liftY;

  /// 实际绘制尺寸：叠加加速度形变（scaleX = 1+d，scaleY = 1−d）。
  double get _drawW => math.max(1.0, _pillW * (1 + _dev));

  double get _drawH => math.max(1.0, _pillH * (1 - _dev));

  @override
  void initState() {
    super.initState();
    // 尺寸未定前先存目标；首次 build 解析 cellW 后统一落位。
    _pillTargetX = _pillX = widget.selectedIndex * 1.0;
  }

  @override
  void didUpdateWidget(_GlassFloatingBar old) {
    super.didUpdateWidget(old);
    // 外部驱动（PageView 滑动 / 点按其他入口）切换了选中页，且当前没有
    // 手指在拖 —— 让高亮条弹簧飞到新位置。
    if (!_dragging && widget.selectedIndex != old.selectedIndex) {
      _targetTo(widget.selectedIndex);
    }
  }

  @override
  void dispose() {
    _ticker.dispose();
    super.dispose();
  }

  void _start() {
    if (_ticker.isActive) return;
    _lastTick = Duration.zero;
    _ticker.start();
  }

  void _targetTo(int index) {
    if (_cellW <= 0) return;
    final next = (index + 0.5) * _cellW;
    if ((next - _pillTargetX).abs() > 1e-6) {
      // 新的一趟行程：记下起点，抬起，并清掉上一趟的回弹标记。
      _travelFrom = _pillX;
      _bounceX = false;
      _bounceY = false;
    }
    _pillTargetX = next;
    _lifted = true;
    _start();
  }

  /// 一根抬起弹簧走一帧（官方 _stepLift）。返回 (值, 速度, 是否已压过)。
  ///
  /// [compressed] 表示此前已压到静止尺寸以下：等它回弹穿过目标值时再 snap
  /// 收尾，落下因此带一下轻微回弹（官方 restBounce，默认开）。
  (double, double, bool) _stepLift(
    double x,
    double v,
    double target,
    double dt,
    double k,
    double d, {
    required bool compressed,
  }) {
    final nv = v + (k * (target - x) - d * v) * dt;
    final nx = x + nv * dt;
    if (target == 0.0) {
      if (nx < 0) return (nx, nv, true);
      if (compressed && x < 0 && nx >= 0) return (0.0, 0.0, false);
      return (nx, nv, compressed);
    }
    if ((nx - target).abs() < 0.0008 && nv.abs() < 0.01) {
      return (target, 0.0, false);
    }
    return (nx, nv, false);
  }

  /// 本次行程已走完的比例（官方 _travelProgress）。
  double _travelProgress() {
    final span = (_pillTargetX - _travelFrom).abs();
    if (span < 1e-6) return 1;
    return (1 - (_pillTargetX - _pillX).abs() / span).clamp(0.0, 1.0);
  }

  void _onTick(Duration elapsed) {
    final dt = (_lastTick == Duration.zero
            ? 1 / 60
            : (elapsed - _lastTick).inMicroseconds / 1e6)
        .clamp(0.0, 1 / 30)
        .toDouble();
    _lastTick = elapsed;

    final dragging = _dragging;
    final k = dragging ? _kDragStiffness : _kSettleStiffness;
    final d = dragging ? _kDragDamping : _kSettleDamping;
    final a = k * (_pillTargetX - _pillX) - d * _pillV;
    _pillV += a * dt;
    _pillX += _pillV * dt;

    // ── 抬起（官方 _lifted）───────────────────────────────────────
    // 官方：!dragging && (弹簧停了 || 行程走到 handoverStart=0.92) → 落下。
    // 也就是说**松手不等于结束，抵达才是结束**：松手后胶囊仍然保持抬起，
    // 一路飞到新格子，落位那一刻才开始缩回 —— 而不是边飞边缩。
    final settled = (_pillTargetX - _pillX).abs() < 0.05 && _pillV.abs() < 0.5;
    if (!dragging && !_tapped && (settled || _travelProgress() >= 0.92)) {
      _lifted = false;
    }
    final liftTarget = _lifted ? 1.0 : 0.0;

    final xr = _stepLift(_liftX, _liftXV, liftTarget, dt, _kLiftStiffness,
        _kLiftDampingX, compressed: _bounceX);
    _liftX = xr.$1;
    _liftXV = xr.$2;
    _bounceX = xr.$3;
    final yr = _stepLift(_liftY, _liftYV, liftTarget, dt, _kLiftStiffness,
        _kLiftDampingY, compressed: _bounceY);
    _liftY = yr.$1;
    _liftYV = yr.$2;
    _bounceY = yr.$3;
    final mr = _stepLift(_liftM, _liftMV, liftTarget, dt, _kMaterialStiffness,
        _kMaterialDamping, compressed: false);
    _liftM = mr.$1;
    _liftMV = mr.$2;

    // ── 加速度形变（官方 deviation）────────────────────────────────
    // 手指拖动：跟手 —— 往右推就拉宽变矮，往左推就挤窄变高。
    // 点按飞行：按行进方向 —— 往右飞全程窄高，往左飞全程宽矮（官方
    // _travelSign：按力取符号会让"起步"和"刹车"自相矛盾，按方向才一致）。
    final speedNorm =
        _cellW > 0 ? (_pillV / _cellW).clamp(-1.0, 1.0).toDouble() : 0.0;
    final gap = _pillTargetX - _pillX;
    final dir = gap.abs() < 0.5 ? 0.0 : gap.sign;
    final devTarget = dragging
        ? speedNorm * _kMaxDeformation
        : -dir * speedNorm.abs() * _kMaxDeformation;
    _dev += (devTarget - _dev) * (1 - math.exp(-dt / _kDeviationTau));

    final liftSettled =
        !_lifted && _liftX == 0 && _liftY == 0 && _liftM == 0;
    final devSettled = _dev.abs() < 0.0005;
    if (settled && liftSettled && devSettled && !dragging && !_tapped) {
      _pillX = _pillTargetX;
      _pillV = 0;
      _dev = 0;
      _ticker.stop();
    }
    setState(() {});
  }

  /// 计算某个 cell 当前的激活度（0~1），驱动图标/文字颜色的渐变。
  ///
  /// 对照 pixez 拖动中的截图：高亮胶囊压过"搜索"图标一半时，图标恰好
  /// 一半变蓝 —— 颜色是跟着胶囊的**物理覆盖范围**走的，而不是按格子
  /// 中心距离衰减。因此这里直接算"胶囊矩形与 cell 矩形的重叠面积占
  /// 胶囊自身面积的比例"，再过一次 smoothstep 让边缘稍柔：
  /// 胶囊完全盖住 cell → 1；完全离开 → 0；压到一半 → 0.5。
  double _activationAt(int index) {
    if (_cellW <= 0) return index == widget.selectedIndex ? 1 : 0;
    final cellLeft = index * _cellW;
    final cellRight = cellLeft + _cellW;
    // 用**实际绘制**宽度（含形变），抬起时胶囊变宽，覆盖判定才跟得上。
    final w = _drawW;
    final pillLeft = _pillX - w / 2;
    final pillRight = _pillX + w / 2;
    final overlap =
        math.min(cellRight, pillRight) - math.max(cellLeft, pillLeft);
    if (overlap <= 0) return 0;
    final t = (overlap / w).clamp(0.0, 1.0);
    return t * t * (3 - 2 * t);
  }

  void _select(int index) {
    final clamped = index.clamp(0, widget.entries.length - 1);
    if (clamped != widget.selectedIndex) {
      widget.onSelected(clamped);
    }
    _targetTo(clamped);
  }

  // ── 手势 ──
  void _onTapUp(TapUpDetails d) {
    final index = (d.localPosition.dx / _cellW).floor();
    _select(index);
  }

  void _onDragStart(DragStartDetails d) {
    _dragging = true;
    _tapped = false;
    // 抓住的瞬间就抬起（官方：a press-and-hold is the same lift held open
    // by a finger）。
    _lifted = true;
    _bounceX = false;
    _bounceY = false;
    _dragStartX = d.localPosition.dx;
    _dragBase = _pillX;
    _dragAccum = 0;
    // 拖起来先让图标高亮跟随当前高亮位置。
    _start();
  }

  void _onDragUpdate(DragUpdateDetails d) {
    if (_cellW <= 0) return;
    _dragAccum = d.localPosition.dx - _dragStartX;
    final target = (_dragBase + _dragAccum).clamp(
      _cellW * 0.5,
      _cellW * (widget.entries.length - 0.5),
    );
    _pillTargetX = target;
    _start();
  }

  void _onDragEnd(DragEndDetails d) {
    _dragging = false;
    // 用 floor 而不是 round：主页（第 0 格）的中心 x = 0.5*cellW 恰好是
    // 拖动下限，round(0.5)=1 会把"拖到主页"判成收藏页导致弹回；floor
    // 与点按(_onTapUp)的取整逻辑一致，胶囊压在哪格就选哪格。
    final index = (_pillTargetX / _cellW).floor().clamp(
      0,
      widget.entries.length - 1,
    );
    _select(index);
  }

  void _onDragCancel() {
    _dragging = false;
    _targetTo(widget.selectedIndex);
  }

  @override
  Widget build(BuildContext context) {
    final mq = MediaQuery.of(context);
    final barLeft = (mq.size.width - widget.width) / 2;
    return Stack(
      clipBehavior: Clip.none,
      children: [
        Positioned(
          left: barLeft,
          bottom: widget.bottomInset,
          width: widget.width,
          height: widget.height,
          // 胶囊玻璃 + 图标内容。胶囊 touch flex 驱动整栏软体放大。
          child: LiquidGlassLens(
            style: _glassStyleOf(context),
            touch: LiquidGlassTouch(flex: _flex),
            child: LayoutBuilder(
              builder: (context, constraints) {
                final w = constraints.maxWidth;
                final n = widget.entries.length;
                if (_cellW <= 0 && w > 0) {
                  _cellW = w / n;
                  _pillX = _pillTargetX = (widget.selectedIndex + 0.5) * _cellW;
                }
                return _buildContent(context);
              },
            ),
          ),
        ),
        // 选中高亮 = 官方 iOS morph pill：静止时收在栏内（cellHeight），
        // 按下/拖动/飞行时**等比放大**到 栏高 + 12（宽按同比例一起变大），
        // 落位后带一下轻微回弹再收回去。尺寸取 _drawW/_drawH（已叠加加速
        // 度形变），定位用 bottom 锚定 + barLeft 偏移，凸出量由
        // (height - _drawH)/2 自然给出。
        // 无 touch 无 child，命中测试自然穿透，点按/拖动仍归下方内容层。
        //
        // 坐标系注意：本 Stack 因外层 Positioned.fill 而占满整屏，
        // _pillX 是胶囊内部坐标 —— 必须加上 barLeft 偏移、并用 bottom
        // 锚定到胶囊的纵向位置，否则 pill 会被画到屏幕左上角（第十六轮
        // "小胶囊消失"的根因）。
        Positioned(
          left: barLeft + _pillX - _drawW / 2,
          bottom: widget.bottomInset + (widget.height - _drawH) / 2,
          width: _drawW,
          height: _drawH,
          child: LiquidGlassLens(style: _buildPillStyle()),
        ),
      ],
    );
  }

  Widget _buildContent(BuildContext context) {
    final n = widget.entries.length;
    final cellW = _cellW;

    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown: (_) {
        if (_tapped) return;
        _tapped = true;
        // 手指一按下就抬起 —— 这才是"按压时胶囊变大"的反馈来源。
        _lifted = true;
        _bounceX = false;
        _bounceY = false;
        _start();
      },
      onTapUp: (d) {
        _tapped = false;
        _start();
        _onTapUp(d);
      },
      onTapCancel: () {
        _tapped = false;
        _start();
      },
      onHorizontalDragStart: _onDragStart,
      onHorizontalDragUpdate: _onDragUpdate,
      onHorizontalDragEnd: _onDragEnd,
      onHorizontalDragCancel: _onDragCancel,
      child: Stack(
        children: [
          // 图标 + 文字：每个 cell 的颜色 / 字号 / 字重都按 _activationAt
          // （胶囊矩形对 cell 的覆盖比例 + smoothstep）做线性插值——胶囊
          // 压过多少，颜色就变多少，切换自然不突兀。
          for (var i = 0; i < n; i++)
            Positioned(
              left: i * cellW,
              top: 0,
              width: cellW,
              height: widget.height,
              child: _buildCell(context, i, _activationAt(i)),
            ),
        ],
      ),
    );
  }

  Widget _buildCell(BuildContext context, int index, double activation) {
    final entry = widget.entries[index];
    final scheme = Theme.of(context).colorScheme;
    // 激活态渐入主题色（对齐 pixez：选中图标/文字是主色，不是纯变亮）；
    // 未激活：onSurface @ 0.6。颜色随覆盖比例连续插值，无硬切。
    final color = Color.lerp(
      scheme.onSurface.withValues(alpha: 0.6),
      scheme.primary,
      activation,
    )!;
    // 字号 21 → 23、字重 500 → 700 同步随 activation 变化，扫过时图
    // 标"鼓起"一点点，强化渐变观感（56dp 紧凑栏相应缩一号）。
    final iconSize = ui.lerpDouble(21, 23, activation)!;
    final fontWeight =
        FontWeight.lerp(FontWeight.w500, FontWeight.w700, activation)!;
    // 图标形态也按 activation 插值：t≈0 用 entry.icon，t=1 用 entry.activeIcon。
    // 由于 `Icon` 不能像颜色那样渐变，用最接近的两种形态：
    //   activation > 0.5 → activeIcon；否则 → icon。
    // 这给到一个软切换，比纯 boolean 更柔（中间帧仍是普通 icon，但颜色
    // 已经在加深）。
    final showActive = activation > 0.5;
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(
          showActive ? entry.activeIcon : entry.icon,
          size: iconSize,
          color: color,
        ),
        const SizedBox(height: 1),
        Text(
          entry.label,
          style: TextStyle(
            fontSize: 10,
            height: 1.0,
            fontWeight: fontWeight,
            color: color,
          ),
        ),
      ],
    );
  }
}
