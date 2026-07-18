package com.crux.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two Whisper model definitions: file naming and the HuggingFace download URLs. */
class VoiceModelTest {

    @Test fun `file names follow the id prefix`() {
        // multilingual models (not the ".en" builds), so proper nouns like place names survive.
        assertEquals("tiny-encoder.int8.onnx", VoiceModel.LIGHT.encoderFile)
        assertEquals("tiny-decoder.int8.onnx", VoiceModel.LIGHT.decoderFile)
        assertEquals("tiny-tokens.txt", VoiceModel.LIGHT.tokensFile)
        assertEquals("base-encoder.int8.onnx", VoiceModel.CAPABLE.encoderFile)
        assertEquals("small-decoder.int8.onnx", VoiceModel.HIGH.decoderFile)
    }

    @Test fun `files lists exactly the three required parts`() {
        assertEquals(
            listOf("base-encoder.int8.onnx", "base-decoder.int8.onnx", "base-tokens.txt"),
            VoiceModel.CAPABLE.files,
        )
    }

    @Test fun `urls point at the model's multilingual huggingface repo`() {
        assertEquals(
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main/tiny-encoder.int8.onnx",
            VoiceModel.LIGHT.urlFor(VoiceModel.LIGHT.encoderFile),
        )
        assertEquals(
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/resolve/main/small-tokens.txt",
            VoiceModel.HIGH.urlFor(VoiceModel.HIGH.tokensFile),
        )
    }

    @Test fun `the tiers grow in size, light to capable to high`() {
        assertTrue(VoiceModel.LIGHT.approxMb < VoiceModel.CAPABLE.approxMb)
        assertTrue(VoiceModel.CAPABLE.approxMb < VoiceModel.HIGH.approxMb)
    }
}
