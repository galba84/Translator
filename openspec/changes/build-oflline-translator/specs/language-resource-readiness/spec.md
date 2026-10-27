## ADDED Requirements

### Requirement: Bundle complete offline resources
The installed application SHALL include all models, tokenizers, vocabularies, and configuration for text English-to-Spanish and Spanish-to-English translation, plus recognition and synthesis resources for English and Spanish, including voice models and phonemizer data. It SHALL require no runtime downloads, preinstalled speech engine, or network access, including on first launch. No additional translation-language models or phonemizer language definitions SHALL be bundled.

#### Scenario: First launch offline
- **WHEN** the user installs the complete APK and launches it for the first time with connectivity disabled
- **THEN** resources initialize locally and all four modes become available with English/Spanish text and speech support

#### Scenario: App data cleared
- **WHEN** the user clears app data and launches offline
- **THEN** any required extracted files are reconstructed from bundled assets without downloading resources

### Requirement: Initialize resources safely
The application SHALL expose local initialization progress, verify resource completeness and integrity, and reject incomplete extracted files. It SHALL offer local retry after recoverable failures and distinguish storage failures from corruption requiring installation repair.

#### Scenario: Insufficient storage
- **WHEN** local extraction fails because storage is insufficient
- **THEN** affected inference stays unavailable, the application requests free storage, and it offers local retry without a download prompt

#### Scenario: Corrupt bundled asset
- **WHEN** an installed asset fails integrity verification
- **THEN** the affected capability stays unavailable and the application explains that installation repair or reinstall is required

### Requirement: Use free open-source components
All inference libraries and their required dependencies SHALL be free and open source. Bundled model, tokenizer, voice, and phonemizer asset licenses SHALL permit redistribution. The release SHALL include required notices and attribution, pin versions/revisions and resource checksums, and require no paid service, subscription, or API key.

#### Scenario: Release audit
- **WHEN** a release APK is prepared
- **THEN** each bundled model, tokenizer, voice, phonemizer asset, and inference library has a recorded origin, pinned revision or version, redistribution license, and required notices

### Requirement: Gate unavailable capabilities
The application SHALL report translation, recognition, and synthesis readiness independently and gate only modes requiring unavailable stages. Normal installations SHALL provide every required mode/language combination; errors SHALL NOT silently reduce declared support.

#### Scenario: Recognition initialization fails
- **WHEN** translation is ready but recognition resources fail initialization
- **THEN** typed translation remains usable and speech input displays an actionable initialization error

#### Scenario: Synthesis initialization fails
- **WHEN** translation and recognition are ready but voice resources fail initialization
- **THEN** text-to-text and speech-to-text remain available and speech-output modes explain their initialization error
