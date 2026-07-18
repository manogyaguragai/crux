package com.crux.app.voice

/**
 * The on-device Whisper models the user can choose between (phase 4 voice). All are the MULTILINGUAL
 * Whisper exports (not the English-only ".en" builds): the multilingual models were trained on ~99
 * languages, so they recognise non-English proper nouns and place names (e.g. "Bhaktapur") that the
 * ".en" models mangle — at the same download size. They run in English mode by default (Latin-script
 * output) while still knowing those names; a full non-English dictation mode would set the recognizer's
 * language, a later toggle.
 *
 * [LIGHT] and [CAPABLE] are the first-run choices; [HIGH] is a larger, more capable tier offered in
 * settings for those who want the best on-device accuracy and have the space. [approxMb] is the rounded
 * sum of the three int8 files, shown so the user knows the one-time download size before committing.
 * Copy for the first two stays calm/positive ("lightweight"/"capable", never "less/more accurate");
 * [HIGH] is the explicit opt-in "high accuracy" tier the owner asked for.
 */
enum class VoiceModel(
    val id: String,           // on-device subdir name + file prefix (e.g. "tiny")
    private val repo: String, // huggingface repo hosting the sherpa-onnx multilingual int8 export
    val approxMb: Int,
) {
    LIGHT("tiny", "csukuangfj/sherpa-onnx-whisper-tiny", 100),
    CAPABLE("base", "csukuangfj/sherpa-onnx-whisper-base", 150),
    HIGH("small", "csukuangfj/sherpa-onnx-whisper-small", 360);

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
