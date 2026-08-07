package com.saarthi.speech

import android.content.Context

/**
 * Persists the user's chosen language, speaker, "Speak slowly", and
 * "Narrate every step".
 *
 * "Always hand back before payment" is deliberately NOT here: it's meant to
 * stay a fixed safety behavior, never a user preference, once the task
 * automation is wired up.
 */
class VoicePreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language: Language
        get() {
            val code = prefs.getString(KEY_LANGUAGE_CODE, SupportedLanguages.DEFAULT.code)
            return SupportedLanguages.ALL.firstOrNull { it.code == code } ?: SupportedLanguages.DEFAULT
        }
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE_CODE, value.code).apply()
        }

    var speaker: Speaker
        get() = Speakers.byId(prefs.getString(KEY_SPEAKER_ID, Speakers.DEFAULT.id) ?: Speakers.DEFAULT.id)
        set(value) {
            prefs.edit().putString(KEY_SPEAKER_ID, value.id).apply()
        }

    var speakSlowly: Boolean
        get() = prefs.getBoolean(KEY_SPEAK_SLOWLY, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SPEAK_SLOWLY, value).apply()
        }

    var narrateEveryStep: Boolean
        get() = prefs.getBoolean(KEY_NARRATE_EVERY_STEP, true)
        set(value) {
            prefs.edit().putBoolean(KEY_NARRATE_EVERY_STEP, value).apply()
        }

    /** Convenience snapshot for callers that just want one bundled settings object. */
    val settings: VoiceSettings
        get() = VoiceSettings(
            language = language,
            speaker = speaker,
            pace = if (speakSlowly) VoiceSettings.PACE_SLOW else VoiceSettings.PACE_NORMAL,
            narrateEveryStep = narrateEveryStep,
        )

    private companion object {
        const val PREFS_NAME = "saarthi_prefs"
        const val KEY_LANGUAGE_CODE = "language_code"
        const val KEY_SPEAKER_ID = "speaker_id"
        const val KEY_SPEAK_SLOWLY = "speak_slowly"
        const val KEY_NARRATE_EVERY_STEP = "narrate_every_step"
    }
}
