package com.crux.app.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs Whisper transcription on recorded PCM through sherpa-onnx, fully on device. The recognizer is
 * heavy to build (it loads a ~85-130 MB int8 decoder), so it is created lazily for the given model
 * paths and reused across takes; [close] frees the native handle. Every sherpa call runs off the main
 * thread. assetManager is null because our model files live in filesDir as absolute paths, not in the
 * APK assets.
 */
class SpeechService(private val paths: ModelPaths) {

    @Volatile private var recognizer: OfflineRecognizer? = null

    private fun ensure(): OfflineRecognizer = recognizer ?: OfflineRecognizer(
        assetManager = null,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(encoder = paths.encoder, decoder = paths.decoder),
                tokens = paths.tokens,
                modelType = "whisper",
                numThreads = 2,
            ),
        ),
    ).also { recognizer = it }

    /** Transcribe 16 kHz mono float samples to text (trimmed). Blank if nothing intelligible. */
    suspend fun transcribe(samples: FloatArray): String = withContext(Dispatchers.Default) {
        if (samples.isEmpty()) return@withContext ""
        val rec = ensure()
        val stream = rec.createStream()
        stream.acceptWaveform(samples, 16_000)
        rec.decode(stream)
        val text = rec.getResult(stream).text
        stream.release()
        text.trim()
    }

    fun close() {
        recognizer?.release()
        recognizer = null
    }
}
