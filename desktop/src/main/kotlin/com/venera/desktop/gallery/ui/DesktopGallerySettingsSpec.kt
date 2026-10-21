package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.domain.GalleryAnimatedMode
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryViewerBackdrop

internal object DesktopGallerySettingsSpec {

    /**
     * 屏上能改的一行。
     *
     * [key] 是**落盘键名**，用例拿它回核偏好件里确有这一枚；[setter] 是这一行按下去要调的那道写口，
     * 用例拿它回核两头：写口在 `DesktopGalleryPreferences` 上真存在、收的类型就是 [pref] 那枚流吐的，
     * 且这一屏的代码里真调着它 —— 少了这一格，"账上记着能改"与"屏上按下去有反应"之间就没有线。
     */
    data class Editable(
        val pref: String,
        val key: String,
        val setter: String,
        val title: String,
        val caption: String,
    )

    /** 屏上逐颗点名的一枚缺席档。[why] 说的是"差的是哪一层"，不是时间表。 */
    data class Absent(
        val pref: String,
        val title: String,
        val why: String,
    )

    /** 八枚。次序就是屏上次序：先"墙上摆成什么样"，再"要不要挡"，最后四枚是"翻着翻着自己走""提前取哪几张""动图要不要逐帧""大图那一屏的底"。 */
    val editable: List<Editable> = listOf(
        Editable(
            pref = "galleryColumnMode",
            key = "pref_gallery_columns",
            setter = "setGalleryColumnMode",
            title = "墙上摆几列",
            caption = "自适应按窗口宽推；另两档是显式覆盖，窗口再宽也不跟着动。",
        ),
        Editable(
            pref = "galleryPreviewQuality",
            key = "pref_gallery_preview",
            setter = "setGalleryPreviewQuality",
            title = "墙上那一格取哪一档",
            caption = "取站方小图档最省；取清楚那一档一屏就是几十 MB 级流量。",
        ),
        Editable(
            pref = "galleryBlockAi",
            key = "pref_gallery_block_ai",
            setter = "setGalleryBlockAi",
            title = "屏蔽站方打了 AI 标的作品",
            caption = "只认站方自己的标签，不靠画风推断；与漫画侧那把是两枚独立开关。",
        ),
        Editable(
            pref = "galleryAiBadge",
            key = "pref_gallery_ai_badge",
            setter = "setGalleryAiBadge",
            title = "在卡片角上标出 AI 标的作品",
            caption = "屏蔽关着时总得有个读数，所以这一枚默认是摆。",
        ),
        Editable(
            pref = "galleryAutoPlaySec",
            key = "pref_gallery_autoplay_sec",
            setter = "setGalleryAutoPlaySec",
            title = "大图页自动连播间隔",
            caption = "浮层开着时到点自己翻下一张；选「关」就停在当前这张。",
        ),
        Editable(
            pref = "galleryPreload",
            key = "pref_gallery_preload",
            setter = "setGalleryPreload",
            title = "大图页提前取哪几张",
            caption = "翻之前先把前后那几张的图取回来，换档就不重取；选「不提前取」最省流量。",
        ),
        // 第七行（批 52）：动图。消费点是批 51 那颗播放器（大图那一屏逐帧解），所以这一枚
        // 从今天起**改得动** —— 从前它挂在缺席那一区，说的是"屏上摆不出选中态"的那句话。
        // ⚠️ 中间那一档在桌面走的是"照播"那一支（桌面量不出哪种网不算流量），这句必须上屏：
        //   不说，那一档就是在承诺一件它没做的事。
        Editable(
            pref = "galleryAnimated",
            key = "pref_gallery_animated",
            setter = "setGalleryAnimated",
            title = "动图要不要逐帧播",
            caption = "只有大图那一屏会一帧一帧放，墙上与浮层仍停在第一帧。" +
                "桌面分不出哪种网不算流量，所以「只在不算流量的网上播」这一档在桌面上照「播」走。",
        ),
        // 第八行（批 61 接色、批 63 接糊）：大图那一屏的那层底。四档 = 共享那枚枚举的全部，一档都不删；
        // GLASS 那一档桌面走的是**应用内**那一层（窗口级那条 S0-6 判负），糊的是底下那面墙。
        Editable(
            pref = "galleryBackdrop",
            key = "pref_gallery_backdrop",
            setter = "setGalleryBackdrop",
            title = "大图那一屏的底",
            caption = "只影响大图那一屏：另三档是一层不透明的底，「模糊 + 压暗」那一档是把底下那面墙糊掉再压暗" +
                "（半径与压暗两枚数照对岸）。桌面没有对岸那种窗口级模糊，这一档走的是应用内那一层。",
        ),
    )

