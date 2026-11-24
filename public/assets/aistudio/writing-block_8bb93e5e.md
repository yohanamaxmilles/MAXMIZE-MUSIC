# MAXMIZE MUSIC
# ULTIMATE MASTER UPGRADE
## INTELLIGENT DJ AUTOMIX, PERFORMANCE, SMART MUSIC MANAGEMENT, ARTWORK, LYRICS, UI/UX AND COMPLETE BUG FIXING

You are a world-class Android development team specializing in professional DJ software, audio engineering, digital signal processing, music information retrieval, Android architecture, Kotlin, Jetpack Compose, database engineering, and premium mobile application design.

Your mission is to upgrade and perfect the EXISTING MAXMIZE MUSIC Android application.

The application is already functional. Your job is to identify its weaknesses, fix existing problems, improve its architecture, and implement advanced capabilities.

THIS IS NOT A NEW APPLICATION.

Do not rebuild the project from scratch.

Do not remove existing functionality.

Do not replace working implementations without a technical reason.

Inspect the complete existing source code before making changes.

Preserve existing user data, playlists, preferences, playback history, and working features.

IMPORTANT PRIORITY:

The Intelligent Automix DJ Engine is the most important feature of this upgrade.

It must become a genuinely intelligent, reliable, genre-aware, offline-capable DJ system.

Do not deliver visual simulations, fake analysis, decorative controls, or unfinished functionality.

==================================================
PRIORITY 1: INTELLIGENT AUTOMIX DJ ENGINE
==================================================

Transform the existing Automix into an advanced professional DJ system.

The current Automix has problems selecting unrelated genres and producing inconsistent musical transitions.

For example:

- A Pop song is playing.
- The next song unexpectedly becomes Psytrance.
- A Deep House track transitions into Hard Techno without a suitable musical transition.

These are unacceptable in strict DJ mixing profiles.

The DJ must understand musical relationships, not simply select the next available track.

## 1.1 Advanced music intelligence

Analyze and understand each music track using:

- Primary genre.
- Secondary genre.
- Subgenre.
- BPM.
- Musical key.
- Camelot notation.
- Energy.
- Danceability.
- Rhythm characteristics.
- Loudness.
- Dynamic range.
- Beatgrid.
- Downbeats.
- Intro structure.
- Outro structure.
- Phrase structure where detectable.
- Transition compatibility.
- Analysis confidence.

Use existing metadata first.

Perform local audio analysis when metadata is unavailable.

Never invent BPM, key, genre, or energy values.

Store analysis results locally.

## 1.2 Advanced genre classification

Implement a hierarchical music classification system.

Electronic music:

- Progressive House.
- Melodic House.
- Deep House.
- Tech House.
- Minimal House.
- Future House.
- Bass House.
- Big Room.
- Electro House.
- Progressive Trance.
- Uplifting Trance.
- Psytrance.
- Techno.
- Melodic Techno.
- Hard Techno.
- Drum and Bass.
- Dubstep.
- Future Bass.

Other genres:

- Pop.
- Dance Pop.
- Synth Pop.
- Hip Hop.
- R&B.
- Afrobeats.
- Afro House.
- Amapiano.
- 3-Step.
- Reggae.
- Rock.
- Alternative.
- Classical.

Allow custom genres and subgenres.

Do not classify every electronic song as the same genre.

Use confidence scores and allow manual corrections.

## 1.3 Intelligent genre compatibility engine

Build a genre relationship matrix.

Examples:

Progressive House → Progressive House: Excellent.

Progressive House → Melodic House: Compatible.

Melodic House → Progressive Trance: Conditional compatibility.

Tech House → Minimal House: Compatible.

Pop → Dance Pop: Compatible.

Pop → Commercial Dance: Compatible.

Psytrance → Psytrance: Excellent.

Psytrance → Progressive Trance: Conditional compatibility.

Pop → Psytrance: Reject by default in strict mode.

Deep House → Hard Techno: Reject by default in strict mode.

Reggae → Hard Techno: Reject by default in strict mode.

These are default compatibility rules.

