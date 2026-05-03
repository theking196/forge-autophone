package com.forge.autophone.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Walks the current accessibility window tree and serialises it to JSON.
 *
 * Output shape:
 * ```json
 * {
 *   "app": "com.instagram.android",
 *   "windowTitle": "Direct",
 *   "elements": [
 *     { "text": "Send message", "desc": "Send", "class": "Button",
 *       "clickable": true, "scrollable": false,
 *       "bounds": {"l":0,"t":1600,"r":1080,"b":1700} }
 *   ]
 * }
 * ```
 */
object ScreenReader {

    private val gson = GsonBuilder().serializeNulls().create()
    private const val MAX_DEPTH = 12
    private const val MAX_NODES = 250

    fun read(rootNode: AccessibilityNodeInfo?, packageName: CharSequence?, windowTitle: String?): String {
        val json = JsonObject()
        json.addProperty("app",         packageName?.toString() ?: "unknown")
        json.addProperty("windowTitle", windowTitle ?: "")

        val elements = JsonArray()
        if (rootNode != null) {
            val counter = intArrayOf(0)
            walkNode(rootNode, elements, 0, counter)
        }
        json.add("elements", elements)
        json.addProperty("elementCount", elements.size())
        return gson.toJson(json)
    }

    private fun walkNode(node: AccessibilityNodeInfo, out: JsonArray, depth: Int, counter: IntArray) {
        if (depth > MAX_DEPTH || counter[0] >= MAX_NODES) return
        counter[0]++

        val text  = node.text?.toString()?.trim()
        val desc  = node.contentDescription?.toString()?.trim()
        val cls   = node.className?.toString()?.substringAfterLast('.')

        // Only emit nodes that carry meaningful info or are actionable
        val hasText   = !text.isNullOrEmpty()
        val hasDesc   = !desc.isNullOrEmpty()
        val clickable = node.isClickable
        val scrollable = node.isScrollable
        val editable  = node.isEditable

        if (hasText || hasDesc || clickable || scrollable || editable) {
            val el = JsonObject()
            if (hasText)   el.addProperty("text",      text)
            if (hasDesc)   el.addProperty("desc",      desc)
            if (cls != null) el.addProperty("class",   cls)
            el.addProperty("clickable",  clickable)
            el.addProperty("scrollable", scrollable)
            el.addProperty("editable",   editable)

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val b = JsonObject()
            b.addProperty("l", bounds.left)
            b.addProperty("t", bounds.top)
            b.addProperty("r", bounds.right)
            b.addProperty("b", bounds.bottom)
            el.add("bounds", b)

            out.add(el)
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                walkNode(child, out, depth + 1, counter)
                child.recycle()
            }
        }
    }
}
