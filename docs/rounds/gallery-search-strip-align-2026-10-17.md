# 首页搜索条与切换器「等高」的真凶（2026-10-17 第四轮）

> 用户两次点名：第一次「搜索框缩成和切换源一样大小」（上一轮改了 `height`），
> 第二次「我看了，搜索栏大小还是没有对齐」。第二轮才量到真凶 —— **不在那个数字上**。

## 一 读数

用户截图 1080×572、密度 2.625：

| 件 | 屏上像素 | 折算 |
|---|---|---|
| 切换器药丸 | 391~519 = **129px** | ≈ 48dp |
| 搜索条底色 | 382~533 = **152px** | ≈ **58dp** = `space2 + 48 + space3` |

⇒ 上一轮把 56 改成 48 **确实生效了**（卡片内缩仍是 4+6），只是被那 10dp 的内缩盖过去，
所以"改完还是不对"。

## 二 真凶：`padding` 挂在哪一层

原写法三个 `padding` 直接串在 `Surface` 的 `modifier` 链上：

```kotlin
Surface(
    modifier = Modifier
        .fillMaxWidth()
        .padding(start = …, end = …, top = space2, bottom = space3)  // ← 在这里
        .height(tokens.spacing.segmentedHeight)
        …
)
```

miuix 的 `Surface` **自己铺底**（`basic/Surface.kt:57-69`：`Box(modifier = modifier.…clip(shape).background(color))`），
而 `modifier` 是它收到的**整条链** ⇒ 串在它之前的 `padding` 被一起涂色。
于是屏上那块面 = 「内缩 + 48dp」，48dp 本身没错，错的是**颜色多铺了一圈边距**。

## 三 改法（唯一一条）

**内缩留在外框，`Surface` 自己只拿 48dp。** 外框高度仍是 `space2 + 48 + space3`，
与 `galleryHomeChromeHeight()` 逐项对应（那一格是墙第一行，兜高度的是它，不能跟着动）。

共享元素锚点也留在**外框**：卡那一端包在调用点的 `Box` 里，两端外框同构（同样满屏宽、同样那三个内缩）
⇒ 飞行时相对关系一致；挂进内缩层会让两端一个是"面"、一个是"面 + 边距"，飞行途中横跳一下。

## 四 判据

`GalleryHomeChromeTest.搜索条的内缩长在外框上而不是 Surface 里` —— **切两段**：

1. 共享元素锚点 → `Surface(`：这一段**必须**有三个内缩；
2. `Surface(` → `.height(tokens.spacing.segmentedHeight)`：这一段 `padding(` **零命中**。

⚠️ 第一版判据只切了"锚点 → 定高"一段，把外框上**应该**有的 padding 也算进了 `Surface`
⇒ 白红一次，然后被自己的红骗着去改对的代码。**判据要按"谁的链"切，不是按"从哪到哪"切。**

## 五 数字

全量 `:app:testDebugUnitTest` **175 类 / 1,388 笔 / 0 失败**；`assembleDebug` 绿
（arm64 **103,747,043** 字节）。

## 六 本条可迁移的一句

> miuix 的 `Surface` / `Card` 这类"自己铺底"的容器，`modifier` 链上**任何**在它之前的
> `padding` / `background` / `border` 都会被算进那一块面。
> 「视觉尺寸不对」先查**内缩挂在哪一层**，再查数字。
