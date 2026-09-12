part of 'settings_page.dart';

class ReaderSettings extends StatefulWidget {
  const ReaderSettings({
    super.key,
    this.onChanged,
    this.comicId,
    this.comicSource,
  });

  final void Function(String key)? onChanged;
  final String? comicId;
  final String? comicSource;

  @override
  State<ReaderSettings> createState() => _ReaderSettingsState();
}

class _ReaderSettingsState extends State<ReaderSettings> {
  bool _isChapterCommentsAtEndSupported() {
    String? readerMode;
    bool? showChapterComments;

    if (widget.comicId != null &&
        widget.comicSource != null &&
        appdata.settings.isComicSpecificSettingsEnabled(
          widget.comicId,
          widget.comicSource,
        )) {
      readerMode = appdata.settings.getReaderSetting(
        widget.comicId!,
        widget.comicSource!,
        'readerMode',
      );
      showChapterComments = appdata.settings.getReaderSetting(
        widget.comicId!,
        widget.comicSource!,
        'showChapterComments',
      );
    } else {
      readerMode = appdata.settings['readerMode'] as String?;
      showChapterComments = appdata.settings['showChapterComments'] as bool?;
    }

    // Must have showChapterComments enabled and be in gallery mode
    if (showChapterComments != true) return false;

    return readerMode == 'galleryLeftToRight' ||
        readerMode == 'galleryRightToLeft';
  }

  void _onShowChapterCommentsChanged() {
    // When showChapterComments is turned off, also turn off showChapterCommentsAtEnd
    bool? showChapterComments;

    if (widget.comicId != null &&
        widget.comicSource != null &&
        appdata.settings.isComicSpecificSettingsEnabled(
          widget.comicId,
          widget.comicSource,
        )) {
      showChapterComments = appdata.settings.getReaderSetting(
        widget.comicId!,
        widget.comicSource!,
        'showChapterComments',
      );
      if (showChapterComments != true) {
        appdata.settings.setReaderSetting(
          widget.comicId!,
          widget.comicSource!,
          'showChapterCommentsAtEnd',
          false,
        );
      }
    } else {
      showChapterComments = appdata.settings['showChapterComments'] as bool?;
      if (showChapterComments != true) {
        appdata.settings['showChapterCommentsAtEnd'] = false;
      }
    }

    setState(() {});
    widget.onChanged?.call("showChapterComments");
  }

  @override
  Widget build(BuildContext context) {
    final comicId = widget.comicId;
    final sourceKey = widget.comicSource;
    final key = "$comicId@$sourceKey";

    bool isEnabledSpecificSettings =
        comicId != null &&
        appdata.settings.isComicSpecificSettingsEnabled(comicId, sourceKey);
    bool useDeviceSpecificSettings =
        !isEnabledSpecificSettings &&
        appdata.settings.isDeviceSpecificSettingsEnabled();

    // 本页会被两个入口打开：设置页（外面已包 MiuixTheme）和**阅读器侧栏**
    // （`_ReaderScaffold.openSetting` 直接 showSideBar，没有任何包裹层）。
    // Miuix 组件在没有祖先时 `MiuixTheme.of` 回退到 `MiuixThemeData.light()`，
    // 深色模式下就是「纯黑文字压在近黑面板上」—— 整块设置几乎看不见。
    // 这里自己包一层，任何入口进来都拿到与 App 亮度一致的取色。
    return withMiuixTheme(
      context,
      _buildBody(context, comicId, sourceKey, key,
          isEnabledSpecificSettings, useDeviceSpecificSettings),
    );
  }

