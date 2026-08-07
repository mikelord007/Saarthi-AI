package com.saarthi.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing / radius / elevation tokens for the design system. Density 1.15x
 * scale, ported as-drawn (px in the original mockups == dp here).
 */
object SaarthiDimens {
    // Spacing scale
    val Space1: Dp = 4.6.dp
    val Space2: Dp = 9.2.dp
    val Space3: Dp = 13.8.dp
    val Space4: Dp = 18.4.dp
    val Space6: Dp = 27.6.dp
    val Space8: Dp = 36.8.dp

    // Radii
    val RadiusSm: Dp = 2.dp
    val RadiusMd: Dp = 4.dp // buttons, fields, cards
    val RadiusLg: Dp = 7.dp // hand-back card

    // Screen padding
    val OnboardingPaddingTop: Dp = 22.dp
    val OnboardingPaddingSides: Dp = 30.dp
    val OnboardingPaddingBottom: Dp = 26.dp
    val ScreenPaddingSides: Dp = 22.dp // home / history / settings

    // Minimum tap target, per the spec's accessibility floor
    val MinTapTarget: Dp = 48.dp

    /** Elevation is a whisper — no heavy drop shadows. Values as (offsetY, blurRadius, alpha). */
    object Shadow {
        val SmOffsetY: Dp = 1.dp
        const val SmBlur = 2f
        const val SmAlpha = 0.14f

        val MdOffsetY: Dp = 3.dp
        const val MdBlur = 10f
        const val MdAlpha = 0.16f

        val LgOffsetY: Dp = 12.dp
        const val LgBlur = 32f
        const val LgAlpha = 0.22f
    }
}
