import 'dart:async';
import 'dart:collection';
import 'dart:convert';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import 'package:syntax_highlight/syntax_highlight.dart';
import 'package:flutter_miuix/miuix.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:shimmer_animation/shimmer_animation.dart';
import 'package:venera/components/background.dart';
import 'package:venera/foundation/app.dart';
import 'package:venera/foundation/app_page_route.dart';
import 'package:venera/foundation/appdata.dart';
import 'package:venera/foundation/comic_source/comic_source.dart';
import 'package:venera/foundation/comic_type.dart';
import 'package:venera/foundation/consts.dart';
import 'package:venera/foundation/content_guard.dart';
import 'package:venera/foundation/favorites.dart';
import 'package:venera/foundation/history.dart';
import 'package:venera/foundation/image_provider/cached_image.dart';
import 'package:venera/foundation/image_provider/history_image_provider.dart';
import 'package:venera/foundation/image_provider/local_comic_image.dart';
import 'package:venera/foundation/local.dart';
import 'package:venera/foundation/log.dart';
import 'package:venera/foundation/res.dart';
import 'package:venera/network/cloudflare.dart';
import 'package:venera/pages/comic_details_page/comic_page.dart';
import 'package:venera/pages/favorites/favorites_page.dart';
import 'package:venera/utils/ext.dart';
import 'package:venera/utils/io.dart';
import 'package:venera/utils/tags_translation.dart';
import 'package:venera/utils/translations.dart';

part 'image.dart';
part 'appbar.dart';
part 'button.dart';
part 'consts.dart';
part 'flyout.dart';
part 'layout.dart';
part 'loading.dart';
part 'menu.dart';
part 'message.dart';
part 'navigation_bar.dart';
part 'pop_up_widget.dart';
part 'scroll.dart';
part 'select.dart';
part 'side_bar.dart';
part 'comic.dart';
part 'nsfw_cover.dart';
part 'effects.dart';
part 'gesture.dart';
part 'code.dart';

/// 全局「Miuix 画风」开关（设置 → 外观 → Settings Style）。
///
/// 所有 Miuix 化的组件（设置页、收藏页卡片、发现页双列网格等）一律用
/// 这个 getter 决定渲染分支 —— 切回 Classic 时全部恢复原版 MD3，不存在
/// 任何单独的切换入口。
bool get useMiuixStyle => appdata.settings['settingsStyle'] == 'miuix';

/// 用 Miuix 主题包住任意组件（按 App 当前明暗取色）。
///
/// Miuix 组件通过 [MiuixTheme.of] 取色，没有祖先时回退浅色默认值，
/// 深色模式必穿帮；凡是脱离设置页环境使用 Miuix 组件的地方都要包一层。
Widget withMiuixTheme(BuildContext context, Widget child) {
  return MiuixTheme(
    data: MiuixThemeData.of(Theme.of(context).brightness),
    child: child,
  );
}

/// TabRow 的半透明配色：默认 `colors.surface` 是实底，在沉浸式背景/壁纸
/// 上会形成一条黑带。必须**在 withMiuixTheme 包裹层树内**使用（需 Miuix
/// 文字色），设置页等已包裹场景可直接传给 `MiuixTabRow(colors: ...)`。
MiuixTabRowColors translucentTabRowColors(BuildContext context) {
  final cs = Theme.of(context).colorScheme;
  final dark = Theme.of(context).brightness == Brightness.dark;
  return MiuixTabRowColors(
    backgroundColor: cs.surface.withValues(alpha: dark ? 0.45 : 0.55),
    contentColor: cs.onSurfaceVariant,
    selectedBackgroundColor:
        cs.surfaceContainer.withValues(alpha: dark ? 0.72 : 0.9),
    selectedContentColor: cs.onSurface,
  );
}

