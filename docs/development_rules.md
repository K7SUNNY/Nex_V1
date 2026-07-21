# Development Handbook & Important Rules

This document outlines structural rules, threading constraints, environment guardrails, and conventions that must be adhered to when working on the Nex V1 codebase.

---

## 1. IDE Switch Guardrails (JDK Path Stability)

Nex V1 is actively developed in both Android Studio and VS Code. To prevent build errors like `jlink.exe does not exist` or Java SDK drift, follow these strict path configurations.

### The Canonical JDK
Use only the Android Studio JetBrains Runtime (JBR) for all gradle syncs and compilation:
```text
C:\Program Files\Android\Android Studio\jbr
```
Do **NOT** use extension-specific runtimes (such as VS Code's `redhat.java` default extensions path).

### Mandatory Project File Settings
The following configuration parameters must remain stable:
- **`.idea/gradle.xml`**: Must keep `gradleJvm = jbr-21`
- **`.idea/misc.xml`**: Must keep `project-jdk-name = jbr-21`
- **`.vscode/settings.json`**: Must keep the following paths pinned to the canonical JBR:
  - `java.jdt.ls.java.home`
  - `java.import.gradle.java.home`

### Switching IDEs Procedure
Each time you transition between VS Code and Android Studio:
1. Stop any running Gradle daemons in a terminal:
   ```powershell
   ./gradlew --stop
   ```
2. Close the current IDE completely.
3. Open the target IDE.
4. Verify the active JVM path by running:
   ```powershell
   ./gradlew -version
   ```

---

## 2. Thread Safety and Architecture Rules

Nex V1 coordinates native code, background database commits, and UI updates. Multi-threaded race conditions can easily corrupt state or trigger crashes.

### Thread Confinement Rules
- **UI Main Thread**: Responsible for updating lists, adapter notifications, text views, and view visibility. Under no circumstances should database queries, model file checks, or JNI calls occur on this thread.
- **`dbExecutor` (Single-Threaded)**: Dedicated to database reads and writes. All Room operations must be dispatched to this thread.
- **Inference Thread (`nex-inference-thread`)**: A high-priority worker thread created in `AIManager`. All native JNI calls (`loadModelNative`, `runInferenceNative`) must execute on this thread.

### Race Condition Guardrails
- **Syncing list indices**: Async operations (like memory extraction or auto-title generation) take time. Do not trust list indexes across async calls. Re-locate items by reference using `.indexOf(object)` on the UI thread when processing callbacks.
- **Shared States**: Always mark indicators (e.g. `isModelLoaded`) as `volatile` or synchronize access blocks when variable states are mutated across thread boundaries.

---

## 3. NDK and C++ Coding Guidelines

When editing the native layer in `native-lib.cpp`:
- **Mutex Locks**: Protect access to global variables (`g_model`, `g_ctx`) using `std::lock_guard<std::mutex>` or `std::unique_lock<std::mutex>` to guarantee thread safety during model loads or cancellation calls.
- **Local JNI References**: JNI limits local references. Use `env->DeleteLocalRef(...)` in loops to clean up strings or objects transferred from JNI callback invocations.
- **Memory Releases**: Always release native character pointers via `env->ReleaseStringUTFChars` immediately after using native strings.
