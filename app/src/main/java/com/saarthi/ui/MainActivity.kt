package com.saarthi.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
import com.saarthi.perception.SaarthiAccessibilityService
import com.saarthi.speech.AudioRecorder
import com.saarthi.speech.Language
import com.saarthi.speech.SarvamTts
import com.saarthi.speech.Speaker
import com.saarthi.speech.Speakers
import com.saarthi.speech.SpeechToText
import com.saarthi.speech.SupportedLanguages
import com.saarthi.speech.TranscriptionResult
import com.saarthi.speech.VoicePreferences
import com.saarthi.ui.screens.HistoryScreen
import com.saarthi.ui.screens.HomeScreen
import com.saarthi.ui.screens.SettingsScreen
import com.saarthi.ui.screens.ThreadDetailScreen
import com.saarthi.ui.screens.onboarding.AccessibilityScreen
import com.saarthi.ui.screens.onboarding.AssistantScreen
import com.saarthi.ui.screens.onboarding.LanguageScreen
import com.saarthi.ui.screens.onboarding.MicScreen
import com.saarthi.ui.screens.onboarding.PromiseScreen
import com.saarthi.ui.screens.onboarding.VoiceScreen
import com.saarthi.ui.screens.onboarding.WelcomeScreen
import com.saarthi.ui.theme.SaarthiTheme
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Hosts every screen except the translucent assist overlay (that's
 * [AssistActivity]). The app's launcher entry point; the only door that
 * leads to Home.
 */
class MainActivity : ComponentActivity() {

    private val chatHistoryStore by lazy { ChatHistoryStore(applicationContext) }
    private val voicePreferences by lazy { VoicePreferences(applicationContext) }
    private val onboardingPreferences by lazy { OnboardingPreferences(applicationContext) }
    private val audioRecorder by lazy { AudioRecorder(this) }

    private var entries by mutableStateOf<List<ChatEntry>>(emptyList())
    private var draft by mutableStateOf("")
    /** [com.saarthi.ui.screens.ThreadDetailScreen]'s own input — separate from Home's [draft] so opening a thread doesn't leak Home's leftover text into it. */
    private var threadDraft by mutableStateOf("")
    private var isRecording by mutableStateOf(false)
    private var isTranscribing by mutableStateOf(false)
    private var isThinking by mutableStateOf(false)

    /** Highest [AgentEvent.Step] index seen per in-flight entry — consumed by [finishTypedTask] into [ChatTurn.taskStepCount], since [AgentEvent.AskUser]'s `agentHistory` is the only other source of a step count and is empty for a normal Done/Blocked/Error run. */
    private val stepCounts = mutableMapOf<String, Int>()

    private var selectedLanguage by mutableStateOf(SupportedLanguages.DEFAULT)
    private var selectedSpeaker by mutableStateOf(Speakers.DEFAULT)
    private var narrateEveryStep by mutableStateOf(true)
    private var speakSlowly by mutableStateOf(false)
    private var permissions by mutableStateOf(Permissions(microphone = false, accessibility = false, defaultAssistant = false))

    /** A class field, not a `remember` inside setContent, so [submitTask] can navigate to the new thread as soon as it's created. */
    private lateinit var nav: SaarthiNav

