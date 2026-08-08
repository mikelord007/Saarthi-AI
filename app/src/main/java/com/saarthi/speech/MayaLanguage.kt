package com.saarthi.speech

/**
 * Maps [Language.code] to the 2-letter code Maya's TTS API expects.
 * Deliberately a table, not `code.substringBefore('-')` — Saarthi's
 * `od-IN` for Odia is non-standard (it follows Sarvam's STT convention);
 * ISO 639-1 for Odia is `or`, and Maya, like most providers, expects the
 * ISO code — a naive slice would silently send the wrong language code.
 *
 * Verified against Maya's docs (https://docs.mayaresearch.ai/reference/languages,
 * checked 2026-08-08): Maya supports exactly these 11 languages, and
 * Sarvam's STT (the app's other provider) supports a superset that
 * includes all 11 of the app's codes too — so [SupportedLanguages.ALL]
 * is already the full Maya/Sarvam intersection; nothing to trim.
 * [FALLBACK] (English) stays as a defensive default, never expected to
 * actually trigger.
 */
object MayaLanguage {

    private val CODES = mapOf(
        "en-IN" to "en",
        "hi-IN" to "hi",
        "kn-IN" to "kn",
        "ta-IN" to "ta",
        "te-IN" to "te",
        "mr-IN" to "mr",
        "bn-IN" to "bn",
        "gu-IN" to "gu",
        "ml-IN" to "ml",
        "pa-IN" to "pa",
        "od-IN" to "or", // Odia — the ISO 639-1 / Maya code is "or", not "od"
    )

    const val FALLBACK = "en"

    fun mayaCodeFor(language: Language): String = CODES[language.code] ?: FALLBACK
}
