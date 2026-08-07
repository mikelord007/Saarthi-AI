package com.saarthi.speech

/** Everything a spoken interaction needs to know about how to speak — bundled so callers take one parameter instead of four. */
data class VoiceSettings(
    val language: Language,
    val speaker: Speaker,
    /** 1.0 normal, ~0.65 for "Speak slowly". */
    val pace: Float,
    val narrateEveryStep: Boolean,
) {
    companion object {
        const val PACE_NORMAL = 1.0f
        const val PACE_SLOW = 0.65f
    }
}
