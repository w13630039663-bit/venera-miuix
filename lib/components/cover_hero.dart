part of 'components.dart';

/// 列表卡片 ⇄ 详情页封面转场的**共享外观层**（Container Transform 的"容器"）。
///
/// 三处必须共用同一个构建函数（铁律 #7），否则起飞首帧或落地末帧会被看到
/// 一次跳变：
/// 1. 列表侧 Hero 的 child（卡片封面）；
/// 2. 飞行中的 shuttle（见 [coverHeroFlightShuttle]）；
/// 3. 详情页 Hero 的 child（真实封面 / 加载占位封面）。
///
/// 圆角与底色由调用方给（卡片端 8 或 16、详情端 8 或 12），shuttle 只负责在
/// 两端之间插值 —— 所以这里不做任何"取当前主题算一个值"的隐含约定。
class CoverHeroChrome extends StatelessWidget {
  const CoverHeroChrome({
    super.key,
    required this.child,
    required this.borderRadius,
    required this.background,
    this.shadows = const [],
    this.width,
    this.height,
  });

  final Widget child;

  final BorderRadius borderRadius;

  final Color background;

  final List<BoxShadow> shadows;

  /// 静止态尺寸。飞行中 Hero 会传入插值后的**紧约束**，Container 上的
  /// width/height 在紧约束下会被覆盖，因此飞行中不需要特殊处理。
  final double? width;

  final double? height;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: background,
        borderRadius: borderRadius,
        boxShadow: shadows.isEmpty ? null : shadows,
      ),
      clipBehavior: Clip.antiAlias,
      child: child,
    );
  }
}
