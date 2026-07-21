# Project Handover & Setup Guide

This guide is designed for developers taking over the development of Nex V1. It outlines context, setup details, model configuration, and critical architectural areas requiring attention.

---

## 1. Project Background & Context

Nex V1 is an offline-first, local AI chat application for Android. It was created to demonstrate that high-performance, private, and personalized AI is achievable on consumer mobile hardware without internet access or API usage costs. It succeeds the **Spark AI** project by introducing a local fact-extraction database (Memory) and sub-millisecond response context caching (KV cache recycling).

---

## 2. Environment Setup

### Required Tools
1. **Android Studio**: Ladybug (2024.2.1) or newer.
2. **Android NDK**: Side-by-side NDK (version 26.x or newer recommended) installed via SDK Manager.
3. **CMake**: Version 3.22.1 or newer.
4. **JDK**: Version 21 (Use the JBR JRE embedded in Android Studio).

### Step-by-Step Setup
1. Clone this repository.
2. Verify SDK and NDK configurations in Android Studio (`File > Project Structure > SDK Location`).
3. Clean build the gradle wrapper:
   ```powershell
   ./gradlew clean
   ```
4. Build the application:
   ```powershell
   ./gradlew :app:assembleDebug
   ```

---

## 3. Model Provisioning

The application does not embed the large language model (`.gguf` file) inside the APK to keep download sizes reasonable. The model must be transferred to the device manually.

### Supported Model Archetypes
- **Recommended base model**: `Qwen2.5-0.5B-Instruct` or `Qwen2.5-1.5B-Instruct`
- **Format**: `.gguf`
- **Quantization**: `Q4_K_M` (Balanced performance-to-size ratio)

### Loading Models
1. Upload the GGUF model to your Android device's storage (e.g., using `adb push` or device file explorer).
   ```powershell
   adb push qwen2.5-0.5b-instruct-q4_k_m.gguf /sdcard/Download/
   ```
2. Launch the Nex V1 application on the device.
3. In the setup menu or Settings, select the local file path to load the GGUF model.

---

## 4. Key Open Tasks & Technical Debt

The application is functional, but there are several structural and concurrency vulnerabilities (noted in the initial `DEBUG_REPORT.md`) that need to be resolved.

### A. JNI Crash Prevention & Exception Boundaries
* **Issue**: The current bridge in `native-lib.cpp` directly invokes `llama.cpp` APIs. If context overflows or memory limit triggers (OOM), C++ signals will abort the application process, bypassing Java-level try-catch logs.
* **Handover Task**: Wrap C++ calls in exception boundaries, establish JNI utility handlers, and return native failure codes back to `AIManager.java`.

### B. Race Condition Synchronization
* **Issue**: `AIManager.chatHistory` (ArrayList) and `MainActivity.messageList` are modified across UI and thread pools without robust locks, risking `ConcurrentModificationException`.
* **Handover Task**: Introduce synchronized wrappers (e.g., `Collections.synchronizedList`) or process all mutations through a single-thread handler loop.

### C. Database Optimizations
* **Issue**: `MemoryDao.replaceMemories` currently performs a destructive write (deletes all rows and inserts all memories) which is inefficient as memory counts grow.
* **Handover Task**: Implement UPSERT (insert or ignore) strategies in DAOs to keep persistence atomic and lightweight.

---

## 5. Feature Roadmap

- [ ] **Multimodal Model Support**: Add vision compatibility to parse image attachments via LLaVA-style GGUF configurations.
- [ ] **Retrieval-Augmented Generation (RAG)**: Create a localized semantic vector index using a lightweight embedding model to parse user documents.
- [ ] **STT and TTS Integration**: Integrate Whispers.cpp and local text-to-speech for direct voice interaction.
