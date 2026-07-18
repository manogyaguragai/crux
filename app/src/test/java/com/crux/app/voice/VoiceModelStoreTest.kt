package com.crux.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** On-device model presence: a model is only "ready" when all three files exist and are non-empty. */
class VoiceModelStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun store() = VoiceModelStore(tmp.root)

    private fun writeModel(store: VoiceModelStore, model: VoiceModel, empty: Boolean = false) {
        val dir = store.dirFor(model).apply { mkdirs() }
        model.files.forEach { name -> File(dir, name).writeText(if (empty) "" else "data") }
    }

    @Test fun `nothing downloaded means not ready and none installed`() {
        val s = store()
        assertFalse(s.isReady(VoiceModel.LIGHT))
        assertNull(s.installed())
    }

    @Test fun `all three files present and non-empty is ready`() {
        val s = store()
        writeModel(s, VoiceModel.LIGHT)
        assertTrue(s.isReady(VoiceModel.LIGHT))
        assertEquals(VoiceModel.LIGHT, s.installed())
    }

    @Test fun `an empty file does not count as ready`() {
        val s = store()
        writeModel(s, VoiceModel.LIGHT, empty = true)
        assertFalse(s.isReady(VoiceModel.LIGHT)) // a half-written partial must not read as installed
    }

    @Test fun `a missing decoder is not ready`() {
        val s = store()
        val dir = s.dirFor(VoiceModel.LIGHT).apply { mkdirs() }
        File(dir, VoiceModel.LIGHT.encoderFile).writeText("data")
        File(dir, VoiceModel.LIGHT.tokensFile).writeText("data")
        assertFalse(s.isReady(VoiceModel.LIGHT))
    }

    @Test fun `installed prefers the heavier model when both are present`() {
        val s = store()
        writeModel(s, VoiceModel.LIGHT)
        writeModel(s, VoiceModel.CAPABLE)
        assertEquals(VoiceModel.CAPABLE, s.installed())
    }

    @Test fun `remove deletes a model's files`() {
        val s = store()
        writeModel(s, VoiceModel.CAPABLE)
        assertTrue(s.isReady(VoiceModel.CAPABLE))
        s.remove(VoiceModel.CAPABLE)
        assertFalse(s.isReady(VoiceModel.CAPABLE))
        assertNull(s.installed())
    }

    @Test fun `paths are absolute and under the model's own dir`() {
        val s = store()
        val p = s.pathsFor(VoiceModel.LIGHT)
        assertTrue(File(p.encoder).isAbsolute)
        assertTrue(p.encoder.endsWith("voice/tiny/tiny-encoder.int8.onnx"))
        assertTrue(p.decoder.endsWith("voice/tiny/tiny-decoder.int8.onnx"))
        assertTrue(p.tokens.endsWith("voice/tiny/tiny-tokens.txt"))
    }

    @Test fun `clearAll wipes every model at once`() {
        val s = store()
        writeModel(s, VoiceModel.LIGHT)
        writeModel(s, VoiceModel.CAPABLE)
        s.clearAll()
        assertNull(s.installed())
        assertFalse(s.isReady(VoiceModel.LIGHT))
        assertFalse(s.isReady(VoiceModel.CAPABLE))
    }
}
