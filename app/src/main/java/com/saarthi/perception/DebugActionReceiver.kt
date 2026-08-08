package com.saarthi.perception

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.saarthi.agent.AgentLoop
import com.saarthi.agent.AgentPrompt
import com.saarthi.agent.ChatRouter
import com.saarthi.agent.ClaudeClient
import com.saarthi.agent.ClaudeDecision
import com.saarthi.speech.AudioRecorder
import com.saarthi.speech.MayaTts
import com.saarthi.speech.SpeechToText
import com.saarthi.speech.TranscriptionResult
import com.saarthi.speech.VoicePreferences
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
private const val ACTION_DECIDE = "com.saarthi.debug.DECIDE"
private const val ACTION_CLASSIFY = "com.saarthi.debug.CLASSIFY"
private const val ACTION_RUN_TASK = "com.saarthi.debug.RUN_TASK"
private const val ACTION_RECORD_START = "com.saarthi.debug.RECORD_START"
private const val ACTION_RECORD_STOP = "com.saarthi.debug.RECORD_STOP"

/**
 * Debug-build-only broadcast receiver that drives the perception layer,
 * [ActionExecutor], a single Claude decision, or a full [AgentLoop] run
 * straight from `adb shell am broadcast` — most of the feature is testable
 * without spending on Claude/Maya/Sarvam, and RUN_TASK is one real call
 * per step. Registered/unregistered from [SaarthiAccessibilityService]'s
 * own lifecycle; never present in a release build (see the
 * `BuildConfig.DEBUG` guard at the call site).
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
 *     adb shell am broadcast -a com.saarthi.debug.DECIDE --es task "search for biryani on swiggy"
 *     adb shell am broadcast -a com.saarthi.debug.CLASSIFY --es message "How are you?"
 *     adb shell am broadcast -a com.saarthi.debug.RUN_TASK --es task "search for biryani on swiggy"
 *     adb shell am broadcast -a com.saarthi.debug.RECORD_START
 *     adb shell am broadcast -a com.saarthi.debug.RECORD_STOP
 */
class DebugActionReceiver(private val service: SaarthiAccessibilityService) : BroadcastReceiver() {

    @Volatile
    private var lastPerception: PerceptionResult? = null

    private var audioRecorder: AudioRecorder? = null

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
            addAction(ACTION_DECIDE)
            addAction(ACTION_CLASSIFY)
            addAction(ACTION_RUN_TASK)
            addAction(ACTION_RECORD_START)
            addAction(ACTION_RECORD_STOP)
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
            ACTION_DECIDE -> withPerception { perception ->
                val task = intent.getStringExtra("task")
                if (task == null) {
                    Log.w(TAG, "DECIDE requires --es task <task description>")
                    return@withPerception
                }
                // English narration for this debug hook — a real run
                // substitutes the user's actual VoicePreferences.language.
                val system = AgentPrompt.system(languageDisplayName = "English")
                val userMessage = AgentPrompt.userMessage(task = task, history = emptyList(), screen = perception.serialized)
                when (val decision = ClaudeClient.decide(system, userMessage)) {
                    is ClaudeDecision.ToolCall -> Log.i(TAG, "DECIDE -> ${decision.tool}")
                    is ClaudeDecision.Malformed -> Log.w(TAG, "DECIDE -> malformed response (no usable tool_use block)")
                    is ClaudeDecision.Refused -> Log.w(TAG, "DECIDE -> refused: ${decision.message}")
                    is ClaudeDecision.Failed -> Log.e(TAG, "DECIDE -> failed: ${decision.message}")
                }
            }
            ACTION_CLASSIFY -> {
                val message = intent.getStringExtra("message")
                if (message == null) {
                    Log.w(TAG, "CLASSIFY requires --es message <text>")
                    return
                }
                // Deliberately does NOT call ScreenPerception — this is the
                // whole point of ChatRouter, and the fastest way to prove
                // it: this action alone can never press HOME or show the
                // task-glow overlay.
                val language = VoicePreferences(service).settings.language.displayName
                service.agentScope.launch {
                    Log.i(TAG, "CLASSIFY -> ${ChatRouter.classify(message, languageDisplayName = language)}")
                }
            }
            ACTION_RUN_TASK -> {
                val task = intent.getStringExtra("task")
                if (task == null) {
                    Log.w(TAG, "RUN_TASK requires --es task <task description>")
                    return
                }
                // Uses the user's real saved language/voice preferences —
                // this is the same settings snapshot the UI-wired version
                // (stage 8) will read.
                val voiceSettings = VoicePreferences(service).settings
                service.agentScope.launch {
                    AgentLoop.run(
                        service = service,
                        task = task,
                        languageDisplayName = voiceSettings.language.displayName,
                        narrateEveryStep = voiceSettings.narrateEveryStep,
                        speak = { text ->
                            Log.i(TAG, "SPEAK: $text")
                            MayaTts.speak(text, voiceSettings)
                        },
                        onEvent = { event -> Log.i(TAG, "EVENT: $event") },
                    )
                }
            }
            ACTION_RECORD_START -> {
                val recorder = AudioRecorder(service)
                try {
                    recorder.start()
                    audioRecorder = recorder
                    Log.i(TAG, "RECORD_START: recording — broadcast com.saarthi.debug.RECORD_STOP when done")
                } catch (e: SecurityException) {
                    Log.e(TAG, "RECORD_START: RECORD_AUDIO not granted at runtime — grant it via the app or `adb shell pm grant`", e)
                }
            }
            ACTION_RECORD_STOP -> {
                val recorder = audioRecorder
                if (recorder == null) {
                    Log.w(TAG, "No recording in progress — broadcast com.saarthi.debug.RECORD_START first")
                    return
                }
                audioRecorder = null
                val language = VoicePreferences(service).language
                service.agentScope.launch {
                    val file = recorder.stop()
                    if (file == null) {
                        Log.w(TAG, "RECORD_STOP: nothing was recorded")
                        return@launch
                    }
                    Log.i(TAG, "RECORD_STOP: uploading ${file.length()} bytes to Sarvam")
                    when (val result = SpeechToText.transcribe(file, language)) {
                        is TranscriptionResult.Success -> Log.i(TAG, "TRANSCRIPT: ${result.transcript}")
                        is TranscriptionResult.Failed -> Log.e(TAG, "TRANSCRIPT failed: ${result.message}")
                    }
                }
            }
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
