package com.saarthi.ui

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.saarthi.R
import com.saarthi.agent.AgentEvent
import com.saarthi.agent.AgentLoop
import com.saarthi.agent.ChatRouter
import com.saarthi.agent.RouterDecision
import com.saarthi.chat.BlockCause
import com.saarthi.perception.SaarthiAccessibilityService
import com.saarthi.speech.AudioRecorder
import com.saarthi.speech.MayaTts
import com.saarthi.speech.SpeechToText
import com.saarthi.speech.TranscriptionResult
import com.saarthi.speech.VoicePreferences
import com.saarthi.ui.components.VoiceState
import com.saarthi.ui.screens.AssistOverlayScreen
import com.saarthi.ui.screens.HandbackScreen
import com.saarthi.ui.theme.SaarthiTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** No silence detection — a real "release to stop" or VAD-based capture is future work; see [startAssistFlow]. */
private const val RECORD_WINDOW_MS = 6000L

/**
 * The translucent overlay reached via the system ASSIST invocation
 * (long-press home on devices/configurations that route here instead of
 * the modern [SaarthiInteractionSession] voice-interaction path). Window
 * starts as a bottom sheet ([applyWindowMode] false): translucent,
 * `FLAG_NOT_TOUCH_MODAL` so the app underneath is still visible and
 * touchable while this is up. It becomes a full-screen modal only for the
 * hand-back hard stop — see [applyWindowMode] and [transitionToHandback].
 *
 * Known limitation, not fixed here: this is an ordinary Activity, so it is
 * paused the instant the agent navigates into another app or presses
 * HOME. Its caption track is therefore only live for tasks that stay
 * inside Saarthi. [SaarthiInteractionSession] (whose window is the
 * platform voice-interaction session window) is the primary v1 agent
 * surface; this Activity is wired for completeness and for the hand-back
 * case, per the implementation plan.
 */
class AssistActivity : ComponentActivity() {

    private val voicePreferences by lazy { VoicePreferences(applicationContext) }

    private var overlayScreen by mutableStateOf<OverlayScreen>(OverlayScreen.Assist)
    private var currentLanguageCode by mutableStateOf("")
    private var voiceState by mutableStateOf(VoiceState.Idle)
    private var narrationLine by mutableStateOf("")
    private var stepLabel by mutableStateOf("")

    private var agentJob: Job? = null

    /** Sets the app's display language from the very first frame — see [LocalizedContent] for the live update applied every [onResume]. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withLocale(VoicePreferences(newBase).language.code))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyWindowMode(modal = false)
        currentLanguageCode = voicePreferences.language.code

        setContent {
            SaarthiTheme {
                LocalizedContent(languageCode = currentLanguageCode) {
                BackHandler { onStopRequested() }

                when (val screen = overlayScreen) {
                    OverlayScreen.Assist -> AssistOverlayScreen(
                        voiceState = voiceState,
                        narrationLine = narrationLine,
                        stepLabel = stepLabel,
                        onClose = ::onStopRequested,
                        onStop = ::onStopRequested,
                    )
                    is OverlayScreen.Handback -> HandbackScreen(
                        actionLabel = screen.actionLabel,
                        onConfirm = ::onHandbackConfirm,
                        onDecline = ::onStopRequested,
                    )
                }
                }
            }
        }

        startAssistFlow()
    }

    override fun onResume() {
        super.onResume()
        // singleTask-style entry points can resume without recreating — keep
        // window geometry and language re-asserted idempotently.
        applyWindowMode(modal = overlayScreen is OverlayScreen.Handback)
        currentLanguageCode = voicePreferences.language.code
    }

    /**
     * The only place window geometry is touched. `modal = true` (hand-back):
     * full-screen, focusable, touch-modal — a stray touch must not leak to
     * whatever's underneath. `modal = false` (assist): bottom sheet,
     * FLAG_NOT_TOUCH_MODAL so the app underneath stays reachable.
     */
    private fun applyWindowMode(modal: Boolean) {
        if (modal) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            )
            window.setGravity(Gravity.BOTTOM)
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    // --- Capture -> agent loop ---

    /**
     * Records for a fixed [RECORD_WINDOW_MS] window (no mic-tap control and
     * no silence detection exist on this surface — see the class doc),
     * transcribes, then runs the task. Launched once per Activity instance
     * from [onCreate].
     */
    private fun startAssistFlow() {
        val service = SaarthiAccessibilityService.current()
        if (service == null) {
            // Onboarding is responsible for accessibility being enabled
            // before this surface is ever reached; nothing to recover here.
            onStopRequested()
            return
        }
        val settings = voicePreferences.settings

        lifecycleScope.launch {
            voiceState = VoiceState.Listening
            narrationLine = getString(R.string.invoke_listening_title)

            val recorder = AudioRecorder(this@AssistActivity)
            try {
                recorder.start()
            } catch (e: SecurityException) {
                onStopRequested()
                return@launch
            }
            delay(RECORD_WINDOW_MS)
            val file = recorder.stop()

            voiceState = VoiceState.Thinking
            if (file == null) {
                onStopRequested()
                return@launch
            }

            when (val result = SpeechToText.transcribe(file, settings.language)) {
                is TranscriptionResult.Success -> onTranscript(service, result.transcript, settings)
                is TranscriptionResult.Failed -> onStopRequested()
            }
        }
    }

