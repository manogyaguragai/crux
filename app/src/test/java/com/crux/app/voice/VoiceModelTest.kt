package com.crux.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two Whisper model definitions: file naming and the HuggingFace download URLs. */
class VoiceModelTest {

    @Test fun `file names follow the id prefix`() {
        assertEquals("tiny.en-encoder.int8.onnx", VoiceModel.LIGHT.encoderFile)
        assertEquals("tiny.en-decoder.int8.onnx", VoiceModel.LIGHT.decoderFile)
        assertEquals("tiny.en-tokens.txt", VoiceModel.LIGHT.tokensFile)
        assertEquals("base.en-encoder.int8.onnx", VoiceModel.CAPABLE.encoderFile)
        assertEquals("base.en-decoder.int8.onnx", VoiceModel.CAPABLE.decoderFile)
    }

    @Test fun `files lists exactly the three required parts`() {
        assertEquals(
            listOf("base.en-encoder.int8.onnx", "base.en-decoder.int8.onnx", "base.en-tokens.txt"),
            VoiceModel.CAPABLE.files,
        )
    }

    @Test fun `urls point at the model's huggingface repo`() {
        assertEquals(
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-encoder.int8.onnx",
            VoiceModel.LIGHT.urlFor(VoiceModel.LIGHT.encoderFile),
        )
        assertEquals(
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base.en/resolve/main/base.en-tokens.txt",
            VoiceModel.CAPABLE.urlFor(VoiceModel.CAPABLE.tokensFile),
        )
    }

    @Test fun `the capable model is the heavier download`() {
        assertTrue(VoiceModel.CAPABLE.approxMb > VoiceModel.LIGHT.approxMb)
    }
}
