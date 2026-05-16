cmake_minimum_required(VERSION 3.22.1)

project(humla_audioinput)

set(HUMLA_OBOE_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../oboe" CACHE PATH "Oboe source root")
set(HUMLA_WEBRTC_APM_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../webrtc-apm" CACHE PATH "WebRTC AudioProcessing checkout or package root")
set(HUMLA_RNNOISE_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../rnnoise" CACHE PATH "RNNoise checkout or package root")
set(HUMLA_LEGACY_JNI_ROOT "${CMAKE_CURRENT_LIST_DIR}/../jni" CACHE PATH "Legacy JNI source root")
set(HUMLA_OPUS_ROOT "${HUMLA_LEGACY_JNI_ROOT}/opus" CACHE PATH "Opus source root")

if (EXISTS "${HUMLA_OBOE_ROOT}/CMakeLists.txt")
    add_subdirectory("${HUMLA_OBOE_ROOT}" "${CMAKE_BINARY_DIR}/oboe")
    target_compile_options(oboe PRIVATE
        -Wno-error
        -Wno-unused-parameter
        -Wno-sign-compare
    )
endif()

find_path(HUMLA_WEBRTC_APM_INCLUDE_DIR
    NAMES modules/audio_processing/include/audio_processing.h
    PATHS "${HUMLA_WEBRTC_APM_ROOT}"
    NO_DEFAULT_PATH
    NO_CMAKE_FIND_ROOT_PATH
)

if (HUMLA_WEBRTC_APM_INCLUDE_DIR AND EXISTS "${HUMLA_WEBRTC_APM_ROOT}/CMakeLists.txt")
    add_subdirectory("${HUMLA_WEBRTC_APM_ROOT}" "${CMAKE_BINARY_DIR}/webrtc-apm")
    set(HUMLA_WEBRTC_APM_LIBRARY webrtc_audio_processing)
endif()

find_path(HUMLA_RNNOISE_INCLUDE_DIR
    NAMES rnnoise.h
    PATHS
        "${HUMLA_RNNOISE_ROOT}/include"
        "${HUMLA_RNNOISE_ROOT}/src"
        "${HUMLA_RNNOISE_ROOT}"
    NO_DEFAULT_PATH
    NO_CMAKE_FIND_ROOT_PATH
)

if (HUMLA_RNNOISE_INCLUDE_DIR AND EXISTS "${HUMLA_RNNOISE_ROOT}/CMakeLists.txt")
    add_subdirectory("${HUMLA_RNNOISE_ROOT}" "${CMAKE_BINARY_DIR}/rnnoise")
    set(HUMLA_RNNOISE_LIBRARY rnnoise)
endif()

if (NOT HUMLA_WEBRTC_APM_INCLUDE_DIR OR NOT HUMLA_WEBRTC_APM_LIBRARY)
    message(FATAL_ERROR
        "WebRTC AudioProcessing source was not found. "
        "Keep the source checkout under libraries/webrtc-apm, or pass "
        "-DHUMLA_WEBRTC_APM_ROOT=/path/to/webrtc-apm.")
endif()

if (NOT TARGET oboe)
    message(FATAL_ERROR
        "Oboe source was not found. "
        "Keep the source checkout under libraries/oboe, or pass "
        "-DHUMLA_OBOE_ROOT=/path/to/oboe.")
endif()

if (NOT HUMLA_RNNOISE_INCLUDE_DIR OR NOT HUMLA_RNNOISE_LIBRARY)
    message(FATAL_ERROR
        "RNNoise source was not found. "
        "Keep the source checkout under libraries/rnnoise, or pass "
        "-DHUMLA_RNNOISE_ROOT=/path/to/rnnoise.")
endif()

if (NOT EXISTS "${HUMLA_OPUS_ROOT}/include/opus.h")
    message(FATAL_ERROR
        "Opus source was not found. "
        "Keep the source checkout under libraries/humla/src/main/jni/opus, or pass "
        "-DHUMLA_OPUS_ROOT=/path/to/opus.")
endif()

file(GLOB HUMLA_OPUS_CELT_SOURCES CONFIGURE_DEPENDS
    "${HUMLA_OPUS_ROOT}/celt/*.c"
)
list(FILTER HUMLA_OPUS_CELT_SOURCES EXCLUDE REGEX ".*/opus_custom_demo\\.c$")

file(GLOB HUMLA_OPUS_SILK_SOURCES CONFIGURE_DEPENDS
    "${HUMLA_OPUS_ROOT}/silk/*.c"
    "${HUMLA_OPUS_ROOT}/silk/fixed/*.c"
)

set(HUMLA_OPUS_SOURCES
    "${HUMLA_OPUS_ROOT}/src/analysis.c"
    "${HUMLA_OPUS_ROOT}/src/mlp.c"
    "${HUMLA_OPUS_ROOT}/src/mlp_data.c"
    "${HUMLA_OPUS_ROOT}/src/opus.c"
    "${HUMLA_OPUS_ROOT}/src/opus_decoder.c"
    "${HUMLA_OPUS_ROOT}/src/opus_encoder.c"
    "${HUMLA_OPUS_ROOT}/src/opus_multistream.c"
    "${HUMLA_OPUS_ROOT}/src/opus_multistream_decoder.c"
    "${HUMLA_OPUS_ROOT}/src/opus_multistream_encoder.c"
    "${HUMLA_OPUS_ROOT}/src/repacketizer.c"
    ${HUMLA_OPUS_CELT_SOURCES}
    ${HUMLA_OPUS_SILK_SOURCES}
)

add_library(humla_opus STATIC ${HUMLA_OPUS_SOURCES})

target_compile_definitions(humla_opus PUBLIC
    OPUS_BUILD
    VAR_ARRAYS
    FIXED_POINT
)

target_include_directories(humla_opus PUBLIC
    "${HUMLA_OPUS_ROOT}/include"
    "${HUMLA_OPUS_ROOT}/celt"
    "${HUMLA_OPUS_ROOT}/silk"
    "${HUMLA_OPUS_ROOT}/silk/fixed"
    "${HUMLA_OPUS_ROOT}/silk/float"
)

add_library(humla_audioinput SHARED
    audio_input_jni.cpp
    native_opus_encoder_jni.cpp
    third_party_audio_pipeline.cpp
)

target_compile_features(humla_audioinput PRIVATE cxx_std_17)

target_compile_definitions(humla_audioinput PRIVATE
    WEBRTC_AUDIO_PROCESSING_ONLY_BUILD
    WEBRTC_ANDROID
    WEBRTC_LINUX
    WEBRTC_POSIX
    WEBRTC_THREAD_RR
    WEBRTC_CLOCK_TYPE_REALTIME
    WEBRTC_NS_FLOAT
)

target_include_directories(humla_audioinput PRIVATE
    "${HUMLA_WEBRTC_APM_INCLUDE_DIR}"
    "${HUMLA_RNNOISE_INCLUDE_DIR}"
)

target_link_libraries(humla_audioinput
    android
    log
    oboe
    humla_opus
    "${HUMLA_WEBRTC_APM_LIBRARY}"
    "${HUMLA_RNNOISE_LIBRARY}"
)
