# MAXMIZE DJ Engine Next

This is a clean, isolated DJ intelligence core under `com.example.data.dj.next`.
It does not call or reuse the legacy DJ queue, scoring, or analysis classes.

## Current implementation
- Kotlin smart-cast issue found by the first CI compilation pass was corrected in the PCM tempo detector.
- Offline mono PCM tempo estimation with autocorrelation.
- Beat timestamps and 32-beat phrase candidates (eight 4/4 bars).
- RMS-based energy and coarse energy-section estimates.
- Confidence bands and explicit nulls for unsupported measurements.
- Independent candidate ranking using tempo, Camelot key, genre family and energy.
- Phrase-length transition plan generation.
- Equal-power crossfade envelope and low-frequency handover curve.
- Library metadata adapter that rejects low-confidence BPM/key values before they enter DJ decisions, including refusing an incoming beat cue when tempo confidence is low.
- Session arc planner that selects tracks phase-by-phase across opening, warm-up, build, peak, breather and closing.
- Live Automix queue ranking now uses the independent engine and iteratively selects a compatible session sequence.
- Dual-deck cue planning now uses measured outgoing outro and incoming first-beat offsets, with a phrase-length transition duration.
- Existing `AudioAnalysisEngine` already decodes local media through `MediaExtractor`/`MediaCodec` into bounded mono PCM windows and derives BPM, key/chroma, energy and genre estimates. The new queue consumes those persisted measurements through the adapter. The separate `DjPcmAnalyzer` remains a focused PCM detector, not the app's production decoder.
- Offline Android TextToSpeech announces the incoming title and artist whenever an Automix transition starts. No network voice service is used.
- Both ExoPlayer decks now have independent three-band PCM EQ controls. BASS_SWAP and EQ_FILTER transitions automate low, mid and high bands; the processor resets both decks after a completed transition or when Automix stops.
- CI unit tests cover silence, synthetic 120 BPM pulses, ranking and mix curves.

## Deliberate limitations
The new standalone `DjPcmAnalyzer` accepts decoded mono Float PCM. Production library analysis currently uses the existing Android media decoder and its separate DSP implementation. BPM/key confidence still needs validation against a labelled reference set. Vocal presence, vocal phrases, semantic drops, breakdowns and build-ups are not yet detected reliably. Intro/outro markers are acoustic RMS activity estimates, not semantic arrangement labels.

Live Automix uses two ExoPlayer instances with volume curves and per-deck three-band EQ processing. This is not yet a sample-accurate shared-clock mixer. Device testing is still required for cue accuracy, playback-rate matching, EQ artifacts, audio-focus handover, TTS audibility and gap-free deck swaps.

## Safety rule
Automatic beat sync must require a reliable beatgrid. When confidence is low, the engine
returns a conservative plan and leaves the transition under manual control.


## Latest refinement
- DJ Voice now uses Android speech/navigation audio attributes, queues the next announcement instead of interrupting active speech, and reports speech start/end to the service.
- During an announcement, both decks are gently reduced to 72% of their current transition level. The original transition curve remains in control, and the deck levels return automatically when speech ends.
- Phrase candidate markers in the PCM analyser mark every 32 beats (eight 4/4 bars). Automix phrase alignment uses the stored phrase length only when BPM confidence is at least 0.62 and the BPM is within the supported range.
- Phrase markers are still grid-based candidates, not a semantic musical-phrase model. A labelled reference set and real-device listening tests are still required.
- Vocal analysis now runs locally through MediaPipe Tasks Audio with the YAMNet model on the decoded opening, middle and ending sample windows. Its singing and speech labels remain acoustic candidates, not verified lyrics or source separation. Frequency-band or energy heuristics are not accepted as proof of vocals.
- Intro/outro detection is still heuristic. The decoder samples separate opening, middle and ending windows. Do not treat those acoustic activity cue points as verified musical arrangement boundaries.


## Dedicated intro/outro decoding
- The production analysis decoder now decodes three independent bounded windows: the opening 20 seconds, a central 20-second analysis window, and the final 20 seconds.
- The non-contiguous windows are decoded separately. They are never concatenated for BPM or key analysis.
- Intro and outro envelopes use 500 ms RMS frames. A boundary estimate requires three consecutive frames across a relative level threshold. If the envelope is too short or ambiguous, the existing conservative duration-based fallback remains.
- The central analysis window now carries its absolute track offset into beat-grid generation. Beat positions and first-beat cues therefore stay on the full-track timeline instead of incorrectly starting at zero after a middle-window decode.
- These are acoustic activity boundaries. They do not identify musical arrangement sections with semantic certainty. Real-device validation against labelled tracks is still required.

