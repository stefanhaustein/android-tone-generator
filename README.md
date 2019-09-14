# Android Tone Generator

A simple single-file (<300LOC) tone generator library for Android >= 21.

Minimal example playing a middle A (440Hz) tone for 1000ms:

```
new ToneGenerator().play(440, 1000);
```

The API works by generating a single wave and then filling a minimal sample with copies. This is then looped for the desired time. If no length is provided, the tone will play until end() is called on the object returned from play. The ToneGenerator API provides means to set the envelope and waveform.

The Android 21 AudioTrack API provides a call to control the volume, allowing to support a sound envelope that is not encoded int the data. Without being able to fade the sound in and out, there may be audible artifacts at the beginning and end of the tone.
