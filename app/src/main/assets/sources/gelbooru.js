/**
 * Gelbooru —— 图站（booru 系）。每条 post 当成"只有一话的漫画"接入。
 *
 * ## 为什么不用 DAPI
 *
 * Gelbooru 官方有两条路可走：
 * 1. **DAPI**（`index.php?page=dapi&s=post&q=index&json=1`）—— 画廊模块走的那条，
 *    它**匿名一律 401**，必须带 `api_key` + `user_id`；
 * 2. **HTML 列表页**（`index.php?page=post&s=list&tags=…&pid=N`）—— **匿名可用**。
 *
 * 这个源走第 2 条。理由：JS 源的账号体系（`account`）在这套壳里只支持
 * webview 登录 / cookie 注入两种，而 DAPI 要的是"两个 query 参数"，
 * 塞不进任何一种；硬塞就等于要用户把 API Key 填到一个本该填密码的地方。
 * 而 HTML 列表页匿名就能用，功能也够（列表 + 单条 + 标签补全），
 * 所以这个源**不需要账号**，与画廊模块那条路各走各的、互不干扰。
 *
 * ## 实测（2026-09-26，本仓探针）
 *
 * - 列表页 `index.php?page=post&s=list&tags=<词>&pid=<页>`，**每页 42 条**，`pid` **0 基**；
 * - 缩略图：`https://img4.gelbooru.com/thumbnails/<xx>/<yy>/thumbnail_<md5>.jpg`
 *   （`xx`/`yy` 是 md5 的前两位，页面上直接给，不必自己拼）；
 * - 单条页：`index.php?page=post&s=view&id=<id>`；
 * - 原图：`https://img4.gelbooru.com/images/<xx>/<yy>/<file>`；
 * - 分级四个单词：`general` / `sensitive` / `questionable` / `explicit`；
 * - 标签补全：`autocomplete2` 匿名可用（返回 JSON 数组）。
 *
 * ⚠️ **`date:` 元标签实测返回空**，所以本源的"排行"页用不了"日榜"那种语义；
 * 下面 explore 的排行用 `sort:score:desc`（全站高分）—— 与画廊模块那边同一口径。
 */
class Gelbooru extends ComicSource {

    name = "Gelbooru"

    key = "gelbooru"

    version = "1.0.0"

    minAppVersion = "1.0.0"

    url = "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/gelbooru.js"

    baseUrl = "https://gelbooru.com"

    imageHost = "https://img4.gelbooru.com"

    /// 站方的列表页路由（`pid` 0 基，每页 42 条 —— 实测）。
    listPath = "index.php?page=post&s=list"

    /// 单条页路由。
    viewPath = "index.php?page=post&s=view"

    /// 每页条数（实测固定 42，不接受 `limit` 参数 —— 分页只能靠 `pid`）。
    pageSize = 42

    /**
     * 从 URL 里抠出 post id。
     *
     * 两种形态都认：`index.php?page=post&s=view&id=123`（单条页）
     * 与 `index.php?page=post&s=list&tags=…&pid=…`（列表页，没有 id → 返回空串）。
     */
    parseUrl(url) {
        let id = ""
        let m = String(url || "").match(/[?&]id=(\d+)/)
        if (m) id = m[1]
        return { id: id }
    }

    /**
     * 列表页 → 归一漫画。
     *
     * HTML 结构（实测）：每条是一个 `<article>`，里面一个指向 `s=view&id=` 的 `<a>`，
     * 缩略图是它下面的 `<img>`；标签串在 `<img alt="...">` 里（**空格分隔**），
     * 行尾还有个 `score:N rating:xxx` 的尾巴。
     *
     * ⚠️ **站方不给标题** —— booru 系的数据模型里没有"作品名"这个概念。
     * 所以标题取 id（`#123456`）。编一个"标题"出来只会让用户以为那是站方的字段。
     */
    parseComic(element) {
        let a = element.querySelector("a")
        let href = a?.attributes?.["href"] || ""
        let m = href.match(/[?&]id=(\d+)/)
        let id = m ? m[1] : ""
        if (!id) return null

        let img = element.querySelector("img")
        let cover = img?.attributes?.["src"] || ""

        // `alt` 里是空格分隔的标签，尾部带 `score:N rating:xxx`。只取真正的标签。
        let alt = img?.attributes?.["alt"] || ""
        let tags = []
        let rating = ""
        for (let t of alt.split(/\s+/)) {
            if (!t) continue
            if (t.startsWith("score:")) continue
            if (t.startsWith("rating:")) {
                rating = t.substring("rating:".length)
                continue
            }
            tags.push(t)
        }

        return new Comic({
            id: id,
            title: "#" + id,
            subtitle: "",
            cover: cover,
            tags: tags,
            description: rating ? "分级：" + rating : "",
        })
    }