  Widget _buildBody(
    BuildContext context,
    String? comicId,
    String? sourceKey,
    String key,
    bool isEnabledSpecificSettings,
    bool useDeviceSpecificSettings,
  ) {
    final comicArgs = (
      comicId: isEnabledSpecificSettings ? widget.comicId : null,
      comicSource: isEnabledSpecificSettings ? widget.comicSource : null,
      useDeviceSettings: useDeviceSpecificSettings,
    );
    return SmoothCustomScrollView(
      slivers: [
        SliverAppbar(title: Text("Reading".tl)),
        // 漫画专属/设备专属设置的开关卡（原先的 SwitchListTile + Divider
        // 是 Material 残留，不跟 Miuix 画风）。
        if (comicId != null && sourceKey != null)
          SliverToBoxAdapter(
            child: SettingsSection(
              child: Column(
                children: [
                  _ReaderToggleRow(
                    title: "Enable comic specific settings".tl,
                    value: isEnabledSpecificSettings,
                    onChanged: (b) {
                      setState(() {
                        appdata.settings.setEnabledComicSpecificSettings(
                          comicId,
                          sourceKey,
                          b,
                        );
                      });
                    },
                  ),
                  if (isEnabledSpecificSettings)
                    Center(
                      child: TextButton(
                        onPressed: () {
                          setState(() {
                            appdata.settings.resetComicReaderSettings(key);
                          });
                        },
                        child: Text(
                          "Clear specific reader settings for this comic".tl,
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        if (comicId == null)
          SliverToBoxAdapter(
            child: SettingsSection(
              child: Column(
                children: [
                  _ReaderToggleRow(
                    title: "Enable device specific settings".tl,
                    value: useDeviceSpecificSettings,
                    onChanged: (b) {
                      setState(() {
                        appdata.settings.setEnabledDeviceSpecificSettings(b);
                      });
                      appdata.saveData();
                    },
                  ),
                  if (useDeviceSpecificSettings)
                    Center(
                      child: TextButton(
                        onPressed: () {
                          setState(() {
                            appdata.settings.resetDeviceReaderSettings();
                          });
                          appdata.saveData();
                        },
                        child: Text(
                          "Clear specific reader settings for this device".tl,
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        _SettingPartTitle(title: "Paging & Mode".tl, icon: Icons.auto_stories),
        SliverToBoxAdapter(
          child: SettingsSection(
            child: Column(
              children: [
                SelectSetting(
                  title: "Reading mode".tl,
                  settingKey: "readerMode",
                  optionTranslation: {
                    "galleryLeftToRight": "Gallery (Left to Right)".tl,
                    "galleryRightToLeft": "Gallery (Right to Left)".tl,
                    "galleryTopToBottom": "Gallery (Top to Bottom)".tl,
                    "continuousLeftToRight": "Continuous (Left to Right)".tl,
                    "continuousRightToLeft": "Continuous (Right to Left)".tl,
                    "continuousTopToBottom": "Continuous (Top to Bottom)".tl,
                  },
                  onChanged: () {
                    setState(() {});
                    var readerMode = appdata.settings['readerMode'];
                    if (readerMode?.toLowerCase().startsWith('continuous') ??
                        false) {
                      appdata.settings['readerScreenPicNumberForLandscape'] = 1;
                      widget.onChanged?.call('readerScreenPicNumberForLandscape');
                      appdata.settings['readerScreenPicNumberForPortrait'] = 1;
                      widget.onChanged?.call('readerScreenPicNumberForPortrait');
                    }
                    widget.onChanged?.call("readerMode");
                  },
                  comicId: comicArgs.comicId,
                  comicSource: comicArgs.comicSource,
                  useDeviceSettings: comicArgs.useDeviceSettings,
                ),
                _SwitchSetting(
                  title: "Tap to turn Pages".tl,
                  settingKey: "enableTapToTurnPages",
                  onChanged: () {
                    widget.onChanged?.call("enableTapToTurnPages");
                  },
                  comicId: comicArgs.comicId,
                  comicSource: comicArgs.comicSource,
                  useDeviceSettings: comicArgs.useDeviceSettings,
                ),
                _SwitchSetting(
                  title: "Reverse tap to turn Pages".tl,
                  settingKey: "reverseTapToTurnPages",
                  onChanged: () {
                    widget.onChanged?.call("reverseTapToTurnPages");
                  },
                  comicId: comicArgs.comicId,
                  comicSource: comicArgs.comicSource,
                  useDeviceSettings: comicArgs.useDeviceSettings,
                ),
                _SwitchSetting(
                  title: "Page animation".tl,
                  settingKey: "enablePageAnimation",
                  onChanged: () {
                    widget.onChanged?.call("enablePageAnimation");
                  },
                  comicId: comicArgs.comicId,
                  comicSource: comicArgs.comicSource,
                  useDeviceSettings: comicArgs.useDeviceSettings,
                ),
                _SliderSetting(
                  title: "Auto page turning interval".tl,
                  settingsIndex: "autoPageTurningInterval",
                  interval: 1,
                  min: 1,
                  max: 20,
                  onChanged: () {
                    setState(() {});
                    widget.onChanged?.call("autoPageTurningInterval");
                  },
                  comicId: comicArgs.comicId,
                  comicSource: comicArgs.comicSource,
                  useDeviceSettings: comicArgs.useDeviceSettings,
                ),
                if (appdata.settings['readerMode']!.startsWith('gallery')) ...[
                  _SliderSetting(
                    title:
                        "The number of pic in screen for landscape (Only Gallery Mode)"
                            .tl,
                    settingsIndex: "readerScreenPicNumberForLandscape",
                    interval: 1,
                    min: 1,
                    max: 5,
                    onChanged: () {
                      setState(() {});
                      widget.onChanged?.call("readerScreenPicNumberForLandscape");
                    },
                    comicId: comicArgs.comicId,
                    comicSource: comicArgs.comicSource,
                    useDeviceSettings: comicArgs.useDeviceSettings,
                  ),
                  _SliderSetting(
                    title:
                        "The number of pic in screen for portrait (Only Gallery Mode)"
                            .tl,
                    settingsIndex: "readerScreenPicNumberForPortrait",
                    interval: 1,
                    min: 1,
                    max: 5,
                    onChanged: () {
                      widget.onChanged?.call("readerScreenPicNumberForPortrait");
                    },
                    comicId: comicArgs.comicId,
                    comicSource: comicArgs.comicSource,
                    useDeviceSettings: comicArgs.useDeviceSettings,
                  ),
                  if (appdata.settings['readerScreenPicNumberForLandscape'] > 1 ||
                      appdata.settings['readerScreenPicNumberForPortrait'] > 1)
                    _SwitchSetting(
                      title: "Show single image on first page".tl,
                      settingKey: "showSingleImageOnFirstPage",
                      onChanged: () {
                        widget.onChanged?.call("showSingleImageOnFirstPage");
                      },
                      comicId: comicArgs.comicId,
                      comicSource: comicArgs.comicSource,
                      useDeviceSettings: comicArgs.useDeviceSettings,
                    ),
                ],
                if (appdata.settings['readerMode']!.startsWith('continuous'))
                  _SliderSetting(
                    title: "Mouse scroll speed".tl,
                    settingsIndex: "readerScrollSpeed",
                    interval: 0.1,
                    min: 0.5,
                    max: 3,
                    onChanged: () {
                      widget.onChanged?.call("readerScrollSpeed");
                    },
                    comicId: comicArgs.comicId,
                    comicSource: comicArgs.comicSource,
                    useDeviceSettings: comicArgs.useDeviceSettings,
                  ),
              ],
            ),
          ),
        ),
        _SettingPartTitle(title: "Zoom Gestures".tl, icon: Icons.zoom_in),
        SliverToBoxAdapter(
          child: SettingsSection(
            child: Column(
              children: [
                _SwitchSetting(
                  title: 'Double tap to zoom'.tl,
                  settingKey: 'enableDoubleTapToZoom',
                  onChanged: () {
                    setState(() {});
                    widget.onChanged?.call('enableDoubleTapToZoom');
                  },
                  comicId: comicArgs.comicId,
                  comicSource: comicArgs.comicSource,
                  useDeviceSettings: comicArgs.useDeviceSettings,
                ),
                _SwitchSetting(
                  title: 'Long press to zoom'.tl,
                  settingKey: 'enableLongPressToZoom',
                  onChanged: () {
                    setState(() {});
                    widget.onChanged?.call('enableLongPressToZoom');
                  },
                  comicId: comicArgs.comicId,
                  comicSource: comicArgs.comicSource,
                  useDeviceSettings: comicArgs.useDeviceSettings,
                ),
                if (appdata.settings['enableLongPressToZoom'] == true)
                  SelectSetting(
                    title: "Long press zoom position".tl,
                    settingKey: "longPressZoomPosition",
                    optionTranslation: {
                      "press": "Press position".tl,
                      "center": "Screen center".tl,
                    },
                    comicId: comicArgs.comicId,
                    comicSource: comicArgs.comicSource,
                    useDeviceSettings: comicArgs.useDeviceSettings,
                  ),
              ],
            ),
          ),
        ),
        _SettingPartTitle(title: "Display".tl, icon: Icons.visibility_outlined),
        SliverToBoxAdapter(
          child: SettingsSection(
            child: Column(
              children: [
                _SwitchSetting(
                  title: 'Limit image width'.tl,
                  subtitle: 'When using Continuous(Top to Bottom) mode'.tl,
                  settingKey: 'limitImageWidth',
                  onChanged: () {
                    widget.onChanged?.call('limitImageWidth');
                  },
                  comicId: comicArgs.comicId,
                  comicSource: comicArgs.comicSource,
                  useDeviceSettings: comicArgs.useDeviceSettings,
                ),
                if (App.isAndroid)
                  _SwitchSetting(
                    title: 'Turn page by volume keys'.tl,
                    settingKey: 'enableTurnPageByVolumeKey',
                    onChanged: () {
                      widget.onChanged?.call('enableTurnPageByVolumeKey');
                    },
                    comicId: comicArgs.comicId,
                    comicSource: comicArgs.comicSource,
                    useDeviceSettings: comicArgs.useDeviceSettings,
                  ),
                _SwitchSetting(
                  title: "Display time & battery info in reader".tl,
                  settingKey: "enableClockAndBatteryInfoInReader",
                  onChanged: () {
                    widget.onChanged?.call("enableClockAndBatteryInfoInReader");
                  },
                  comicId: comicArgs.comicId,
                  comicSource: comicArgs.comicSource,
                  useDeviceSettings: comicArgs.useDeviceSettings,
                ),
                _SwitchSetting(
                  title: "Show system status bar".tl,
                  settingKey: "showSystemStatusBar",
                  onChanged: () {
                    widget.onChanged?.call("showSystemStatusBar");
                  },
                  comicId: comicArgs.comicId,
                  comicSource: comicArgs.comicSource,
                  useDeviceSettings: comicArgs.useDeviceSettings,
                ),
                _SwitchSetting(
                  title: "Show Page Number".tl,
                  settingKey: "showPageNumberInReader",
                  onChanged: () {
                    widget.onChanged?.call("showPageNumberInReader");
                  },
                  comicId: comicArgs.comicId,
                  comicSource: comicArgs.comicSource,
                  useDeviceSettings: comicArgs.useDeviceSettings,
                ),
              ],
            ),
          ),
        ),
        _SettingPartTitle(
          title: "Images & Comments".tl,
          icon: Icons.image_outlined,
        ),
        SliverToBoxAdapter(
          child: SettingsSection(
            child: Column(
              children: [
                SelectSetting(
                  title: "Quick collect image".tl,
                  settingKey: "quickCollectImage",
                  optionTranslation: {
                    "No": "Not enable".tl,
                    "DoubleTap": "Double Tap".tl,
                    "Swipe": "Swipe".tl,
                  },
                  onChanged: () {
                    widget.onChanged?.call("quickCollectImage");
                  },
                  help:
                      "On the image browsing page, you can quickly collect images by sliding horizontally or vertically according to your reading mode"
                          .tl,
                  comicId: comicArgs.comicId,
                  comicSource: comicArgs.comicSource,
                  useDeviceSettings: comicArgs.useDeviceSettings,
                ),
                _CallbackSetting(
                  title: "Custom Image Processing".tl,
                  callback: () => context.to(() => _CustomImageProcessing()),
                  actionTitle: "Edit".tl,
                ),
                _SliderSetting(
                  title: "Number of images preloaded".tl,
                  settingsIndex: "preloadImageCount",
                  interval: 1,
                  min: 1,
                  max: 16,
                  comicId: comicArgs.comicId,
                  comicSource: comicArgs.comicSource,
                  useDeviceSettings: comicArgs.useDeviceSettings,
                ),
                _SwitchSetting(
                  title: "Show Chapter Comments".tl,
                  settingKey: "showChapterComments",
                  onChanged: _onShowChapterCommentsChanged,
                  comicId: comicArgs.comicId,
                  comicSource: comicArgs.comicSource,
                  useDeviceSettings: comicArgs.useDeviceSettings,
                ),
                if (_isChapterCommentsAtEndSupported())
                  _SwitchSetting(
                    title: "Show Comments at Chapter End".tl,
                    settingKey: "showChapterCommentsAtEnd",
                    onChanged: () {
                      widget.onChanged?.call("showChapterCommentsAtEnd");
                    },
                    comicId: comicArgs.comicId,
                    comicSource: comicArgs.comicSource,
                    useDeviceSettings: comicArgs.useDeviceSettings,
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

/// 阅读设置里「漫画专属/设备专属」开关行：两种画风各自渲染
/// （开关逻辑与 appdata.settings 的专属设置绑定，不能直接用 _SwitchSetting）。
class _ReaderToggleRow extends StatelessWidget {
  const _ReaderToggleRow({
    required this.title,
    required this.value,
    required this.onChanged,
  });

  final String title;

  final bool value;

  final void Function(bool) onChanged;

  @override
  Widget build(BuildContext context) {
    if (_useMiuixStyle) {
      return MiuixSwitchPreference(
        title: title,
        value: value,
        onChanged: onChanged,
      );
    }
    return ListTile(
      title: Text(title),
      trailing: Switch(value: value, onChanged: onChanged),
    );
  }
}

class _CustomImageProcessing extends StatefulWidget {
  const _CustomImageProcessing();

  @override
  State<_CustomImageProcessing> createState() => __CustomImageProcessingState();
}

class __CustomImageProcessingState extends State<_CustomImageProcessing> {
  var current = '';

  @override
  void initState() {
    super.initState();
    current = appdata.settings['customImageProcessing'];
  }

  @override
  void dispose() {
    appdata.settings['customImageProcessing'] = current;
    appdata.saveData();
    super.dispose();
  }

  int resetKey = 0;

  @override
  Widget build(BuildContext context) {
    // 本页由 ReaderSettings 里 `context.to` 直接推入根 Navigator，不继承
    // 设置页的 MiuixTheme；内部的 _SwitchSetting 在 Miuix 画风下是 Miuix
    // 组件，深色模式同样会回退浅色取色，需要自己包一层。
    return withMiuixTheme(context, _buildScaffold());
  }

  Widget _buildScaffold() {
    return Scaffold(
      appBar: Appbar(
        title: Text("Custom Image Processing".tl),
        actions: [
          TextButton(
            onPressed: () {
              current = defaultCustomImageProcessing;
              appdata.settings['customImageProcessing'] = current;
              resetKey++;
              setState(() {});
            },
            child: Text("Reset".tl),
          ),
        ],
      ),
      body: Column(
        children: [
          _SwitchSetting(
            title: "Enable".tl,
            settingKey: "enableCustomImageProcessing",
          ),
          Expanded(
            child: Container(
              margin: EdgeInsets.all(8),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(4),
                border: Border.all(color: context.colorScheme.outlineVariant),
              ),
              child: SizedBox.expand(
                child: CodeEditor(
                  key: ValueKey(resetKey),
                  initialValue: appdata.settings['customImageProcessing'],
                  onChanged: (value) {
                    current = value;
                  },
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
