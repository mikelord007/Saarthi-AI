package com.saarthi.perception

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

private const val MAX_LABEL_LENGTH = 80
private const val NODE_BUDGET = 150
private const val MAX_TREE_DEPTH = 128
private const val DESCENDANT_MERGE_DEPTH = 3
private const val HOME_RETRY_WAIT_MS = 1200L
private const val TRAVERSAL_TIMEOUT_MS = 2000L

/**
 * Turns whatever app is in the foreground into a compact, LLM-safe text
 * representation plus a ref -> node map [ActionExecutor] can act on.
 * Shared by narration reads and the agent loop alike.
 *
 * Root resolution deliberately never uses `rootInActiveWindow` alone (see
 * [resolveRoots]) — that alone misses dialogs, permission prompts, and
 * autocomplete popups stacked above the active window, exactly the
 * screens [IrreversibleActionGuard] and the login-wall check need to see.
 *
 * `AccessibilityNodeInfo` binder reads are not interruptible, so a
 * traversal that hangs on a wedged app can't be cancelled out from under
 * — it can only be given up on. Traversal therefore runs single-flight
 * (concurrent callers share one in-flight read rather than racing separate
 * ones over the same node cache) on a dedicated single-thread dispatcher
 * (kept off the shared IO pool so it never contends with the agent's HTTP
 * calls), and a caller that times out gets the last good snapshot back
 * instead of blocking — the orphaned read keeps running in the background
 * and, if it eventually completes, still updates the cache for next time.
 */
object ScreenPerception {