    /**
     * 拼一次列表页地址。
     * @param tags {string} - 站方语法的标签串（空格分隔，含 `-` 排除项）
     * @param pid {number} - **0 基**页号。调用方的 page 从 1 起，所以这里减 1。
     */
    buildListUrl(tags, page) {
        let pid = Math.max(0, (page || 1) - 1)
        return `${this.baseUrl}/${this.listPath}&tags=${encodeURIComponent(tags || "")}&pid=${pid}`
    }

    /**
     * 抓一页列表并解析。
     *
     * 站方把整页 HTML 回来（不是 JSON），所以走 `HtmlDocument`。
     * ⚠️ 用 `article` 当容器选择器是实测的：该站列表页每条都是一个 `<article>` 元素。
     * 若哪天改版，这条会在"0 条结果"上表现出来 —— 那时要重新看页面结构，
     * 而不是怀疑标签写错了。
     */
    async loadListPage(tags, page) {
        let url = this.buildListUrl(tags, page)
        let res = await Network.get(url, {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
        })
        if (res.status !== 200) {
            throw "Invalid Status Code: " + res.status
        }
        let document = new HtmlDocument(res.body)
        let nodes = document.querySelectorAll("article")
        let comics = []
        for (let node of nodes) {
            let c = this.parseComic(node)
            if (c != null) comics.push(c)
        }
        // 站方不给总页数，只给"这一页有几条"。所以 maxPage 只能这样表达：
        // 满页 ⇒ 可能还有下一页；不满页 ⇒ 这是最后一页。
        let maxPage = comics.length >= this.pageSize ? (page || 1) + 1 : (page || 1)
        return { comics: comics, maxPage: maxPage }
    }

    explore = [
        {
            title: "Gelbooru",
            /// 一页到底 + 可翻页的列表。
            type: "multiPageComicList",

            load: async (page) => {
                return this.loadListPage("sort:score:desc", page)
            },
        },
    ]

    category = {
        title: "Gelbooru",
        parts: [
            {
                name: "分级",
                type: "fixed",
                categories: ["general", "sensitive", "questionable", "explicit"],
                itemType: "search",
            },
            {
                name: "热门标签",
                type: "fixed",
                categories: [
                    "1girl", "1boy", "solo", "touhou", "original",
                    "blue_archive", "genshin_impact", "hololive", "vocaloid",
                ],
                itemType: "search",
            },
        ],
        enableRankingPage: true,
    }

    categoryComics = {
        ranking: {
            options: [
                "score-Top Score",
                "updated-Recently Updated",
            ],
            /**
             * 排行页。⚠️ **这一站没有"日榜"** —— 官方唯一的排序入口是元标签
             * `sort:`（`sort:score` / `sort:updated`），没有按天的视图
             * （实测 `date:` 元标签返回空）。
             * 所以"Top Score"是**全站累计高分**，不是"今天的"——
             * 选项文案上如实写"Top Score"而不是"今日热门"，免得用户以为那是日榜。
             */
            load: async (option, page) => {
                let sort = option === "updated" ? "sort:updated:desc" : "sort:score:desc"
                return this.loadListPage(sort, page)
            },
        },
        /**
         * 从分类页点一枚标签进来。
         * @param category {string} - 标签名
         * @param param {string?}
         * @param page {number}
         */
        load: async (category, param, page, options) => {
            return this.loadListPage(category, page)
        },
    }

