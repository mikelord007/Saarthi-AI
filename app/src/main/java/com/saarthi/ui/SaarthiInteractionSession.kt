package com.saarthi.ui

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.saarthi.R
import com.saarthi.agent.AgentEvent
import com.saarthi.agent.AgentLoop
import com.saarthi.agent.ChatRouter
import com.saarthi.agent.RouterDecision
import com.saarthi.chat.BlockCause
import com.saarthi.chat.ChatEntry
import com.saarthi.chat.ChatHistoryStore
import com.saarthi.chat.ChatStatus
import com.saarthi.chat.ChatTurn
import com.saarthi.chat.EntryKind
import com.saarthi.perception.SaarthiAccessibilityService
import com.saarthi.perception.ScreenPerception
import com.saarthi.speech.AudioRecorder
import com.saarthi.speech.MayaTts
import com.saarthi.speech.SpeechToText
import com.saarthi.speech.TranscriptionResult
import com.saarthi.speech.VoicePreferences
import com.saarthi.ui.screens.InvokeSheet
import com.saarthi.ui.screens.InvokeState
import com.saarthi.ui.theme.SaarthiTheme
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The live session behind [InvokeSheet]: owns the sheet's [InvokeState],
 * responds to its callbacks, and is the primary v1 surface for the task
 * agent — its window is the platform voice-interaction session window,
 * which is how "Assistant" visually persists over other apps, unlike
 * [AssistActivity] (an ordinary Activity, paused the instant the agent
 * navigates elsewhere). Verify on-device that this window really does
 * survive backgrounding; the documented fallback if it doesn't is an
 * AccessibilityService-owned `TYPE_ACCESSIBILITY_OVERLAY`.
 *
 * The running [AgentLoop] is launched on
 * [SaarthiAccessibilityService.agentScope], never on this session's own
 * [lifecycleScope] — a takeover task routinely backgrounds this session
 * (pressing HOME, navigating into the target app), and this session's
 * lifecycle must not be what the loop's survival depends on. Recording
 * and transcription, by contrast, use [lifecycleScope]: nothing about
 * capturing a spoken task needs to outlive this session.
 *
 * A [VoiceInteractionSession] is not an Activity and provides none of the
 * scaffolding Compose needs to host a [ComposeView] outside one — this class
 * supplies [Lifecycle], [ViewModelStore] and [SavedStateRegistry] itself, the
 * standard recipe for embedding Compose in a raw window.
 */