    /**
     * 连播那排按钮的档位（批 46）。
     *
     * 桌面这一屏没有对岸那枚 0..30 的连续滑条，所以给**档位**：全落在对岸那条区间内
     * （`DesktopGallerySettingsSpecTest` ⑤ 钉着），而 `0` 那一档就是"关"
     * —— 与对岸同一个数（共享那颗 `GalleryAutoPlay.shouldAdvance` 认 `seconds <= 0`）。
     */
    val autoPlayTiers: List<Int> = listOf(0, 5, 10, 20, 30)

    /** 三档 = 共享那颗枚举 [GalleryPreloadMode] 的全部，桌面不自造档位（自造一枚就是屏上一颗没人读的档）。 */
    val preloadTiers: List<GalleryPreloadMode> = GalleryPreloadMode.entries.toList()

    /**
     * 三档 = 共享那颗枚举 [GalleryAnimatedMode] 的全部（批 52），桌面不自造也不删档
     * —— 屏上叫法与档位同列表（`DesktopGallerySettingsSpecTest` ⑧ 拿枚举回核它）。
     *
     * ⚠️ **成对写而不是 `when` 展开**：那一族的**判定**只许住在共享
     * `GalleryMotion.animates` 里，而 `DesktopGalleryViewerMotionTest` ⑥ 扫的是
     * "枚举成员 + 分支箭头"那个形状（它分不清"重述判定"与"给档位起名"）。
     */
    val animatedTierLabels: List<Pair<GalleryAnimatedMode, String>> = listOf(
        GalleryAnimatedMode.ALWAYS to "一律逐帧播",
        GalleryAnimatedMode.WIFI_ONLY to "只在不算流量的网上播",
        GalleryAnimatedMode.NEVER to "停在第一帧",
    )

    /**
     * 四档 = 共享那枚枚举 [GalleryViewerBackdrop] 的全部（批 61），桌面不自造也不删档；
     * 屏上那四个叫法**逐字照对岸** `feature/settings/GallerySettings.kt:263-266`（同一枚档在两端念同一个词）。
     *
     * ⚠️ `GLASS` 那一枚从批 63 起说的是**真做的事**（糊底下那面墙 + 压暗，两枚数照对岸）。
     * 屏上这一句的行为断言每一版都要对着代码回核一次：写"糊"而画的是实心底，与写"实心底"而真糊了，
     * 是同一颗缺陷的两个方向（本仓那一族「文案说还没有 X 而 X 已落地」已经抓过四批）。
     * 另三档吃枚举自带的 `argb`（口径住在共享那一侧，见 `DesktopViewerBackdropPolicy`）。
     */
    val backdropTierLabels: List<Pair<GalleryViewerBackdrop, String>> = listOf(
        GalleryViewerBackdrop.GLASS to "模糊 + 压暗（默认）",
        GalleryViewerBackdrop.BLACK to "纯黑",
        GalleryViewerBackdrop.DARK_GRAY to "深灰",
        GalleryViewerBackdrop.WHITE to "纯白",
    )