    search = {
        /**
         * 搜索。关键字**原样当站方标签语法用** —— 空格分隔、支持 `-` 排除、
         * 支持 `rating:` / `sort:` 这类元标签，与网页搜索框完全是同一套。
         */
        load: async (keyword, options, page) => {
            return this.loadListPage(keyword, page)
        },

        /**
         * 标签补全。
         *
         * 走 `autocomplete2`（实测匿名可用，回一个 JSON 数组）。
         * ⚠️ 它回的是**纯数组**（`["tag1","tag2"]`），不是对象数组 ——
         * 如果哪天它改了形状，`JSON.parse` 之后这里会拿到 undefined，
         * 表现为"补全永远空"，而不是抛错。
         */
        loadTags: async (keyword) => {
            let url = `${this.baseUrl}/autocomplete2?q=${encodeURIComponent(keyword)}&limit=20`
            let res = await Network.get(url, {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            })
            if (res.status !== 200) return []
            let list = JSON.parse(res.body)
            if (!Array.isArray(list)) return []
            return list.map((e) => (typeof e === "string" ? { label: e, value: e } : e))
        },

        enableTagsSuggestions: true,
    }

    /// 单条：一条 post 就是"只有一话的漫画"，那一话里只有这一张图。
    comic = {
        /**
         * 取条目详情。
         *
         * 站方的单条页 HTML 里没有 JSON，所以从页面里抠：
         * - 原图地址在 `#image` 那个 `<img>` 的 `src`（或 `data-src`）；
         * - 标签在右侧栏的 `<a href="...tags=xxx">` 里。
         *
         * ⚠️ 抠不到原图时**抛错**而不是回一张空列表：静默回空会让用户看到
         * 一个能打开、但永远是白屏的阅读页 —— 那比直接报错难查得多。
         */
        loadInfo: async (comicId) => {
            let url = `${this.baseUrl}/${this.viewPath}&id=${comicId}`
            let res = await Network.get(url, {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            })
            if (res.status !== 200) {
                throw "Invalid Status Code: " + res.status
            }
            let document = new HtmlDocument(res.body)

            let img = document.getElementById("image")
            let fileUrl = img?.attributes?.["src"] || img?.attributes?.["data-src"] || ""
            if (!fileUrl) {
                throw "Gelbooru 单条页里没找到原图（页面结构可能已改版）"
            }

            let tags = []
            for (let node of document.querySelectorAll("a[href*='tags=']")) {
                let text = node.text
                if (text && text.indexOf(" ") < 0 && text.length > 0 && text.length < 100) {
                    tags.push(text)
                }
            }

            return new ComicDetails({
                title: "#" + comicId,
                subtitle: "",
                description: "",
                tags: tags,
                chapters: new Map([
                    ["默认", [comicId]],
                ]),
            })
        },

        /**
         * 取某一话的图片列表。只有一张。
         *
         * 这里再抓一次单条页（而不是把 loadInfo 的结果缓存起来）：
         * 壳里这两步是分开调的，而缓存会带来"什么时候失效"的问题 ——
         * 多一次请求换一处不会错的状态，值。
         */
        loadEp: async (comicId, epId) => {
            let url = `${this.baseUrl}/${this.viewPath}&id=${comicId}`
            let res = await Network.get(url, {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            })
            if (res.status !== 200) {
                throw "Invalid Status Code: " + res.status
            }
            let document = new HtmlDocument(res.body)
            let img = document.getElementById("image")
            let fileUrl = img?.attributes?.["src"] || img?.attributes?.["data-src"] || ""
            if (!fileUrl) {
                throw "Gelbooru 单条页里没找到原图（页面结构可能已改版）"
            }
            return {
                images: [fileUrl],
            }
        },

        /**
         * 图片加载要带的头。
         *
         * 站方的 CDN 挂在 Cloudflare 后面，带一个浏览器 UA 最稳
         * （实测匿名直连也能取到，所以这**不是**必需项，见画廊侧那条注释的口径）。
         */
        loadImage: async (url) => {
            return {
                url: url,
                headers: {
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                    "Referer": "https://gelbooru.com/",
                },
            }
        },

        /**
         * 缩略图与图片同一口径。
         */
        loadThumbnail: async (url) => {
            return {
                url: url,
                headers: {
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                    "Referer": "https://gelbooru.com/",
                },
            }
        },

        /// 没有评论接口（这个源不接）。
        enableTags: true,
    }

    /**
     * 不做在线收藏。
     *
     * 站方有 `fav:` 元标签，但那是**搜索**用的（`fav:<userid>`），
     * 要配 `user_id` 才能用；而这个源刻意不带账号（理由见文件头）。
     * 所以收藏交给壳本地的漫画收藏，不假装有在线收藏。
     */
    favorites = {
        multiFolder: false,
    }
}
