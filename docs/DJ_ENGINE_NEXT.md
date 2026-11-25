# MAXMIZE DJ Engine Next

This is a clean, isolated DJ intelligence core under `com.example.data.dj.next`.
It does not call or reuse the legacy DJ queue, scoring, or analysis classes.

## Current implementation
- Kotlin smart-cast issue found by the first CI compilation pass was corrected in the PCM tempo detector.
- Offline mono PCM tempo estimation with autocorrelation.
- Beat timestamps and 16-beat phrase markers.
- RMS-based energy and coarse energy-section estimates.
- Confidence bands and explicit nulls for unsupported measurements.
- Independent candidate ranking using tempo, Camelot key, genre family and energy.
- Phrase-length transition plan generation.
- Equal-power crossfade envelope and low-frequency handover curve.
- Library metadata adapter that rejects low-confidence BPM/key values before they enter DJ decisions.
- Session arc planner that selects tracks phase-by-phase across opening, warm-up, build, peak, breather and closing.
- Live Automix queue ranking now uses the independent engine and iteratively selects a compatible session sequence.
- Dual-deck cue planning now uses measured outgoing outro and incoming first-beat offsets, with a phrase-length transition duration.
- Existing `AudioAnalysisEngine` already decodes local media through `MediaExtractor`/`MediaCodec` into bounded mono PCM windows and derives BPM, key/chroma, energy and genre estimates. The new queue consumes those persisted measurements through the adapter. The separate `DjPcmAnalyzer` remains a focused PCM detector, not the app's production decoder.
- Offline Android TextToSpeech announces the incoming title and artist whenever an Automix transition starts. No network voice service is used.
- Both ExoPlayer decks now have independent three-band PCM EQ controls. BASS_SWAP and EQ_FILTER transitions automate low, mid and high bands; the processor resets both decks after a completed transition or when Automix stops.
- CI unit tests cover silence, synthetic 120 BPM pulses, ranking and mix curves.

## Deliberate limitations
The new standalone `DjPcmAnalyzer` accepts decoded mono Float PCM. Production library analysis currently uses the existing Android media decoder and its separate DSP implementation. BPM/key confidence still needs validation against a labelled reference set. Vocal presence, vocal phrases, semantic drops, breakdowns and build-ups are not yet detected reliably. Intro/outro markers remain heuristic estimates.

Live Automix uses two ExoPlayer instances with volume curves and per-deck three-band EQ processing. This is not yet a sample-accurate shared-clock mixer. Device testing is still required for cue accuracy, playback-rate matching, EQ artifacts, audio-focus handover, TTS audibility and gap-free deck swaps.

## Safety rule
Automatic beat sync must require a reliable beatgrid. When confidence is low, the engine
returns a conservative plan and leaves the transition under manual control.
