package com.crux.app.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Records 16 kHz mono PCM from the mic while [record] runs, returning the samples as normalized floats
 * [-1, 1] — the shape sherpa-onnx / Whisper wants. [record] loops until [stop] is called (the
 * hold-to-talk press is released), then stops the mic and returns everything captured; [stop] is used
 * rather than coroutine cancellation precisely so the samples can be returned. Chunks are copied out of
 * the read buffer as exact-size arrays and flattened at the end, so there is no per-sample boxing.
 */
class AudioRecorder {

    @Volatile private var active = false

    /** Signal the in-progress [record] to finish and return its samples. */
    fun stop() { active = false }

    @SuppressLint("MissingPermission") // the caller gates on RECORD_AUDIO before starting
    suspend fun record(): FloatArray = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val bufSize = maxOf(minBuf, SAMPLE_RATE) // ~1s of headroom
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, bufSize)
        val chunks = ArrayList<ShortArray>()
        var totalLen = 0
        active = true
        try {
            recorder.startRecording()
            val buf = ShortArray(bufSize)
            // stop() ends the take; coroutineContext.isActive covers cancellation (VM cleared mid-take).
            while (active && coroutineContext.isActive) {
                val n = recorder.read(buf, 0, buf.size)
                if (n > 0) {
                    chunks.add(buf.copyOf(n))
                    totalLen += n
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        val out = FloatArray(totalLen)
        var i = 0
        for (chunk in chunks) for (s in chunk) out[i++] = s / 32768f
        out
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
