# Maxmize Music — Neon Lime UI Redesign

This branch starts the visual refresh using the existing Android/Kotlin/Jetpack Compose architecture.

## Design direction
- Deep black / charcoal surfaces.
- Neon-lime accent (#C8E64C) as the primary brand accent.
- Artwork-led Now Playing screen with a full-screen artwork backdrop and dark readability scrims.
- Rounded, restrained surfaces with subtle borders.
- Shared spacing, shapes, and color tokens; avoid introducing a second accent system.
- Keep the existing user-selectable theme, font, density, and accent settings working.

## Changes in this branch
- Added MaxmizeDesign.kt as a shared set of reusable visual tokens.
- Changed portrait Now Playing layout so artwork is no longer presented as a circular album-art tile; the current track artwork is used as the full-screen backdrop and controls sit in the lower part of the screen.
- Improved the artwork backdrop with a layered dark gradient and a platform-aware blur (Android 12+). On Android 7–11 the backdrop remains dimmed for legibility because Compose's built-in blur modifier requires Android 12+.
- Unified highlighted navigation, Home feature-card, DJ Home, and DJ Profile accents with the active Material theme accent instead of the former lavender feature accent.
- Preserved existing playback callbacks, queue screen, navigation test tags, and track-selection behavior.

## Important implementation note
The project has minSdk = 24. The built-in Compose Modifier.blur is supported from Android 12 (API 31). Devices on Android 7–11 currently receive the same artwork backdrop with a stronger dim overlay, not a true blur. If true blur is required on those devices, add a compatible Coil bitmap transformation or another API-24-compatible blur implementation as a separate, tested change rather than relying on Modifier.blur.

## Google AI Studio integration prompt
Use this prompt after checking out this branch or importing its latest source:

> Update the existing Maxmize Music Android application using the Neon Lime UI redesign in branch ui-redesign-neon-lime as the reference implementation. This is a native Kotlin + Jetpack Compose application, not a React/web project.
>
> First inspect the current project and merge the existing changes from this branch. Do not recreate the app or replace its architecture.
>
> Extend the same design language consistently across Home, Library, DJ Mode (DJ Home, Automix, Queue, Profiles, Analysis), Settings, and all playback-related screens:
> - deep black / charcoal backgrounds;
> - neon lime as the active accent (respect the user's selected accent preset where the app already supports it);
> - refined typography using the existing font settings;
> - consistent rounded cards, subtle borders, spacing, and selected states;
> - clean, minimal icons with consistent visual weight; avoid adding a new icon dependency unless necessary.
>
> Keep every existing feature and callback working. Do not change Media3 playback, Room/database entities, media scanning, permissions, playlist logic, navigation routes, or settings persistence as part of a visual-only change. Preserve all existing test tags and accessibility content descriptions.
>
> For Now Playing, keep the current track artwork as a full-screen background, remove the circular artwork presentation on portrait screens, apply a dark overlay for readable text, and keep the title, artist, favorite, seek bar, shuffle, previous, play/pause, next, repeat, Queue, Lyrics, Audio, and Up Next controls visible and functional. Previous and Next must remain distinct and clearly visible. On API levels below 31, do not call unsupported blur APIs; use a compatible image blur implementation or the existing dimmed fallback.
>
> Before finishing, run the Gradle unit tests and assemble the debug APK. Fix compile errors and regressions. Report exactly which files changed and any tests that could not be run. Do not modify signing configuration, application ID, package namespace, minSdk, or release configuration.

## Verification checklist
- [ ] Gradle debug build succeeds.
- [ ] Existing unit and Compose UI tests pass.
- [ ] Playback: play/pause, previous, next, seek, shuffle, repeat.
- [ ] Artwork changes when the current track changes.
- [ ] Queue, Lyrics, Audio/Equalizer and Up Next open correctly.
- [ ] Home, Library, DJ tabs, Settings and bottom navigation remain functional.
- [ ] Dark, Light and System themes still work.
- [ ] Accent preset, font family, text scale and layout density still work.
- [ ] Android 11 / API 30 fallback is visually checked.