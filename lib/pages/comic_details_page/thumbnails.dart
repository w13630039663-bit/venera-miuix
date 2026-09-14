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

  /// 兜底模式使用的首话章节 id（阅读器侧 eid 也是它，图片缓存 key 才能对齐）
  String? _fallbackChapterId;

  bool _previewFallbackLoaded = false;

  /// 首话页面数是否超过展示上限（用于显示「查看更多」卡片）
  bool hasMore = false;

  @override
  void didChangeDependencies() {
    state = context.findAncestorStateOfType<_ComicPageState>()!;
    thumbnails = List.from(state.comic.thumbnails ?? []);
    super.didChangeDependencies();
    if (thumbnails.isNotEmpty) {
      // 详情接口已给出首批缩略图，按 token 继续拉更多。
      loadNext();
    } else if (state.comicSource.loadComicThumbnail != null) {
      // 源自带官方缩略图接口 → **优先用它**（e-hentai 的 loadThumbnails 抓的是
      // gallery 页面里的官方预览小图）：一次请求拿一页 HTML 里的全部缩略图，
      // 比「逐页请求大图」的兜底模式快得多，也不吃站方的图片配额。
      loadNext();
    } else {
      loadFirstChapterPreview();
    }
  }

  /// 能否用「首话页面图」兜底当预览。
  bool get canFallbackPreview {
    final chapters = state.comic.chapters;
    return state.comicSource.loadComicPages != null &&
        chapters != null &&
        chapters.ids.isNotEmpty;
  }

  /// 当详情接口没有提供 thumbnails 时，从首话页面图回退生成预览。
  /// 只取前 [previewLimit] 张，超出则在列表末尾提供「查看更多」卡片。
  static const int previewLimit = 10;

  void loadFirstChapterPreview() {
    if (_previewFallbackLoaded) return;
    if (!canFallbackPreview) return;
    final source = state.comicSource;
    final chapters = state.comic.chapters!;
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
        _fallbackChapterId = epId;
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
    if (state.comicSource.loadComicThumbnail == null) {
      // 没有官方缩略图接口 → 直接用首话页面图兜底。
      loadFirstChapterPreview();
      return;
    }
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
      // 接口通了却一张图都没解析出来（源改版 / 需要登录）：退回首话兜底，
      // 否则预览区会停在「既没图也没报错」的空状态。
      if (thumbnails.isEmpty && next == null) {
        if (mounted) {
          setState(() {
            isLoading = false;
          });
        }
        loadFirstChapterPreview();
        return;
      }
    } else {
      error = res.errorMessage;
      // 首屏就失败：能兜底就兜底，别让预览区只剩一句报错。
      if (thumbnails.isEmpty && canFallbackPreview) {
        error = null;
        if (mounted) {
          setState(() {
            isLoading = false;
          });
        }
        loadFirstChapterPreview();
        return;
      }
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
              // 兜底模式拿的是首话的「页面图」，必须走阅读器那条图片链路：
              // 图源的解混淆/解密钩子（getImageLoadingConfig 的 modifyImage）
              // 与全局「自定义图片处理」只在 loadComicImage 里执行，缩略图
              // 链路（loadThumbnail）不跑 —— 用 CachedImageProvider 会直接
              // 显示被打乱过的原图（横条撕裂）。cacheKey 与阅读器完全一致，
              // 顺带把阅读器要用的那张图预热到磁盘缓存。
              final bool readerPipeline =
                  _isFallbackMode && _fallbackChapterId != null;
              final ImageProvider imageProvider = readerPipeline
                  ? ReaderImageProvider(
                      thumbnails[index],
                      state.comic.sourceKey,
                      state.comic.id,
                      _fallbackChapterId!,
                      index + 1,
                    )
                  : CachedImageProvider(
                      url,
                      sourceKey: state.widget.sourceKey,
                    );
              // 阅读器的裁剪/还原全在源侧，ComicImage 也没有 part 参数 —— 走
              // 阅读器链路时本地不再裁。
              final ImagePart? cardPart = readerPipeline ? null : part;
              final outlineColor = Theme.of(context).colorScheme.outline;
              return Padding(
                padding: context.width < changePoint
                    ? const EdgeInsets.all(4)
                    : const EdgeInsets.all(8),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Expanded(
                      child: InkWell(
                        // 顺带把卡片这张图交给阅读器当「图集就绪前的占位」——
                        // 阅读器首帧还没有图，没有占位就会「飞完闪一下再转圈」。
                        // 占位 = 裸图（previewFlightImage），与卡片 Hero child
                        // 同一构建函数；卡片框不进阅读器。
                        onTap: () => state.read(
                          null,
                          index + 1,
                          null,
                          previewFlightImage(
                            image: imageProvider,
                            part: cardPart,
                            // 占位要铺满全屏，且不带缩放的 provider 与阅读器
                            // 图集用的是同一份原始解码缓存（BaseImageProvider
                            // 的 == 按 key 比较，enableResize 也都是 false）。
                            cacheWidth: null,
                          ),
                        ),
                        borderRadius:
                        const BorderRadius.all(Radius.circular(8)),
                        // 共享元素：点卡片 → 阅读器时，封面从这张卡片的位置
                        // 连续放大到全屏；阅读器返回时原路缩回。tag 与阅读器
                        // 侧严格一致（见 preview_hero.dart），否则静默不飞。
                        //
                        // Hero child 只放裸图：卡片的圆角 + 描边画在下面的
                        // Container 上、留在原地。Hero 飞行按插值矩形逐帧重新
                        // 布局，框若进 Hero 会跟着从卡片「长」到全屏。
                        //
                        // PreviewFlightBackdrop：飞行期间本卡片糊+暗+淡
                        // （demo 的背景退场，e = smoothstep(飞行进度)）。
                        child: PreviewFlightBackdrop(
                          child: Container(
                            clipBehavior: Clip.antiAlias,
                            decoration: BoxDecoration(
                              borderRadius:
                                  const BorderRadius.all(Radius.circular(8)),
                              // miuix：格子加卡片底色，与区块卡片同层次。
                              color: useMiuixStyle
                                  ? comicCardBg(context)
                                  : null,
                            ),
                            foregroundDecoration: BoxDecoration(
                              borderRadius:
                                  const BorderRadius.all(Radius.circular(8)),
                              border: Border.all(color: outlineColor),
                            ),
                            child: Hero(
                              tag: previewHeroTag(
                                state.comic.sourceKey,
                                state.comic.id,
                                1,
                                index + 1,
                              ),
                              transitionOnUserGestures: true,
                              flightShuttleBuilder: previewHeroFlightShuttle,
                              createRectTween: previewHeroCreateRectTween,
                              child: previewFlightImage(
                                image: imageProvider,
                                part: cardPart,
                                // 裁剪参数是原图的像素坐标，做过解码缩放就会裁错
                                // 位置 —— 只有不带 part 时才限制解码尺寸。
                                cacheWidth: cardPart == null
                                    ? coverDecodeWidth(context, 200)
                                    : null,
                              ),
                            ),
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
        if (!useMiuixStyle)
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
            color: useMiuixStyle ? comicCardBg(context) : null,
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
