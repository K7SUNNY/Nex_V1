# 🛠️ Nex V1 — Comprehensive Debug Remediation & Implementation Plan

**Document:** `docs/debug_remediation_plan.md`  
**Created:** 2026-08-30  
**Target:** Elimination of Crashes, UI Stutter, Native KV-Cache Desync, and Memory Leaks  
**Status:** Approved for Implementation  

---

## 🏗️ Architecture & Dependency Flow

The remediation is organized into **4 sequential phases**. Each phase builds on the stability guarantees of the previous phase to ensure zero regressions.

```mermaid
graph TD
    subgraph Phase 1: Critical Stability & Crash Elimination
        P1_1[1.1 NexApplication Singleton & AIManager Lifecycle] --> P1_2[1.2 Room DB Main-Thread Query Fix & Migrations]
        P1_2 --> P1_3[1.3 Native KV-Cache Invalidation on Cancel]
        P1_3 --> P1_4[1.4 ChatHistory Role Consistency on Cancel]
    end

    subgraph Phase 2: Performance, UI Jank & ANR Prevention
        P2_1[2.1 ChatAdapter Streaming Optimization & Debounce] --> P2_2[2.2 DocumentHelper Async Offloading]
        P2_2 --> P2_3[2.3 onResume Model Reload Elimination]
    end

    subgraph Phase 3: Integrity, Leaks & Build Reproducibility
        P3_1[3.1 SettingsActivity Cursor & Receiver Fix] --> P3_2[3.2 ModelManager Real SHA-256 Hashing]
        P3_2 --> P3_3[3.3 CMake llama.cpp Git Tag Pinning]
        P3_3 --> P3_4[3.4 Attachment & Cache Directory Pruning]
    end

    subgraph Phase 4: Polish, Theming & Clean Architecture
        P4_1[4.1 MemoryDao syncMemories Safety Guard] --> P4_2[4.2 Theme Color Tokens in Adapters]
        P4_2 --> P4_3[4.3 Logcat Privacy & PII Stripping]
    end

    Phase 1 --> Phase 2
    Phase 2 --> Phase 3
    Phase 3 --> Phase 4
```

---

## 📋 Phase-by-Phase Implementation Breakdown

### 🔴 Phase 1: Critical Stability & Lifecycle (P0 - Immediate Blockers)

#### 1.1 `NexApplication` Singleton & Global `AIManager` Lifecycle
- **Goal:** Prevent native use-after-free, SIGSEGV crashes, and backend teardown races during Activity lifecycle transitions (e.g., screen rotation, task switching).
- **Files to Modify / Create:**
  - `[NEW] app/src/main/java/com/k7sunny/nexv1/NexApplication.java`
  - `[MODIFY] app/src/main/AndroidManifest.xml`
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/MainActivity.java`
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/AIManager.java`
- **Implementation Steps:**
  1. Create `NexApplication extends Application` to hold the single process-wide instance of `AIManager`.
  2. Register `NexApplication` in `AndroidManifest.xml` via `android:name=".NexApplication"`.
  3. In `MainActivity.java`, retrieve `aiManager = ((NexApplication) getApplication()).getAiManager();` instead of creating `new AIManager()`.
  4. Remove `aiManager.release()` from `MainActivity.onDestroy()` (only release on explicit app termination).
  5. Add thread-safety synchronization to `initNative()` and `freeNative()` in `native-lib.cpp` to prevent overlapping backend calls.

