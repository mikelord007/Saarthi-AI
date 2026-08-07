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
import com.saarthi.speech.VoicePreferences
import com.saarthi.ui.components.VoiceState
import com.saarthi.ui.screens.AssistOverlayScreen
import com.saarthi.ui.screens.HandbackScreen
import com.saarthi.ui.theme.SaarthiTheme

/**
 * The translucent overlay reached via the system ASSIST invocation
 * (long-press home). Window starts as a bottom sheet ([applyWindowMode]
 * false): translucent, `FLAG_NOT_TOUCH_MODAL` so the app underneath is
 * still visible and touchable while this is up. It would become a
 * full-screen modal only for the hand-back hard stop — see
 * [applyWindowMode] — once something actually drives [overlayScreen] there.
 *
 * Screen reading and step-by-step task execution aren't wired up yet: this
 * Activity currently just shows the overlay shell (voice-state mark, close,
 * stop) so the surface exists to build on. [OverlayScreen.Handback] is
 * implemented and ready — nothing sets [overlayScreen] to it yet.
 */
class AssistActivity : ComponentActivity() {

    private val voicePreferences by lazy { VoicePreferences(applicationContext) }

    private var overlayScreen by mutableStateOf<OverlayScreen>(OverlayScreen.Assist)
    private var currentLanguageCode by mutableStateOf("")
    private var voiceState by mutableStateOf(VoiceState.Idle)
    private var narrationLine by mutableStateOf("")
    private var stepLabel by mutableStateOf("")

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

    // --- Hand-back and stop/cancel ---

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
        finish()
    }
}
