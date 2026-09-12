part of 'comic_page.dart';

class _CommentsPart extends StatefulWidget {
  const _CommentsPart({
    required this.comments,
    required this.showMore,
  });

  final List<Comment> comments;

  final void Function() showMore;

  @override
  State<_CommentsPart> createState() => _CommentsPartState();
}

class _CommentsPartState extends State<_CommentsPart> {
  final scrollController = ScrollController();

  late List<Comment> comments;

  @override
  void initState() {
    comments = widget.comments.where((c) => !_shouldBlockComment(c)).toList();
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    final header = ListTile(
      title: Text("Comments".tl),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton(
            icon: const Icon(Icons.chevron_left),
            onPressed: () {
              scrollController.animateTo(
                scrollController.position.pixels - 340,
                duration: const Duration(milliseconds: 200),
                curve: Curves.ease,
              );
            },
          ),
          IconButton(
            icon: const Icon(Icons.chevron_right),
            onPressed: () {
              scrollController.animateTo(
                scrollController.position.pixels + 340,
                duration: const Duration(milliseconds: 200),
                curve: Curves.ease,
              );
            },
          ),
        ],
      ),
    );
    final list = SizedBox(
      height: 184,
      child: MediaQuery.removePadding(
        removeTop: true,
        context: context,
        child: ListView.builder(
          controller: scrollController,
          scrollDirection: Axis.horizontal,
          itemCount: comments.length,
          itemBuilder: (context, index) {
            return _CommentWidget(comment: comments[index]);
          },
        ),
      ),
    );
    final viewMore = _ActionButton(
      icon: const Icon(Icons.comment),
      text: "View more".tl,
      onPressed: widget.showMore,
      iconColor: context.useTextColor(Colors.green),
    ).fixHeight(48).paddingRight(8).toAlign(Alignment.centerRight);

    // miuix：标题 + 横向评论 + 查看更多整节进一张卡，无独立 Divider。
    if (useMiuixStyle) {
      return SliverToBoxAdapter(
        child: _ComicSectionCard(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              header,
              list,
              const SizedBox(height: 4),
              viewMore,
              const SizedBox(height: 8),
            ],
          ),
        ),
      );
    }
    return MultiSliver(
      children: [
        SliverLazyToBoxAdapter(child: header),
        SliverToBoxAdapter(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              list,
              const SizedBox(height: 8),
              viewMore,
              const SizedBox(height: 8),
            ],
          ),
        ),
        const SliverToBoxAdapter(
          child: Divider(),
        ),
      ],
    );
  }
}

class _CommentWidget extends StatelessWidget {
  const _CommentWidget({required this.comment});

  final Comment comment;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: double.infinity,
      margin: const EdgeInsets.fromLTRB(16, 8, 0, 8),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      width: 324,
      decoration: BoxDecoration(
        color: context.colorScheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        children: [
          Row(
            children: [
              if (comment.avatar != null)
                Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(18),
                    color: context.colorScheme.surfaceContainer,
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: AnimatedImage(
                    image: CachedImageProvider(comment.avatar!),
                    width: 36,
                    height: 36,
                    fit: BoxFit.cover,
                    // 头像显示 36 逻辑像素，限解码宽防大图整张解码。
                    cacheWidth:
                        (36 * MediaQuery.devicePixelRatioOf(context)).round(),
                  ),
                ).paddingRight(8),
              Text(comment.userName, style: ts.bold),
            ],
          ),
          const SizedBox(height: 4),
          Expanded(
            child: RichCommentContent(
              text: comment.content,
              showImages: false,
            ).fixWidth(324),
          ),
          const SizedBox(height: 4),
          if (comment.time != null)
            Text(comment.time!, style: ts.s12).toAlign(Alignment.centerLeft),
        ],
      ),
    );
  }
}
