package com.venera.compose.engine

import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.LinkedHashMap

class DocumentWrapper(val doc: Document) {
    val elements = ArrayList<Element>()
    val nodes = ArrayList<Node>()

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

    fun elementGetText(key: Int): String? {
        return elements.getOrNull(key)?.text()
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

    fun nodeGetText(key: Int): String? {
        val node = nodes.getOrNull(key) ?: return null
        return if (node is TextNode) node.text() else node.toString()
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
    private val documents = object : LinkedHashMap<Int, DocumentWrapper>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, DocumentWrapper>?): Boolean {
            return size > 8
        }
    }

    @Synchronized
    fun handle(data: Map<String, Any?>): Any? {
        val fn = data["function"] as? String ?: return null
        return when (fn) {
            "parse" -> {
                val key = (data["key"] as? Number)?.toInt() ?: 0
                val html = data["data"]?.toString() ?: ""
                documents[key] = DocumentWrapper(Jsoup.parse(html))
                null
            }
            "querySelector" -> {
                val key = (data["key"] as? Number)?.toInt() ?: return null
                val query = data["query"]?.toString() ?: ""
                documents[key]?.querySelector(query)
            }
            "querySelectorAll" -> {
                val key = (data["key"] as? Number)?.toInt() ?: return emptyList<Int>()
                val query = data["query"]?.toString() ?: ""
                documents[key]?.querySelectorAll(query) ?: emptyList<Int>()
            }
            "getElementById" -> {
                val key = (data["key"] as? Number)?.toInt() ?: return null
                val id = data["id"]?.toString() ?: ""
                documents[key]?.getElementById(id)
            }
            "getText" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                documents[docKey]?.elementGetText(key)
            }
            "getAttributes" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return emptyMap<String, String>()
                val key = (data["key"] as? Number)?.toInt() ?: return emptyMap<String, String>()
                documents[docKey]?.elementGetAttributes(key) ?: emptyMap<String, String>()
            }
            "getInnerHTML" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                documents[docKey]?.elementGetInnerHTML(key)
            }
            "getParent" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                documents[docKey]?.elementGetParent(key)
            }
            "dom_querySelector" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                val query = data["query"]?.toString() ?: ""
                documents[docKey]?.elementQuerySelector(key, query)
            }
            "dom_querySelectorAll" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return emptyList<Int>()
                val key = (data["key"] as? Number)?.toInt() ?: return emptyList<Int>()
                val query = data["query"]?.toString() ?: ""
                documents[docKey]?.elementQuerySelectorAll(key, query) ?: emptyList<Int>()
            }
            "getChildren" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return emptyList<Int>()
                val key = (data["key"] as? Number)?.toInt() ?: return emptyList<Int>()
                documents[docKey]?.elementGetChildren(key) ?: emptyList<Int>()
            }
            "getNodes" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return emptyList<Int>()
                val key = (data["key"] as? Number)?.toInt() ?: return emptyList<Int>()
                documents[docKey]?.elementGetNodes(key) ?: emptyList<Int>()
            }
            "node_text" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                documents[docKey]?.nodeGetText(key)
            }
            "node_type" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return "unknown"
                val key = (data["key"] as? Number)?.toInt() ?: return "unknown"
                documents[docKey]?.nodeType(key) ?: "unknown"
            }
            "node_to_element" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                documents[docKey]?.nodeToElement(key)
            }
            "getClassNames" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return emptyList<String>()
                val key = (data["key"] as? Number)?.toInt() ?: return emptyList<String>()
                documents[docKey]?.getClassNames(key) ?: emptyList<String>()
            }
            "getId" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                documents[docKey]?.getId(key)
            }
            "getLocalName" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                documents[docKey]?.getLocalName(key)
            }
            "getPreviousSibling" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                documents[docKey]?.getPreviousSibling(key)
            }
            "getNextSibling" -> {
                val docKey = (data["doc"] as? Number)?.toInt() ?: return null
                val key = (data["key"] as? Number)?.toInt() ?: return null
                documents[docKey]?.getNextSibling(key)
            }
            "dispose" -> {
                val key = (data["key"] as? Number)?.toInt() ?: return null
                documents.remove(key)
                null
            }
            else -> null
        }
    }
}
