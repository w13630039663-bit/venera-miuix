part of 'components.dart';

/// 「这一屏要不要遮」的场景开关。
///
/// 遮蔽判定是**源级 + 条目级**的，但场景比两者都准：同一个作品在首页要遮、
/// 在用户自己点进去的源里不用遮 —— 用户已经知道自己在看什么了，再糊一遍
/// 纯属骚扰。这条规则来自设计里那张场景表：
///
/// | 场景 | 遮 |
/// |---|---|
/// | 首页 / 聚合搜索 / 发现 / 分类橱窗 / 追更 / 历史 | ✅ |
/// | 用户主动进入某个源之后（源的分类列表、单源搜索） | ❌ |
/// | 本地/已下载、详情页内的列表 | ❌ |
///
/// 实现上是 **[InheritedWidget] 而不是参数传递**：卡片要经过
/// `SliverGridComics → ComicTile → buildImage → NsfwCover` 四层，
/// 一路加参数既污染公共 API（`ComicList` 有 9 个参数了），又必然漏传 ——
/// 而漏传的后果是静默不遮，最难发现。
///
/// **默认值 = 遮**：没有包这一层的页面按"用户无预期"处理，宁可多糊一张。
class NsfwMaskScope extends InheritedWidget {
  const NsfwMaskScope({required this.mask, required super.child, super.key});

  /// 该子树是否参与遮蔽。
  final bool mask;

  /// 取当前场景。默认 `true`（见类文档）。
  static bool of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<NsfwMaskScope>()?.mask ?? true;

  @override
  bool updateShouldNotify(NsfwMaskScope oldWidget) => oldWidget.mask != mask;
}

/// 封面遮蔽壳：把 [child]（封面图）糊掉，并在中间提示。
///
/// ## 为什么用 `ImageFiltered` 而不是 `BackdropFilter`
///
/// 要遮的是"那一张图"，不是"这块区域显示出来的所有东西"。`BackdropFilter`
/// 采样的是背后**整个已绘制图层**，在卡片密集的网格里会把相邻卡片、背景色一起
/// 采样进来，既慢又不对。`ImageFiltered` 只作用于自己的子树，语义准确、无采样开销。
///
/// ## 只遮封面，不遮文字
///
/// 标题和标签照常可读。社死风险几乎全部来自封面图（大、直观、一眼扫到），
/// 而文字不社死、且标签本身就是判断依据。这样误伤范围从"这条记录整个不能用"
/// 缩到"封面看不到" —— 把"某些源没打标"从阻塞问题降级成体验瑕疵。
class NsfwCover extends StatefulWidget {
  const NsfwCover({required this.comic, required this.child, super.key});

  final Comic comic;

  final Widget child;

  @override
  State<NsfwCover> createState() => _NsfwCoverState();
}

class _NsfwCoverState extends State<NsfwCover> {
  @override
  Widget build(BuildContext context) {
    // 场景开关优先于一切判定：整屏不需要遮时连判定都不做。
    if (!NsfwMaskScope.of(context)) {
      return widget.child;
    }
    final verdict = ContentGuard.verdict(widget.comic);
    if (!verdict.shouldMask) {
      return widget.child;
    }

    final revealable = ContentGuard.strength == NsfwMaskStrength.blurReveal;

    Widget content = Stack(
      fit: StackFit.passthrough,
      children: [
        // σ=18 的模糊在网格滚动时会被逐帧重算。RepaintBoundary 把它的光栅
        // 缓存下来：模糊只作用于自身子树（封面图），缓存不会因背景变化失效，
        // 滚动时直接平移复用的缓存位图。
        RepaintBoundary(
          child: ImageFiltered(
            imageFilter: ui.ImageFilter.blur(sigmaX: 18, sigmaY: 18),
            child: widget.child,
          ),
        ),
        Positioned.fill(
          child: ColoredBox(
            color: Colors.black.withValues(alpha: 0.18),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(
                  Icons.visibility_off_outlined,
                  size: 22,
                  color: Colors.white70,
                ),
                const SizedBox(height: 4),
                Text(
                  revealable ? "R-18 · Tap to show".tl : "R-18".tl,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 11,
                    color: Colors.white70,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );

    if (!revealable) {
      return content;
    }

    return GestureDetector(
      // 用 GestureDetector 而不是 InkWell：卡片外层通常已有自己的手势与 Hero，
      // 加一层 Material ink 会盖住封面圆角并产生多余的涟漪。
      behavior: HitTestBehavior.opaque,
      onTap: () {
        // 解锁是持久的（写进 unlockedComics），同一个作品之后在任何列表
        // 都不再被遮 —— 一次点击 = 一次标注，判定精度随使用自增长。
        ContentGuard.unlock(widget.comic);
        setState(() {});
      },
      child: content,
    );
  }
}
