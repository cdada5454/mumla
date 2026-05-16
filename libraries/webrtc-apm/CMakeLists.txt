cmake_minimum_required(VERSION 3.22.1)

project(webrtc_audio_processing_compat)

file(GLOB_RECURSE WEBRTC_APM_SOURCES
    CONFIGURE_DEPENDS
    "${CMAKE_CURRENT_LIST_DIR}/webrtc/*.c"
    "${CMAKE_CURRENT_LIST_DIR}/webrtc/*.cc"
)

list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*(_neon|_mips)\\.(c|cc)$")
if (NOT ANDROID_ABI MATCHES "^x86")
    list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*(_sse|_sse2)\\.(c|cc)$")
endif()
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*(_arm|_armv7)\\.S$")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*/source/.*_win\\.cc$")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*/source/.*_win\\.h$")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*/source/rw_lock_generic\\.cc$")
list(FILTER WEBRTC_APM_SOURCES EXCLUDE REGEX ".*/source/condition_variable.*\\.cc$")

add_library(webrtc_audio_processing SHARED ${WEBRTC_APM_SOURCES})

target_compile_features(webrtc_audio_processing PRIVATE cxx_std_11)

target_compile_definitions(webrtc_audio_processing PUBLIC
    WEBRTC_AUDIO_PROCESSING_ONLY_BUILD
    WEBRTC_ANDROID
    WEBRTC_LINUX
    WEBRTC_POSIX
    WEBRTC_THREAD_RR
    WEBRTC_CLOCK_TYPE_REALTIME
    WEBRTC_NS_FLOAT
    NDEBUG
)

target_include_directories(webrtc_audio_processing PUBLIC
    "${CMAKE_CURRENT_LIST_DIR}"
)

target_link_libraries(webrtc_audio_processing
    log
)

if (ANDROID_ABI MATCHES "^x86")
    foreach(source_file IN LISTS WEBRTC_APM_SOURCES)
        if (source_file MATCHES ".*(_sse|_sse2)\\.(c|cc)$")
            set_source_files_properties("${source_file}" PROPERTIES COMPILE_OPTIONS "-msse2")
        endif()
    endforeach()
endif()
