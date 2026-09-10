part of 'components.dart';

class NetworkError extends StatelessWidget {
  const NetworkError({
    super.key,
    required this.message,
    this.retry,
    this.withAppbar = true,
    this.buttonText,
    this.action,
  });

  final String message;

  final void Function()? retry;

  final bool withAppbar;

  final String? buttonText;

  final Widget? action;

  @override
  Widget build(BuildContext context) {
    var cfe = CloudflareException.fromString(message);
    Widget body = Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Center(
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  Icons.error_outline,
                  size: 28,
                  color: context.colorScheme.error,
                ),
                const SizedBox(width: 8),
                Text(
                  "Error".tl,
                  style: ts.withColor(context.colorScheme.error).s16,
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          Text(
            cfe == null ? message : "Cloudflare verification required".tl,
            textAlign: TextAlign.center,
            maxLines: 3,
          ),
          TextButton(
            onPressed: () {
              saveFile(
                data: utf8.encode(Log().toString()),
                filename: 'log.txt',
              );
            },
            child: Text("Export logs".tl),
          ),
          const SizedBox(height: 8),
          if (retry != null)
            if (cfe != null)
              FilledButton(
                onPressed: () => passCloudflare(
                  CloudflareException.fromString(message)!,
                  retry!,
                ),
                child: Text('Verify'.tl),
              )
            else
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  if (action != null)
                    action!.paddingRight(8),
                  FilledButton(
                    onPressed: retry,
                    child: Text(buttonText ?? 'Retry'.tl),
                  ),
                ],
              ),
        ],
      ),
    );
    if (withAppbar) {
      body = Column(
        children: [
          const Appbar(title: Text("")),
          Expanded(child: body),
        ],
      );
    }
    return Material(child: body);
  }
}

class ListLoadingIndicator extends StatelessWidget {
  const ListLoadingIndicator({super.key});

  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      width: double.infinity,
      height: 80,
      child: Center(child: FiveDotLoadingAnimation()),
    );
  }
}

class SliverListLoadingIndicator extends StatelessWidget {
  const SliverListLoadingIndicator({super.key});

  @override
  Widget build(BuildContext context) {
    // SliverToBoxAdapter can not been lazy loaded.
    // Use SliverList to make sure the animation can be lazy loaded.
    return SliverList.list(
      children: const [SizedBox(), ListLoadingIndicator()],
    );
  }
}

/// 漫画卡片网格骨架屏：**直接复用 [SliverGridDelegateWithComics]**，
/// 保证占位几何与真实卡片（detailed 横排 / brief 海报 / miuix 双列卡）
/// 完全一致；每个占位元素套 [Shimmer] 做流光动画。
/// 用作 ComicList 首屏加载的占位。
class ComicGridSkeleton extends StatelessWidget {
  const ComicGridSkeleton({this.itemCount = 10, super.key});

