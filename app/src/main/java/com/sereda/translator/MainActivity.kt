package com.sereda.translator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sereda.translator.ui.theme.TranslatorTheme

class MainActivity : ComponentActivity() {
    private val translator: TranslatorViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TranslatorTheme {
                val state by translator.state.collectAsState()
                TranslatorScreen(state, translator)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        translator.ensureResources()
    }

    override fun onStop() {
        translator.leavingScreen()
        super.onStop()
    }
}

@Composable
private fun TranslatorScreen(state: TranslatorState, model: TranslatorViewModel) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.startRecording() else model.permissionDenied()
    }
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Translator", style = MaterialTheme.typography.headlineLarge)
            Text("Your words, in another language. Entirely offline.", style = MaterialTheme.typography.bodyMedium)
            Choice("Translation mode", state.mode.label, TranslationMode.entries.toList(), { it.label }, model::mode,
                enabled = !state.initializing)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    Choice("From", state.source?.label ?: "Choose language", state.mode.sources(), { it.label }, model::source,
                        enabled = !state.initializing)
                }
                Box(Modifier.weight(1f)) {
                    Choice("To", state.target?.label ?: "Choose language", state.mode.targets(), { it.label }, model::target,
                        enabled = !state.initializing)
                }
            }
            if (!state.selectionValid) {
                Text("Choose a source and target language.", color = MaterialTheme.colorScheme.error)
            }
            if (state.initializing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.progress)
            } else {
                state.readiness.error(state.mode)?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = model::initialize) { Text("Retry preparation") }
                }
            }
            OutlinedTextField(
                value = state.input, onValueChange = model::input,
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                label = { Text(if (state.mode.speechInput) "Transcript · you can edit it" else "Text to translate") },
                placeholder = { Text("A word, a phrase, or a few sentences…") },
                enabled = !state.recording && !state.initializing
            )
            if (state.mode.speechInput) {
                Button(onClick = {
                    if (state.recording) model.stopRecording()
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        model.startRecording()
                    } else permission.launch(Manifest.permission.RECORD_AUDIO)
                }, enabled = state.available && !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.recording) "Stop and translate" else "Record speech")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = model::translate, enabled = state.available && !state.recording && !state.busy && state.input.isNotBlank(),
                    modifier = Modifier.weight(1f)) {
                    Text(if (state.mode.speechInput) "Translate transcript" else "Translate")
                }
                if (state.busy || state.recording) {
                    OutlinedButton(onClick = model::cancel) { Text("Cancel") }
                }
            }
            if (state.busy || state.recording) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.progress)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                if (!state.initializing && state.readiness.translation != null) {
                    OutlinedButton(onClick = model::initialize) { Text("Retry preparation") }
                }
            }
            if (state.result.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.target?.label.orEmpty(), style = MaterialTheme.typography.labelLarge)
                        SelectionContainer { Text(state.result, style = MaterialTheme.typography.titleLarge) }
                        if (state.mode.speechOutput) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = model::replay, enabled = !state.busy && !state.recording) {
                                    Text(if (state.audioError != null) "Retry audio" else "Replay")
                                }
                                if (state.playing) OutlinedButton(onClick = model::stopPlayback) { Text("Stop audio") }
                            }
                        }
                        state.responseMillis?.let { Text("Response: %.1f s".format(it / 1000.0), style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            state.audioError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("English · Spanish · All four modes", style = MaterialTheme.typography.bodySmall)
            var licenses by remember { mutableStateOf(false) }
            TextButton(onClick = { licenses = true }) { Text("Open-source acknowledgements") }
            if (licenses) {
                val notices = remember {
                    context.assets.open("NOTICE.txt").bufferedReader().use { it.readText() } +
                        context.assets.list("licenses").orEmpty().sorted().joinToString("\n\n") { name ->
                            "\n$name\n" + context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
                        }
                }
                AlertDialog(onDismissRequest = { licenses = false }, title = { Text("Acknowledgements") },
                    text = { Text(notices, Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) },
                    confirmButton = { TextButton(onClick = { licenses = false }) { Text("Close") } })
            }
        }
    }
}

@Composable
private fun <T> Choice(label: String, selected: String, options: List<T>, name: (T) -> String, choose: (T) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(selected) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(name(option)) }, onClick = { expanded = false; choose(option) }) }
            }
        }
    }
}
