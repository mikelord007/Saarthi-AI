package com.saarthi.perception

import android.view.accessibility.AccessibilityNodeInfo

private const val DESCENDANT_TEXT_DEPTH = 3

/** [label] is the node's own visible text — used to tell the user what button it stopped at. */
data class GuardMatch(val label: String, val matchedKeyword: String)

/**
 * The primary, code-level control against irreversible actions — the
 * eventual system-prompt instruction telling the model to use `blocked`
 * for pay/send/confirm/etc. is a backup, not the guard itself. Runs before
 * every tap and long-press, independent of what the model said it was
 * doing.
 *
 * English keywords are matched with `\b` word boundaries, case-insensitive
 * — plain substring matching was a real bug in the app this design is
 * based on: it matched "pay" inside "Paytm", "Repay", "Payments", tripping
 * the guard on the Paytm app icon itself or a "Repayment history" row.
 * "payment"/"purchase"/"checkout" are included as their own word-bounded
 * keywords (not just "pay") so a "Make Payment" button is still caught —
 * `\bpay\b` alone wouldn't match "Payment" — without reopening that bug:
 * `\bpayment\b` still doesn't match inside "Repayment" or "Paytm" either.
 *
 * Indic-script keywords are matched as plain substrings, deliberately not
 * `\b`-bounded — Kotlin's `Regex` word boundary is ASCII-word-character
 * based and doesn't reliably bound Devanagari/Kannada/Tamil/etc. runs.
 * These lists are a best-effort pass across ten languages, not a
 * professional translation review — worth a native-speaker check before
 * this ships, since it's the safety-critical half of the app.
 */
object IrreversibleActionGuard {

    private val ENGLISH_KEYWORDS = listOf(
        "pay", "payment", "send", "confirm", "order", "submit", "transfer", "buy", "purchase", "checkout",
    )

    private val INDIC_KEYWORDS = listOf(
        // Hindi
        "भुगतान", "भेजें", "पुष्टि", "सबमिट", "ट्रांसफर", "खरीदें",
        // Kannada
        "ಪಾವತಿ", "ಕಳುಹಿಸಿ", "ಖಚಿತಪಡಿಸಿ", "ಸಲ್ಲಿಸಿ", "ವರ್ಗಾವಣೆ", "ಖರೀದಿಸಿ",
        // Tamil
        "செலுத்து", "அனுப்பு", "உறுதிப்படுத்து", "சமர்ப்பி", "வாங்கு",
        // Telugu
        "చెల్లించండి", "పంపండి", "నిర్ధారించండి", "సమర్పించండి", "బదిలీ", "కొనండి",
        // Marathi
        "पेमेंट", "पाठवा", "पुष्टी", "हस्तांतरण", "खरेदी करा",
        // Bengali
        "পেমেন্ট", "পাঠান", "নিশ্চিত করুন", "জমা দিন", "স্থানান্তর", "কিনুন",
        // Gujarati
        "ચુકવણી", "મોકલો", "ખાતરી કરો", "સબમિટ કરો", "સ્થાનાંતરણ", "ખરીદો",
        // Malayalam
        "പണമടയ്ക്കുക", "അയയ്ക്കുക", "സ്ഥിരീകരിക്കുക", "സമർപ്പിക്കുക", "കൈമാറ്റം", "വാങ്ങുക",
        // Punjabi
        "ਭੁਗਤਾਨ", "ਭੇਜੋ", "ਪੁਸ਼ਟੀ", "ਜਮ੍ਹਾਂ ਕਰੋ", "ਟ੍ਰਾਂਸਫਰ", "ਖਰੀਦੋ",
        // Odia
        "ପେମେଣ୍ଟ", "ପଠାନ୍ତୁ", "ନିଶ୍ଚିତ", "ଦାଖଲ କରନ୍ତୁ", "କିଣନ୍ତୁ",
    )

    private val englishPatterns = ENGLISH_KEYWORDS.map { keyword ->
        keyword to Regex("\\b${Regex.escape(keyword)}\\b", RegexOption.IGNORE_CASE)
    }

    /** Null if [node] is safe to act on. Non-null carries the node's own label and the keyword that matched. */
    fun check(node: AccessibilityNodeInfo): GuardMatch? {
        val ownLabel = NodeText.ownLabel(node) ?: ""
        val descendantText = NodeText.collectDescendantText(node, DESCENDANT_TEXT_DEPTH)
        val combined = listOf(ownLabel, descendantText).filter { it.isNotBlank() }.joinToString(" ")
        if (combined.isBlank()) return null

        for ((keyword, pattern) in englishPatterns) {
            if (pattern.containsMatchIn(combined)) return GuardMatch(label = ownLabel, matchedKeyword = keyword)
        }
        for (keyword in INDIC_KEYWORDS) {
            if (combined.contains(keyword)) return GuardMatch(label = ownLabel, matchedKeyword = keyword)
        }
        return null
    }
}
