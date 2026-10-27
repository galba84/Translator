package com.sereda.translator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MicrophoneRecorder(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private class Session {
        val active = AtomicBoolean(true)
        val discard = AtomicBoolean(false)
        @Volatile var recorder: AudioRecord? = null
    }
    @Volatile private var session: Session? = null

    fun start(completed: (Result<FloatArray>) -> Unit) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Microphone permission is required for speech input. Typed translation remains available."
        }
        check(session?.active?.get() != true) { "Recording is already active." }
        val current = Session()
        session = current
        executor.execute {
            val outcome = runCatching {
                val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0) { "Microphone format is unavailable." }
                val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 8192))
                current.recorder = audio
                try {
                    check(audio.state == AudioRecord.STATE_INITIALIZED) { "Cannot open the microphone." }
                    val output = ArrayList<Float>(16000 * 10)
                    if (current.active.get()) audio.startRecording()
                    val chunk = ShortArray(2048)
                    while (current.active.get() && output.size < 16000 * 60) {
                        val n = audio.read(chunk, 0, chunk.size)
                        if (n < 0) { if (current.active.get()) error("Microphone capture failed.") else break }
                        for (index in 0 until n) output.add(chunk[index] / 32768.0f)
                    }
                    check(output.size >= 1600 && output.any { kotlin.math.abs(it) > 0.01f }) {
                        "No speech recorded. Check the microphone and try again."
                    }
                    output.toFloatArray()
                } finally {
                    runCatching { audio.stop() }
                    audio.release()
                    current.recorder = null
                }
            }
            current.active.set(false)
            if (!current.discard.get()) completed(outcome)
        }
    }

    fun stop(cancel: Boolean = false) {
        val current = session ?: return
        if (cancel) current.discard.set(true)
        current.active.set(false)
        runCatching { current.recorder?.stop() }
    }

    fun close() { stop(true); executor.shutdown() }
}

class LocalAudioPlayer(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong()
    @Volatile private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null

    fun play(audio: SpeechAudio, started: () -> Unit = {}, status: (Boolean, String?) -> Unit) {
        stop()
        val id = generation.get()
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                if (change < 0 && generation.get() == id) { stop(); status(false, null) }
            }.build()
        focus = request
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            status(false, "Audio is busy. Retry audio when the other app finishes.")
            return
        }
        status(true, null)
        executor.execute {
            var failure: String? = null
            try {
                if (generation.get() != id) return@execute
                val format = AudioFormat.Builder().setSampleRate(audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build()
                val output = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format)
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(8192,
                        AudioTrack.getMinBufferSize(audio.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT))).build()
                track = output
                try {
                    check(output.state == AudioTrack.STATE_INITIALIZED) { "Audio output is unavailable." }
                    if (generation.get() != id) return@execute
                    output.play()
                    var offset = 0
                    while (offset < audio.samples.size && generation.get() == id) {
                        val count = output.write(audio.samples, offset, minOf(2048, audio.samples.size - offset), AudioTrack.WRITE_BLOCKING)
                        check(count > 0) { "Audio playback failed." }
                        if (offset == 0 && generation.get() == id) started()
                        offset += count
                    }
                    while (generation.get() == id && output.playbackHeadPosition < audio.samples.size) Thread.sleep(20)
                } finally {
                    if (track === output) track = null
                    runCatching { output.stop() }
                    output.release()
                }
            } catch (e: Exception) { failure = e.message ?: "Audio playback failed. Retry audio." }
            finally {
                if (generation.get() == id) {
                    manager.abandonAudioFocusRequest(request)
                    status(false, failure)
                }
            }
        }
    }

    fun stop() {
        generation.incrementAndGet()
        runCatching { track?.pause(); track?.flush(); track?.stop() }
        focus?.let { manager.abandonAudioFocusRequest(it) }
        focus = null
    }
    fun close() { stop(); executor.shutdown() }
}
