import 'dart:async';
import 'dart:math';
import 'dart:ui';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:venera/components/background.dart';
import 'package:venera/foundation/app.dart';
import 'package:venera/foundation/appdata.dart';
import 'package:venera/foundation/preview_hero.dart';

const double _kBackGestureWidth = 20.0;
const int _kMaxDroppedSwipePageForwardAnimationTime = 800;
const int _kMaxPageBackAnimationTime = 300;
const double _kMinFlingVelocity = 1.0;

class AppPageRoute<T> extends PageRoute<T> with _AppRouteTransitionMixin{
  /// Construct a MaterialPageRoute whose contents are defined by [builder].
  AppPageRoute({
    required this.builder,
    super.settings,
    this.maintainState = true,
    super.fullscreenDialog,
    // 快照必须关：预测返回手势期间框架用 SnapshotWidget 把页面离屏
    // 拍成静态图来播转场（TransitionRoute 默认 true），部分机型/渲染
    // 引擎上会拍到过期纹理（实测：手势中混出开屏 splash 旧帧、多层
    // 页面内容叠加，静止时完全正常）。关掉后手势动画直接用实时渲染
    // 的页面，配合每路由自绘背景观感一致。
    super.allowSnapshotting = false,
    super.barrierDismissible = false,
    this.enableIOSGesture = true,
    this.preventRebuild = true,
    this.sharedElementPopTransition = false,
    this.transitionDuration = const Duration(milliseconds: 300),
    this.reverseTransitionDuration = const Duration(milliseconds: 300),
  }) {
    assert(opaque);
  }

  /// Builds the primary contents of the route.
  final WidgetBuilder builder;

  String? label;

  @override
  toString() => "/$label";

  @override
  Widget buildContent(BuildContext context) {
    var widget = builder(context);
    label = widget.runtimeType.toString();
    return widget;
  }

  @override
  final bool maintainState;

  @override
  String get debugLabel => '${super.debugLabel}(${settings.name})';

  @override
  final bool enableIOSGesture;

  @override
  final bool preventRebuild;

  /// 该路由的"跟手返回"由页面内的共享元素（Hero）自己完成，页面本身不做
  /// 形变。详见 [_AppRouteTransitionMixin.buildTransitions]。
  @override
  final bool sharedElementPopTransition;

  /// 转场时长（默认 300ms）。共享元素路由（阅读器）传更长值以对齐
  /// demo 的 620ms 光学转场。
  @override
  final Duration transitionDuration;

  @override
  final Duration reverseTransitionDuration;
}

