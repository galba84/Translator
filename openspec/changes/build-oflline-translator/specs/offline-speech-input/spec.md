## ADDED Requirements

### Requirement: Permission-aware speech capture
The application SHALL request microphone access when the user invokes speech input and SHALL record only after permission is granted and the user starts capture.

#### Scenario: Permission denied
- **WHEN** the user denies microphone permission
- **THEN** the application does not record, explains that speech input requires permission, and keeps typed input usable

### Requirement: Recognize speech locally
The application SHALL recognize English and Spanish speech locally with bundled resources, without network access or remote fallback. It SHALL use the selected source language for transcription and place the transcript in editable input. Both speech-to-text and speech-to-speech SHALL permit English and Spanish source and target languages only.

#### Scenario: Recognize in airplane mode
- **WHEN** the user records a phrase with recognition resources ready and connectivity disabled
- **THEN** recognition runs locally and its transcript becomes editable translation input

#### Scenario: All required speech languages
- **WHEN** the user records a phrase with English or Spanish selected as source in airplane mode
- **THEN** each recording produces an editable source-language transcript that can be translated into any target allowed by the selected mode

#### Scenario: Language selection is consistent across modes
- **WHEN** the user selects speech-to-text mode
- **THEN** both source and target selectors offer exactly English and Spanish

#### Scenario: No recognizable speech
- **WHEN** capture ends without recognizable speech or recognition fails
- **THEN** the application displays an actionable error and permits another recording without overwriting existing text

### Requirement: Stop and cancel recording
The application SHALL show when recording is active and SHALL release microphone capture when the user stops, cancels, or leaves the active screen. Cancellation SHALL preserve the existing translation input.

#### Scenario: Cancel capture
- **WHEN** the user cancels an active recording
- **THEN** microphone capture ends and no transcript replaces the existing input

#### Scenario: Leave capture screen
- **WHEN** the user leaves the active screen while recording
- **THEN** microphone capture ends
