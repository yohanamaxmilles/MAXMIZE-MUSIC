# MAXMIZE online music integration

## Architecture

MAXMIZE follows a provider-adapter architecture:
- provider-neutral catalogue models and interface;
- provider-specific networking and response parsing isolated from Compose;
- discovery and playback capabilities declared separately;
- the existing Media3 service, local library and DJ engine remain independent.

This implementation is written for MAXMIZE. It does not copy BitChord source files.

## Current implementation

`YoutubeDataApiProvider` uses the official YouTube Data API v3 search endpoint. It returns video metadata and canonical YouTube links.

The adapter is intentionally discovery-only:
- it does not extract media URLs;
- it does not bypass YouTube's player;
- it does not send YouTube catalogue results to ExoPlayer;
- it does not permit DJ processing of YouTube catalogue results.

An API key must be supplied through secure runtime configuration or a backend. Do not commit keys.

## Next stages

1. Add secure API-key configuration and quota/error handling.
2. Connect catalogue search state to the Online Music screen.
3. Open results through an approved YouTube playback experience.
4. Add a separate music provider whose licence explicitly permits direct in-app audio playback.
5. Enable DJ Engine routing only for providers whose agreements expressly permit mixing, pitch/time changes and crossfades.

## Local playback protection

Do not change the local-file Track model, MusicRepository, PlaybackController, or MaxmizeMediaLibraryService to implement catalogue search. Keep online catalogue models separate until an authorized playback provider is selected.
