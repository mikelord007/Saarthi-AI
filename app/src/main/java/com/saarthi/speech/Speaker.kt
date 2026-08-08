package com.saarthi.speech

/**
 * A voice choice offered in onboarding. [id] is a real Sarvam `bulbul:v3`
 * speaker name (lowercase, sent as-is in the TTS `speaker` field — see
 * [com.saarthi.speech.SarvamTts]), curated from Sarvam's published
 * voice directory for quality (low critical-error-rate) and tone —
 * this is not the full ~35-voice catalog, just a good spread of it.
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
            id = "shubh",
            displayName = "Shubh",
            description = "Warm and steady — the default",
            waveformBars = listOf(12, 22, 15),
        ),
        Speaker(
            id = "mani",
            displayName = "Mani",
            description = "Low and clear, slower pace",
            waveformBars = listOf(9, 26, 11),
        ),
        Speaker(
            id = "priya",
            displayName = "Priya",
            description = "Bright and quick, for busy screens",
            waveformBars = listOf(16, 18, 24),
        ),
        Speaker(
            id = "ishita",
            displayName = "Ishita",
            description = "Warm and clear, a gentler pace",
            waveformBars = listOf(14, 20, 17),
        ),
    )

    val DEFAULT = ALL.first { it.id == "shubh" }

    fun byId(id: String): Speaker = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
