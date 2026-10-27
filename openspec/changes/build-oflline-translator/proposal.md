## Why

Users need to translate words and spoken phrases when an internet connection is unavailable. The app currently displays a starter greeting and has no translation workflow.

## What Changes

- Offer text-to-text, speech-to-text, text-to-speech, and speech-to-speech translation modes.
- Offer English and Spanish as source and target in every mode, supporting both translation directions and same-language passthrough.
- Translate typed words and phrases locally from first launch using bundled resources.
- Recognize English and Spanish speech locally with microphone permission and translate the editable transcript.
- Synthesize English and Spanish translated results locally in speech-output modes; show translated text and provide stop/replay controls.
- Bundle all models, tokenizers, and configuration; initialize locally without runtime downloads.
- Use free open-source libraries and redistributable models, without paid services or API keys.
- Validate the complete offline workflow on the user's Xiaomi/Redmi Note 14 Pro.
- Explain missing resources, unsupported languages, recognition failures, and translation failures with actionable messages.

## Capabilities

### New Capabilities
- `offline-translation`: Language selection and on-device translation of typed or recognized text.
- `offline-speech-input`: Permission-aware speech capture and local transcription for translation.
- `offline-speech-output`: Bundled English/Spanish speech synthesis, playback, stop/replay, and audio failure recovery.
- `language-resource-readiness`: Bundled resource integrity, local initialization, and offline readiness from first launch.

### Modified Capabilities
None. There are no existing capability specifications.

## Impact

- `MainActivity.kt` and Compose UI state, strings, and new translation/resource components.
- `AndroidManifest.xml`: microphone permission; no INTERNET permission. Preserve the launcher activity and restored icons.
- Baseline: ONNX Runtime Android and SentencePiece for OPUS-MT translation; whisper.cpp for English/Spanish transcription; evaluate Piper and redistributable English/Spanish voices for synthesis. Exact revisions, licenses, and artifacts require validation.
- Bundled assets, ARM64 native integration, local extraction, license notices, storage/memory use, and first-launch airplane-mode tests.
- This change defines the translator; implementation and measured Note 14 Pro compatibility remain outstanding.
