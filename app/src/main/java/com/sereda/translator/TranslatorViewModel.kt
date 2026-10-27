package com.sereda.translator

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

data class TranslatorState(
    val mode: TranslationMode = TranslationMode.TEXT_TEXT,
    val source: Language? = Language.ENGLISH,
    val target: Language? = Language.SPANISH,
    val input: String = "", val result: String = "",
    val readiness: ResourceReadiness = ResourceReadiness(),
    val initializing: Boolean = true, val busy: Boolean = false,
    val recording: Boolean = false, val playing: Boolean = false,
    val progress: String = "Preparing offline resources…", val error: String? = null,
    val audioError: String? = null, val responseMillis: Long? = null
) {
    val selectionValid get() = mode.accepts(source, target)
    val available get() = !initializing && selectionValid && readiness.error(mode) == null
}

class TranslatorViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(TranslatorState())
    val state = mutableState.asStateFlow()
    private val resources = BundledResources(application)
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val speech = OfflineSpeech(resources)
    private val pipeline = TranslationPipeline(OnnxTranslationEngine(resources), speech, speech)
    private val microphone = MicrophoneRecorder(application)
    private val player = LocalAudioPlayer(application)
    private val generation = AtomicLong()
    private var work: Job? = null
    private var initialization: Job? = null
    private var closed = false
    private var lastAudio: SpeechAudio? = null
    private val recordingStoppedAt = AtomicLong()

    init { initialize() }

    fun ensureResources() {
        if (!state.value.initializing && resources.needsPreparation()) initialize()
    }

    fun initialize() {
        if (initialization?.isActive == true) return
        cancel()
        mutableState.update { it.copy(initializing = true, error = null) }
        initialization = viewModelScope.launch {
            try {
                val readiness = withContext(dispatcher) {
                    resources.initialize({ message -> mutableState.update { it.copy(progress = message) } }, { closed })
                }
                mutableState.update { it.copy(readiness = readiness, initializing = false, progress = "Ready offline") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutableState.update { it.copy(initializing = false, error = e.message ?: "Resource initialization failed. Retry or reinstall.") }
            }
        }
    }

    fun mode(mode: TranslationMode) {
        invalidate()
        mutableState.update { it.copy(mode = mode,
            source = it.source?.takeIf { language -> language in mode.sources() },
            target = it.target?.takeIf { language -> language in mode.targets() }) }
    }
    fun source(language: Language) { invalidate(); mutableState.update { it.copy(source = language) } }
    fun target(language: Language) { invalidate(); mutableState.update { it.copy(target = language) } }
    fun input(text: String) { invalidate(); mutableState.update { it.copy(input = text) } }
    fun permissionDenied() { mutableState.update { it.copy(error = "Microphone permission is required for speech input. You can still use text modes.") } }

    private fun invalidate() {
        cancel()
        lastAudio = null
        mutableState.update { it.copy(result = "", error = null, audioError = null, responseMillis = null) }
    }

    fun cancel() {
        generation.incrementAndGet()
        work?.cancel()
        microphone.stop(true)
        player.stop()
        mutableState.update { it.copy(busy = false, recording = false, playing = false, progress = "") }
    }

    fun startRecording() {
        ensureResources()
        val selection = state.value
        if (!selection.available || !selection.mode.speechInput) return
        cancel()
        val id = generation.get()
        recordingStoppedAt.set(0)
        mutableState.update { it.copy(recording = true, error = null, audioError = null, progress = "Listening… Tap Stop when finished (up to 60 seconds).") }
        try {
            microphone.start { outcome ->
                val stoppedAt = recordingStoppedAt.get().takeIf { it > 0 } ?: SystemClock.elapsedRealtime()
                viewModelScope.launch {
                    if (generation.get() != id) return@launch
                    mutableState.update { it.copy(recording = false) }
                    outcome.fold({ samples -> process(samples, stoppedAt) }, { error ->
                        mutableState.update { it.copy(error = error.message, progress = "") }
                    })
                }
            }
        } catch (e: Exception) { mutableState.update { it.copy(recording = false, error = e.message, progress = "") } }
    }

    fun stopRecording() { recordingStoppedAt.set(SystemClock.elapsedRealtime()); microphone.stop() }
    fun translate() { process(null, SystemClock.elapsedRealtime()) }

    private fun process(samples: FloatArray?, started: Long) {
        ensureResources()
        val selection = state.value
        if (!selection.available) return
        if (samples == null && selection.input.isBlank()) return
        cancel()
        val id = generation.get()
        val cancelled = { closed || generation.get() != id }
        lastAudio = null
        mutableState.update { it.copy(busy = true, result = "", error = null, audioError = null, responseMillis = null) }
        work = viewModelScope.launch {
            try {
                val text = if (samples != null) {
                    mutableState.update { it.copy(progress = "Recognizing ${selection.source!!.label}…") }
                    withContext(dispatcher) { pipeline.transcribe(samples, selection.source!!, cancelled) }.also { transcript ->
                        if (!cancelled()) mutableState.update { it.copy(input = transcript) }
                    }
                } else selection.input
                mutableState.update { it.copy(progress = "Translating…") }
                val translated = withContext(dispatcher) { pipeline.translate(text, selection.source!!, selection.target!!, cancelled) }
                checkActive(cancelled)
                mutableState.update { it.copy(result = translated, responseMillis = SystemClock.elapsedRealtime() - started) }
                if (selection.mode.speechOutput) {
                    mutableState.update { it.copy(progress = "Preparing spoken result…") }
                    try {
                        val audio = withContext(dispatcher) { pipeline.speak(translated, selection.target!!, cancelled) }
                        checkActive(cancelled)
                        lastAudio = audio
                        play(audio, id, started)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { mutableState.update { it.copy(audioError = e.message ?: "Audio failed. Retry audio.") } }
                }
            } catch (_: CancellationException) { /* New selections own the state. */ }
            catch (e: Exception) { if (!cancelled()) mutableState.update { it.copy(error = e.message ?: "Translation failed. Retry.") } }
            finally { if (!cancelled()) mutableState.update { it.copy(busy = false, progress = "") } }
        }
    }

    fun replay() {
        ensureResources()
        val selection = state.value
        if (selection.result.isBlank() || !selection.mode.speechOutput || selection.busy || selection.recording) return
        cancel()
        val id = generation.get()
        lastAudio?.let { play(it, id); return }
        val cancelled = { closed || generation.get() != id }
        mutableState.update { it.copy(busy = true, audioError = null, progress = "Preparing spoken result…") }
        work = viewModelScope.launch {
            try {
                val audio = withContext(dispatcher) { pipeline.speak(selection.result, selection.target!!, cancelled) }
                checkActive(cancelled)
                lastAudio = audio
                play(audio, id)
            } catch (_: CancellationException) { }
            catch (e: Exception) { if (!cancelled()) mutableState.update { it.copy(audioError = e.message ?: "Audio failed. Retry.") } }
            finally { if (!cancelled()) mutableState.update { it.copy(busy = false, progress = "") } }
        }
    }

    private fun play(audio: SpeechAudio, id: Long, requestStarted: Long? = null) {
        player.play(audio, started = {
            if (requestStarted != null && generation.get() == id) {
                mutableState.update { it.copy(responseMillis = SystemClock.elapsedRealtime() - requestStarted) }
            }
        }) { playing, error ->
            if (generation.get() == id) mutableState.update { it.copy(playing = playing, audioError = error) }
        }
    }
    fun stopPlayback() { player.stop(); mutableState.update { it.copy(playing = false) } }
    fun leavingScreen() { cancel() }

    override fun onCleared() {
        closed = true
        cancel()
        microphone.close()
        player.close()
        dispatcher.close()
        super.onCleared()
    }
}
