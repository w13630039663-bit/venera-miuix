import 'dart:math';
import 'dart:ui';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:venera/components/background.dart';
import 'package:venera/foundation/app.dart';

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
}

mixin _AppRouteTransitionMixin<T> on PageRoute<T> {
  /// Builds the primary contents of the route.
  @protected
  Widget buildContent(BuildContext context);

  @override
  Duration get transitionDuration => const Duration(milliseconds: 300);

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
  final content = RepaintBoundary(
    child: Stack(
      fit: StackFit.expand,
      children: [
        ColoredBox(color: Theme.of(context).colorScheme.surface),
        if (AppBackground.enabled)
          RepaintBoundary(child: AppBackground.buildImmersive(context)),
        child,
      ],
    ),
  );

  return builder.buildTransitions(
        this,
        context,
        animation,
        secondaryAnimation,
    enableIOSGesture && App.isIOS
      ? IOSBackGestureDetector(
        gestureWidth: _kBackGestureWidth,
        enabledCallback: () => _isPopGestureEnabled<T>(this),
        onStartPopGesture: () => _startPopGesture(this),
        child: content,
        )
      : content);
  }

  IOSBackGestureController _startPopGesture(PageRoute<T> route) {
    return IOSBackGestureController(route.controller!, route.navigator!);
  }
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
