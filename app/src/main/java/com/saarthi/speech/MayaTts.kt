package com.saarthi.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.saarthi.BuildConfig
import com.saarthi.net.SaarthiHttp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val MAYA_TTS_URL = "https://tts.mayaresearch.ai/v1/tts"
private const val SAMPLE_RATE_HZ = 24000
private const val BYTES_PER_SAMPLE = 2 // 16-bit mono PCM

// Maya offers 2 voices (Ananya/Arjun); the app's existing Speaker picker
// (meera/arvind/pavithra, see Speaker.kt) is unrelated UI-preference
// metadata for v1 rather than a real voice selection — see the
// implementation doc's open question about unifying the two. Kept behind
// one constant so wiring a real Speaker -> Maya-voice map later is a
// one-file change.
private const val MAYA_VOICE = "Ananya"

/**
 * Narration playback: POSTs to Maya's one-off `/v1/tts` endpoint (not the
 * WebSocket streaming variant — narration lines are short, so the latency
 * difference doesn't matter here) and plays the raw 24kHz mono 16-bit PCM
 * response through a single, freshly-built [AudioTrack] per call.
 *
 * Calls are serialized by [mutex] and don't return until playback
 * actually finishes — [com.saarthi.agent.AgentLoop]'s per-step narration
 * is fired via `scope.launch { speak(...) }` (fire-and-forget from the
 * loop's perspective, so step N+1's perception can proceed while step N's
 * audio is still playing), but without this serialization here, two
 * narrations queued close together would talk over each other.
 *
 * [AudioTrack.MODE_STATIC], not `MODE_STREAM`: the whole response body is
 * already read into memory by [fetchPcm] before any playback starts (no
 * incremental/chunked decode), so there's no streaming benefit to
 * `MODE_STREAM` here — revisit if this ever moves to Maya's WebSocket
 * endpoint for genuinely incremental playback.
 */
object MayaTts {

    private val mutex = Mutex()

    suspend fun speak(text: String, settings: VoiceSettings) {
        if (text.isBlank()) return
        mutex.withLock {
            val pcm = fetchPcm(text, settings) ?: return@withLock
            play(pcm)
        }
    }

    private suspend fun fetchPcm(text: String, settings: VoiceSettings): ByteArray? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.MAYA_API_KEY
        if (apiKey.isBlank()) return@withContext null

        val requestBody = buildJsonObject {
            put("text", text)
            put("voice", MAYA_VOICE)
            put("language", MayaLanguage.mayaCodeFor(settings.language))
        }

        val request = Request.Builder()
            .url(MAYA_TTS_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("content-type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            SaarthiHttp.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.bytes()
            }
        } catch (e: IOException) {
            null
        }
    }

    private suspend fun play(pcm: ByteArray) {
        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE_HZ, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufferSize <= 0 || pcm.isEmpty()) return

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBufferSize, pcm.size))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            audioTrack.write(pcm, 0, pcm.size)
            audioTrack.play()
            val durationMs = (pcm.size / BYTES_PER_SAMPLE) * 1000L / SAMPLE_RATE_HZ
            delay(durationMs)
        } finally {
            runCatching { audioTrack.stop() }
            audioTrack.release()
        }
    }
}
