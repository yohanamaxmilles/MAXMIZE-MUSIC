# MAXMIZE DJ Engine Next

This is a clean, isolated DJ intelligence core under `com.example.data.dj.next`.
It does not call or reuse the legacy DJ queue, scoring, or analysis classes.

## Current implementation
- Offline mono PCM tempo estimation with autocorrelation.
- Beat timestamps and 16-beat phrase markers.
- RMS-based energy and coarse energy-section estimates.
- Confidence bands and explicit nulls for unsupported measurements.
- Independent candidate ranking using tempo, Camelot key, genre family and energy.
- Phrase-length transition plan generation.
- Equal-power crossfade envelope and low-frequency handover curve.
- Library metadata adapter that rejects low-confidence BPM/key values before they enter DJ decisions.
- Session arc planner that selects tracks phase-by-phase across opening, warm-up, build, peak, breather and closing.
- Unit tests for silence, synthetic 120 BPM pulses, ranking and mix curves.

## Deliberate limitations
The current PCM pass does not decode files itself. A decoder must supply mono Float PCM.
It does not claim to detect musical key, vocals, genre, drops or breakdowns. Those fields
remain null until dedicated chroma, vocal-separation/classification and structure models
are integrated. The section labels are energy heuristics, not semantic event recognition.
The transition plan is a plan, not a live dual-player mixer. Live two-deck playback must be
integrated with a verified two-source PCM mixer and device latency tests before advertising
fully automatic DJ performance.

## Safety rule
Automatic beat sync must require a reliable beatgrid. When confidence is low, the engine
returns a conservative plan and leaves the transition under manual control.
