package com.saarthi.perception

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAP_DURATION_MS = 50L
private const val LONG_PRESS_DURATION_MS = 600L
private const val DEFAULT_SWIPE_DURATION_MS = 300L
private const val MIN_SWIPE_DURATION_MS = 10L
private const val MAX_SWIPE_DURATION_MS = 5000L
private const val MAX_ANCESTOR_WALK = 30

enum class ScrollDirection { UP, DOWN }

/** Never a boolean — callers need to distinguish "guard fired" from "genuinely failed." */
sealed interface ActionResult {
    data object Success : ActionResult
    data class Blocked(val label: String, val matchedKeyword: String) : ActionResult
    data class Failed(val reason: String) : ActionResult
}

/**
 * Executes one action against a ref from the *latest* [PerceptionResult] —
 * refs are only ever valid for the capture pass that produced them; the
 * caller is always the [AgentLoop][com.saarthi.agent.AgentLoop] step that
 * just perceived. Every action awaits
 * [SaarthiAccessibilityService.awaitSettled] before returning — never
 * re-perceive immediately after acting.
 */
object ActionExecutor {

    suspend fun tap(service: SaarthiAccessibilityService, perception: PerceptionResult, ref: String): ActionResult {
        val node = perception.refMap[ref]
            ?: return ActionResult.Failed("no element with ref $ref in the last perception")

        guard(node)?.let { return it }

        // Walk up from the target node through ancestors looking for the
        // first isClickable node and fire ACTION_CLICK on it — the model
        // frequently targets a label TextView instead of the clickable row
        // wrapping it.
        val clickableNode = nearestClickableAncestor(node)
        val clicked = clickableNode?.let {
            runCatching { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
        } ?: false

        if (!clicked) {
            val bounds = boundsOf(node)
            if (!hasValidBounds(bounds)) {
                return ActionResult.Failed("element has no valid on-screen bounds — needs scrolling into view")
            }
            val path = Path().apply { moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
                .build()
            if (!dispatchGesture(service, gesture)) return ActionResult.Failed("tap gesture dispatch failed")
        }

        service.awaitSettled()
        return ActionResult.Success
    }

    suspend fun longPress(service: SaarthiAccessibilityService, perception: PerceptionResult, ref: String): ActionResult {
        val node = perception.refMap[ref]
            ?: return ActionResult.Failed("no element with ref $ref in the last perception")

        guard(node)?.let { return it }

        val performed = runCatching { node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) }.getOrDefault(false)
        if (!performed) {
            val bounds = boundsOf(node)
            if (!hasValidBounds(bounds)) {
                return ActionResult.Failed("element has no valid on-screen bounds — needs scrolling into view")
            }
            val path = Path().apply { moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, LONG_PRESS_DURATION_MS))
                .build()
            if (!dispatchGesture(service, gesture)) return ActionResult.Failed("long-press gesture dispatch failed")
        }

        service.awaitSettled()
        return ActionResult.Success
    }

