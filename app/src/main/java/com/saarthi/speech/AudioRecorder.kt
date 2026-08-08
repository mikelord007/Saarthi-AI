package com.saarthi.speech

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

private const val SAMPLE_RATE_HZ = 16000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
private const val WAV_HEADER_SIZE = 44
private const val CHANNEL_COUNT = 1
private const val BITS_PER_SAMPLE = 16

/**
 * Records the user's spoken task to a WAV file in [Context.cacheDir] for
 * [SpeechToText] to upload. `AudioRecord`, not `MediaRecorder` — Sarvam
 * wants PCM-ish audio and `MediaRecorder`'s default encoders (AAC/AMR)
 * would need re-encoding; `AudioRecord` gives raw PCM directly, at the
 * cost of writing the WAV header by hand — `AudioRecord`'s output is
 * headerless, and a wrong or missing header is the classic cause of a
 * provider silently returning an empty transcript instead of an error.
 */
class AudioRecorder(private val context: Context) {

    private val recordingDispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "AudioRecorder") }.asCoroutineDispatcher()
    private val recordingScope = CoroutineScope(SupervisorJob() + recordingDispatcher)

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var outputFile: File? = null

    @Volatile
    private var isRecording = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_ENCODING)
        check(minBufferSize > 0) { "AudioRecord.getMinBufferSize failed on this device" }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            AUDIO_ENCODING,
            minBufferSize * 4,
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialize" }

        val file = File(context.cacheDir, "saarthi_task_${System.currentTimeMillis()}.wav")
        outputFile = file
        audioRecord = record
        isRecording = true
        record.startRecording()

        recordingJob = recordingScope.launch { writeAudioData(record, file, minBufferSize) }
    }

    /** Stops recording and finalizes the WAV header. Returns the file, or null if nothing was recorded. */
    suspend fun stop(): File? {
        isRecording = false
        recordingJob?.join() // waits for the write loop to notice, close, and patch the header before we touch the record below
        recordingJob = null

        audioRecord?.let { record ->
            runCatching { record.stop() }
            record.release()
        }
        audioRecord = null

        return outputFile
    }

    private fun writeAudioData(record: AudioRecord, file: File, minBufferSize: Int) {
        val buffer = ByteArray(minBufferSize)
        var totalBytes = 0L

        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.write(ByteArray(WAV_HEADER_SIZE)) // placeholder, patched once the real size is known

            while (isRecording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    raf.write(buffer, 0, read)
                    totalBytes += read
                }
            }

            writeWavHeader(raf, totalBytes)
        }
    }

    private fun writeWavHeader(raf: RandomAccessFile, pcmDataSize: Long) {
        val byteRate = SAMPLE_RATE_HZ * CHANNEL_COUNT * (BITS_PER_SAMPLE / 8)
        val blockAlign = CHANNEL_COUNT * (BITS_PER_SAMPLE / 8)
        val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36 + pcmDataSize).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // fmt chunk size
            putShort(1) // PCM
            putShort(CHANNEL_COUNT.toShort())
            putInt(SAMPLE_RATE_HZ)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(BITS_PER_SAMPLE.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmDataSize.toInt())
        }
        raf.seek(0)
        raf.write(header.array())
    }
}
