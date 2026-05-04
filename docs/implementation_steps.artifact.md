# Implementation Plan: Phase 6 - llama.cpp Integration

This phase integrates the `llama.cpp` inference engine into the project.

## Phase 6: Integrate llama.cpp
**Goal:** Build `llama.cpp` source within the Android project and link it to the native library.

### Tasks
- [ ] Add `llama.cpp` source as a subdirectory in `app/src/main/cpp`. (Done via git clone)
- [ ] Update `app/src/main/cpp/CMakeLists.txt` to:
    - Include `llama.cpp` subdirectory.
    - Set necessary flags for Android (OpenMP, ARM optimizations).
    - Link `llama` and `llama-common` libraries to `nexv1`.
    - Add include directories for `llama.cpp`.
- [ ] Update `app/build.gradle.kts` to pass CMake arguments (`GGML_OPENMP`, etc.).
- [ ] Verify that the project compiles with the engine included.

---

## Verification Plan

### Phase 6 Verification
1. **Gradle Sync:** Ensure the project syncs successfully with the new CMake structure.
2. **Build:** Perform a full build. The first build will take significantly longer as it compiles `llama.cpp`.
3. **Runtime Check:** Ensure the app still launches and the native bridge (from Phase 5) still works.
    - **How to perform:** I will check Logcat for `Native bridge test: Hello from C++ (NDK)`. If it's still there, the integration didn't break existing functionality and linking was successful.
