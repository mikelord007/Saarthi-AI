package com.saarthi.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import com.saarthi.BuildConfig
import com.saarthi.net.SaarthiHttp
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private const val TAG = "SaarthiAgent"
private const val MAYA_TTS_WS_URL = "wss://tts.mayaresearch.ai/v1/tts/stream"

private const val SAMPLE_RATE_HZ = 24000
private const val BYTES_PER_SAMPLE = 2 // 16-bit mono PCM (pcm_s16le)

/** Safety net if the server never sends `end` and the socket doesn't close on its own — see [streamAndPlay]. */
private const val STREAM_TIMEOUT_MS = 20_000L

/**
 * Narration playback via Maya's `v1/tts/stream` WebSocket (protocol v2:
 * `start` -> `metadata` -> `text` (`continue=false` to flush/close the
 * turn) -> `audio`* -> `end`), not the one-off REST `/v1/tts` POST this
 * replaces — the REST endpoint only returns audio once the entire clip
 * has been synthesized server-side, so nothing plays until that full
 * round-trip completes. The WebSocket streams base64 PCM chunks as
 * they're generated, so playback (via a streaming [AudioTrack] in
 * [AudioTrack.MODE_STREAM]) starts on the first chunk instead of waiting
 * for the whole narration line.
 *
 * One fresh connection per [speak] call, not a session-long persistent
 * one: narration lines are short and sporadic here, not a continuous
 * dialogue, so reconnect-on-drop/keepalive/mid-session reconfiguration
 * isn't worth building for the connection-reuse savings.
 *
 * The server's `metadata` frame carries the real sample rate/channels/
 * encoding, but it's not parsed here — Maya's docs only ever describe
 * 24kHz mono `pcm_s16le`, matching the constants above, and the REST
 * endpoint this replaces made the same assumption.
 *
 * Doesn't support [VoiceSettings.pace] — Maya's `start` frame has no
 * pace-equivalent field, unlike Sarvam's.
 */
object MayaTts {

    private val mutex = Mutex()

    suspend fun speak(text: String, settings: VoiceSettings) {
        if (text.isBlank()) return
        mutex.withLock {
            streamAndPlay(text, settings)
        }
    }

    /**
     * Opens the WS connection, sends `start`+`text` (`continue=false`),
     * decodes each `audio` message's base64 chunk into [chunks] as it
     * arrives, and feeds [play] concurrently so playback starts on the
     * first chunk rather than after the whole stream finishes. Waits for
     * the server's `end` (or `cancelled`/`error`, connection close/
     * failure, or [STREAM_TIMEOUT_MS] as a last resort) before tearing
     * down — always via the same paths, so a dropped connection can
     * never hang [speak] forever.
     */
    private suspend fun streamAndPlay(text: String, settings: VoiceSettings) = coroutineScope {
        val apiKey = BuildConfig.MAYA_API_KEY
        if (apiKey.isBlank()) return@coroutineScope

        val chunks = Channel<ByteArray>(Channel.UNLIMITED)
        val finished = CompletableDeferred<Unit>()
        val contextId = UUID.randomUUID().toString()

        val request = Request.Builder()
            .url(MAYA_TTS_WS_URL)
            .header("Authorization", "Bearer $apiKey")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(startMessage(settings).toString())
                webSocket.send(textMessage(contextId, text).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                when (json["type"]?.jsonPrimitive?.contentOrNull) {
                    "audio" -> decodeAudioChunk(json)?.let { chunks.trySend(it) }
                    "end", "cancelled" -> finished.complete(Unit)
                    "error" -> {
                        log("✖ error frame: ${json["error"]?.jsonPrimitive?.contentOrNull}")
                        finished.complete(Unit)
                    }
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
        val base64Audio = message["audio"]?.jsonPrimitive?.contentOrNull ?: return null
        return runCatching { Base64.decode(base64Audio, Base64.DEFAULT) }.getOrNull()
    }

    private fun startMessage(settings: VoiceSettings) = buildJsonObject {
        put("type", "start")
        put("v2", true)
        put("voice", settings.speaker.id)
        put("language", MayaLanguage.mayaCodeFor(settings.language))
    }

    private fun textMessage(contextId: String, text: String) = buildJsonObject {
        put("type", "text")
        put("context_id", contextId)
        put("text", text)
        put("continue", false)
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
        if (BuildConfig.DEBUG) Log.d(TAG, "[MayaTts] $message")
    }
}
