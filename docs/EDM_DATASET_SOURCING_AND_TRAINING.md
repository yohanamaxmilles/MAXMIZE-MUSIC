# MAXMIZE MUSIC EDM Dataset Sourcing and Training Plan

## Commercial-release rule

MAXMIZE MUSIC is intended for commercial distribution. A track being free to download, royalty-free, or usable in a creator video does not automatically grant permission to use it for machine-learning training.

Every audio file must have a documented license that permits the intended ML training, evaluation, storage, preprocessing, and model distribution. When those rights are ambiguous, exclude the file or obtain written permission from the rights holder.

## Explicit exclusions

- NoCopyrightSounds (NCS): exclude. Its current usage policy/terms prohibit using NCS music to train, test, or improve AI/ML systems. The creator-video permission is not an ML-training licence.
- MTG-Jamendo: exclude from commercial model training unless Jamendo grants written commercial authorization. The dataset page states the dataset is for non-commercial research and academic use.
- Any CC BY-NC, CC BY-NC-SA, CC BY-NC-ND, research-only, educational-only, or unclear-rights material: exclude from the commercial training corpus.
- CC BY-ND: exclude from the initial corpus because model-training transformations and derived artifacts need a separate rights review.
- YouTube, Spotify, SoundCloud streams, ripped uploads, and user uploads without explicit training rights: exclude.

## Candidate sources for audit

### 1. SoundSafari/CC0-1.0-Music

Repository: https://github.com/SoundSafari/CC0-1.0-Music

The repository describes approximately 7,000 songs / 40 GB as CC0 or public-domain music. It aggregates files from several upstream sources and warns that files may vary in format and that incorrect CC0 classifications can be reported for takedown.

This is a candidate source, not an automatically approved training corpus. Before ingestion:
- inspect the source folder and upstream track page;
- verify the individual track is genuinely CC0 or public domain;
- retain source URL, creator, title, license URL, retrieval date, file hash, and verification notes;
- exclude any track whose provenance cannot be established.

### 2. FreeTZAN

Dataset: https://huggingface.co/datasets/gclef-cmu/freetzan

The dataset describes 1,200 30-second clips in 10 broad genres with artist-independent train/validation/test splits. Audio is sourced from FMA under per-track Creative Commons, CC0, or Public Domain Mark terms.

Use only after checking the license for each underlying track. This is suitable as a small broad-genre baseline and pipeline smoke test, not as sufficient evidence for fine-grained EDM subgenre accuracy.

### 3. Free Music Archive

License guide: https://freemusicarchive.org/License_Guide

Use only individually verified CC0 tracks or tracks whose CC BY terms and rights-holder permissions clearly cover commercial ML training and distribution of the resulting model. Preserve attribution metadata. Do not treat the whole archive as one licence.

## Target EDM labels

- Progressive House
- Future House
- Deep House
- Tech House
- Afro House
- Techno
- Melodic Techno
- Peak-Time Techno
- Hard Techno
- Minimal Techno
- Midtempo Bass
- Melodic Dubstep
- Dubstep
- Future Bass
- Drum & Bass
- Bass House
- Trance
- Psytrance
- Amapiano
- 3-Step
- Afro Tech

Labels must be confirmed by reliable metadata or human review. Do not automatically infer fine-grained labels from BPM alone.

## Dataset manifest

Maintain one row per recording with:
- stable track_id and SHA-256;
- local audio path or approved source reference;
- source provider and original track URL;
- title and artist when known;
- exact license identifier and license URL;
- rights-holder / provenance evidence;
- target genre and subgenre labels;
- label source and reviewer;
- duration, sample rate, channels, and file format;
- split assignment.

Never commit copyrighted audio files or large raw datasets to the app repository. Store data in a controlled training workspace and keep only scripts, manifests that are safe to publish, and model cards in Git.

## Training and evaluation protocol

1. Audit licenses and provenance before downloading or processing.
2. Decode and validate audio, remove corrupt files, duplicates, and near-duplicates.
3. Use artist- and track-separated splits. Never split windows from the same track across train and test.
4. Use verified metadata as initial labels, then human-review ambiguous or fine-grained labels.
5. Balance classes where possible and record class counts. Do not fabricate missing classes.
6. Include hard-negative pairs: Midtempo Bass vs Drum & Bass, Techno vs Drum & Bass, Amapiano vs Pop/Afrobeats, and 3-Step vs Amapiano/Afro House.
7. Train a broad electronic-family model first, then a specialist EDM subgenre model only when enough verified examples exist per class.
8. Report per-class precision, recall, F1, confusion matrix, calibration, abstention coverage, and artist-disjoint test results.
9. Compare model size, inference latency, memory use, battery impact, and quantized accuracy on representative Android devices.
10. Ship only if the agreed per-class thresholds are met. Return Unknown when evidence is weak.

## Current implementation status

The Android-side taxonomy and inference contracts are in the app. This document defines the data gate and reproducible training rules. No audio has been approved or ingested by this document alone, and no trained weights are included yet.
