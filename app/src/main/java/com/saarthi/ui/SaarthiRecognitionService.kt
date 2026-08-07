package com.saarthi.ui

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * A required, otherwise-unused citizen: `VoiceInteractionService`'s metadata
 * (res/xml/interaction_service.xml) MUST name a real `recognitionService`
 * component or the platform won't offer this app as an assistant candidate
 * at all. This class exists purely to satisfy that manifest contract and is
 * never expected to be invoked.
 */
class SaarthiRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: RecognitionService.Callback) {
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: RecognitionService.Callback) {}

    override fun onStopListening(listener: RecognitionService.Callback) {}
}
