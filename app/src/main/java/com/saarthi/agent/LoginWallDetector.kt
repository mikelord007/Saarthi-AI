package com.saarthi.agent

import com.saarthi.perception.PerceptionResult

/**
 * Checked before the model is ever called, on every step. On a match, the
 * loop emits [AgentEvent.AskUser] immediately — it never reaches the
 * model, never attempts to read or enter credentials. Operates purely on
 * [PerceptionResult.serialized] (a plain string) rather than walking
 * nodes directly, so this stays consistent with the rest of
 * `com.saarthi.agent` never importing `android.view.accessibility.*`.
 *
 * Requires BOTH an editable field present on screen AND a keyword match
 * — a bare keyword match alone false-positived on ordinary navigation
 * screens (e.g. Settings' home list, which merely has a "Passwords &
 * accounts" row with no input field anywhere). Every genuine login/OTP/
 * verification screen has at least one editable field; a menu that only
 * mentions "password" in a label does not. This still catches a
 * genuine `isPassword` field via the literal "password" flag token
 * [com.saarthi.perception.ScreenPerception] emits alongside "editable"
 * for it, and still catches an OTP/verification field even when the
 * matching text is a nearby label rather than the field's own hint —
 * it does not require the keyword and the editable flag on the same
 * line, only that both appear somewhere on screen.
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
        val hasEditableField = text.lineSequence().any { " editable" in it }
        return hasEditableField && KEYWORDS.any { text.contains(it.lowercase()) }
    }
}