Allow users to customize genre relationships.

A strict genre restriction must not be overridden merely because two songs have similar BPM or musical keys.

Open Format mode is allowed to mix different genres, but transitions must still be musically intelligent.

## 1.4 Intelligent multi-factor track selection

Create a sophisticated candidate ranking algorithm.

Evaluate:

1. Genre compatibility.
2. Subgenre compatibility.
3. BPM compatibility.
4. Harmonic compatibility.
5. Energy compatibility.
6. Phrase compatibility.
7. Rhythm compatibility.
8. Transition compatibility.
9. Loudness compatibility.
10. User preferences.
11. Mixing profile.
12. Previous track history.

Use weighted compatibility scoring.

Provide a compatibility score from 0 to 100.

Apply strict rejection rules before ranking candidates.

Do not allow high BPM or key scores to compensate for a prohibited genre combination.

If no compatible track exists:

- Search the selected music collection again.
- Search compatible subgenres.
- Consider user-approved relaxed constraints.
- Offer a controlled transition.
- Explain when no suitable track exists.

Never silently select an unrelated track.

## 1.5 Long-term DJ intelligence

The DJ must think ahead.

Do not select each next song independently.

Implement multi-track look-ahead planning.

Evaluate several upcoming tracks before choosing the next transition.

Maintain:

- Genre consistency.
- Musical direction.
- BPM progression.
- Energy progression.
- Harmonic flow.
- Artist diversity.
- Track variety.

Avoid repetitive songs and sudden changes in musical atmosphere.

Build a continuously updated DJ queue.

Recalculate upcoming selections when the user changes the mixing profile or playlist.

Do not interrupt the current transition when recalculating the queue.

## 1.6 Professional DJ mixing profiles

Create dedicated profiles:

- Progressive House DJ.
- Melodic House DJ.
- Deep House DJ.
- Tech House DJ.
- Techno DJ.
- Hard Techno DJ.
- Psytrance DJ.
- Trance DJ.
- EDM Festival DJ.
- Commercial Dance DJ.
- Afro House DJ.
- Amapiano DJ.
- Open Format DJ.

Each profile must define:

- Preferred genres.
- Allowed subgenres.
- BPM ranges.
- Key compatibility.
- Energy progression.
- Transition style.
- Phrase matching.
- Genre compatibility.
- Track selection weights.

Allow users to customize, save, duplicate, and delete personal profiles.

## 1.7 Advanced BPM and harmonic mixing

Implement real BPM detection.

Support:

- Tempo estimation.
- Tempo stability.
- Beatgrid detection.
- Downbeat detection.
- BPM synchronization.
- Tempo adjustment.
- Pitch preservation.
- Key Lock.
- Camelot Wheel compatibility.
- Manual BPM correction.
- Manual key correction.

Use confidence scores.

Avoid aggressive tempo adjustments when the difference is too large.

Do not claim a track is harmonically compatible without valid analysis.

## 1.8 Professional DJ transitions

Implement genuine audio transitions:

- Dual-deck playback.
- Beatmatching.
- Beat-synchronized crossfade.
- Phrase-aware transitions.
- Bass swap.
- EQ-assisted transitions.
- Intro-to-outro mixing.
- Outro-to-intro mixing.
- Automatic gain matching.
- Configurable transition duration.
- Tempo synchronization.
- Key Lock.

Analyze the actual audio before selecting transition points.

Avoid cutting vocals or important musical phrases unnecessarily when phrase information is available.

Use reliable beatgrid information.

Do not simulate DJ mixing with visual effects.

Both decks must produce real synchronized audio during transitions.

Prevent clipping, clicks, gaps, sudden volume changes, and incorrect synchronization.

==================================================
PRIORITY 2: AUTOMIX MUST NEVER LOCK THE APPLICATION
==================================================

This is a critical architecture bug.

Currently, when Automix is active, certain sections of the application, particularly Settings, become unavailable or unresponsive.

Fix this completely.

Automix must operate independently from the application's navigation and screen lifecycle.

## 2.1 Background playback architecture

