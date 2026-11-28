# MAXMIZE online music integration

## Architecture

MAXMIZE uses provider adapters:
- provider-neutral catalogue models in `data/online`;
- provider-specific API requests and response parsing stay outside Compose;
- catalogue discovery and playback permissions are declared separately;
- the existing Media3 player service remains the playback engine;
- local Library data and DJ processing remain separate from online catalogue data.

## Audius provider

`AudiusMusicProvider` provides:
- public track search through the official Audius REST API;
- artist, title, artwork, duration and canonical track-page metadata;
- direct streaming through the official `/tracks/{track_id}/stream` endpoint;
- filtering of tracks marked as stream-gated.

Audius' published Free API plan currently allows 10 requests per second and 500,000 requests per month. A developer API key is required for the app integration. The key is free to create and Audius documents it as safe for client-side use. MAXMIZE stores the key locally in app preferences. No bearer token, Audius login, subscription or paid plan is required.

The app only streams tracks returned as public and not marked stream-gated. A creator can disable third-party API streaming, and those tracks will not be playable through this integration.

## Playback and DJ boundaries

Audius streams are passed to the existing Media3 playback controller as remote MediaItems. The app does not extract streams from YouTube or other services.

Online Audius tracks are not converted into local Library records. DJ processing is disabled for Audius tracks because permission to stream does not by itself establish permission for tempo changes, key changes, crossfades or other DJ transformations.

## YouTube adapter

`YoutubeDataApiProvider` remains discovery-only. It returns YouTube catalogue metadata and canonical YouTube links. It does not extract media URLs or send YouTube results to ExoPlayer. YouTube playback must use an approved YouTube player experience.

## Local playback protection

The local-file Track model, MusicRepository, local scanning, Offline Library and DJ engine are kept separate from Audius catalogue results. The existing Media3 service is reused only for normal remote playback. No changes are made to Automix transition logic or Dual Deck processing for Audius content.
