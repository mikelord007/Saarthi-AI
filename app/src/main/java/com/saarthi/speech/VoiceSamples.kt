package com.saarthi.speech

import android.content.Context
import android.media.MediaPlayer
import com.saarthi.R

/**
 * Per-voice preview clips for onboarding's Voice screen. These are bundled
 * recordings (`res/raw`), not a live Sarvam call — the sample line never
 * changes, so there's nothing to gain from fetching it on every tap.
 */
object VoiceSamples {

    private val RAW_RES_BY_SPEAKER_ID = mapOf(
        "shubh" to R.raw.voice_sample_shubh,
        "mani" to R.raw.voice_sample_mani,
        "priya" to R.raw.voice_sample_priya,
        "ishita" to R.raw.voice_sample_ishita,
    )

    private var player: MediaPlayer? = null

    /** Stops whatever preview is currently playing (if any) and starts [speaker]'s. */
    fun play(context: Context, speaker: Speaker) {
        val resId = RAW_RES_BY_SPEAKER_ID[speaker.id] ?: return
        player?.release()
        player = MediaPlayer.create(context.applicationContext, resId)?.apply {
            setOnCompletionListener { it.release() }
            start()
        }
    }
}
