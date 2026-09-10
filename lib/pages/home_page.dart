import 'package:flutter/material.dart';
import 'package:flutter_miuix/miuix.dart';
import 'package:sliver_tools/sliver_tools.dart';
import 'package:url_launcher/url_launcher_string.dart';
import 'package:venera/components/components.dart';
import 'package:venera/foundation/app.dart';
import 'package:venera/foundation/appdata.dart';
import 'package:venera/foundation/comic_source/comic_source.dart';
import 'package:venera/foundation/consts.dart';
import 'package:venera/foundation/favorites.dart';
import 'package:venera/foundation/history.dart';
import 'package:venera/foundation/image_provider/cached_image.dart';
import 'package:venera/foundation/image_provider/history_image_provider.dart';
import 'package:venera/foundation/local.dart';
import 'package:venera/foundation/log.dart';
import 'package:venera/pages/comic_details_page/comic_page.dart';
import 'package:venera/pages/comic_source_page.dart';
import 'package:venera/pages/downloading_page.dart';
import 'package:venera/pages/follow_updates_page.dart';
import 'package:venera/pages/history_page.dart';
import 'package:venera/pages/image_favorites_page/image_favorites_page.dart';
import 'package:venera/utils/data_sync.dart';
import 'package:venera/utils/import_comic.dart';
import 'package:venera/utils/tags_translation.dart';
import 'package:venera/utils/translations.dart';

import 'local_comics_page.dart';

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    var widget = SmoothCustomScrollView(
      slivers: [
        SliverPadding(padding: EdgeInsets.only(top: context.padding.top)),
        const _SyncDataWidget(),
        // Miuix 画风 = 截图式卡片排版（今日推荐/历史/漫画源/本地/图片收藏）；
        // Classic 画风保留原版 MD3 分区卡片。由「外观 → Settings Style」切换。
        if (useMiuixStyle) ...[
          const _TodayUpdates(),
          const _MiuixHistory(),
          const _MiuixComicSources(),
          const _MiuixLocal(),
          const _MiuixImageFavorites(),
        ] else ...[
          const _History(),
          const _Local(),
          const FollowUpdatesWidget(),
          const _ComicSourceWidget(),
          const ImageFavorites(),
        ],
        SliverPadding(padding: EdgeInsets.only(top: context.padding.bottom)),
      ],
    );
    return context.width > changePoint ? widget.paddingHorizontal(8) : widget;
  }
}

// ── Miuix 画风主页分区 ─────────────────────────────────────────────
// 排版对齐参考截图：小标题行（标题 + 右箭头）→ 横向卡片流。
// 每个分区都包 withMiuixTheme（MiuixTheme.of 无祖先时回退浅色）。

class _MiuixSectionHeader extends StatelessWidget {
  const _MiuixSectionHeader(this.title, {required this.onTap});