    /** Home's mic control — a grant here should immediately start recording. */
    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording()
    }

    /** Onboarding's "Allow microphone" — advances regardless of the result; the grant itself is all this needs to do. */
    private val requestMicPermissionOnboarding = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    /** Sets the app's display language from the very first frame, before onCreate/setContent run — see [LocalizedContent] for the live, no-recreate switch used after this. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withLocale(VoicePreferences(newBase).language.code))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedLanguage = voicePreferences.language
        selectedSpeaker = voicePreferences.speaker
        narrateEveryStep = voicePreferences.narrateEveryStep
        speakSlowly = voicePreferences.speakSlowly
        nav = SaarthiNav(if (onboardingPreferences.isComplete) Screen.Home else Screen.Welcome)

        setContent {
            SaarthiTheme {
                LocalizedContent(languageCode = selectedLanguage.code) {
                BackHandler(enabled = nav.canGoBack) { nav.back() }

                when (val screen = nav.current) {
                    // --- Onboarding: welcome -> language -> voice -> mic -> a11y -> assistant -> promise -> home ---
                    Screen.Welcome -> WelcomeScreen(onBegin = { nav.go(Screen.Language) })
                    Screen.Language -> LanguageScreen(
                        languages = SupportedLanguages.ALL,
                        selected = selectedLanguage,
                        onSelected = ::onLanguageSelected,
                        onContinue = { nav.go(Screen.Voice) },
                        onBack = nav::back,
                    )
                    Screen.Voice -> VoiceScreen(
                        speakers = Speakers.ALL,
                        selected = selectedSpeaker,
                        onSelected = ::onSpeakerSelected,
                        onContinue = { nav.go(Screen.MicPermission) },
                        onBack = nav::back,
                    )
                    Screen.MicPermission -> MicScreen(
                        onAllow = { requestMicPermissionOnboarding.launch(Manifest.permission.RECORD_AUDIO); nav.go(Screen.AccessibilityPermission) },
                        onNotNow = { nav.go(Screen.AccessibilityPermission) },
                        onBack = nav::back,
                    )
                    Screen.AccessibilityPermission -> AccessibilityScreen(
                        onOpenSettings = { openAccessibilitySettings(); nav.go(Screen.AssistantPermission) },
                        onBack = nav::back,
                    )
                    Screen.AssistantPermission -> AssistantScreen(
                        onSetAsAssistant = { openAssistantSettings(); nav.go(Screen.Promise) },
                        onNotNow = { nav.go(Screen.Promise) },
                        onBack = nav::back,
                    )
                    Screen.Promise -> PromiseScreen(
                        onUnderstood = {
                            onboardingPreferences.isComplete = true
                            nav.reset(Screen.Home)
                        },
                    )

                    // --- Everyday use ---
                    Screen.Home -> HomeScreen(
                        language = selectedLanguage,
                        recentEntries = entries.sortedByDescending { it.timestamp },
                        draft = draft,
                        onDraftChange = { draft = it },
                        isRecording = isRecording,
                        isTranscribing = isTranscribing,
                        isThinking = isThinking,
                        onMicTap = ::onMicToggle,
                        onSuggestionTap = ::submitTask,
                        onSend = ::onSend,
                        onOpenHistory = { nav.go(Screen.History) },
                        onOpenThread = { chatId -> threadDraft = ""; nav.go(Screen.ThreadDetail(chatId)) },
                        onOpenSettings = { nav.go(Screen.Settings) },
                    )

                    Screen.History -> HistoryScreen(
                        entries = entries,
                        onBack = nav::back,
                        onOpenThread = { chatId -> threadDraft = ""; nav.go(Screen.ThreadDetail(chatId)) },
                    )
                    is Screen.ThreadDetail -> ThreadDetailScreen(
                        entry = entries.firstOrNull { it.id == screen.chatId },
                        onBack = nav::back,
                        draft = threadDraft,
                        onDraftChange = { threadDraft = it },
                        onSend = { onThreadSend(screen.chatId) },
                    )
                    Screen.Settings -> SettingsScreen(
                        onBack = nav::back,
                        languages = SupportedLanguages.ALL,
                        selectedLanguage = selectedLanguage,
                        onLanguageSelected = ::onLanguageSelected,
                        permissions = permissions,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        onOpenMicrophoneSettings = ::openMicrophoneSettings,
                        onOpenAssistantSettings = ::openAssistantSettings,
                        narrateEveryStep = narrateEveryStep,
                        onNarrateToggle = ::onNarrateToggle,
                        speakSlowly = speakSlowly,
                        onSpeakSlowlyToggle = ::onSpeakSlowlyToggle,
                    )
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reloadHistory()
        permissions = PermissionStatus.snapshot(this)
    }

    private fun reloadHistory() {
        entries = chatHistoryStore.loadAll()
    }

    // --- Task submission (typed, spoken, or a suggestion tap) ---

    private fun onSend() {
        val text = draft.trim()
        if (text.isEmpty()) return
        draft = ""
        submitTask(text)
    }

    private fun onThreadSend(chatId: String) {
        val text = threadDraft.trim()
        if (text.isEmpty()) return
        threadDraft = ""
        continueThread(chatId, text)
    }

    /**
     * A message sent from inside an already-open thread — same idea as
     * [submitTask], but appends instead of creating a new entry.
     *
     * Only resumes the existing task (keeping [ChatEntry.task] and
     * [ChatEntry.agentHistory] as-is) when the thread is actually paused
     * on [ChatStatus.ASK_USER] — that's the one case where the reply is
     * an answer to the agent's own question, not a new instruction.
     * Every other status (DONE, BLOCKED, ERROR) means the previous run
     * already finished, so a new message here is a brand-new task: it
     * must overwrite [ChatEntry.task] and clear [ChatEntry.agentHistory],
     * otherwise [respondTo] would keep running [AgentLoop] against
     * whatever the thread's very first message was, ignoring what the
     * user actually just typed.
     */
    private fun continueThread(chatId: String, userText: String) {
        val existing = chatHistoryStore.find(chatId) ?: return
        val resuming = existing.status == ChatStatus.ASK_USER
        val entry = existing.copy(turns = existing.turns + ChatTurn("user", userText), status = ChatStatus.RUNNING)
        chatHistoryStore.upsert(entry)
        reloadHistory()
        respondTo(entry, incoming = userText, resuming = resuming)
    }

    /**
     * A fresh message from Home — always starts a brand-new thread and
     * navigates straight into it, before any reply arrives, so the exchange
     * plays out live like a normal chat.
     */
    private fun submitTask(userText: String) {
        val entry = ChatEntry(
            id = UUID.randomUUID().toString(),
            task = userText,
            timestamp = System.currentTimeMillis(),
            turns = listOf(ChatTurn("user", userText)),
            status = ChatStatus.RUNNING,
        )
        chatHistoryStore.upsert(entry)
        reloadHistory()
        nav.go(Screen.ThreadDetail(entry.id))
        respondTo(entry, incoming = userText, resuming = false)
    }

    /**
     * Routes a fresh message before doing anything else: [ChatRouter]
     * decides whether this needs [AgentLoop] at all, or is answered
     * directly. This is the only thing standing between every typed
     * message and a real task run — see [ChatRouter]'s own doc for why a
     * plain "how are you" must never reach [AgentLoop.run] (perception
     * excludes Saarthi's own windows, so it would read the Home screen as
     * empty and press HOME to recover, and the task-glow overlay would
     * show for something that isn't a task).
     *
     * [resuming] skips the router entirely — a thread paused on
     * ASK_USER's next reply is an answer to the agent's own question, not
     * a fresh instruction, and there's nothing sensible to classify.
     * [entry.task]/[entry.agentHistory] are only overwritten once we know
     * this is genuinely a new task (see [runAgent]) — a plain chat reply
     * must never clobber the thread's task title with itself.
     *
     * Launched on [SaarthiAccessibilityService.agentScope] when available
     * — see that class's doc for why a takeover task's survival must not
     * depend on the launching surface staying alive — falling back to
     * [lifecycleScope] only for the classify-and-maybe-chat path, which
     * needs neither the service nor that guarantee.
     */
    private fun respondTo(entry: ChatEntry, incoming: String, resuming: Boolean) {
        isThinking = true
        val settings = voicePreferences.settings
        val scope = SaarthiAccessibilityService.current()?.agentScope ?: lifecycleScope
        scope.launch {
            if (resuming) {
                runAgent(entry.id, entry.task, entry.agentHistory, settings)
                return@launch
            }
            when (val decision = ChatRouter.classify(incoming, recentContext(entry), settings.language.displayName)) {
                is RouterDecision.Chat -> finishChatReply(entry.id, decision.reply, settings)
                is RouterDecision.Task -> {
                    val existing = chatHistoryStore.find(entry.id) ?: return@launch
                    chatHistoryStore.upsert(existing.copy(task = decision.task, agentHistory = emptyList()))
                    reloadHistory()
                    runAgent(entry.id, decision.task, emptyList(), settings)
                }
            }
        }
    }

    /** The `Task` half of [respondTo] — everything that was `respondTo` before [ChatRouter] existed. */
    private fun runAgent(entryId: String, task: String, initialHistory: List<String>, settings: com.saarthi.speech.VoiceSettings) {
        val service = SaarthiAccessibilityService.current()
        if (service == null) {
            isThinking = false
            val existing = chatHistoryStore.find(entryId) ?: return
            chatHistoryStore.upsert(
                existing.copy(turns = existing.turns + ChatTurn("assistant", getString(R.string.a11y_required_notice)), status = ChatStatus.ERROR),
            )
            reloadHistory()
            return
        }
        service.agentScope.launch {
            AgentLoop.run(
                service = service,
                task = task,
                languageDisplayName = settings.language.displayName,
                initialHistory = initialHistory,
                narrateEveryStep = settings.narrateEveryStep,
                speak = { text -> SarvamTts.speak(text, settings) },
                onEvent = { event -> handleTypedTaskEvent(entryId, event) },
            )
        }
    }

    /** The `Chat` half of [respondTo] — never touches [AgentLoop]/[SaarthiAccessibilityService] at all. */
    private suspend fun finishChatReply(entryId: String, reply: String, settings: com.saarthi.speech.VoiceSettings) {
        isThinking = false
        val existing = chatHistoryStore.find(entryId) ?: return
        chatHistoryStore.upsert(existing.copy(turns = existing.turns + ChatTurn("assistant", reply), status = ChatStatus.DONE))
        reloadHistory()
        SarvamTts.speak(reply, settings)
    }

    /** A short excerpt for [ChatRouter] — enough to tell "continuing this chat" from "starting fresh" without sending the whole thread. */
    private fun recentContext(entry: ChatEntry): List<String> =
        entry.turns.takeLast(6).map { "${it.role}: ${it.text.take(200)}" }

    private fun handleTypedTaskEvent(entryId: String, event: AgentEvent) {
        // No per-step UI on this surface (unlike InvokeSheet's Working
        // state) — isThinking just stays true for Step events, until a
        // terminal event lands.
        when (event) {
            is AgentEvent.Step -> stepCounts[entryId] = event.index
            is AgentEvent.Done -> finishTypedTask(entryId, ChatStatus.DONE, event.summary)
            is AgentEvent.AskUser -> finishTypedTask(entryId, ChatStatus.ASK_USER, event.question, agentHistory = event.history)
            is AgentEvent.Blocked -> finishTypedTask(
                entryId,
                ChatStatus.BLOCKED,
                event.reason,
                blockCause = event.cause,
                handbackLabel = event.actionLabel,
            )
            is AgentEvent.Error -> finishTypedTask(entryId, ChatStatus.ERROR, event.message)
        }
    }

    private fun finishTypedTask(
        entryId: String,
        status: ChatStatus,
        message: String,
        agentHistory: List<String> = emptyList(),
        blockCause: BlockCause? = null,
        handbackLabel: String? = null,
    ) {
        isThinking = false
        val existing = chatHistoryStore.find(entryId) ?: return
        val stepIndex = stepCounts.remove(entryId) ?: 0
        val stepCount = maxOf(stepIndex, agentHistory.size, existing.stepCount)
        chatHistoryStore.upsert(
            existing.copy(
                turns = existing.turns + ChatTurn(
                    "assistant", message,
                    isTaskStep = true,
                    taskStatus = status,
                    blockCause = blockCause,
                    handbackLabel = handbackLabel,
                    taskStepCount = stepCount,
                ),
                status = status,
                stepCount = stepCount,
                blockCause = blockCause,
                handbackLabel = handbackLabel,
                agentHistory = agentHistory,
            ),
        )
        reloadHistory()
    }

    // --- Voice input alternative ---

    private fun onMicToggle() {
        if (isRecording) {
            stopRecordingAndTranscribe()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        try {
            audioRecorder.start()
            isRecording = true
        } catch (e: SecurityException) {
            // RECORD_AUDIO not granted at runtime despite the check above
            // (e.g. revoked between the check and this call) — stay idle
            // rather than crash.
        }
    }

    private fun stopRecordingAndTranscribe() {
        isRecording = false
        isTranscribing = true
        val settings = voicePreferences.settings
        lifecycleScope.launch {
            val file = audioRecorder.stop()
            if (file == null) {
                isTranscribing = false
                return@launch
            }
            when (val result = SpeechToText.transcribe(file, settings.language)) {
                is TranscriptionResult.Success -> {
                    isTranscribing = false
                    submitTask(result.transcript)
                }
                is TranscriptionResult.Failed -> {
                    isTranscribing = false
                }
            }
        }
    }

    // --- Onboarding + Settings: language, speaker, permissions, behaviour toggles ---

    private fun onLanguageSelected(language: Language) {
        voicePreferences.language = language
        selectedLanguage = language
    }

    private fun onSpeakerSelected(speaker: Speaker) {
        voicePreferences.speaker = speaker
        selectedSpeaker = speaker
    }

    private fun onNarrateToggle(value: Boolean) {
        voicePreferences.narrateEveryStep = value
        narrateEveryStep = value
    }

    private fun onSpeakSlowlyToggle(value: Boolean) {
        voicePreferences.speakSlowly = value
        speakSlowly = value
    }

    private fun openAccessibilitySettings() {
        startActivity(PermissionStatus.accessibilitySettingsIntent())
    }

    private fun openMicrophoneSettings() {
        startActivity(PermissionStatus.appDetailsSettingsIntent(this))
    }

    private fun openAssistantSettings() {
        try {
            startActivity(PermissionStatus.assistantSettingsIntent())
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
