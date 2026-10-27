## ADDED Requirements

### Requirement: Synthesize translated results locally
The application SHALL synthesize translated text in English and Spanish using bundled voices without network access, external voice downloads, or a preinstalled speech engine. Speech-output modes SHALL display the translated text and automatically play its generated audio after successful translation.

#### Scenario: Offline Spanish output
- **WHEN** translation produces Spanish text in a speech-output mode on first launch in airplane mode
- **THEN** the application displays the Spanish translation and generates and plays Spanish speech locally

#### Scenario: Offline English output
- **WHEN** translation produces English text in a speech-output mode in airplane mode
- **THEN** the application displays the English translation and generates and plays English speech locally

### Requirement: Control playback and microphone use
The application SHALL expose playback state, stop, and replay controls. It SHALL stop playback on cancellation, mode/input/language changes, screen exit, or audio-focus loss and before recording begins. It SHALL discard stale synthesis results and avoid simultaneously recording its own output.

#### Scenario: Replay result
- **WHEN** the user requests replay of the current translated result
- **THEN** the application plays that result in the current target language without recording or rerunning translation

#### Scenario: Change selection during synthesis
- **WHEN** a user changes mode, input, or language while synthesis is running
- **THEN** audio for the previous selection does not start playing

#### Scenario: Begin a new recording
- **WHEN** the user starts speech input while translated audio is playing
- **THEN** playback stops before microphone capture starts

#### Scenario: Playback interrupted
- **WHEN** the user stops playback, leaves the screen, or the application loses audio focus
- **THEN** playback ends and the displayed translation is retained

### Requirement: Recover from audio failures
The application SHALL retain translated text if synthesis or playback fails, explain the failure, and offer audio retry without requiring translation or recording again.

#### Scenario: Synthesis failure
- **WHEN** a voice engine fails after translation succeeds
- **THEN** translated text remains visible and the user can retry speech output without repeating earlier stages
