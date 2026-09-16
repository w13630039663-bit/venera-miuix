import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:venera/components/components.dart';

// ===== 光学·径向转场调参区（全部对齐 build/optical-transition-demo.html 的
// radial 模式；改动手感只动这里）=====

/// 光学强度（demo 强度滑杆默认 1）。
const double kPreviewOpticalStrength = 1.0;

/// 散焦峰值半径（物理像素，demo 默认 24）。低端机中段卡顿先降这个。
const double kPreviewBlurMax = 24.0;

/// 光程差归一化参考值（demo radial = 0.32）。
const double kPreviewOffRef = 0.32;

/// 卡片端的圆角半径（逻辑像素，与预览网格的框一致）。
/// 飞行中圆角 = 8 × (1 − 插值进度)：卡片端 8px 契合缩略图框，全屏端 0
/// 契合阅读器直角满屏（demo 的 rad = mix(rA, rB, pe) 同款思路）。
const double kPreviewCardRadius = 8.0;

/// 打开/收尾的基准时长（demo DUR = 620ms）。
const Duration kPreviewFlightDuration = Duration(milliseconds: 620);

/// 飞行进度（0 = 卡片态, 1 = 全屏态）。shuttle 每帧写入，详情页预览网格
/// 监听它做背景退场（糊 + 暗 + 淡，见 [PreviewFlightBackdrop]）。
final ValueNotifier<double> previewFlightProgress = ValueNotifier(0);

/// demo 的几何插值曲线：pe(p) = p²(3−2p)，两端斜率为 0。
double smoothstep(double t) => t * t * (3 - 2 * t);

ui.FragmentProgram? _opticalProgram;
bool _opticalProgramLoading = false;

Future<void> _loadOpticalProgram() async {
  if (_opticalProgram != null || _opticalProgramLoading) {
    return;
  }
  _opticalProgramLoading = true;
  try {
    _opticalProgram = await ui.FragmentProgram.fromAsset(
      'shaders/preview_optical.frag',
    );
  } catch (_) {
    // 加载失败（资产缺失等）→ 永久回退普通飞行，不再重试。
  }
}

/// Shared tag builder for the "preview card ⇄ reader" shared-element (Hero)
/// transition.
///
/// The detail page's preview cards and the reader gallery must produce the
/// *exact same* tag, otherwise the Hero silently refuses to fly — there is no
/// error, the animation just doesn't happen. Keeping the format in one place
/// stops the two sides from drifting apart.
///
/// [sourceKey] + [comicId] scope the tag to one comic (ids are not unique
/// across sources). [chapter] is 1-based — the preview grid always maps to
/// chapter 1, because it taps `read(null, page)` and the reader defaults to
/// chapter 1 when no chapter is given. [page] is the 1-based image/page number
/// inside that chapter: in single-image gallery mode
/// `reader.page == initialPage == image number == gallery index`.
String previewHeroTag(
  String sourceKey,
  String comicId,
  int chapter,
  int page,
) =>
    'preview-$sourceKey-$comicId-$chapter-$page';

/// Shared flight shuttle for both sides of the preview-card Hero.
///
/// Renders the **source** hero's child for the whole flight: the default
/// shuttle renders the destination's child, which is wrong on push here (the
/// reader builds its gallery only after `loadComicPages` resolves — the
/// destination would be a spinner). Installed on both heroes so both flight
/// directions behave the same.
///
/// 光学层：shuttle 收到的 [animation] 就是转场进度 p（push 0→1；pop 的
/// parent 是被弹路由的 animation，天然 1→0 —— 与 demo 的 p 语义一致），
/// 预测返回手势期间 = 手指位置，径向效果跟手涨落。
Widget previewHeroFlightShuttle(
  BuildContext flightContext,
  Animation<double> animation,
  HeroFlightDirection flightDirection,
  BuildContext fromHeroContext,
  BuildContext toHeroContext,
) =>
    _OpticalShuttle(
      animation: animation,
      flightDirection: flightDirection,
      child: (fromHeroContext.widget as Hero).child,
    );

