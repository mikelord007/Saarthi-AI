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
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.saarthi.R
import com.saarthi.speech.VoicePreferences
import com.saarthi.ui.screens.InvokeSheet
import com.saarthi.ui.screens.InvokeState
import com.saarthi.ui.theme.SaarthiTheme

/**
 * The live session behind [InvokeSheet]: owns the sheet's [InvokeState] and
 * responds to its callbacks. Voice capture and task handling aren't wired
 * up yet, so only [InvokeState.Listening] is reachable right now — [Heard]
 * and [Working] are fully built and ready for whichever surface ends up
 * producing a transcript and a plan.
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

    /** Read fresh every use — the user may change language in Settings between invocations. */
    private val voice get() = voicePreferences.settings

    private var invokeState by mutableStateOf<InvokeState>(InvokeState.Listening)
    private var isRecording = false

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
                    onMicTap = ::onMicTap,
                    onReadScreenAloud = ::dismissSession,
                    onDoTaskForMe = ::dismissSession,
                    onConfirmPlan = ::dismissSession,
                    onRetryTranscript = ::beginListening,
                    onStop = ::dismissSession,
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

    // --- Listening ---

    private fun beginListening() {
        invokeState = InvokeState.Listening
        isRecording = false
    }

    /** Flips a local flag only — voice capture isn't wired up yet. */
    private fun onMicTap() {
        isRecording = !isRecording
    }

    // --- Stop / dismiss ---

    /** Stop button, back, swipe-down all end here. */
    private fun dismissSession() {
        finish()
    }
}