    private val traversalDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ScreenPerception-Traversal")
    }.asCoroutineDispatcher()
    private val traversalScope = CoroutineScope(SupervisorJob() + traversalDispatcher)

    private val inFlightLock = Mutex()
    private var inFlight: Deferred<PerceptionResult>? = null

    @Volatile
    private var lastGood: PerceptionResult? = null

    /**
     * @param goHomeIfEmpty Only true for the very first perception of a
     *   brand-new task. If no window resolves, presses HOME once — never
     *   once per retry, since re-pressing on a retry can interrupt a
     *   transition already in flight — and retries root resolution up to
     *   3x. Mid-task perceptions must pass false: going home mid-task
     *   would cancel navigation the agent just performed.
     */
    suspend fun capture(context: Context, goHomeIfEmpty: Boolean): PerceptionResult {
        val service = SaarthiAccessibilityService.current()
            ?: return PerceptionResult(serialized = "", refMap = emptyMap())

        var roots = resolveRoots(service, context.packageName)

        if (roots.isEmpty() && goHomeIfEmpty) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            var attempt = 0
            while (roots.isEmpty() && attempt < 3) {
                service.awaitContentChanged(HOME_RETRY_WAIT_MS)
                roots = resolveRoots(service, context.packageName)
                attempt++
            }
        }

        if (roots.isEmpty()) {
            return PerceptionResult(serialized = "", refMap = emptyMap())
        }

        val screenBounds = Rect(
            0, 0,
            context.resources.displayMetrics.widthPixels,
            context.resources.displayMetrics.heightPixels,
        )
        val deferred = traversalDeferred(roots, screenBounds)
        val fresh = withTimeoutOrNull(TRAVERSAL_TIMEOUT_MS) { deferred.await() }

        return if (fresh != null) {
            lastGood = fresh
            fresh
        } else {
            lastGood?.copy(stale = true) ?: PerceptionResult(serialized = "", refMap = emptyMap(), stale = true)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class) // Deferred.getCompleted() in the completion callback below
    private suspend fun traversalDeferred(roots: List<AccessibilityNodeInfo>, screenBounds: Rect): Deferred<PerceptionResult> =
        inFlightLock.withLock {
            val existing = inFlight
            if (existing != null && existing.isActive) return@withLock existing
            val created = traversalScope.async { buildResult(roots, screenBounds) }
            inFlight = created
            created.invokeOnCompletion { cause ->
                if (inFlight === created) inFlight = null
                // Updates the cache even if the caller that requested this
                // traversal already gave up and moved on with a stale
                // result — the next capture() benefits from it.
                if (cause == null) runCatching { created.getCompleted() }.getOrNull()?.let { lastGood = it }
            }
            created
        }

    /**
     * Active window first (its layer looked up from [AccessibilityService.getWindows]),
     * then every other `TYPE_APPLICATION` window stacked above it — dialogs,
     * bottom sheets, permission prompts. Degrades to all
     * `TYPE_APPLICATION`/`TYPE_SYSTEM` windows, sorted app-first-then-by-layer,
     * if there's no active root at all. Saarthi's own window is excluded by
     * package name — its overlay/hand-back screens are ordinary
     * Activity/session windows, not a `TYPE_ACCESSIBILITY_OVERLAY` the type
     * filter would drop automatically, and would otherwise be tappable by
     * the agent as if they were part of the target app.
     */
    private fun resolveRoots(service: AccessibilityService, ownPackage: String): List<AccessibilityNodeInfo> {
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        val activeRoot = runCatching { service.rootInActiveWindow }.getOrNull()

        val candidates: List<AccessibilityNodeInfo> = if (activeRoot != null) {
            val activeWindowId = runCatching { activeRoot.windowId }.getOrDefault(-1)
            val activeLayer = windows
                .firstOrNull { w -> runCatching { w.id }.getOrDefault(-2) == activeWindowId }
                ?.let { runCatching { it.layer }.getOrDefault(0) }
                ?: 0

            val overlays = windows.mapNotNull { window ->
                val type = runCatching { window.type }.getOrNull()
                val layer = runCatching { window.layer }.getOrDefault(Int.MIN_VALUE)
                if (type == AccessibilityWindowInfo.TYPE_APPLICATION && layer > activeLayer) {
                    runCatching { window.root }.getOrNull()
                } else {
                    null
                }
            }
            listOf(activeRoot) + overlays
        } else {
            windows
                .sortedWith(
                    compareByDescending<AccessibilityWindowInfo> { w ->
                        if (runCatching { w.type }.getOrNull() == AccessibilityWindowInfo.TYPE_APPLICATION) 1 else 0
                    }.thenByDescending { w -> runCatching { w.layer }.getOrDefault(0) },
                )
                .mapNotNull { window ->
                    val type = runCatching { window.type }.getOrNull()
                    if (type == AccessibilityWindowInfo.TYPE_APPLICATION || type == AccessibilityWindowInfo.TYPE_SYSTEM) {
                        runCatching { window.root }.getOrNull()
                    } else {
                        null
                    }
                }
        }

        return candidates
            .filter { node -> runCatching { node.packageName?.toString() }.getOrNull() != ownPackage }
            .distinctBy { runCatching { it.windowId }.getOrDefault(-1) }
    }

    private fun buildResult(roots: List<AccessibilityNodeInfo>, screenBounds: Rect): PerceptionResult {
        val kept = mutableListOf<KeptNode>()
        roots.forEach { root -> collect(root, depth = 0, activePath = ArrayDeque(), screenBounds = screenBounds, out = kept) }
        return serialize(applyBudget(kept))
    }

    // ---- tree walk -----------------------------------------------------

    private data class KeptNode(
        val node: AccessibilityNodeInfo,
        val depth: Int,
        val className: String,
        val label: String?,
        val clickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean,
        val checkable: Boolean,
        val checked: Boolean,
        val isPassword: Boolean,
        val onScreen: Boolean,
        val boundsCenter: Point?,
    )

    /**
     * DFS from [roots]. A node is kept if it's actionable
     * (clickable/editable/scrollable/checkable) or has a non-empty label.
     * [activePath] is an identity-based (not `equals`-based — a11y node
     * equality is unreliable) cycle guard.
     *
     * A clickable container with no independently-actionable descendant
     * (a plain list row/card — the common case in food/shopping apps)
     * collapses into one line with descendant text merged in, instead of
     * forcing the model to piece N sibling lines back together itself. A
     * container that *does* have an independently-actionable descendant
     * (e.g. a "+" quantity stepper nested in a cart row) is left alone so
     * the DFS can still surface that descendant as its own element.
     */
    private fun collect(
        node: AccessibilityNodeInfo,
        depth: Int,
        activePath: ArrayDeque<AccessibilityNodeInfo>,
        screenBounds: Rect,
        out: MutableList<KeptNode>,
    ) {
        if (depth > MAX_TREE_DEPTH) return
        if (activePath.any { it === node }) return

        val clickable = runCatching { node.isClickable }.getOrDefault(false)
        val editable = runCatching { node.isEditable }.getOrDefault(false)
        val scrollable = runCatching { node.isScrollable }.getOrDefault(false)
        val checkable = runCatching { node.isCheckable }.getOrDefault(false)
        val checked = runCatching { node.isChecked }.getOrDefault(false)
        val isPassword = runCatching { node.isPassword }.getOrDefault(false)
        val actionable = clickable || editable || scrollable || checkable
        val ownLabel = NodeText.ownLabel(node)

        val isMergeableContainer = clickable && !editable && !scrollable && !checkable &&
            runCatching { node.childCount }.getOrDefault(0) > 0 &&
            !NodeText.hasActionableDescendant(node, DESCENDANT_MERGE_DEPTH)

        if (isMergeableContainer) {
            val merged = NodeText.collectDescendantText(node, DESCENDANT_MERGE_DEPTH)
            val label = listOfNotNull(ownLabel, merged.takeIf { it.isNotBlank() })
                .joinToString(" ")
                .trim()
                .ifEmpty { null }
            out += toKeptNode(node, depth, label, clickable, editable, scrollable, checkable, checked, isPassword, screenBounds)
            return // descendants already folded into this line — don't re-emit them
        }

        if (actionable || !ownLabel.isNullOrEmpty()) {
            out += toKeptNode(node, depth, ownLabel, clickable, editable, scrollable, checkable, checked, isPassword, screenBounds)
        }

        val childCount = runCatching { node.childCount }.getOrDefault(0)
        if (childCount == 0) return
        activePath.addLast(node)
        for (i in 0 until childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            collect(child, depth + 1, activePath, screenBounds, out)
        }
        activePath.removeLast()
    }

    private fun toKeptNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        label: String?,
        clickable: Boolean,
        editable: Boolean,
        scrollable: Boolean,
        checkable: Boolean,
        checked: Boolean,
        isPassword: Boolean,
        screenBounds: Rect,
    ): KeptNode {
        val className = runCatching { node.className?.toString() }.getOrNull()?.substringAfterLast('.') ?: "View"
        val bounds = Rect()
        runCatching { node.getBoundsInScreen(bounds) }
        val onScreen = bounds.left < bounds.right && bounds.top < bounds.bottom &&
            bounds.right > 0 && bounds.bottom > 0 &&
            bounds.left < screenBounds.right && bounds.top < screenBounds.bottom
        val actionable = clickable || editable || scrollable || checkable
        // Off-screen actionable nodes (rows below the fold) can report
        // stale/degenerate bounds because they aren't currently laid out —
        // handing those out as tap targets is unsafe. Omitting bounds tells
        // the model "exists, but scroll to reach it" rather than either
        // hiding the element or handing out coordinates that don't mean
        // anything yet.
        val boundsCenter = if (actionable && onScreen) Point(bounds.centerX(), bounds.centerY()) else null
        return KeptNode(node, depth, className, label, clickable, editable, scrollable, checkable, checked, isPassword, onScreen, boundsCenter)
    }

    // ---- node budget -----------------------------------------------------

    /** Under budget, keep everything. Over budget: actionable nodes win over decorative text, on-screen wins over off-screen, within each group. */
    private fun applyBudget(nodes: List<KeptNode>): List<KeptNode> {
        if (nodes.size <= NODE_BUDGET) return nodes
        val (actionable, textOnly) = nodes.partition { it.clickable || it.editable || it.scrollable || it.checkable }
        val orderedActionable = actionable.sortedWith(compareByDescending<KeptNode> { it.onScreen }.thenBy { it.depth })
        val orderedText = textOnly.sortedWith(compareByDescending<KeptNode> { it.onScreen }.thenBy { it.depth })
        val takenActionable = orderedActionable.take(NODE_BUDGET)
        val remaining = NODE_BUDGET - takenActionable.size
        return takenActionable + (if (remaining > 0) orderedText.take(remaining) else emptyList())
    }

    // ---- serialization -----------------------------------------------------

    /**
     * One line per kept node, in ref order: `e4 Button "Pay now" clickable
     * bounds=[540,1820]`. Refs are assigned after budgeting, so they're
     * stable only for this one pass — never reused across captures. Labels
     * are truncated to [MAX_LABEL_LENGTH] chars unconditionally (not just
     * when the whole screen is over budget) — a single unclipped label
     * (a full news headline, say) can blow the prompt size up on its own.
     */
    private fun serialize(nodes: List<KeptNode>): PerceptionResult {
        val refMap = mutableMapOf<String, AccessibilityNodeInfo>()
        val lines = StringBuilder()
        nodes.forEachIndexed { index, kept ->
            val ref = "e${index + 1}"
            refMap[ref] = kept.node
            lines.append(ref).append(' ').append(kept.className)
            kept.label?.let { label ->
                val truncated = if (label.length > MAX_LABEL_LENGTH) label.take(MAX_LABEL_LENGTH - 1) + "…" else label
                lines.append(" \"").append(truncated.replace("\"", "'")).append('"')
            }
            if (kept.clickable) lines.append(" clickable")
            if (kept.editable) lines.append(" editable")
            if (kept.scrollable) lines.append(" scrollable")
            if (kept.checkable) lines.append(if (kept.checked) " checked" else " checkable")
            if (kept.isPassword) lines.append(" password")
            kept.boundsCenter?.let { lines.append(" bounds=[").append(it.x).append(',').append(it.y).append(']') }
            lines.append('\n')
        }
        return PerceptionResult(serialized = lines.toString().trimEnd('\n'), refMap = refMap)
    }
}
