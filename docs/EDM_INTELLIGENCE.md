# MAXMIZE EDM Intelligence

## Scope

This repository now defines the product-owned EDM taxonomy, normalized audio feature contract, prediction confidence contract, and an inference boundary. It does not yet contain trained model weights or a production classifier.

## Target labels

The first model targets these classes:

- Progressive House, Future House, Deep House, Tech House, Afro House
- Techno, Melodic Techno, Peak-Time Techno, Hard Techno, Minimal Techno
- Midtempo Bass, Melodic Dubstep, Dubstep, Future Bass, Drum & Bass, Bass House
- Trance, Psytrance
- Amapiano, 3-Step, Afro Tech

The taxonomy deliberately separates Midtempo Bass from Drum & Bass and keeps Amapiano and 3-Step outside generic Pop labels.

## Training requirements

1. Use only audio datasets and individual recordings with documented commercial-use rights.
2. Store provenance for every sample: source, license, artist/track identifier, label, and permitted use.
3. Remove duplicates and near-duplicates before splitting.
4. Split by artist and track, not by random audio window, to prevent leakage between train and evaluation sets.
5. Include hard negatives and borderline examples, especially Midtempo Bass vs Drum & Bass, Techno vs Drum & Bass, and Amapiano/3-Step vs Pop/Afrobeats.
6. Report per-class precision, recall, F1, confusion matrix, calibration error, and abstention coverage.
7. Do not ship a model unless each class meets agreed minimum evaluation thresholds on a held-out set.
8. Calibrate confidence. The Android app must return Unknown when confidence is below threshold or evidence is ambiguous.
9. Export a mobile model only after comparing quantized accuracy and latency on representative Android devices.

## Runtime design

The Android pipeline decodes audio into bounded windows, computes the feature vector defined in `EdmAudioIntelligence.kt`, batches window predictions, and aggregates them with confidence calibration. The classifier interface is model-runtime agnostic so a commercially licensed model or an internally trained model can be integrated without changing the DJ decision engine.

## Current status

- Taxonomy and feature contracts: added.
- Prediction confidence and abstention contract: added.
- Trained weights: not yet available.
- Real audio validation: pending licensed dataset.
- On-device performance and battery testing: pending.