#### 1.2 Room DB Migration Safety & Main-Thread Query Elimination
- **Goal:** Eliminate `IllegalStateException: Cannot access database on the main thread` and prevent upgrade crashes from legacy DB versions.
- **Files to Modify:**
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/HistoryManager.java`
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/MemoryManager.java`
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/data/NexDatabase.java`
- **Implementation Steps:**
  1. In `HistoryManager.java` and `MemoryManager.java`, move `migrateLegacyData(context)` off the constructor thread and execute it on `dbExecutor`.
  2. In `NexDatabase.java`, add `.fallbackToDestructiveMigration()` in the Room database builder to guarantee upgrades never crash if intermediate migrations are missing.
  3. Define `MIGRATION_1_2` if schema history is known, or ensure clean fallback.

#### 1.3 Native KV-Cache Synchronization on Cancel
- **Goal:** Eliminate attention corruption and truncated/garbage AI responses following a cancelled generation.
- **Files to Modify:**
  - `[MODIFY] app/src/main/cpp/native-lib.cpp`
- **Implementation Steps:**
  1. In `native-lib.cpp`, initialize `cache_valid = false` if cancellation is detected (`g_cancel_inference.load() == true`) inside the prompt evaluation chunk loop (`:502`) or token generation loop (`:548`).
  2. When `cache_valid` is `false`, clear `g_last_tokens` and execute `llama_memory_clear(llama_get_memory(g_ctx), true)` to reset KV slots cleanly.

#### 1.4 Chat History Role Continuity on Cancel
- **Goal:** Prevent consecutive `[User, User]` prompt structures from entering the inference pipeline.
- **Files to Modify:**
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/AIManager.java`
- **Implementation Steps:**
  1. If inference fails or is cancelled before the assistant turn is generated, rollback/remove the trailing user message from `chatHistory`.

---

### 🟠 Phase 2: Performance, UI Jank & ANR Prevention (P1 - High Priority)

#### 2.1 `ChatAdapter` Token Streaming Rendering Optimization
- **Goal:** Eliminate frame drops and O(n²) view re-inflation / syntax highlighting thrashing during token streaming.
- **Files to Modify:**
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/ChatAdapter.java`
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/MainActivity.java`
- **Implementation Steps:**
  1. In `ChatAdapter.java`, modify `bindAiStreaming`: only call `renderMessageBlocks` when the number or boundaries of code fences change.
  2. For regular text blocks without new code blocks, update the text directly in the active `TextView` rather than calling `removeAllViews()` and re-inflating layout items.
  3. In `MainActivity.java`, throttle/coalesce high-frequency token updates to the UI (e.g. update every ~30–50ms or on word boundaries) instead of firing `notifyItemChanged` on every single 1-character token.

#### 2.2 Asynchronous Document & PDF Parsing
- **Goal:** Eliminate UI thread freezes and ANRs when users attach large PDFs (up to 12MB).
- **Files to Modify:**
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/MainActivity.java`
- **Implementation Steps:**
  1. Move `DocumentHelper.parseDocument(this, uri)` from `handleSelectedDocument` on the main thread to `dbExecutor` / background thread.
  2. Display a lightweight progress/spinner in the document preview bar while parsing.
  3. Update `attachedDocText`, `attachedDocName`, and `layoutDocumentPreview` on the UI thread via `runOnUiThread()`.

#### 2.3 Guard `onResume` Model Reloads
- **Goal:** Prevent unnecessary multi-second model re-initializations and KV-cache loss when returning to `MainActivity`.
- **Files to Modify:**
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/MainActivity.java`
- **Implementation Steps:**
  1. In `checkModelStatus()`, check if `aiManager.isModelLoaded()` is already `true` and the selected model key has not changed.
  2. Only trigger `aiManager.loadModel(...)` or `loadVisionModel(...)` if the model is not currently loaded or the user switched model presets in Settings.

---

### 🟡 Phase 3: Resource Leaks, Integrity & Build Safety (P2 - Medium Priority)

