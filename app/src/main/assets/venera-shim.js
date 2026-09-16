/**
 * Venera WebView JS shim
 * 桥接 _venera.sendMessage() 与 init.js
 */

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

function sendMessage(message) {
    if (!message) return null;

    // delay 方法在 init.js 里使用 sendMessage({method:'delay', time}).then(...)
    if (message.method === 'delay') {
        var ms = message.time || 0;
        return new Promise(function(resolve) {
            window.setTimeout(function() {
                resolve(null);
            }, ms);
        });
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
        var serialized = _serializeMessage(res);
        _venera.postAsyncResult(callbackId, JSON.stringify({ success: true, data: serialized }));
    }).catch(function(err) {
        _venera.postAsyncResult(callbackId, JSON.stringify({ success: false, error: String(err) }));
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