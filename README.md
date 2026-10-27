# Offline Translator

An Android app for translating English and Spanish entirely on device, built with Kotlin, Jetpack Compose, and local speech and translation models. It supports text-to-text, speech-to-text, text-to-speech, and speech-to-speech translation, displaying translated text in every mode. No account, API key, system speech engine, or runtime network permission is required.

## Project highlights

- **On-device inference:** Marian/OPUS-MT translation through ONNX Runtime, Whisper recognition, and Piper-format speech synthesis through sherpa-onnx.
- **Lifecycle-aware audio:** microphone permission handling, bounded recording, audio focus, stop/replay, and cleanup when the screen closes.
- **Cancellation and recovery:** stale requests cannot publish results; speech failures preserve translated text and allow audio-only retry.
- **Reproducible resources:** pinned model revisions, download checksums, bundled integrity verification, and recovery after cache eviction.
- **Validation:** pipeline and tokenizer unit tests plus Android integration and UI workflow tests. Measured results and remaining device checks are documented in [validation evidence](docs/validation.md).

## Code tour

| Area | Entry point |
| --- | --- |
| Compose UI and permissions | [MainActivity.kt](app/src/main/java/com/sereda/translator/MainActivity.kt) |
| UI state, lifecycle, and request coordination | [TranslatorViewModel.kt](app/src/main/java/com/sereda/translator/TranslatorViewModel.kt) |
| Pipeline contracts and cancellation | [Translation.kt](app/src/main/java/com/sereda/translator/Translation.kt) |
| Translation and source tokenization | [OnnxTranslationEngine.kt](app/src/main/java/com/sereda/translator/OnnxTranslationEngine.kt), [MarianTokenizer.kt](app/src/main/java/com/sereda/translator/MarianTokenizer.kt) |
| Recognition and synthesis | [OfflineSpeech.kt](app/src/main/java/com/sereda/translator/OfflineSpeech.kt) |
| Microphone capture and playback | [AudioIO.kt](app/src/main/java/com/sereda/translator/AudioIO.kt) |
| Resource integrity and cache recovery | [BundledResources.kt](app/src/main/java/com/sereda/translator/BundledResources.kt) |

## Prepare and build

Requires JDK 21, Android SDK Platform 36.1, and an Android 13+ device or emulator (ARM64 or x86_64). Clone the repository and open it in Android Studio, which creates your machine-specific `local.properties`. For a command-line setup, create that ignored file with `sdk.dir` pointing to your Android SDK (for example, `sdk.dir=C:/Users/your-name/AppData/Local/Android/Sdk`). Gradle 9.3.1 is used by this project. Before the first build, run from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-resources.ps1
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

The Gradle wrapper uses Gradle 9.3.1 and downloads it on first use if it is not already cached.

The preparation script downloads pinned ONNX translations, multilingual Whisper base, English/Spanish voices, and the sherpa-onnx Android AAR. It validates existing downloads against `scripts/resources.lock.json`, verifies Hugging Face LFS checksums, and generates the per-file APK manifest `app/src/main/assets/resources.json`. Large assets and the AAR are intentionally excluded from Git and must be regenerated in a fresh checkout. Build-time internet is needed for dependency/model preparation; the installed APK contains all resources and works offline from first launch.

Output: `app/build/outputs/apk/debug/app-debug.apk`. This is a development-signed APK, not a production-signed store release. ARM64 phones and x86_64 emulators are included. Models are compressed in the APK (about 579 MB total) and extracted into reclaimable app cache. Allow additional free space for Android's update staging. The app verifies resources before inference, rebuilds evicted cache from bundled assets, and removes obsolete generated resources during upgrades without touching other app files. Compression preserves all model bytes.

## Engines and lifecycle

- ONNX Runtime Android 1.23.2 executes pinned Xenova conversions of Helsinki-NLP Marian/OPUS-MT. English-to-Spanish and Spanish-to-English routes both use direct models. Models are reused across sentences within a request, with sentence splitting to prevent paragraph truncation observed during reference testing.
- A Kotlin source-SentencePiece unigram tokenizer reads `source.spm` scores and maps pieces into Marian's joint vocabulary; target-only pieces are excluded from source segmentation. Common English and Spanish fixtures are compared against the official SentencePiece implementation. Normalization uses Unicode NFKC and whitespace cleanup; model-specific uncommon normalization mappings are not exhaustively validated.
- sherpa-onnx 1.12.20 executes Whisper-base INT8 recognition and Piper-format English/Spanish synthesis. This replaces the initial direct whisper.cpp/Piper native integration proposal with a prebuilt Android runtime. The multilingual recognizer is explicitly restricted to English/Spanish in the UI and pipeline.
- AudioRecord captures 16 kHz mono PCM for at most 60 seconds. Recording is user initiated and ends on cancellation or screen exit. AudioTrack plays synthesized local audio, with stop/replay, audio focus, and no overlap with recording.
- A single inference dispatcher, generation checks, and scoped model sessions prevent old requests from publishing text or starting audio after selection/input changes. Audio failure preserves text and allows synthesis/playback retry without repeating translation.

## Validation

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
openspec.cmd validate build-oflline-translator --strict
```

Unit tests cover mode/language eligibility, routes, cancellation, same-language speech pipelines, independent readiness, and reference tokenization. Instrumentation tests execute both direct model routes and English/Spanish synthesis/recognition on an actual Android runtime. `scripts/generate-reference.py` regenerates fixtures using development-only Python packages `sentencepiece` and `onnxruntime==1.23.2`; Python is not part of the installed app.

Target: complete translated text and, for speech output, audible speech onset within six seconds after submitting a few sentences or stopping recording. Initialization, recording duration, and full playback duration are measured separately. Performance and native-language quality must be checked on the user's exact Xiaomi/Redmi Note 14 Pro; an emulator does not establish that acceptance target. See OpenSpec tasks and `docs/validation.md` for measured evidence and remaining device checks.

## Third-party material

`app/src/main/assets/NOTICE.txt`, `assets/licenses/`, original model cards, and `scripts/resources.lock.json` record origins, license texts, revisions, and checksums. The native speech library includes GPL-licensed eSpeak NG as well as Apache/MIT components; preserve corresponding-source availability and all applicable notices with redistributed builds. No voice training recordings are included. This repository and its exact dependency source/build references must accompany a redistribution audit.
