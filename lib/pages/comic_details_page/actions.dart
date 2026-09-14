part of 'comic_page.dart';

abstract mixin class _ComicPageActions {
  void update();

  ComicDetails get comic;

  ComicSource get comicSource => ComicSource.find(comic.sourceKey)!;

  History? get history;

  bool isLiking = false;

  bool isLiked = false;

  void likeOrUnlike() async {
    if (isLiking) return;
    isLiking = true;
    update();
    var res = await comicSource.likeOrUnlikeComic!(comic.id, isLiked);
    if (res.error) {
      App.rootContext.showMessage(message: res.errorMessage!);
    } else {
      isLiked = !isLiked;
    }
    isLiking = false;
    update();
  }

  /// whether the comic is added to local favorite
  bool isAddToLocalFav = false;

  /// whether the comic is favorite on the server
  bool isFavorite = false;

  FavoriteItem _toFavoriteItem() {
    var tags = <String>[];
    for (var e in comic.tags.entries) {
      tags.addAll(e.value.map((tag) => '${e.key}:$tag'));
    }
    return FavoriteItem(
      id: comic.id,
      name: comic.title,
      coverPath: comic.cover,
      author: comic.subTitle ?? comic.uploader ?? '',
      type: comic.comicType,
      tags: tags,
    );
  }

  void openFavPanel() {
    showSideBar(
      App.rootContext,
      _FavoritePanel(
        cid: comic.id,
        type: comic.comicType,
        isFavorite: isFavorite,
        onFavorite: (local, network) {
          if (network != null) {
            isFavorite = network;
          }
          if (local != null) {
            isAddToLocalFav = local;
          }
          update();
        },
        favoriteItem: _toFavoriteItem(),
        updateTime: comic.findUpdateTime(),
      ),
    );
  }

  void quickFavorite() {
    var folder = appdata.settings['quickFavorite'];
    if (folder is! String) {
      return;
    }
    LocalFavoritesManager().addComic(
      folder,
      _toFavoriteItem(),
      null,
      comic.findUpdateTime(),
    );
    isAddToLocalFav = true;
    update();
    App.rootContext.showMessage(message: "Added".tl);
  }

  void share() {
    var text = comic.title;
    var link = comic.url;
    if (link == null || link.isEmpty) {
      // 部分源插件的 ComicDetails 不带 url。注意 comicSource.url 是插件
      // JS 文件的下载地址（cdn.jsdelivr.net/...），并非站点域名，不能
      // 用来拼链接；只对详情页路径规则确定的源做映射，其余源不拼出
      // 假链接（退化为仅分享标题）。
      final key = comicSource.key.toLowerCase();
      final id = comic.id;
      link = switch (key) {
        'nhentai' => 'https://nhentai.net/g/$id/',
        _ when key.contains('18comic') || key.contains('jm') =>
          'https://18comic.vip/album/$id',
        _ => '',
      };
    }
    if (link.isNotEmpty) {
      text += '\n$link';
    }
    Share.shareText(text);
  }

  /// read the comic
  ///
  /// [ep] the episode number, start from 1
  ///
  /// [page] the page number, start from 1
  ///
  /// [group] the chapter group number, start from 1
  ///
  /// [previewPlaceholder] 预览卡片那张图（由预览卡传入）。图集要等
  /// loadComicPages 返回才构建，阅读器首帧只有它可显示；不给就会在转场落地
  /// 时「闪一下再转圈」。
  void read([int? ep, int? page, int? group, Widget? previewPlaceholder]) {
    // 推到「主内容区」的内嵌 Navigator（与详情页同栈），而不是根 Navigator。
    // Hero 飞行只由发起转场的那个 Navigator 的 HeroController 驱动：详情页
    // 本身挂在内嵌栈上（comic.dart:84 用 mainNavigatorKey 推入），阅读器必须
    // 同栈，预览卡片 ⇄ 阅读器的 Hero 才会飞（与页内 cover→CoverViewer 完全
    // 同一机制）。mainNavigatorKey 尚未就绪时退回根 Navigator，行为同旧版。
    (App.mainNavigatorKey?.currentContext ?? App.rootContext)
        .to(
      () => Reader(
        type: comic.comicType,
        cid: comic.id,
        name: comic.title,
        chapters: comic.chapters,
        initialChapter: ep,
        initialPage: page,
        initialChapterGroup: group,
        history: history ?? History.fromModel(model: comic, ep: 0, page: 0),
        author: comic.findAuthor() ?? '',
        tags: comic.plainTags,
        previewPlaceholder: previewPlaceholder,
      ),
      // 返回时由预览卡 ⇄ 阅读器的共享元素（Hero）跟手缩回，页面本身不缩放
      // —— 否则阅读器的深色满屏会被 Android 共享元素转场缩成圆角卡片，
      // 在图片已经缩回去的同时露出一大块深色底（详情见 app_page_route.dart）。
      sharedElementPopTransition: true,
      // 光学·径向转场（demo 规格）：打开 620ms；普通返回（非手势）收尾
      // 300ms smoothstep，手势松手的收尾时长由 _SharedElementPopRestore
      // 按 demo 公式驱动。
      transitionDuration: kPreviewFlightDuration,
      reverseTransitionDuration: const Duration(milliseconds: 300),
    )
        .then((_) {
      onReadEnd();
    });
  }

  void continueRead() {
    var ep = history?.ep ?? 1;
    var page = history?.page ?? 1;
    var group = history?.group ?? 1;
    read(ep, page, group);
  }

  void onReadEnd();

  void download() async {
    if (LocalManager().isDownloading(comic.id, comic.comicType)) {
      App.rootContext.showMessage(message: "The comic is downloading".tl);
      return;
    }
    if (comic.chapters == null &&
        LocalManager().isDownloaded(comic.id, comic.comicType, 0)) {
      App.rootContext.showMessage(message: "The comic is downloaded".tl);
      return;
    }

    if (comicSource.archiveDownloader != null) {
      bool useNormalDownload = false;
      List<ArchiveInfo>? archives;
      int selected = -1;
      bool isLoading = false;
      bool isGettingLink = false;
      await showDialog(
        context: App.rootContext,
        builder: (context) {
          return StatefulBuilder(
            builder: (context, setState) {
              return ContentDialog(
                title: "Download".tl,
                content: RadioGroup<int>(
                  groupValue: selected,
                  onChanged: (v) {
                    setState(() {
                      selected = v ?? selected;
                    });
                  },
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      RadioListTile<int>(
                        value: -1,
                        title: Text("Normal".tl),
                      ),
                      ExpansionTile(
                        title: Text("Archive".tl),
                        shape: const RoundedRectangleBorder(
                          borderRadius: BorderRadius.zero,
                        ),
                        collapsedShape: const RoundedRectangleBorder(
                          borderRadius: BorderRadius.zero,
                        ),
                        onExpansionChanged: (b) {
                          if (!isLoading && b && archives == null) {
                            isLoading = true;
                            comicSource.archiveDownloader!
                                .getArchives(comic.id)
                                .then((value) {
                              if (value.success) {
                                archives = value.data;
                              } else {
                                App.rootContext
                                    .showMessage(message: value.errorMessage!);
                              }
                              setState(() {
                                isLoading = false;
                              });
                            });
                          }
                        },
                        children: [
                          if (archives == null)
                            const ListLoadingIndicator().toCenter()
                          else
                            for (int i = 0; i < archives!.length; i++)
                              RadioListTile<int>(
                                value: i,
                                title: Text(archives![i].title),
                                subtitle: Text(archives![i].description),
                              )
                        ],
                      )
                    ],
                  ),
                ),
                actions: [
                  Button.filled(
                    isLoading: isGettingLink,
                    onPressed: () async {
                      if (selected == -1) {
                        useNormalDownload = true;
                        context.pop();
                        return;
                      }
                      setState(() {
                        isGettingLink = true;
                      });
                      var res =
                          await comicSource.archiveDownloader!.getDownloadUrl(
                        comic.id,
                        archives![selected].id,
                      );
                      if (res.error) {
                        App.rootContext.showMessage(message: res.errorMessage!);
                        setState(() {
                          isGettingLink = false;
                        });
                      } else if (context.mounted) {
                        if (res.data.isNotEmpty) {
                          LocalManager()
                            .addTask(ArchiveDownloadTask(res.data, comic));
                          App.rootContext
                            .showMessage(message: "Download started".tl);
                        }
                        context.pop();
                      }
                    },
                    child: Text("Confirm".tl),
                  ),
                ],
              );
            },
          );
        },
      );
      if (!useNormalDownload) {
        return;
      }
    }

    if (comic.chapters == null) {
      LocalManager().addTask(ImagesDownloadTask(
        source: comicSource,
        comicId: comic.id,
        comic: comic,
      ));
    } else {
      List<int>? selected;
      var downloaded = <int>[];
      var localComic = LocalManager().find(comic.id, comic.comicType);
      if (localComic != null) {
        for (int i = 0; i < comic.chapters!.length; i++) {
          if (localComic.downloadedChapters
              .contains(comic.chapters!.ids.elementAt(i))) {
            downloaded.add(i);
          }
        }
      }
      await showSideBar(
        App.rootContext,
        _SelectDownloadChapter(
          comic.chapters!.titles.toList(),
          (v) => selected = v,
          downloaded,
        ),
      );
      if (selected == null) return;
      LocalManager().addTask(ImagesDownloadTask(
        source: comicSource,
        comicId: comic.id,
        comic: comic,
        chapters: selected!.map((i) {
          return comic.chapters!.ids.elementAt(i);
        }).toList(),
      ));
    }
    App.rootContext.showMessage(message: "Download started".tl);
    update();
  }

  void onTapTag(String tag, String namespace) {
    // 点击标签 = 「在这个源里搜这个词」→ 跳**单源搜索结果页**
    // （SearchResultPage）。不要跳 AggregatedSearchPage：那是多源聚合页，
    // 会把所有已启用源一起搜一遍，与点标签的意图不符。
    //
    // 关键词就是标签名本身（纯文本），**不拼 `tag:` 精确过滤词**：
    // 标签在搜索框里显示成 `tag:"big breasts"` 很费解，而且多数源不支持
    // 标签语法、拼过去只会搜出垃圾。用户点标签的预期就是「搜这个词」。
    // 需要精确过滤时可以在结果页用「＋ Add Tag」追加条件（走 tag: 过滤）。
    App.mainNavigatorKey!.currentContext!.to(
      () => SearchResultPage(
        text: tag,
        sourceKey: comicSource.key,
      ),
    );
  }

  void showMoreActions() {
    var context = App.rootContext;
    showMenuX(
        context,
        Offset(
          context.width - 16,
          context.padding.top,
        ),
        [
          MenuEntry(
            icon: Icons.copy,
            text: "Copy Title".tl,
            onClick: () {
              Clipboard.setData(ClipboardData(text: comic.title));
              context.showMessage(message: "Copied".tl);
            },
          ),
          MenuEntry(
            icon: Icons.copy_rounded,
            text: "Copy ID".tl,
            onClick: () {
              Clipboard.setData(ClipboardData(text: comic.id));
              context.showMessage(message: "Copied".tl);
            },
          ),
          if (comic.url != null)
            MenuEntry(
              icon: Icons.link,
              text: "Copy URL".tl,
              onClick: () {
                Clipboard.setData(ClipboardData(text: comic.url!));
                context.showMessage(message: "Copied".tl);
              },
            ),
          if (comic.url != null)
            MenuEntry(
              icon: Icons.open_in_browser,
              text: "Open in Browser".tl,
              onClick: () {
                launchUrlString(comic.url!);
              },
            ),
        ]);
  }

  void showComments() {
    showSideBar(
      App.rootContext,
      CommentsPage(
        data: comic,
        source: comicSource,
      ),
    );
  }

  void starRating() {
    if (!comicSource.isLogged) {
      return;
    }
    var rating = 0.0;
    var isLoading = false;
    showDialog(
      context: App.rootContext,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setState) => SimpleDialog(
          title: const Text("Rating"),
          alignment: Alignment.center,
          children: [
            SizedBox(
              height: 100,
              child: Center(
                child: SizedBox(
                  width: 210,
                  child: Column(
                    children: [
                      const SizedBox(
                        height: 10,
                      ),
                      RatingWidget(
                        padding: 2,
                        onRatingUpdate: (value) => rating = value,
                        value: 1,
                        selectable: true,
                        size: 40,
                      ),
                      const Spacer(),
                      Button.filled(
                        isLoading: isLoading,
                        onPressed: () {
                          setState(() {
                            isLoading = true;
                          });
                          comicSource.starRatingFunc!(comic.id, rating.round())
                              .then((value) {
                            if (value.success) {
                              App.rootContext
                                  .showMessage(message: "Success".tl);
                              Navigator.of(dialogContext).pop();
                            } else {
                              App.rootContext
                                  .showMessage(message: value.errorMessage!);
                              setState(() {
                                isLoading = false;
                              });
                            }
                          });
                        },
                        child: Text("Submit".tl),
                      )
                    ],
                  ),
                ),
              ),
            )
          ],
        ),
      ),
    );
  }
}