    /**
     * `ACTION_SET_TEXT` first, then verify by re-reading the node's text.
     * If it didn't take — common on Compose/WebView/password fields —
     * falls back to focus + clipboard + `ACTION_PASTE` and re-verifies.
     * Returns [ActionResult.Failed] honestly if it still didn't take,
     * rather than reporting success.
     */
    suspend fun setText(
        service: SaarthiAccessibilityService,
        perception: PerceptionResult,
        ref: String,
        text: String,
        clear: Boolean,
    ): ActionResult {
        val node = perception.refMap[ref]
            ?: return ActionResult.Failed("no element with ref $ref in the last perception")

        if (clear) {
            runCatching {
                node.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "") },
                )
            }
        }

        val setArgs = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs) }

        // Park the cursor at the end of the inserted text.
        runCatching {
            node.performAction(
                AccessibilityNodeInfo.ACTION_SET_SELECTION,
                Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
                },
            )
        }

        runCatching { node.refresh() }
        if (runCatching { node.text?.toString() }.getOrNull() == text) {
            service.awaitSettled()
            return ActionResult.Success
        }

        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("saarthi", text))
        val pasted = runCatching { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) }.getOrDefault(false)
        runCatching { node.refresh() }
        val afterPaste = runCatching { node.text?.toString() }.getOrNull()

        service.awaitSettled()
        return if (pasted && afterPaste == text) {
            ActionResult.Success
        } else {
            ActionResult.Failed("setText($ref) did not take, even after clipboard fallback")
        }
    }

    /**
     * Prefers `ACTION_SCROLL_FORWARD`/`ACTION_SCROLL_BACKWARD` on the
     * innermost scrollable (see [findInnermostScrollable]); falls back to
     * a clamped swipe gesture over its bounds if the node action fails.
     */
    suspend fun scroll(service: SaarthiAccessibilityService, perception: PerceptionResult, direction: ScrollDirection): ActionResult {
        val scrollable = findInnermostScrollable(perception.refMap.values)
            ?: return ActionResult.Failed("no scrollable element found on screen")

        val action = if (direction == ScrollDirection.DOWN) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val performed = runCatching { scrollable.performAction(action) }.getOrDefault(false)

        if (!performed) {
            val bounds = boundsOf(scrollable)
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                return ActionResult.Failed("scrollable element has no valid bounds for a swipe fallback")
            }
            val quarterHeight = bounds.height() / 4
            val startY = if (direction == ScrollDirection.DOWN) bounds.bottom - quarterHeight else bounds.top + quarterHeight
            val endY = if (direction == ScrollDirection.DOWN) bounds.top + quarterHeight else bounds.bottom - quarterHeight
            val x = bounds.centerX().toFloat()
            val path = Path().apply {
                moveTo(x, startY.toFloat())
                lineTo(x, endY.toFloat())
            }
            val duration = DEFAULT_SWIPE_DURATION_MS.coerceIn(MIN_SWIPE_DURATION_MS, MAX_SWIPE_DURATION_MS)
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            if (!dispatchGesture(service, gesture)) return ActionResult.Failed("scroll swipe dispatch failed")
        }

        service.awaitSettled()
        return ActionResult.Success
    }

    /** Submits the focused field: `ACTION_IME_ENTER` (API 30+) on the focused node, falling back to injecting a newline. */
    suspend fun keyboardEnter(service: SaarthiAccessibilityService, perception: PerceptionResult): ActionResult {
        val focused = perception.refMap.values.firstOrNull { runCatching { it.isFocused }.getOrDefault(false) }
            ?: runCatching { service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
            ?: return ActionResult.Failed("no focused field to submit")

        val submitted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id) }.getOrDefault(false)
        } else {
            false
        }

        if (!submitted) {
            val current = runCatching { focused.text?.toString() }.getOrNull().orEmpty()
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "$current\n")
            }
            val injected = runCatching { focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }.getOrDefault(false)
            if (!injected) return ActionResult.Failed("could not submit the focused field")
        }

        service.awaitSettled()
        return ActionResult.Success
    }

    suspend fun back(service: SaarthiAccessibilityService): ActionResult {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        service.awaitSettled()
        return ActionResult.Success
    }

    suspend fun home(service: SaarthiAccessibilityService): ActionResult {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        service.awaitSettled()
        return ActionResult.Success
    }

    suspend fun openNotifications(service: SaarthiAccessibilityService): ActionResult {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        service.awaitSettled()
        return ActionResult.Success
    }

    suspend fun openQuickSettings(service: SaarthiAccessibilityService): ActionResult {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        service.awaitSettled()
        return ActionResult.Success
    }

    // ---- shared helpers -----------------------------------------------------

    /** Runs [IrreversibleActionGuard] and, on a match, returns [ActionResult.Blocked] without performing anything. */
    private fun guard(node: AccessibilityNodeInfo): ActionResult.Blocked? {
        val match = IrreversibleActionGuard.check(node) ?: return null
        val label = match.label.ifBlank { NodeText.ownLabel(node) ?: "this button" }
        return ActionResult.Blocked(label = label, matchedKeyword = match.matchedKeyword)
    }

    private fun nearestClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < MAX_ANCESTOR_WALK) {
            if (runCatching { current!!.isClickable }.getOrDefault(false)) return current
            current = runCatching { current!!.parent }.getOrNull()
            depth++
        }
        return null
    }

    /**
     * When multiple scrollable candidates exist, prefers whichever one is a
     * descendant of another scrollable candidate — outer wrapper
     * ScrollViews/collapsing headers often report `isScrollable=true` with
     * a bigger footprint than the actual nested content list; the inner
     * one is the real list.
     */
    private fun findInnermostScrollable(nodes: Collection<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val scrollables = nodes.filter { runCatching { it.isScrollable }.getOrDefault(false) }
        if (scrollables.isEmpty()) return null
        return scrollables.firstOrNull { candidate ->
            scrollables.any { other -> other !== candidate && isDescendantOf(candidate, other) }
        } ?: scrollables.first()
    }

    private fun isDescendantOf(node: AccessibilityNodeInfo, potentialAncestor: AccessibilityNodeInfo): Boolean {
        var current = runCatching { node.parent }.getOrNull()
        var depth = 0
        while (current != null && depth < MAX_ANCESTOR_WALK) {
            if (current === potentialAncestor) return true
            current = runCatching { current!!.parent }.getOrNull()
            depth++
        }
        return false
    }

    private fun boundsOf(node: AccessibilityNodeInfo): Rect {
        val bounds = Rect()
        runCatching { node.getBoundsInScreen(bounds) }
        return bounds
    }

    private fun hasValidBounds(bounds: Rect): Boolean =
        bounds.left < bounds.right && bounds.top < bounds.bottom && bounds.right > 0 && bounds.bottom > 0

    private suspend fun dispatchGesture(service: AccessibilityService, gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { continuation ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            val dispatched = runCatching { service.dispatchGesture(gesture, callback, null) }.getOrDefault(false)
            if (!dispatched && continuation.isActive) continuation.resume(false)
        }
}
