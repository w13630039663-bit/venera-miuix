part of "components.dart";

void showMenuX(BuildContext context, Offset location, List<MenuEntry> entries) {
  Navigator.of(context, rootNavigator: true).push(_MenuRoute(entries, location));
}

class _MenuRoute<T> extends PopupRoute<T> {
  final List<MenuEntry> entries;

  final Offset location;

  _MenuRoute(this.entries, this.location);

  // 快照必须关：预测返回手势期间 SnapshotWidget 会拍到过期纹理。
  @override
  bool get allowSnapshotting => false;

  @override
  Color? get barrierColor => Colors.transparent;

  @override
  bool get barrierDismissible => true;

  @override
  String? get barrierLabel => "menu";

  double get entryHeight => App.isMobile ? 42 : 36;

  /// iOS 26 液态玻璃弹窗跟随「外观 → Navigation Bar: Floating Glass」：
  /// 该选项代表整套玻璃视觉语言，切换回 Classic 时菜单同步回到原版。
  bool get _useGlass => appdata.settings['navBarStyle'] == 'floating';

  @override
  Widget buildPage(BuildContext context, Animation<double> animation,
      Animation<double> secondaryAnimation) {
    if (_useGlass) {
      return _buildGlassPage(context, animation);
    }
    return _buildClassicPage(context);
  }

  // ── iOS 26 液态玻璃菜单 ──
  // 材质**完全照抄导航栏高亮胶囊的抬起端**（官方
  // LiquidGlassNavBarMotionPill，见 navigation_bar.dart _buildPillStyle）：
  // 0x1CFFFFFF（≈11% 白）、blur 1.5、borderWidth 0.6、lightIntensity 1.3、
  // 折射 0.12 / 带宽 18 / 色散 0.002，Apple 连续圆角。与胶囊的区别只有
  // 两点：菜单只在按住时弹出（=「不按压胶囊不存在」），以及圆角取 26
  // 而非高度一半（菜单是纵向列表，取高度一半会变成透镜形）。
  // 文字颜色必须适配明暗主题（导航胶囊没有文字，不能照抄）。
  Widget _buildGlassPage(
      BuildContext context, Animation<double> animation) {
    var width = entries.first.icon == null ? 216.0 : 242.0;
    final size = MediaQuery.of(context).size;
    var left = location.dx;
    if (left < 10) {
      left = 10;
    }
    if (left + width > size.width - 10) {
      left = size.width - width - 10;
    }
    var top = location.dy;
    var height = 16 + entryHeight * entries.length;
    if (top + height > size.height - 15) {
      top = size.height - height - 15;
    }
    final isDark = context.brightness == Brightness.dark;
    // 缩放锚点近似按住的位置：按按压点相对菜单的象限取 -1~1。
    final ax = ((location.dx - left) / width * 2 - 1).clamp(-1.0, 1.0);
    final ay = ((location.dy - top) / height * 2 - 1).clamp(-1.0, 1.0);
    return Stack(
      children: [
        Positioned(
          left: left,
          top: top,
          child: ScaleTransition(
            scale: CurvedAnimation(
              parent: animation,
              curve: const Cubic(0.16, 1.0, 0.3, 1.0),
            ).drive(Tween<double>(begin: 0.84, end: 1.0)),
            alignment: Alignment(ax, ay),
            child: LiquidGlassLens(
              style: LiquidGlassStyle(
                shape: LiquidGlassShape.continuousRoundedRectangle(
                  cornerRadius: 26,
                  borderWidth: 0.6,
                  lightIntensity: 1.3,
                  lightDirection: 80,
                ),
                appearance: const LiquidGlassAppearance(
                  color: Color(0x1CFFFFFF),
                  blur: LiquidGlassBlur(sigmaX: 1.5, sigmaY: 1.5),
                ),
                refraction: const LiquidGlassRefraction(
                  distortion: 0.12,
                  distortionWidth: 18,
                  chromaticAberration: 0.002,
                ),
              ),
              // 导航栏同款软体物理：按住菜单项时玻璃整体下陷、松手回弹
              // （官方 LiquidGlassFlex 的 tap/hold 反馈，见导航栏 _flex）。
              touch: const LiquidGlassTouch(
                flex: LiquidGlassFlex(
                  stretch: 14,
                  squeeze: 0.7,
                  lean: 0.5,
                  grip: 0,
                  compressInward: true,
                  holdScale: 0.06,
                  tapScale: 0.03,
                  maxPull: 48,
                ),
              ),
              child: Material(
                color: Colors.transparent,
                child: Container(
                  width: width,
                  padding:
                      const EdgeInsets.symmetric(vertical: 8, horizontal: 6),
                  child: DefaultTextStyle(
                    style: TextStyle(
                      color: isDark ? Colors.white : Colors.black87,
                      fontSize: 15,
                      decoration: TextDecoration.none,
                      fontWeight: FontWeight.w400,
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children:
                          entries.map((e) => buildEntry(e, context)).toList(),
                    ),
                  ),
                ),
              ),
            ),
          ),
        )
      ],
    );
  }

