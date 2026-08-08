package com.saarthi.speech

import android.content.Context
import com.saarthi.R

/**
 * Per-voice preview for onboarding's Voice screen. [Speaker.id] is now a
 * real Maya voice name, and Maya only offers its own two voices (no
 * sample-clip catalog to bundle), so previewing a tap means actually
 * calling Maya's TTS with that voice — see [MayaTts].
 */
object VoiceSamples {

    /** Stops (mutex in [MayaTts]) whatever preview is playing and starts [speaker]'s, in [language]. */
    suspend fun play(context: Context, speaker: Speaker, language: Language) {
        val sampleLine = context.getString(R.string.onboarding_voice_sample_line)
        MayaTts.speak(
            text = sampleLine,
            settings = VoiceSettings(
                language = language,
                speaker = speaker,
                pace = VoiceSettings.PACE_NORMAL,
                narrateEveryStep = true,
            ),
        )
    }
}
