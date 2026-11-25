# MAXMIZE MUSIC: Intelligent DJ Engine v2

## Product contract

The DJ engine has two user-facing operating modes:

- Automix: the listener chooses a mixing technique or profile. The engine performs the selected style.
- Intelligent DJ: the engine manages the musical journey, track selection, transitions, and DJ announcements with less user intervention.

Both modes use the same analysis, decision, transition-planning, and audio-execution layers.

Hybrid operation is mandatory:
- Local playback and transition execution never depend on an internet connection.
- Local analysis supplies BPM, beat timing, loudness, waveform features, and other measurable audio evidence.
- A remote provider may enrich genre, subgenre, mood, and content identity when configured and authorized.
- Cloud credentials stay on an authenticated backend. No model API key belongs in the Android APK.
- Metadata lookup and audio upload are separate permissions. Raw audio is never uploaded by default.
- A cloud timeout or offline device must not stop an active mix.

## Current source modules

### v2 contracts and decision layer

- `data/dj/v2/DjIntelligenceModels.kt`: evidence-aware track profile, content kind, sections, decision weights, and transition contracts.
- `data/dj/v2/DjDecisionEngine.kt`: deterministic offline ranking, strict safety filters, tempo compatibility, Camelot scoring, energy matching, and rejection reasons.
- `data/dj/v2/DjTransitionPlanner.kt`: refuses to invent phrase boundaries or instrumental intro cues. It returns a plan only when reliable evidence exists.
- `data/dj/v2/DjTrackProfileFactory.kt`: maps existing library data into v2 without promoting estimated intro/outro percentages into verified sections.
- `data/dj/v2/CloudDjIntelligenceProvider.kt`: secure cloud-provider boundary. A backend implementation and user-consent UI are still required.
- `player/djvoice/DjVoiceDirector.kt`: on-device Android TextToSpeech announcements. Lifecycle integration, voice preferences, and final mix ducking still need wiring.

### Existing audio runtime

The current Media3 service already owns two ExoPlayer decks and a background queue. The current transition executor mainly uses player-volume curves and playback-rate changes. The v2 planner is not yet connected to this runtime. Do not describe current transitions as sample-accurate phrase mixing.

## Required engine layers

1. Content gate: distinguish verified music from podcasts, audiobooks, spoken word, sound effects, and unknown audio.
2. Audio decoder: decode representative intro, middle, and outro windows without blocking the UI or playback.
3. Rhythm analysis: onset envelope, tempo hypotheses including half/double time, beatgrid phase, downbeat, tempo stability, and confidence.
4. Harmonic analysis: windowed spectral chroma, key candidates, Camelot conversion, confidence, and disagreement checks across sections.
5. Structure analysis: intro, outro, breakdown, buildup, drop, vocal and instrumental regions, silence, and phrase boundaries.
6. Music identity: metadata-backed genre taxonomy plus optional cloud enrichment. Audio-only genre guesses must remain low confidence.
7. Session memory: recent tracks, artists, genres, energy curve, transitions, listener skips, and DJ profile.
8. Decision engine: hard rejection rules first, then multi-factor ranking and a reason for every choice.
9. Transition planner: select a safe outgoing phrase, incoming cue, beat alignment, tempo ratio, key-lock behavior, and EQ/filter strategy.
10. Deck DSP: independent deck gain, 3-band EQ, high/low-pass filter, limiter, transition automation, and clipping protection.
11. DJ voice: opening, occasional contextual announcements, and closing. Voice should not interrupt vocals or drops; speech should duck music smoothly.
12. Session supervisor: recovery, offline fallback, queue replenishment, navigation independence, and persistence.

## DJ decision rules

Hard safety checks must run before scoring:
- Never queue an explicitly identified podcast, audiobook, spoken-word item, or sound effect into music Automix.
- Reject long unverified audio.
- Reject missing or low-confidence BPM when the selected technique requires beatmatching.
- Reject unknown genre in Strict Genre mode.
- Reject a tempo ratio outside the user's configured pitch range.
- Do not claim harmonic compatibility when either key is unknown.
- Respect recently played tracks and configurable artist-repeat limits.

