package com.saarthi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The Classical system is a single, deliberately-lit theme — not Material,
 * and not dark-mode-aware. The one dark SURFACE in the product (the promise
 * screen) is a fixed design choice, not a system setting, so this theme never
 * reads [isSystemInDarkTheme] and there is no values-night variant.
 *
 * Still wraps [MaterialTheme]: `material3.Text` / `OutlinedTextField` read
 * `LocalContentColor` / `LocalTextStyle` from it, so any un-styled Text would
 * otherwise fall back to Material's default purple.
 */
@Composable
fun SaarthiTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = SaarthiColors.Accent700,
        onPrimary = SaarthiColors.Accent100,
        secondary = SaarthiColors.Accent,
        background = SaarthiColors.Bg,
        onBackground = SaarthiColors.Text,
        surface = SaarthiColors.Bg,
        onSurface = SaarthiColors.Text,
        surfaceVariant = SaarthiColors.Surface,
        onSurfaceVariant = SaarthiColors.Neutral700,
        outline = SaarthiColors.Divider,
        error = SaarthiColors.Accent800,
    )

    val bodyStyle = TextStyle(
        fontFamily = SaarthiType.Lora,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = SaarthiColors.Text,
    )
    val typography = Typography(
        bodyLarge = bodyStyle,
        bodyMedium = bodyStyle.copy(fontSize = 14.5.sp, lineHeight = 21.sp),
        bodySmall = bodyStyle.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
        headlineLarge = bodyStyle.copy(fontFamily = SaarthiType.Cormorant, fontSize = 36.sp, lineHeight = 41.sp),
        headlineMedium = bodyStyle.copy(fontFamily = SaarthiType.Cormorant, fontSize = 30.sp, lineHeight = 38.sp),
        headlineSmall = bodyStyle.copy(fontFamily = SaarthiType.Cormorant, fontSize = 23.sp, lineHeight = 27.sp),
        titleLarge = bodyStyle.copy(fontFamily = SaarthiType.Cormorant, fontWeight = FontWeight.W500, fontSize = 20.sp),
        labelLarge = bodyStyle.copy(fontFamily = SaarthiType.Cormorant, fontSize = 19.sp, letterSpacing = 1.5.sp),
    )

    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}

/**
 * The promise screen is the only dark surface in the app — its status bar
 * icons need to flip to light-on-dark for the duration it's shown, then
 * restore. Every other screen leaves the system default alone.
 */
@Composable
fun SetStatusBarAppearance(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(lightIcons) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !lightIcons
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }
}

/** Ensures edge-to-edge drawing is enabled once, from each Activity's onCreate. */
fun enableEdgeToEdge(window: android.view.Window) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
}