Implement a proper Android playback architecture using an appropriate MediaSessionService and Media3 architecture where compatible with the existing project.

The DJ engine must run independently of the currently displayed screen.

Separate:

- Audio playback service.
- DJ engine.
- Track selection engine.
- Audio analysis engine.
- Navigation.
- UI state.
- Settings.
- Database operations.

Do not tie the lifetime of Automix to a Compose screen, Activity, or navigation destination.

Do not use blocking operations on the main thread.

Do not hold global application locks during audio playback.

Do not block database access while the DJ engine is active.

## 2.2 Full application accessibility during Automix

When Automix is active, the user must freely access:

- Settings.
- Music Library.
- Home.
- Search.
- Albums.
- Artists.
- Playlists.
- Lyrics.
- Equalizer.
- Music Information.
- Queue.
- DJ Settings.
- Personalization.

The user must be able to navigate between screens without stopping Automix.

The user must be able to edit settings while music continues playing.

The user must be able to modify playlists and inspect track information.

## 2.3 Live settings changes

Allow safe settings updates during Automix.

Examples:

- Change theme.
- Change accent color.
- Change typography.
- Change lyrics settings.
- Change scanning preferences.
- Change audio preferences.
- Change DJ mixing profile.
- Adjust transition duration.
- Adjust volume normalization.
- Change queue preferences.

Apply changes at safe points.

For changes affecting the DJ algorithm, update future track selections without abruptly interrupting the currently playing track or active transition.

## 2.4 Reliable application state

Use reactive state management.

Maintain independent states for:

- Playback.
- Automix.
- DJ queue.
- Current track.
- Current deck.
- Incoming deck.
- Lyrics.
- Navigation.
- Settings.

Navigating away from the player must never reset the playback engine.

Opening Settings must never require stopping Automix.

## 2.5 Automix lifecycle

Support:

- Start Automix.
- Pause Automix.
- Resume Automix.
- Stop Automix.
- Navigate away.
- Return to player.
- Lock device.
- Open another application.
- Change settings.
- Restart the application.

Restore playback and queue state where appropriate.

The DJ service must handle Android audio focus, service lifecycle, and background execution correctly.

==================================================
PRIORITY 3: ULTRA-FAST MUSIC SCANNER
==================================================

The existing scanner is too slow.

Transform it into a high-performance incremental music discovery system.

## 3.1 First-time scanning

After permission is granted:

1. Query Android MediaStore.
2. Retrieve available audio metadata.
3. Index music files.
4. Display songs immediately as results become available.
5. Load artwork asynchronously.
6. Continue metadata enrichment in the background.
7. Start optional DJ analysis separately.

Do not wait for BPM analysis, key detection, or artwork downloads before displaying the library.

## 3.2 Incremental scanning

Implement:

- Initial full scan.
- Incremental scanning.
- New track detection.
- Deleted track detection.
- Modified track detection.
- MediaStore change observation.
- Database synchronization.
- Duplicate prevention.

Do not rescan the entire library every time the application opens.

Use cached database results immediately.

Where supported, use MediaStore version and generation information to optimize change detection.

## 3.3 Separate processing pipelines

Pipeline A: Basic music discovery.

Pipeline B: Artwork retrieval and metadata enrichment.

Pipeline C: Advanced DJ analysis.

Pipeline D: Lyrics retrieval.

These operations must not unnecessarily block each other.

Use background workers, bounded concurrency, and caching.

==================================================
PRIORITY 4: SMART MUSIC METADATA AND ARTWORK FIXER
==================================================

Implement an advanced Music Fixer system.

Its purpose is to automatically improve music files that have missing or incorrect information.

This feature must be practical, reliable, and safe.

## 4.1 Automatic album artwork detection

When a song has no album cover:

1. Read embedded artwork.
2. Check the local album artwork cache.
3. Search local artwork resources.
4. Identify the track using title, artist, album, and other available metadata.
5. Search authorized artwork providers.
6. Compare search results with the actual track information.
7. Select the most accurate artwork.
8. Save the artwork locally.
9. Associate it with the correct track or album.
10. Display it in all relevant screens.