class SaarthiInteractionSession(baseContext: Context) :
    VoiceInteractionSession(baseContext),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    // Deliberately NOT using `baseContext` below — every reference to
    // `context` in this class resolves to VoiceInteractionSession's own
    // inherited `getContext()`, which is the correct, live context for this
    // session (see that method's doc: it may wrap `baseContext`).

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val voicePreferences by lazy { VoicePreferences(context) }
    private val chatHistoryStore by lazy { ChatHistoryStore(context) }
    private val audioRecorder by lazy { AudioRecorder(context) }

    /** Read fresh every use — the user may change language in Settings between invocations. */
    private val voice get() = voicePreferences.settings

    private var invokeState by mutableStateOf<InvokeState>(InvokeState.Listening)
    private var isRecording = false
    private var currentTask: String = ""
    private var currentJob: Job? = null

    /** Set on [AgentEvent.AskUser]; consumed by the next [confirmPlan] so the SAME task resumes instead of starting over. */
    private var pendingResume: PendingResume? = null
    private data class PendingResume(val entryId: String, val task: String, val history: List<String>)

    /** Highest [AgentEvent.Step] index seen this run — there's only ever one task in flight per session, unlike [MainActivity]'s per-entry map. Reset in [confirmPlan], consumed into [ChatTurn.taskStepCount] by the terminal event. */
    private var lastStepIndex = 0

    init {
        // Must be called before onCreate() — see VoiceInteractionSession.setTheme's own doc — so it happens here, not in an onCreate override.
        setTheme(R.style.Theme_Saarthi_InvokeSession)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onCreateContentView(): View {
        val view = ComposeView(context)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setContent {
            SaarthiTheme {
                InvokeSheet(
                    state = invokeState,
                    languageLabel = voice.language.displayName,
                    onMicTap = ::toggleRecording,
                    onReadScreenAloud = ::readScreenAloud,
                    onDoTaskForMe = ::toggleRecording,
                    onConfirmPlan = ::confirmPlan,
                    onRetryTranscript = ::beginListening,
                    onStop = ::stopTask,
                    onSwipeDownDismiss = ::dismissSession,
                )
            }
        }
        return view
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        // A stray touch on the app underneath must not itself dismiss the
        // sheet — the window is intentionally non-modal, not a
        // cancel-on-outside-tap dialog.
        window.setCanceledOnTouchOutside(false)
        beginListening()
    }

    override fun onHide() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        super.onHide()
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            dismissSession()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // --- Listening / recording ---

    private fun beginListening() {
        invokeState = InvokeState.Listening
        isRecording = false
    }

    private fun toggleRecording() {
        if (isRecording) stopRecordingAndTranscribe() else startRecording()
    }

    private fun startRecording() {
        try {
            audioRecorder.start()
            isRecording = true
        } catch (e: SecurityException) {
            // RECORD_AUDIO not granted at runtime — onboarding/Settings is
            // responsible for that before this surface is reachable; stay
            // in Listening rather than crash.
        }
    }

    private fun stopRecordingAndTranscribe() {
        isRecording = false
        val settings = voice
        lifecycleScope.launch {
            val file = audioRecorder.stop()
            if (file == null) {
                beginListening()
                return@launch
            }
            when (val result = SpeechToText.transcribe(file, settings.language)) {
                is TranscriptionResult.Success -> onTranscript(result.transcript, settings)
                is TranscriptionResult.Failed -> beginListening()
            }
        }
    }

    /**
     * A resume answer (mid-ASK_USER) always goes straight to [InvokeState.Heard]
     * as before — it's an answer to the agent's own question, not a fresh
     * instruction, so there's nothing to classify. Anything else is routed
     * through [ChatRouter] first: a plain chat reply is spoken and the sheet
     * returns to [InvokeState.Listening] without ever touching [AgentLoop] —
     * see [ChatRouter]'s own doc for why that matters (no HOME press, no
     * task-glow overlay). [InvokeState.Working] (rather than staying in
     * [InvokeState.Listening]) covers the round-trip's latency — the same
     * state [confirmPlan] uses for the same reason.
     */
    private fun onTranscript(transcript: String, settings: com.saarthi.speech.VoiceSettings) {
        if (pendingResume != null) {
            currentTask = transcript
            invokeState = InvokeState.Heard(transcript = transcript, plan = context.getString(R.string.invoke_plan_template))
            return
        }
        invokeState = InvokeState.Working(step = 1, of = 4, label = context.getString(R.string.state_thinking))
        lifecycleScope.launch {
            when (val decision = ChatRouter.classify(transcript, languageDisplayName = settings.language.displayName)) {
                is RouterDecision.Chat -> {
                    MayaTts.speak(decision.reply, settings)
                    chatHistoryStore.upsert(
                        ChatEntry(
                            id = UUID.randomUUID().toString(),
                            task = transcript,
                            timestamp = System.currentTimeMillis(),
                            turns = listOf(ChatTurn("user", transcript), ChatTurn("assistant", decision.reply)),
                            status = ChatStatus.DONE,
                            kind = EntryKind.TASK,
                        ),
                    )
                    beginListening()
                }
                RouterDecision.Task -> {
                    currentTask = transcript
                    invokeState = InvokeState.Heard(transcript = transcript, plan = context.getString(R.string.invoke_plan_template))
                }
            }
        }
    }

    // --- Read screen aloud ---

    private fun readScreenAloud() {
        val service = SaarthiAccessibilityService.current() ?: return
        val settings = voice
        lifecycleScope.launch {
            val perception = ScreenPerception.capture(service, goHomeIfEmpty = false)
            val summary = describeForNarration(perception.serialized)
            MayaTts.speak(summary, settings)
            chatHistoryStore.upsert(
                ChatEntry(
                    id = UUID.randomUUID().toString(),
                    task = context.getString(R.string.note_narrated),
                    timestamp = System.currentTimeMillis(),
                    turns = listOf(ChatTurn(role = "assistant", text = summary)),
                    status = ChatStatus.DONE,
                    kind = EntryKind.NARRATION,
                ),
            )
        }
    }

    /** Crude but LLM-free screen summary for "read this screen aloud": every quoted label, joined. */
    private fun describeForNarration(serialized: String): String =
        serialized.lineSequence()
            .mapNotNull { line ->
                val start = line.indexOf('"')
                if (start < 0) return@mapNotNull null
                val end = line.indexOf('"', start + 1)
                if (end < 0) null else line.substring(start + 1, end).takeIf { it.isNotBlank() }
            }
            .joinToString(", ")
            .ifBlank { context.getString(R.string.invoke_read_screen_empty) }

    // --- Confirm plan / run the task ---

    private fun confirmPlan() {
        val service = SaarthiAccessibilityService.current() ?: return
        val settings = voice
        val resume = pendingResume
        pendingResume = null

        val entryId = resume?.entryId ?: UUID.randomUUID().toString()
        val task = resume?.task ?: currentTask
        val initialHistory = resume?.history ?: emptyList()

        lastStepIndex = 0
        invokeState = InvokeState.Working(step = 1, of = 4, label = context.getString(R.string.state_thinking))
        if (resume == null) persistEntry(entryId, task, ChatStatus.RUNNING, emptyList(), turns = listOf(ChatTurn("user", task)))

        currentJob = service.agentScope.launch {
            AgentLoop.run(
                service = service,
                task = task,
                languageDisplayName = settings.language.displayName,
                initialHistory = initialHistory,
                narrateEveryStep = settings.narrateEveryStep,
                speak = { text -> MayaTts.speak(text, settings) },
                onEvent = { event -> handleAgentEvent(entryId, task, event) },
            )
        }
    }

    private fun handleAgentEvent(entryId: String, task: String, event: AgentEvent) {
        when (event) {
            is AgentEvent.Step -> {
                lastStepIndex = event.index
                invokeState = InvokeState.Working(
                    step = ((event.index - 1) / 5 + 1).coerceIn(1, 4),
                    of = 4,
                    label = event.description,
                )
            }
            is AgentEvent.Done -> {
                persistEntry(
                    entryId, task, ChatStatus.DONE, emptyList(),
                    turns = listOf(ChatTurn("assistant", event.summary)),
                    isTaskStep = true, taskStatus = ChatStatus.DONE, taskStepCount = lastStepIndex,
                )
                dismissSession()
            }
            is AgentEvent.AskUser -> {
                pendingResume = PendingResume(entryId, task, event.history)
                persistEntry(
                    entryId, task, ChatStatus.ASK_USER, event.history,
                    turns = listOf(ChatTurn("assistant", event.question)),
                    isTaskStep = true, taskStatus = ChatStatus.ASK_USER, taskStepCount = lastStepIndex,
                )
                beginListening()
            }
            is AgentEvent.Blocked -> {
                persistEntry(
                    entryId, task, ChatStatus.BLOCKED, emptyList(),
                    turns = listOf(ChatTurn("assistant", event.reason)),
                    blockCause = event.cause,
                    handbackLabel = event.actionLabel,
                    isTaskStep = true, taskStatus = ChatStatus.BLOCKED, taskStepCount = lastStepIndex,
                )
                // AgentLoop deliberately stays silent for IRREVERSIBLE_GUARD
                // (see its own doc) — speaking the hand-back message is this
                // surface's job, reusing the same copy the legacy
                // AssistActivity/HandbackScreen flow shows. This session's
                // window is a non-modal sheet over the target app, so simply
                // dismissing it reveals the guarded button for the user to
                // tap themselves — no separate confirmation card needed here.
                if (event.cause == BlockCause.IRREVERSIBLE_GUARD) {
                    val label = event.actionLabel.orEmpty()
                    val message = "${context.getString(R.string.handback_action_question, label)} ${context.getString(R.string.handback_body)}"
                    lifecycleScope.launch { MayaTts.speak(message, voice) }
                }
                dismissSession()
            }
            is AgentEvent.Error -> {
                persistEntry(
                    entryId, task, ChatStatus.ERROR, emptyList(),
                    turns = listOf(ChatTurn("assistant", event.message)),
                    isTaskStep = true, taskStatus = ChatStatus.ERROR, taskStepCount = lastStepIndex,
                )
                dismissSession()
            }
        }
    }

    /**
     * [isTaskStep]/[taskStatus]/[taskStepCount] are stamped onto [turns]
     * only when [isTaskStep] is true — the initial "user" turn persisted
     * from [confirmPlan] deliberately passes the default `false`, so the
     * "Task" marker in [com.saarthi.ui.screens.ThreadDetailScreen] lands
     * on the terminal outcome turn, not the request.
     */
    private fun persistEntry(
        id: String,
        task: String,
        status: ChatStatus,
        agentHistory: List<String>,
        turns: List<ChatTurn> = emptyList(),
        blockCause: BlockCause? = null,
        handbackLabel: String? = null,
        isTaskStep: Boolean = false,
        taskStatus: ChatStatus? = null,
        taskStepCount: Int = 0,
    ) {
        val existing = chatHistoryStore.find(id)
        val stampedTurns = if (isTaskStep) {
            turns.map { it.copy(isTaskStep = true, taskStatus = taskStatus, blockCause = blockCause, handbackLabel = handbackLabel, taskStepCount = taskStepCount) }
        } else {
            turns
        }
        chatHistoryStore.upsert(
            ChatEntry(
                id = id,
                task = task,
                timestamp = existing?.timestamp ?: System.currentTimeMillis(),
                turns = existing?.turns.orEmpty() + stampedTurns,
                status = status,
                stepCount = maxOf(taskStepCount, agentHistory.size, existing?.stepCount ?: 0),
                blockCause = blockCause,
                handbackLabel = handbackLabel,
                kind = EntryKind.TASK,
                agentHistory = agentHistory,
            ),
        )
    }

    // --- Stop / dismiss ---

    /** Stop button, back, swipe-down all end here. */
    private fun stopTask() {
        currentJob?.cancel()
        currentJob = null
        dismissSession()
    }

    private fun dismissSession() {
        finish()
    }
}
