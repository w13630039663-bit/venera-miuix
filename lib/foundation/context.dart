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
  /// 页面本身不做缩放/圆角形变，只随返回手势淡出。阅读器用它，见
  /// [AppPageRoute.sharedElementPopTransition]。
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