mixin _AppRouteTransitionMixin<T> on PageRoute<T> {
  /// Builds the primary contents of the route.
  @protected
  Widget buildContent(BuildContext context);

  @override
  Color? get barrierColor => null;

  @override
  String? get barrierLabel => null;

  @override
  bool canTransitionTo(TransitionRoute<dynamic> nextRoute) {
    // Don't perform outgoing animation if the next route is a fullscreen dialog.
    return nextRoute is PageRoute && !nextRoute.fullscreenDialog;
  }

  bool get enableIOSGesture;

  bool get preventRebuild;

  bool get sharedElementPopTransition;

  /// 嵌套导航器防「一次返回手势弹两层」。
  ///
  /// `PredictiveBackPageTransitionsBuilder` 会为每个路由挂一个全局
  /// `WidgetsBindingObserver`，认领条件是 `route.isCurrent` —— 但该条件
  /// 只表示「自己所在 navigator 的栈顶」。本项目是嵌套导航器结构
  /// （根 Navigator + 主内容区内嵌 Navigator），两个 navigator 的栈顶页
  /// 会同时认领同一次返回手势：阅读器（根）返回时详情页（内嵌）被连带
  /// 弹掉直达主页；评论侧栏（根 PopupRoute）打开时每次手势把内嵌栈
  /// 一层层弹空；下层页还会跟着做返回动画导致背景透掉。
  ///
  /// 这里限定全应用同一时刻只有一个路由可认领：内嵌 Navigator 的路由
  /// 仅当根 Navigator 没有额外页面（只剩 MainPage 底座）时才允许；
  /// 根 Navigator 的栈顶页本身就是全应用顶，照常允许。
  @override
  bool get popGestureEnabled {
    if (!super.popGestureEnabled) {
      return false;
    }
    final innerNavigator = App.mainNavigatorKey?.currentState;
    if (innerNavigator != null && identical(navigator, innerNavigator)) {
      final rootNavigator = App.rootNavigatorKey.currentState;
      if (rootNavigator != null && rootNavigator.canPop()) {
        return false;
      }
    }
    return true;
  }

  Widget? _child;

  @override
  Widget buildPage(
      BuildContext context,
      Animation<double> animation,
      Animation<double> secondaryAnimation,
      ) {
    Widget result;

    if(preventRebuild){
      result = _child ?? (_child = buildContent(context));
    } else {
      result = buildContent(context);
    }

    return Semantics(
      scopesRoute: true,
      explicitChildNodes: true,
      child: result,
    );
  }

  static bool _isPopGestureEnabled<T>(PageRoute<T> route) {
    if (route.isFirst ||
        route.willHandlePopInternally ||
        route.popDisposition == RoutePopDisposition.doNotPop ||
        route.fullscreenDialog ||
        route.animation!.status != AnimationStatus.completed ||
        route.secondaryAnimation!.status != AnimationStatus.dismissed ||
        !route.popGestureEnabled ||
        route.navigator!.userGestureInProgress) {
      return false;
    }

    return true;
  }

  @override
  Widget buildTransitions(BuildContext context, Animation<double> animation, Animation<double> secondaryAnimation, Widget child) {
    // 预测返回手势必须由 PredictiveBackPageTransitionsBuilder 提供 ——
    // 它在 buildTransitions 内挂 _PredictiveBackGestureDetector 并向
    // WidgetsBinding 注册 observer
    // （material/predictive_back_page_transitions_builder.dart:297），是系统
    // 预测返回事件（handleStartBackGesture / updateBackGestureProgress）的
    // 唯一接收方。此前换成 SlidePageTransitionBuilder 后事件无人接收，
    // 边缘滑动全部退化成普通返回 —— 即「预测手势被禁用」。现恢复。
    //
    // fallbackColor 透明：框架默认给转场垫 colorScheme.surface（深色≈黑），
    // 在壁纸/氛围光模式下会闪一下黑；每个路由已自绘背景，无需再垫。
    final PageTransitionsBuilder builder = App.isAndroid
        ? const PredictiveBackPageTransitionsBuilder(
            fallbackColor: Colors.transparent,
          )
        : SlidePageTransitionBuilder();

  // 沉浸式背景：兜底实底 surface（最底层）+ 壁纸/氛围光（中间）+ 页面（顶层）。
  // 兜底 surface 是硬保证：即便 AppBackground 因图片异步解码、构建时机
  // 异常、模式切换等极端情况出现一帧没铺满，底页也透不出来。壁纸/氛围光
  // 正常铺满时整个 surface 被覆盖、不可见；异常时透出来兜底，杜绝转场中途
  // 两页内容互相穿透（主人 03:50 截图复现了氛围光模式漏 fit 的问题）。
  //
  // 页面（child）外包一层 AOSP 式缩放：被上层覆盖时缩到 0.92，返回揭开时
  // 放大回 1.0（stock Android 预测返回的下层页动效）。只缩放、**不淡出** ——
  // 缩小时露出的边缘由底下两层（surface/壁纸）兜住，不会透出窗口黑底。
  final content = RepaintBoundary(
    child: Stack(
      fit: StackFit.expand,
      children: [
        ColoredBox(color: Theme.of(context).colorScheme.surface),
        if (AppBackground.enabled)
          RepaintBoundary(child: AppBackground.buildImmersive(context)),
        AnimatedBuilder(
          animation: secondaryAnimation,
          builder: (context, child) {
            final t = secondaryAnimation.value.clamp(0.0, 1.0);
            return Transform.scale(
              scale: 0.92 + 0.08 * (1 - t),
              child: child,
            );
          },
          child: child,
        ),
      ],
    ),
  );

  // 景深模糊（MIUI 同款）：被下层页覆盖时按覆盖进度实时模糊，返回时随手势
  // 退去。AOSP / MIUIX 两种返回动画共用；阅读器返回时详情页的景深也来自这里。
  // 仅动画期间生效（status forward/reverse），完全盖住（被不透明上层遮挡、
  // 不可见）或完全露出时零 GPU 开销；sigma 按 2px 量化减少 ImageFilter 重建
  // （BiliPai 用 4px）。
  final depthBlurred = _DepthBlur(
    secondaryAnimation: secondaryAnimation,
    child: content,
  );

  final Widget transitionChild = enableIOSGesture && App.isIOS
      ? IOSBackGestureDetector(
          gestureWidth: _kBackGestureWidth,
          enabledCallback: () => _isPopGestureEnabled<T>(this),
          onStartPopGesture: () => _startPopGesture(this),
          child: depthBlurred,
        )
      : depthBlurred;

  // 共享元素路由（阅读器）：真正的"跟手缩回"由页面里的 Hero 那张图完成，
  // 页面本身不该再形变。PredictiveBackPageTransitionsBuilder 在手势期间会把
  // 整页缩到 0.9 并加圆角（_PredictiveBackSharedElementPageTransition ——
  // Android 官方共享元素转场在没有真共享元素时的兜底视觉），而阅读器是深色
  // 满屏，一缩就露出四周一大块深色、和图片变成两层不同步的缩放。Flutter
  // 3.47 这个 builder 只有 fallbackColor，没有可换 delegate 的口子。
  // 所以把「转场壳」套在一个空壳上：只为保住 _PredictiveBackGestureDetector
  // （系统预测返回事件的唯一接收方，摘了手势就失效），真实页面不做任何形变，
  // 只随 route.animation 淡出/淡入。预测返回期间 route.animation 就是手指
  // 进度（handleStartBackGestureProgress 直接驱动它），于是「图跟手缩回 +
  // 页面跟手淡出」天然同源同步。iOS 走滑动转场，不存在这个问题。
  if (!(sharedElementPopTransition && App.isAndroid)) {
    // 被覆盖时不做框架的二级转场（secondary 换常量 0）：旧页保持完全可见，
    // 新页直接盖上来 —— 防止框架 FadeForwards 的「旧页淡出」透出黑色窗口底
    // （详见 git 历史中本段注释的完整分析）。
    if (App.isAndroid &&
        appdata.settings['backAnimStyle'] == 'miuix') {
      // MIUIX：整页从右侧滑入（push）/ 跟手滑出（预测返回手势与返回键），
      // 下层页由 _DepthBlur 做景深模糊 + 轻视差；commit 重置由
      // _SharedElementPopRestore 统一修复。
      //
      // ⚠️ shell 必须保留：builder 是 _PredictiveBackGestureDetector 的
      // 挂载点（系统预测返回事件的唯一接收方），丢掉它返回手势就完全失效
      // （与 sharedElement 分支的空壳同款做法，空壳无视觉不挡交互）。
      final Widget shell = builder.buildTransitions(this, context, animation,
          secondaryAnimation, const SizedBox.expand());
      return Stack(
        fit: StackFit.expand,
        children: [
          _SharedElementPopRestore(
            route: this,
            controller: controller!,
            enabledCallback: () => isCurrent && popGestureEnabled,
            child: _MiuixSlideTransition(
              animation: animation,
              child: transitionChild,
            ),
          ),
          IgnorePointer(child: shell),
        ],
      );
    }
    // AOSP：框架 PredictiveBackPageTransitionsBuilder 的缩放 + 圆角 + 横移。
    return builder.buildTransitions(
        this, context, animation, kAlwaysDismissedAnimation, transitionChild);
  }

  final Widget shell = builder.buildTransitions(
      this, context, animation, secondaryAnimation, const SizedBox.expand());
  return Stack(
    fit: StackFit.expand,
    children: [
      // 动画值在「松手提交」时会被框架重置回 1.0（原因见
      // _SharedElementPopRestore 的注释），这个 widget 负责把它拉回手指最后
      // 停下的位置，图片和页面才会从那里接着缩回去，而不是重播一遍。
      _SharedElementPopRestore(
        route: this,
        controller: controller!,
        enabledCallback: () => isCurrent && popGestureEnabled,
        // demo 的背景淡出也是 smoothstep(p)（backdrop(e)），页面淡出同源。
        child: FadeTransition(
          opacity: const _SmoothStepTween().animate(animation),
          child: transitionChild,
        ),
      ),
      IgnorePointer(child: shell),
    ],
  );
}

  IOSBackGestureController _startPopGesture(PageRoute<T> route) {
    return IOSBackGestureController(route.controller!, route.navigator!);
  }
}

