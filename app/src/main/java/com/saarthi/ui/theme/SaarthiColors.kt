package com.saarthi.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens for the app's "Classical" design system: an editorial,
 * paper-ground, stroke-first palette — NOT Material. Gold (`Accent` and its
 * scale) is used as a stroke/text color everywhere in the product except one
 * place: the hand-back confirm button is the single solid-gold fill in the
 * whole app, and that contrast is deliberate — see HandbackScreen. Do not
 * fill anything else with Accent.
 */
object SaarthiColors {
    // Ground
    val Bg = Color(0xFFF3F2F2)
    val Surface = Color(0xFFEAE9E9)
    val Text = Color(0xFF201F1D)
    val Divider = Color(0x29201F1D) // 16% of #201f1d

    // Gold — stroke/text only (see class doc)
    val Accent = Color(0xFFB68235)

    // Neutrals 100–900
    val Neutral100 = Color(0xFFF8F4F4)
    val Neutral200 = Color(0xFFEAE7E7)
    val Neutral300 = Color(0xFFD7D3D3)
    val Neutral400 = Color(0xFFBAB6B6)
    val Neutral500 = Color(0xFF9B9797)
    val Neutral600 = Color(0xFF7D7979)
    val Neutral700 = Color(0xFF605D5D)
    val Neutral800 = Color(0xFF444141)
    val Neutral900 = Color(0xFF2D2B2B) // Promise screen ground

    // Gold scale 100–900
    val Accent100 = Color(0xFFFFF3E4) // Hand-back card fill
    val Accent200 = Color(0xFFFFE3BF)
    val Accent300 = Color(0xFFFACB8D) // Gold ON DARK (promise, overlay marks)
    val Accent400 = Color(0xFFE1AD66)
    val Accent500 = Color(0xFFC28D41)
    val Accent600 = Color(0xFFA06F24)
    val Accent700 = Color(0xFF7D5411) // Gold text on light ground
    val Accent800 = Color(0xFF5A3B0A) // Pressed state of the confirm button
    val Accent900 = Color(0xFF3A270D) // Body copy inside the hand-back card

    // Ad-hoc overlay/scrim colors used only on the assist/handback surfaces
    val OverlayInkStrong = Color(0xB8201F1D) // rgba(32,31,29,.72) scrim top
    val OverlayInkSoft = Color(0x29201F1D) // rgba(32,31,29,.16) scrim bottom
    val OverlayInkFlat = Color(0x9E201F1D) // rgba(32,31,29,.62) handback flat scrim
    val OnDarkRule = Color(0x29FFFFFF) // rgba(255,255,255,.16)
    val OnDarkRuleStrong = Color(0x33FFFFFF) // rgba(255,255,255,.2)
    val OnDarkHover = Color(0x0FFFFFFF) // rgba(255,255,255,.06)
    val OnDarkInk40 = Color(0x66FFFFFF) // rgba(255,255,255,.4) — idle mark on dark
}
