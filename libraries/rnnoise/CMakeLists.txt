cmake_minimum_required(VERSION 3.22.1)

project(rnnoise_compat C)

add_library(rnnoise SHARED
    src/denoise.c
    src/rnn.c
    src/rnn_data.c
    src/rnn_reader.c
    src/pitch.c
    src/kiss_fft.c
    src/celt_lpc.c
)

target_include_directories(rnnoise PUBLIC
    "${CMAKE_CURRENT_LIST_DIR}/include"
    "${CMAKE_CURRENT_LIST_DIR}/src"
)

target_link_libraries(rnnoise
    m
)