  final String title;

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 8, 4, 4),
      child: InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          child: Row(
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const Spacer(),
              Icon(
                Icons.arrow_forward_ios,
                size: 14,
                color: MiuixTheme.of(context).colors.onSurfaceVariantSummary,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 今日推荐：追踪更新文件夹中有更新的漫画，横向大卡片
/// （封面 + 标题 + 更新信息 + NEW 角标）。
class _TodayUpdates extends StatefulWidget {
  const _TodayUpdates();

  @override
  State<_TodayUpdates> createState() => _TodayUpdatesState();
}

class _TodayUpdatesState extends State<_TodayUpdates> {
  List<FavoriteItemWithUpdateInfo> _updated = [];

  String? get folder => appdata.settings["followUpdatesFolder"];

  void load() {
    if (folder == null ||
        !LocalFavoritesManager().folderNames.contains(folder)) {
      _updated = const [];
      return;
    }
    // 与列表页同一套屏蔽口径（hide 强度 / 屏蔽词 / 标签 / 画师 / 收录作品）。
    // 这里是自己拼的横向卡片流，不经过 SliverGridComics，必须显式过滤。
    _updated = filterBlocked(
      LocalFavoritesManager()
          .getComicsWithUpdatesInfo(folder!)
          .where((c) => c.hasNewUpdate),
    );
  }

  void onChange() {
    if (mounted) {
      setState(load);
    }
  }

  @override
  void initState() {
    super.initState();
    load();
    LocalFavoritesManager().addListener(onChange);
  }

  @override
  void dispose() {
    LocalFavoritesManager().removeListener(onChange);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_updated.isEmpty) {
      return const SliverToBoxAdapter(child: SizedBox.shrink());
    }
    return withMiuixTheme(
      context,
      SliverToBoxAdapter(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _MiuixSectionHeader(
              "Today's Recommendations".tl,
              onTap: () {
                context.to(() => FollowUpdatesPage());
              },
            ),
            SizedBox(
              height: 152,
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 8),
                itemCount: _updated.length,
                itemBuilder: (context, index) {
                  final comic = _updated[index];
                  return _TodayCard(comic: comic);
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TodayCard extends StatelessWidget {
  const _TodayCard({required this.comic});

  final FavoriteItemWithUpdateInfo comic;

  @override
  Widget build(BuildContext context) {
    final heroID = comic.id.hashCode;
    return withMiuixTheme(
      context,
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4),
        child: MiuixCard(
          cornerRadius: 16,
          insideMargin: const EdgeInsets.all(8),
          onPressed: () {
            context.to(
              () => ComicPage(
                id: comic.id,
                sourceKey: comic.type.sourceKey,
                cover: comic.coverPath,
                title: comic.name,
                heroID: heroID,
              ),
            );
          },
          feedbackType: MiuixPressFeedbackType.sink,
          child: SizedBox(
            width: 264,
            child: Row(
              children: [
                Container(
                  width: 96,
                  height: double.infinity,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(12),
                    color: MiuixTheme.of(context)
                        .colors
                        .onSurfaceVariantSummary
                        .withValues(alpha: 0.12),
                  ),
                  clipBehavior: Clip.antiAlias,
                  // 自绘封面（今日推荐卡片）绕过 ComicTile.buildImage，
                  // 遮蔽壳要自己套。
                  child: NsfwCover(
                    comic: comic,
                    child: AnimatedImage(
                      image: CachedImageProvider(
                        comic.coverPath,
                        sourceKey: comic.type.sourceKey,
                        cid: comic.id,
                        fallbackToLocalCover: true,
                      ),
                      width: double.infinity,
                      height: double.infinity,
                      fit: BoxFit.cover,
                      // 封面区固定 96dp 宽（见上方 Container）。
                      cacheWidth: coverDecodeWidth(context, 96),
                      filterQuality: FilterQuality.low,
                    ),
                  ),
                ),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(10, 4, 4, 4),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          comic.name.replaceAll('\n', ''),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w500,
                            height: 1.25,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          comic.description,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 12,
                            color: MiuixTheme.of(context)
                                .colors
                                .onSurfaceVariantSummary,
                          ),
                        ),
                        const Spacer(),
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: const Color(0xCCD32F2F),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: const Text(
                            'NEW',
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.w700,
                              color: Colors.white,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// 历史：横向滑动的**两行**网格（一屏同时显示两列卡片），
/// 封面在上，标题两行 + 副标题一行，无进度条。
class _MiuixHistory extends StatefulWidget {
  const _MiuixHistory();

  @override
  State<_MiuixHistory> createState() => _MiuixHistoryState();
}

class _MiuixHistoryState extends State<_MiuixHistory> {
  late List<History> history;

  /// 与列表页同一套屏蔽口径。「H 是不行的」强度为 hide、或命中屏蔽词 /
  /// 标签 / 画师 / 收录作品时，历史卡片同样要整条剔除 —— 这条横向卡片流
  /// 是自己拼的，不走 SliverGridComics，必须显式过滤。
  List<History> _visibleHistory() =>
      filterBlocked(HistoryManager().getRecent());

  void onHistoryChange() {
    if (mounted) {
      setState(() {
        history = _visibleHistory();
      });
    }
  }

  @override
  void initState() {
    super.initState();
    history = _visibleHistory();
    HistoryManager().addListener(onHistoryChange);
  }

  @override
  void dispose() {
    HistoryManager().removeListener(onHistoryChange);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (history.isEmpty) {
      return const SliverToBoxAdapter(child: SizedBox.shrink());
    }
    return withMiuixTheme(
      context,
      SliverToBoxAdapter(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _MiuixSectionHeader(
              "History".tl,
              onTap: () {
                context.to(() => const HistoryPage());
              },
            ),
            // 横向 GridView：crossAxisCount=2 即竖向两行同时可见。
            // cell 高 = (420 - 8) / 2 = 206，其中封面用 Expanded 弹性填充。
            SizedBox(
              height: 420,
              child: GridView.builder(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 8),
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 2,
                  crossAxisSpacing: 8,
                  mainAxisSpacing: 8,
                  mainAxisExtent: 124,
                ),
                itemCount: history.length,
                itemBuilder: (context, index) {
                  return _HistoryCard(comic: history[index]);
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _HistoryCard extends StatelessWidget {
  const _HistoryCard({required this.comic});

  final History comic;

  @override
  Widget build(BuildContext context) {
    final heroID = comic.id.hashCode;
    return withMiuixTheme(
      context,
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4),
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: () {
            context.to(
              () => ComicPage(
                id: comic.id,
                sourceKey: comic.type.sourceKey,
                cover: comic.cover,
                title: comic.title,
                heroID: heroID,
              ),
            );
          },
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Hero(
                  tag: "cover$heroID",
                  child: Container(
                    width: double.infinity,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(12),
                      color: MiuixTheme.of(context)
                          .colors
                          .onSurfaceVariantSummary
                          .withValues(alpha: 0.12),
                    ),
                    clipBehavior: Clip.antiAlias,
                    // 历史卡片自绘封面（HistoryImageProvider），不走
                    // ComicTile.buildImage，遮蔽壳要自己套。
                    child: NsfwCover(
                      comic: comic,
                      child: AnimatedImage(
                      image: HistoryImageProvider(comic),
                      width: double.infinity,
                      height: double.infinity,
                      fit: BoxFit.cover,
                      // 卡片宽度 = 网格 mainAxisExtent（124dp）。
                      cacheWidth: coverDecodeWidth(context, 124),
                      filterQuality: FilterQuality.low,
                    ),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 6),
              // 标题两行 + 副标题一行，无进度条。
              Text(
                comic.title.replaceAll('\n', ''),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                  height: 1.25,
                ),
              ),
              Text(
                comic.subtitle,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 12,
                  color: MiuixTheme.of(context).colors.onSurfaceVariantSummary,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 漫画源：横向小方卡，图标用源网站的 favicon（加载失败回退通用图标）。
class _MiuixComicSources extends StatefulWidget {
  const _MiuixComicSources();

  @override
  State<_MiuixComicSources> createState() => _MiuixComicSourcesState();
}

class _MiuixComicSourcesState extends State<_MiuixComicSources> {
  late List<ComicSource> sources;

  void onComicSourceChange() {
    if (mounted) {
      setState(() {
        sources = ComicSource.all();
      });
    }
  }

  @override
  void initState() {
    super.initState();
    sources = ComicSource.all();
    ComicSourceManager().addListener(onComicSourceChange);
  }

  @override
  void dispose() {
    ComicSourceManager().removeListener(onComicSourceChange);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (sources.isEmpty) {
      return const SliverToBoxAdapter(child: SizedBox.shrink());
    }
    return withMiuixTheme(
      context,
      SliverToBoxAdapter(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _MiuixSectionHeader(
              "Comic Source".tl,
              onTap: () {
                context.to(() => const ComicSourcePage());
              },
            ),
            SizedBox(
              height: 108,
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 8),
                itemCount: sources.length,
                itemBuilder: (context, index) {
                  return _SourceCard(source: sources[index]);
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SourceCard extends StatelessWidget {
  const _SourceCard({required this.source});

  final ComicSource source;

  /// 指定源使用其官网 favicon（源插件自带的 url 未必指向真正的官网）。
  /// key 为源名小写并去掉空格/横杠/下划线后的规范化形式。
  /// 地址均实测可达：nhentai 的图标是 favicon.png（.ico 为 404）；
  /// picacg 官网只有 http 通道（https 不通），依赖 manifest 开启明文流量。
  static const Map<String, String> _faviconOverrides = {
    'nhentai': 'https://nhentai.net/favicon.png',
    'picacg': 'http://picacgp.com/favicon.ico',
    'bika': 'http://picacgp.com/favicon.ico',
    'komiic': 'https://komiic.com/favicon.ico',
    'ehentai': 'https://e-hentai.org/favicon.ico',
    '禁漫天堂': 'https://18comic.vip/favicon.ico',
    'jmcomic': 'https://18comic.vip/favicon.ico',
  };

  /// 源网站 favicon：优先按源名查覆盖表，否则取 url 的 host 拼标准路径。
  String? get _faviconUrl {
    final normalizedName =
        source.name.toLowerCase().replaceAll(RegExp(r'[\s\-_]'), '');
    final override = _faviconOverrides[normalizedName];
    if (override != null) return override;
    try {
      final host = Uri.parse(source.url).host;
      if (host.isEmpty) return null;
      return 'https://$host/favicon.ico';
    } catch (_) {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final favicon = _faviconUrl;
    return withMiuixTheme(
      context,
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4),
        child: MiuixCard(
          cornerRadius: 16,
          insideMargin: const EdgeInsets.all(8),
          onPressed: () {
            context.to(() => const ComicSourcePage());
          },
          feedbackType: MiuixPressFeedbackType.sink,
          child: SizedBox(
            width: 84,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                SizedBox(
                  width: 40,
                  height: 40,
                  child: favicon == null
                      ? Icon(
                          Icons.public,
                          size: 32,
                          color: MiuixTheme.of(context)
                              .colors
                              .onSurfaceVariantSummary,
                        )
                      : Image(
                          image: CachedImageProvider(favicon),
                          width: 40,
                          height: 40,
                          fit: BoxFit.contain,
                          errorBuilder: (context, error, stackTrace) => Icon(
                            Icons.public,
                            size: 32,
                            color: MiuixTheme.of(context)
                                .colors
                                .onSurfaceVariantSummary,
                          ),
                        ),
                ),
                const SizedBox(height: 6),
                Text(
                  source.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// 本地：MiuixCard 行卡 —— 左侧标题 + 文件数，右侧导入按钮。
class _MiuixLocal extends StatefulWidget {
  const _MiuixLocal();

  @override
  State<_MiuixLocal> createState() => _MiuixLocalState();
}

class _MiuixLocalState extends State<_MiuixLocal> {
  late int count;

  void onLocalComicsChange() {
    if (mounted) {
      setState(() {
        count = LocalManager().count;
      });
    }
  }

  @override
  void initState() {
    super.initState();
    count = LocalManager().count;
    LocalManager().addListener(onLocalComicsChange);
  }

  @override
  void dispose() {
    LocalManager().removeListener(onLocalComicsChange);
    super.dispose();
  }

  void import() {
    showDialog(
      barrierDismissible: false,
      context: App.rootContext,
      builder: (context) {
        return const _ImportComicsWidget();
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    // 本地栏文字/取色必须用 MD3 主题（build 的 context 在 withMiuixTheme
    // 包裹层之外，这里取 MiuixTheme.of 会拿到浅色默认值，深色模式必穿帮）。
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cardColors = isDark
        ? MiuixCardColors(
            // 深色下用与页面同色温且亮一档的 MD3 色，避免 Miuix 默认
            // 纯灰 #242424 贴在偏蓝黑背景上的突兀感。
            color: context.colorScheme.surfaceContainerHigh,
            contentColor: context.colorScheme.onSurface,
          )
        : null;
    final subtitleColor = context.colorScheme.onSurfaceVariant;
    return withMiuixTheme(
      context,
      SliverToBoxAdapter(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _MiuixSectionHeader(
              "Local".tl,
              onTap: () {
                context.to(() => const LocalComicsPage());
              },
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: MiuixCard(
                cornerRadius: 16,
                colors: cardColors,
                insideMargin:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                onPressed: () {
                  context.to(() => const LocalComicsPage());
                },
                feedbackType: MiuixPressFeedbackType.sink,
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            "Local".tl,
                            style: const TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            "@a local files".tlParams({
                              'a': count,
                            }),
                            style: TextStyle(
                              fontSize: 13,
                              color: subtitleColor,
                            ),
                          ),
                        ],
                      ),
                    ),
                    if (LocalManager().downloadingTasks.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: InkWell(
                          borderRadius: BorderRadius.circular(16),
                          onTap: () {
                            showPopUpWidget(context, const DownloadingPage());
                          },
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const _AnimatedDownloadingIcon(),
                              const SizedBox(width: 6),
                              Text("@a Tasks".tlParams({
                                'a': LocalManager().downloadingTasks.length,
                              })),
                            ],
                          ),
                        ).paddingHorizontal(8),
                      ),
                    Button.filled(
                      onPressed: import,
                      child: Text("Import".tl),
                    ).fixHeight(36),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SyncDataWidget extends StatefulWidget {
  const _SyncDataWidget();

  @override
  State<_SyncDataWidget> createState() => _SyncDataWidgetState();
}

class _SyncDataWidgetState extends State<_SyncDataWidget>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    DataSync().addListener(update);
    WidgetsBinding.instance.addObserver(this);
    lastCheck = DateTime.now();
  }

  void update() {
    if (mounted) {
      setState(() {});
    }
  }

  @override
  void dispose() {
    super.dispose();
    DataSync().removeListener(update);
    WidgetsBinding.instance.removeObserver(this);
  }

  late DateTime lastCheck;

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    if (state == AppLifecycleState.resumed) {
      if (DateTime.now().difference(lastCheck) > const Duration(minutes: 10)) {
        lastCheck = DateTime.now();
        DataSync().downloadData();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    Widget child;
    if (!DataSync().isEnabled) {
      child = const SliverPadding(padding: EdgeInsets.zero);
    } else if (DataSync().isUploading || DataSync().isDownloading) {
      child = SliverToBoxAdapter(
        child: Container(
          margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
          decoration: BoxDecoration(
            border: Border.all(
              color: Theme.of(context).colorScheme.primary,
            ),
            borderRadius: BorderRadius.circular(8),
          ),
          child: ListTile(
            leading: const Icon(Icons.sync),
            title: Text('Syncing Data'.tl),
            trailing: const CircularProgressIndicator(strokeWidth: 2)
                .fixWidth(18)
                .fixHeight(18),
          ),
        ),
      );
    } else {
      child = SliverToBoxAdapter(
        child: Container(
          margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
          decoration: BoxDecoration(
            border: Border.all(
              color: Theme.of(context).colorScheme.outlineVariant,
            ),
            borderRadius: BorderRadius.circular(8),
          ),
          child: ListTile(
            leading: const Icon(Icons.sync),
            title: Text('Sync Data'.tl),
            trailing: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (DataSync().lastError != null)
                  InkWell(
                    borderRadius: BorderRadius.circular(16),
                    onTap: () {
                      showDialogMessage(
                        App.rootContext,
                        "Error".tl,
                        DataSync().lastError!,
                      );
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: context.colorScheme.errorContainer,
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            Icons.error_outline,
                            color: Colors.red,
                            size: 18,
                          ),
                          const SizedBox(width: 4),
                          Text('Error'.tl, style: ts.s12),
                        ],
                      ),
                    ),
                  ).paddingRight(4),
                IconButton(
                  icon: const Icon(Icons.cloud_upload_outlined),
                  onPressed: () async {
                    DataSync().uploadData();
                  },
                ),
                IconButton(
                  icon: const Icon(Icons.cloud_download_outlined),
                  onPressed: () async {
                    DataSync().downloadData();
                  },
                ),
              ],
            ),
          ),
        ),
      );
    }
    return SliverAnimatedPaintExtent(
      duration: const Duration(milliseconds: 200),
      child: child,
    );
  }
}

class _History extends StatefulWidget {
  const _History();

  @override
  State<_History> createState() => _HistoryState();
}

class _HistoryState extends State<_History> {
  late List<History> history;
  late int count;

  // Classic 画风的历史卡片用的是 SimpleComicTile（已自带 NsfwCover 遮蔽），
  // 但 hide 强度的整条剔除要靠这里过滤 —— 与 Miuix 分支口径一致。
  List<History> _visibleHistory() =>
      filterBlocked(HistoryManager().getRecent());

  void onHistoryChange() {
    if (mounted) {
      setState(() {
        history = _visibleHistory();
        count = HistoryManager().count();
      });
    }
  }

  @override
  void initState() {
    history = _visibleHistory();
    count = HistoryManager().count();
    HistoryManager().addListener(onHistoryChange);
    super.initState();
  }

  @override
  void dispose() {
    HistoryManager().removeListener(onHistoryChange);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SliverToBoxAdapter(
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        decoration: BoxDecoration(
          border: Border.all(
            color: Theme.of(context).colorScheme.outlineVariant,
            width: 0.6,
          ),
          borderRadius: BorderRadius.circular(8),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(8),
          onTap: () {
            context.to(() => const HistoryPage());
          },
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                height: 56,
                child: Row(
                  children: [
                    Center(
                      child: Text('History'.tl, style: ts.s18),
                    ),
                    Container(
                      margin: const EdgeInsets.symmetric(horizontal: 8),
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 2),
                      decoration: BoxDecoration(
                        color: Theme.of(context).colorScheme.secondaryContainer,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(count.toString(), style: ts.s12),
                    ),
                    const Spacer(),
                    const Icon(Icons.arrow_right),
                  ],
                ),
              ).paddingHorizontal(16),
              if (history.isNotEmpty)
                SizedBox(
                  height: 136,
                  child: ListView.builder(
                    scrollDirection: Axis.horizontal,
                    itemCount: history.length,
                    itemBuilder: (context, index) {
                      final heroID = history[index].id.hashCode;
                      return SimpleComicTile(
                        comic: history[index],
                        heroID: heroID,
                        onTap: () {
                          context.to(
                            () => ComicPage(
                              id: history[index].id,
                              sourceKey: history[index].type.sourceKey,
                              cover: history[index].cover,
                              title: history[index].title,
                              heroID: heroID,
                            ),
                          );
                        },
                      ).paddingHorizontal(8).paddingVertical(2);
                    },
                  ),
                ).paddingHorizontal(8).paddingBottom(16),
            ],
          ),
        ),
      ),
    );
  }
}

class _Local extends StatefulWidget {
  const _Local();

  @override
  State<_Local> createState() => _LocalState();
}

class _LocalState extends State<_Local> {
  late List<LocalComic> local;
  late int count;

  void onLocalComicsChange() {
    setState(() {
      local = LocalManager().getRecent();
      count = LocalManager().count;
    });
  }

  @override
  void initState() {
    local = LocalManager().getRecent();
    count = LocalManager().count;
    LocalManager().addListener(onLocalComicsChange);
    super.initState();
  }

  @override
  void dispose() {
    LocalManager().removeListener(onLocalComicsChange);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SliverToBoxAdapter(
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        decoration: BoxDecoration(
          border: Border.all(
            color: Theme.of(context).colorScheme.outlineVariant,
            width: 0.6,
          ),
          borderRadius: BorderRadius.circular(8),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(8),
          onTap: () {
            context.to(() => const LocalComicsPage());
          },
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                height: 56,
                child: Row(
                  children: [
                    Center(
                      child: Text('Local'.tl, style: ts.s18),
                    ),
                    Container(
                      margin: const EdgeInsets.symmetric(horizontal: 8),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: Theme.of(context).colorScheme.secondaryContainer,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(count.toString(), style: ts.s12),
                    ),
                    const Spacer(),
                    const Icon(Icons.arrow_right),
                  ],
                ),
              ).paddingHorizontal(16),
              if (local.isNotEmpty)
                SizedBox(
                  height: 136,
                  child: ListView.builder(
                    scrollDirection: Axis.horizontal,
                    itemCount: local.length,
                    itemBuilder: (context, index) {
                      final heroID = local[index].id.hashCode;
                      return SimpleComicTile(
                        comic: local[index],
                        heroID: heroID,
                        onTap: () {
                          context.to(
                            () => ComicPage(
                              id: local[index].id,
                              sourceKey: local[index].sourceKey,
                              cover: local[index].cover,
                              title: local[index].title,
                              heroID: heroID,
                            ),
                          );
                        },
                      ).paddingHorizontal(8).paddingVertical(2);
                    },
                  ),
                ).paddingHorizontal(8),
              Row(
                children: [
                  if (LocalManager().downloadingTasks.isNotEmpty)
                    Button.outlined(
                      child: Row(
                        children: [
                          if (LocalManager().downloadingTasks.first.isPaused)
                            const Icon(Icons.pause_circle_outline, size: 18)
                          else
                            const _AnimatedDownloadingIcon(),
                          const SizedBox(width: 8),
                          Text("@a Tasks".tlParams({
                            'a': LocalManager().downloadingTasks.length,
                          })),
                        ],
                      ),
                      onPressed: () {
                        showPopUpWidget(context, const DownloadingPage());
                      },
                    ),
                  const Spacer(),
                  Button.filled(
                    onPressed: import,
                    child: Text("Import".tl),
                  ),
                ],
              ).paddingHorizontal(16).paddingVertical(8),
            ],
          ),
        ),
      ),
    );
  }

  void import() {
    showDialog(
      barrierDismissible: false,
      context: App.rootContext,
      builder: (context) {
        return const _ImportComicsWidget();
      },
    );
  }
}

class _ImportComicsWidget extends StatefulWidget {
  const _ImportComicsWidget();

  @override
  State<_ImportComicsWidget> createState() => _ImportComicsWidgetState();
}

class _ImportComicsWidgetState extends State<_ImportComicsWidget> {
  int type = 0;

  bool loading = false;

  var key = GlobalKey();

  var height = 200.0;

  var folders = LocalFavoritesManager().folderNames;

  String? selectedFolder;

  bool copyToLocalFolder = true;

  bool cancelled = false;

  @override
  void dispose() {
    loading = false;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    String info = [
      "Select a directory which contains the comic files.".tl,
      "Select a directory which contains the comic directories.".tl,
      "Select an archive file (cbz, zip, 7z, cb7)".tl,
      "Select a directory which contains multiple archive files.".tl,
      "Select an EhViewer database and a download folder.".tl,
      "Scan the current local path and restore the local database.".tl,
    ][type];
    List<String> importMethods = [
      "Single Comic".tl,
      "Multiple Comics".tl,
      "An archive file".tl,
      "Multiple archive files".tl,
      "EhViewer downloads".tl,
      "Restore local downloads".tl,
    ];

    return ContentDialog(
      dismissible: !loading,
      title: "Import Comics".tl,
      content: loading
          ? SizedBox(
              width: 600,
              height: height,
              child: const Center(
                child: CircularProgressIndicator(),
              ),
            )
          : RadioGroup<int>(
              groupValue: type,
              onChanged: (value) {
                setState(() {
                  type = value ?? type;
                  if (type == 5) {
                    selectedFolder = null;
                  }
                });
              },
              child: Column(
                key: key,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SizedBox(width: 600),
                  ...List.generate(importMethods.length, (index) {
                    return RadioListTile<int>(
                      title: Text(importMethods[index]),
                      value: index,
                    );
                  }),
                  if (type != 4 && type != 5)
                    ListTile(
                      title: Text("Add to favorites".tl),
                      trailing: Select(
                        current: selectedFolder,
                        values: folders,
                        minWidth: 112,
                        onTap: (v) {
                          setState(() {
                            selectedFolder = folders[v];
                          });
                        },
                      ),
                    ).paddingHorizontal(8),
                  if (!App.isIOS &&
                      !App.isMacOS &&
                      type != 2 &&
                      type != 3 &&
                      type != 5)
                    CheckboxListTile(
                        enabled: true,
                        title: Text("Copy to app local path".tl),
                        value: copyToLocalFolder,
                        onChanged: (v) {
                          setState(() {
                            copyToLocalFolder = !copyToLocalFolder;
                          });
                        }).paddingHorizontal(8),
                  const SizedBox(height: 8),
                  Text(info).paddingHorizontal(24),
                ],
              ),
          ),
      actions: [
        Button.text(
          child: Row(
            children: [
              Icon(
                Icons.help_outline,
                size: 18,
                color: context.colorScheme.primary,
              ),
              const SizedBox(width: 8),
              Text("help".tl),
            ],
          ),
          onPressed: () {
            launchUrlString(
                "https://github.com/w13630039663-bit/venera-miuix/blob/master/doc/import_comic.md");
          },
        ).fixWidth(90).paddingRight(8),
        Button.filled(
          isLoading: loading,
          onPressed: selectAndImport,
          child: Text("Select".tl),
        )
      ],
    );
  }

  void selectAndImport() async {
    height = key.currentContext!.size!.height;

    setState(() {
      loading = true;
    });
    var importer = ImportComic(
        selectedFolder: selectedFolder, copyToLocal: copyToLocalFolder);
    var result = switch (type) {
      0 => await importer.directory(true),
      1 => await importer.directory(false),
      2 => await importer.cbz(),
      3 => await importer.multipleCbz(),
      4 => await importer.ehViewer(),
      5 => await importer.localDownloads(),
      int() => true,
    };
    if (result) {
      context.pop();
    } else {
      setState(() {
        loading = false;
      });
    }
  }
}

class _ComicSourceWidget extends StatefulWidget {
  const _ComicSourceWidget();

  @override
  State<_ComicSourceWidget> createState() => _ComicSourceWidgetState();
}

class _ComicSourceWidgetState extends State<_ComicSourceWidget> {
  late List<String> comicSources;

  void onComicSourceChange() {
    setState(() {
      comicSources = ComicSource.all().map((e) => e.name).toList();
    });
  }

  @override
  void initState() {
    comicSources = ComicSource.all().map((e) => e.name).toList();
    ComicSourceManager().addListener(onComicSourceChange);
    super.initState();
  }

  @override
  void dispose() {
    ComicSourceManager().removeListener(onComicSourceChange);
    super.dispose();
  }

  int get _availableUpdates {
    int c = 0;
    ComicSourceManager().availableUpdates.forEach((key, version) {
      var source = ComicSource.find(key);
      if (source != null) {
        if (compareSemVer(version, source.version)) {
          c++;
        }
      }
    });
    return c;
  }

  @override
  Widget build(BuildContext context) {
    return SliverToBoxAdapter(
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        decoration: BoxDecoration(
          border: Border.all(
            color: Theme.of(context).colorScheme.outlineVariant,
            width: 0.6,
          ),
          borderRadius: BorderRadius.circular(8),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(8),
          onTap: () {
            context.to(() => const ComicSourcePage());
          },
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                height: 56,
                child: Row(
                  children: [
                    Center(
                      child: Text('Comic Source'.tl, style: ts.s18),
                    ),
                    Container(
                      margin: const EdgeInsets.symmetric(horizontal: 8),
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 2),
                      decoration: BoxDecoration(
                        color: Theme.of(context).colorScheme.secondaryContainer,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child:
                          Text(comicSources.length.toString(), style: ts.s12),
                    ),
                    const Spacer(),
                    const Icon(Icons.arrow_right),
                  ],
                ),
              ).paddingHorizontal(16),
              if (comicSources.isNotEmpty)
                SizedBox(
                  width: double.infinity,
                  child: Wrap(
                    runSpacing: 8,
                    spacing: 8,
                    children: comicSources.map((e) {
                      return Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 8, vertical: 2),
                        decoration: BoxDecoration(
                          color:
                              Theme.of(context).colorScheme.secondaryContainer,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Text(e),
                      );
                    }).toList(),
                  ).paddingHorizontal(16).paddingBottom(16),
                ),
              if (_availableUpdates > 0)
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    border: Border.all(
                      color: context.colorScheme.outlineVariant,
                      width: 0.6,
                    ),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.update,
                        color: context.colorScheme.primary,
                        size: 20,
                      ),
                      const SizedBox(width: 8),
                      Text(
                        "@c updates".tlParams({
                          'c': _availableUpdates,
                        }),
                        style: ts.withColor(context.colorScheme.primary),
                      ),
                    ],
                  ),
                )
                    .toAlign(Alignment.centerLeft)
                    .paddingHorizontal(16)
                    .paddingBottom(8),
            ],
          ),
        ),
      ),
    );
  }
}

class _AnimatedDownloadingIcon extends StatefulWidget {
  const _AnimatedDownloadingIcon();

  @override
  State<_AnimatedDownloadingIcon> createState() =>
      __AnimatedDownloadingIconState();
}

class __AnimatedDownloadingIconState extends State<_AnimatedDownloadingIcon>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      lowerBound: -1,
      vsync: this,
      duration: const Duration(milliseconds: 2000),
    )..repeat();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return Container(
          width: 18,
          height: 18,
          decoration: BoxDecoration(
            border: Border(
              bottom: BorderSide(
                color: Theme.of(context).colorScheme.primary,
                width: 2,
              ),
            ),
          ),
          clipBehavior: Clip.hardEdge,
          child: Transform.translate(
            offset: Offset(0, 18 * _controller.value),
            child: Icon(
              Icons.arrow_downward,
              size: 16,
              color: Theme.of(context).colorScheme.primary,
            ),
          ),
        );
      },
    );
  }
}

class ImageFavorites extends StatefulWidget {
  const ImageFavorites({super.key});

  @override
  State<ImageFavorites> createState() => _ImageFavoritesState();
}

class _ImageFavoritesState extends State<ImageFavorites> {
  ImageFavoritesComputed? imageFavoritesCompute;

  int displayType = 0;

  void refreshImageFavorites() async {
    try {
      imageFavoritesCompute =
          await ImageFavoriteManager.computeImageFavorites();
      if (mounted) {
        setState(() {});
      }
    } catch (e, stackTrace) {
      Log.error("Unhandled Exception", e.toString(), stackTrace);
    }
  }

  @override
  void initState() {
    refreshImageFavorites();
    ImageFavoriteManager().addListener(refreshImageFavorites);
    super.initState();
  }

  @override
  void dispose() {
    ImageFavoriteManager().removeListener(refreshImageFavorites);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    bool hasData =
        imageFavoritesCompute != null && !imageFavoritesCompute!.isEmpty;
    return SliverToBoxAdapter(
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        decoration: BoxDecoration(
          border: Border.all(
            color: Theme.of(context).colorScheme.outlineVariant,
            width: 0.6,
          ),
          borderRadius: BorderRadius.circular(8),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(8),
          onTap: () {
            context.to(
              () => const ImageFavoritesPage()
            );
          },
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                height: 56,
                child: Row(
                  children: [
                    Center(
                      child: Text('Image Favorites'.tl, style: ts.s18),
                    ),
                    if (hasData)
                      Container(
                        margin: const EdgeInsets.symmetric(horizontal: 8),
                        padding: const EdgeInsets.symmetric(
                            horizontal: 8, vertical: 2),
                        decoration: BoxDecoration(
                          color:
                              Theme.of(context).colorScheme.secondaryContainer,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Text(
                          imageFavoritesCompute!.count.toString(),
                          style: ts.s12,
                        ),
                      ),
                    const Spacer(),
                    const Icon(Icons.arrow_right),
                  ],
                ),
              ).paddingHorizontal(16),
              if (hasData)
                Row(
                  children: [
                    const Spacer(),
                    buildTypeButton(0, "Tags".tl),
                    const Spacer(),
                    buildTypeButton(1, "Authors".tl),
                    const Spacer(),
                    buildTypeButton(2, "Comics".tl),
                    const Spacer(),
                  ],
                ),
              if (hasData) const SizedBox(height: 8),
              if (hasData)
                buildChart(switch (displayType) {
                  0 => imageFavoritesCompute!.tags,
                  1 => imageFavoritesCompute!.authors,
                  2 => imageFavoritesCompute!.comics,
                  _ => [],
                })
                    .paddingHorizontal(16)
                    .paddingBottom(16),
            ],
          ),
        ),
      ),
    );
  }

  Widget buildTypeButton(int type, String text) {
    const radius = 24.0;
    return InkWell(
      borderRadius: BorderRadius.circular(radius),
      onTap: () async {
        setState(() {
          displayType = type;
        });
        await Future.delayed(const Duration(milliseconds: 20));
        var scrollController = ScrollState.of(context).controller;
        scrollController.animateTo(
          scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 200),
          curve: Curves.ease,
        );
      },
      child: AnimatedContainer(
        width: 96,
        padding: const EdgeInsets.symmetric(vertical: 4),
        decoration: BoxDecoration(
          color:
              displayType == type ? context.colorScheme.primaryContainer : null,
          border: Border.all(
            color: Theme.of(context).colorScheme.outlineVariant,
            width: 0.6,
          ),
          borderRadius: BorderRadius.circular(radius),
        ),
        duration: const Duration(milliseconds: 200),
        child: Center(
          child: Text(
            text,
            style: ts.s16,
          ),
        ),
      ),
    );
  }

  Widget buildChart(List<TextWithCount> data) {
    if (data.isEmpty) {
      return const SizedBox();
    }
    var maxCount = data.map((e) => e.count).reduce((a, b) => a > b ? a : b);
    return ConstrainedBox(
      constraints: BoxConstraints(
        maxHeight: 164,
      ),
      child: SingleChildScrollView(
        child: Column(
          key: ValueKey(displayType),
          children: data.map((e) {
            return _ChartLine(
              text: e.text,
              count: e.count,
              maxCount: maxCount,
              enableTranslation: displayType != 2,
              onTap: (text) {
                context.to(
                  () => ImageFavoritesPage(initialKeyword: text),
                );
              },
            );
          }).toList(),
        ),
      ),
    );
  }
}

