/**
 * Venera WebView JS shim
 *
 * 桥接 _venera.sendMessage() 与 venera-init.js
 *
 * 设计要点（性能关键）：
 * 1. Android 的 @JavascriptInterface 全部在 WebView 的**单一 JavaBridge 线程**上串行执行。
 *    如果 HTTP 请求在该线程里同步阻塞，全网 33 源并发搜索会退化成排队串行，
 *    任何一个不可达的源都会独占线程直到 connect timeout，整体表现为"搜不到/一直转圈"。
 * 2. 因此 shim 把 `method === 'http'` 的调用改走**异步通道**：
 *    - JS 侧立即拿到一个 Promise（同步调用瞬间返回，不占用桥线程）
 *    - Kotlin 侧在独立线程池执行 OkHttp，完成后回调 window.__veneraResolve(reqId)
 *    - JS 侧再用一次**极快的同步拉取** _venera.takeAsyncResult(reqId) 取回 JSON 结果
 *    这样大响应体不需要被转义成 JS 源码字符串，也不会阻塞桥线程。
 * 3. 其余方法（delay / html / convert / setting / cookie ...）保持同步语义不变。
 */

/* ------------------------------------------------------------------ *
 * 原生定时器快照（必须在 venera-init.js 之前抓）
 *
 * venera-init.js 用**函数声明**覆写了全局定时器：
 *     function setTimeout(callback, delay) {
 *         sendMessage({ method: 'delay', time: delay }).then(callback);
 *     }
 * 而 shim 的 sendMessage 在收到 {method:'delay'} 时又去调 window.setTimeout ——
 * 此时 window.setTimeout 已经是上面那个被覆写的版本，于是形成
 * `sendMessage → setTimeout → sendMessage → …` 的**无限递归**，
 * 实测抛 `RangeError: Maximum call stack size exceeded`。
 *
 * 受影响的是所有在源脚本里用 setTimeout 的源（copy_manga / hot_manga /
 * mxs / copy_manga_multi_accounts），它们的重试与限速等待会直接崩掉。
 * 这里在 init.js 载入前抓住原生实现，delay 通道只走它。
 * ------------------------------------------------------------------ */

var _nativeSetTimeout = (function () {
    // 若本文件被二次求值（此时 init.js 已覆写过 setTimeout），沿用首次捕获的原生实现
    if (typeof _nativeSetTimeout === 'function') return _nativeSetTimeout;
    var raw = (typeof window !== 'undefined' && window.setTimeout) ? window.setTimeout : setTimeout;
    var owner = (typeof window !== 'undefined') ? window : null;
    return function (fn, ms) {
        return owner ? raw.call(owner, fn, ms) : raw(fn, ms);
    };
})();

