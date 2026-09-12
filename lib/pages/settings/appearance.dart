part of 'settings_page.dart';

/// 外观设置页。
///
/// Miuix 画风下仿 HyperOS「主题设置」布局：顶部手机预览模型（实时跟随
/// 当前主题）→ 主题模式三段分段 → 分组开关卡片。Classic 画风保持
/// 原有的 SelectSetting 列表不变。
class AppearanceSettings extends StatefulWidget {
  const AppearanceSettings({super.key});

  @override
  State<AppearanceSettings> createState() => _AppearanceSettingsState();
}

class _AppearanceSettingsState extends State<AppearanceSettings> {
  void _set(String key, dynamic value, {bool reinitColor = false}) {
    appdata.settings[key] = value;
    appdata.saveData();
    if (reinitColor) {
      App.init().then((_) => App.forceRebuild());
    } else {
      App.forceRebuild();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!useMiuixStyle) {
      return _buildClassic();
    }
    final navBarStyle = appdata.settings['navBarStyle'];
    return SmoothCustomScrollView(
      slivers: [
        SliverAppbar(title: Text("Appearance".tl)),
        const SliverPadding(padding: EdgeInsets.only(top: 8)),
        // 手机预览模型：实时渲染当前主题模式 + 主题色。
        const SliverLazyToBoxAdapter(child: _ThemePreview()),
        _section("Theme Mode".tl),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: MiuixTabRow(
              tabs: ["System".tl, "Light".tl, "Dark".tl],
              selectedTabIndex: switch (appdata.settings['theme_mode']) {
                'light' => 1,
                'dark' => 2,
                _ => 0,
              },
              colors: translucentTabRowColors(context),
              onTabSelected: (i) {
                _set('theme_mode', switch (i) {
                  1 => 'light',
                  2 => 'dark',
                  _ => 'system',
                });
              },
            ),
          ),
        ),
        _section("Theme Color".tl),
        SliverToBoxAdapter(child: _buildColorCard()),
        _section("Navigation Bar".tl),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: MiuixTabRow(
              tabs: ["Classic".tl, "MD3".tl, "Liquid Glass".tl],
              selectedTabIndex: switch (navBarStyle) {
                'md3' => 1,
                'floating' => 2,
                _ => 0,
              },
              colors: translucentTabRowColors(context),
              onTabSelected: (i) {
                _set('navBarStyle', switch (i) {
                  1 => 'md3',
                  2 => 'floating',
                  _ => 'classic',
                });
              },
            ),
          ),
        ),
        // 「Settings Style / Settings Entry」两个开关已移除：
        // 设置页已全面卡片化（miuix 画风），classic 设置页不再提供切换入口；
        // 设置入口位置沿用既有存储值。
        _section("Immersive Background".tl),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: MiuixTabRow(
              tabs: ["Off".tl, "Ambient".tl, "Wallpaper".tl],
              selectedTabIndex: switch (appdata.settings['backgroundMode']) {
                'ambient' => 1,
                'wallpaper' => 2,
                _ => 0,
              },
              colors: translucentTabRowColors(context),
              onTabSelected: (i) {
                _set('backgroundMode', switch (i) {
                  1 => 'ambient',
                  2 => 'wallpaper',
                  _ => 'off',
                });
              },
            ),
          ),
        ),
        if (appdata.settings['backgroundMode'] == 'wallpaper') ...[
          const SliverPadding(padding: EdgeInsets.only(top: 8)),
          SliverToBoxAdapter(child: _buildWallpaperCard()),
          SliverToBoxAdapter(child: _buildBlurCard()),
        ],
        const SliverPadding(padding: EdgeInsets.only(bottom: 24)),
      ],
    );
  }

  /// Classic 画风：原有 SelectSetting 列表，保持不变。
  Widget _buildClassic() {
    return SmoothCustomScrollView(
      slivers: [
        SliverAppbar(title: Text("Appearance".tl)),
        SelectSetting(
          title: "Theme Mode".tl,
          settingKey: "theme_mode",
          optionTranslation: {
            "system": "System".tl,
            "light": "Light".tl,
            "dark": "Dark".tl,
          },
          onChanged: () async {
            App.forceRebuild();
          },
        ).toSliver(),
        SelectSetting(
          title: "Theme Color".tl,
          settingKey: "color",
          optionTranslation: {
            "system": "System".tl,
            "red": "Red".tl,
            "pink": "Pink".tl,
            "purple": "Purple".tl,
            "green": "Green".tl,
            "orange": "Orange".tl,
            "blue": "Blue".tl,
          },
          onChanged: () async {
            await App.init();
            App.forceRebuild();
          },
        ).toSliver(),
        SelectSetting(
          title: "Navigation Bar".tl,
          settingKey: "navBarStyle",
          optionTranslation: {
            "classic": "Classic".tl,
            "floating": "Floating Glass".tl,
            "frosted": "Frosted".tl,
          },
          onChanged: () async {
            App.forceRebuild();
          },
        ).toSliver(),
        SelectSetting(
          title: "Immersive Background".tl,
          settingKey: "backgroundMode",
          optionTranslation: {
            "off": "Off".tl,
            "ambient": "Ambient".tl,
            "wallpaper": "Wallpaper".tl,
          },
          onChanged: () async {
            App.forceRebuild();
          },
        ).toSliver(),
        if (appdata.settings['backgroundMode'] == 'wallpaper') ...[
          SliverToBoxAdapter(
            child: ListTile(
              title: Text("Custom Wallpaper".tl),
              subtitle: Text(_wallpaperSubtitle(), maxLines: 1),
              trailing: IconButton(
                icon: const Icon(Icons.image_outlined),
                onPressed: _pickWallpaper,
              ),
              onTap: _pickWallpaper,
            ),
          ),
          SelectSetting(
            title: "Blur Strength".tl,
            settingKey: "wallpaperBlur",
            optionTranslation: {
              "0": "0",
              "8": "8",
              "16": "16",
              "24": "24",
              "32": "32",
              "40": "40",
            },
            onChanged: () async {
              App.forceRebuild();
            },
          ).toSliver(),
        ],
      ],
    );
  }

  String _wallpaperSubtitle() {
    final path = appdata.settings['wallpaperPath'] as String? ?? '';
    if (path.isEmpty) {
      return "Tap to select an image".tl;
    }
    return Uri.file(path).pathSegments.last;
  }

  /// 选择自定义壁纸：复制到应用数据目录持久化（换图时清理旧文件，
  /// 文件名带时间戳规避图片缓存）。
  Future<void> _pickWallpaper() async {
    var f = await selectFile(ext: ['png', 'jpg', 'jpeg', 'webp']);
    if (f == null) return;
    final old = appdata.settings['wallpaperPath'] as String? ?? '';
    if (old.isNotEmpty) {
      File(old).deleteIgnoreError();
    }
    final ext = f.path.split('.').last.toLowerCase();
    final newPath =
        '${App.dataPath}/wallpaper_${DateTime.now().millisecondsSinceEpoch}.$ext';
    await File(f.path).copy(newPath);
    if (!mounted) return;
    setState(() {
      appdata.settings['wallpaperPath'] = newPath;
    });
    appdata.saveData();
  }

  void _clearWallpaper() {
    final old = appdata.settings['wallpaperPath'] as String? ?? '';
    if (old.isNotEmpty) {
      File(old).deleteIgnoreError();
    }
    setState(() {
      appdata.settings['wallpaperPath'] = '';
    });
    appdata.saveData();
  }

  Widget _section(String text) {
    return SliverToBoxAdapter(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 20, 16, 8),
        child: Text(
          text,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: context.colorScheme.secondary,
          ),
        ),
      ),
    );
  }

  /// 主题颜色卡片：标题 + 一排圆形色点，点选即换（替代原下拉选择）。
  Widget _buildColorCard() {
    const options = [
      ('system', null),
      ('red', Colors.red),
      ('pink', Colors.pink),
      ('purple', Colors.purple),
      ('green', Colors.green),
      ('orange', Colors.orange),
      ('blue', Colors.blue),
    ];
    final current = appdata.settings['color'];
    final cs = context.colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8),
      child: MiuixCard(
        cornerRadius: 16,
        insideMargin:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        feedbackType: MiuixPressFeedbackType.sink,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                _IconBadge(icon: Icons.palette_outlined),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        "Theme Color".tl,
                        style: const TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        options
                            .firstWhere((e) => e.$1 == current)
                            .$1
                            .tl,
                        style: TextStyle(
                          fontSize: 12,
                          color: cs.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 12,
              runSpacing: 10,
              children: [
                for (var (key, color) in options)
                  _ColorDot(
                    color: color,
                    selected: current == key,
                    onTap: () {
                      if (current != key) {
                        _set('color', key, reinitColor: true);
                      }
                    },
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  /// 壁纸卡片：点按选图，已设置时显示缩略图 + 清除按钮。
  Widget _buildWallpaperCard() {
    final path = appdata.settings['wallpaperPath'] as String? ?? '';
    final file = path.isNotEmpty ? File(path) : null;
    final exists = file != null && file.existsSync();
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: MiuixCard(
        cornerRadius: 16,
        insideMargin:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        onPressed: _pickWallpaper,
        feedbackType: MiuixPressFeedbackType.sink,
        child: Row(
          children: [
            const _IconBadge(icon: Icons.image_outlined),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    "Custom Wallpaper".tl,
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    _wallpaperSubtitle(),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 12,
                      color: context.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
            if (exists) ...[
              const SizedBox(width: 8),
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: Image(
                  image: ResizeImage(
                    FileImage(file),
                    width: 88,
                    policy: ResizeImagePolicy.exact,
                  ),
                  width: 44,
                  height: 44,
                  fit: BoxFit.cover,
                ),
              ),
              IconButton(
                icon: const Icon(Icons.close, size: 18),
                onPressed: _clearWallpaper,
              ),
            ],
          ],
        ),
      ),
    );
  }

  /// 模糊强度滑条卡片（0~40σ，实时生效）。
  Widget _buildBlurCard() {
    final sigma =
        (double.tryParse(appdata.settings['wallpaperBlur']?.toString() ?? '') ?? 24)
            .clamp(0.0, 40.0);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: MiuixCard(
        cornerRadius: 16,
        insideMargin:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const _IconBadge(icon: Icons.blur_on),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    "Blur Strength".tl,
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
                Text(
                  "${sigma.round()} px",
                  style: TextStyle(
                    fontSize: 12,
                    color: context.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
            MiuixSlider(
              value: sigma / 40,
              onValueChanged: (v) {
                setState(() {
                  appdata.settings['wallpaperBlur'] =
                      ((v * 40).round()).toString();
                });
              },
              onValueChangeFinished: () => appdata.saveData(),
            ),
          ],
        ),
      ),
    );
  }
}

