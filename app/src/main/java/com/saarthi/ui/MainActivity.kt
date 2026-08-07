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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.saarthi.chat.ChatEntry
import com.saarthi.chat.ChatHistoryStore
import com.saarthi.chat.ChatStatus
import com.saarthi.chat.ChatTurn
import com.saarthi.speech.Language
import com.saarthi.speech.Speaker
import com.saarthi.speech.Speakers
import com.saarthi.speech.SupportedLanguages
import com.saarthi.speech.VoicePreferences
import com.saarthi.ui.screens.HomeScreen
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

    private var entries by mutableStateOf<List<ChatEntry>>(emptyList())
    private var draft by mutableStateOf("")
    /** [com.saarthi.ui.screens.ThreadDetailScreen]'s own input — separate from Home's [draft] so opening a thread doesn't leak Home's leftover text into it. */
    private var threadDraft by mutableStateOf("")
    private var isRecording by mutableStateOf(false)
    private var isTranscribing by mutableStateOf(false)
    private var isThinking by mutableStateOf(false)

    private var selectedLanguage by mutableStateOf(SupportedLanguages.DEFAULT)
    private var selectedSpeaker by mutableStateOf(Speakers.DEFAULT)
    private var narrateEveryStep by mutableStateOf(true)
    private var speakSlowly by mutableStateOf(false)
    private var permissions by mutableStateOf(Permissions(microphone = false, accessibility = false, defaultAssistant = false))

    /** A class field, not a `remember` inside setContent, so [submitTask] can navigate to the new thread as soon as it's created. */
    private lateinit var nav: SaarthiNav

    /** Home's mic control — a grant here should immediately reflect as recording. */
    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) isRecording = true
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
                    // Wired up in a later commit.
                    Screen.Welcome, Screen.Language, Screen.Voice, Screen.MicPermission,
                    Screen.AccessibilityPermission, Screen.AssistantPermission, Screen.Promise -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.TextButton(onClick = {
                                onboardingPreferences.isComplete = true
                                nav.reset(Screen.Home)
                            }) {
                                Text("Coming soon — tap to skip to Home")
                            }
                        }
                    }

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
                        onOpenSettings = { nav.go(Screen.Settings) },
                    )

                    // --- History / thread detail / settings: wired up in a later commit. ---
                    Screen.History, is Screen.ThreadDetail, Screen.Settings -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Coming soon")
                        }
                    }
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
        respondTo(entry)
    }

    /**
     * Turns a submitted message into a reply. This is the seam where real
     * task handling plugs in later — for now it just closes the loop with a
     * placeholder line so every screen downstream (thread view, history,
     * status) has something real to render.
     */
    private fun respondTo(entry: ChatEntry) {
        isThinking = true
        lifecycleScope.launch {
            val replyText = "Noted — I'll be able to act on this soon."
            isThinking = false
            chatHistoryStore.upsert(entry.copy(turns = entry.turns + ChatTurn("assistant", replyText), status = ChatStatus.DONE))
            reloadHistory()
        }
    }

    // --- Voice input alternative ---

    /** Flips the recording indicator only — capture/transcription isn't wired up yet. */
    private fun onMicToggle() {
        if (isRecording) {
            isRecording = false
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            isRecording = true
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
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