#### 3.1 `SettingsActivity` Cursor & Receiver Cleanup
- **Goal:** Eliminate SQLite cursor leak and fix incorrect receiver unregistration in download listener.
- **Files to Modify:**
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/SettingsActivity.java`
- **Implementation Steps:**
  1. Wrap `dm.query(query)` in a `try (Cursor cursor = ...)` block to ensure the cursor is closed under all execution paths.
  2. Change `unregisterReceiver(this)` to `unregisterReceiver(onDownloadComplete)` to unregister the actual `BroadcastReceiver` instance.

#### 3.2 Real Model File SHA-256 Verification
- **Goal:** Detect corrupted or truncated GGUF files and prevent silent native failures.
- **Files to Modify:**
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/ModelManager.java`
- **Implementation Steps:**
  1. In `verifyModelHash()` and `verifyMmprojHash()`, implement a streaming `MessageDigest.getInstance("SHA-256")` file read on a background thread.
  2. Compare calculated digest against `getModelExpectedHash(modelKey)`.
  3. Mark `verified` true only if the hash matches.

#### 3.3 CMake `llama.cpp` Git Commit Pinning
- **Goal:** Guarantee reproducible native builds and protect against upstream breaking C++ API changes.
- **Files to Modify:**
  - `[MODIFY] app/src/main/cpp/CMakeLists.txt`
- **Implementation Steps:**
  1. In `FetchContent_Declare(llama_cpp ...)`, replace `GIT_TAG master` with a tested, stable commit hash (e.g. pinned commit tag).

#### 3.4 Temporary Cache & Attachment Pruning
- **Goal:** Prevent unbounded disk growth in `cache/images`.
- **Files to Modify:**
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/MainActivity.java`
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/DocumentHelper.java`
- **Implementation Steps:**
  1. Implement a routine to prune aged temp files in `getCacheDir()/images` during `MainActivity.onDestroy()` or on app launch.

---

### 🟢 Phase 4: Polish, Safety Guards & Clean Code (P3 - Quality of Life)

#### 4.1 `MemoryDao.syncMemories(null)` Null Safety Guard
- **Goal:** Protect against accidental memory table purge if a `null` list is passed.
- **Files to Modify:**
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/data/MemoryDao.java`
- **Implementation Steps:**
  1. Add an early-return guard: `if (newMemories == null) return;` at the beginning of `syncMemories`.

#### 4.2 Theme Color Tokens in Adapters
- **Goal:** Ensure dark/light mode consistency and remove hardcoded hex values.
- **Files to Modify:**
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/ModelAdapter.java`
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/RecentChatAdapter.java`
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/MemoryAdapter.java`

#### 4.3 Logcat Privacy & PII Stripping
- **Goal:** Prevent logging full user prompts and AI responses in production builds.
- **Files to Modify:**
  - `[MODIFY] app/src/main/cpp/native-lib.cpp`
  - `[MODIFY] app/src/main/java/com/k7sunny/nexv1/AIManager.java`
- **Implementation Steps:**
  1. Guard `LOG_PROMPT` and `Log.d` prompts with `BuildConfig.DEBUG` or `#ifndef NDEBUG`.

---

## 🧪 Verification & QA Testing Protocol

After executing each phase, run the following verification suite:

```bash
# 1. Verify Java/Kotlin code compiles cleanly
./gradlew :app:compileDebugJavaWithJavac

# 2. Verify C++ CMake compilation
./gradlew :app:externalNativeBuildDebug

# 3. Run Android Lint for syntax & lifecycle warnings
./gradlew :app:lintDebug

# 4. Run Unit Tests
./gradlew :app:testDebugUnitTest
```

### Manual QA Scenarios:
1. **Screen Rotation Test:** Send a long prompt, rotate the screen between Portrait and Landscape repeatedly during streaming → Verify no native SIGSEGV or process crash.
2. **Cancellation & Follow-Up Test:** Send a prompt, press "Stop" after 2 tokens, immediately send a second prompt → Verify response is coherent and not truncated.
3. **Large Document Test:** Attach a 5MB+ multi-page PDF → Verify smooth loading state without ANR dialog.
4. **App Resumption Test:** Open Drawer, switch to Settings, return to Chat → Verify chat input is immediately responsive without reloading the model.
