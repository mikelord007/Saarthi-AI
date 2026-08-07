package com.saarthi.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/** A live snapshot of the three permissions onboarding asks for and Settings shows. */
data class Permissions(
    val microphone: Boolean,
    val accessibility: Boolean,
    val defaultAssistant: Boolean,
)

/**
 * Reads permission state directly from the system every time — drives the
 * Settings pills from the live values, not stored flags. Callers re-snapshot
 * in onResume.
 */
object PermissionStatus {

    fun snapshot(context: Context): Permissions = Permissions(
        microphone = hasMicrophone(context),
        accessibility = hasAccessibility(context),
        defaultAssistant = isDefaultAssistant(context),
    )

    private fun hasMicrophone(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** True if the system's enabled-accessibility-services list contains any component of this app. */
    private fun hasAccessibility(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { it.startsWith("${context.packageName}/") }
    }

    /**
     * No public API exists for "is this app the default assistant." Best
     * effort via the two Settings.Secure keys different Android versions
     * use; if neither can be read, report false rather than claim a
     * permission we can't verify.
     */
    private fun isDefaultAssistant(context: Context): Boolean {
        val cr = context.contentResolver
        val assistant = runCatching { Settings.Secure.getString(cr, "assistant") }.getOrNull()
        val voiceInteraction = runCatching { Settings.Secure.getString(cr, "voice_interaction_service") }.getOrNull()
        return assistant?.contains(context.packageName) == true || voiceInteraction?.contains(context.packageName) == true
    }

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Absent on some OEM ROMs — callers must wrap the resulting startActivity in try/catch(ActivityNotFoundException) and fall back to ACTION_SETTINGS. */
    fun assistantSettingsIntent(): Intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)

    /** RECORD_AUDIO has no dedicated settings screen — this app-details page is where a permanently-denied permission gets re-granted. */
    fun appDetailsSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
