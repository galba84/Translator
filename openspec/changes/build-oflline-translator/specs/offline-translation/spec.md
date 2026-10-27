## ADDED Requirements

### Requirement: Select supported languages and enter text
The application SHALL offer English (`en`) and Spanish (`es`) independently as text source and text target languages and allow users to enter or edit text. It SHALL support both directed pairs between distinct languages in every mode. Same-language selection SHALL skip translation inference and retain unchanged text while still performing speech stages required by the selected mode.

#### Scenario: Prepare a translation
- **WHEN** a user selects a supported language pair and enters a non-empty word or phrase
- **THEN** the application retains the selected pair and input and allows translation when its resources are ready

#### Scenario: Empty input
- **WHEN** the input contains only whitespace or is empty
- **THEN** the application prevents translation and presents no new translation result

#### Scenario: All required directions
- **WHEN** the user submits non-empty text with resources ready for each of `en -> es` and `es -> en`
- **THEN** each request produces a local translation in its selected target language

#### Scenario: Same-language selection
- **WHEN** the user submits non-empty text with identical source and target languages
- **THEN** the application returns unchanged input without inference

### Requirement: Translate locally
The application SHALL translate text on the device without network access when the selected pair's resources are ready. It SHALL display the result with the selected target language and SHALL NOT silently fall back to a remote service.

#### Scenario: Translate in airplane mode
- **WHEN** resources are ready and the user requests translation with network connectivity disabled
- **THEN** the application displays a translation produced locally

### Requirement: Handle translation lifecycle and failures
The application SHALL expose translation progress, show recoverable failures, preserve input after failure, and prevent results for earlier input or language selections from replacing the current result.

#### Scenario: Engine failure
- **WHEN** local translation fails
- **THEN** the application ends the progress state, preserves the input, and displays an error with a retry action

#### Scenario: Selection changes during translation
- **WHEN** a translation completes after the user changes the input or language pair
- **THEN** its result does not replace the result for the current input and selection

### Requirement: Validate the target phone
The application SHALL pass offline acceptance checks on the user's Xiaomi/Redmi Note 14 Pro before device support is declared verified. Results SHALL record exact variant, RAM, Android version, runtime ABI, package and installed sizes, peak memory, and cold/warm response times.

#### Scenario: Device acceptance
- **WHEN** a release candidate is installed on the target phone and launched in airplane mode
- **THEN** both translation directions, English/Spanish recognition and synthesis, and every valid combination in all four modes work without network access, crashes, or out-of-memory termination while the UI remains responsive

### Requirement: Four translation modes
The application SHALL offer text-to-text, speech-to-text, text-to-speech, and speech-to-speech modes. Source and target languages SHALL be English and Spanish only for text and speech. It SHALL translate to the selected target language, display translated text in all modes, and synthesize it in speech-output modes. It SHALL retain the selected source and target when changing modes and SHALL NOT silently change language or mode.

#### Scenario: Speech-to-speech translation
- **WHEN** the user records English with Spanish selected as target in speech-to-speech mode
- **THEN** the application transcribes locally, translates to Spanish, displays Spanish text, and speaks the Spanish result locally

#### Scenario: Spanish text-to-speech translation
- **WHEN** the user enters Spanish text with English selected as target in text-to-speech mode
- **THEN** the application translates the text into English and displays and speaks the English result

#### Scenario: Change translation mode
- **WHEN** the user changes from text-to-text with English source and Spanish target to a speech-output mode
- **THEN** English source and Spanish target remain selected and valid

#### Scenario: Speech-to-text translation
- **WHEN** the user records Spanish with English selected as target in speech-to-text mode
- **THEN** the application displays the English translation without synthesizing audio

#### Scenario: Same-language speech-to-speech
- **WHEN** the user records English with English selected as target in speech-to-speech mode
- **THEN** the application transcribes the recording, skips translation inference, displays the transcript, and synthesizes English output

### Requirement: Few-sentence response time
On the user's Xiaomi/Redmi Note 14 Pro, the application SHALL display the complete translation within 6 seconds for documented few-sentence acceptance fixtures in every valid mode/language combination. Speech-output modes SHALL also begin audible translated output within the same 6-second budget. Timing SHALL start at text submission or the end of recording and include recognition, translation, synthesis startup, and per-request model loading as applicable. Recording duration and full audio playback duration SHALL be excluded. Validation SHALL record fixture sentence/word counts and cold-request and repeated-request timings separately; one-time bundled-resource initialization SHALL be reported separately.

#### Scenario: Timed speech-to-speech request
- **WHEN** the user finishes recording a few-sentence acceptance fixture in English with Spanish as the spoken target on the target phone
- **THEN** the complete translated text is displayed and audible Spanish output begins within 6 seconds of recording ending

#### Scenario: Timed text-to-text request
- **WHEN** the user submits a few-sentence acceptance fixture for a supported text pair on the target phone
- **THEN** the complete translated text is displayed within 6 seconds, including any required per-request model loading
