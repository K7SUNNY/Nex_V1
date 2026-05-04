# Implementation Plan: Phase 9 - Automated Model Management

This phase adds an automated model downloader and manager to ensure the user has a model file without manual intervention.

## Phase 9: Automated Model Management
**Goal:** Automatically check for the model on startup, prompt the user if missing, and download it to the internal storage.

### Tasks
- [ ] Create `ModelManager.java` to handle downloading and file checks.
- [ ] Update `AIManager.java` to interact with `ModelManager`.
- [ ] Add a "Download Model" overlay or dialog in `MainActivity.java`.
- [ ] Implement `DownloadManager` integration for background downloading.
- [ ] Ensure the model path is dynamic (e.g., using `context.getFilesDir()`).

### UI Logic:
- On startup, check if `model.gguf` exists in `files/models/`.
- If missing, show a prominent "Download Model" card in the `welcomeContainer`.
- Show a progress bar while downloading.
- Once finished, automatically load the model via `AIManager`.

---

## Verification Plan

### Phase 9 Verification
1. **Initial Run (Clean Install):**
    - The app should show a "Download Model" prompt.
    - **Action:** Click Download.
    - **Expected:** A progress bar appears, and the file is saved to internal storage.
2. **Persistence:**
    - Close and reopen the app.
    - **Expected:** The app should detect the model and show "Model loaded successfully" in logs without prompting again.
3. **Manual Check:**
    - Verify the file exists using `adb shell ls /data/data/com.k7sunny.nexv1/files/models/`.
