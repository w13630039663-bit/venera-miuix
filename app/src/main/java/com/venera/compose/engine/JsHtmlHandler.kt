package com.venera.compose.engine

import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.LinkedHashMap

class DocumentWrapper(val doc: Document) {
    val elements = ArrayList<Element>()
    val nodes = ArrayList<Node>()

    /** 释放句柄表引用，配合文档回收避免句柄泄漏（jsoup Document 本身由 GC 回收） */
    fun free() {
        elements.clear()
        nodes.clear()
    }

    fun querySelector(query: String): Int? {
        val el = doc.selectFirst(query) ?: return null
        elements.add(el)
        return elements.size - 1
    }

    fun querySelectorAll(query: String): List<Int> {
        val list = doc.select(query)
        val keys = ArrayList<Int>(list.size)
        for (el in list) {
            elements.add(el)
            keys.add(elements.size - 1)
        }
        return keys
    }

    fun getElementById(id: String): Int? {
        val el = doc.getElementById(id) ?: return null
        elements.add(el)
        return elements.size - 1
    }

    /**
     * 对齐官方 `package:html` 的 `Element.text`（`_getText` + `_ConcatTextVisitor`）：
     * 递归拼接全部后代文本节点的**原文**，不做 trim、不折叠空白、不为 `<br>`/块级元素补空格。
     *
     * ⚠️ 绝不能直接用 jsoup 的 `Element.text()`，它与官方有三处真实差异
     * （ehentai 详情页 "Cannot read properties of undefined (reading 'text')" 就是第 1 条引起的）：
     * 1. jsoup 把 `<script>` / `<style>` 的内容存成 [DataNode]，而 `text()` 只遍历
     *    [TextNode] —— 脚本/样式内容被整个跳过，`text()` 恒为空串。
     *    于是 ehentai.js 的
     *    `document.querySelectorAll("script").find(e => e.text.includes("var token"))`
     *    恒返回 undefined，紧接着的 `script.text` 抛
     *    `Cannot read properties of undefined (reading 'text')`。
     *    package:html 没有 DataNode 概念，脚本内容是普通 Text 子节点，会原样返回源码。
     * 2. jsoup 的 `text()` 会 trim 首尾并折叠内部连续空白 —— 源脚本里
     *    `document.querySelector("a#favoritelink")?.text === " Add to Favorites"`
     *    （判据**带前导空格**）因此恒为 false，收藏态判定失效并连带走入错误的
     *    `div#fav` 解析分支。
     * 3. jsoup 会为 `<br>` 与块级元素插入空格，官方不插。
     */
    fun elementGetText(key: Int): String? {
        val el = elements.getOrNull(key) ?: return null
        val sb = StringBuilder()
        appendRawText(el, sb)
        return sb.toString()
    }

    /**
     * 与官方 `_ConcatTextVisitor` 等价的递归拼接：
     * - [TextNode] → 原文（`getWholeText()`，保留原始空白）
     * - [DataNode] → 原始数据（jsoup 里 `<script>`/`<style>` 的内容，官方是 Text）
     * - [Element] → 递归其子节点
     * - 其余（Comment / DocumentType / XmlDeclaration）→ 官方 visitor 不贡献文本，跳过
     */
    private fun appendRawText(node: Node, sb: StringBuilder) {
        for (child in node.childNodes()) {
            when (child) {
                is TextNode -> sb.append(child.getWholeText())
                is DataNode -> sb.append(child.getWholeData())
                is Element -> appendRawText(child, sb)
                else -> Unit
            }
        }
    }

    fun elementGetAttributes(key: Int): Map<String, String> {
        val el = elements.getOrNull(key) ?: return emptyMap()
        return el.attributes().associate { it.key to it.value }
    }

    fun elementGetInnerHTML(key: Int): String? {
        return elements.getOrNull(key)?.html()
    }

    fun elementGetParent(key: Int): Int? {
        val parent = elements.getOrNull(key)?.parent() ?: return null
        elements.add(parent)
        return elements.size - 1
    }

    fun elementQuerySelector(key: Int, query: String): Int? {
        val el = elements.getOrNull(key)?.selectFirst(query) ?: return null
        elements.add(el)
        return elements.size - 1
    }

    fun elementQuerySelectorAll(key: Int, query: String): List<Int> {
        val list = elements.getOrNull(key)?.select(query) ?: return emptyList()
        val keys = ArrayList<Int>(list.size)
        for (el in list) {
            elements.add(el)
            keys.add(elements.size - 1)
        }
        return keys
    }