class _OpticalShuttle extends StatefulWidget {
  const _OpticalShuttle({
    required this.animation,
    required this.flightDirection,
    required this.child,
  });

  final Animation<double> animation;

  final HeroFlightDirection flightDirection;

  final Widget child;

  @override
  State<_OpticalShuttle> createState() => _OpticalShuttleState();
}

class _OpticalShuttleState extends State<_OpticalShuttle> {
  ui.FragmentShader? _shader;

  @override
  void initState() {
    super.initState();
    _loadOpticalProgram();
    widget.animation.addListener(_onProgress);
    _publishProgress();
  }

  @override
  void didUpdateWidget(covariant _OpticalShuttle oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.animation != widget.animation) {
      oldWidget.animation.removeListener(_onProgress);
      widget.animation.addListener(_onProgress);
    }
  }

  @override
  void dispose() {
    widget.animation.removeListener(_onProgress);
    // flight 结束（或被打断改道）→ 背景退场复位。
    previewFlightProgress.value = 0;
    _shader?.dispose();
    super.dispose();
  }

  void _onProgress() {
    _publishProgress();
    setState(() {});
  }

  void _publishProgress() {
    previewFlightProgress.value = _progress().$1;
  }

  /// (p, pe)：p 是光效/背景退场用的「驱动进度」，pe 是矩形/圆角用的「插值
  /// 进度」—— 与 _OpticalRectTween 完全同式，两层永远同相位。
  /// - push：p = easeOutQuart(时间)（快出手），pe = smoothstep(p)；
  /// - pop：p = route.animation（手势=手指 / 松手控制器=easeOutQuart），
  ///   pe = smoothstep(1 − p)。
  (double, double) _progress() {
    final double raw = widget.animation.value.clamp(0.0, 1.0);
    switch (widget.flightDirection) {
      case HeroFlightDirection.push:
        final double p = Curves.easeOutQuart.transform(raw);
        return (p, smoothstep(p));
      case HeroFlightDirection.pop:
        return (raw, smoothstep(1.0 - raw));
    }
  }

  @override
  Widget build(BuildContext context) {
    final (double p, double pe) = _progress();
    final double amt = math.sin(p * math.pi);
    final program = _opticalProgram;
    // 圆角随插值进度收放：卡片端 8px 契合缩略图框，全屏端 0 契合阅读器
    // 直角满屏。这是纯几何（不依赖 shader），Skia 回退路径也生效。
    final double radius = kPreviewCardRadius * (1.0 - pe);
    Widget content = widget.child;
    if (radius > 0.05) {
      content = ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: content,
      );
    }
    // 两端（amt≈0）或 shader 不可用（Skia 后端 / 资产未就绪）：只做圆角几何
    // —— 转场首帧/末帧与静止态零差异（铁律 #7）。
    if (amt < 0.02 ||
        program == null ||
        !ui.ImageFilter.isShaderFilterSupported) {
      return content;
    }
    // 复用 shader 实例并直接更新 uniform（避免每帧创建与销毁原生对象的 GC 抖动）
    final shader = _shader ??= program.fragmentShader();
    shader
      ..setFloat(2, p)
      ..setFloat(3, kPreviewOpticalStrength)
      ..setFloat(4, kPreviewBlurMax)
      ..setFloat(5, kPreviewOffRef)
      // 边缘高光的 SDF 半径与实际裁剪一致（物理像素）。
      ..setFloat(6, radius * MediaQuery.devicePixelRatioOf(context));
    // 引擎把被过滤内容（child 光栅）自动绑到第一个 sampler —— demo 里
    // 的 cover 映射/矩形裁剪整段不需要，Flutter 布局已经做了。
    return ImageFiltered(
      imageFilter: ui.ImageFilter.shader(shader),
      child: content,
    );
  }
}