Do not assign random artwork.

Do not assign an unrelated artist's image.

Do not assign a cover merely because the song title matches.

Use multiple matching signals.

If confidence is low, request user confirmation.

## 4.2 Artwork providers

Build an independent provider architecture.

Use legitimate sources such as:

- MusicBrainz.
- Cover Art Archive.
- iTunes Search API where appropriate.
- Other authorized artwork providers.

Use provider-specific fallback logic.

Do not scrape unauthorized websites.

Respect rate limits and provider terms.

## 4.3 Automatic artwork download

Provide settings for:

- Automatically find missing artwork.
- Automatically download matching covers.
- Wi-Fi only.
- Mobile data allowed.
- Download high-quality artwork.
- Download artwork for the entire library.
- Download artwork for selected playlists.
- Retry failed artwork searches.
- Clear artwork cache.

Artwork retrieval must happen in the background.

Never delay music playback while downloading covers.

## 4.4 Artwork quality

Prefer:

- High-resolution square artwork.
- Correct album cover.
- Consistent image quality.
- Proper aspect ratio.
- Efficient image storage.

Avoid stretched, pixelated, distorted, or unrelated images.

## 4.5 Smart metadata fixer

Identify missing or incorrect:

- Track title.
- Artist.
- Album.
- Album artist.
- Genre.
- Track number.
- Disc number.
- Year.
- Artwork.
- Other supported metadata.

Use embedded tags and legitimate metadata sources.

Compare results before suggesting corrections.

Do not automatically overwrite correct existing information.

## 4.6 Safe metadata editing

Provide:

- Fix one track.
- Fix selected tracks.
- Fix entire album.
- Scan missing metadata.
- Preview proposed corrections.
- Confirm changes.
- Undo supported changes.

Keep original files safe.

Use proper Android permissions and supported write access mechanisms.

Never modify files without user authorization.

If a correction only affects the application's local database, clearly distinguish it from an actual embedded file tag modification.

==================================================
PRIORITY 5: ADVANCED LYRICS ENGINE
==================================================

Fix the existing lyrics problems.

The current system sometimes displays overlapping lines, incorrect timing, and fails to find lyrics for many tracks.

Implement:

- Multiple authorized lyrics providers.
- Exact track matching.
- Alternative search queries.
- Local lyrics caching.
- Automatic downloads.
- LRC parsing.
- Enhanced LRC support.
- Line-level synchronization.
- Word-level synchronization where available.
- Lyrics offset correction.
- Tap-to-seek.
- Manual lyrics import.
- Offline lyrics.

Prioritize authorized sources such as LRCLIB and Musixmatch where API access and licensing permit.

Do not scrape unauthorized copyrighted lyrics websites.

## 5.1 Better lyrics matching

Compare:

- Title.
- Artist.
- Featured artists.
- Album.
- Duration.
- Version.
- Remix information.
- Alternative spelling.

Avoid matching live, instrumental, radio edit, or remix versions incorrectly.

## 5.2 Accurate synchronization

Use actual player position as the source of truth.

Fix:

- Timestamp parsing.
- Millisecond conversion.
- Multiple timestamps.
- Duplicate timestamps.
- Line ordering.
- Lyrics offset.
- Seeking.
- Playback speed.
- Automatic scrolling.

Never fabricate timestamps.

If synchronized lyrics are unavailable, display clean plain lyrics.

## 5.3 Lyrics layout

Implement:

- Proper line heights.
- Consistent spacing.
- Correct text wrapping.
- No overlapping lines.
- Smooth scrolling.
- Current-line highlighting.
- Manual scrolling.
- Automatic follow mode.
- Responsive typography.

Ensure lyrics always belong to the actual current track.

Cancel old lyrics requests when tracks change.

==================================================
PRIORITY 6: PREMIUM UI AND TYPOGRAPHY
==================================================

Improve the complete application interface.

Preserve the MAXMIZE MUSIC black and gold visual identity.

Use a premium, minimal, modern music application design.

Avoid generic Material Design layouts.

Typography recommendations:

Primary: Inter.