    fun elementGetChildren(key: Int): List<Int> {
        val children = elements.getOrNull(key)?.children() ?: return emptyList()
        val keys = ArrayList<Int>(children.size)
        for (el in children) {
            elements.add(el)
            keys.add(elements.size - 1)
        }
        return keys
    }

    fun elementGetNodes(key: Int): List<Int> {
        val childNodes = elements.getOrNull(key)?.childNodes() ?: return emptyList()
        val keys = ArrayList<Int>(childNodes.size)
        for (node in childNodes) {
            nodes.add(node)
            keys.add(nodes.size - 1)
        }
        return keys
    }

    /**
     * 对齐官方 `DocumentWrapper.nodeGetText`（Dart 侧就是 `nodes[key].text`）：
     * 文本节点返回**原文**（jsoup 的 `TextNode.text()` 会折叠空白，与官方不同），
     * 元素节点走与 [elementGetText] 完全相同的拼接语义。
     */
    fun nodeGetText(key: Int): String? {
        val node = nodes.getOrNull(key) ?: return null
        return when (node) {
            is TextNode -> node.getWholeText()
            is DataNode -> node.getWholeData()
            is Element -> {
                val sb = StringBuilder()
                appendRawText(node, sb)
                sb.toString()
            }
            else -> node.toString()
        }
    }

    fun nodeType(key: Int): String {
        val node = nodes.getOrNull(key) ?: return "unknown"
        return when (node) {
            is Element -> "element"
            is TextNode -> "text"
            is Comment -> "comment"
            is Document -> "document"
            else -> "unknown"
        }
    }

    fun nodeToElement(key: Int): Int? {
        val node = nodes.getOrNull(key) ?: return null
        if (node is Element) {
            elements.add(node)
            return elements.size - 1
        }
        return null
    }

    fun getClassNames(key: Int): List<String> {
        return elements.getOrNull(key)?.classNames()?.toList() ?: emptyList()
    }

    fun getId(key: Int): String? {
        return elements.getOrNull(key)?.id()
    }

    fun getLocalName(key: Int): String? {
        return elements.getOrNull(key)?.tagName()
    }

    fun getPreviousSibling(key: Int): Int? {
        val el = elements.getOrNull(key)?.previousElementSibling() ?: return null
        elements.add(el)
        return elements.size - 1
    }

    fun getNextSibling(key: Int): Int? {
        val el = elements.getOrNull(key)?.nextElementSibling() ?: return null
        elements.add(el)
        return elements.size - 1
    }
}

class JsHtmlHandler {

    /** 文档句柄：包装 jsoup Document 并记录最后访问时间，用于空闲回收 */
    private class DocEntry(val wrapper: DocumentWrapper) {
        @Volatile
        var lastAccess: Long = System.currentTimeMillis()
        fun touch() {
            lastAccess = System.currentTimeMillis()
        }
    }

    /**
     * 文档缓存。
     *
     * ⚠️ 历史缺陷：这里是 accessOrder=true 且上限仅 **8** 的 LRU。
     * 全网 33 源并发检索时，每个源都会创建自己的 HtmlDocument，
     * 而 JS 侧从创建到真正执行 querySelectorAll 之间会 `await` 其它源的网络请求，
     * 于是活跃文档在解析途中就被驱逐 → `documents[docKey]` 取回 null →
     * `querySelectorAll` 返回空数组 → **该源零结果**（表现为"搜不到"）。
     *
     * 现策略：
     * 1. 上限提高到 40（足以覆盖 33 源并发 + 余量），正常检索期间不会驱逐。
     * 2. 空闲超过 120s 的文档按时间回收，保证长期内存有界（JS 侧不调用 dispose）。
     */
    private val documents = LinkedHashMap<Int, DocEntry>(16, 0.75f, true)

