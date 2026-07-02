# SoundCardFX

Aplikasi Android real-time audio processing (echo, reverb, 3-band EQ) untuk streaming,
bertujuan menggantikan sound card USB fisik (V8/K5).

## Stack
- Kotlin, AGP 8.7.2, Gradle 8.9, JVM 17
- Audio engine: `AudioRecord` -> DSP chain (Equalizer -> Echo -> Reverb) -> `AudioTrack`, jalan
  di foreground service (`AudioEngineService`) supaya tetap hidup saat app di-background.

## Struktur
```
app/src/main/java/com/endlan/soundcardfx/
  MainActivity.kt              # UI: toggle start/stop + slider tiap efek
  audio/AudioEngine.kt         # capture -> proses -> playback loop
  audio/AudioEngineService.kt  # foreground service pembungkus AudioEngine
  audio/effects/Equalizer.kt   # 3-band EQ (biquad low-shelf/peaking/high-shelf)
  audio/effects/Echo.kt        # delay line + feedback
  audio/effects/Reverb.kt      # Schroeder reverb (comb + allpass filter)
```

## Build
Wrapper jar sengaja tidak di-commit (di-generate otomatis oleh CI / Android Studio).
- **Lokal (Android Studio)**: buka folder ini, Android Studio otomatis generate wrapper.
- **Lokal (command line)**: `gradle wrapper --gradle-version 8.9 --distribution-type bin` dulu,
  baru `./gradlew assembleDebug`.
- **CI**: otomatis lewat GitHub Actions (`.github/workflows/build.yml`), hasil APK ada di tab Actions > Artifacts.

## Status
- [x] Skeleton project + build pipeline
- [x] Audio engine dasar (capture/playback + EQ/Echo/Reverb)
- [ ] Testing di device asli (latency, kualitas audio)
- [ ] Routing output ke virtual audio device / USB (buat dipakai OBS dkk)
- [ ] UI polish
