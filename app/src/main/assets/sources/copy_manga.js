class CopyManga extends ComicSource {

    name = "拷贝漫画"

    key = "copy_manga"

    version = "1.4.15-debug1"

    minAppVersion = "1.6.0"

    url = "https://cdn.jsdelivr.net/gh/moonskycake/venera-next@main/copy_manga.js"

    async getReqID() {
        if (this.copyRegion === "0") {
            return "";
        }
        let now = Date.now();
        // 成功缓存 5 分钟；失败缓存 10 分钟，避免每个接口都白等一次超时
        if (this._reqIdCache && now < this._reqIdCache.expire) {
            return this._reqIdCache.value;
        }
        const reqIdUrl = "https://marketing.aiacgn.com/api/v2/adopr/query3/?format=json&ident=200100001";
        let reqId = "";
        let ttl = 10 * 60 * 1000;
        try {
            const response = await this.networkGet(reqIdUrl, this.headers);

            if (response.status === 200) {
                const data = JSON.parse(response.body);
                reqId = (data.results && data.results.request_id) || "";
                if (reqId) {
                    ttl = 5 * 60 * 1000;
                }
            }
        } catch (e) {
        }
        this._reqIdCache = { value: reqId, expire: now + ttl };
        return reqId;
    }

    get headers() {
        let token = this.loadData("token");
        let secret = "M2FmMDg1OTAzMTEwMzJlZmUwNjYwNTUwYTA1NjNhNTM="

        let now = new Date(Date.now());
        let year = now.getFullYear();
        let month = (now.getMonth() + 1).toString().padStart(2, '0');
        let day = now.getDate().toString().padStart(2, '0');
        let ts = Math.floor(now.getTime() / 1000).toString()

        if (!token) {
            token = "";
        } else {
            token = " " + token;
        }

        let sig = Convert.hmacString(
            Convert.decodeBase64(secret),
            Convert.encodeUtf8(ts),
            "sha256"
        )

        return {
            "User-Agent": `COPY/3.0.6`,
            "source": "copyApp",
            "deviceinfo": this.deviceinfo,
            "dt": `${year}.${month}.${day}`,
            "platform": "3",
            "referer": `com.copymanga.app-3.0.6`,
            "version": "3.0.6",
            "device": this.device,
            "pseudoid": this.pseudoid,
            "Accept": "application/json",
            "region": this.copyRegion,
            "authorization": `Token${token}`,
            "umstring": "b4c89ca4104ea9a97750314d791520ac",
            "x-auth-timestamp": ts,
            "x-auth-signature": sig,
        }
    }

    // static defaultCopyVersion = "3.0.6"

    // static defaultCopyPlatform = "2"

    static defaultCopyRegion = "0"

    static defaultImageQuality = "1500"

    static defaultApiUrl = 'api.copy5000.com'

    // 备用线路：主线路被风控或解析失败时轮换
    static fallbackApiUrls = ['api.copy5000.com', 'api.copy4000.com', 'api.manga2026.xyz']

    // 网页版搜索接口（站点前端 countApi）。2026-10 站点把 searchb 换成了 searchcl，
    // 旧的 /api/kb/web/searchb/comics 现在返回 404。
    static searchApi = "/api/kb/web/searchcl/comics"

    // refreshSearchApi 抓取页面用的网页站（页面里才有 const countApi = ...）
    static searchHosts = ['www.copy5000.com', 'www.copy4000.com', 'www.copy20.com']

    // 图片请求统一使用的 UA（与官方 App 一致）
    static userAgent = "COPY/3.0.6"

    // 网页版线路（阅读图片走这里，不经过 App 接口的版本/破解检查）
    static webHosts = ['www.manga2026.xyz', 'www.copy5000.com', 'www.copy4000.com', 'www.copy20.com']

    static webUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"


    get deviceinfo() {
        let info = this.loadData("_deviceinfo");
        if (!info) {
            info = CopyManga.generateDeviceInfo();
            this.saveData("_deviceinfo", info);
        }
        return info;
    }

    get device() {
        let dev = this.loadData("_device");
        if (!dev) {
            dev = CopyManga.generateDevice();
            this.saveData("_device", dev);
        }
        return dev;
    }

    get pseudoid() {
        let pid = this.loadData("_pseudoid");
        if (!pid) {
            pid = CopyManga.generatePseudoid();
            this.saveData("_pseudoid", pid);
        }
        return pid;
    }

    // get copyVersion() {
    //     return this.loadSetting('version')
    // }

    // get copyPlatform()
    // return this.loadSetting('platform')
    // }

    static generateDeviceInfo() {
        return `${randomInt(1000000, 9999999)}V-${randomInt(1000, 9999)}`;
    }

    static generateDevice() {
        function randCharA() {
            return String.fromCharCode(65 + randomInt(0, 25));
        }
        function randDigit() {
            return String.fromCharCode(48 + randomInt(0, 9));
        }
        return (
            randCharA() +
            randCharA() +
            randDigit() +
            randCharA() + "." +
            randDigit() +
            randDigit() +
            randDigit() +
            randDigit() +
            randDigit() +
            randDigit() + "." +
            randDigit() +
            randDigit() +
            randDigit()
        );
    }

    static generatePseudoid() {
        const chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        let pseudoid = '';
        for (let i = 0; i < 16; i++) {
            pseudoid += chars.charAt(randomInt(0, chars.length - 1));
        }
        return pseudoid;
    }

    // 清除已保存的设备信息, 下次构造请求头时会重新生成
    clearDeviceInfo() {
        this.deleteData("_deviceinfo");
        this.deleteData("_device");
        this.deleteData("_pseudoid");
    }

    // 每次网络请求结束后调用; 若开启了对应设置则自动清除设备信息
    afterRequest() {
        if (this.loadSetting('auto_clear_device_info') === true) {
            this.clearDeviceInfo();
        }
    }

    async networkGet(url, headers) {
        try {
            return await Network.get(url, headers);
        } finally {
            this.afterRequest();
        }
    }

    async networkPost(url, headers, data) {
        try {
            return await Network.post(url, headers, data);
        } finally {
            this.afterRequest();
        }
    }

    // 登录用的网页站：优先「当前 API 线路对应的 www.」，其余用内置候选
    get loginHosts() {
        let list = []
        let push = (h) => {
            if (h && list.indexOf(h) < 0) list.push(h)
        }
        let apiHost = this._discoveredApi || this.loadSetting("base_url") || this.defaultApiUrl
        if (apiHost.indexOf("api.") === 0) {
            push(`www.${apiHost.substring(4)}`)
        }
        for (let h of CopyManga.webHosts) {
            if (h.indexOf("www.") === 0 && h.indexOf("copy20.com") < 0) {
                push(h)
            }
        }
        return list
    }

    // 网页版登录（表单编码）。App 端 /api/v3/login 当前返回 210「該功能已關閉」，
    // 而且它对 body 格式要求很怪（表单报 JSON parse error、JSON 又读不到字段），
    // 所以以网页接口为主，App 接口仅作兜底。
    async webLogin(account, pwd) {
        let salt = randomInt(100000, 999999)
        let pass = Convert.encodeBase64(Convert.encodeUtf8(`${pwd}-${salt}`))
        let body =
            `username=${encodeURIComponent(account)}` +
            `&password=${encodeURIComponent(pass)}` +
            `&salt=${salt}&platform=2&version=3.0.0&source=freeSite`
        let lastMsg = ""
        let hosts = this.loginHosts.slice(0, 2)
        for (let host of hosts) {
            let res
            try {
                res = await Network.post(
                    `https://${host}/api/kb/web/login`,
                    {
                        "User-Agent": CopyManga.webUserAgent,
                        "Accept": "application/json",
                        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
                        "Authorization": "Token ",
                        "platform": "2",
                    },
                    body
                )
            } catch (e) {
                lastMsg = String(e)
                continue
            }
            let data = null
            try {
                data = JSON.parse(res.body)
            } catch (e) {
            }
            let token = data && data.results && data.results.token
            if (res.status === 200 && token) {
                this.saveData("token", token)
                return "ok"
            }
            lastMsg = (data && data.message) || `状态码 ${res.status}`
            // 账号/密码问题换域名也没用，直接报出来
            if (
                lastMsg.indexOf("不存在") >= 0 ||
                lastMsg.indexOf("密碼") >= 0 ||
                lastMsg.indexOf("密码") >= 0 ||
                lastMsg.indexOf("不能為空") >= 0
            ) {
                throw `拷贝漫画登录失败：${lastMsg}`
            }
            // 命中风控（提示下载正版/等 1 小时）：换域名只会加重，立刻停
            if (lastMsg.indexOf("破解") >= 0 || lastMsg.indexOf("等待1小時") >= 0 || lastMsg.indexOf("等待1小时") >= 0) {
                throw `拷贝漫画登录被风控拦截：${lastMsg}`
            }
        }
        throw `拷贝漫画登录失败：${lastMsg || "所有登录线路都不可用"}`
    }

    get apiUrl() {
        return `https://${this._discoveredApi || this.loadSetting('base_url') || this.defaultApiUrl}`
    }

    // 网页站列表：自动发现的域名优先，其余用内置候选
    get webHostList() {
        let list = []
        let push = (h) => {
            if (h && list.indexOf(h) < 0) {
                list.push(h)
            }
        }
        if (Array.isArray(this._extraWebHosts)) {
            for (let h of this._extraWebHosts) {
                push(h)
            }
        }
        for (let h of CopyManga.webHosts) {
            push(h)
        }
        return list
    }

    // 从 210 响应里解析服务器要求的等待时间（上限 15s，文案里说的“1 小時”实测几十秒就恢复）
    static waitTimeFrom(body) {
        try {
            let msg = JSON.parse(body).message || "";
            let m = msg.match(/(\d+)\s*seconds/);
            if (m && m[1]) {
                return Math.min(parseInt(m[1]) * 1000, 15000);
            }
            if (msg.indexOf("1小時") >= 0 || msg.indexOf("1小时") >= 0) {
                return 10000;
            }
        } catch (e) {
        }
        return 0;
    }

    // 内容类接口统一入口：
    // 拷贝会对“设备指纹 + IP”做频率风控，命中时返回 210 或 200 + results:null，
    // 直接抛错会让详情/阅读卡住，所以这里退避重试，并在线路之间轮换。
    async requestResults(path, options) {
        let tries = (options && options.tries) || 3;
        let wait = (options && options.wait) || 8000;
        let hosts = [];
        if (this._discoveredApi) {
            hosts.push(this._discoveredApi);
        }
        if (this._workingApiHost && hosts.indexOf(this._workingApiHost) < 0) {
            hosts.push(this._workingApiHost);
        }
        let configured = this.loadSetting('base_url') || this.defaultApiUrl;
        if (hosts.indexOf(configured) < 0) {
            hosts.push(configured);
        }
        for (let h of CopyManga.fallbackApiUrls) {
            if (hosts.indexOf(h) < 0) {
                hosts.push(h);
            }
        }
        let lastError = "";
        let lastStatus = 0;
        for (let i = 0; i < tries; i++) {
            let skip = this._hostSkip || 0;
            let host = hosts[(i + skip) % hosts.length];
            let res;
            try {
                res = await this.networkGet(`https://${host}${path}`, this.headers);
            } catch (e) {
                res = { status: -1, body: String(e) };
            }
            if (res.status === 200) {
                let data = null;
                try {
                    data = JSON.parse(res.body);
                } catch (e) {
                }
                if (data && data.results !== null && data.results !== undefined) {
                    this._workingApiHost = host;
                    return data.results;
                }
                lastError = "空数据";
            } else {
                lastStatus = res.status;
                lastError = `状态码 ${res.status}`;
                let asked = CopyManga.waitTimeFrom(res.body);
                if (asked > wait) {
                    wait = Math.min(asked, 15000);
                }
            }
            // 换线路可以立刻试，但 210 是「按 IP 计数」的频控：
            // 连续换线路猛打会更像攻击，所以命中 210 时无论换不换线路都先退避。
            if (i < tries - 1) {
                let nextHost = hosts[(i + 1 + (this._hostSkip || 0)) % hosts.length];
                if (nextHost === host || lastStatus === 210) {
                    await new Promise((resolve) => setTimeout(resolve, wait));
                }
            }
        }
        throw `拷贝漫画接口请求失败(${lastError})，可能触发风控，请稍后重试或更换线路`;
    }

    // 网页版章页 -> 解密出图片列表
    // 页面里 var cct = '<16位密钥>'; var contentKey = '<16位IV + 十六进制密文>';
    // 解密方式：AES-128-CBC(key=cct, iv=contentKey[0:16], PKCS7)
    async loadEpFromWeb(comicId, epId) {
        let html = null;
        for (let host of this.webHostList) {
            let res;
            try {
                res = await fetch(
                    `https://${host}/comic/${comicId}/chapter/${epId}`,
                    { headers: { "User-Agent": CopyManga.webUserAgent } }
                );
            } catch (e) {
                continue;
            }
            if (res.status !== 200) {
                continue;
            }
            let text = await res.text();
            if (text.indexOf("contentKey") >= 0) {
                html = text;
                break;
            }
        }
        if (!html) {
            return null;
        }
        let cct = html.match(/var cct\s*=\s*'([^']+)'/);
        let key = html.match(/contentKey\s*=\s*'([^']+)'/);
        if (!cct || !key) {
            return null;
        }
        let contentKey = key[1];
        let ivBytes = Convert.encodeUtf8(contentKey.substring(0, 16));
        let hex = contentKey.substring(16);
        // 注意：aes-cbc 的 value/key/iv 都必须是 ArrayBuffer（Uint8Array 会被转成 Map 而报错）
        let cipher = new Uint8Array(hex.length / 2);
        for (let i = 0; i < cipher.length; i++) {
            cipher[i] = parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        let plain = Convert.decryptAesCbc(
            cipher.buffer,
            Convert.encodeUtf8(cct[1]),
            ivBytes
        );
        if (!plain) {
            return null;
        }
        // 返回值可能是 Uint8Array（有 length）也可能是 ArrayBuffer（只有 byteLength）
        let plainLen = plain.length !== undefined ? plain.length : plain.byteLength;
        if (!plainLen) {
            return null;
        }
        let text = Convert.decodeUtf8(plain);
        // 去掉可能的 PKCS7 尾部残留
        let end = text.lastIndexOf("]");
        if (end >= 0) {
            text = text.substring(0, end + 1);
        }
        let list = JSON.parse(text);
        let images = list.map((e) => (typeof e === "string" ? e : e.url));
        return images.length > 0 ? images : null;
    }

    get copyRegion() {
        return this.loadSetting('region') || this.defaultCopyRegion
    }

    get imageQuality() {
        return this.loadSetting('image_quality') || this.defaultImageQuality
    }

    init() {
        // 用于储存 { 作者名 : 英文参数 }
        this.author_path_word_dict = {}
        // 仅影响“网页端搜索”这一可选开关，失败不应阻塞源初始化
        this.refreshSearchApi()
        // 线路自动更新（失败静默，不影响使用）
        this.refreshApiConfig()
    }

    static discoverTtl = 12 * 60 * 60 * 1000

    // 判定某个 API 主机是不是「App 口味」：homeIndex 里必须有 hotComics 或 newComics。
    // 网页版后端（如 api.manga2026.xyz）只给 recComics / rankWeeklyFreeComics 等字段，
    // 用来当 App 后端会导致「发现」页解析失败。
    async probeAppFlavor(host) {
        let res
        try {
            res = await this.networkGet(
                `https://${host}/api/v3/h5/homeIndex`,
                this.headers
            )
        } catch (e) {
            return false
        }
        if (!res || res.status !== 200) {
            return false
        }
        let results = null
        try {
            results = JSON.parse(res.body).results
        } catch (e) {
            return false
        }
        if (!results) {
            return false
        }
        return results.hotComics !== undefined || results.newComics !== undefined
    }

    async refreshApiConfig() {
        if (this.loadSetting("update_api") === false) {
            return
        }
        let now = Date.now()
        let cached = null
        try {
            cached = this.loadData("api_config")
        } catch (e) {
        }
        if (cached && cached.ts && now - cached.ts < CopyManga.discoverTtl) {
            if (cached.api) {
                this._discoveredApi = cached.api
            }
            if (Array.isArray(cached.web) && cached.web.length > 0) {
                this._extraWebHosts = cached.web
            }
            return
        }

        let configured = this.loadSetting("base_url") || this.defaultApiUrl
        let candidates = []
        let addCandidate = (h) => {
            if (h && candidates.indexOf(h) < 0) {
                candidates.push(h)
            }
        }
        addCandidate(configured)
        for (let h of CopyManga.fallbackApiUrls) {
            addCandidate(h)
        }

        // ① 直接用 homeIndex 校验已知候选（最多 3 个，避免请求过多）
        let okHost = null
        for (let i = 0; i < candidates.length && i < 3; i++) {
            if (await this.probeAppFlavor(candidates[i])) {
                okHost = candidates[i]
                break
            }
        }

        // ② 已知候选都不合格：用 network2 的 share 推导新域名，再校验
        let share = []
        if (!okHost) {
            let shareSources = []
            let addSource = (h) => {
                if (h && shareSources.indexOf(h) < 0) shareSources.push(h)
            }
            for (let h of candidates) addSource(h)
            for (let h of CopyManga.webHosts) addSource(h)
            for (let i = 0; i < shareSources.length && share.length === 0; i++) {
                let res
                try {
                    res = await this.networkGet(
                        `https://${shareSources[i]}/api/v3/system/network2?platform=3`,
                        this.headers
                    )
                } catch (e) {
                    continue
                }
                if (!res || res.status !== 200) continue
                let results = null
                try {
                    results = JSON.parse(res.body).results
                } catch (e) {
                    continue
                }
                if (results && Array.isArray(results.share)) {
                    for (let item of results.share) {
                        let d = Array.isArray(item) ? item[0] : item
                        if (typeof d === "string" && d.indexOf(".") > 0) {
                            share.push(d)
                        }
                    }
                }
            }
            let derived = []
            for (let d of share) {
                let bare = d.replace(/^www\./, "")
                derived.push(`api.${bare}`)
                derived.push(bare)
            }
            for (let h of derived) {
                if (await this.probeAppFlavor(h)) {
                    okHost = h
                    break
                }
            }
        }

        if (!okHost) {
            // 全都不合格：保持现状（用设置里的线路），不改动
            return
        }

        this._discoveredApi = okHost
        if (share.length > 0) {
            this._extraWebHosts = share
        }
        try {
            this.saveData("api_config", { ts: now, api: okHost, web: share })
        } catch (e) {
        }
    }

    /// account
    /// set this to null to desable account feature
    account = {
        /// login func
        login: async (account, pwd) => {
            let webError = null
            try {
                return await this.webLogin(account, pwd)
            } catch (e) {
                webError = e
                // 账号级错误（不存在/密码错）换接口也没用，直接报
                if (String(e).indexOf("登录失败：") >= 0) {
                    throw e
                }
            }
            // 兜底：App 接口（多数情况下服务器已关闭，返回 210 該功能已關閉）
            let salt = randomInt(1000, 9999)
            let base64 = Convert.encodeBase64(Convert.encodeUtf8(`${pwd}-${salt}`))
            let res = await this.networkPost(
                `${this.apiUrl}/api/v3/login`,
                {
                    ...this.headers,
                    "Content-Type": "application/x-www-form-urlencoded;charset=utf-8"
                },
                `username=${account}&password=${base64}\n&salt=${salt}&authorization=Token+`
            );
            if (res.status === 200) {
                let data = JSON.parse(res.body)
                let res0 = data.results || {}
                let token = res0.token
                if (!token) {
                    throw "拷贝漫画登录失败：账号或密码不正确"
                }
                this.saveData('token', token)
                return "ok"
            }
            let msg = ""
            try {
                msg = JSON.parse(res.body).message || ""
            } catch (e) {
            }
            if (res.status === 210) {
                throw String(webError || `拷贝漫画登录失败（服务器返回 210${msg ? "：" + msg : ""}），未登录也能正常阅读`)
            }
            throw String(webError || `拷贝漫画登录失败（状态码 ${res.status}${msg ? "：" + msg : ""}）`)
        },
        // callback when user log out
        logout: () => {
            this.deleteData('token')
        },
        registerWebsite: null
    }

    /// explore pages
    explore = [
        {
            title: "拷贝漫画",
            type: "singlePageWithMultiPart",
            load: async () => {
                let dataStr = await this.networkGet(
                    `${this.apiUrl}/api/v3/h5/homeIndex`,
                    this.headers
                )

                if (dataStr.status !== 200) {
                    throw `Invalid status code: ${dataStr.status}`
                }

                let data = JSON.parse(dataStr.body)

                function parseComic(comic) {
                    if (comic["comic"] !== null && comic["comic"] !== undefined) {
                        comic = comic["comic"]
                    }
                    let tags = []
                    if (comic["theme"] !== null && comic["theme"] !== undefined) {
                        tags = comic["theme"].map(t => t["name"])
                    }
                    let author = null

                    if (Array.isArray(comic["author"]) && comic["author"].length > 0) {
                        author = comic["author"][0]["name"]
                    }

                    return {
                        id: comic["path_word"],
                        title: comic["name"],
                        subTitle: author,
                        cover: comic["cover"],
                        tags: tags
                    }
                }

                // 两种后端口味：App 版字段优先，缺了再退回网页版字段，都缺则明确报错
                let res = {}
                let addSection = (name, value) => {
                    let list = Array.isArray(value) ? value : (value && value.list) || null
                    if (list && list.length > 0) {
                        res[name] = list.map(parseComic)
                    }
                }
                let r = data["results"] || {}
                addSection("推荐", r["recComics"])
                addSection("热门", r["hotComics"])
                addSection("最新", r["newComics"])
                addSection("完结", r["finishComics"])
                addSection("今日排行", r["rankDayComics"])
                addSection("本周排行", r["rankWeekComics"])
                addSection("本月排行", r["rankMonthComics"])
                if (Object.keys(res).length === 0) {
                    addSection("最近更新", r["updateWeeklyFreeComics"])
                    addSection("周榜", r["rankWeeklyFreeComics"])
                }
                if (Object.keys(res).length === 0) {
                    throw "拷贝漫画发现页数据为空（可能触发风控或线路不匹配），请稍后重试或更换线路"
                }

                return res
            }
        }
    ]

    static category_param_dict = {
        "全部": "",
        "愛情": "aiqing",
        "歡樂向": "huanlexiang",
        "冒險": "maoxian",
        "奇幻": "qihuan",
        "百合": "baihe",
        "校园": "xiaoyuan",
        "科幻": "kehuan",
        "東方": "dongfang",
        "耽美": "danmei",
        "生活": "shenghuo",
        "格鬥": "gedou",
        "轻小说": "qingxiaoshuo",
        "悬疑": "xuanyi",
        "其他": "qita",
        "神鬼": "shengui",
        "职场": "zhichang",
        "TL": "teenslove",
        "萌系": "mengxi",
        "治愈": "zhiyu",
        "長條": "changtiao",
        "四格": "sige",
        "节操": "jiecao",
        "舰娘": "jianniang",
        "竞技": "jingji",
        "搞笑": "gaoxiao",
        "伪娘": "weiniang",
        "热血": "rexue",
        "励志": "lizhi",
        "性转换": "xingzhuanhuan",
        "彩色": "COLOR",
        "後宮": "hougong",
        "美食": "meishi",
        "侦探": "zhentan",
        "AA": "aa",
        "音乐舞蹈": "yinyuewudao",
        "魔幻": "mohuan",
        "战争": "zhanzheng",
        "历史": "lishi",
        "异世界": "yishijie",
        "惊悚": "jingsong",
        "机战": "jizhan",
        "都市": "dushi",
        "穿越": "chuanyue",
        "恐怖": "kongbu",
        "C100": "comiket100",
        "重生": "chongsheng",
        "C99": "comiket99",
        "C101": "comiket101",
        "C97": "comiket97",
        "C96": "comiket96",
        "生存": "shengcun",
        "宅系": "zhaixi",
        "武侠": "wuxia",
        "C98": "C98",
        "C95": "comiket95",
        "FATE": "fate",
        "转生": "zhuansheng",
        "無修正": "Uncensored",
        "仙侠": "xianxia",
        "LoveLive": "loveLive"
    }

    category = {
        title: "拷贝漫画",
        parts: [
            {
                name: "拷贝漫画",
                type: "fixed",
                categories: ["排行"],
                categoryParams: ["ranking"],
                itemType: "category"
            },
            {
                name: "主题",
                type: "fixed",
                categories: Object.keys(CopyManga.category_param_dict),
                categoryParams: Object.values(CopyManga.category_param_dict),
                itemType: "category"
            }
        ]
    }

    categoryComics = {
        load: async (category, param, options, page) => {
            options = options || []
            // ranking 分支的 audience_type / date_type 取 options[0] / options[1]，
            // 但 category.parts 里没有 optionList，App 传进来是空数组
            //（原来会拼出 audience_type=undefined 直接失败），这里补空串。
            let opt0 = options[0] === undefined ? "" : options[0]
            let opt1 = options[1] === undefined ? "" : options[1]
            let path;
            let category_url;
            // 分类-排行
            if (category === "排行" || param === "ranking") {
                category_url = `${this.apiUrl}/api/v3/ranks?limit=30&offset=${(page - 1) * 30}&_update=true&type=1&audience_type=${opt0}&date_type=${opt1}`
            } else {
                // 分类-主题
                if (category !== undefined && category !== null) {
                    // 若传入category，则转化为对应param
                    param = CopyManga.category_param_dict[category] || "";
                }
                options = options.map(e => e.replace("*", "-"))
                category_url = `${this.apiUrl}/api/v3/comics?limit=30&offset=${(page - 1) * 30}&ordering=${opt1}&theme=${param}&top=${opt0}`
            }


            // 这两个接口也会被 210 风控，走统一重试 + 换线路
            // 注意 requestResults 返回的已经是 results 层，下面按 results.list 取
            let data = { results: await this.requestResults(category_url.substring(this.apiUrl.length)) }

            function parseComic(comic) {
                //判断是否是漫画排名格式
                let sort = null
                let popular = 0
                let rise_sort = 0;
                if (comic["sort"] !== null && comic["sort"] !== undefined) {
                    sort = comic["sort"]
                    rise_sort = comic["rise_sort"]
                    popular = comic["popular"]
                }

                if (comic["comic"] !== null && comic["comic"] !== undefined) {
                    comic = comic["comic"]
                }
                let tags = []
                if (comic["theme"] !== null && comic["theme"] !== undefined) {
                    tags = comic["theme"].map(t => t["name"])
                }
                let author = null
                let author_num = 0
                if (Array.isArray(comic["author"]) && comic["author"].length > 0) {
                    author = comic["author"][0]["name"]
                    author_num = comic["author"].length
                }

                //如果是漫画排名，则描述为 排名(+升降箭头)+作者+人气
                if (sort !== null) {
                    return {
                        id: comic["path_word"],
                        title: comic["name"],
                        subTitle: author,
                        cover: comic["cover"],
                        tags: tags,
                        description: `${sort} ${rise_sort > 0 ? '▲' : rise_sort < 0 ? '▽' : '-'}\n` +
                            `${author_num > 1 ? `${author} 等${author_num}位` : author}\n` +
                            `🔥${(popular / 10000).toFixed(1)}W`
                    }
                    //正常情况的描述为更新时间
                } else {
                    return {
                        id: comic["path_word"],
                        title: comic["name"],
                        subTitle: author,
                        cover: comic["cover"],
                        tags: tags,
                        description: comic["datetime_updated"]
                    }
                }
            }

            return {
                comics: data["results"]["list"].map(parseComic),
                maxPage: Math.ceil(data["results"]["total"] / 30)
            }
        },
        optionList: [
            {
                options: [
                    "-全部",
                    "japan-日漫",
                    "korea-韩漫",
                    "west-美漫",
                    "finish-已完结"
                ],
                notShowWhen: null,
                showWhen: Object.keys(CopyManga.category_param_dict)
            },
            {
                options: [
                    "*datetime_updated-时间倒序",
                    "datetime_updated-时间正序",
                    "*popular-热度倒序",
                    "popular-热度正序",
                ],
                notShowWhen: null,
                showWhen: Object.keys(CopyManga.category_param_dict)
            },
            {
                options: [
                    "male-男频",
                    "female-女频"
                ],
                notShowWhen: null,
                showWhen: ["排行"]
            },
            {
                options: [
                    "day-上升最快",
                    "week-最近7天",
                    "month-最近30天",
                    "total-總榜單"
                ],
                notShowWhen: null,
                showWhen: ["排行"]
            }
        ]
    }

    search = {
        load: async (keyword, options, page) => {
            let author;
            if (keyword.startsWith("作者:")) {
                author = keyword.substring("作者:".length).trim();
            }
            let res;
            // 通过onClickTag传入时有"作者:"前缀，处理这种情况
            if (author && author in this.author_path_word_dict) {
                let path_word = encodeURIComponent(this.author_path_word_dict[author]);
                res = await this.networkGet(
                    `${this.apiUrl}/api/v3/comics?limit=30&offset=${(page - 1) * 30}&ordering=-datetime_updated&author=${path_word}`,
                    this.headers
                )
            }
            // 一般的搜索情况
            else {
                let q_type = "";
                if (options && options[0]) {
                    q_type = options[0];
                }
                keyword = encodeURIComponent(keyword)
                let useWebApi = this.loadSetting('search_api') === "webAPI"
                let search_url = useWebApi
                    ? `${this.apiUrl}${CopyManga.searchApi}`
                    : `${this.apiUrl}/api/v3/search/comic`
                res = await this.networkGet(
                    `${search_url}?limit=30&offset=${(page - 1) * 30}&q=${keyword}&q_type=${q_type}`,
                    this.headers
                )
                // 网页搜索接口被站点下线时会 404，这时退回 App 搜索接口
                if (useWebApi && res.status !== 200) {
                    res = await this.networkGet(
                        `${this.apiUrl}/api/v3/search/comic?limit=30&offset=${(page - 1) * 30}&q=${keyword}&q_type=${q_type}`,
                        this.headers
                    )
                }
            }
            if (res.status !== 200) {
                throw `Invalid status code: ${res.status}`
            }

            let data = JSON.parse(res.body)

            function parseComic(comic) {
                if (comic["comic"] !== null && comic["comic"] !== undefined) {
                    comic = comic["comic"]
                }
                let tags = []
                if (comic["theme"] !== null && comic["theme"] !== undefined) {
                    tags = comic["theme"].map(t => t["name"])
                }
                let author = null

                if (Array.isArray(comic["author"]) && comic["author"].length > 0) {
                    author = comic["author"][0]["name"]
                }

                return {
                    id: comic["path_word"],
                    title: comic["name"],
                    subTitle: author,
                    cover: comic["cover"],
                    tags: tags,
                    description: comic["datetime_updated"]
                }
            }

            return {
                comics: data["results"]["list"].map(parseComic),
                maxPage: Math.ceil(data["results"]["total"] / 30)
            }
        },
        optionList: [
            {
                type: "select",
                options: [
                    "-全部",
                    "name-名称",
                    "author-作者",
                    "local-汉化组"
                ],
                label: "搜索选项"
            }
        ]
    }

    favorites = {
        multiFolder: false,
        addOrDelFavorite: async (comicId, folderId, isAdding) => {
            let is_collect = isAdding ? 1 : 0
            let token = this.loadData("token");
            let reqId = await this.getReqID();
            let comicData = await this.networkGet(
                `${this.apiUrl}/api/v3/comic2/${comicId}?in_mainland=true&request_id=${reqId}&platform=3`,
                this.headers
            )
            if (comicData.status !== 200) {
                throw `Invalid status code: ${comicData.status}`
            }
            let comic_id = JSON.parse(comicData.body).results.comic.uuid
            let res = await this.networkPost(
                `${this.apiUrl}/api/v3/member/collect/comic`,
                {
                    ...this.headers,
                    "Content-Type": "application/x-www-form-urlencoded;charset=utf-8",
                },
                `comic_id=${comic_id}&is_collect=${is_collect}&authorization=Token+${token}`
            )
            if (res.status === 401) {
                throw `Login expired`;
            }
            if (res.status !== 200) {
                throw `Invalid status code: ${res.status}`
            }
            return "ok"
        },
        loadComics: async (page, folder) => {
            let ordering = this.loadSetting('favorites_ordering') || '-datetime_updated';
            var res = await this.networkGet(
                `${this.apiUrl}/api/v3/member/collect/comics?limit=30&offset=${(page - 1) * 30}&free_type=1&ordering=${ordering}`,
                this.headers
            )

            if (res.status === 401) {
                throw `Login expired`
            }

            if (res.status !== 200) {
                throw `Invalid status code: ${res.status}`
            }

            let data = JSON.parse(res.body)

            function parseComic(comic) {
                if (comic["comic"] !== null && comic["comic"] !== undefined) {
                    comic = comic["comic"]
                }
                let tags = []
                if (comic["theme"] !== null && comic["theme"] !== undefined) {
                    tags = comic["theme"].map(t => t["name"])
                }
                let author = null

                if (Array.isArray(comic["author"]) && comic["author"].length > 0) {
                    author = comic["author"][0]["name"]
                }

                return {
                    id: comic["path_word"],
                    title: comic["name"],
                    subTitle: author,
                    cover: comic["cover"],
                    tags: tags,
                    description: comic["datetime_updated"]
                }
            }

            return {
                comics: data["results"]["list"].map(parseComic),
                maxPage: Math.ceil(data["results"]["total"] / 30)
            }
        }
    }

    comic = {
        loadInfo: async (id) => {
            let getChapters = async (id, groups) => {
                let fetchSingle = async (id, path) => {
                    let reqId = await this.getReqID();
                    let eps = new Map();
                    let offset = 0;
                    let total = 0;
                    do {
                        let data = await this.requestResults(
                            offset === 0
                                ? `/api/v3/comic/${id}/group/${path}/chapters?limit=100&offset=0&in_mainland=true&request_id=${reqId}`
                                : `/api/v3/comic/${id}/group/${path}/chapters?limit=100&offset=${offset}&in_mainland=true&request_id=`
                        );
                        (data.list || []).forEach((e) => {
                            eps.set(e.uuid, e.name);
                        });
                        total = data.total || 0;
                        offset += 100;
                    } while (offset < total);
                    if (eps.size === 0) {
                        throw "拷贝漫画章节列表为空（可能触发风控），请稍后重试或更换线路";
                    }
                    return eps;
                };
                let keys = Object.keys(groups);
                let result = {};
                // 分组串行请求：并发会更快触发拷贝的频率风控
                for (let group of keys) {
                    let path = groups[group]["path_word"];
                    result[group] = await fetchSingle(id, path);
                }
                if (this.isAppVersionAfter("1.3.0")) {
                    // 支持多分组
                    let sortedResult = new Map();
                    for (let key of keys) {
                        let name = groups[key]["name"];
                        sortedResult.set(name, result[key]);
                    }
                    return sortedResult;
                } else {
                    // 合并所有分组
                    let merged = new Map();
                    for (let key of keys) {
                        for (let [k, v] of result[key]) {
                            merged.set(k, v);
                        }
                    }
                    return merged;
                }
            }

            let getFavoriteStatus = async (id) => {
                let results = await this.requestResults(`/api/v3/comic2/${id}/query`);
                return results.collect != null;
            }
            let reqId = await this.getReqID();
            let detailPath = `/api/v3/comic2/${id}?in_mainland=true&request_id=${reqId}&platform=3`;
            let data = await this.requestResults(detailPath);
            // 风控时不报错、只给空 groups，表现为“有书名但没有章节”，这里换线路再试一次
            if (!data.comic || !data.groups || Object.keys(data.groups).length === 0) {
                await new Promise((resolve) => setTimeout(resolve, 3000));
                this._hostSkip = (this._hostSkip || 0) + 1;
                data = await this.requestResults(detailPath);
            }
            if (!data.comic || !data.groups || Object.keys(data.groups).length === 0) {
                throw "拷贝漫画详情接口没有返回章节（可能触发风控），请稍后重试或更换线路";
            }
            let isFavorite = false;
            try {
                isFavorite = await getFavoriteStatus.bind(this)(id);
            } catch (e) {
                isFavorite = false;
            }
            let comicData = data.comic;

            let title = comicData.name;
            let cover = comicData.cover;
            let authors = comicData.author.map(e => e.name);
            // author_path_word_dict长度限制为最大100
            if (!this.author_path_word_dict) {
                this.author_path_word_dict = {};
            }
            if (Object.keys(this.author_path_word_dict).length > 100) {
                this.author_path_word_dict = {};
            }
            // 储存author对应的path_word
            comicData.author.forEach(e => (this.author_path_word_dict[e.name] = e.path_word));
            let tags = comicData.theme.map(e => e?.name).filter(name => name !== undefined && name !== null);
            let updateTime = comicData.datetime_updated ? comicData.datetime_updated : "";
            let description = comicData.brief;
            let chapters = await getChapters(id, data.groups);
            let status = comicData.status.display;

            return {
                title: title,
                cover: cover,
                description: description,
                tags: {
                    "作者": authors,
                    "更新": [updateTime],
                    "标签": tags,
                    "状态": [status],
                },
                chapters: chapters,
                isFavorite: isFavorite,
                subId: comicData.uuid
            }
        },
        loadEp: async (comicId, epId) => {
            // 1) 网页版优先：页面里 { cct, contentKey } 用 AES-128-CBC 加密了图片列表，
            //    网页接口没有 App 接口那套版本/破解检查，不会因为 210 卡住阅读。
            let webImages = null;
            try {
                webImages = await this.loadEpFromWeb(comicId, epId);
            } catch (e) {
                webImages = null;
            }
            if (webImages && webImages.length > 0) {
                return { images: webImages };
            }

            // 2) 回退到 App 接口
            let reqId = await this.getReqID();
            let data = await this.requestResults(
                `/api/v3/comic/${comicId}/chapter2/${epId}?in_mainland=true&request_id=${reqId}`,
                { tries: 3, wait: 10000 }
            );
            if (!data.chapter || !data.chapter.contents) {
                throw "拷贝漫画章节接口没有返回图片，可能触发风控，请稍后重试或更换线路";
            }
            // Handle image link sorting
            let imagesUrls = data.chapter.contents.map((e) => e.url);
            let orders = data.chapter.words;

            // Replace origin images urls to selected quality images urls
            let hdImagesUrls = imagesUrls.map((url) =>
                url.replace(/([./])c\d+x\.[a-zA-Z]+$/, `$1c${this.imageQuality}x.webp`)
            )
            // 站点只为部分尺寸预生成文件（实测 c1500x 有，c1200x/c900x 404），
            // 所以替换后先探测第一张，失败就整体退回接口原始地址。
            if (imagesUrls.length > 0 && hdImagesUrls[0] !== imagesUrls[0]) {
                let probe = null
                try {
                    probe = await this.networkGet(hdImagesUrls[0], this.headers)
                } catch (e) {
                    probe = null
                }
                if (!probe || probe.status !== 200) {
                    hdImagesUrls = imagesUrls
                }
            }

            let images = new Array(hdImagesUrls.length).fill(""); // Initialize an array with the same length as imagesUrls

            // Arrange images according to orders
            for (let i = 0; i < hdImagesUrls.length; i++) {
                images[orders[i]] = hdImagesUrls[i];
            }

            return {
                images: images,
            };
        },
        // 图片请求统一使用拷贝 App 的 UA，避免 CDN 因浏览器 UA 拒绝
        onImageLoad: (imageKey, comicId, epId) => ({
            url: imageKey,
            headers: {
                "User-Agent": CopyManga.userAgent,
                "referer": "com.copymanga.app-3.0.6",
            },
        }),
        loadComments: async (comicId, subId, page, replyTo) => {
            let url = `${this.apiUrl}/api/v3/comments?comic_id=${subId}&limit=20&offset=${(page - 1) * 20}`;
            if (replyTo) {
                url = url + `&reply_id=${replyTo}&_update=true`;
            }
            let res = await this.networkGet(
                url,
                this.headers,
            );

            if (res.status === 404) {
                // 当前线路没有评论接口（网页版后端），当作没有评论
                return { comments: [], maxPage: 1 };
            }
            if (res.status !== 200) {
                if (res.status === 210) {
                    throw "210：注冊用戶一天可以發5條評論"
                }
                throw `Invalid status code: ${res.status}`;
            }

            let data = JSON.parse(res.body);

            let total = data.results.total;

            return {
                comments: data.results.list.map(e => {
                    return {
                        userName: replyTo ? `${e.user_name}  👉  ${e.parent_user_name}` : e.user_name, // 拷贝的回复页并没有楼中楼（所有回复都在一个response中），但会显示谁回复了谁。所以加上👉显示。
                        avatar: e.user_avatar,
                        content: e.comment,
                        time: e.create_at,
                        replyCount: e.count,
                        id: e.id,
                    }
                }),
                maxPage: (total - (total % 20)) / 20 + 1,
            }
        },
        sendComment: async (comicId, subId, content, replyTo) => {
            let token = this.loadData("token");
            if (!token) {
                throw "未登录"
            }
            if (!replyTo) {
                replyTo = '';
            }
            let res = await this.networkPost(
                `${this.apiUrl}/api/v3/member/comment`,
                {
                    ...this.headers,
                    "Content-Type": "application/x-www-form-urlencoded;charset=utf-8",
                },
                `comic_id=${subId}&comment=${encodeURIComponent(content)}&reply_id=${replyTo}`,
            );

            if (res.status === 401) {
                error(`Login expired`);
                return;
            }

            if (res.status !== 200) {
                throw `Invalid status code: ${res.status}`;
            } else {
                return "ok"
            }
        },
        loadChapterComments: async (comicId, epId, page, replyTo) => {
            let url = `${this.apiUrl}/api/v3/roasts?chapter_id=${epId}&limit=20&offset=${(page - 1) * 20}`;
            let res = await this.networkGet(
                url,
                this.headers,
            );

            if (res.status !== 200) {
                throw `Invalid status code: ${res.status}`;
            }

            let data = JSON.parse(res.body);

            let total = data.results.total;

            return {
                comments: data.results.list.map(e => {
                    return {
                        userName: e.user_name,
                        avatar: e.user_avatar,
                        content: e.comment,
                        time: e.create_at,
                        replyCount: null,
                        id: null,
                    }
                }),
                maxPage: (total - (total % 20)) / 20 + 1,
            }
        },
        sendChapterComment: async (comicId, epId, content, replyTo) => {
            let token = this.loadData("token");
            if (!token) {
                throw "未登录"
            }
            let res = await this.networkPost(
                `${this.apiUrl}/api/v3/member/roast`,
                {
                    ...this.headers,
                    "Content-Type": "application/x-www-form-urlencoded;charset=utf-8",
                },
                `chapter_id=${epId}&roast=${encodeURIComponent(content)}`,
            );

            if (res.status === 401) {
                throw `Login expired`;
            }

            if (res.status !== 200) {
                if (res.status === 210) {
                    throw `210:评论过于频繁或评论内容过短过长`;
                }
                throw `Invalid status code: ${res.status}`;
            } else {
                return "ok"
            }
        },
        onClickTag: (namespace, tag) => {
            if (namespace === "标签") {
                return {
                    // 'search' or 'category'
                    action: 'category',
                    keyword: `${tag}`,
                    // {string?} only for category action
                    param: null,
                }
            }
            if (namespace === "作者") {
                return {
                    // 'search' or 'category'
                    action: 'search',
                    keyword: `${namespace}:${tag}`,
                    // {string?} only for category action
                    param: null,
                }
            }
            throw "未支持此类Tag检索"
        }
    }

    settings = {
        update_api: {
            title: "自动更新线路",
            type: "switch",
            default: true,
        },
        favorites_ordering: {
            title: "收藏排序方式",
            type: "select",
            options: [
                {
                    value: '-datetime_updated',
                    text: '更新时间'
                },
                {
                    value: '-datetime_modifier',
                    text: '收藏时间'
                },
                {
                    value: '-datetime_browse',
                    text: '阅读时间'
                }
            ],
            default: '-datetime_updated',
        },
        region: {
            title: "CDN线路",
            type: "select",
            options: [
                {
                    value: "1",
                    text: '大陆线路'
                },
                {
                    value: "0",
                    text: '海外线路'
                },
            ],
            default: CopyManga.defaultCopyRegion,
        },
        image_quality: {
            title: "图片质量",
            type: "select",
            options: [
                {
                    value: '800',
                    text: '低 (800)'
                },
                {
                    value: '1200',
                    text: '中 (1200)'
                },
                {
                    value: '1500',
                    text: '高 (1500)'
                }
            ],
            default: CopyManga.defaultImageQuality,
        },
        search_api: {
            title: "搜索方式",
            type: "select",
            options: [
                {
                    value: 'baseAPI',
                    text: '基础API'
                },
                {
                    value: 'webAPI',
                    text: '网页端API'
                }
            ],
            default: 'baseAPI'
        },
        base_url: {
            title: "API地址",
            type: "input",
            validator: '^(?!:\\/\\/)(?=.{1,253})([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$',
            default: CopyManga.defaultApiUrl,
        },
        clear_device_info: {
            title: "清除设备信息",
            type: "callback",
            buttonText: "点击清除设备信息",
            callback: () => {
                this.clearDeviceInfo();
            }
        },
        auto_clear_device_info: {
            title: "每次请求后自动清除设备信息",
            type: "switch",
            default: false,
        },
        // version: {
        //     title: "拷贝版本（重启APP生效）",
        //     type: "input",
        //     default: CopyManga.defaultCopyVersion,
        // },
        // platform: {
        //     title: "平台代号（重启APP生效）",
        //     type: "input",
        //     validator: '^\\d+(?:\\.\\d+)*$',
        //     default: CopyManga.defaultCopyPlatform,
        // },
    }

    /**
     * Check if the current app version is after the target version
     * @param target {string} target version
     * @returns {boolean} true if the current app version is after the target version
     */
    isAppVersionAfter(target) {
        let current = APP.version
        let targetArr = target.split('.')
        let currentArr = current.split('.')
        for (let i = 0; i < 3; i++) {
            if (parseInt(currentArr[i]) < parseInt(targetArr[i])) {
                return false
            }
        }
        return true
    }

    async refreshSearchApi() {
        // 站点前端里写的是 const countApi = "/api/kb/web/searchcl/comics"；
        // www.manga2026.xyz 的 countApi 是 /api/v3/search/count2（仅计数），不能当列表接口，
        // 所以只接受 /api/kb/web/ 前缀。多站点轮流试，不再依赖需要代理的 www.copy20.com。
        for (let host of this.webHostList) {
            let res
            try {
                res = await fetch(`https://${host}/search`)
            } catch (e) {
                continue
            }
            if (!res || res.status !== 200) {
                continue
            }
            let text = await res.text()
            let match = text.match(/const countApi = "([^"]+)"/)
            if (match && match[1] && match[1].indexOf("/api/kb/web/") === 0) {
                CopyManga.searchApi = match[1]
                return
            }
        }
    }

}