/// 卡片左侧的小图标徽章（圆角方块 + 主题色图标）。
class _IconBadge extends StatelessWidget {
  const _IconBadge({required this.icon});

  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final cs = context.colorScheme;
    return Container(
      width: 34,
      height: 34,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(10),
        color: cs.primary.withValues(alpha: 0.14),
      ),
      child: Icon(icon, size: 18, color: cs.primary),
    );
  }
}

/// 主题色圆形色点。system 选项画调色板图标，其余纯色填充；
/// 选中时画一圈外环。
class _ColorDot extends StatelessWidget {
  const _ColorDot({
    required this.color,
    required this.selected,
    required this.onTap,
  });

  final Color? color;

  final bool selected;

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = context.colorScheme;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 30,
        height: 30,
        padding: const EdgeInsets.all(2),
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          border: Border.all(
            color: selected ? cs.onSurface : Colors.transparent,
            width: 2,
          ),
        ),
        child: Container(
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: color ?? cs.surfaceContainerHighest,
          ),
          child: color == null
              ? Icon(Icons.palette_outlined,
                  size: 14, color: cs.onSurfaceVariant)
              : null,
        ),
      ),
    );
  }
}

/// 顶部手机预览模型：圆角手机壳内用容器画出 venera 的迷你布局
/// （标题 + 色块内容区 + 底栏胶囊），颜色实时跟随当前主题。
class _ThemePreview extends StatelessWidget {
  const _ThemePreview();

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Center(
      child: Container(
        width: 196,
        height: 296,
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(28),
          color: dark ? const Color(0xFF1A1A1C) : const Color(0xFFEDEDED),
          border: Border.all(
            color: dark ? Colors.white.withValues(alpha: 0.12) : Colors.black.withValues(alpha: 0.15),
            width: 1.2,
          ),
        ),
        child: Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            color: cs.surface,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 应用名 + 状态栏小点
              Row(
                children: [
                  Expanded(
                    child: Text(
                      "venera",
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: cs.onSurface,
                      ),
                    ),
                  ),
                  Container(
                    width: 5,
                    height: 5,
                    decoration:
                        BoxDecoration(shape: BoxShape.circle, color: cs.primary),
                  ),
                  const SizedBox(width: 4),
                  Container(
                    width: 12,
                    height: 5,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(3),
                      color: cs.onSurface.withValues(alpha: 0.25),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              // 内容区：色块网格（模仿漫画卡片）
              Expanded(
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Column(
                        children: [
                          _block(cs.primary, height: 74),
                          const SizedBox(height: 8),
                          _block(cs.surfaceContainerHigh, height: 60),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        children: [
                          _block(cs.surfaceContainerHigh, height: 52),
                          const SizedBox(height: 8),
                          _block(cs.surfaceContainerHigh, height: 82),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 10),
              // 底栏胶囊：4 个点，第 1 个为主色高亮
              Container(
                height: 26,
                padding: const EdgeInsets.symmetric(horizontal: 10),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(13),
                  color: cs.surfaceContainerHigh,
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: [
                    for (var i = 0; i < 4; i++)
                      Container(
                        width: 6,
                        height: 6,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: i == 0
                              ? cs.primary
                              : cs.onSurface.withValues(alpha: 0.3),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _block(Color color, {required double height}) {
    return Container(
      width: double.infinity,
      height: height,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8),
        color: color.withValues(alpha: 0.85),
      ),
    );
  }
}
