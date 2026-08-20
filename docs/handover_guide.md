# Project Handover & Setup Guide

This guide is designed for developers taking over the development of Nex V1. It outlines context, setup details, model configuration, and critical architectural areas.

---

## 1. Project Background & Context

Nex V1 is an offline-first, local AI chat application for Android. It demonstrates that high-performance, private, and personalized text and multimodal vision AI is achievable on consumer mobile hardware without internet access or API usage costs. It features local fact-extraction (Memory), sub-millisecond response context caching (KV cache recycling), and multimodal image reasoning via Qwen2.5-VL and `mtmd`.

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

The app supports 4 distinct model options:

| Model ID | Model Name | Base File (.gguf) | Vision Projector (mmproj) |
| :--- | :--- | :--- | :--- |
| `fast` | **Nex Fast** | `qwen2.5-0.5b-instruct-q4_k_m.gguf` | *None* |
| `pro` | **Nex Pro** | `qwen2.5-1.5b-instruct-q4_k_m.gguf` | *None* |
| `ultra` | **Nex Ultra** | `qwen2.5-3b-instruct-q4_k_m.gguf` | *None* |
| `vision` | **Nex Vision** | `Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf` | `mmproj-Qwen2.5-VL-3B-Instruct-f16.gguf` |

### Automatic & Manual Provisioning
- **In-App Download**: `ModelManager.java` supports automated downloading directly from HuggingFace repositories into app-scoped external storage (`/storage/emulated/0/Android/data/com.k7sunny.nexv1/files/models/`).
- **Manual ADB Push**:
  ```powershell
  adb push Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf /sdcard/Android/data/com.k7sunny.nexv1/files/models/
  adb push mmproj-Qwen2.5-VL-3B-Instruct-f16.gguf /sdcard/Android/data/com.k7sunny.nexv1/files/models/
  ```

---

## 4. Completed Architectural Safeguards

1. **JNI Exception Boundaries**: All native calls in `native-lib.cpp` are wrapped in `try { ... } catch (const std::exception& e)` with clean Java runtime exception propagation.
2. **Race Condition Synchronization**: `AIManager.chatHistory` operations use explicit `synchronized` blocks. `MainActivity` accesses `messageList` on the UI thread and re-locates items via `.indexOf(msg)` after async operations.
3. **Memory Filter Guards**: Reject AI self-referencing statements and prompt instruction leakage. Normalizes facts to third-person (`User ...`).
4. **Multimodal Mobile Safety**: Pre-scales images to patch-aligned 392px and sets `image_max_tokens = 256` to prevent kernel page allocation failures and swap thrashing.

---

## 5. Feature Roadmap

- [x] **Multimodal Model Support**: Full vision compatibility with Qwen2.5-VL 3B + mmproj using `mtmd`.
- [x] **Token-Based KV Cache Recycling**: Sub-millisecond prompt evaluation for multi-turn chats.
- [x] **Persistent Chat & Image History**: Room DB schema v4 with `image_uri`.
- [ ] **Retrieval-Augmented Generation (RAG)**: Localized semantic vector index using lightweight embeddings to parse user documents.
- [ ] **STT and TTS Integration**: Integrate Whisper.cpp and local text-to-speech for direct voice interaction.
- [ ] **NPU / GPU Acceleration**: Explore Vulkan / NNAPI backends for accelerated prompt decoding.
