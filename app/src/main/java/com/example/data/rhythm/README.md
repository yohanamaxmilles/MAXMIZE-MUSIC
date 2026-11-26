# MAXMIZE MUSIC Rhythm Intelligence

This package is isolated from the existing DJ audio analysis engine.

## Current source files

- `RhythmAudioConfig.kt`: fixed input and model configuration.
- `RhythmFeatureExtractor.kt`: mono 22,050 Hz PCM to normalized [128, 345] log-Mel features.

## Preprocessing contract

| Setting | Value |
|---|---:|
| Sample rate | 22,050 Hz |
| Channels | Mono |
| Duration | 8 seconds |
| Input samples | 176,400 |
| FFT size | 2,048 |
| Hop length | 512 |
| Mel bands | 128 |
| Frames | 345 |
| Window | Periodic Hann |
| Centered STFT padding | 1,024 zero samples on each side |
| Mel scale | Slaney |
| Mel normalization | Slaney area normalization |
| Power | 2 |
| dB reference | Maximum value |
| dB range | -80 to 0 |
| Normalization | (dB + 80) / 80 |

## Important integration limits

1. The extractor expects audio already converted to mono 22,050 Hz PCM. Resampling has not been implemented here because it must be verified against the Colab reference. The training pipeline used librosa's high-quality SOXR resampler.
2. The TensorFlow Lite runtime and model asset have not yet been added. The repository's Gradle files were intentionally left unchanged.
3. The preliminary model predicts presence/absence for 25 drum and percussion classes over an eight-second segment. It does not return drum-hit timestamps.
4. BPM estimation and genre classification are separate systems. This model does not replace the current DJ audio analysis engine.
5. This source has not yet been built or validated on an Android device. Compare its output against the Android Test Reference package before enabling model inference.

## Dataset attribution

Training dataset: WaivOps EDM-HSE  
DOI: https://doi.org/10.5281/zenodo.13769544  
License: CC BY 4.0

The model package and any redistributed dataset-derived material must retain the dataset attribution and license notice.