function _arrayBufferToBase64(buffer) {
    var binary = '';
    var bytes = new Uint8Array(buffer);
    var len = bytes.byteLength;
    for (var i = 0; i < len; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
}

function _base64ToArrayBuffer(base64) {
    var binary_string = atob(base64);
    var len = binary_string.length;
    var bytes = new Uint8Array(len);
    for (var i = 0; i < len; i++) {
        bytes[i] = binary_string.charCodeAt(i);
    }
    return bytes.buffer;
}

function _serializeMessage(obj) {
    if (obj === null || obj === undefined) return obj;
    if (obj instanceof ArrayBuffer) {
        return { "__bytes_base64__": _arrayBufferToBase64(obj) };
    }
    if (ArrayBuffer.isView(obj)) {
        var buf = obj.buffer.slice(obj.byteOffset, obj.byteOffset + obj.byteLength);
        return { "__bytes_base64__": _arrayBufferToBase64(buf) };
    }
    if (obj instanceof Map) {
        var mapCopy = {};
        obj.forEach(function(val, key) {
            mapCopy[String(key)] = _serializeMessage(val);
        });
        return mapCopy;
    }
    if (obj instanceof Set) {
        return Array.from(obj).map(_serializeMessage);
    }
    if (Array.isArray(obj)) {
        return obj.map(_serializeMessage);
    }
    if (typeof obj === 'object') {
        var copy = {};
        for (var k in obj) {
            if (Object.prototype.hasOwnProperty.call(obj, k)) {
                copy[k] = _serializeMessage(obj[k]);
            }
        }
        return copy;
    }
    return obj;
}

function _deserializeResult(obj) {
    if (obj === null || obj === undefined) return obj;
    if (typeof obj === 'object') {
        if (obj["__bytes_base64__"] !== undefined) {
            return _base64ToArrayBuffer(obj["__bytes_base64__"]);
        }
        if (Array.isArray(obj)) {
            return obj.map(_deserializeResult);
        }
        var copy = {};
        for (var k in obj) {
            if (Object.prototype.hasOwnProperty.call(obj, k)) {
                copy[k] = _deserializeResult(obj[k]);
            }
        }
        return copy;
    }
    return obj;
}

/* ------------------------------------------------------------------ *
 * optionList → 传给源 load/loadNext 的 options 数组
 *
 * 严格对齐官方 Dart 侧的两段实现：
 *   1) parser.dart `_loadSearchData()`
 *        - 逐个解析 optionList[i].options（形如 "0-Doujinshi"）：
 *          空串或不含 "-" 的项**跳过**；否则以第一个 "-" 切分，键=前段、值=后段
 *        - defaultValue = (default == null) ? null : jsonEncode(default)
 *        - SearchOptions.defaultValue => defaultVal ?? options.keys.firstOrNull ?? ""
 *   2) search_page.dart `useDefaultOptions()`
 *        - options = searchOptions.map((e) => e.defaultValue).toList()
 *
 * 因此每个元素要么是 null，要么是"JSON 编码后的字符串"：
 *   - multi-select 源拿到 '["0","1"]'，可 JSON.parse（ehentai 正是如此）
 *   - select      源拿到 '"value"'（含引号，官方行为如此，保持一致）
 *   - 无 default  时回退到 options **首个插入键**（可能是空串，如 "-<none>"）
 *
 * ★ 早前实现直接传 []，导致 ehentai 的 JSON.parse(options[0]) 抛
 *   `SyntaxError: "undefined" is not valid JSON`，搜索整体不可用。
 * ------------------------------------------------------------------ */

function _veneraOptionValues(optionList) {
    var result = [];
    var list = optionList || [];

    for (var i = 0; i < list.length; i++) {
        var opt = list[i] || {};
        var rawOptions = opt.options || [];

        // LinkedHashMap 语义：必须保留**插入顺序**，不能用 Object.keys 的顺序
        // （JS 会把 "0".."9" 这类整数键提到最前，与官方不一致）
        var keys = [];
        var seen = {};
        for (var j = 0; j < rawOptions.length; j++) {
            var raw = String(rawOptions[j]);
            if (raw.length === 0 || raw.indexOf('-') < 0) continue;
            var idx = raw.indexOf('-');
            var k = raw.substring(0, idx);
            if (!Object.prototype.hasOwnProperty.call(seen, k)) {
                seen[k] = true;
                keys.push(k);
            }
        }

        var value;
        if (opt['default'] !== undefined && opt['default'] !== null) {
            value = JSON.stringify(opt['default']);
        } else {
            value = keys.length > 0 ? keys[0] : "";
        }
        result.push(value);
    }

    return result;
}

/* ------------------------------------------------------------------ *
 * 异步通道（HTTP / 计算密集型调用）
 * ------------------------------------------------------------------ */

var _asyncRequestSeq = 0;
var _asyncPending = {};

/** 发起一次异步桥调用，返回 Promise */
function _sendMessageAsync(message) {
    return new Promise(function(resolve, reject) {
        var reqId = 'a' + (++_asyncRequestSeq);
        _asyncPending[reqId] = { resolve: resolve, reject: reject };

        var payload;
        try {
            payload = JSON.stringify(_serializeMessage(message));
        } catch (e) {
            delete _asyncPending[reqId];
            reject("serialize failed: " + String(e));
            return;
        }

        try {
            // Kotlin 侧立即返回，不阻塞桥线程
            _venera.sendMessageAsync(reqId, payload);
        } catch (e) {
            delete _asyncPending[reqId];
            reject(String(e));
        }
    });
}

/** 供 Kotlin 在后台任务完成后回调（必须挂在 window 上） */
window.__veneraResolve = function(reqId) {
    var pending = _asyncPending[reqId];
    if (!pending) return;
    delete _asyncPending[reqId];

    var raw;
    try {
        raw = _venera.takeAsyncResult(reqId);
    } catch (e) {
        pending.reject("takeAsyncResult failed: " + String(e));
        return;
    }

    if (raw === null || raw === undefined || raw === "") {
        pending.reject("empty async result");
        return;
    }

    var parsed;
    try {
        parsed = JSON.parse(raw);
    } catch (e) {
        pending.reject("bad async payload: " + String(e));
        return;
    }

    if (parsed && parsed.__error__ !== undefined && parsed.__error__ !== null) {
        pending.reject(parsed.__error__);
        return;
    }

    var data = (parsed && Object.prototype.hasOwnProperty.call(parsed, "__result__"))
        ? parsed.__result__
        : parsed;

    try {
        pending.resolve(_deserializeResult(data));
    } catch (e) {
        pending.reject(String(e));
    }
};

/* ------------------------------------------------------------------ *
 * 同步通道（内存级快速调用）
 * ------------------------------------------------------------------ */

function sendMessage(message) {
    if (!message) return null;

    // delay 方法在 init.js 里使用 sendMessage({method:'delay', time}).then(...)
    // ⚠️ 必须用 _nativeSetTimeout：init.js 覆写后的 setTimeout 会回到 sendMessage，
    //    直接调它会造成无限递归（详见文件顶部的说明）。
    if (message.method === 'delay') {
        var ms = message.time || 0;
        return new Promise(function(resolve) {
            _nativeSetTimeout(function() {
                resolve(null);
            }, ms);
        });
    }

    // 网络请求与 compute 走异步通道，避免占用唯一的 JavaBridge 线程
    if (message.method === 'http' || message.method === 'compute') {
        return _sendMessageAsync(message);
    }

    try {
        var serialized = _serializeMessage(message);
        var rawResult = _venera.sendMessage(JSON.stringify(serialized));
        if (rawResult === null || rawResult === undefined || rawResult === "null" || rawResult === "") {
            return null;
        }
        var parsed = JSON.parse(rawResult);
        if (parsed && typeof parsed === 'object' && Object.prototype.hasOwnProperty.call(parsed, "__result__")) {
            return _deserializeResult(parsed.__result__);
        }
        return _deserializeResult(parsed);
    } catch (e) {
        if (typeof console !== "undefined" && console.error) {
            console.error("sendMessage error:", e);
        }
        return null;
    }
}

// 异步执行桥接器，供 Kotlin 侧 evaluateAsync 调用
function _runAsync(callbackId, promiseOrValue) {
    Promise.resolve(promiseOrValue).then(function(res) {
        var serialized;
        try {
            serialized = JSON.stringify({ success: true, data: _serializeMessage(res) });
        } catch (e) {
            serialized = JSON.stringify({ success: false, error: "serialize failed: " + String(e) });
        }
        _venera.postAsyncResult(callbackId, serialized);
    }).catch(function(err) {
        // 错误信封带上 stack 中含行号的首行，否则「undefined 的 text」这类
        // TypeError 在无行号时只能靠猜。
        var msg = (err && err.message) ? err.message : String(err);
        var loc = "";
        try {
            if (err && err.stack) {
                var lines = String(err.stack).split("\n");
                for (var i = 0; i < lines.length; i++) {
                    if (lines[i].indexOf("sources/") !== -1 || lines[i].indexOf(".js:") !== -1) {
                        loc = " @ " + lines[i].trim();
                        break;
                    }
                }
            }
        } catch (_) {}
        _venera.postAsyncResult(callbackId, JSON.stringify({ success: false, error: msg + loc }));
    });
}

// 注入全局应用版本号
var appVersion = "1.0.0";

// Console 拦截与输出到 Logcat
if (typeof console === "undefined" || !console.log) {
    var console = {
        log: function(msg) { _venera.log(String(msg)); },
        error: function(msg) { _venera.log("ERROR: " + String(msg)); },
        warn: function(msg) { _venera.log("WARN: " + String(msg)); },
        info: function(msg) { _venera.log("INFO: " + String(msg)); }
    };
}

/* ------------------------------------------------------------------ *
 * 字节表示差异补偿
 *
 * 官方引擎（flutter_qjs）里，原生侧返回的字节是真正的 ArrayBuffer，
 * 所以 init.js 的 `Convert.hexEncode` 可以直接 `new Uint8Array(bytes)`。
 *
 * 而我们的桥走 JSON，字节被表示成 `{__bytes_base64__: "..."}`。
 * 直接 `new Uint8Array({...})` 会得到**长度 0** 的空视图（对象没有 length），
 * 于是 hexEncode 返回空串 → 依赖它生成密钥的源（jm / zaimanhua）
 * 会拿到空 AES key → 解密出乱码 → 最终在 `JSON.parse` 处报
 * "undefined" is not valid JSON / "Unexpected token ','" 。
 *
 * 这里在 init.js 加载**之后**把 hexEncode 包一层，把字节表示还原成真
 * ArrayBuffer。init.js 本身保持官方原样。
 * ------------------------------------------------------------------ */
function _veneraBase64ToArrayBuffer(b64) {
    var bin = atob(b64);
    var len = bin.length;
    var bytes = new Uint8Array(len);
    for (var i = 0; i < len; i++) bytes[i] = bin.charCodeAt(i) & 0xff;
    return bytes.buffer;
}

/** 把桥的字节表示还原成真正的 ArrayBuffer；非字节值原样返回 */
function _veneraToArrayBuffer(v) {
    if (v == null) return v;
    if (typeof ArrayBuffer !== "undefined") {
        if (v instanceof ArrayBuffer) return v;
        if (ArrayBuffer.isView && ArrayBuffer.isView(v)) return v;
    }
    if (typeof v === "object" && typeof v.__bytes_base64__ === "string") {
        return _veneraBase64ToArrayBuffer(v.__bytes_base64__);
    }
    if (Object.prototype.toString.call(v) === "[object Array]") {
        return new Uint8Array(v).buffer;
    }
    return v;
}

/**
 * 在 init.js 求值完成后调用，修正依赖平台字节表示的函数。
 * @returns {boolean} 是否成功打上补丁
 */
function _veneraApplyPostInitPatches() {
    if (typeof Convert === "undefined" || !Convert) return false;
    if (typeof Convert.hexEncode !== "function") return false;
    // 避免重复包裹
    if (Convert.hexEncode.__veneraPatched === true) return true;
    var orig = Convert.hexEncode;
    var patched = function (bytes) {
        return orig.call(Convert, _veneraToArrayBuffer(bytes));
    };
    patched.__veneraPatched = true;
    Convert.hexEncode = patched;
    return true;
}
