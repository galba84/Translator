package com.sereda.translator

enum class Language(val code: String, val label: String) {
    ENGLISH("en", "English"), SPANISH("es", "Español")
}

enum class TranslationMode(val label: String, val speechInput: Boolean, val speechOutput: Boolean) {
    TEXT_TEXT("Text → Text", false, false),
    SPEECH_TEXT("Speech → Text", true, false),
    TEXT_SPEECH("Text → Speech", false, true),
    SPEECH_SPEECH("Speech → Speech", true, true);

    fun sources() = Language.entries.toList()
    fun targets() = Language.entries.toList()
    fun accepts(source: Language?, target: Language?) = source in sources() && target in targets()
}

object TranslationRoutes {
    fun pairs(source: Language, target: Language): List<String> =
        if (source == target) emptyList() else listOf("${source.code}-${target.code}")
}

class SpeechAudio(val samples: FloatArray, val sampleRate: Int)

interface TranslationEngine {
    fun translate(text: String, source: Language, target: Language, cancelled: () -> Boolean): String
}
interface RecognitionEngine {
    fun recognize(samples: FloatArray, source: Language, cancelled: () -> Boolean): String
}
interface SynthesisEngine {
    fun synthesize(text: String, target: Language, cancelled: () -> Boolean): SpeechAudio
}

/** The caller owns cancellation; a stale request never proceeds to the next stage. */
class TranslationPipeline(
    private val translation: TranslationEngine,
    private val recognition: RecognitionEngine,
    private val synthesis: SynthesisEngine
) {
    fun transcribe(samples: FloatArray, source: Language, cancelled: () -> Boolean): String {
        checkActive(cancelled)
        return recognition.recognize(samples, source, cancelled).also {
            checkActive(cancelled)
            require(it.isNotBlank()) { "No speech recognized. Try recording again." }
        }
    }

    fun translate(text: String, source: Language, target: Language, cancelled: () -> Boolean): String {
        require(text.isNotBlank()) { "Enter text or record a phrase first." }
        checkActive(cancelled)
        val result = if (source == target) text else translation.translate(text, source, target, cancelled)
        checkActive(cancelled)
        require(result.isNotBlank()) { "Translation returned no text. Please retry." }
        return result
    }

    fun speak(text: String, target: Language, cancelled: () -> Boolean): SpeechAudio {
        checkActive(cancelled)
        return synthesis.synthesize(text, target, cancelled).also { checkActive(cancelled) }
    }
}

fun checkActive(cancelled: () -> Boolean) {
    if (cancelled()) throw java.util.concurrent.CancellationException()
}
