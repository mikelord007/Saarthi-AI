package com.saarthi.speech

/**
 * A voice choice offered in onboarding. [id] is the exact, case-sensitive
 * voice name Maya's TTS API expects — Maya interpolates it straight into
 * its prompt server-side, so `"ananya"` is a silently different (and
 * wrong) request, not an error (docs.mayaresearch.ai/reference/voices).
 * This is a real voice selection sent to the active TTS provider: see
 * [com.saarthi.speech.MayaTts]'s `startMessage`, which reads
 * [VoiceSettings.speaker]`.id` directly.
 */
data class Speaker(
    val id: String,
    val displayName: String,
    val description: String,
    /** Static waveform "fingerprint" bar heights (dp) — decorative, not driven by audio. */
    val waveformBars: List<Int>,
)

object Speakers {
    val ALL = listOf(
        Speaker(
            id = "Ananya",
            displayName = "Ananya",
            description = "Female voice — the default",
            waveformBars = listOf(12, 22, 15),
        ),
        Speaker(
            id = "Arjun",
            displayName = "Arjun",
            description = "Male voice",
            waveformBars = listOf(9, 26, 11),
        ),
    )

    val DEFAULT = ALL.first { it.id == "Ananya" }

    fun byId(id: String): Speaker = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
