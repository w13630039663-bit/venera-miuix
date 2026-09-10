part of 'components.dart';

class PopUpWidget<T> extends PopupRoute<T> {
  PopUpWidget(this.widget);

  final Widget widget;

  // 快照必须关：预测返回手势期间 SnapshotWidget 会拍到过期纹理，
  // 导致手势中多层旧帧叠加（见 app_page_route.dart 同名注释）。
  @override
  bool get allowSnapshotting => false;

  @override
  Color? get barrierColor {
    // 全屏呈现（窄屏）时页面自身已透明、露出根部壁纸，54% 黑的 barrier
    // 会把壁纸整体压暗一层 —— 置透明；宽屏浮动卡片保持半透明遮罩。
    final context = App.rootNavigatorKey.currentContext;
    if (context != null && MediaQuery.of(context).size.width <= 500) {
      return Colors.transparent;
    }
    return Colors.black54;
  }

  @override
  bool get barrierDismissible => true;

  @override
  String? get barrierLabel => "exit";

  @override
  Widget buildPage(BuildContext context, Animation<double> animation,
      Animation<double> secondaryAnimation) {
    var height = MediaQuery.of(context).size.height * 0.9;
    bool showPopUp = MediaQuery.of(context).size.width > 500;
    Widget body = PopupIndicatorWidget(
      child: Container(
        decoration: showPopUp
            ? BoxDecoration(
                borderRadius: BorderRadius.all(Radius.circular(12)),
                boxShadow: context.brightness == ui.Brightness.dark ? [
                  BoxShadow(
                    color: Colors.white.withAlpha(50),
                    blurRadius: 10,
                    offset: Offset(0, 2),
                  ),
                ] : null,
              )
            : null,
        clipBehavior: showPopUp ? Clip.antiAlias : Clip.none,
        width: showPopUp ? 500 : double.infinity,
        height: showPopUp ? height : double.infinity,
        child: ClipRect(
          child: Navigator(
            onGenerateRoute: (settings) => MaterialPageRoute(
              builder: (context) => widget,
            ),
          ),
        ),
      ),
    );
    if (App.isIOS) {
      body = IOSBackGestureDetector(
        enabledCallback: () => true,
        gestureWidth: 20.0,
        onStartPopGesture: () =>
            IOSBackGestureController(controller!, navigator!),
        child: body,
      );
    }
    // 全屏呈现时，在路由层垫上与全局一致的实底背景：本路由是非 opaque
    // 的 PopupRoute，其下所有路由（主页等）在 Overlay 上始终处于舞台中
    // 被绘制，弹层内容若自身是透明 Scaffold（下载页、搜索/分类/探索页
    // 管理、漫画源列表、WebDAV 设置等），下层页面会直接透上来。
    //
    // 必须无条件垫底、不能只垫给沉浸模式：主题把全部 Scaffold 背景设成
    // 透明、页面透明地铺在根部背景上，off 模式（默认）下路由自身无实底，
    // 转场/弹层动画中底页内容照样穿透（与 AppPageRoute 同因同修）。off
    // 模式时兜底 surface 实底；开启壁纸/氛围光时加铺沉浸背景。
    if (!showPopUp) {
      body = Stack(
        fit: StackFit.expand,
        children: [
          ColoredBox(color: Theme.of(context).colorScheme.surface),
          if (AppBackground.enabled)
            RepaintBoundary(child: AppBackground.buildImmersive(context)),
          body,
        ],
      );
    }
    if (showPopUp) {
      return MediaQuery.removePadding(
        removeTop: true,
        context: context,
        child: Center(
          child: body,
        ),
      );
    }
    return body;
  }

  @override
  Duration get transitionDuration => const Duration(milliseconds: 350);

  @override
  Widget buildTransitions(BuildContext context, Animation<double> animation,
      Animation<double> secondaryAnimation, Widget child) {
    return FadeTransition(
      opacity: animation.drive(
        Tween(begin: 0.0, end: 1.0).chain(CurveTween(curve: Curves.ease)),
      ),
      child: child,
    );
  }
}

class PopupIndicatorWidget extends InheritedWidget {
  const PopupIndicatorWidget({super.key, required super.child});

  @override
  bool updateShouldNotify(covariant InheritedWidget oldWidget) => false;

  static PopupIndicatorWidget? maybeOf(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<PopupIndicatorWidget>();
  }
}

Future<T> showPopUpWidget<T>(BuildContext context, Widget widget) async {
  return await Navigator.of(context, rootNavigator: true)
      .push(PopUpWidget(widget));
}

class PopUpWidgetScaffold extends StatefulWidget {
  const PopUpWidgetScaffold(
      {required this.title, required this.body, this.tailing, super.key});

  final Widget body;
  final List<Widget>? tailing;
  final String title;

  @override
  State<PopUpWidgetScaffold> createState() => _PopUpWidgetScaffoldState();
}

class _PopUpWidgetScaffoldState extends State<PopUpWidgetScaffold> {
  bool top = true;

  @override
  Widget build(BuildContext context) {
    // 全屏呈现（非浮动卡片）时页面透明：与其它页面一致，把同一份沉浸式
    // 背景画在自己内部（不画的话，非 opaque 弹层会让下层页面内容互相
    // 穿透）；浮动卡片内（宽屏弹窗）保持不透明，卡片本身是实心面板。
    final inFloatingCard = PopupIndicatorWidget.maybeOf(context) != null;
    final column = Column(
        children: [
          Container(
            height: 56 + context.padding.top,
            padding: EdgeInsets.only(top: context.padding.top),
            width: double.infinity,
            decoration: BoxDecoration(
              color: top
                  ? null
                  : Theme.of(context).colorScheme.surfaceTint.withAlpha(20),
            ),
            child: Row(
              children: [
                const SizedBox(
                  width: 8,
                ),
                Tooltip(
                  message: "Back".tl,
                  child: IconButton(
                    icon: const Icon(Icons.arrow_back_sharp),
                    onPressed: () =>
                        context.canPop() ? context.pop() : App.pop(),
                  ),
                ),
                const SizedBox(
                  width: 16,
                ),
                Text(
                  widget.title,
                  style: const TextStyle(
                      fontSize: 22, fontWeight: FontWeight.w500),
                ),
                const Spacer(),
                if (widget.tailing != null) ...widget.tailing!,
                const SizedBox(width: 8),
              ],
            ),
          ),
          NotificationListener<ScrollNotification>(
            onNotification: (notifications) {
              if (notifications.metrics.axisDirection != AxisDirection.down) {
                return false;
              }
              if (notifications.metrics.pixels ==
                      notifications.metrics.minScrollExtent &&
                  !top) {
                setState(() {
                  top = true;
                });
              } else if (notifications.metrics.pixels !=
                      notifications.metrics.minScrollExtent &&
                  top) {
                setState(() {
                  top = false;
                });
              }
              return false;
            },
            child: MediaQuery.removePadding(
              removeTop: true,
              context: context,
              child: Expanded(child: widget.body),
            ),
          ),
          SizedBox(
            height: MediaQuery.of(context).viewInsets.bottom -
                        0.05 * MediaQuery.of(context).size.height >
                    0
                ? MediaQuery.of(context).viewInsets.bottom -
                    0.05 * MediaQuery.of(context).size.height
                : 0,
          )
        ],
    );
    return Material(
      color: inFloatingCard ? null : Colors.transparent,
      child: inFloatingCard
          ? column
          : Stack(
              fit: StackFit.expand,
              children: [
                RepaintBoundary(
                  child: AppBackground.buildImmersive(context),
                ),
                column,
              ],
            ),
    );
  }
}
