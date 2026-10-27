package com.sereda.translator

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import java.io.File
import java.util.concurrent.CancellationException

class TranslationTest {
    @Test fun everyModeSupportsExactlyEnglishAndSpanish() {
        val languages = listOf(Language.ENGLISH, Language.SPANISH)
        assertEquals(languages, Language.entries.toList())
        for (mode in TranslationMode.entries) {
            assertEquals(languages, mode.sources())
            assertEquals(languages, mode.targets())
            for (source in languages) for (target in languages) assertTrue(mode.accepts(source, target))
            assertFalse(mode.accepts(null, Language.ENGLISH))
        }
    }

    @Test fun bothDirectionsUseDirectModels() {
        for (source in Language.entries) for (target in Language.entries) {
            val route = TranslationRoutes.pairs(source, target)
            if (source == target) assertTrue(route.isEmpty())
            else {
                assertTrue(route.first().startsWith(source.code + "-"))
                assertTrue(route.last().endsWith("-" + target.code))
                assertEquals(1, route.size)
            }
        }
    }

    @Test fun staleRequestNeverContinuesToTranslation() {
        var cancelled = false
        var translated = false
        val engine = object : TranslationEngine {
            override fun translate(text: String, source: Language, target: Language, cancelled: () -> Boolean): String {
                translated = true; return "hola"
            }
        }
        val recognition = object : RecognitionEngine {
            override fun recognize(samples: FloatArray, source: Language, cancelled: () -> Boolean): String {
                return "hello".also { cancelledFlag() }
            }
            fun cancelledFlag() { cancelled = true }
        }
        val synthesis = object : SynthesisEngine {
            override fun synthesize(text: String, target: Language, cancelled: () -> Boolean) = SpeechAudio(floatArrayOf(1f), 16000)
        }
        val pipeline = TranslationPipeline(engine, recognition, synthesis)
        assertThrows(CancellationException::class.java) { pipeline.transcribe(floatArrayOf(1f), Language.ENGLISH) { cancelled } }
        assertFalse(translated)
    }

    @Test fun sameLanguageSkipsTranslationButAllowsAudio() {
        var speechText = ""
        val pipeline = TranslationPipeline(object : TranslationEngine {
            override fun translate(text: String, source: Language, target: Language, cancelled: () -> Boolean): String = error("Should bypass translation")
        }, object : RecognitionEngine {
            override fun recognize(samples: FloatArray, source: Language, cancelled: () -> Boolean) = "Hello"
        }, object : SynthesisEngine {
            override fun synthesize(text: String, target: Language, cancelled: () -> Boolean): SpeechAudio {
                speechText = text; return SpeechAudio(floatArrayOf(1f), 16000)
            }
        })
        val text = pipeline.transcribe(floatArrayOf(1f), Language.ENGLISH) { false }
        val result = pipeline.translate(text, Language.ENGLISH, Language.ENGLISH) { false }
        pipeline.speak(result, Language.ENGLISH) { false }
        assertEquals("Hello", speechText)
        assertThrows(IllegalArgumentException::class.java) { pipeline.translate("  ", Language.ENGLISH, Language.SPANISH) { false } }
    }

    @Test fun sourceTokenizerMatchesOfficialSentencePieceFixtures() {
        val fixture = javaClass.classLoader!!.getResourceAsStream("tokenizer-reference.json")!!.bufferedReader().use { it.readText() }
        val array = JSONArray(fixture)
        val tokenizers = mutableMapOf<String, MarianTokenizer>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val pair = item.getString("pair")
            val tokenizer = tokenizers.getOrPut(pair) { MarianTokenizer(File("src/main/assets/models/translation/$pair")) }
            val expected = item.getJSONArray("ids")
            assertArrayEquals("$pair: ${item.getString("text")}", LongArray(expected.length()) { expected.getLong(it) }, tokenizer.encode(item.getString("text")))
        }
    }

    @Test fun readinessDoesNotDisableUnrelatedTextModes() {
        val readiness = ResourceReadiness(null, "Recognition unavailable", "Voices unavailable")
        assertNull(readiness.error(TranslationMode.TEXT_TEXT))
        assertEquals("Recognition unavailable", readiness.error(TranslationMode.SPEECH_TEXT))
        assertEquals("Voices unavailable", readiness.error(TranslationMode.TEXT_SPEECH))
    }
}
