package com.saarthi.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Maps a language code (e.g. "hi-IN", "od-IN" — see [com.saarthi.speech.SupportedLanguages])
 * to the Android [Locale] that resolves the matching `values-<qualifier>` resources.
 * Odia's ISO 639-1 code is "or", not "od", so it needs remapping; every other
 * code's language part already matches its Android qualifier.
 */
fun localeForLanguageCode(languageCode: String): Locale {
    val iso = languageCode.substringBefore("-").let { if (it == "od") "or" else it }
    return Locale(iso, "IN")
}

/**
 * A Context whose resources/configuration are pinned to [languageCode], regardless of
 * this Context's own locale. Used both for [MainActivity]/[AssistActivity]'s
 * `attachBaseContext` (correct language from the very first frame) and for resolving a
 * string tied to a specific language from plain (non-Composable) code.
 */
@Suppress("AppBundleLocaleChanges") // No dynamic feature delivery here — every values-* qualifier ships in the base APK.
fun Context.withLocale(languageCode: String): Context {
    val locale = localeForLanguageCode(languageCode)
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}

/**
 * Wraps [content] so every `stringResource()` (and anything else reading
 * [LocalContext]/[LocalConfiguration]) inside resolves against [languageCode] —
 * live, on every recomposition triggered by a language change, with no Activity
 * recreate. A recreate would work for Settings but would blow away in-flight
 * onboarding nav state, so the whole app is switched by re-pointing the
 * composition's Context instead.
 */
@Composable
fun LocalizedContent(languageCode: String, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val configuration = remember(languageCode) {
        Configuration(base.resources.configuration).apply {
            setLocale(localeForLanguageCode(languageCode))
        }
    }
    val localizedContext = remember(configuration) { base.createConfigurationContext(configuration) }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
    ) {
        content()
    }
}
