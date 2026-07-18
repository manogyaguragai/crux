package com.crux.app.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where hold-to-talk is in its life. The mic in the omnibar renders itself from this, and settings
 * reads the same to show model status — so one download's progress is visible from either place.
 */
sealed interface VoiceState {
    /** No model on device yet; a mic tap opens the one-time download chooser. */
    data object NeedsModel : VoiceState
    /** Pulling a model's files; [fraction] spans the whole set (0f..1f). */
    data class Downloading(val model: VoiceModel, val fraction: Float) : VoiceState
    /** Model present, warming the recognizer (loads the int8 decoder) — briefly, after a download. */
    data object Preparing : VoiceState
    /** Ready: press-and-hold the mic to record. */
    data object Ready : VoiceState
    /** Recording now; release to transcribe. */
    data object Listening : VoiceState
    /** Turning the recorded audio into text on-device. */
    data object Transcribing : VoiceState
    /** Something went wrong; [retry] is the model to re-attempt if a download failed (else null). */
    data class Failed(val message: String, val retry: VoiceModel?) : VoiceState
}

/**
 * Orchestrates on-device voice capture (phase 4): download a Whisper model once, then record → Whisper
 * → text on each hold. App-scoped (held in AppContainer) with its own [scope] so a large model download
 * survives navigating between home and settings; the omnibar and settings both observe [state]. The
 * transcript is emitted on [transcripts] for the omnibar to drop into its input — capture stays a
 * review-then-submit, never an auto-commit, so a misheard word costs one tap to fix.
 *
 * Recording is gated on RECORD_AUDIO by the caller; downloading needs no permission.
 */
class VoiceController(
    private val store: VoiceModelStore,
    private val scope: CoroutineScope,
) {
    private val recorder = AudioRecorder()
    private val downloader = ModelDownloader(store)

    @Volatile private var speech: SpeechService? = null
    @Volatile private var speechModel: VoiceModel? = null

    private val _state = MutableStateFlow<VoiceState>(
        if (store.installed() != null) VoiceState.Ready else VoiceState.NeedsModel,
    )
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _installed = MutableStateFlow(store.installed())
    val installed: StateFlow<VoiceModel?> = _installed.asStateFlow()

    private val _transcripts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val transcripts: SharedFlow<String> = _transcripts

    private var downloadJob: Job? = null
    private var recordJob: Job? = null

    /**
     * Download [model] (resumable), warm the recognizer, then go Ready. A no-op if one is running. When
     * [replaceOthers] is set (settings "switch to…"), the other model's files are deleted once the new
     * one is fully in — only after, so a failed switch never leaves the user with no model.
     */
    fun download(model: VoiceModel, replaceOthers: Boolean = false) {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            _state.value = VoiceState.Downloading(model, 0f)
            try {
                downloader.download(model) { p ->
                    _state.value = VoiceState.Downloading(model, p.fraction)
                }
                if (replaceOthers) VoiceModel.entries.filter { it != model }.forEach { store.remove(it) }
                _installed.value = store.installed()
                _state.value = VoiceState.Preparing
                withContext(Dispatchers.Default) { ensureSpeech(model) } // warm: first take is snappy
                _state.value = VoiceState.Ready
            } catch (e: CancellationException) {
                _state.value = if (store.installed() != null) VoiceState.Ready else VoiceState.NeedsModel
                throw e
            } catch (e: Exception) {
                // Leave the partial ".part" in place so a retry resumes rather than restarts.
                _state.value = VoiceState.Failed("download interrupted. tap to resume.", model)
            }
        }
    }

    /** Begin recording. Caller must hold RECORD_AUDIO. No-op unless Ready. */
    fun startListening() {
        if (_state.value != VoiceState.Ready) return
        val model = _installed.value ?: return
        _state.value = VoiceState.Listening
        recordJob = scope.launch {
            val samples = try {
                recorder.record()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = VoiceState.Ready
                return@launch
            }
            _state.value = VoiceState.Transcribing
            val text = try {
                ensureSpeech(model).transcribe(samples)
            } catch (e: Exception) {
                ""
            }
            _state.value = VoiceState.Ready
            if (text.isNotBlank()) _transcripts.emit(text)
        }
    }

    /** Release: stop the mic; the take then transcribes and its text lands on [transcripts]. */
    fun stopListening() {
        recorder.stop()
    }

    /** Settings "remove": delete a model's files and free the recognizer if it was the loaded one. */
    fun remove(model: VoiceModel) {
        if (speechModel == model) {
            speech?.close(); speech = null; speechModel = null
        }
        store.remove(model)
        _installed.value = store.installed()
        _state.value = if (_installed.value != null) VoiceState.Ready else VoiceState.NeedsModel
    }

    private fun ensureSpeech(model: VoiceModel): SpeechService {
        if (speechModel != model) {
            speech?.close(); speech = null
        }
        return speech ?: SpeechService(store.pathsFor(model)).also { speech = it; speechModel = model }
    }
}