/// [MiuixTabRow] 的「按最长标签自适应」胶囊宽度。
///
/// **为什么必须自己算**：库的几何是给短标签设计的（`minWidth = 76`、
/// `maxWidth = 98`）。而 `MiuixTabRow` 内部给**所有**标签用同一个宽度，
/// [_calculateTabWidth] 在 `idealWidth < minWidth` 时直接返回 `minWidth` ——
/// 也就是说标签数 ≥ 4 左右时，每个胶囊被统一压到 76dp（文字区仅 52dp）。
/// 源名和分区名动辄 4~10 个字符，"哔咔随机" 会被截成 "哔咔随…"，
/// 未翻译的英文标题更惨（"Picacg Random" → "Pic…"）。
///
/// 这里按 body1(16sp) 的**加粗**样式（选中态最宽）实测每个标签，取最大值
/// 加上左右各 12dp 内边距与 8dp 余量，并把结果**同时**传给 `minWidth` 和
/// `maxWidth`：宽度不够时库返回该宽度（每个胶囊刚好放得下最长标签），
/// 宽度富余时库的 `idealWidth` 分支会自动拉伸填满整行。
///
/// `textScaler` 跟随系统字号，用户放大字体后胶囊会一起变宽，不再截断。
double miuixTabRowWidth(BuildContext context, List<String> tabs) {
  if (tabs.isEmpty) return MiuixTabRowDefaults.tabRowMinWidth;
  final textScaler = MediaQuery.textScalerOf(context);
  // 与 _TabItem 内 MiuixText 的实际样式对齐：fontSize 取 body1，字重取加粗。
  const style = TextStyle(fontSize: 16, fontWeight: FontWeight.bold);
  var maxTextWidth = 0.0;
  for (final tab in tabs) {
    final painter = TextPainter(
      text: TextSpan(text: tab, style: style),
      maxLines: 1,
      textDirection: TextDirection.ltr,
      textScaler: textScaler,
    )..layout();
    maxTextWidth = math.max(maxTextWidth, painter.width);
  }
  final width =
      maxTextWidth + MiuixTabRowDefaults.itemHorizontalPadding * 2 + 8;
  return width.clamp(96.0, 240.0).toDouble();
}

/// 双列胶囊的单条数据：左列来源名（浅色小字），右列分区名（粗体）。
///
/// [source] 为空表示**单列**模式 —— 标签本身就是自包含的（库自带写法），
/// 此时只用 [category] 居中显示，不再硬凑一个来源名。
@immutable
class MiuixTabLabel {
  const MiuixTabLabel(this.source, this.category);

  final String source;
  final String category;

  bool get isTwoColumn => source.isNotEmpty && category.isNotEmpty;

  /// 无障碍朗读与 debug 用。
  String get plainText => isTwoColumn ? "$source $category" : category;

  @override
  bool operator ==(Object other) =>
      other is MiuixTabLabel &&
      other.source == source &&
      other.category == category;

  @override
  int get hashCode => Object.hash(source, category);
}

/// 双列版 [MiuixTabRow]：胶囊内左「来源名」右「分区名」。
///
/// **为什么不能直接用库的 [MiuixTabRow]**：它只接受 `List<String>`，而且是
/// **等宽**布局 —— 先用 `_calculateTabWidth` 算出统一宽度，再把每个标签塞进
/// 等宽的胶囊里。分区名动辄 4~6 个字，等宽要么全体被压到 `minWidth` 截断，
/// 要么被最长的那一条撑得很宽、短标签留一大片空白。
///
/// 所以这里自己实现，几何与动画**照抄**库的 `_TabRowBase` / `_TabItem`：
/// - 胶囊高 [height]、圆角 [cornerRadius]，选中态是 [MiuixSquircleBorder]
///   的 squircle 底（不是普通 RRect），未选中态是内缩 1dp 的 squircle 描边；
/// - 间距 [MiuixTabRowDefaults.tabRowItemSpacing]（9dp）、内边距
///   [MiuixTabRowDefaults.itemHorizontalPadding]（12dp）；
/// - 字号 body1（右列）/ body2（左列），选中加粗，均跟随系统字号缩放。
///
/// 与库的两处**有意**差异：
/// 1. **每颗胶囊按自身内容实测宽度**（不等宽），短标签不再留白；
/// 2. 指示器由 [progress] 连续插值驱动 —— 手指拖动 TabBarView 时底块是
///    跟手的，而不是越过中点才瞬移（库的普通 TabRow 用的是 `Duration.zero`）。
class MiuixTwoColumnTabRow extends StatefulWidget {
  const MiuixTwoColumnTabRow({
    super.key,
    required this.tabs,
    required this.progress,
    required this.selectedTabIndex,
    required this.onTabSelected,
    this.colors,
    this.height = 48,
    this.cornerRadius = 14,
    this.itemSpacing = MiuixTabRowDefaults.tabRowItemSpacing,
    this.minWidth = 96,
    this.maxWidth = 232,
    this.scrollController,
  });

  final List<MiuixTabLabel> tabs;

  /// `0 .. tabs.length - 1` 的连续进度，一般传 `TabController.animation!.value`。
  final double progress;

  /// 已落定的下标，用于自动居中滚动与选中态判定。
  final int selectedTabIndex;

  final ValueChanged<int> onTabSelected;
  final MiuixTabRowColors? colors;
  final double height;
  final double cornerRadius;
  final double itemSpacing;