  Widget _buildClassicPage(BuildContext context) {
    var width = entries.first.icon == null ? 216.0 : 242.0;
    final size = MediaQuery.of(context).size;
    var left = location.dx;
    if (left < 10) {
      left = 10;
    }
    if (left + width > size.width - 10) {
      left = size.width - width - 10;
    }
    var top = location.dy;
    var height = 16 + entryHeight * entries.length;
    if (top + height > size.height - 15) {
      top = size.height - height - 15;
    }
    return Stack(
      children: [
        Positioned(
          left: left,
          top: top,
          child: Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(4),
              border: context.brightness == Brightness.dark
                  ? Border.all(color: context.colorScheme.outlineVariant)
                  : null,
              boxShadow: [
                BoxShadow(
                  color: context.colorScheme.shadow.toOpacity(0.2),
                  blurRadius: 8,
                  blurStyle: BlurStyle.outer,
                ),
              ],
            ),
            child: BlurEffect(
              borderRadius: BorderRadius.circular(4),
              child: Material(
                color: context.colorScheme.surface.toOpacity(0.92),
                borderRadius: BorderRadius.circular(4),
                child: Container(
                  width: width,
                  padding:
                      const EdgeInsets.symmetric(vertical: 12, horizontal: 6),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children:
                        entries.map((e) => buildEntry(e, context)).toList(),
                  ),
                ),
              ),
            ),
          ),
        )
      ],
    );
  }

  Widget buildEntry(MenuEntry entry, BuildContext context) {
    final isDark = context.brightness == Brightness.dark;
    return InkWell(
      borderRadius: BorderRadius.circular(_useGlass ? 12 : 4),
      onTap: () {
        Navigator.of(context).pop();
        entry.onClick();
      },
      child: SizedBox(
        height: entryHeight,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Row(
            children: [
              if (entry.icon != null)
                Icon(
                  entry.icon,
                  size: 18,
                  color: entry.color ??
                      (_useGlass
                          ? (isDark ? Colors.white : Colors.black87)
                          : null),
                ),
              const SizedBox(width: 12),
              Text(
                  entry.text,
                  style: TextStyle(
                      color: entry.color ??
                          (_useGlass
                              ? (isDark ? Colors.white : Colors.black87)
                              : null),
                      decoration: TextDecoration.none,
                      fontSize: _useGlass ? 15 : null,
                      fontWeight:
                          _useGlass ? FontWeight.w400 : FontWeight.normal,
                  )
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Duration get transitionDuration =>
      Duration(milliseconds: _useGlass ? 280 : 200);

  // 库官方注释（liquid_glass_dialog.dart transitionBuilder）：玻璃透镜
  // 外**不能套 Opacity/Fade**——Opacity 会隔离透镜的背景采样，动画中途
  // 玻璃失去身后页面、结束时突然跳变。因此玻璃模式只用缩放过渡，
  // 曲线与库 showLiquidGlassDialog 一致；原版菜单保留淡入。
  @override
  Widget buildTransitions(BuildContext context, Animation<double> animation,
      Animation<double> secondaryAnimation, Widget child) {
    if (_useGlass) {
      return child;
    }
    return FadeTransition(
      opacity: animation.drive(Tween<double>(begin: 0, end: 1)
          .chain(CurveTween(curve: Curves.ease))),
      child: child,
    );
  }
}

class MenuEntry {
  final String text;
  final IconData? icon;
  final Color? color;
  final void Function() onClick;

  MenuEntry({required this.text, this.icon, this.color, required this.onClick});
}
