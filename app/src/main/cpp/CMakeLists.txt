# Builds whisper.cpp (open-source speech-to-text) and a small JNI bridge for the app.
cmake_minimum_required(VERSION 3.22.1)
project(musictracker_whisper C CXX)

include(FetchContent)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
if (ANDROID AND ANDROID_ABI STREQUAL "arm64-v8a")
    # Nothing Phone (2a) CPU supports dot-product and half-float instructions: much faster.
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
endif ()

FetchContent_Declare(
    whisper
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG v1.9.5
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(whisper)

add_library(whisperjni SHARED whisper_jni.cpp)
target_compile_features(whisperjni PRIVATE cxx_std_17)
target_link_libraries(whisperjni PRIVATE whisper)
if (ANDROID)
    target_link_libraries(whisperjni PRIVATE log)
endif ()
