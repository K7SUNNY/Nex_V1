cmake_minimum_required(VERSION 3.22.1)

project("nexv1")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# Configure llama.cpp flags before adding
set(LLAMA_BUILD_COMMON ON CACHE INTERNAL "")
set(LLAMA_BUILD_EXAMPLES OFF CACHE INTERNAL "")
set(LLAMA_BUILD_TESTS OFF CACHE INTERNAL "")
set(LLAMA_BUILD_SERVER OFF CACHE INTERNAL "")
set(GGML_OPENMP ON CACHE INTERNAL "")

# Automatically fetch llama.cpp if not present locally
include(FetchContent)
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
    GIT_TAG        master
    GIT_SHALLOW    TRUE
    GIT_CONFIG     core.longpaths=true
)
FetchContent_MakeAvailable(llama_cpp)

add_library(
    nexv1
    SHARED
    native-lib.cpp
)

# Use the source directory from FetchContent
target_include_directories(
    nexv1
    PRIVATE
    ${llama_cpp_SOURCE_DIR}
    ${llama_cpp_SOURCE_DIR}/common
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/ggml/include
)

find_library(
    log-lib
    log
)

# OpenMP support
find_package(OpenMP)
if(OpenMP_CXX_FOUND)
    set(OPENMP_LIBRARIES OpenMP::OpenMP_CXX)
endif()

target_link_libraries(
    nexv1
    llama
    llama-common
    ${log-lib}
    ${OPENMP_LIBRARIES}
)