class _ChartLine extends StatefulWidget {
  const _ChartLine({
    required this.text,
    required this.count,
    required this.maxCount,
    required this.enableTranslation,
    this.onTap,
  });

  final String text;

  final int count;

  final int maxCount;

  final bool enableTranslation;

  final void Function(String text)? onTap;

  @override
  State<_ChartLine> createState() => __ChartLineState();
}

class __ChartLineState extends State<_ChartLine>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 200),
      value: 0,
    )..forward();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    var text = widget.text;
    var enableTranslation =
        App.locale.countryCode == 'CN' && widget.enableTranslation;
    if (enableTranslation) {
      text = text.translateTagsToCN;
    }
    if (widget.enableTranslation && text.contains(':')) {
      text = text.split(':').last;
    }
    return Row(
      children: [
        InkWell(
          borderRadius: BorderRadius.circular(4),
          onTap: () {
            widget.onTap?.call(widget.text);
          },
          child: Text(
            text,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          )
              .paddingHorizontal(4)
              .toAlign(Alignment.centerLeft)
              .fixWidth(context.width > 600 ? 120 : 80)
              .fixHeight(double.infinity),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: LayoutBuilder(builder: (context, constrains) {
            var width = constrains.maxWidth * widget.count / widget.maxCount;
            return AnimatedBuilder(
              animation: _controller,
              builder: (context, child) {
                return Container(
                  width: width * _controller.value,
                  height: 18,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(2),
                    gradient: LinearGradient(
                      colors: context.isDarkMode
                          ? [
                              Colors.blue.shade800,
                              Colors.blue.shade500,
                            ]
                          : [
                              Colors.blue.shade300,
                              Colors.blue.shade600,
                            ],
                    ),
                  ),
                ).toAlign(Alignment.centerLeft);
              },
            );
          }),
        ),
        const SizedBox(width: 8),
        Text(
          widget.count.toString(),
          style: ts.s12,
        ).fixWidth(context.width > 600 ? 60 : 30),
      ],
    ).fixHeight(28);
  }
}