/// demo 的矩形插值：进度先反解出「原始进度 v」，再套 smoothstep。三条驱动
/// 路径各自精确对齐 demo：
/// - 打开（v 线性于时间）：smoothstep(easeOutQuart(v)) = demo 打开曲线；
/// - 手势拖拽（v = 手指）：smoothstep(v) 1:1 跟手（同 demo 拖拽）；
/// - 松手收尾（控制器曲线 = easeOutQuart，app_page_route.dart 驱动）：
///   smoothstep(v(time)) = demo closePanel。
///
/// 为什么要反解：框架给 tween 的 t 已被 Hero 默认曲线 fastOutSlowIn 加工过
/// （push: t = fastOutSlowIn(v)；pop: t = fastOutSlowIn(1−route.animation)，
/// 见 heroes.dart `_HeroFlightManifest.animation`）。fastOutSlowIn 是单调
/// 三次贝塞尔，二分 24 次即可精确反解。
class _OpticalRectTween extends RectTween {
  _OpticalRectTween({required super.begin, required super.end});

  final MaterialRectArcTween _arc = MaterialRectArcTween();

  /// 方向判定：end 比 begin 大 = 打开（push）。pop 的 begin 是阅读器矩形、
  /// end 是卡片（begin/end 始终 from→to，不互换）。
  bool get _isPush {
    final begin = this.begin;
    final end = this.end;
    if (begin == null || end == null) {
      return true;
    }
    return end.width * end.height > begin.width * begin.height;
  }

  static double _invertFastOutSlowIn(double t) {
    const curve = Curves.fastOutSlowIn;
    double lo = 0.0;
    double hi = 1.0;
    for (var i = 0; i < 24; i++) {
      final double mid = (lo + hi) / 2;
      if (curve.transform(mid) < t) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return (lo + hi) / 2;
  }

  @override
  Rect? transform(double t) {
    final begin = this.begin;
    final end = this.end;
    if (begin == null || end == null) {
      return null;
    }
    final double v = _invertFastOutSlowIn(t.clamp(0.0, 1.0));
    final double eased = _isPush
        ? smoothstep(Curves.easeOutQuart.transform(v))
        : smoothstep(v);
    // 弧线路径保留（Material 默认），只扭曲时间轴。
    _arc
      ..begin = begin
      ..end = end;
    return _arc.transform(eased);
  }
}

/// [Hero.createRectTween] / `PhotoViewHeroAttributes.createRectTween` 工厂。
RectTween previewHeroCreateRectTween(Rect? begin, Rect? end) =>
    _OpticalRectTween(begin: begin, end: end);

/// 列表卡片 ⇄ 详情页封面的飞行 shuttle（Container Transform）。
///
/// 框架默认的 shuttle 渲染的是**目的** child，所以原来飞行中看到的一直是
/// 详情页的封面框 —— 卡片自己的圆角与底色从头到尾没有出现过，"卡片形变"
/// 的感觉因此很弱。这里改成统一渲染 [CoverHeroChrome]，把圆角、底色、阴影
/// 按进度从卡片端插值到详情端；落地那一帧正好等于详情端静止态，首末帧与
/// 静止态零差异。
///
/// 两端只要有一端不是 [CoverHeroChrome]，就退回默认行为（渲染目的 child）
/// —— 详情页 ⇄ 封面查看器那条路对面是 photo_view 自己的 Hero，必须不受影响。
Widget coverHeroFlightShuttle(
  BuildContext flightContext,
  Animation<double> animation,
  HeroFlightDirection flightDirection,
  BuildContext fromHeroContext,
  BuildContext toHeroContext,
) {
  final Widget fromChild = (fromHeroContext.widget as Hero).child;
  final Widget toChild = (toHeroContext.widget as Hero).child;
  if (fromChild is! CoverHeroChrome || toChild is! CoverHeroChrome) {
    return toChild;
  }
  return _CoverMorphShuttle(animation: animation, from: fromChild, to: toChild);
}

class _CoverMorphShuttle extends StatelessWidget {
  const _CoverMorphShuttle({
    required this.animation,
    required this.from,
    required this.to,
  });