    /**
     * Routes through [ChatRouter] before ever calling [runTask] — see that
     * class's doc for why a plain chat reply must never reach
     * [AgentLoop.run] (it would press HOME to recover from Saarthi's own
     * screen reading empty, and light the task-glow overlay for something
     * that isn't a task). [voiceState] is already `Thinking` from
     * [startAssistFlow] and covers the router's latency.
     *
     * The reply must be spoken (a suspend call that awaits playback)
     * *before* [onStopRequested] — that function calls `finish()`
     * synchronously, same as every other terminal branch in
     * [handleAgentEvent], so setting [narrationLine] without awaiting
     * playback first would never actually be seen or heard.
     */
    private fun onTranscript(service: SaarthiAccessibilityService, transcript: String, settings: com.saarthi.speech.VoiceSettings) {
        lifecycleScope.launch {
            when (val decision = ChatRouter.classify(transcript, languageDisplayName = settings.language.displayName)) {
                is RouterDecision.Chat -> {
                    voiceState = VoiceState.Speaking
                    narrationLine = decision.reply
                    MayaTts.speak(decision.reply, settings)
                    onStopRequested()
                }
                is RouterDecision.Task -> runTask(service, decision.task, settings)
            }
        }
    }

    private fun runTask(service: SaarthiAccessibilityService, task: String, settings: com.saarthi.speech.VoiceSettings) {
        // Deliberately launched on the service's own scope, not this
        // Activity's lifecycleScope — see SaarthiInteractionSession's doc
        // for why the loop's survival must not depend on the caller's
        // lifecycle, even though (per this class's own known limitation)
        // the caption track here will stop updating once this Activity is
        // paused.
        agentJob = service.agentScope.launch {
            AgentLoop.run(
                service = service,
                task = task,
                languageDisplayName = settings.language.displayName,
                narrateEveryStep = settings.narrateEveryStep,
                speak = { text -> MayaTts.speak(text, settings) },
                onEvent = { event -> handleAgentEvent(event) },
            )
        }
    }

    private fun handleAgentEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.Step -> {
                voiceState = VoiceState.Acting
                narrationLine = event.description
                stepLabel = getString(R.string.step_of, event.index, MAX_STEPS_FOR_DISPLAY)
            }
            is AgentEvent.Done -> {
                voiceState = VoiceState.Speaking
                narrationLine = event.summary
                onStopRequested()
            }
            is AgentEvent.AskUser -> {
                // No resumable capture flow on this surface (no confirm
                // step, no pendingResume state machine) — narrate the
                // question and hand back to the user rather than silently
                // stopping.
                voiceState = VoiceState.Speaking
                narrationLine = event.question
                onStopRequested()
            }
            is AgentEvent.Blocked -> {
                if (event.cause == BlockCause.IRREVERSIBLE_GUARD && event.actionLabel != null) {
                    transitionToHandback(actionLabel = event.actionLabel, spokenReason = event.reason)
                } else {
                    voiceState = VoiceState.Speaking
                    narrationLine = event.reason
                    onStopRequested()
                }
            }
            is AgentEvent.Error -> {
                voiceState = VoiceState.Speaking
                narrationLine = event.message
                onStopRequested()
            }
        }
    }

    // --- Hand-back and stop/cancel ---

    /** Sets the screen AND the window mode together — setting [overlayScreen] alone would render the full-screen card inside bottom-sheet geometry. */
    private fun transitionToHandback(actionLabel: String, spokenReason: String) {
        overlayScreen = OverlayScreen.Handback(actionLabel = actionLabel, spokenReason = spokenReason)
        applyWindowMode(modal = true)
    }

    /**
     * This screen never performs the guarded action itself — this only
     * clears the modal so the REAL button underneath is tappable again,
     * then gets out of the way. See HandbackScreen's doc.
     */
    private fun onHandbackConfirm() {
        applyWindowMode(modal = false)
        finish()
    }

    /** Stop / x / system back — all cancel and get out of the way. */
    private fun onStopRequested() {
        agentJob?.cancel()
        agentJob = null
        finish()
    }
}

// InvokeSheet-style 4-segment collapse, matching AgentLoop.MAX_STEPS (kept
// as a literal here rather than a cross-module constant reference, since
// AgentLoop.MAX_STEPS is private to that file — see AgentEvent.Step's use
// in SaarthiInteractionSession for the canonical version of this mapping).
private const val MAX_STEPS_FOR_DISPLAY = 20