  /// 单列模式下的最小/最大宽度（双列会在实测值上再夹一遍）。
  final double minWidth;
  final double maxWidth;
  final ScrollController? scrollController;

  @override
  State<MiuixTwoColumnTabRow> createState() => _MiuixTwoColumnTabRowState();
}

class _MiuixTwoColumnTabRowState extends State<MiuixTwoColumnTabRow> {
  late ScrollController _controller;
  int _lastSettled = -1;

  /// 双列中间的空隙。
  static const double _columnGap = 6;

  @override
  void initState() {
    super.initState();
    _controller = widget.scrollController ?? ScrollController();
  }

  @override
  void didUpdateWidget(MiuixTwoColumnTabRow oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.scrollController != widget.scrollController) {
      if (oldWidget.scrollController == null) _controller.dispose();
      _controller = widget.scrollController ?? ScrollController();
      _lastSettled = -1;
    }
  }

  @override
  void dispose() {
    if (widget.scrollController == null) _controller.dispose();
    super.dispose();
  }

  /// 实测每颗胶囊的内容宽度（不夹上下限），随后由调用方 clamp。
  List<double> _measure(MiuixThemeData theme, TextScaler scaler) {
    final base = theme.textStyles.main;
    final leftStyle = base.copyWith(fontSize: theme.textStyles.body2.fontSize);
    final rightStyle = base.copyWith(
      fontSize: theme.textStyles.body1.fontSize,
      fontWeight: FontWeight.bold,
    );
    double widthOf(String text, TextStyle style) {
      if (text.isEmpty) return 0;
      final painter = TextPainter(
        text: TextSpan(text: text, style: style),
        maxLines: 1,
        textDirection: TextDirection.ltr,
        textScaler: scaler,
      )..layout();
      return painter.width;
    }

    return [
      for (final tab in widget.tabs)
        widthOf(tab.source, leftStyle) +
            (tab.isTwoColumn ? _columnGap : 0) +
            widthOf(tab.category, rightStyle) +
            MiuixTabRowDefaults.itemHorizontalPadding * 2 +
            // 字重/字距的小误差留一点余量，避免刚好差 1px 被 ellipsis。
            4,
    ];
  }

  void _centerSelected(double available, List<double> widths) {
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted || !_controller.hasClients || widget.tabs.isEmpty) return;
      final index = widget.selectedTabIndex.clamp(0, widget.tabs.length - 1);
      var offset = 0.0;
      for (var i = 0; i < index; i++) {
        offset += widths[i] + widget.itemSpacing;
      }
      final target = (offset + widths[index] / 2 - available / 2).clamp(
        0.0,
        _controller.position.maxScrollExtent,
      );
      final animate = _lastSettled >= 0 && _lastSettled != index;
      _lastSettled = index;
      if (animate) {
        await _controller.animateTo(
          target,
          duration: const Duration(milliseconds: 275),
          curve: MiuixMotion.standardDecelerate,
        );
      } else {
        _controller.jumpTo(target);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final colors = widget.colors ?? MiuixTabRowDefaults.defaultColors(context);
    final theme = MiuixTheme.of(context);
    final outline = MiuixTheme.of(context).colors.outline;
    final raw = _measure(theme, MediaQuery.textScalerOf(context));
    final widths = [
      for (final w in raw) w.clamp(widget.minWidth, widget.maxWidth).toDouble(),
    ];
    final offsets = <double>[];
    var acc = 0.0;
    for (var i = 0; i < widths.length; i++) {
      offsets.add(acc);
      acc += widths[i] + widget.itemSpacing;
    }
    final totalWidth = widths.isEmpty ? 0.0 : acc - widget.itemSpacing;

    return LayoutBuilder(
      builder: (context, constraints) {
        final available =
            constraints.hasBoundedWidth ? constraints.maxWidth : 0.0;
        _centerSelected(available, widths);

        final selected = widget.tabs.isEmpty
            ? 0
            : widget.selectedTabIndex.clamp(0, widget.tabs.length - 1);
        // 指示器位置按连续进度插值：拖动跟手，落位平滑。
        final lastIndex = math.max(0, widths.length - 1);
        final p = widget.progress.clamp(0.0, lastIndex.toDouble());
        final i0 = p.floor().clamp(0, lastIndex).toInt();
        final i1 = math.min(i0 + 1, lastIndex);
        final t = (widths.length <= 1) ? 0.0 : (p - i0);

        Widget indicator = const SizedBox.shrink();
        if (widths.isNotEmpty) {
          final left = ui.lerpDouble(offsets[i0], offsets[i1], t) ?? offsets[i0];
          final width =
              ui.lerpDouble(widths[i0], widths[i1], t) ?? widths[i0];
          indicator = Positioned(
            left: left,
            top: 0,
            bottom: 0,
            width: width,
            child: DecoratedBox(
              decoration: ShapeDecoration(
                color: colors.background(true),
                shape: MiuixSquircleBorder(cornerRadius: widget.cornerRadius),
              ),
            ),
          );
        }

        Widget scrollContent = SizedBox(
          width: totalWidth,
          height: widget.height,
          child: Stack(
            children: [
              indicator,
              Row(
                children: [
                  for (var i = 0; i < widget.tabs.length; i++) ...[
                    if (i > 0) SizedBox(width: widget.itemSpacing),
                    _TwoColumnTabItem(
                      label: widget.tabs[i],
                      width: widths[i],
                      selected: selected == i,
                      cornerRadius: widget.cornerRadius,
                      contentColor: colors.content(selected == i),
                      secondaryColor: colors.contentColor,
                      outlineColor: outline,
                      body1: theme.textStyles.body1.fontSize,
                      body2: theme.textStyles.body2.fontSize,
                      onTap: () => widget.onTabSelected(i),
                      semantics: '${widget.tabs[i].plainText}'
                          '，标签 ${i + 1} / ${widget.tabs.length}',
                    ),
                  ],
                ],
              ),
            ],
          ),
        );

        scrollContent = SingleChildScrollView(
          controller: _controller,
          scrollDirection: Axis.horizontal,
          physics: const ClampingScrollPhysics(),
          child: scrollContent,
        );

        return SizedBox(
          width: double.infinity,
          height: widget.height,
          child: ColoredBox(color: colors.background(false), child: scrollContent),
        );
      },
    );
  }
}

