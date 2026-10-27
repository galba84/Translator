## Context

The Kotlin/Compose Android app implements offline translation with a minimum SDK of 33. Launcher icons and resource processing are valid.

Languages: English (`en`) and Spanish (`es`) only, each selectable as source and target for text and speech in all four modes. All inference libraries must be free and open source; every required model, tokenizer, and voice resource must be bundled, with no first-run downloads.

Installation size has no user-imposed limit and is not a model-selection or release acceptance constraint. Prioritize translation accuracy, speech quality, and measured performance on the target phone. Available device storage, runtime RAM, and technical packaging limits still determine whether an artifact can be installed and executed.

| Translation mode | Source languages | Target languages | Pipeline |
| --- | --- | --- | --- |
| Text to text | English, Spanish | English, Spanish | Typed text -> translation -> displayed text |
| Speech to text | English, Spanish | English, Spanish | Recording -> transcription -> translation -> displayed text |
| Text to speech | English, Spanish | English, Spanish | Typed text -> translation -> displayed text and spoken output |
| Speech to speech | English, Spanish | English, Spanish | Recording -> transcription -> translation -> displayed text and spoken output |

These modes translate into the selected target language, rather than merely transcribing or reading the source. Same-language selection uses passthrough text; selected speech input/output stages still run. Both distinct-language directions are supported in every mode. Switching modes preserves the selected source and target languages; never silently change a language or mode.

Target device: the user's Xiaomi/Redmi Note 14 Pro. Xiaomi lists a [4G variant](https://www.mi.com/global/product/redmi-note-14-pro/specs/) with Helio G100-Ultra and a [5G variant](https://www.mi.com/my/product/redmi-note-14-pro-5g/specs/) with Dimensity 7300-Ultra, both with configurations starting at 8 GB RAM. Record the exact variant and Android version during testing. Compatibility and performance have not yet been measured.

## Goals / Non-Goals

**Goals:** Four offline translation modes with the language matrix above, bundled resources usable from first launch, free open-source libraries, and acceptance testing on the user's Note 14 Pro.

**Non-Goals:** continuous/background listening, voice cloning, preservation of the speaker's voice, history, accounts, runtime model downloads, and cloud fallback.

## Decisions

1. Separate Compose presentation from translation, speech input, speech output, and resource services. A state holder coordinates the selected mode and pipeline stages and exposes language selection, editable input/transcript, translated text, readiness, progress, playback, and errors.

