# MAXMIZE MUSIC Rhythm Intelligence

This package is isolated from the existing DJ audio analysis engine.

## Source files

- `RhythmAudioConfig.kt`: fixed input and model configuration.
- `RhythmFeatureExtractor.kt`: mono 22,050 Hz PCM to normalized [128, 345] log-Mel features.
- `RhythmInferenceEngine.kt`: TensorFlow Lite inference for the 25-output model.
- `RhythmAnalyzer.kt`: end-to-end analysis facade.

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

## Integration status and limits

1. The extractor expects mono PCM already converted to 22,050 Hz. Audio decoding, channel mixing and high-quality resampling are not implemented in this package yet. The training pipeline used librosa with SOXR HQ resampling. Validate Android preprocessing against the supplied Android Test Reference before enabling inference.
2. The TensorFlow Lite runtime dependency is declared in the app Gradle files. The model binary must be placed at `app/src/main/assets/models/rhythm_instrument_model_float32.tflite`.
3. The inference engine requires the exact 25 class labels in the same order as the training output. Do not guess or reorder these labels.
4. The model predicts instrument presence over an eight-second segment. It does not return individual drum-hit timestamps.
5. BPM estimation and genre classification are separate systems. This model does not replace the current DJ audio analysis engine.
6. Build and device validation are still required before production use. No claim of successful Android compilation or device inference should be made until those tests pass.

## Dataset attribution

Training dataset: WaivOps EDM-HSE  
DOI: https://doi.org/10.5281/zenodo.13769544  
License: CC BY 4.0

Retain the dataset attribution and license notice when redistributing dataset-derived materials.