Secondary: Manrope.

Use licensed, bundled font resources.

Create a consistent typography system.

Improve:

- Song titles.
- Artist names.
- Screen headings.
- Lyrics.
- Metadata.
- Buttons.
- Navigation.
- Settings.

Maintain proper line height, spacing, readability, and font weights.

Avoid:

- Random gradients.
- Unnecessary glass effects.
- AI-looking decorative elements.
- Excessive cards.
- Too many buttons.
- Inconsistent icons.
- Poor text alignment.
- Cluttered layouts.

Every visible button must have a working function.

==================================================
PRIORITY 7: ADVANCED AUDIO AND MUSIC MANAGEMENT
==================================================

Preserve and improve existing audio functionality.

Where supported, implement:

- Functional 5-band or 10-band EQ.
- Bass Boost.
- Virtualizer.
- ReplayGain.
- Loudness normalization.
- Audio balance.
- Playback speed.
- Gapless playback.
- Crossfade.
- Skip silence.
- Audio visualizer based on real playback audio.
- Headphone behavior.
- Bluetooth controls.

Improve music management:

- Favorites.
- Playlists.
- Recently Played.
- Most Played.
- Recently Added.
- Recently Skipped.
- Song ratings.
- Duplicate detection.
- Missing metadata detection.
- Smart playlists.
- Advanced search.
- Music statistics.

Never delete original music files without explicit approval.

==================================================
PRIORITY 8: ADVANCED PERSONALIZATION
==================================================

Allow users to customize:

- Theme.
- Accent color.
- Fonts.
- Font size.
- Player layout.
- Library layout.
- Mini-player.
- Lyrics appearance.
- Gesture behavior.
- Playback settings.
- DJ profiles.
- Automix preferences.

Settings must remain accessible while Automix is active.

Persist all preferences locally.

Apply settings without unnecessary application restarts.

==================================================
PRIORITY 9: PERFORMANCE, STABILITY AND DATABASE
==================================================

Optimize the entire application.

Requirements:

- Fast startup.
- Fast music discovery.
- Efficient artwork caching.
- Efficient lyrics caching.
- Efficient DJ analysis.
- Low memory usage.
- Low unnecessary battery consumption.
- Smooth navigation.
- Responsive audio.
- Reliable background playback.
- Database efficiency.
- Safe database migrations.

Use Kotlin Coroutines, Flow, Room, DataStore, and suitable Android background processing where appropriate.

Never block the main thread with heavy processing.

Avoid unnecessary parallel audio analysis.

Preserve all existing database records.

==================================================
PRIORITY 10: COMPLETE BUG AUDIT
==================================================

Inspect and fix all existing application bugs.

Specifically test:

1. Automix locking Settings.
2. Automix stopping during navigation.
3. Playback state inconsistencies.
4. Incorrect next-track selection.
5. Genre incompatibility.
6. Incorrect BPM analysis.
7. Incorrect key detection.
8. Audio transition problems.
9. Lyrics showing the previous track.
10. Lyrics overlapping.
11. Incorrect lyrics timestamps.
12. Missing album artwork.
13. Incorrect artwork matching.
14. Incorrect metadata.
15. Slow music scanning.
16. Repeated scanning.
17. Artwork loading delays.
18. Queue problems.
19. Background playback failures.
20. UI crashes.
21. Memory leaks.
22. Database errors.
23. Settings not saving.
24. Non-functional controls.

Fix root causes rather than hiding errors.

==================================================
PRIORITY 11: TESTING AND VERIFICATION
==================================================

Perform actual functional tests.

AUTOMIX TESTS:

- Progressive House playlist.
- Melodic House playlist.
- Tech House playlist.
- Psytrance playlist.
- Pop playlist.
- Techno playlist.
- Mixed electronic playlist.
- Strict genre compatibility.
- Open Format transitions.
- Multi-track look-ahead.
- BPM matching.
- Harmonic matching.
- Energy progression.
- Dual-deck mixing.
- Continuous playback.
- Offline operation.

APPLICATION ACCESSIBILITY TESTS:

