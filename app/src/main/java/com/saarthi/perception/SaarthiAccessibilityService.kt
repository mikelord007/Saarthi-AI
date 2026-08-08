package com.saarthi.perception

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.saarthi.BuildConfig
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
        debugActionReceiver?.unregister(this)
        agentScope.cancel()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        debugActionReceiver?.unregister(this)
        if (::agentScope.isInitialized) agentScope.cancel()
        instance = null
        super.onDestroy()
    }

    /**
     * Suspends until a [AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED]
     * fires or [timeoutMs] elapses, whichever first. Every action in
     * [com.saarthi.perception.ActionExecutor] calls this after performing
     * an action — never re-perceive immediately after acting.
     */
    suspend fun awaitContentChanged(timeoutMs: Long) {
        val waiter = CompletableDeferred<Unit>()
        contentChangedWaiters += waiter
        withTimeoutOrNull(timeoutMs) { waiter.await() }
        contentChangedWaiters.remove(waiter)
    }
}