  final Animation<double> animation;

  final CoverHeroChrome from;

  final CoverHeroChrome to;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: animation,
      // child 不随进度变，交给 AnimatedBuilder 缓存，避免每帧重建图片子树。
      child: to.child,
      builder: (context, child) {
        // push：animation 0→1（卡片态 → 详情态）；pop：1→0。两个方向都能
        // 直接把 animation.value 当插值进度用，不需要判方向。
        final double t = animation.value.clamp(0.0, 1.0).toDouble();
        return CoverHeroChrome(
          borderRadius:
              BorderRadius.lerp(from.borderRadius, to.borderRadius, t)!,
          background: Color.lerp(from.background, to.background, t)!,
          shadows: BoxShadow.lerpList(from.shadows, to.shadows, t) ?? const [],
          // 内容取**目的端**：两端是同一个 ImageProvider，但详情端的解码尺寸
          // 更大，用目的端可避免落地瞬间的重新解码闪动。
          child: child!,
        );
      },
    );
  }
}

/// 详情页预览卡的「背景退场」：飞行期间平滑暗化（e = smoothstep(p)）。
/// 叠层采用纯色半透明遮罩，零 GPU 离屏滤波开销，同时保留视觉层次退场质感。
class PreviewFlightBackdrop extends StatelessWidget {
  const PreviewFlightBackdrop({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        child,
        ValueListenableBuilder<double>(
          valueListenable: previewFlightProgress,
          builder: (context, double p, _) {
            final double e = smoothstep(p);
            if (e < 0.01) {
              return const SizedBox.shrink();
            }
            return Positioned.fill(
              child: IgnorePointer(
                child: ColoredBox(
                  color: Colors.black.withValues(alpha: 0.45 * e),
                ),
              ),
            );
          },
        ),
      ],
    );
  }
}

/// 预览卡封面里的**裸图**（不含卡片框）。**卡片 Hero child / Hero 飞行
/// shuttle / 阅读器占位**三处必须长得一模一样：飞行中的内容是源侧 Hero 的
/// child（这个函数），而落地那一刻 overlay 里的 shuttle 被摘掉、换成目的侧
/// Hero 的 child —— 如果目的侧不是同一个东西，末帧就会看到一次变化（阅读器
/// 要等图集才构建，之前目的侧是转圈，所以「打开时闪一下再转圈」）。
///
/// 卡片的 8px 圆角 + 描边**不能**进 Hero child：Hero 飞行是按插值矩形逐帧
/// 重新布局（heroes.dart `_buildOverlay` 的 Positioned），框会从卡片尺寸一路
/// 「长」到全屏 —— 这就是「放大动画把卡片小框一起放大」。框属于卡片静态
/// 外观，画在网格单元上（thumbnails.dart），不随图片飞。
///
/// 阅读器首帧还没有图集，拿不到自己的图，所以由详情页把这个 widget 一并交给
/// 它（`_ComicPageActions.read` 的第 4 个可选参数 → `Reader.previewPlaceholder`），
/// 作为图集就绪之前的占位。三处共用一个构建函数，差别只允许出现在解码尺寸上
/// （[cacheWidth]：卡片小尺寸，占位不传 = 原图，顺带和阅读器共用同一份已解码
/// 图像缓存）。
Widget previewFlightImage({
  required ImageProvider image,
  ImagePart? part,
  int? cacheWidth,
}) =>
    AnimatedImage(
      image: image,
      fit: BoxFit.contain,
      width: double.infinity,
      height: double.infinity,
      part: part,
      cacheWidth: cacheWidth,
    );
