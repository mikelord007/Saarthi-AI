package com.saarthi.speech

import com.saarthi.BuildConfig
import com.saarthi.net.SaarthiHttp
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

private const val SARVAM_STT_URL = "https://api.sarvam.ai/speech-to-text"

/** Latest STT model as of writing. Narration TTS is on Maya now (see [com.saarthi.speech.MayaTts]), an unrelated provider, so there's no matching TTS-model pick to cross-reference here anymore. */
private const val SARVAM_MODEL = "saaras:v4"

sealed interface TranscriptionResult {
    data class Success(val transcript: String) : TranscriptionResult
    data class Failed(val message: String) : TranscriptionResult
}

/**
 * Uploads a WAV file recorded by [AudioRecorder] to Sarvam's
 * `/speech-to-text` endpoint — a REST endpoint documented for "quick
 * responses under 30s", not a streaming one, which fits a "user taps mic,
 * speaks a task, releases" capture flow. A genuinely live/streaming
 * transcript would need a different Sarvam endpoint and is out of scope
 * for v1. Not wired into the existing `SaarthiRecognitionService` stub —
 * see that class's own doc for why.
 */
object SpeechToText {

    suspend fun transcribe(audioFile: File, language: Language): TranscriptionResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.SARVAM_API_KEY
        if (apiKey.isBlank()) {
            return@withContext TranscriptionResult.Failed("Sarvam API key not configured (local.properties: sarvam.apiKey)")
        }
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext TranscriptionResult.Failed("No audio was recorded")
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", SARVAM_MODEL)
            .addFormDataPart("language_code", language.code)
            .build()

        val request = Request.Builder()
            .url(SARVAM_STT_URL)
            .header("api-subscription-key", apiKey)
            .post(requestBody)
            .build()

        try {
            SaarthiHttp.client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string()
                if (!response.isSuccessful || rawBody == null) {
                    return@use TranscriptionResult.Failed("Sarvam request failed: HTTP ${response.code}")
                }
                val json = runCatching { Json.parseToJsonElement(rawBody).jsonObject }.getOrNull()
                val transcript = json?.get("transcript")?.jsonPrimitive?.contentOrNull
                if (transcript.isNullOrBlank()) {
                    TranscriptionResult.Failed("Sarvam returned an empty transcript")
                } else {
                    TranscriptionResult.Success(transcript)
                }
            }
        } catch (e: IOException) {
            TranscriptionResult.Failed("Sarvam request failed: ${e.message}")
        }
    }
}
