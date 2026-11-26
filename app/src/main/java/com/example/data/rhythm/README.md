# MAXMIZE MUSIC Rhythm Intelligence

Rhythm Intelligence is an isolated AI drum-instrument presence analyzer inside DJ Mode. It does not replace or change the existing DJ BPM, key, energy, or Automix analysis engine.

## Source files

- `RhythmAudioConfig.kt`: model input and preprocessing constants.
- `RhythmFeatureExtractor.kt`: mono 22,050 Hz PCM to normalized [128, 345] log-Mel features.
- `RhythmInstrumentLabels.kt`: the 25 model output labels in training order.
- `RhythmInferenceEngine.kt`: TensorFlow Lite inference.
- `RhythmAnalyzer.kt`: feature extraction plus model inference.
- `RhythmAnalyzerFactory.kt`: creates the analyzer with the fixed label order.
- `RhythmAudioDecoder.kt`: decodes the first eight seconds of a selected audio Uri, downmixes to mono and resamples to 22,050 Hz using linear interpolation.
- `RhythmIntelligenceSection.kt`: DJ Mode user interface for selecting a file and viewing predictions.

## How to use

1. Open DJ Mode.
2. Select the Rhythm AI tab.
3. Choose an audio file.
4. Wait for decoding and analysis.
5. Review the 25 instrument probabilities and which outputs pass the 0.5 threshold.

The analyzer processes only the first eight seconds of the selected file. It does not alter playback or the audio file.

## Model input contract

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

## Validation status and limitations

1. The training pipeline used librosa with SOXR HQ resampling. The Android file decoder currently uses linear interpolation. Android features have not yet been proven numerically equivalent to the training pipeline. Treat predictions from arbitrary files as experimental until the supplied Android Test Reference has been used to compare the Android feature tensor against the Colab reference.
2. The model was trained and evaluated on eight-second generated EDM drum loops. Performance on full commercial tracks and other audio styles is not established.
3. The model predicts whether each instrument is present somewhere in the eight-second segment. It does not locate individual drum-hit timestamps, estimate BPM, or classify genre.
4. Rare classes have weak test performance. Do not treat every class output as equally reliable.
5. TensorFlow Lite float32 is the primary model. The Android build and physical-device inference must pass before production release.
6. The model binary is expected at `app/src/main/assets/models/rhythm_instrument_model_float32.tflite`.

## Dataset attribution

Training dataset: WaivOps EDM-HSE  
DOI: https://doi.org/10.5281/zenodo.13769544  
License: CC BY 4.0

Retain the dataset attribution and license notice when redistributing dataset-derived materials.
