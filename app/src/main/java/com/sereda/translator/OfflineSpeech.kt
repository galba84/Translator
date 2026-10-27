package com.sereda.translator

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

class OfflineSpeech(private val resources: BundledResources) : RecognitionEngine, SynthesisEngine {
    override fun recognize(samples: FloatArray, source: Language, cancelled: () -> Boolean): String {
        checkActive(cancelled)
        val directory = resources.file("sherpa-onnx-whisper-base")
        val config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = directory.resolve("base-encoder.int8.onnx").path,
                decoder = directory.resolve("base-decoder.int8.onnx").path,
                language = source.code, task = "transcribe"
            ), tokens = directory.resolve("base-tokens.txt").path, numThreads = 4, provider = "cpu"
        ))
        val recognizer = OfflineRecognizer(config = config)
        try {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples, 16000)
                recognizer.decode(stream)
                checkActive(cancelled)
                return recognizer.getResult(stream).text.trim()
            } finally { stream.release() }
        } finally { recognizer.release() }
    }

    override fun synthesize(text: String, target: Language, cancelled: () -> Boolean): SpeechAudio {
        checkActive(cancelled)
        val voice = if (target == Language.ENGLISH) "en_US-lessac-medium" else "es_ES-sharvard-medium"
        val directory = resources.file("vits-piper-$voice")
        val tts = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(
                model = directory.resolve("$voice.onnx").path,
                tokens = directory.resolve("tokens.txt").path,
                dataDir = directory.resolve("espeak-ng-data").path
            ), numThreads = 4, provider = "cpu"
        ), maxNumSentences = 1))
        try {
            val generated = tts.generate(text, 0, 1.0f)
            checkActive(cancelled)
            require(generated.samples.isNotEmpty()) { "Speech generation failed. Retry audio." }
            return SpeechAudio(generated.samples, generated.sampleRate)
        } finally { tts.release() }
    }
}
