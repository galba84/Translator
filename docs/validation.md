# Validation evidence

The application supports English and Spanish for text and speech in all four modes. Only the direct `en-es` and `es-en` translation models are shipped. The phonemizer includes English/Spanish language definitions and dictionaries, with shared runtime data retained. Unsupported generated model copies are pruned when an existing installation prepares its resources.

## Checks

The unit suite checks both languages in every mode, direct routing, same-language speech passthrough, cancellation, independent resource readiness, and 12 reference-tokenizer fixtures generated with official SentencePiece. Android tests cover both real translation directions, English/Spanish synthesis and recognition, extracted-file integrity, local preparation recovery, cache eviction, legacy migration, and obsolete-resource cleanup. A foreground workflow test covers language preservation across mode changes, audio focus, replay, permission handling, capture cancellation, and screen exit.

Current English/Spanish-only build: clean APK build and unit tests passed; all nine Android integration/workflow checks passed in 51.172 seconds. The APK is 578,990,449 bytes (about 579 MB), and it updated the installed emulator app successfully. The packaged-resource check confirms exactly two translation models and English/Spanish phonemizer dictionaries and language definitions. Upgrade initialization removes obsolete extracted resources; the installed cache contains only `en-es` and `es-en` translation directories. OpenSpec strict validation passed. Previous storage-fix builds installed and updated successfully on the connected emulator with generated model copies in reclaimable cache. The current model files retain their pinned checksums; APK compression does not alter inference content.

## Target device acceptance

Only the Pixel 9a AVD (`sdk_gphone64_x86_64`, Android 17) is connected. Physical Xiaomi/Redmi Note 14 Pro acceptance remains outstanding:

- Record the exact phone variant, RAM, Android version, and ABI.
- Exercise English/Spanish in both directions and same-language passthrough across all four modes, using real microphone recordings and native-speaker quality review.
- Measure cold and repeated few-sentence requests: complete text and first audible output must arrive within six seconds after submission/end of recording, including per-request model loading. Report initial extraction, capture duration, and full playback separately.
- Record peak memory and thermal behavior. Installation size has no product cap, but the device needs space for Android update staging and extracted models.
- Fault-inject synthesis/playback failures and confirm text retention and audio-only retry. Complete real silence/unrecognized-speech checks.

Uncommon model-specific SentencePiece normalization mappings remain unverified beyond the reference fixtures. Full Gradle lint previously stalled in the Kotlin UAST/BidirectionalTextDetector scan and is not reported as passed; APK builds, unit tests, IDE inspection, and Android runtime tests are separate checks.

## Storage behavior

Models are compressed in the APK and extracted under `cacheDir/offline-resources`. Missing or incomplete cache is detected on entry and before operations, then reconstructed locally from bundled assets. Legacy `no_backup/offline-resources` copies are migrated without touching other app files. Old resources absent from the current integrity manifest are removed from the generated cache. No runtime download or INTERNET permission is needed.

Large generated assets and the Android speech AAR are fetched by `scripts/prepare-resources.ps1` in a fresh checkout. Resource URLs/checksums are in `scripts/resources.lock.json`; bundled file checksums are in `app/src/main/assets/resources.json`. Prebuilt native libraries are packaged as supplied because this environment has no NDK for stripping them.