  final int itemCount;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return GridView.builder(
      padding: const EdgeInsets.symmetric(vertical: 8),
      gridDelegate: SliverGridDelegateWithComics(),
      itemCount: itemCount,
      itemBuilder: (context, i) => Shimmer(
        color: dark ? Colors.white : Colors.black,
        child: _buildPlaceholder(context, dark),
      ),
    );
  }

  Widget _line(Color color, double? width, {double height = 12}) {
    return Container(
      width: width,
      height: height,
      margin: const EdgeInsets.only(bottom: 7),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(6),
      ),
    );
  }

  Widget _buildPlaceholder(BuildContext context, bool dark) {
    final color = dark
        ? Colors.white.withValues(alpha: 0.10)
        : Colors.black.withValues(alpha: 0.08);
    final delegate = SliverGridDelegateWithComics();
    if (useMiuixStyle && delegate.miuixTwoColumn) {
      // 双列卡：封面占上、两行文字占下。
      return Padding(
        padding: const EdgeInsets.all(6),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Container(
                width: double.infinity,
                decoration: BoxDecoration(
                  color: color,
                  borderRadius: BorderRadius.circular(16),
                ),
              ),
            ),
            const SizedBox(height: 8),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _line(color, null, height: 13),
                  _line(color, 96, height: 11),
                ],
              ),
            ),
          ],
        ),
      );
    }
    if (delegate.useBriefMode) {
      // 海报模式：整格封面。
      return Padding(
        padding: const EdgeInsets.all(6),
        child: Container(
          decoration: BoxDecoration(
            color: color,
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      );
    }
    // detailed 横排：左侧封面 + 右侧多行文字。
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 24, 8),
      child: Row(
        children: [
          Container(
            width: 104,
            height: double.infinity,
            decoration: BoxDecoration(
              color: color,
              borderRadius: BorderRadius.circular(8),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 6),
                _line(color, null, height: 15),
                _line(color, 140),
                _line(color, 100),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

abstract class LoadingState<T extends StatefulWidget, S extends Object>
    extends State<T> {
  bool isLoading = false;

  S? data;

  String? error;

  Future<Res<S>> loadData();

  Future<Res<S>> loadDataWithRetry() async {
    int retry = 0;
    while (true) {
      var res = await loadData();
      if (res.success) {
        return res;
      } else {
        if (!mounted) return res;
        if (retry >= 3) {
          return res;
        }
        retry++;
        await Future.delayed(const Duration(milliseconds: 200));
      }
    }
  }

  FutureOr<void> onDataLoaded() {}

  Widget buildContent(BuildContext context, S data);

  Widget? buildFrame(BuildContext context, Widget child) => null;

  Widget buildLoading() {
    return Center(
      child: const CircularProgressIndicator(
        strokeWidth: 2,
      ).fixWidth(32).fixHeight(32),
    );
  }

  void retry() {
    setState(() {
      isLoading = true;
      error = null;
    });
    loadDataWithRetry().then((value) async {
      if (value.success) {
        data = value.data;
        await onDataLoaded();
        setState(() {
          isLoading = false;
        });
      } else {
        setState(() {
          isLoading = false;
          error = value.errorMessage!;
        });
      }
    });
  }

  Widget buildError() {
    return NetworkError(message: error!, retry: retry);
  }

  @override
  @mustCallSuper
  void initState() {
    isLoading = true;
    Future.microtask(() {
      loadDataWithRetry().then((value) async {
        if (!mounted) return;
        if (value.success) {
          data = value.data;
          await onDataLoaded();
          setState(() {
            isLoading = false;
          });
        } else {
          setState(() {
            isLoading = false;
            error = value.errorMessage!;
          });
        }
      });
    });
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    Widget child;

    if (isLoading) {
      child = buildLoading();
    } else if (error != null) {
      child = buildError();
    } else {
      child = buildContent(context, data!);
    }

    return buildFrame(context, child) ?? child;
  }
}

abstract class MultiPageLoadingState<T extends StatefulWidget, S extends Object>
    extends State<T> {
  bool _isFirstLoading = true;

  bool _isLoading = false;

  List<S>? data;

  String? _error;

  int _page = 1;

  int? _maxPage;

  Future<Res<List<S>>> loadData(int page);

  Widget? buildFrame(BuildContext context, Widget child) => null;

  Widget buildContent(BuildContext context, List<S> data);

  bool get isLoading => _isLoading || _isFirstLoading;

  bool get isFirstLoading => _isFirstLoading;

  bool get haveNextPage => _maxPage == null || _page <= _maxPage!;

  void nextPage() {
    if (_maxPage != null && _page > _maxPage!) return;
    if (_isLoading) return;
    _isLoading = true;
    loadData(_page).then((value) {
      _isLoading = false;
      if (mounted) {
        if (value.success) {
          _page++;
          if (value.subData is int) {
            _maxPage = value.subData as int;
          }
          setState(() {
            data!.addAll(value.data);
          });
        } else {
          var message = value.errorMessage ?? "Network Error";
          if (message.length > 20) {
            message = "${message.substring(0, 20)}...";
          }
          context.showMessage(message: message);
        }
      }
    });
  }

  void reset() {
    setState(() {
      _isFirstLoading = true;
      _isLoading = false;
      data = null;
      _error = null;
      _page = 1;
    });
    firstLoad();
  }

  void firstLoad() {
    Future.microtask(() {
      loadData(_page).then((value) {
        if (!mounted) return;
        if (value.success) {
          _page++;
          if (value.subData is int) {
            _maxPage = value.subData as int;
          }
          setState(() {
            _isFirstLoading = false;
            data = value.data;
          });
        } else {
          setState(() {
            _isFirstLoading = false;
            _error = value.errorMessage!;
          });
        }
      });
    });
  }

  @override
  void initState() {
    firstLoad();
    super.initState();
  }

  Widget buildLoading(BuildContext context) {
    return Center(
      child: const CircularProgressIndicator().fixWidth(32).fixHeight(32),
    );
  }

  Widget buildError(BuildContext context, String error) {
    return NetworkError(withAppbar: false, message: error, retry: reset);
  }

  @override
  Widget build(BuildContext context) {
    Widget child;

    if (_isFirstLoading) {
      child = buildLoading(context);
    } else if (_error != null) {
      child = buildError(context, _error!);
    } else {
      child = NotificationListener<ScrollNotification>(
        onNotification: (notification) {
          if (notification.metrics.pixels ==
              notification.metrics.maxScrollExtent) {
            nextPage();
          }
          return false;
        },
        child: buildContent(context, data!),
      );
    }

    return buildFrame(context, child) ?? child;
  }
}

class FiveDotLoadingAnimation extends StatefulWidget {
  const FiveDotLoadingAnimation({super.key});

  @override
  State<FiveDotLoadingAnimation> createState() =>
      _FiveDotLoadingAnimationState();
}

class _FiveDotLoadingAnimationState extends State<FiveDotLoadingAnimation>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
      upperBound: 6,
    )..repeat(min: 0, max: 5.2, period: const Duration(milliseconds: 1200));
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  static const _colors = [
    Colors.red,
    Colors.green,
    Colors.blue,
    Colors.yellow,
    Colors.purple,
  ];

  static const _padding = 12.0;

  static const _dotSize = 12.0;

  static const _height = 24.0;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return SizedBox(
          width: _dotSize * 5 + _padding * 6,
          height: _height,
          child: Stack(children: List.generate(5, (index) => buildDot(index))),
        );
      },
    );
  }

  Widget buildDot(int index) {
    var value = _controller.value;
    var startValue = index * 0.8;
    return Positioned(
      left: index * _dotSize + (index + 1) * _padding,
      bottom:
          (math.sin(math.pi / 2 * (value - startValue).clamp(0, 2))) *
          (_height - _dotSize),
      child: Container(
        width: _dotSize,
        height: _dotSize,
        decoration: BoxDecoration(
          color: _colors[index],
          shape: BoxShape.circle,
        ),
      ),
    );
  }
}