    private fun pruneIdleLocked() {
        val now = System.currentTimeMillis()
        val iterator = documents.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastAccess > IDLE_EVICT_MS) {
                iterator.remove()
                entry.value.wrapper.free()
            }
        }
    }

    private fun getLocked(key: Int): DocumentWrapper? {
        val entry = documents[key] ?: return null
        entry.touch()
        return entry.wrapper
    }

    @Synchronized
    fun handle(data: Map<String, Any?>): Any? {
        val fn = data["function"] as? String ?: return null
        return when (fn) {
            "parse" -> {
                val key = (data["key"] as? Number)?.toInt() ?: 0
                val html = data["data"]?.toString() ?: ""
                pruneIdleLocked()
                // 极端情况下的兜底：超过硬上限才按 LRU 驱逐
                if (documents.size >= MAX_DOCUMENTS) {
                    val eldest = documents.entries.firstOrNull()
                    if (eldest != null) {
                        eldest.value.wrapper.free()
                        documents.remove(eldest.key)
                        android.util.Log.w(
                            "VeneraJS",
                            "HtmlDocument cache overflow (${MAX_DOCUMENTS}), evicted key=${eldest.key}"
                        )
                    }
                }
                documents[key] = DocEntry(DocumentWrapper(Jsoup.parse(html)))
                null
            }
            "querySelector" -> {
                val key = (data["key"] as? Number)?.toInt() ?: return null
                val query = data["query"]?.toString() ?: ""
                getLocked(key)?.querySelector(query)
            }
            "querySelectorAll" -> {
                val key = (data["key"] as? Number)?.toInt() ?: return emptyList<Int>()
                val query = data["query"]?.toString() ?: ""
                getLocked(key)?.querySelectorAll(query) ?: emptyList<Int>()
            }
            "getElementById" -> {
                val key = (data["key"] as? Number)?.toInt() ?: return null
                val id = data["id"]?.toString() ?: ""
                getLocked(key)?.getElementById(id)
            }
            "getText" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                getLocked(docKey)?.elementGetText(key)
            }
            "getAttributes" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return emptyMap<String, String>()
                val key = (data["key"] as? Number)?.toInt() ?: return emptyMap<String, String>()
                getLocked(docKey)?.elementGetAttributes(key) ?: emptyMap<String, String>()
            }
            "getInnerHTML" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                getLocked(docKey)?.elementGetInnerHTML(key)
            }
            "getParent" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                getLocked(docKey)?.elementGetParent(key)
            }
            "dom_querySelector" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                val query = data["query"]?.toString() ?: ""
                getLocked(docKey)?.elementQuerySelector(key, query)
            }
            "dom_querySelectorAll" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return emptyList<Int>()
                val key = (data["key"] as? Number)?.toInt() ?: return emptyList<Int>()
                val query = data["query"]?.toString() ?: ""
                getLocked(docKey)?.elementQuerySelectorAll(key, query) ?: emptyList<Int>()
            }
            "getChildren" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return emptyList<Int>()
                val key = (data["key"] as? Number)?.toInt() ?: return emptyList<Int>()
                getLocked(docKey)?.elementGetChildren(key) ?: emptyList<Int>()
            }
            "getNodes" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return emptyList<Int>()
                val key = (data["key"] as? Number)?.toInt() ?: return emptyList<Int>()
                getLocked(docKey)?.elementGetNodes(key) ?: emptyList<Int>()
            }
            "node_text" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                getLocked(docKey)?.nodeGetText(key)
            }
            "node_type" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return "unknown"
                val key = (data["key"] as? Number)?.toInt() ?: return "unknown"
                getLocked(docKey)?.nodeType(key) ?: "unknown"
            }
            // 注意：JS 侧 HtmlNode.toElement() 发送的是 camelCase 的 "node_toElement"，
            // 旧实现只识别 snake_case 的 "node_to_element"，导致该 API 恒返回 null。
            "node_toElement", "node_to_element" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                getLocked(docKey)?.nodeToElement(key)
            }
            "getClassNames" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return emptyList<String>()
                val key = (data["key"] as? Number)?.toInt() ?: return emptyList<String>()
                getLocked(docKey)?.getClassNames(key) ?: emptyList<String>()
            }
            "getId" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                getLocked(docKey)?.getId(key)
            }
            "getLocalName" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                getLocked(docKey)?.getLocalName(key)
            }
            "getPreviousSibling" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                getLocked(docKey)?.getPreviousSibling(key)
            }
            "getNextSibling" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                getLocked(docKey)?.getNextSibling(key)
            }
            "dispose" -> {
                val key = (data["key"] as? Number)?.toInt() ?: return null
                documents.remove(key)?.wrapper?.free()
                null
            }
            else -> null
        }
    }

    companion object {
        private const val MAX_DOCUMENTS = 40
        private const val IDLE_EVICT_MS = 120_000L
    }
}
