package com.saarthi.agent

import com.saarthi.perception.PerceptionResult

/**
 * Checked before the model is ever called, on every step. On a match, the
 * loop emits [AgentEvent.AskUser] immediately — it never reaches the
 * model, never attempts to read or enter credentials. Operates purely on
 * [PerceptionResult.serialized] (a plain string) rather than walking
 * nodes for `isPassword`, so this stays consistent with the rest of
 * `com.saarthi.agent` never importing `android.view.accessibility.*` —
 * [com.saarthi.perception.ScreenPerception] already emits a literal
 * "password" flag token for password fields, which the keyword check
 * below catches the same way it catches "password" appearing in an
 * on-screen label.
 */
object LoginWallDetector {

    private val KEYWORDS = listOf(
        "password", "otp", "verification code", "verify",
        // Hindi
        "पासवर्ड", "ओटीपी", "सत्यापन",
        // Kannada
        "ಗುಪ್ತಪದ", "ಒಟಿಪಿ", "ಪರಿಶೀಲನೆ",
    )

    fun isLoginWall(perception: PerceptionResult): Boolean {
        val text = perception.serialized.lowercase()
        return KEYWORDS.any { text.contains(it.lowercase()) }
    }
}
