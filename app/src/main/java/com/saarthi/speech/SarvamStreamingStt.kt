package com.saarthi.speech

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresPermission
import com.saarthi.BuildConfig
import com.saarthi.net.SaarthiHttp
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private const val TAG = "SaarthiAgent"

// "https", not "wss" - see SarvamTts's SARVAM_TTS_WS_URL doc for why:
// HttpUrl.toHttpUrl() (used below to add query params) only accepts
// http/https, and the scheme label doesn't affect the wire protocol once
// the WebSocket Upgrade handshake happens.
private const val SARVAM_STT_WS_URL = "https://api.sarvam.ai/speech-to-text-realtime/ws"

/** The only value Sarvam's realtime endpoint accepts as of writing — not a choice, kept as a named constant for clarity/pinning. */
private const val SARVAM_STT_REALTIME_MODEL = "saaras:v3-realtime"

private const val SAMPLE_RATE_HZ = 16000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT

/** Safety net if the server never finalizes and the connection doesn't close on its own. */
private const val FINALIZE_TIMEOUT_MS = 30_000L

/**
 * Live mic capture streamed straight to Sarvam's realtime speech-to-text
 * WebSocket, for surfaces that want hands-free end-of-speech detection
 * instead of a manual "tap to stop": the server's own VAD (`endpointing`
 * defaults to `vad`) finalizes the transcript once it detects the user
 * has stopped talking, so [record] returns on its own — no [stop] call
 * required for the normal case.
 *
 * [stop] still exists for a manual early cut, mirroring the mic orb's
 * existing "tap while listening = stop" affordance (see
 * [com.saarthi.ui.screens.InvokeSheet]'s `MicOrb` doc): it sends `flush`
 * to force the server to finalize whatever's been said so far, rather
 * than discarding the recording — the same [onMessage]-driven completion
 * path handles both a VAD-triggered and a manually-forced finalization,
 * so there's only one place that ever completes [record]'s result.
 *
 * Deliberately a separate class from [AudioRecorder]/[SpeechToText],
 * which [com.saarthi.ui.AssistActivity]'s fixed-window capture still
 * uses: live streaming (no file, chunk-by-chunk over a socket the moment
 * each buffer is read) and record-then-upload are different enough
 * capture models that sharing one class would mean threading a
 * streaming-vs-file branch through every method.
 */
class SarvamStreamingStt {

    private val recordingDispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "SarvamSttStream") }.asCoroutineDispatcher()
    private val sessionScope = CoroutineScope(SupervisorJob() + recordingDispatcher)

    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var isStreaming = false
    private var recordingJob: Job? = null

    /**
     * Starts capturing and streaming immediately; suspends until the
     * server finalizes a transcript (VAD-detected end of speech, [stop]
     * was called, the connection failed, or [FINALIZE_TIMEOUT_MS]
     * elapses) and returns it.
     *
     * [onSpeechEnded] fires when the server's VAD detects the user has
     * stopped talking (`vad.speech_end`) — noticeably before the final
     * transcript itself arrives, so callers can flip a "still listening"
     * indicator to a "transcribing" one instead of holding it right up
     * to the result. Always invoked on [Dispatchers.Main], regardless of
     * which thread the WebSocket event arrived on.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun record(language: Language, onSpeechEnded: () -> Unit = {}): TranscriptionResult {
        val apiKey = BuildConfig.SARVAM_API_KEY
        if (apiKey.isBlank()) {
            return TranscriptionResult.Failed("Sarvam API key not configured (local.properties: sarvam.apiKey)")
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_ENCODING)
        if (minBufferSize <= 0) return TranscriptionResult.Failed("AudioRecord.getMinBufferSize failed on this device")

        val record = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_ENCODING, minBufferSize * 4)
        } catch (e: SecurityException) {
            return TranscriptionResult.Failed("RECORD_AUDIO not granted: ${e.message}")
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return TranscriptionResult.Failed("AudioRecord failed to initialize")
        }
        audioRecord = record

        val done = CompletableDeferred<TranscriptionResult>()

        val url = SARVAM_STT_WS_URL.toHttpUrl().newBuilder()
            .addQueryParameter("language_code", language.code)
            .addQueryParameter("model", SARVAM_STT_REALTIME_MODEL)
            .addQueryParameter("endpointing", "vad")
            .addQueryParameter("encoding", "linear16")
            .addQueryParameter("sample_rate", SAMPLE_RATE_HZ.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("API-SUBSCRIPTION-KEY", apiKey)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isStreaming = true
                record.startRecording()
                recordingJob = sessionScope.launch { streamMic(record, ws, minBufferSize) }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                when (json["event"]?.jsonPrimitive?.contentOrNull) {
                    "vad.speech_end" -> sessionScope.launch(Dispatchers.Main) { onSpeechEnded() }
                    "transcript.final" -> {
                        val transcript = json["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        done.complete(
                            if (transcript.isBlank()) {
                                TranscriptionResult.Failed("Sarvam returned an empty transcript")
                            } else {
                                TranscriptionResult.Success(transcript)
                            },
                        )
                    }
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                done.complete(TranscriptionResult.Failed("Connection closed before a transcript was finalized"))
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                log("✖ WebSocket failed: ${t.message}")
                done.complete(TranscriptionResult.Failed("Sarvam request failed: ${t.message}"))
            }
        }

        webSocket = SaarthiHttp.client.newWebSocket(request, listener)

        // finally, not a sequential call after — if the calling coroutine
        // is cancelled (e.g. the invoke sheet is dismissed mid-recording)
        // while suspended on done.await(), a plain statement after this
        // block would never run, leaking the AudioRecord/WebSocket to
        // keep running in the background indefinitely. teardown() itself
        // is fully synchronous (no suspending calls), so it's safe to run
        // here even during cancellation.
        return try {
            withTimeoutOrNull(FINALIZE_TIMEOUT_MS) { done.await() }
                ?: TranscriptionResult.Failed("Timed out waiting for a transcript")
        } finally {
            teardown()
        }
    }

    /**
     * Manual early stop — e.g. the user tapped the mic again instead of
     * waiting for VAD. Forces the server to finalize whatever's been said
     * so far rather than discarding it. No-op if nothing is streaming.
     */
    fun stop() {
        if (!isStreaming) return
        isStreaming = false
        webSocket?.send("""{"event":"flush"}""")
    }

    private fun streamMic(record: AudioRecord, ws: WebSocket, minBufferSize: Int) {
        val buffer = ByteArray(minBufferSize)
        while (isStreaming) {
            val read = record.read(buffer, 0, buffer.size)
            if (read > 0) {
                val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                val message = buildJsonObject {
                    put("event", "audio_input")
                    put("audio", Base64.encodeToString(chunk, Base64.NO_WRAP))
                }
                ws.send(message.toString())
            }
        }
    }

    private fun teardown() {
        isStreaming = false
        webSocket?.close(1000, null)
        webSocket = null
        audioRecord?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioRecord = null
        recordingJob?.cancel()
        recordingJob = null
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "[SarvamStreamingStt] $message")
    }
}
