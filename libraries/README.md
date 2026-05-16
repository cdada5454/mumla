# Native Audio Dependencies

The Humla input pipeline expects Android-native builds of:

- Humla, the Mumble protocol and audio library used by the app.
- Oboe, for Android native input stream management.
- WebRTC AudioProcessing (APM), for AEC and AGC.
- RNNoise, for denoise.

These dependencies must be kept as source in the repository and are built by
CMake as part of the Android build. Do not commit prebuilt `.so`, `.a`, `.aar`,
or `.jar` copies of these native libraries.

Default source layout:

```text
libraries/
  humla/
    VENDORED_VERSION.txt
  webrtc-apm/
    modules/audio_processing/include/audio_processing.h
    CMakeLists.txt
  oboe/
    include/oboe/Oboe.h
    CMakeLists.txt
  rnnoise/
    include/rnnoise.h
    CMakeLists.txt
```

Supported ABIs are the app ABIs: `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`.

You can also point CMake at another location:

```text
-DHUMLA_WEBRTC_APM_ROOT=/path/to/webrtc-apm
-DHUMLA_OBOE_ROOT=/path/to/oboe
-DHUMLA_RNNOISE_ROOT=/path/to/rnnoise
```

Opus is built from the source under `libraries/humla/src/main/jni/opus` for the
`humla_audioinput` encoder path. The legacy JavaCPP codec libraries
`jniopus`, `jnispeex`, `jnicelt7`, and `jnicelt11` are generated from
`libraries/humla/src/main/jni` by `ndk-build`; their `.so` outputs are ignored
build artifacts, not source dependencies.

The Oboe input stream feeds `ThirdPartyAudioPipeline` in this order:

```text
microphone PCM -> WebRTC AEC -> RNNoise -> WebRTC AGC -> encoder
```