## Vocal model selection notes
- A small open model named SMAD reports 87.95% accuracy on its own 4,000-clip synthetic test set, but its model card discloses MUSDB18-HQ (CC BY-NC-SA) and a real-world training subset without a license grant. Do not ship those weights in this commercial app without resolving the training-data rights.
- Essentia offers voice/instrumental classifiers, but its core library is AGPL-3.0 or separately licensed. A commercial Android integration needs a deliberate licensing decision.
- YAMNet is a 4.1 MB Apache-2.0 LiteRT/TFLite audio event model and is a lower-risk on-device baseline. Its AudioSet labels are broad sound-event classes, so a singing label must be treated as a candidate signal, not a verified lyric/vocal detector. It needs a labelled music validation set and threshold calibration before Automix uses it.
- YAMNet is now wired into production library analysis and its candidate metrics/segments are persisted with each track analysis. Tracks that cannot be decoded retain UNKNOWN vocal status. The model is not yet used to make Automix selection decisions.


## Beat synchronization refinement
- A dedicated beat-sync planner now requires reliable BPM confidence (at least 0.62) and a reliable first-beat cue on both decks.
- It checks direct, half-time and double-time relationships, then applies a speed correction only when the required adjustment stays within 8%.
- It rejects larger corrections and leaves the incoming deck at native speed. It does not clamp a poor match into a false sync.
- Beat and phrase cue alignment also stops when BPM confidence is low. This prevents fallback genre-prior BPM values from driving automatic cue placement.
- The planner has JVM unit tests for half-time matching, low-confidence input and excessive tempo correction.

## Vocal evidence pipeline
- A model-output interpreter now accepts frame scores from an approved audio classifier and groups singing/speech candidate segments.
- It tracks average and peak singing probability, active-frame ratio, speech probability and a conservative confidence band.
- YAMNet-style broad labels are treated as candidate evidence only. Low singing scores do not prove a track is instrumental.
- `YamnetVocalClassifier` uses MediaPipe Tasks Audio 1.2.3 and the pinned YAMNet model. Gradle downloads the 4.1 MB model into generated assets, verifies SHA-256 `4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf`, and packages it in the APK for offline inference. The model file is not committed as a binary to the repository.
- The production decoder supplies separate opening, central and ending PCM windows to the classifier. Each is resampled to 16 kHz mono. Vocal probabilities, activity ratio, speech probability, confidence and candidate segment timestamps are stored in Room. A database migration upgrades version 3 to version 4.
- YAMNet is a broad AudioSet sound-event classifier. It is not a vocal separator, lyric recognizer or guaranteed detector of quiet/background vocals. It remains advisory and is not yet used to make Automix decisions.
- Real-device latency, memory use, classification accuracy on EDM tracks and false-positive/false-negative rates still need testing with a labelled music set.


## YAMNet runtime integration
- Added MediaPipe Tasks Audio 1.2.3 with `AUDIO_CLIPS` mode and all model categories enabled so singing/speech evidence is not lost by a top-five-only result.
- Added a pinned Google-hosted YAMNet model download at build time. The Gradle task checks the expected SHA-256 before packaging the model asset. Runtime inference uses the packaged asset and requires no network access.
- The classifier linearly resamples the decoder's mono PCM to 16 kHz and sends independent opening, middle and ending windows through YAMNet. Original track offsets are added to each classifier timestamp.
- `TrackAnalysis` and its Room entity now retain average/peak singing probability, vocal active ratio, average speech probability, confidence, candidate segment JSON and an analysis version. Room migration 3 to 4 adds these fields without dropping existing analysis data.
- If decoding or inference is unavailable, vocal status remains UNKNOWN. It is never converted into an instrumental classification.
- This is an initial production integration. Device testing and labelled EDM validation are required before vocal-aware transition rules are enabled.


## Production BPM confidence refinement
- Replaced raw onset autocorrelation with mean-centred normalized autocorrelation to reduce dependence on track loudness and broad RMS changes.
- Reduced the tempo prior so measured periodicity dominates candidate selection.
- Weak or non-periodic signals now retain a neutral display BPM with zero confidence because the persisted model requires a non-null BPM field. The new DJ profile adapter rejects that value, so it cannot drive beat sync or tempo matching.
- Added synthetic production-pipeline tests at 90, 128 and 174 BPM plus a low-level flat-signal confidence test.
- These tests verify the DSP path on synthetic PCM. A labelled real-world EDM reference set and device listening tests are still required before claiming production-grade BPM accuracy.
