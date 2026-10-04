package com.venera.compose.data.update

/**
 * 从一版发布挂的多份 APK 里选出**该由本机下哪一份**。
 *
 * v2.0.3 实测挂了四份（另有一份 `output-metadata.json` 已在解析层被类型门挡掉）：
 * `venera-miuix-2.0.3.apk`（通用）、`-arm64-v8a`、`-armeabi-v7a`、`-x86_64`。
 * 四份的体积几乎一样（14.42~14.48 MB），所以**选错不会省什么也不会多花什么**，
 * 但选错可能装不上或跑不了 —— 这一层的价值全在"不静默给一份别的"。
 */

/** 发布包名里可能出现的 ABI 段。长的那个在前，`x86_64` 不许被 `x86` 抢走。 */
private val ABI_TAGS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

/**
 * [supportedAbis] 传 `Build.SUPPORTED_ABIS`（它本身就是按优先级排的）。
 *
 * 匹配的是 `"-<abi>."` 这个**带尾点**的形状，不是 `-<abi>`：
 * 少了那个点，`armeabi-v7a` 与 `armeabi` 会互相误命中，`x86` 会命中 `x86_64` 的前半截，
 * 而误命中的表现是"下载完安装失败"，不是编译错误。
 *
 * 交回 null 是**答案**而不是失败：要么本机 ABI 一份都不命中、且这一版没挂通用包，
 * 要么这一版根本没有 APK。调用侧据此说"这一版没有适配本机的安装包"并给一条去浏览器的出口，
 * 不许退化成"随便下一份试试"。
 */
internal fun pickApkAsset(assets: List<ReleaseAsset>, supportedAbis: List<String>): ReleaseAsset? {
    if (assets.isEmpty()) return null
    for (abi in supportedAbis) {
        if (abi.isBlank()) continue
        assets.firstOrNull { it.name.contains("-$abi.", ignoreCase = true) }?.let { return it }
    }
    // 通用包 = 名字里不含任何 ABI 段的那一份。
    return assets.firstOrNull { asset -> ABI_TAGS.none { tag -> asset.name.contains("-$tag.", ignoreCase = true) } }
}
