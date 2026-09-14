import 'package:flutter/material.dart';
import 'package:venera/components/components.dart';

import 'app_page_route.dart';

extension Navigation on BuildContext {
  void pop<T>([T? result]) {
    if(mounted) {
      Navigator.of(this).pop(result);
    }
  }

  bool canPop() {
    return Navigator.of(this).canPop();
  }

  /// [sharedElementPopTransition]：页面内自带共享元素（Hero）的跟手返回，
  /// 页面本身不做位移/缩放/圆角形变，只随返回手势淡出。阅读器用它，见
  /// [AppPageRoute.sharedElementPopTransition]。
  ///
  /// ⚠️ **只在页面内真的挂了 Hero 时才传 true**。`app_page_route.dart:250`
  /// 一旦为 true 就整段跳过 miuix 滑动壳（**push 方向同样生效**，名字带 Pop
  /// 但与方向无关），页面只剩淡入淡出，形变完全交给 Hero。
  /// - 有共享元素 → 必须 true。否则「页面滑动 + Hero 形变」两套运动叠加，
  ///   观感就是"乱"（2026-09-14 定位了半天的根因，也是 Hero 乱飞清单里的
  ///   第 3 条：自定义 Route 的 transition 和 Hero 抢同一个位移）。
  ///   详情页判定 = 调用点有没有传 `heroID`，共 7 处（comic.dart ×2 /
  ///   home_page ×4 / local_favorites ×1）。
  /// - 没有共享元素 → 必须 false，否则页面凭空失去常规滑动，只干巴巴淡入，
  ///   与应用其它页面不一致。
  ///
  /// [transitionDuration] / [reverseTransitionDuration]：转场时长，默认
  /// 300ms。共享元素光学转场（阅读器）传 620ms / 300ms。
  Future<T?> to<T>(
    Widget Function() builder, {
    bool sharedElementPopTransition = false,
    Duration transitionDuration = const Duration(milliseconds: 300),
    Duration reverseTransitionDuration = const Duration(milliseconds: 300),
  }) {
    return Navigator.of(this).push<T>(AppPageRoute(
        sharedElementPopTransition: sharedElementPopTransition,
        transitionDuration: transitionDuration,
        reverseTransitionDuration: reverseTransitionDuration,
        builder: (context) => builder()));
  }

  Future<void> toReplacement<T>(Widget Function() builder) {
    return Navigator.of(this).pushReplacement(AppPageRoute(
        builder: (context) => builder()));
  }

  double get width => MediaQuery.of(this).size.width;

  double get height => MediaQuery.of(this).size.height;

  EdgeInsets get padding => MediaQuery.of(this).padding;

  EdgeInsets get viewInsets => MediaQuery.of(this).viewInsets;

  ColorScheme get colorScheme => Theme.of(this).colorScheme;

  Brightness get brightness => Theme.of(this).brightness;

  bool get isDarkMode => brightness == Brightness.dark;

  void showMessage({required String message}) {
    showToast(message: message, context: this);
  }

  Color useBackgroundColor(MaterialColor color) {
    return color[brightness == Brightness.light ? 100 : 800]!;
  }

  Color useTextColor(MaterialColor color) {
    return color[brightness == Brightness.light ? 800 : 100]!;
  }
}