/// 图片收藏（Miuix 画风）：小节标题 + MiuixCard，卡片内是 Tags/Authors/Comics
/// 三档切换与条形图。数据源与 Classic 画风的 [ImageFavorites] 完全一致
/// （同样是 `ImageFavoriteManager.computeImageFavorites`），只是容器换成卡片。
///
/// 注意：本 State 的 `context` 在 `withMiuixTheme` **之外**，所以这里一律取
/// MD3 的 `Theme.of(context).colorScheme`；Miuix 取色只交给被包在里面的子组件
/// （MiuixCard / [_MiuixSectionHeader]）。
class _MiuixImageFavorites extends StatefulWidget {
  const _MiuixImageFavorites();

  @override
  State<_MiuixImageFavorites> createState() => _MiuixImageFavoritesState();
}

class _MiuixImageFavoritesState extends State<_MiuixImageFavorites> {
  ImageFavoritesComputed? _computed;

  int _displayType = 0;

  void _refresh() async {
    try {
      final data = await ImageFavoriteManager.computeImageFavorites();
      if (mounted) {
        setState(() => _computed = data);
      }
    } catch (e, stackTrace) {
      Log.error("Unhandled Exception", e.toString(), stackTrace);
    }
  }

  @override
  void initState() {
    super.initState();
    _refresh();
    ImageFavoriteManager().addListener(_refresh);
  }

