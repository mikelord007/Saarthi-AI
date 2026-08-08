package com.saarthi.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import com.saarthi.BuildConfig
import com.saarthi.net.SaarthiHttp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
// "https", not "wss" — OkHttp's HttpUrl.toHttpUrl() (used below to add
// query params) only accepts http/https; the ws/wss -> http/https
// translation only happens inside Request.Builder.url(String), which
// this bypasses. https is fine here regardless: OkHttp negotiates the
// WebSocket upgrade over whatever transport the scheme implies (TLS,
// same as wss would be), the scheme label doesn't change the wire
// protocol once the Upgrade handshake happens.
private const val SARVAM_TTS_WS_URL = "https://api.sarvam.ai/text-to-speech/ws"

/** Latest TTS model as of writing — see [com.saarthi.speech.SpeechToText]'s own `SARVAM_MODEL` doc for the matching STT pick. */
private const val SARVAM_TTS_MODEL = "bulbul:v3"

// bulbul:v3's own default voice. The app's existing Speaker picker
// (meera/arvind/pavithra, see Speaker.kt) is unrelated UI-preference
// metadata for v1 rather than a real voice selection — kept behind one
// constant, same as MayaTts's MAYA_VOICE was, so wiring a real Speaker ->
// Sarvam-voice map later is a one-file change.
private const val SARVAM_VOICE = "shubh"

private const val SAMPLE_RATE_HZ = 24000
private const val BYTES_PER_SAMPLE = 2 // 16-bit mono PCM

/** Safety net if the server never sends a completion event and the socket doesn't close on its own — see [streamAndPlay]. */
private const val STREAM_TIMEOUT_MS = 20_000L

/**
 * Narration playback via Sarvam's `bulbul` TTS streaming WebSocket,
 * standing in for [MayaTts] until Maya API credits come through.
 *
 * Uses the WebSocket endpoint, not the one-off REST `/text-to-speech`
 * POST: the REST endpoint only returns audio once the *entire* clip has
 * been synthesized server-side, so nothing plays until that full
 * round-trip completes. The WebSocket streams `linear16` chunks as
 * they're generated, so playback (via a streaming [AudioTrack] in
 * [AudioTrack.MODE_STREAM]) starts on the first chunk instead of
 * waiting for the whole narration line — this is what actually closes
 * the "action happened, narration is still silent" gap, not a faster
 * model.
 *
 * One fresh connection per [speak] call, not a session-long persistent
 * one: narration lines are short and sporadic here, not a continuous
 * dialogue, so the added complexity of reconnect-on-drop/keepalive/
 * mid-session reconfiguration isn't worth it for the connection-reuse
 * savings — a WS handshake is still far cheaper than today's
 * multi-second full-generation wait.
 *
 * [VoiceSettings.language]'s own `code` (e.g. "hi-IN") is already in
 * Sarvam's `language_code` format — no mapping layer needed here,
 * unlike Maya's `MayaLanguage`. [VoiceSettings.pace] is wired straight
 * into Sarvam's `pace` field, which Maya's request body never
 * supported.
 */
object SarvamTts {

    private val mutex = Mutex()

    suspend fun speak(text: String, settings: VoiceSettings) {
        if (text.isBlank()) return
        mutex.withLock {
            streamAndPlay(text, settings)
        }
    }

