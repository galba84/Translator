## 1. Scope and feasibility

- [x] 1.1 Define four translation modes: English/Spanish for text and speech input/output in both directions, and Xiaomi/Redmi Note 14 Pro as the target device family.
- [x] 1.2 Require free open-source libraries and bundled redistributable resources without runtime downloads.
- [ ] 1.3 Record exact phone variant, RAM, Android version, and runtime ABI from the connected device.
- [x] 1.4 Integrate pinned quantized OPUS-MT ONNX conversions, source SentencePiece token mapping, cached decoding, and both direct routes; compare both against desktop reference fixtures on Android. Target-phone quality evaluation remains in 4.2.
- [x] 1.5 Integrate multilingual Whisper base INT8 through sherpa-onnx Android JNI, restrict speech input to English/Spanish, and verify both with emulator speech fixtures. Target-phone validation remains in 4.2.
- [ ] 1.6 Record APK and installed/extracted sizes for installation planning; measure peak memory, cold/warm latency, quality, and thermal behavior, define runtime/quality acceptance thresholds, and finalize models without a product size cap.
- [x] 1.7 Pin shipped runtime versions and model/tokenizer artifacts, checksum downloads and bundled files, include licenses/model cards/attribution, and document reproducible resource preparation. Preconverted ONNX artifacts are used rather than an unpinned local conversion.
- [x] 1.8 Integrate pinned Piper-format English/Spanish voices through sherpa-onnx, bundle phonemizer data and license notices, and verify synthesis/recognition round trips on an emulator. Native-language quality and phone performance remain in 4.2/4.6.

## 2. Services and bundled initialization

- [x] 2.1 Define translation, recognition, synthesis, playback, and resource interfaces with independent readiness/error states and mode-aware pipeline orchestration.
- [x] 2.2 Implement both direct routes with sequential model loading, per-sentence decoding with shared sessions, and same-language passthrough; verify outputs against reference fixtures.
- [x] 2.3 Package translation/recognition models, tokenizers, English/Spanish voices/configs, and phonemizer data in the APK; implement integrity checks, atomic extraction when needed, progress, preparation failure recovery, and local retry.
- [x] 2.4 Integrate prebuilt ARM64/x86_64 sherpa-onnx Whisper, explicit source-language transcription, microphone permission, stop/cancel, and lifecycle cleanup.
- [x] 2.5 Include license notices, preserve launcher/icons, and verify the merged debug manifest has RECORD_AUDIO and no INTERNET permission.
- [x] 2.6 Integrate bundled English/Spanish synthesis and local playback; implement stop/replay, audio-focus handling, stale-audio rejection, recording/playback exclusion, and audio retry retaining text.

## 3. Translation workflow

- [x] 3.1 Replace the greeting with four-mode selection, consistent English/Spanish language selectors across modes, editable input, initialization indicators, and translated text in every mode.
- [x] 3.2 Connect speech capture and editable transcripts, including permission denial and recognition errors.
- [x] 3.3 Implement recognition/translation/synthesis stage progress and errors, sequential engine use, and stale-result rejection after mode/input/language changes.
- [x] 3.4 Connect automatic spoken translated results in speech-output modes, stop/replay controls, and audio retry without repeating recognition or translation.

## 4. Verification

- [x] 4.1 Test independent readiness, invalid local extraction targets and retry, damaged extracted-file repair, same-language speech pipelines, mode/language filtering, reference tokenization, and pipeline cancellation with unit tests and real Android instrumentation.
- [ ] 4.2 Install the self-contained APK on the user's Note 14 Pro; verify first-launch airplane-mode operation for every valid combination in all four modes, both translation directions, and English/Spanish recognition and synthesis.
- [ ] 4.3 Repeat initialization after clearing app data offline; verify microphone denial, unrecognized speech, cancellation, and leaving the screen during recording.
- [x] 4.4 Verify stop/replay, audio-focus loss, language preservation across mode changes, permission denial, recording cancellation, and stopping capture on screen exit with a foreground Android workflow test. Synthesis failure/retry is implemented; fault-injection acceptance remains in 4.7.
- [x] 4.5 Run clean Android build/resource checks, unit tests, and first-install airplane-mode model tests; record emulator timings and remaining target-device checks in docs/validation.md. Full Gradle lint did not complete; IDE inspection found no errors in the implementation files.
- [ ] 4.6 Define fixed few-sentence fixtures with sentence/word counts and benchmark every valid mode/language combination on the user's Note 14 Pro: complete text and, where applicable, audible speech onset within 6 seconds of submission/recording end, including per-request model loading; report cold/repeated requests and one-time initialization separately.
- [ ] 4.7 Complete target-phone failure acceptance for unrecognized real speech and synthesis/playback errors, verifying text retention and retry without repeating earlier pipeline stages.
