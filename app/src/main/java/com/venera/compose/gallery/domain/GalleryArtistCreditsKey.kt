package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GallerySite

/**
 * 画师署名的缓存键：`站点 | 画师名 | 出处`。
 *
 * 三样各有一条理由，缺哪一样都会出**会说话的错**：
 *
 * - **站点**：各站有自己的画师记录与别名体系，同名的两个人不保证是同一个。
 * - **画师名转小写**：各站的标签都是小写、我们也在入库前统一过，但"画师页点进来"那一路
 *   是用户从屏上抄的名字，大小写不该把同一位拆成两份缓存（拆了只是白取一次，不致命，
 *   但会让"同一屏里两枚同样的名字各自转圈"这种怪象出现）。
 * - **出处**：站方记录缺档时，那一枚 pixiv 入口是从**出处反查**来的
 *   （`GalleryArtistLinks.pixivArtworkId` → `PixivClient.artworkAuthor`）。
 *   同一位画师的两张图挂着不同出处时，反查到的"作者"未必是同一个人
 *   （转投、代投、原作是别人）—— 混用一份会给这一位挂上另一位的号。
 *   这一条是**唯一不显然**的那一样，所以它单独成文件、也单独有单测。
 *
 * 键里不放 [GallerySite.displayName] 那类会变的东西：键要能跨版本存活，
 * 改一次显示名就让整份缓存失配是白花钱。
 */
internal object GalleryArtistCreditsKey {

    fun of(site: GallerySite, name: String, source: String): String =
        "${site.name}|${name.lowercase()}|$source"
}
