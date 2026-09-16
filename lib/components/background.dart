import 'dart:io';
import 'dart:ui';

import 'package:flutter/material.dart';

import 'package:venera/foundation/appdata.dart';

/// 全局页面背景层。
///
/// 插在 App 根部（`main.dart` 的 `MaterialApp.builder`）最底层，所有页面
/// ——包括 34 个 `Scaffold`（主题里 `scaffoldBackgroundColor` 已置透明）
/// ——都透明地铺在它上面：
///
///   off       —— 纯 `surface`（与旧版实心背景完全一致，零回归）
///   ambient   —— Apple Music 式主题色氛围光（顶部大光斑 + 高斯模糊 + 暗化）
///   wallpaper —— 自定义壁纸 + 可调高斯模糊 + 暗化遮罩 + 底部渐入底色
///
/// 同时通过 [AppBackground.buildImmersive] 供每个路由自绘同一份背景：
/// Flutter 的 Overlay 只会隐藏「栈顶第一个 opaque 路由之下」的层，透明
/// 页面叠透明页面时会全部混画（下层页面内容互相穿透）。让每个路由把
/// 同一份全屏壁纸/氛围光画在自己内部，层与层之间便不再穿透，转场时
/// 视觉与「透出根部壁纸」完全一致。
class AppBackground extends StatelessWidget {
  const AppBackground({super.key});

  /// 沉浸式背景是否开启（mode != off）。
  static bool get enabled => appdata.settings['backgroundMode'] != 'off';

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      // `appdata.settings` 是 ChangeNotifier，设置写入时通知本层重建。
      listenable: appdata.settings,
      builder: (context, _) {
        return buildImmersive(context);
      },
    );
  }

  /// 不带监听的静态背景。根部实例负责监听设置变化；路由内部实例无需
  /// 监听（设置改动走 `App.forceRebuild` 重建路由）。
  static Widget buildImmersive(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final dark = Theme.of(context).brightness == Brightness.dark;
    final mode = appdata.settings['backgroundMode'];
    if (mode == 'ambient') {
      // RepaintBoundary：σ=90 的全屏高斯模糊非常昂贵，而它的内容只随主题色
      // 变化、不随页面重绘变化。加一层边界后光栅只算一次，之后的重绘与转场
      // 直接复用缓存，省掉每帧重算超大模糊的开销。
      return RepaintBoundary(child: _buildAmbient(context, cs, dark));
    }
    if (mode == 'wallpaper') {
      final path = appdata.settings['wallpaperPath'] as String? ?? '';
      if (path.isNotEmpty && File(path).existsSync()) {
        return _buildWallpaper(cs, dark, path);
      }
    }
    return ColoredBox(color: cs.surface);
  }

  /// Apple Music 式主题色氛围光：surface 底 + 顶部超大主色光斑经平滑径向渐变晕开。
  /// 采用 RadialGradient 代替 ImageFilter.blur(sigma: 90)，零离屏纹理与零高斯卷积开销，
  /// 视觉光晕 100% 保持自然柔和。深色下降低光斑透明度，避免刺眼。
  static Widget _buildAmbient(BuildContext context, ColorScheme cs, bool dark) {
    final size = MediaQuery.sizeOf(context);
    final glowSize = size.width * 1.6;
    Widget glow(Color color, double alpha, {double top = 0, double? left, double? right}) {
      return Positioned(
        top: top,
        left: left,
        right: right,
        child: Container(
          width: glowSize,
          height: glowSize * 0.8,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: RadialGradient(
              colors: [
                color.withValues(alpha: alpha),
                color.withValues(alpha: alpha * 0.65),
                color.withValues(alpha: alpha * 0.25),
                color.withValues(alpha: 0.0),
              ],
              stops: const [0.0, 0.35, 0.68, 1.0],
            ),
          ),
        ),
      );
    }

    return ClipRect(
      child: Stack(
        // fit: StackFit.expand 必须显式声明——默认 StackFit.loose 会按
        // 最大非定位子节点（glowSize = 屏幕宽×1.6）布局，导致背景只占中间
        // 一团、四周透明，转场中底页内容直接透出来（主人 03:50 截图复现）。
        fit: StackFit.expand,
        children: [
          Positioned.fill(child: ColoredBox(color: cs.surface)),
          glow(
            cs.primary,
            dark ? 0.30 : 0.45,
            top: -glowSize * 0.62,
            left: (size.width - glowSize) / 2,
          ),
          glow(
            cs.tertiary,
            dark ? 0.16 : 0.25,
            top: -glowSize * 0.35,
            right: -glowSize * 0.4,
          ),
          // 轻微暗化，给白色顶栏文字留对比度（深色模式下表面本身已暗）。
          if (!dark)
            Positioned.fill(
              child: ColoredBox(
                color: Colors.black.withValues(alpha: 0.08),
              ),
            ),
        ],
      ),
    );
  }

  /// 自定义壁纸背景：全屏铺满 + 按设置的 σ 值高斯模糊 + 暗化遮罩 +
  /// 底部渐入 surface，保证列表内容的可读性。
  static Widget _buildWallpaper(ColorScheme cs, bool dark, String path) {
    final sigma =
        (double.tryParse(appdata.settings['wallpaperBlur']?.toString() ?? '') ?? 24)
            .clamp(0.0, 40.0);
    return RepaintBoundary(
      child: Stack(
        fit: StackFit.expand,
        children: [
          ClipRect(
            // 壁纸铺满后留白边缘会发白，稍微放大避免模糊边界穿帮。
            child: ImageFiltered(
              imageFilter: ImageFilter.blur(sigmaX: sigma, sigmaY: sigma),
              child: Transform.scale(
                scale: sigma > 0 ? 1.0 + sigma / 60 : 1.0,
                child: Image(
                  key: ValueKey(path),
                  // 降采样解码：壁纸只需屏幕级分辨率，避免原图常驻内存。
                  image: ResizeImage(
                    FileImage(File(path)),
                    width: 1080,
                    policy: ResizeImagePolicy.exact,
                  ),
                  fit: BoxFit.cover,
                  alignment: Alignment.topCenter,
                ),
              ),
            ),
          ),
          ColoredBox(
            color: Colors.black.withValues(alpha: dark ? 0.45 : 0.28),
          ),
          DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                stops: const [0.45, 1.0],
                colors: [
                  cs.surface.withValues(alpha: 0),
                  cs.surface.withValues(alpha: 0.85),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
