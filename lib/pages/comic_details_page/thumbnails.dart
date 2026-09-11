part of 'comic_page.dart';

class _ComicThumbnails extends StatefulWidget {
  const _ComicThumbnails();

  @override
  State<_ComicThumbnails> createState() => _ComicThumbnailsState();
}

class _ComicThumbnailsState extends State<_ComicThumbnails> {
  late _ComicPageState state;

  late List<String> thumbnails;

  bool isInitialLoading = true;

  String? next;

  String? error;

  bool isLoading = false;

  /// 兜底模式：本源详情接口未返回 thumbnails，改用首话页面图当预览
  bool _isFallbackMode = false;

  bool _previewFallbackLoaded = false;

  /// 首话页面数是否超过展示上限（用于显示「查看更多」卡片）
  bool hasMore = false;

  @override
  void didChangeDependencies() {
    state = context.findAncestorStateOfType<_ComicPageState>()!;
    thumbnails = List.from(state.comic.thumbnails ?? []);
    super.didChangeDependencies();
    if (thumbnails.isEmpty) {
      loadFirstChapterPreview();
    } else {
      loadNext();
    }
  }

  /// 当详情接口没有提供 thumbnails 时，从首话页面图回退生成预览。
  /// 只取前 [previewLimit] 张，超出则在列表末尾提供「查看更多」卡片。
  static const int previewLimit = 10;

  void loadFirstChapterPreview() {
    if (_previewFallbackLoaded) return;
    final source = state.comicSource;
    final chapters = state.comic.chapters;
    if (source.loadComicPages == null ||
        chapters == null ||
        chapters.ids.isEmpty) {
      return;
    }
    _previewFallbackLoaded = true;
    if (mounted) {
      setState(() {
        isLoading = true;
      });
    }
    final epId = chapters.ids.first;
    source.loadComicPages!(state.comic.id, epId).then((res) {
      if (res.success) {
        final urls = res.data;
        hasMore = urls.length > previewLimit;
        thumbnails = urls.take(previewLimit).toList();
        _isFallbackMode = true;
      } else {
        error = res.errorMessage;
        _previewFallbackLoaded = false; // 允许重试
      }
      if (mounted) {
        setState(() {
          isLoading = false;
        });
      }
    });
  }

  void loadNext() async {
    if (state.comicSource.loadComicThumbnail == null) return;
    if (!isInitialLoading && next == null) {
      return;
    }
    if (isLoading) return;
    Future.microtask(() {
      setState(() {
        isLoading = true;
      });
    });
    var res = await state.comicSource.loadComicThumbnail!(state.comic.id, next);
    if (res.success) {
      thumbnails.addAll(res.data);
      next = res.subData;
      isInitialLoading = false;
    } else {
      error = res.errorMessage;
    }
    if (mounted) {
      setState(() {
        isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return MultiSliver(
      children: [
        SliverToBoxAdapter(
          child: ListTile(
            title: Text("Preview".tl),
          ),
        ),
        SliverGrid(
          delegate: SliverChildBuilderDelegate(
            childCount: thumbnails.length + (hasMore ? 1 : 0),
                (context, index) {
              if (hasMore && index == thumbnails.length) {
                return _buildViewMoreCard(context);
              }
              if (index == thumbnails.length - 1 && error == null) {
                loadNext();
              }
              var url = thumbnails[index];
              ImagePart? part;
              if (url.contains('@')) {
                var params = url.split('@')[1].split('&');
                url = url.split('@')[0];
                double? x1, y1, x2, y2;
                try {
                  for (var p in params) {
                    if (p.startsWith('x')) {
                      var r = p.split('=')[1];
                      x1 = double.parse(r.split('-')[0]);
                      x2 = double.parse(r.split('-')[1]);
                    }
                    if (p.startsWith('y')) {
                      var r = p.split('=')[1];
                      y1 = double.parse(r.split('-')[0]);
                      y2 = double.parse(r.split('-')[1]);
                    }
                  }
                } catch (_) {
                  // ignore
                }
                part = ImagePart(x1: x1, y1: y1, x2: x2, y2: y2);
              }
              return Padding(
                padding: context.width < changePoint
                    ? const EdgeInsets.all(4)
                    : const EdgeInsets.all(8),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Expanded(
                      child: InkWell(
                        onTap: () => state.read(null, index + 1),
                        borderRadius:
                        const BorderRadius.all(Radius.circular(8)),
                        child: Container(
                          foregroundDecoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(
                              color: Theme.of(context).colorScheme.outline,
                            ),
                          ),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(8),
                          ),
                          width: double.infinity,
                          height: double.infinity,
                          clipBehavior: Clip.antiAlias,
                          child: AnimatedImage(
                            image: CachedImageProvider(
                              url,
                              sourceKey: state.widget.sourceKey,
                            ),
                            fit: BoxFit.contain,
                            width: double.infinity,
                            height: double.infinity,
                            part: part,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(
                      height: 4,
                    ),
                    Text((index + 1).toString()),
                  ],
                ),
              );
            },
          ),
          gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
            maxCrossAxisExtent: 200,
            childAspectRatio: 0.68,
          ),
        ),
        if (error != null)
          SliverToBoxAdapter(
            child: Column(
              children: [
                Text(error!),
                Button.outlined(
                  onPressed: _isFallbackMode ? loadFirstChapterPreview : loadNext,
                  child: Text("Retry".tl),
                )
              ],
            ),
          )
        else if (isLoading)
          const SliverListLoadingIndicator(),
        const SliverToBoxAdapter(
          child: Divider(),
        ),
      ],
    );
  }

  /// 「查看更多」卡片：首话页面数超过展示上限时，在预览网格末尾出现，
  /// 点击进入阅读器（默认第一话第一页）。
  Widget _buildViewMoreCard(BuildContext context) {
    final color = Theme.of(context).colorScheme.primary;
    return Padding(
      padding: context.width < changePoint
          ? const EdgeInsets.all(4)
          : const EdgeInsets.all(8),
      child: InkWell(
        onTap: () => state.read(null, 1),
        borderRadius: const BorderRadius.all(Radius.circular(8)),
        child: Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: Theme.of(context).colorScheme.outline),
          ),
          width: double.infinity,
          height: double.infinity,
          clipBehavior: Clip.antiAlias,
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.arrow_forward_ios, size: 28, color: color),
                const SizedBox(height: 8),
                Text(
                  "View More".tl,
                  style: TextStyle(color: color),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