  @override
  void dispose() {
    ImageFavoriteManager().removeListener(_refresh);
    super.dispose();
  }

  /// 切档后把主页滚到底，让条形图整体可见（与原版行为一致）。
  void _switchType(int type) async {
    setState(() => _displayType = type);
    await Future.delayed(const Duration(milliseconds: 20));
    if (!mounted) return;
    final controller = ScrollState.of(context).controller;
    controller.animateTo(
      controller.position.maxScrollExtent,
      duration: const Duration(milliseconds: 200),
      curve: Curves.ease,
    );
  }

  void _openPage([String? keyword]) {
    context.to(
      () => keyword == null
          ? const ImageFavoritesPage()
          : ImageFavoritesPage(initialKeyword: keyword),
    );
  }

  @override
  Widget build(BuildContext context) {
    final data = _computed;
    final hasData = data != null && !data.isEmpty;

    // 深色下 Miuix 默认卡片是纯灰 #242424，与 app 偏蓝黑的背景色温不搭，
    // 与 [_MiuixLocal] 保持同一处理。
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cardColors = isDark
        ? MiuixCardColors(
            color: context.colorScheme.surfaceContainerHigh,
            contentColor: context.colorScheme.onSurface,
          )
        : null;
    final subtitleColor = context.colorScheme.onSurfaceVariant;

    return withMiuixTheme(
      context,
      SliverToBoxAdapter(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _MiuixSectionHeader(
              "Image Favorites".tl,
              onTap: _openPage,
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: MiuixCard(
                cornerRadius: 16,
                colors: cardColors,
                insideMargin: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 12,
                ),
                onPressed: _openPage,
                feedbackType: MiuixPressFeedbackType.sink,
                child: hasData
                    ? Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            "Calculate your favorite from @a comics and @b images"
                                .tlParams({
                              'a': data.comics.length,
                              'b': data.count,
                            }),
                            style: TextStyle(
                              fontSize: 12,
                              color: subtitleColor,
                            ),
                          ),
                          const SizedBox(height: 12),
                          Row(
                            children: [
                              _typeButton(0, "Tags".tl),
                              const SizedBox(width: 8),
                              _typeButton(1, "Authors".tl),
                              const SizedBox(width: 8),
                              _typeButton(2, "Comics".tl),
                            ],
                          ),
                          const SizedBox(height: 12),
                          _chart(switch (_displayType) {
                            0 => data.tags,
                            1 => data.authors,
                            _ => data.comics,
                          }),
                        ],
                      )
                    : _computed == null
                        // 首次计算（收藏数 >100 时走 Isolate）期间留白占位，
                        // 避免闪一下「暂无收藏」再跳成条形图。
                        ? const SizedBox(height: 20)
                        : Text(
                            "No image favorites yet".tl,
                            style: TextStyle(
                              fontSize: 13,
                              color: subtitleColor,
                            ),
                          ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _typeButton(int type, String text) {
    final selected = _displayType == type;
    final cs = context.colorScheme;
    return Expanded(
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: () => _switchType(type),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          height: 32,
          decoration: BoxDecoration(
            color: selected ? cs.primaryContainer : cs.surfaceContainerHigh,
            borderRadius: BorderRadius.circular(20),
          ),
          child: Center(
            child: Text(
              text,
              style: TextStyle(
                fontSize: 13,
                fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
                color: selected ? cs.onPrimaryContainer : cs.onSurfaceVariant,
              ),
            ),
          ),
        ),
      ),
    );
  }

  /// 条形图直接复用 Classic 画风的 [_ChartLine]（纯主题色渲染，与画风无关）。
  Widget _chart(List<TextWithCount> data) {
    if (data.isEmpty) {
      return const SizedBox.shrink();
    }
    final maxCount = data.map((e) => e.count).reduce((a, b) => a > b ? a : b);
    return ConstrainedBox(
      constraints: const BoxConstraints(maxHeight: 164),
      child: SingleChildScrollView(
        child: Column(
          key: ValueKey(_displayType),
          children: [
            for (final e in data)
              _ChartLine(
                text: e.text,
                count: e.count,
                maxCount: maxCount,
                enableTranslation: _displayType != 2,
                onTap: _openPage,
              ),
          ],
        ),
      ),
    );
  }
}
