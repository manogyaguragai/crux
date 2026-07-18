package com.crux.app.voice

import java.io.File

/** Absolute paths to a model's three files, for handing to sherpa-onnx. */
data class ModelPaths(val encoder: String, val decoder: String, val tokens: String)

/**
 * Where the downloaded Whisper models live on device and whether they are ready. Deliberately holds
 * no Android type (takes a plain [baseDir]) so the presence logic is JVM-unit-testable; the caller
 * passes context.filesDir. Each model gets its own subdir: <baseDir>/voice/<id>/.
 */
class VoiceModelStore(private val baseDir: File) {

    fun dirFor(model: VoiceModel): File = File(baseDir, "$VOICE/${model.id}")

    /** Ready only when all three files exist and are non-empty — a partial download is not ready. */
    fun isReady(model: VoiceModel): Boolean {
        val dir = dirFor(model)
        return model.files.all { val f = File(dir, it); f.isFile && f.length() > 0L }
    }

    /** The installed model, if any. The heavier model wins if both happen to be present. */
    fun installed(): VoiceModel? =
        VoiceModel.entries.sortedByDescending { it.approxMb }.firstOrNull { isReady(it) }

    /** A leftover ".part" means a download for this model was interrupted and can be resumed. */
    fun hasPartial(model: VoiceModel): Boolean =
        dirFor(model).listFiles()?.any { it.name.endsWith(".part") } == true

    fun pathsFor(model: VoiceModel): ModelPaths {
        val dir = dirFor(model)
        return ModelPaths(
            encoder = File(dir, model.encoderFile).absolutePath,
            decoder = File(dir, model.decoderFile).absolutePath,
            tokens = File(dir, model.tokensFile).absolutePath,
        )
    }

    /** Delete a model's files (settings "remove", or clearing a half-finished download). */
    fun remove(model: VoiceModel) {
        dirFor(model).deleteRecursively()
    }

    /** Delete every downloaded model (hard reset) — the whole voice dir, so orphans from an old
     *  model id are reclaimed too. */
    fun clearAll() {
        File(baseDir, VOICE).deleteRecursively()
    }

    private companion object {
        const val VOICE = "voice"
    }
}
