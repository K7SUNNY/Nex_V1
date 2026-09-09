cmake_minimum_required(VERSION 3.22.1)

project("nexv1")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# Configure llama.cpp flags before adding
set(LLAMA_BUILD_COMMON ON CACHE INTERNAL "")
set(LLAMA_BUILD_EXAMPLES OFF CACHE INTERNAL "")
set(LLAMA_BUILD_TESTS OFF CACHE INTERNAL "")
set(LLAMA_BUILD_SERVER OFF CACHE INTERNAL "")
set(LLAMA_BUILD_MTMD ON CACHE INTERNAL "")
set(GGML_OPENMP ON CACHE INTERNAL "")
set(GGML_VULKAN ON CACHE INTERNAL "")

# Set glslc location from Android NDK if available
if(ANDROID_NDK)
    find_program(Vulkan_GLSLC_EXECUTABLE
        NAMES glslc glslc.exe
        PATHS "${ANDROID_NDK}/shader-tools/windows-x86_64"
              "${ANDROID_NDK}/shader-tools/linux-x86_64"
              "${ANDROID_NDK}/shader-tools/darwin-x86_64"
        NO_DEFAULT_PATH
    )
    if(Vulkan_GLSLC_EXECUTABLE)
        set(Vulkan_GLSLC_EXECUTABLE "${Vulkan_GLSLC_EXECUTABLE}" CACHE FILEPATH "Path to glslc" FORCE)
    endif()
endif()

# Automatically fetch dependencies if not present locally
include(FetchContent)

# Fetch Vulkan-Headers (provides vulkan/vulkan.hpp C++ bindings)
FetchContent_Declare(
    vulkan_headers
    GIT_REPOSITORY https://github.com/KhronosGroup/Vulkan-Headers.git
    GIT_TAG        main
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(vulkan_headers)

# Fetch SPIRV-Headers required by llama.cpp Vulkan backend
FetchContent_Declare(
    spirv_headers
    GIT_REPOSITORY https://github.com/KhronosGroup/SPIRV-Headers.git
    GIT_TAG        main
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(spirv_headers)

# Make Vulkan and SPIRV headers available to all targets and subdirectories
include_directories(
    ${vulkan_headers_SOURCE_DIR}/include
    ${spirv_headers_SOURCE_DIR}/include
)

# Generate SPIRV-HeadersConfig.cmake so find_package(SPIRV-Headers CONFIG REQUIRED) succeeds
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/SPIRV-HeadersConfig.cmake"
"if(NOT TARGET SPIRV-Headers::SPIRV-Headers)
    add_library(SPIRV-Headers::SPIRV-Headers INTERFACE IMPORTED)
    set_target_properties(SPIRV-Headers::SPIRV-Headers PROPERTIES
        INTERFACE_INCLUDE_DIRECTORIES \"${spirv_headers_SOURCE_DIR}/include\"
    )
endif()
set(SPIRV-Headers_FOUND TRUE)
set(SPIRV-Headers_VERSION \"1.5.5\")
"
)
set(SPIRV-Headers_DIR "${CMAKE_CURRENT_BINARY_DIR}" CACHE PATH "" FORCE)
list(APPEND CMAKE_PREFIX_PATH "${CMAKE_CURRENT_BINARY_DIR}")

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
    ${llama_cpp_SOURCE_DIR}/tools/mtmd
    ${spirv_headers_SOURCE_DIR}/include
)

find_library(
    log-lib
    log
)

find_library(
    vulkan-lib
    vulkan
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
    mtmd
    ${log-lib}
    ${OPENMP_LIBRARIES}
    ${vulkan-lib}
)
