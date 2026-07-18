package com.crux.app.voice

/**
 * The two on-device Whisper models the user chooses between at the first-download prompt (phase 4
 * voice). Both run fully offline through sherpa-onnx; the choice is a size/speed vs richness trade,
 * not on/off quality. Display copy uses the calm positive framing decided in DECISIONS.log —
 * "lightweight" and "capable", never "less/more accurate".
 *
 * [approxMb] is the rounded sum of the three int8 files (encoder + decoder + tokens), shown in the
 * prompt so the user knows the one-time download size before committing.
 */
enum class VoiceModel(
    val id: String,           // on-device subdir name + file prefix (e.g. "tiny.en")
    private val repo: String, // huggingface repo hosting the sherpa-onnx int8 export
    val approxMb: Int,
) {
    LIGHT("tiny.en", "csukuangfj/sherpa-onnx-whisper-tiny.en", 100),
    CAPABLE("base.en", "csukuangfj/sherpa-onnx-whisper-base.en", 150);

    val encoderFile: String get() = "$id-encoder.int8.onnx"
    val decoderFile: String get() = "$id-decoder.int8.onnx"
    val tokensFile: String get() = "$id-tokens.txt"

    /** The three files that must all be present on device for this model to run. */
    val files: List<String> get() = listOf(encoderFile, decoderFile, tokensFile)

    /** Direct HuggingFace download URL for one of this model's files. */
    fun urlFor(file: String): String = "$HF/$repo/resolve/main/$file"

    private companion object {
        const val HF = "https://huggingface.co"
    }
}