class _TwoColumnTabItem extends StatelessWidget {
  const _TwoColumnTabItem({
    required this.label,
    required this.width,
    required this.selected,
    required this.cornerRadius,
    required this.contentColor,
    required this.secondaryColor,
    required this.outlineColor,
    required this.body1,
    required this.body2,
    required this.onTap,
    required this.semantics,
  });

  final MiuixTabLabel label;
  final double width;
  final bool selected;
  final double cornerRadius;
  final Color contentColor;
  final Color secondaryColor;
  final Color outlineColor;
  final double? body1;
  final double? body2;
  final VoidCallback onTap;
  final String semantics;

  @override
  Widget build(BuildContext context) {
    Widget child = Container(
      width: width,
      height: double.infinity,
      padding: const EdgeInsets.symmetric(
        horizontal: MiuixTabRowDefaults.itemHorizontalPadding,
      ),
      alignment: Alignment.center,
      child: label.isTwoColumn
          ? Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Flexible(
                  child: MiuixText(
                    label.source,
                    color: secondaryColor,
                    fontSize: body2,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                const SizedBox(width: 6),
                Flexible(
                  child: MiuixText(
                    label.category,
                    color: contentColor,
                    fontSize: body1,
                    fontWeight: selected ? FontWeight.bold : FontWeight.normal,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            )
          : MiuixText(
              label.category,
              color: contentColor,
              fontSize: body1,
              fontWeight: selected ? FontWeight.bold : FontWeight.normal,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
    );
    // 未选中：内缩 1dp 的 squircle 描边（与库的 _TabItem 一致）。
    if (!selected) {
      child = CustomPaint(
        foregroundPainter: _TabInsetBorderPainter(
          color: outlineColor,
          radius: cornerRadius,
        ),
        child: child,
      );
    }
    return Semantics(
      button: true,
      selected: selected,
      inMutuallyExclusiveGroup: true,
      label: semantics,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: child,
      ),
    );
  }
}

/// 与库内 `_InsetSquircleBorderPainter` 同一套画法（内缩半个描边宽，
/// 避免描边被裁掉一半）。
class _TabInsetBorderPainter extends CustomPainter {
  const _TabInsetBorderPainter({required this.color, required this.radius});

  final Color color;
  final double radius;

  @override
  void paint(Canvas canvas, Size size) {
    const stroke = MiuixTabRowDefaults.borderWidth;
    final inset = stroke / 2;
    final path = Path();
    addSquircleRect(
      path,
      size.width - stroke,
      size.height - stroke,
      math.max(0, radius - inset),
    );
    canvas.drawPath(
      path.shift(Offset(inset, inset)),
      Paint()
        ..color = color
        ..style = PaintingStyle.stroke
        ..strokeWidth = stroke,
    );
  }

  @override
  bool shouldRepaint(_TabInsetBorderPainter oldDelegate) =>
      oldDelegate.color != color || oldDelegate.radius != radius;
}

