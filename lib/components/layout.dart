part of 'components.dart';

class SliverGridViewWithFixedItemHeight extends StatelessWidget {
  const SliverGridViewWithFixedItemHeight(
      {required this.delegate,
      required this.maxCrossAxisExtent,
      required this.itemHeight,
      super.key});

  final SliverChildDelegate delegate;

  final double maxCrossAxisExtent;

  final double itemHeight;

  @override
  Widget build(BuildContext context) {
    return SliverLayoutBuilder(
      builder: (context, constraints) => SliverGrid(
        delegate: delegate,
        gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
          maxCrossAxisExtent: maxCrossAxisExtent,
          childAspectRatio: calcChildAspectRatio(constraints.crossAxisExtent),
        ),
      ),
    );
  }

  double calcChildAspectRatio(double width) {
    var crossItems = width ~/ maxCrossAxisExtent;
    if (width % maxCrossAxisExtent != 0) {
      crossItems += 1;
    }
    final itemWidth = width / crossItems;
    return itemWidth / itemHeight;
  }
}

class SliverGridDelegateWithFixedHeight extends SliverGridDelegate {
  const SliverGridDelegateWithFixedHeight({
    required this.maxCrossAxisExtent,
    required this.itemHeight,
  });

  final double maxCrossAxisExtent;

  final double itemHeight;

  @override
  SliverGridLayout getLayout(SliverConstraints constraints) {
    final width = constraints.crossAxisExtent;
    var crossItems = width ~/ maxCrossAxisExtent;
    if (width % maxCrossAxisExtent != 0) {
      crossItems += 1;
    }
    return SliverGridRegularTileLayout(
        crossAxisCount: crossItems,
        mainAxisStride: itemHeight,
        crossAxisStride: width / crossItems,
        childMainAxisExtent: itemHeight,
        childCrossAxisExtent: width / crossItems,
        reverseCrossAxis: false);
  }

  @override
  bool shouldRelayout(covariant SliverGridDelegate oldDelegate) {
    if (oldDelegate is! SliverGridDelegateWithFixedHeight) return true;
    if (oldDelegate.maxCrossAxisExtent != maxCrossAxisExtent ||
        oldDelegate.itemHeight != itemHeight) {
      return true;
    }
    return false;
  }
}

class SliverGridDelegateWithComics extends SliverGridDelegate {
  SliverGridDelegateWithComics({this.miuixTwoColumn = false});

  /// Miuix 双列卡片模式（发现页专用）：固定双列（宽屏自适应加列），
  /// 卡片 = 封面 + 底部标题/副标题。仅在 [useMiuixStyle] 时由调用方启用。
  final bool miuixTwoColumn;

  final bool useBriefMode = appdata.settings['comicDisplayMode'] == 'brief';

  final double scale = (appdata.settings['comicTileScale'] as num).toDouble();

  /// 构造时的 Miuix 开关快照（detailed 模式的卡片边距依赖它）。
  final bool useMiuix = useMiuixStyle;

  @override
  SliverGridLayout getLayout(SliverConstraints constraints) {
    if (miuixTwoColumn) {
      return getMiuixTwoColumnLayout(constraints);
    }
    if (useBriefMode) {
      return getBriefModeLayout(
        constraints,
        scale,
      );
    } else {
      return getDetailedModeLayout(
        constraints,
        scale,
      );
    }
  }

