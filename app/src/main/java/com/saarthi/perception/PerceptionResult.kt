package com.saarthi.perception

import android.view.accessibility.AccessibilityNodeInfo

/**
 * One full read of the foreground app's screen: [serialized] is the
 * LLM-safe text the agent's tool-calling prompt sees; [refMap] resolves
 * the "e4"-style ref strings that appear in it back to real nodes for
 * [ActionExecutor].
 *
 * [refMap] is marked `internal` as a documentation signal, not a
 * compiler-enforced boundary — this is a single Gradle module, so
 * `internal` doesn't actually stop `com.saarthi.agent` from reaching in.
 * The real, checkable rule is: nothing under `com.saarthi.agent` imports
 * `android.view.accessibility.*` at all. The agent only ever sees ref
 * strings, never a node.
 *
 * Rebuilt fully on every [ScreenPerception.capture] call, never
 * diffed/cached across passes — a stale [AccessibilityNodeInfo] crashes or
 * silently no-ops. [stale] distinguishes a genuine fresh read from a
 * timed-out traversal that fell back to the last good snapshot.
 *
 * [hasActionableElement] is separate from [isEmpty] — a transitional
 * screen caught mid-animation (e.g. only the status bar's own icons) can
 * have several kept nodes and so not be [isEmpty], while still having
 * nothing clickable/editable/scrollable/checkable on it at all. Defaults
 * to `false` so the empty-fallback constructions elsewhere in
 * [ScreenPerception] don't need to say so explicitly; [ScreenPerception]'s
 * real serialization path computes and passes the true value.
 */
data class PerceptionResult(
    val serialized: String,
    internal val refMap: Map<String, AccessibilityNodeInfo>,
    val stale: Boolean = false,
    val hasActionableElement: Boolean = false,
) {
    val isEmpty: Boolean get() = refMap.isEmpty()
}
