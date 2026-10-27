package com.sereda.translator

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OfflineEngineTest {
    companion object {
        private lateinit var context: Context
        private lateinit var resources: BundledResources
        @JvmStatic @BeforeClass fun prepare() {
            context = InstrumentationRegistry.getInstrumentation().targetContext
            resources = BundledResources(context)
            val readiness = resources.initialize({}, { false })
            assertNull(readiness.translation)
            assertNull(readiness.recognition)
            assertNull(readiness.synthesis)
        }
    }

    @Test fun bothTranslationDirectionsMatchReference() {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val cases = JSONArray(testAssets.open("translation-reference.json").bufferedReader().use { it.readText() })
        val engine = OnnxTranslationEngine(resources)
        val timings = JSONArray()
        for (index in 0 until cases.length()) {
            val item = cases.getJSONObject(index)
            val source = Language.entries.first { it.code == item.getString("source") }
            val target = Language.entries.first { it.code == item.getString("target") }
            val started = SystemClock.elapsedRealtime()
            val translated = engine.translate(item.getString("text"), source, target) { false }
            val millis = SystemClock.elapsedRealtime() - started
            assertEquals("$source -> $target", item.getString("result"), translated)
            timings.put(JSONObject().put("source", source.code).put("target", target.code)
                .put("milliseconds", millis).put("result", translated))
        }
        val report = JSONObject().put("model", Build.MODEL).put("abis", Build.SUPPORTED_ABIS.joinToString())
            .put("android", Build.VERSION.RELEASE).put("translation", timings)
        File(context.getExternalFilesDir(null), "translation-benchmark.json").writeText(report.toString(2))
    }

    @Test fun bundledEnglishAndSpanishVoicesAndRecognitionWork() {
        val speech = OfflineSpeech(resources)
        val translation = OnnxTranslationEngine(resources)
        for ((language, text) in listOf(Language.ENGLISH to "Good morning. I need some water. Thank you very much.",
            Language.SPANISH to "Buenos días. Necesito agua. Muchas gracias.")) {
            val started = SystemClock.elapsedRealtime()
            val generated = speech.synthesize(text, language) { false }
            android.util.Log.i("OfflineEngineTest", "$language generated ${generated.samples.size} samples at ${generated.sampleRate} Hz")
            assertTrue("$language returned no audio", generated.samples.isNotEmpty())
            assertTrue(generated.samples.any { kotlin.math.abs(it) > 0.01f })
            // Resample the generated voice to the recognizer's 16 kHz PCM input.
            val samples = FloatArray((generated.samples.size * 16000L / generated.sampleRate).toInt()) { i ->
                val position = i * generated.sampleRate / 16000.0
                val from = position.toInt().coerceAtMost(generated.samples.lastIndex)
                val next = (from + 1).coerceAtMost(generated.samples.lastIndex)
                (generated.samples[from] * (1 - (position - from)) + generated.samples[next] * (position - from)).toFloat()
            }
            val transcript = speech.recognize(samples, language) { false }
            assertTrue("$language: $transcript", transcript.isNotBlank())
            assertTrue("$language: $transcript", if (language == Language.ENGLISH) transcript.lowercase().contains("water")
                else transcript.lowercase().contains("agua"))
            val target = if (language == Language.ENGLISH) Language.SPANISH else Language.ENGLISH
            val result = translation.translate(transcript, language, target) { false }
            assertTrue(result.isNotBlank())
            val output = speech.synthesize(result, target) { false }
            assertTrue(output.samples.isNotEmpty())
            android.util.Log.i("OfflineEngineTest", "$language round trip: $transcript -> $result in ${SystemClock.elapsedRealtime() - started} ms (includes fixture synthesis)")
        }
    }

    @Test fun damagedExtractedResourceIsRepairedOffline() {
        val configuration = resources.file("translation/en-es/config.json")
        val original = configuration.readBytes()
        configuration.writeText("damaged")
        val readiness = resources.initialize({}, { false })
        assertNull(readiness.translation)
        assertArrayEquals(original, configuration.readBytes())
    }

    @Test fun evictedCacheIsDetectedAndRestoredFromBundledAssets() {
        assertEquals(File(context.cacheDir, "offline-resources"), resources.root)
        val configuration = resources.file("translation/en-es/config.json")
        val original = configuration.readBytes()
        assertTrue(configuration.delete())
        assertTrue(resources.needsPreparation())
        assertNull(resources.initialize({}, { false }).translation)
        assertArrayEquals(original, configuration.readBytes())
        assertFalse(resources.needsPreparation())
    }

    @Test fun legacyModelCopiesAreRemovedWithoutTouchingOtherFiles() {
        val sentinel = File(context.noBackupFilesDir, "keep-storage-test.txt")
        val legacy = File(context.noBackupFilesDir, "offline-resources")
        sentinel.writeText("keep")
        legacy.mkdirs()
        File(legacy, "old-model-copy").writeText("obsolete")
        try {
            assertNull(resources.initialize({}, { false }).translation)
            assertFalse(legacy.exists())
            assertEquals("keep", sentinel.readText())
        } finally { sentinel.delete() }
    }

    @Test fun obsoleteModelCopiesArePrunedOnUpgrade() {
        val obsolete = resources.file("translation/obsolete-model")
        obsolete.mkdirs()
        File(obsolete, "weights.bin").writeText("obsolete")
        assertNull(resources.initialize({}, { false }).translation)
        assertFalse(obsolete.exists())
        assertEquals(setOf("en-es", "es-en"), resources.file("translation").listFiles()!!.map { it.name }.toSet())
    }

    @Test fun packagedLanguageResourcesAreEnglishAndSpanishOnly() {
        assertEquals(setOf("en-es", "es-en"), context.assets.list("models/translation")!!.toSet())
        for (voice in listOf("en_US-lessac-medium", "es_ES-sharvard-medium")) {
            val base = "models/vits-piper-$voice/espeak-ng-data"
            val dictionaries = context.assets.list(base)!!.filter { it.endsWith("_dict") }.toSet()
            assertEquals(setOf("en_dict", "es_dict"), dictionaries)
            fun checkLanguages(path: String) {
                for (name in context.assets.list(path).orEmpty()) {
                    val child = "$path/$name"
                    if (context.assets.list(child).orEmpty().isNotEmpty()) checkLanguages(child)
                    else assertTrue("Unexpected phonemizer language: $child", Regex("(?i)^(en|es)(-|$).*").matches(name))
                }
            }
            checkLanguages("$base/lang")
        }
    }

    @Test fun failedLocalPreparationKeepsSpeechReadinessAndAllowsRetry() {
        val configuration = resources.file("translation/en-es/config.json")
        val original = configuration.readBytes()
        check(configuration.delete())
        val directory = configuration.parentFile!!
        check(directory.setWritable(false, false))
        try {
            val readiness = resources.initialize({}, { false })
            assertNotNull(readiness.translation)
            assertNull(readiness.recognition)
            assertNull(readiness.synthesis)
        } finally {
            check(directory.setWritable(true, true))
            configuration.writeBytes(original)
        }
        assertNull(resources.initialize({}, { false }).translation)
    }
}