    /**
     * 两枚（批 61 起又少一枚：`galleryBackdrop` 有了消费点与写口，已挪到上面那一区）。它们**不是**"忘了做"，
     * 但也不是一句"桌面没有那一屏"能整块打发的 ——
     * 第十一批 P0/P1 之后桌面有了详情浮层（能换三档、能翻页），所以每一枚都得各自说清差在哪。
     *
     * ⚠️ 下面每句 `why` 是**直接上屏**的（`DesktopGallerySettingsPane.kt:143` 拿它当 `Text`），
     * 所以只写用户能懂的话；"差的是哪一层"的技术账记在这里：
     * - `keepScreenOn` 判据本体是 Android 的 keep-screen-on 窗口标记，Windows 侧要另造一颗平台件；
     * - `volumeKeyTurn` 那枚键位在桌面上不是翻页键位，桌面翻页的入口是浮层那两枚按钮；
     * - `autoPlaySec` **接得住**（浮层已能翻页），差一段"到点自动翻下一张"的定时器 + 一道写口；
     * - `preload` 预取对象存在（前后张），差的是桌面手里没有那枚 ImageLoader 句柄（走默认单例）；
     * - `animated`（批 52 起这一枚已在上面那一区，下面这四环仍是**要账的那一路**：它量的是 `AsyncImage`，
     *   也就是墙上与浮层那两张为什么还静帧）这一档屏上说的是**量过的**（第十八批 批 18，四环链，缺一环都不许说）：
     *   ① 桌面走 coil3 默认 ImageLoader（共享那颗 `GalleryImageLoader` 仍在 `desktop/build.gradle.kts`
     *   的排除清单里）；② `javap` 读 `coil-core-jvm`：非 Android 那侧的默认组件注册函数里 new 的就是
     *   `SkiaImageDecoder$Factory`，而 JVM 侧没有别的真解码件；③ `SkiaImageDecoder.decodeBitmapSync`
     *   调的是 `org.jetbrains.skia.Image.makeFromEncoded(byte[])` 而**不是** `Codec`；
     *   ④ `DesktopAnimatedDecodeProbeTest` 本机真跑：自造的两帧 GIF（`Codec帧数=2`）经那颗入口交出
     *   `4×4` 一张静态图、像素 == 第 0 帧。⇒ **解得出来，但只解首帧**，所以屏上说"停在第一帧"。
     *   ⚠️ 桌面 classpath 上 `coil-core-jvm` / `coil-compose-core-jvm` 条目名零处含 animated / gif、
     *   也没引 `coil-gif` —— "动着放"这一档在桌面上**没有承载件**，不是"没做"。写口仍然没有（属 W5）。
     *   ⚠️ **第五十一批改了那一半**：大图那一屏不再吃那条链，它自己拿 `org.jetbrains.skia.Codec`
     *   逐帧解（`DesktopAnimatedImage`）⇒ "停在第一帧"今天只对**墙上与浮层那一路 `AsyncImage`** 成立。
     *   为什么只在大图那一屏：`coil3.Image` 那个接口在 JVM 侧**没有"帧"这一档**（`javap` 只有
     *   宽/高/shareable/draw），所以"写一颗 Decoder 让 `AsyncImage` 自己动"结构性不通 —— 上面那四环
     *   量的仍是 `AsyncImage` 那一路，`DesktopAnimatedDecodeProbeTest` 钉的还是它，别当被本批改掉了。
     * - `backdrop`（批 61 起这一枚已在上面那一区，批 63 起四档**全做**）：GLASS 那一档走的是**应用内**那一层 ——
     *   窗口级模糊判负（S0-6，材质根本没画出来），而应用内那条批 62 探针量到画得出来，于是桌面把它接在**墙那棵子树**上
     *   （半径 18dp + 黑压暗 0.45，两枚数照对岸，由 `DesktopViewerBackdropTest` ⑨ 逐字回核）。
     *   屏上那句标签与 caption 每改一次行为都要跟着改（同一批里改，别留旧句子）。
     *
     * 措辞由 `DesktopGallerySettingsSpecTest` ⑦ 钉：不许再用过期总称，且两句不许撞成同一句。
     */
    val notOnScreen: List<Absent> = listOf(
        Absent("galleryKeepScreenOn", "大图页保持屏幕常亮", "看大图的时候不让屏幕自己熄下去 —— 桌面今天不做这件事"),
        Absent("galleryVolumeKeyTurn", "音量键翻大图页", "按音量键翻下一张是手机上的键位；桌面上翻页走浮层里那两枚按钮，键盘的 ← 与 → 也认"),
        Absent("galleryWatercolorBeta", "把大图重画成水彩", "那一层重绘要 Android 的位图管线（Canvas 与模糊笔刷），桌面没有那层，所以这一枚在桌面上恒关"),
    )

    /** 两枚结构性不参与：桌面走侧栏，没有那两条栏可收（P5 拍板，见 DesktopGalleryPreferences 类注释）。 */
    val STRUCTURAL_ABSENT: List<String> = listOf("galleryHideTopBar", "galleryHideBottomBar")
}