/// demo 的 smoothstep：pe(p) = p²(3−2p)。用于共享元素路由的页面淡出
/// （两个方向都是 raw 值的 smoothstep，与 demo backdrop 同源）。
class _SmoothStepTween extends Animatable<double> {
  const _SmoothStepTween();

  @override
  double transform(double t) => t * t * (3 - 2 * t);
}

/// 修「返回时图片已经跟手缩到卡片了，松手后又整个重播一遍缩回动画」。
///
/// 根因在上游：Flutter 3.47 的预测返回提交路径（`TransitionRoute._handleDragEnd`，
/// 由 PR #154718「Shared element transition for predictive back」改成）在松手
/// 提交时会先把路由动画**重置回 1.0** 再反向播放：
///
/// ```dart
/// navigator?.pop();
/// if (_controller?.isAnimating ?? false) {
///   _controller!.reverse(from: _controller!.upperBound);  // ← 重置
/// }
/// ```
///
/// 这是给那个规范里的「回弹 + 淡出」用的（commit 之后需要一段完整的 1→0 给
/// `_PredictiveBackSharedElementPageTransition`），但它同时也把任何
/// `transitionOnUserGestures` 的 Hero 一起重置了：Hero 的飞行进度就是
/// `ReverseAnimation(route.animation)`，动画一回到 1.0，图片与页面就跳回全屏，
/// 再把手指已经拖过的那段重播一遍。框架这条路径没有测试覆盖
/// （`widgets/heroes_test.dart` 里没有预测返回的手势用例），所以是上游的盲区。
///
/// 修法**不碰手势本身**：手势仍由框架的 `PredictiveBackPageTransitionsBuilder`
/// 驱动（这里只是搭个便车收一份事件，用来记录手指的进度），提交时把动画值拉回
/// 手指最后给出的位置再让它从那里继续反向。用微任务是为了不依赖两个观察者的
/// 回调顺序（无论是框架的 detector 还是本 widget 先收到 commit 都成立）；如果
/// 上游哪天不再重置，`controller.value > anchor` 不成立，这里自动变成空操作。
class _SharedElementPopRestore extends StatefulWidget {
  const _SharedElementPopRestore({
    required this.route,
    required this.controller,
    required this.enabledCallback,
    required this.child,
  });

