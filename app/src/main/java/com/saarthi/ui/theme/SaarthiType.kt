package com.saarthi.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.saarthi.R

/**
 * Fonts: Cormorant Garamond (headings/figures) + Lora (body). Bold is never
 * used — 600 is the ceiling; display sizes use 400.
 *
 * Both bundled files are VARIABLE fonts (Google Fonts no longer ships static
 * cuts for these families). Each [Font] entry below selects a distinct
 * instance of the same file via the `wght` axis — `Font(resId, weight)`
 * defaults its `variationSettings` to that weight, which is what lets one
 * TTF serve four visually distinct weights. Variable-font axis rendering is
 * a platform feature since API 26, matching this app's minSdk exactly.
 *
 * Indic scripts (Devanagari, Kannada, Tamil, etc.) are NOT covered by either
 * family — Android's per-glyph system fallback substitutes Noto automatically,
 * so a language switch changes script, not layout.
 */
object SaarthiType {

    val Cormorant = FontFamily(
        Font(R.font.cormorant_garamond_variable, FontWeight.W300),
        Font(R.font.cormorant_garamond_variable, FontWeight.W400),
        Font(R.font.cormorant_garamond_variable, FontWeight.W500),
        Font(R.font.cormorant_garamond_variable, FontWeight.W600),
    )

    val Lora = FontFamily(
        Font(R.font.lora_variable, FontWeight.W400),
        Font(R.font.lora_variable, FontWeight.W500),
        Font(R.font.lora_variable, FontWeight.W600),
        Font(R.font.lora_italic_variable, FontWeight.W400, FontStyle.Italic),
    )

    /** Figures set tabular — step counters, times, amounts. */
    val TabularFigures = TextStyle(fontFeatureSettings = "tnum")

    fun TextStyle.tabularFigures(): TextStyle = this.merge(TabularFigures)
}
