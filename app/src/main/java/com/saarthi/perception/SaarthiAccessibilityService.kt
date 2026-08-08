package com.saarthi.perception

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.saarthi.BuildConfig
import com.saarthi.ui.TaskGlowBorderView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "SaarthiA11yService"

/**
 * The perception + actuation layer's anchor. [com.saarthi.agent.AgentLoop]
 * runs on [agentScope] rather than any Activity's `lifecycleScope` or the
 * `SaarthiInteractionSession`'s `LifecycleRegistry` — a takeover task
 * routinely backgrounds whichever UI surface started it (pressing HOME,
 * navigating into the target app), and both of those lifecycles would
 * cancel the loop mid-task. This service's own lifetime is exactly the
 * feature's lifetime: the OS keeps it alive precisely while accessibility
 * is enabled, which is also the precondition for any [android.view.accessibility.AccessibilityNodeInfo]
 * ref being meaningful. [current] doubles as the "accessibility not
 * enabled" signal — UI already knows how to present that via
 * `PermissionStatus.hasAccessibility()`.
 *
 * `serviceInfo` is intentionally left untouched here — the manifest's
 * `res/xml/accessibility_service_config.xml` is the single source of
 * truth. Programmatically overriding it in [onServiceConnected] (a pattern
 * some reference implementations use) would make the XML flags dead code
 * and invite the two silently disagreeing with each other.
 */
class SaarthiAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: SaarthiAccessibilityService? = null

        /** Null means accessibility is not enabled (or the service hasn't connected yet). */
        fun current(): SaarthiAccessibilityService? = instance
    }

    /**
     * App-scoped, not tied to any Activity/Session lifecycle — see the
     * class doc. `Dispatchers.Main.immediate`: both accessibility-node
     * interaction and [android.media.AudioTrack] control want a
     * consistent, main-ish thread; every blocking HTTP call the agent loop
     * makes must explicitly `withContext(Dispatchers.IO)` around itself.
     */
    lateinit var agentScope: CoroutineScope
        private set

    // TYPE_WINDOW_CONTENT_CHANGED waiters registered by awaitContentChanged.
    // A list, not a single slot, because a mid-task narration read and an
    // in-flight agent step can both be waiting at once.
    private val contentChangedWaiters = CopyOnWriteArrayList<CompletableDeferred<Unit>>()

    private var debugActionReceiver: DebugActionReceiver? = null

    // The pulsing gold border shown for the duration of a running task —
    // see [TaskGlowBorderView]. At most one at a time; [showTaskGlow] is
    // idempotent so callers never need to track whether it's already up.
    private var taskGlowView: TaskGlowBorderView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        agentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        if (BuildConfig.DEBUG) {
            debugActionReceiver = DebugActionReceiver(this).also { it.register(this) }
        }
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        // Deliberately does NOT trigger a perception capture here. Running an
        // independent capture per event was observed (in the reference
        // implementation this app's design is based on) to invalidate node
        // references a concurrent caller was actively using mid-action. This
        // event's only job is to release callers already waiting on
        // awaitContentChanged; the next real capture is always the caller's
        // own explicit ScreenPerception.capture() call.
        val waiters = contentChangedWaiters.toList()
        contentChangedWaiters.removeAll(waiters)
        waiters.forEach { it.complete(Unit) }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hideTaskGlow()
        debugActionReceiver?.unregister(this)
        agentScope.cancel()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hideTaskGlow()
        debugActionReceiver?.unregister(this)
        if (::agentScope.isInitialized) agentScope.cancel()
        instance = null
        super.onDestroy()
    }

    /**
     * Adds [TaskGlowBorderView] as a `TYPE_ACCESSIBILITY_OVERLAY` window —
     * the type an accessibility service is granted implicitly by its
     * binding, no `SYSTEM_ALERT_WINDOW` permission required. Non-touchable
     * and non-focusable so it never intercepts the taps/gestures
     * [ActionExecutor] performs on the app underneath. Safe to call more
     * than once; a second call while the glow is already up is a no-op
     * rather than stacking a duplicate view.
     */
    fun showTaskGlow() {
        if (taskGlowView != null) return
        val windowManager = getSystemService(WindowManager::class.java) ?: return
        val view = TaskGlowBorderView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { windowManager.addView(view, params) }
            .onSuccess { taskGlowView = view }
            .onFailure { Log.w(TAG, "Failed to add task-glow overlay", it) }
    }

    /** Removes the glow added by [showTaskGlow]. A no-op if it isn't showing. */
    fun hideTaskGlow() {
        val view = taskGlowView ?: return
        taskGlowView = null
        val windowManager = getSystemService(WindowManager::class.java) ?: return
        runCatching { windowManager.removeView(view) }
    }

    /**
     * Suspends until a [AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED]
     * fires or [timeoutMs] elapses, whichever first. A single-shot wait —
     * [AgentLoop][com.saarthi.agent.AgentLoop]'s empty-screen retry uses
     * this directly (it already loops the full capture externally); for
     * waiting out a single action's aftermath, use [awaitSettled] instead.
     */
    suspend fun awaitContentChanged(timeoutMs: Long) {
        awaitContentChangedEvent(timeoutMs)
    }

    /**
     * Waits for on-screen content to actually finish changing, not just
     * start changing. A single content-changed event can fire for the very
     * start of a transition (the outgoing screen beginning to animate
     * away, or a status-bar icon refreshing) well before the destination
     * screen has finished laying out — a capture taken right after that
     * first event can catch a near-empty transitional state (confirmed on
     * a real device: tapping into a Settings sub-screen sometimes read
     * back only the status bar's own icons, no real content, when the
     * capture followed the very first change event by under 200ms).
     * Waits for [quietMs] with no further events before returning, bounded
     * overall by [maxTotalMs] so a screen that only ever sends one event
     * still returns promptly. Every [ActionExecutor] action calls this
     * after performing itself — never re-perceive immediately after
     * acting.
     */
    suspend fun awaitSettled(quietMs: Long = 250L, maxTotalMs: Long = 1500L) {
        val deadline = SystemClock.elapsedRealtime() + maxTotalMs
        var sawChange = false
        while (true) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) return
            val window = if (sawChange) minOf(quietMs, remaining) else remaining
            val fired = awaitContentChangedEvent(window)
            if (!fired) return
            sawChange = true
        }
    }

    private suspend fun awaitContentChangedEvent(timeoutMs: Long): Boolean {
        val waiter = CompletableDeferred<Unit>()
        contentChangedWaiters += waiter
        val result = withTimeoutOrNull(timeoutMs) { waiter.await() }
        contentChangedWaiters.remove(waiter)
        return result != null
    }
}
