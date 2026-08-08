package com.saarthi.perception

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

private const val TAG = "SaarthiDebugAction"
private const val ACTION_PERCEIVE = "com.saarthi.debug.PERCEIVE"
private const val ACTION_TAP = "com.saarthi.debug.TAP"
private const val ACTION_LONG_PRESS = "com.saarthi.debug.LONG_PRESS"
private const val ACTION_SET_TEXT = "com.saarthi.debug.SET_TEXT"
private const val ACTION_SCROLL = "com.saarthi.debug.SCROLL"
private const val ACTION_KEYBOARD_ENTER = "com.saarthi.debug.KEYBOARD_ENTER"
private const val ACTION_BACK = "com.saarthi.debug.BACK"
private const val ACTION_HOME = "com.saarthi.debug.HOME"

/**
 * Debug-build-only broadcast receiver that drives the perception layer and
 * [ActionExecutor] (and, once stage 5 lands, the full agent loop) straight
 * from `adb shell am broadcast` — no LLM call needed to exercise most of
 * the feature, so most of it can be tested without spending on
 * Claude/Maya/Sarvam. Registered/unregistered from
 * [SaarthiAccessibilityService]'s own lifecycle; never present in a
 * release build (see the `BuildConfig.DEBUG` guard at the call site).
 *
 * Broadcast [ACTION_PERCEIVE] first to see the current refs in logcat,
 * then reference one in a follow-up action broadcast — refs are only
 * valid for the perception pass that produced them, same as in the real
 * agent loop, so re-run PERCEIVE after anything that changes the screen.
 *
 *     adb shell am broadcast -a com.saarthi.debug.PERCEIVE
 *     adb shell am broadcast -a com.saarthi.debug.TAP --es ref e4
 *     adb shell am broadcast -a com.saarthi.debug.LONG_PRESS --es ref e4
 *     adb shell am broadcast -a com.saarthi.debug.SET_TEXT --es ref e2 --es text "chicken biryani" [--ez clear true]
 *     adb shell am broadcast -a com.saarthi.debug.SCROLL --es direction down   # or up
 *     adb shell am broadcast -a com.saarthi.debug.KEYBOARD_ENTER
 *     adb shell am broadcast -a com.saarthi.debug.BACK
 *     adb shell am broadcast -a com.saarthi.debug.HOME
 */
class DebugActionReceiver(private val service: SaarthiAccessibilityService) : BroadcastReceiver() {

    @Volatile
    private var lastPerception: PerceptionResult? = null

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(ACTION_PERCEIVE)
            addAction(ACTION_TAP)
            addAction(ACTION_LONG_PRESS)
            addAction(ACTION_SET_TEXT)
            addAction(ACTION_SCROLL)
            addAction(ACTION_KEYBOARD_ENTER)
            addAction(ACTION_BACK)
            addAction(ACTION_HOME)
        }
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    fun unregister(context: Context) {
        runCatching { context.unregisterReceiver(this) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PERCEIVE -> service.agentScope.launch {
                val result = ScreenPerception.capture(service, goHomeIfEmpty = false)
                lastPerception = result
                Log.i(TAG, "PERCEIVE (stale=${result.stale}):\n${result.serialized}")
            }
            ACTION_TAP -> withPerceptionAnd(intent) { perception, ref ->
                logResult("TAP($ref)", ActionExecutor.tap(service, perception, ref))
            }
            ACTION_LONG_PRESS -> withPerceptionAnd(intent) { perception, ref ->
                logResult("LONG_PRESS($ref)", ActionExecutor.longPress(service, perception, ref))
            }
            ACTION_SET_TEXT -> withPerception { perception ->
                val ref = intent.getStringExtra("ref")
                val text = intent.getStringExtra("text")
                if (ref == null || text == null) {
                    Log.w(TAG, "SET_TEXT requires --es ref <ref> --es text <text>")
                    return@withPerception
                }
                val clear = intent.getBooleanExtra("clear", false)
                logResult("SET_TEXT($ref, \"$text\", clear=$clear)", ActionExecutor.setText(service, perception, ref, text, clear))
            }
            ACTION_SCROLL -> withPerception { perception ->
                val direction = when (intent.getStringExtra("direction")?.lowercase()) {
                    "up" -> ScrollDirection.UP
                    "down" -> ScrollDirection.DOWN
                    else -> null
                }
                if (direction == null) {
                    Log.w(TAG, "SCROLL requires --es direction up|down")
                    return@withPerception
                }
                logResult("SCROLL($direction)", ActionExecutor.scroll(service, perception, direction))
            }
            ACTION_KEYBOARD_ENTER -> withPerception { perception ->
                logResult("KEYBOARD_ENTER", ActionExecutor.keyboardEnter(service, perception))
            }
            ACTION_BACK -> service.agentScope.launch { logResult("BACK", ActionExecutor.back(service)) }
            ACTION_HOME -> service.agentScope.launch { logResult("HOME", ActionExecutor.home(service)) }
        }
    }

    private inline fun withPerception(crossinline block: suspend (PerceptionResult) -> Unit) {
        val perception = lastPerception
        if (perception == null) {
            Log.w(TAG, "No cached perception — broadcast com.saarthi.debug.PERCEIVE first")
            return
        }
        service.agentScope.launch { block(perception) }
    }

    private inline fun withPerceptionAnd(intent: Intent, crossinline block: suspend (PerceptionResult, String) -> Unit) {
        val ref = intent.getStringExtra("ref")
        if (ref == null) {
            Log.w(TAG, "Requires --es ref <ref>")
            return
        }
        withPerception { perception -> block(perception, ref) }
    }

    private fun logResult(label: String, result: ActionResult) {
        Log.i(TAG, "$label -> $result")
    }
}