  final PageRoute<dynamic> route;

  final AnimationController controller;

  /// 必须与框架 `_PredictiveBackGestureDetector` 的认领条件等价或更严格，
  /// 否则会出现「我们认领了但框架没认领」——手势被吞掉、返回彻底不动。
  final bool Function() enabledCallback;

  final Widget child;

  @override
  State<_SharedElementPopRestore> createState() =>
      _SharedElementPopRestoreState();
}

class _SharedElementPopRestoreState extends State<_SharedElementPopRestore>
    with WidgetsBindingObserver {
  /// 手指最近一次给出的进度，等于提交前一刻的路由动画值。null 表示当前没有
  /// 本 widget 认领的手势。
  double? _gestureValue;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  bool handleStartBackGesture(PredictiveBackEvent backEvent) {
    // 返回键（非手势）不走预测返回，交回框架默认处理。
    if (backEvent.isButtonEvent || !widget.enabledCallback()) {
      return false;
    }
    _gestureValue = 1 - backEvent.progress;
    return true;
  }

  @override
  void handleUpdateBackGestureProgress(PredictiveBackEvent backEvent) {
    _gestureValue = 1 - backEvent.progress;
  }

  @override
  void handleCancelBackGesture() {
    // 取消时框架会把动画正着播回 1.0，不需要我们插手。
    _gestureValue = null;
  }

  @override
  void handleCommitBackGesture() {
    final double? anchor = _gestureValue;
    _gestureValue = null;
    if (anchor == null) {
      return;
    }
    scheduleMicrotask(() {
      if (!mounted) {
        return;
      }
      final AnimationController controller = widget.controller;
      // 框架只在 pop 真的发生时才会执行 reverse(from: 1.0) 重置：此刻
      // controller 处于反向动画中、值被拉回 1.0。pop 没发生的异常路径下
      // controller 是 completed 且静止 —— 绝不插手，否则会把没弹出的页面
      // 自己淡出去。（不能用 route.isCurrent 判断「pop 是否发生」：pop()
      // 是同步的，返回时路由已进入 _RouteLifecycle.popping、不再是
      // present，isCurrent 恒为 false —— 之前的守卫因此把整个修复
      // 拦成了死代码。）
      if (!controller.isAnimating ||
          controller.status != AnimationStatus.reverse) {
        return;
      }
      // 框架没重置（或已经走到终点）就什么都不用做。
      if (controller.value <= anchor) {
        return;
      }
      controller.value = anchor;
      // demo closePanel：时长 = DUR × (0.35 + 0.65×(1−p))，时间曲线
      // easeOutQuart（快出手长滑行，无过冲）。easeOutQuart 由控制器提供，
      // tween 那边只做 smoothstep（见 preview_hero.dart 的 _OpticalRectTween），
      // 合成结果精确等于 demo 的收尾。
      controller.animateBack(
        0.0,
        duration: Duration(
          milliseconds: (kPreviewFlightDuration.inMilliseconds *
                  (0.35 + 0.65 * (1 - anchor)))
              .round(),
        ),
        curve: Curves.easeOutQuart,
      );
    });
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

class IOSBackGestureController {
  final AnimationController controller;

  final NavigatorState navigator;

  IOSBackGestureController(this.controller, this.navigator) {
    navigator.didStartUserGesture();
  }

  void dragEnd(double velocity) {
    const Curve animationCurve = Curves.fastLinearToSlowEaseIn;
    final bool animateForward;

    if (velocity.abs() >= _kMinFlingVelocity) {
      animateForward = velocity <= 0;
    } else {
      animateForward = controller.value > 0.5;
    }

    if (animateForward) {
      final droppedPageForwardAnimationTime = min(
        lerpDouble(
                _kMaxDroppedSwipePageForwardAnimationTime, 0, controller.value)!
            .floor(),
        _kMaxPageBackAnimationTime,
      );
      controller.animateTo(1.0,
          duration: Duration(milliseconds: droppedPageForwardAnimationTime),
          curve: animationCurve);
    } else {
      navigator.pop();
      if (controller.isAnimating) {
        final droppedPageBackAnimationTime = lerpDouble(
                0, _kMaxDroppedSwipePageForwardAnimationTime, controller.value)!
            .floor();
        controller.animateBack(0.0,
            duration: Duration(milliseconds: droppedPageBackAnimationTime),
            curve: animationCurve);
      }
    }

    if (controller.isAnimating) {
      late AnimationStatusListener animationStatusCallback;
      animationStatusCallback = (status) {
        navigator.didStopUserGesture();
        controller.removeStatusListener(animationStatusCallback);
      };
      controller.addStatusListener(animationStatusCallback);
    } else {
      navigator.didStopUserGesture();
    }
  }

  void dragUpdate(double delta) {
    controller.value -= delta;
  }
}

class IOSBackGestureDetector extends StatefulWidget {
  const IOSBackGestureDetector({
    required this.enabledCallback,
    required this.child,
    required this.gestureWidth,
    required this.onStartPopGesture,
    super.key,
  });

  final double gestureWidth;
  final bool Function() enabledCallback;
  final IOSBackGestureController Function() onStartPopGesture;
  final Widget child;

  @override
  State<IOSBackGestureDetector> createState() => _IOSBackGestureDetectorState();
}

class _IOSBackGestureDetectorState extends State<IOSBackGestureDetector> {
  IOSBackGestureController? _backGestureController;
  late _BackSwipeRecognizer _recognizer;


  @override
  void initState() {
    super.initState();
    _recognizer = _BackSwipeRecognizer(
      debugOwner: this,
      gestureWidth: widget.gestureWidth,
      isPointerInHorizontal: _isPointerInHorizontalScrollable,
      onStart: _handleDragStart,
      onUpdate: _handleDragUpdate,
      onEnd: _handleDragEnd,
      onCancel: _handleDragCancel,
    );
  }

  @override
  void dispose() {
    _recognizer.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return RawGestureDetector(
      behavior: HitTestBehavior.translucent,
      gestures: {
        _BackSwipeRecognizer: GestureRecognizerFactoryWithHandlers<_BackSwipeRecognizer>(
          () => _recognizer,
          (instance) {
            instance.gestureWidth = widget.gestureWidth;
          },
        ),
      },
      child: widget.child,
    );
  }

  bool _isPointerInHorizontalScrollable(Offset globalPosition) {
    final HitTestResult result = HitTestResult();
    final binding = WidgetsBinding.instance;
    binding.hitTestInView(result, globalPosition, binding.platformDispatcher.implicitView!.viewId);

    for (final entry in result.path) {
      final target = entry.target;
      if (target is RenderViewport) {
        if (target.axisDirection == AxisDirection.left || 
            target.axisDirection == AxisDirection.right) {
          return true;
        }
      } 
      else if (target is RenderSliver) {
         if (target.constraints.axisDirection == AxisDirection.left || 
             target.constraints.axisDirection == AxisDirection.right) {
          return true;
        }
      }
      else if (target.runtimeType.toString() == '_RenderSingleChildViewport') {
        try {
          final dynamic renderObject = target;
          if (renderObject.axis == Axis.horizontal) {
            return true;
          }
        } catch (e) {
          // protected
        }
      }
      else if (target is RenderEditable) {
         return true;
      }
    }
    return false;
  }

  void _handleDragStart(DragStartDetails details) {
    if (!widget.enabledCallback()) return;
    if (mounted && _backGestureController == null) {
      _backGestureController = widget.onStartPopGesture();
    }
  }

  void _handleDragUpdate(DragUpdateDetails details) {
    if (mounted && _backGestureController != null) {
      _backGestureController!.dragUpdate(
          _convertToLogical(details.primaryDelta! / context.size!.width));
    }
  }

  void _handleDragEnd(DragEndDetails details) {
    if (mounted && _backGestureController != null) {
      _backGestureController!.dragEnd(_convertToLogical(
          details.velocity.pixelsPerSecond.dx / context.size!.width));
      _backGestureController = null;
    }
  }

  void _handleDragCancel() {
    if (mounted && _backGestureController != null) {
      _backGestureController?.dragEnd(0.0);
      _backGestureController = null;
    }
  }

  double _convertToLogical(double value) {
    switch (Directionality.of(context)) {
      case TextDirection.rtl: return -value;
      case TextDirection.ltr: return value;
    }
  }
}

class _BackSwipeRecognizer extends OneSequenceGestureRecognizer {
  _BackSwipeRecognizer({
    required this.isPointerInHorizontal,
    required this.gestureWidth,
    required this.onStart,
    required this.onUpdate,
    required this.onEnd,
    required this.onCancel,
    super.debugOwner,
  });

  final bool Function(Offset globalPosition) isPointerInHorizontal;
  double gestureWidth;
  final ValueSetter<DragStartDetails> onStart;
  final ValueSetter<DragUpdateDetails> onUpdate;
  final ValueSetter<DragEndDetails> onEnd;
  final VoidCallback onCancel;

  Offset? _startGlobal;
  bool _accepted = false;
  bool _startedInHorizontal = false;
  bool _startedNearLeftEdge = false; 

  VelocityTracker? _velocityTracker;

  static const double _minDistance = 5.0; 

  @override
  void addPointer(PointerDownEvent event) {
    startTrackingPointer(event.pointer);
    _startGlobal = event.position;
    _accepted = false;
    
    _startedInHorizontal = isPointerInHorizontal(event.position);
    _startedNearLeftEdge = event.position.dx <= gestureWidth;

    _velocityTracker = VelocityTracker.withKind(event.kind);
    _velocityTracker?.addPosition(event.timeStamp, event.position);
  }

  @override
  void handleEvent(PointerEvent event) {
    if (event is PointerMoveEvent || event is PointerUpEvent) {
      _velocityTracker?.addPosition(event.timeStamp, event.position);
    }

    if (event is PointerMoveEvent) {
      if (_startGlobal == null) return;
      final delta = event.position - _startGlobal!;
      final dx = delta.dx;
      final dy = delta.dy.abs();

      if (!_accepted) {
        if (delta.distance < _minDistance) return;

        final isRight = dx > 0;
        final isHorizontal = dx.abs() > dy * 1.5;
        final bool eligible = _startedNearLeftEdge || (!_startedInHorizontal);

        if (isRight && isHorizontal && eligible) {
          _accepted = true;
          resolve(GestureDisposition.accepted);
          onStart(DragStartDetails(
            globalPosition: _startGlobal!, 
            localPosition: event.localPosition
          ));
        } else {
          resolve(GestureDisposition.rejected);
          stopTrackingPointer(event.pointer);
          _startGlobal = null;
          _velocityTracker = null;
        }
      }

      if (_accepted) {
        onUpdate(DragUpdateDetails(
          globalPosition: event.position,
          localPosition: event.localPosition,
          primaryDelta: event.delta.dx,
          delta: Offset(event.delta.dx, 0),
        ));
      }
    } else if (event is PointerUpEvent) {
      if (_accepted) {
        final Velocity velocity = _velocityTracker?.getVelocity() ?? Velocity.zero;
        
        onEnd(DragEndDetails(
          velocity: velocity,
          primaryVelocity: velocity.pixelsPerSecond.dx
        ));
      }
      _reset();
    } else if (event is PointerCancelEvent) {
      if (_accepted) {
        onCancel();
      }
      _reset();
    }
  }

  void _reset() {
    stopTrackingPointer(0);
    _accepted = false;
    _startGlobal = null;
    _startedInHorizontal = false;
    _startedNearLeftEdge = false;
    _velocityTracker = null;
  }

  @override
  String get debugDescription => 'IOSBackSwipe';

  @override
  void didStopTrackingLastPointer(int pointer) {}
}

class SlidePageTransitionBuilder extends PageTransitionsBuilder {
  @override
  Widget buildTransitions<T>(
      PageRoute<T> route,
      BuildContext context,
      Animation<double> animation,
      Animation<double> secondaryAnimation,
      Widget child) {
    final Animation<double> primaryAnimation = App.isIOS
        ? animation
        : CurvedAnimation(parent: animation, curve: Curves.easeOutCubic);
    final Animation<double> secondaryCurve = App.isIOS
        ? secondaryAnimation
        : CurvedAnimation(parent: secondaryAnimation, curve: Curves.easeOutCubic);

    // 纯滑动转场，全过程 alpha=1，杜绝「两页同时半透明 → 内容互透」
    // （之前 PredictiveBackPageTransitionsBuilder / FadeForwards 出现过，
    // 主人 03:43 截图复现）。SlideTransition 直接包 child，不再用
    // PhysicalModel/Material(color: transparent) 包装——后者会让页面
    // 背景透明，给未来埋雷。
    return SlideTransition(
      position: Tween<Offset>(
        begin: const Offset(1, 0),
        end: Offset.zero,
      ).animate(primaryAnimation),
      child: SlideTransition(
        position: Tween<Offset>(
          begin: Offset.zero,
          end: const Offset(-0.3, 0),
        ).animate(secondaryCurve),
        child: child,
      ),
    );
  }
}

/// 景深模糊（MIUI 同款）：本路由被上层页覆盖时，按覆盖进度实时模糊自身；
/// 返回揭开时随手势退去。驱动源 = secondaryAnimation（0 = 未被覆盖，
/// 1 = 完全被覆盖）。设计参数对齐 BiliPai 的 PredictiveBackBackgroundPolicy：
/// 仅动画期间生效、量化步长、浅色背景加薄纱防发灰。
class _DepthBlur extends StatelessWidget {
  const _DepthBlur({required this.secondaryAnimation, required this.child});

  final Animation<double> secondaryAnimation;

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: secondaryAnimation,
      child: child,
      builder: (context, child) {
        final status = secondaryAnimation.status;
        // 仅过渡动画期间生效：完全盖住（被不透明上层遮挡，模糊不可见）
        // 或完全露出（无需景深）时都是零 GPU 开销。
        if (status != AnimationStatus.forward &&
            status != AnimationStatus.reverse) {
          return child!;
        }
        final t = secondaryAnimation.value.clamp(0.0, 1.0);
        var sigma = 24.0 * t;
        sigma = (sigma / 2).roundToDouble() * 2; // 2px 量化
        if (sigma < 2) {
          return child!;
        }
        Widget page = ImageFiltered(
          imageFilter: ImageFilter.blur(sigmaX: sigma, sigmaY: sigma),
          child: child,
        );
        // MIUIX 模式：下层页随覆盖进度轻微左移（视差，HyperOS 同款层次）。
        if (appdata.settings['backAnimStyle'] == 'miuix') {
          page = Transform.translate(
            offset: Offset(
              -MediaQuery.of(context).size.width * 0.12 * t,
              0,
            ),
            child: page,
          );
        }
        // 浅色背景叠 5% 白纱，防止大面积模糊后发灰（BiliPai 同款）。
        if (Theme.of(context).brightness == Brightness.light) {
          page = ColoredBox(
            color: Colors.white.withValues(alpha: 0.05 * t),
            child: page,
          );
        }
        return page;
      },
    );
  }
}

/// MIUIX 返回动画：整页从右侧滑入（push，t 0→1）/ 跟手滑出（pop，t 1→0），
/// 一个公式覆盖两个方向。下层页的景深模糊与视差由 [_DepthBlur] 负责。
class _MiuixSlideTransition extends StatelessWidget {
  const _MiuixSlideTransition({required this.animation, required this.child});

  final Animation<double> animation;

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: animation,
      child: child,
      builder: (context, child) {
        final t = animation.value.clamp(0.0, 1.0);
        return Transform.translate(
          offset: Offset((1 - t) * MediaQuery.of(context).size.width, 0),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(24 * (1 - t)),
            child: child,
          ),
        );
      },
    );
  }
}