    /**
     * Opens the WS connection, sends config+text+flush, decodes each
     * `audio` message's base64 chunk into [chunks] as it arrives, and
     * feeds [play] concurrently so playback starts on the first chunk
     * rather than after the whole stream finishes. Waits for the
     * server's completion event (or connection close/failure, or
     * [STREAM_TIMEOUT_MS] as a last resort) before tearing down —
     * always via the same three paths, so a dropped connection can
     * never hang [speak] forever.
     */
    private suspend fun streamAndPlay(text: String, settings: VoiceSettings) = coroutineScope {
        val apiKey = BuildConfig.SARVAM_API_KEY
        if (apiKey.isBlank()) return@coroutineScope

        val chunks = Channel<ByteArray>(Channel.UNLIMITED)
        val finished = CompletableDeferred<Unit>()

        val url = SARVAM_TTS_WS_URL.toHttpUrl().newBuilder()
            .addQueryParameter("model", SARVAM_TTS_MODEL)
            .addQueryParameter("send_completion_event", "true")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Api-Subscription-Key", apiKey)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(configMessage(settings).toString())
                webSocket.send(textMessage(text).toString())
                webSocket.send("""{"type":"flush"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                when (json["type"]?.jsonPrimitive?.contentOrNull) {
                    "audio" -> decodeAudioChunk(json)?.let { chunks.trySend(it) }
                    "event" -> finished.complete(Unit)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                finished.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log("✖ WebSocket failed: ${t.message}")
                finished.complete(Unit)
            }
        }

        val webSocket = SaarthiHttp.client.newWebSocket(request, listener)
        val playbackJob = launch(Dispatchers.IO) { play(chunks) }

        withTimeoutOrNull(STREAM_TIMEOUT_MS) { finished.await() }
        chunks.close()
        webSocket.close(1000, null)
        playbackJob.join()
    }

    private fun decodeAudioChunk(message: JsonObject): ByteArray? {
        val base64Audio = message["data"]?.jsonObject?.get("audio")?.jsonPrimitive?.contentOrNull ?: return null
        return runCatching { Base64.decode(base64Audio, Base64.DEFAULT) }.getOrNull()
    }

    private fun configMessage(settings: VoiceSettings) = buildJsonObject {
        put("type", "config")
        put(
            "data",
            buildJsonObject {
                put("language_code", settings.language.code)
                put("speaker", SARVAM_VOICE)
                put("model", SARVAM_TTS_MODEL)
                put("pace", settings.pace)
                put("speech_sample_rate", SAMPLE_RATE_HZ)
                put("output_audio_codec", "linear16")
            },
        )
    }

    private fun textMessage(text: String) = buildJsonObject {
        put("type", "text")
        put("data", buildJsonObject { put("text", text) })
    }

    /**
     * Consumes decoded PCM chunks as they arrive and writes them into a
     * streaming [AudioTrack] — built lazily on the first real chunk, so a
     * connection that fails before producing any audio never allocates
     * one. [AudioTrack.write] (blocking mode) paces itself against
     * playback, but only for data already queued — after the channel
     * closes, the loop waits out whatever's left of the total duration
     * before stop()/release() so the tail doesn't get cut short.
     */
    private suspend fun play(chunks: ReceiveChannel<ByteArray>) {
        var audioTrack: AudioTrack? = null
        var totalBytesWritten = 0L
        var playbackStartMs = 0L
        try {
            for (chunk in chunks) {
                if (chunk.isEmpty()) continue
                val track = audioTrack ?: buildStreamingAudioTrack()?.also {
                    audioTrack = it
                    it.play()
                    playbackStartMs = System.currentTimeMillis()
                } ?: continue
                track.write(chunk, 0, chunk.size)
                totalBytesWritten += chunk.size
            }
            if (audioTrack != null) {
                val totalDurationMs = totalBytesWritten / BYTES_PER_SAMPLE * 1000L / SAMPLE_RATE_HZ
                val remainingMs = totalDurationMs - (System.currentTimeMillis() - playbackStartMs)
                if (remainingMs > 0) delay(remainingMs)
            }
        } finally {
            audioTrack?.let {
                runCatching { it.stop() }
                it.release()
            }
        }
    }

    private fun buildStreamingAudioTrack(): AudioTrack? {
        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE_HZ, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufferSize <= 0) return null
        return AudioTrack.Builder()
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
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /** Debug-build-only — mirrors [com.saarthi.agent.AgentLoop]'s own logging convention under the same tag. */
    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "[SarvamTts] $message")
    }
}