  /// Miuix 双列卡片网格：每格 = [MiuixComicTile]（外间距 6 + MiuixCard）。
  ///
  /// 卡片内部结构（ComicTile._buildMiuixGridMode）：
  ///   Column [ 封面（宽高比 0.68，左右内缩 6） , 信息区 66dp（标题 2 行 +
  ///   副标题 1 行） ] —— 封面高度由列宽决定，因此格子高度必须在这里算出。
  SliverGridLayout getMiuixTwoColumnLayout(SliverConstraints constraints) {
    const spacing = 12.0;
    final width = constraints.crossAxisExtent;
    // 手机双列；宽屏（平板/桌面窗口）按 220dp 一列自适应加列。
    final crossItems = math.max(2, width ~/ 220);
    final colW = (width - spacing * (crossItems - 1)) / crossItems;
    const coverAspect = 0.68;
    final imgH = (colW - 12) / coverAspect;
    const infoH = 66.0;
    final cellH = imgH + infoH;
    return SliverGridRegularTileLayout(
      crossAxisCount: crossItems,
      mainAxisStride: cellH + spacing,
      crossAxisStride: colW + spacing,
      childMainAxisExtent: cellH,
      childCrossAxisExtent: colW,
      reverseCrossAxis: false,
    );
  }

  SliverGridLayout getDetailedModeLayout(
      SliverConstraints constraints, double scale) {
    // Miuix 模式下横向卡片外围有 8dp 边距（MiuixCard 卡片化），格子加高。
    final itemHeight = 152 * scale + (useMiuixStyle ? 16 : 0);
    const minCrossAxisExtent = 360;
    final width = constraints.crossAxisExtent;
    var crossItems = width ~/ minCrossAxisExtent;
    crossItems = math.max(1, crossItems);
    return SliverGridRegularTileLayout(
        crossAxisCount: crossItems,
        mainAxisStride: itemHeight,
        crossAxisStride: width / crossItems,
        childMainAxisExtent: itemHeight,
        childCrossAxisExtent: width / crossItems,
        reverseCrossAxis: false);
  }

  SliverGridLayout getBriefModeLayout(
      SliverConstraints constraints, double scale) {
    // 「两列」模式：固定双列（用户要求——按宽度自适应在宽屏/高分辨率
    // 手机上会算出三列，太多）。scale 仍生效：调大时列宽不变、格子变高
    // （aspect 除以 scale）。
    const crossAxisCount = 2;
    const childAspectRatio = 0.64;
    const crossAxisSpacing = 0.0;
    final double usableCrossAxisExtent = math.max(
      0.0,
      constraints.crossAxisExtent - crossAxisSpacing * (crossAxisCount - 1),
    );
    final double childCrossAxisExtent = usableCrossAxisExtent / crossAxisCount;
    final double childMainAxisExtent =
        childCrossAxisExtent / (childAspectRatio / scale);
    return SliverGridRegularTileLayout(
      crossAxisCount: crossAxisCount,
      mainAxisStride: childMainAxisExtent,
      crossAxisStride: childCrossAxisExtent + crossAxisSpacing,
      childMainAxisExtent: childMainAxisExtent,
      childCrossAxisExtent: childCrossAxisExtent,
      reverseCrossAxis: axisDirectionIsReversed(constraints.crossAxisDirection),
    );
  }

  @override
  bool shouldRelayout(covariant SliverGridDelegate oldDelegate) {
    if (oldDelegate is! SliverGridDelegateWithComics) return true;
    if (oldDelegate.scale != scale ||
        oldDelegate.useBriefMode != useBriefMode ||
        oldDelegate.miuixTwoColumn != miuixTwoColumn ||
        oldDelegate.useMiuix != useMiuix) {
      return true;
    }
    return false;
  }
}

class SliverLazyToBoxAdapter extends StatelessWidget {
  /// Creates a sliver that contains a single box widget which can be lazy loaded.
  const SliverLazyToBoxAdapter({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return SliverList.list(children: [
      SizedBox(),
      child,
    ]);
  }
}

class SliverAnimatedVisibility extends StatelessWidget {
  const SliverAnimatedVisibility({
    super.key,
    required this.visible,
    required this.child,
  });

  final bool visible;

  final Widget child;

  @override
  Widget build(BuildContext context) {
    var child = visible ? this.child : const SizedBox.shrink();

    return SliverToBoxAdapter(
      child: AnimatedSize(
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeInOut,
        alignment: Alignment.topCenter,
        child: child,
      ),
    );
  }
}
