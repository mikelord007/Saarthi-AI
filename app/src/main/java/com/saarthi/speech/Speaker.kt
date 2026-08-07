package com.saarthi.speech

/**
 * A voice choice offered in onboarding/Settings. [id] is the identifier the
 * eventual voice pipeline will key off of.
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
            id = "meera",
            displayName = "Meera",
            description = "Warm, unhurried — the default",
            waveformBars = listOf(12, 22, 15),
        ),
        Speaker(
            id = "arvind",
            displayName = "Arvind",
            description = "Low and steady, slower pace",
            waveformBars = listOf(9, 26, 11),
        ),
        Speaker(
            id = "pavithra",
            displayName = "Pavithra",
            description = "Bright and quick, for busy screens",
            waveformBars = listOf(16, 18, 24),
        ),
    )

    val DEFAULT = ALL.first { it.id == "meera" }

    fun byId(id: String): Speaker = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
