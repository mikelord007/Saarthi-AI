package com.saarthi.perception

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Shared node-text helpers used by both [ScreenPerception] (to merge a
 * clickable container's descendant labels into one line instead of N
 * separate sibling lines) and [IrreversibleActionGuard] (to match
 * payment/confirm keywords against text that lives on a descendant, not
 * the clickable node itself — a "Pay" label is usually a child of the row
 * that's actually clickable, not the row itself).
 *
 * Every getter is wrapped defensively: a stale [AccessibilityNodeInfo]
 * throws [RuntimeException] on almost any accessor, and one throw partway
 * through a recursive walk would otherwise lose the whole subtree.
 */
internal object NodeText {

    /** Trimmed [AccessibilityNodeInfo.getText], falling back to [AccessibilityNodeInfo.getContentDescription]. Null if both are blank. */
    fun ownLabel(node: AccessibilityNodeInfo): String? {
        val text = runCatching { node.text?.toString()?.trim() }.getOrNull()
        if (!text.isNullOrEmpty()) return text
        val description = runCatching { node.contentDescription?.toString()?.trim() }.getOrNull()
        return description?.takeIf { it.isNotEmpty() }
    }

    /** Concatenates every descendant's [ownLabel] up to [maxDepth] levels down, in DFS order. */
    fun collectDescendantText(node: AccessibilityNodeInfo, maxDepth: Int): String {
        val parts = mutableListOf<String>()
        fun walk(current: AccessibilityNodeInfo, depth: Int) {
            if (depth > maxDepth) return
            val childCount = runCatching { current.childCount }.getOrDefault(0)
            for (i in 0 until childCount) {
                val child = runCatching { current.getChild(i) }.getOrNull() ?: continue
                ownLabel(child)?.let { parts += it }
                walk(child, depth + 1)
            }
        }
        walk(node, 0)
        return parts.joinToString(" ")
    }

    /** True if any descendant, up to [maxDepth] levels down, is independently interactive. */
    fun hasActionableDescendant(node: AccessibilityNodeInfo, maxDepth: Int): Boolean {
        if (maxDepth < 0) return false
        val childCount = runCatching { node.childCount }.getOrDefault(0)
        for (i in 0 until childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            val actionable = runCatching {
                child.isClickable || child.isEditable || child.isScrollable || child.isCheckable || child.isLongClickable
            }.getOrDefault(false)
            if (actionable) return true
            if (hasActionableDescendant(child, maxDepth - 1)) return true
        }
        return false
    }
}
