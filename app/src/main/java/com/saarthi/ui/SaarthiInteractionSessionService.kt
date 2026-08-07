package com.saarthi.ui

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * The framework's factory for [SaarthiInteractionSession] — bound and asked
 * for a fresh session every time the assist gesture fires. There is
 * deliberately no state here: everything the session needs is constructed
 * inside the session itself.
 */
class SaarthiInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession = SaarthiInteractionSession(this)
}