1. Start Automix.
2. Open Settings.
3. Change the theme.
4. Open Music Library.
5. Search for a song.
6. Open Lyrics.
7. Edit a playlist.
8. Change DJ preferences.
9. Return to Now Playing.

Music must continue playing throughout.

ARTWORK TESTS:

- Song without cover.
- Album without cover.
- Multiple tracks sharing an album.
- Incorrect existing artwork.
- Artwork download failure.
- Offline artwork display.
- Duplicate artwork prevention.

SCANNER TESTS:

- First installation.
- Permission handling.
- Large music library.
- Application restart.
- Incremental scanning.
- New songs.
- Deleted songs.
- Offline operation.

LYRICS TESTS:

- Plain lyrics.
- LRC.
- Enhanced LRC.
- Long lyrics.
- Rapid track switching.
- Seeking.
- Manual scrolling.
- Offline cached lyrics.

==================================================
PRIORITY 12: IMPLEMENTATION STRATEGY
==================================================

Do not modify everything simultaneously.

Follow this order:

PHASE 1: Audit existing architecture and identify root causes.

PHASE 2: Fix Automix and application navigation conflicts.

PHASE 3: Upgrade Intelligent DJ selection and genre compatibility.

PHASE 4: Improve dual-deck audio mixing and transitions.

PHASE 5: Optimize music scanner and DJ analysis.

PHASE 6: Implement Smart Artwork and Metadata Fixer.

PHASE 7: Repair lyrics matching, synchronization, and layout.

PHASE 8: Improve typography and complete UI consistency.

PHASE 9: Implement additional personalization and audio features.

PHASE 10: Perform complete regression testing and prepare APK.

Build after every significant phase.

Fix compilation errors before proceeding.

Do not destroy working features.

==================================================
STRICT DEVELOPMENT REQUIREMENTS
==================================================

- Work on the existing source code.
- Preserve existing functionality.
- Preserve existing user data.
- Do not generate fake metadata.
- Do not generate fake artwork.
- Do not simulate DJ intelligence.
- Do not fabricate BPM or musical keys.
- Do not fabricate synchronized lyrics.
- Do not use unauthorized scraping.
- Do not block Settings during Automix.
- Do not tie playback to screen lifecycle.
- Do not make audio analysis block music scanning.
- Do not make artwork downloading block playback.
- Do not introduce unnecessary dependencies.
- Do not leave decorative or non-functional controls.
- Do not claim completion without testing.

If technical limitations prevent a feature from being implemented completely, explain the exact limitation and implement the best legitimate alternative.

==================================================
FINAL DELIVERY REQUIREMENTS
==================================================

Deliver the upgraded existing Android project.

Include:

- Updated source code.
- Optimized music scanner.
- Advanced intelligent Automix engine.
- Genre-aware track selection.
- Multi-track DJ planning.
- Improved DJ transitions.
- Independent background playback architecture.
- Full application accessibility during Automix.
- Smart artwork downloader.
- Metadata fixer.
- Advanced lyrics engine.
- Premium typography.
- UI/UX improvements.
- Database improvements.
- Bug fixes.
- Testing report.
- Performance comparison.
- Installable APK.

Report which features are fully implemented, partially implemented, and still incomplete.

Never report an untested feature as completed.

FINAL PRODUCT VISION:

MAXMIZE MUSIC must become a premium Android offline music player with a genuinely intelligent DJ engine.

It must scan music quickly, understand musical genres, choose compatible tracks, create professional transitions, retrieve accurate lyrics, automatically find missing album artwork, correct music metadata safely, and remain fully accessible while Automix is running.

THE DJ MUST THINK LIKE A DJ.

THE APPLICATION MUST REMAIN RESPONSIVE.

THE MUSIC LIBRARY MUST LOAD FAST.

THE MUSIC INFORMATION MUST BE ACCURATE.

EVERY FEATURE MUST ACTUALLY WORK.

PRESERVE WHAT WORKS. FIX WHAT IS BROKEN. IMPROVE WHAT IS SLOW. BUILD WHAT IS MISSING. TEST EVERYTHING.