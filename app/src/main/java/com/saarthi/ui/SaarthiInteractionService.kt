package com.saarthi.ui

import android.service.voice.VoiceInteractionService
import android.util.Log

private const val TAG = "SaarthiInteractionService"

/**
 * The system binds this (permission `BIND_VOICE_INTERACTION`, see the
 * manifest) once this app is chosen as the default assistant, and calls
 * into it whenever the assist gesture — long-press home, or the
 * gesture-nav corner swipe — fires. It exists only so the framework has a
 * component to resolve `sessionService` against (see
 * res/xml/interaction_service.xml); all real behaviour lives in
 * [SaarthiInteractionSession], created via [SaarthiInteractionSessionService].
 *
 * This is a second, separate entry point from [AssistActivity]'s
 * `android.intent.action.ASSIST` activity — that one still exists and still
 * works stand-alone (Settings' assistant chooser lists apps satisfying
 * either mechanism). This service owns only the voice session lifecycle.
 */
class SaarthiInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "Voice interaction service ready")
    }
}