2. Adopt this implementation baseline, subject to conversion, quality, and target-device acceptance tests. These components have not yet been integrated or benchmarked together.

   | Component | Baseline | License evidence |
   | --- | --- | --- |
   | Translation runtime | ONNX Runtime Android, CPU baseline | [Mobile support](https://onnxruntime.ai/docs/get-started/with-mobile.html), [MIT](https://github.com/microsoft/onnxruntime/blob/main/LICENSE) |
   | Translation weights | Helsinki-NLP OPUS-MT, converted to ONNX; evaluate INT8 quantization | Model cards label Apache-2.0: [es-en](https://huggingface.co/Helsinki-NLP/opus-mt-es-en), [en-es](https://huggingface.co/Helsinki-NLP/opus-mt-en-es) |
   | Tokenizer | Kotlin unigram segmentation reading source SentencePiece scores and joint Marian IDs; Unicode NFKC/whitespace normalization | [Apache-2.0 format/reference implementation](https://github.com/google/sentencepiece); official tokenizer golden fixtures in unit tests |
   | Speech runtime | sherpa-onnx 1.12.20 prebuilt Android JNI AAR, replacing direct whisper.cpp compilation | [Apache-2.0 runtime with Android support](https://github.com/k2-fsa/sherpa-onnx/tree/v1.12.20) |
   | Speech weights | Multilingual Whisper `base`, evaluating quantization; never `base.en` | [MIT for code and weights](https://github.com/openai/whisper) |
   | Speech output | sherpa-onnx VITS with bundled Piper-format en_US-lessac-medium and es_ES-sharvard-medium voices, tokens/configs, and eSpeak phonemizer data | [Voice repository labelled MIT](https://huggingface.co/rhasspy/piper-voices), original model cards bundled; phonemizer includes GPL-3.0 eSpeak NG |

   Implementation uses prebuilt sherpa-onnx for both Whisper and Piper-format models, avoiding a new local NDK toolchain and direct integration with the newer GPL Piper engine. Exact downloads are checksum-locked in `scripts/resources.lock.json`; all runtime model files have an APK integrity manifest. Original model cards and engine/dependency license texts are included in the APK acknowledgements. The native runtime still includes GPL eSpeak NG; preserve applicable corresponding-source and notice obligations when redistributing. Do not depend on installed system TTS or downloadable voice packs. Uncommon model-specific SentencePiece normalization mappings remain a quality-validation limitation; common English/Spanish inputs are tested against the official tokenizer.

   Pin library versions, model revisions, conversion tools, and checksums before packaging. Verify licenses of the exact redistributed artifacts, including tokenizer files and dependencies, and include required notices. Paid APIs, subscriptions, API keys, and proprietary inference SDKs are excluded.

3. Use two direct models: `en -> es` and `es -> en`. No pivot routing or extra language models are needed. Same-language selection returns unchanged input without translation inference; the selected speech stages still run.

   The implemented routes use two pinned Xenova ONNX conversions. Android outputs are checked against desktop ONNX reference results in both directions. Translate one sentence at a time while reusing sessions within a request: paragraph-level input omitted later sentences in initial reference runs. Greedy cached decoding is used; quality with representative native-language examples still needs acceptance testing.

4. Use Whisper in transcription mode with explicit English or Spanish as the selected source language. Put the transcript in editable input, then use the text translation service. Do not use Whisper's speech-translation mode for arbitrary target languages. A larger multilingual model is an alternative if base fails recognition quality, subject to renewed device measurements.

   In speech-output modes, synthesize the translated text in the selected English or Spanish target language and play it locally after translation completes. Always show the translated text. Provide stop and replay actions. On synthesis/playback failure, retain text and offer audio retry without rerunning translation or recording. End playback on cancellation, mode/input/language changes, screen exit, or audio-focus loss. Stop playback before recording to avoid capturing the app's own voice, and discard stale audio generation results.

5. Bundle all translation/recognition weights, tokenizers, vocabularies, English and Spanish voice models/configs, phonemizer data, and configuration in a self-contained installable APK. Revision-pinned downloads and conversion during build preparation are allowed; the installed application never downloads resources. Evaluate distribution size constraints before store publication without introducing on-demand resources.

6. Initialize resources locally using a versioned checksum manifest. Copy assets into private storage only when an engine requires filesystem paths; use temporary files and atomic completion. Report progress, allow local retry after recoverable storage errors, and explain repair/reinstall for corrupt bundled assets. First launch and launch after clearing app data must work in airplane mode.

   Compress bundled model files in the APK; inference uses extracted files, so uncompressed APK model entries are unnecessary. Keep regenerated copies under `cacheDir/offline-resources` so Android can reclaim them when staging updates. Migrate the previous `noBackupFilesDir/offline-resources` directory, touching only that generated model directory. Detect missing/incomplete cache on screen entry and before new operations, and reconstruct it from bundled assets. This reduces update storage pressure without changing model bytes or requiring downloads; Android still needs temporary staging headroom beyond the APK size.

7. Build native libraries for `arm64-v8a` and start with CPU inference, without requiring GPU/NPU acceleration. Serialize recognition, translation, and synthesis; load only models needed for the current stage and release previous large sessions. Measure APK size, storage overhead, peak memory, per-stage and full-pipeline cold/warm latency, and thermal behavior on the user's phone.

   The accepted response budget is up to 5-6 seconds for a few sentences: use 6 seconds as the maximum acceptance threshold on the user's Note 14 Pro. Measure from text submission or the end of recording to the displayed complete translation; speech-output modes must also start audible translated speech within that same total budget. Include recognition, translation, synthesis startup, and any per-request model loading; do not assign each stage its own six-second allowance. Recording duration and the time needed to speak the full result are excluded. Measure first requests after cold app start and repeated requests separately. Report one-time installation/resource initialization separately rather than hiding per-request model loading there. Use fixed, documented few-sentence fixtures across every valid mode/language combination; record sentence and word counts with the results. This is an acceptance target, not measured performance.

8. Request microphone permission on speech input. Stop recording on stop, cancellation, or leaving the screen; preserve text after cancellation/failure. Keep inference off the UI thread, discard stale completion events, and release resources with their owning lifecycle. Do not add INTERNET permission; audit the merged release manifest and transitive dependencies.

## Risks / Trade-offs

- Installation feasibility -> Record bundled and extracted storage needs and verify installation with sufficient free space; impose no product size cap. Evaluate quantization for runtime memory and speed only when quality remains acceptable.
- Conversion/tokenization/quantization errors -> Compare Android results against upstream reference inference in both directions.
- English/Spanish recognition, translation, or synthesis quality insufficient -> Test recordings, voice intelligibility, and reference translations; revise models without dropping required mode/language combinations.
- Device variant affects latency/memory -> Benchmark the exact phone, with sequential loading and responsive UI; support remains unverified until acceptance passes.
- Local extraction failure/corruption -> Block affected inference, retry local extraction where recoverable, and explain when reinstall is required.

## Migration Plan

Replace the greeting after services and state handling exist. Preserve package identity, theme, launcher activity, and restored icons. No translation-data migration is needed. Invalidate extracted files when the bundled model version changes. Reverting implementation restores the starter workflow.

## Open Questions

- Record the exact Note 14 Pro variant, RAM, Android version, and runtime ABI during connected-device testing.
- Do OPUS-MT conversion/quantization, English/Spanish Whisper recognition, and local synthesis meet quality and full-pipeline latency expectations on that phone? Finalize revisions after measurement.
- Which English and Spanish voice models and native TTS integration meet redistribution, intelligibility, and device constraints? Pin voice revisions and phonemizer assets before implementation is finalized.
- Establish translation/speech quality and runtime memory thresholds after the feasibility run. The few-sentence response limit is already set to 6 seconds; installation size has no user-imposed threshold.
