package com.sereda.translator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkflowTest {
    @Test fun foregroundWorkflowHandlesModeChangesPlaybackAndRecordingCancellation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains(Manifest.permission.INTERNET))
        lateinit var model: TranslatorViewModel
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> model = ViewModelProvider(activity)[TranslatorViewModel::class.java] }
            await { !model.state.value.initializing }
            assertNull(model.state.value.readiness.translation)
            scenario.onActivity {
                model.target(Language.SPANISH)
                model.mode(TranslationMode.TEXT_SPEECH)
            }
            assertEquals(Language.SPANISH, model.state.value.target)
            assertTrue(model.state.value.selectionValid)
            scenario.onActivity {
                model.target(Language.SPANISH)
                model.input("Good morning. Thank you very much.")
                model.translate()
            }
            await { model.state.value.playing || model.state.value.error != null || model.state.value.audioError != null }
            assertNull(model.state.value.error)
            assertNull(model.state.value.audioError)
            val translated = model.state.value.result
            assertTrue(translated.isNotBlank())
            scenario.onActivity { model.stopPlayback() }
            assertFalse(model.state.value.playing)
            assertEquals(translated, model.state.value.result)
            await { !model.state.value.busy }
            scenario.onActivity { model.replay() }
            await { model.state.value.playing }
            val manager = context.getSystemService(AudioManager::class.java)
            val otherFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setOnAudioFocusChangeListener {}.build()
            scenario.onActivity { assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(otherFocus)) }
            await { !model.state.value.playing }
            manager.abandonAudioFocusRequest(otherFocus)
            scenario.onActivity {
                model.mode(TranslationMode.SPEECH_TEXT)
                model.input("Keep this text")
            }
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                scenario.onActivity { model.startRecording() }
                assertFalse(model.state.value.recording)
                assertTrue(model.state.value.error.orEmpty().contains("permission"))
            }
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
            scenario.onActivity { model.startRecording() }
            assertTrue(model.state.value.recording)
            scenario.onActivity { model.cancel() }
            assertFalse(model.state.value.recording)
            assertEquals("Keep this text", model.state.value.input)
            scenario.onActivity { model.startRecording() }
            assertTrue(model.state.value.recording)
        }
        await { !model.state.value.recording }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 60000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Workflow timed out" }
            Thread.sleep(25)
        }
    }
}
