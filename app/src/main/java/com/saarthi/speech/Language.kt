package com.saarthi.speech

/**
 * [displayName] is English and MUST stay English-only — it's the label the
 * eventual voice pipeline will key off of. [nativeName] and [latinName] are
 * additive, display-only fields for the language picker (e.g. "हिन्दी" / "Hindi").
 */
data class Language(val code: String, val displayName: String, val nativeName: String, val latinName: String)

/** The supported language list, in the onboarding's display order. */
object SupportedLanguages {
    val ALL = listOf(
        Language("en-IN", "English", nativeName = "English", latinName = "India"),
        Language("hi-IN", "Hindi", nativeName = "हिन्दी", latinName = "Hindi"),
        Language("kn-IN", "Kannada", nativeName = "ಕನ್ನಡ", latinName = "Kannada"),
        Language("ta-IN", "Tamil", nativeName = "தமிழ்", latinName = "Tamil"),
        Language("te-IN", "Telugu", nativeName = "తెలుగు", latinName = "Telugu"),
        Language("mr-IN", "Marathi", nativeName = "मराठी", latinName = "Marathi"),
        Language("bn-IN", "Bengali", nativeName = "বাংলা", latinName = "Bengali"),
        Language("gu-IN", "Gujarati", nativeName = "ગુજરાતી", latinName = "Gujarati"),
        Language("ml-IN", "Malayalam", nativeName = "മലയാളം", latinName = "Malayalam"),
        Language("pa-IN", "Punjabi", nativeName = "ਪੰਜਾਬੀ", latinName = "Punjabi"),
        Language("od-IN", "Odia", nativeName = "ଓଡ଼ିଆ", latinName = "Odia"),
    )

    /** Onboarding forces an explicit choice on first run; this is only the fallback for a corrupt/missing preference. */
    val DEFAULT = ALL.first { it.code == "en-IN" }
}