Ranking may then consider:
- Genre/subgenre/family compatibility.
- Tempo ratio and tempo stability.
- Harmonic relationship and key confidence.
- Energy direction and session phase.
- Mood and vocal/instrumental placement.
- Intro/outro and phrase suitability.
- Artist and track repetition.
- User skips, favorites, and manual queue choices.

Every decision must retain its component scores and plain-language explanation.

## Transition execution

The planner and executor are separate. The planner returns cue points and a transition recipe. The service executes the recipe.

Required transition techniques:
- Beat and downbeat synchronization.
- 8/16/32-bar phrase alignment.
- Intro-to-outro and instrumental-to-instrumental blends.
- Bass swap with independent low-frequency control.
- Vocal-aware midrange management.
- Filter-assisted transitions.
- Loop extension only when a reliable loop region exists.
- Tempo easing after the transition, with key lock when enabled.
- Automatic gain matching and a final limiter.

Two independently clocked ExoPlayer instances do not guarantee sample-accurate beat phase. A later audio-runtime phase must measure real deck phase and decide whether to keep the current dual-player architecture or introduce a shared PCM mixer. No UI animation is evidence of a real audio transition.

## DJ voice behavior

The DJ voice is enabled by default in Intelligent DJ mode:
- Welcome at session start.
- Short context-aware announcements at suitable musical gaps.
- Farewell at session end.
- No repeated chatter on every track.
- Never speak over a vocal hook, breakdown, or peak drop when avoidable.
- Duck music gently while speaking and restore the exact active mix gains afterward.
- Use on-device TTS as the offline fallback. A cloud voice provider is optional and must never be required for playback.

## Implementation phases

### Phase A: evidence and safety
- Finish tests for v2 ranking, content filtering, and transition planning.
- Replace fabricated fallback BPM/key/intro/outro values with unknown values.
- Introduce analysis versioning and cache invalidation for old estimates.
- Build a reliable multi-window decoder and feature extraction tests.

### Phase B: real musical analysis
- Improve tempo hypotheses and beatgrid detection.
- Implement windowed FFT/chroma key analysis.
- Detect structure and phrase boundaries with confidence.
- Validate against a labelled test corpus of real tracks across House, Techno, Trance, Pop, Hip-Hop, Afro, and other genres.

### Phase C: DJ session brain
- Add session phases, energy memory, artist-repeat limits, mood, and user feedback.
- Implement Automix technique selection.
- Implement Intelligent DJ autonomous session decisions.
- Add decision explanations and manual overrides.

### Phase D: live audio execution
- Add independent per-deck DSP controls.
- Connect transition plans to the Media3 service.
- Verify phase alignment, gain staging, EQ, key lock, clipping, clicks, and gaps on real devices.
- Keep the service independent of Compose navigation.

### Phase E: hybrid intelligence and voice
- Configure a secure backend provider and consent controls.
- Cache cloud enrichment locally and retain provenance/model version.
- Wire the on-device DJ voice into the playback service.
- Add speech timing and music ducking.

### Phase F: verification
- Test offline operation and network loss during a set.
- Test long audio, podcasts, poor metadata, low-confidence BPM/key, and genre conflicts.
- Test multiple hours of playback and background operation.
- Test on physical Android devices and compare against reference DJ transitions.

## Acceptance criteria

The engine is not complete until:
- It never invents BPM, key, genre, mood, or phrase markers.
- It reliably excludes spoken audio from music queues.
- It selects tracks for musical reasons, not BPM alone.
- It chooses and executes phrase-aware transitions using measured deck timing.
- It handles vocal overlap, bass handoff, gain, and tempo safely.
- It keeps playing offline and during app navigation.
- DJ voice starts and ends sessions, with context-aware speech and smooth ducking.
- Automated tests pass and real-device listening tests confirm the sound.
