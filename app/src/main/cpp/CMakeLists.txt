cmake_minimum_required(VERSION 3.22.1)

project("nexv1")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# Configure llama.cpp flags before adding subdirectory
set(LLAMA_BUILD_COMMON ON CACHE INTERNAL "")
set(LLAMA_BUILD_EXAMPLES OFF CACHE INTERNAL "")
set(LLAMA_BUILD_TESTS OFF CACHE INTERNAL "")
set(LLAMA_BUILD_SERVER OFF CACHE INTERNAL "")

# Add llama.cpp as a subdirectory
add_subdirectory(llama.cpp)

add_library(
    nexv1
    SHARED
    native-lib.cpp
)

target_include_directories(
    nexv1
    PRIVATE
    llama.cpp
    llama.cpp/common
    llama.cpp/include
    llama.cpp/ggml/include
)

find_library(
    log-lib
    log
)

target_link_libraries(
    nexv1
    llama
    llama-common
    ${log-lib}
)