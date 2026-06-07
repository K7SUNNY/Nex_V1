# Nex V1 — Implementation Roadmap

> Living document tracking upcoming features, technical debt, and architecture decisions for the Nex personal AI workspace.

---

## Phase 1 — Chat Experience (High Priority)

These directly impact the core user experience — talking to the AI.

### 1.1 Stop Generation
- [x] Add a **Stop** button (replaces Send while generating) to abort inference mid-stream
- [x] Requires native JNI hook: expose a `cancelInference()` method in `native-lib.cpp` that sets an atomic flag checked each token loop
- [x] Partial response should be kept in the chat as-is (not discarded)
- **Files**: `AIManager.java`, `native-lib.cpp`, `MainActivity.java`, `activity_main.xml`

### 1.2 Regenerate Response
- [x] Add a small "Regenerate" button below the last AI message
- [x] Removes the current AI response, re-sends the same user prompt
- [x] Button should only appear on the most recent AI message, and disappear during generation
- **Files**: `ChatAdapter.java`, `item_ai_message.xml`, `MainActivity.java`

### 1.3 Markdown Rendering
- [ ] Replace plain `TextView` with a Markdown renderer for AI messages (e.g. [Markwon](https://github.com/noties/markwon))
- [ ] Support: **bold**, *italic*, `inline code`, code blocks with background, headings, bullet/numbered lists
- [ ] Code blocks should have a "Copy" button in the top-right corner
- [ ] User messages stay as plain text (no markdown)
- **Files**: `ChatAdapter.java`, `item_ai_message.xml`, `build.gradle.kts` (add Markwon dependency)

### 1.4 Typing Indicator Animation
- [ ] Replace the static `"..."` placeholder with an animated three-dot pulse
- [ ] Use a custom `View` or Lottie animation inside `item_ai_message.xml`
- [ ] Hide the animation and show the text `TextView` once the first token arrives
- **Files**: `ChatAdapter.java`, `item_ai_message.xml`

### 1.5 Scroll-to-Bottom FAB
- [ ] Show a floating "↓" button when the user has scrolled up during streaming
- [ ] Tapping it scrolls to bottom and dismisses the button
- [ ] Tie visibility to the existing `isUserScrolledUp` flag in `MainActivity`
- **Files**: `MainActivity.java`, `activity_main.xml`

---

## Phase 2 — Message Actions & Interactions

### 2.1 Visible Copy Button
- [x] Add a subtle copy icon below each AI message (currently copy is long-press only, which isn't discoverable)
- [x] Toast or snackbar confirmation on tap
- **Files**: `ChatAdapter.java`, `item_ai_message.xml`

### 2.2 Share Message
- [x] Add a share icon next to the copy button on AI messages
- [x] Uses Android `Intent.ACTION_SEND` with `text/plain`
- **Files**: `ChatAdapter.java`, `item_ai_message.xml`

### 2.3 Haptic Feedback
- [ ] Subtle vibration on:
  - Sending a message
  - First token received (response starts)
  - Long-press copy
  - Menu interactions
- [ ] Use `HapticFeedbackConstants` for system-consistent haptics
- **Files**: `MainActivity.java`, `ChatAdapter.java`

### 2.4 Message Timestamps
- [ ] Add a subtle, right-aligned timestamp below each message (e.g. "2:30 PM")
- [ ] Requires adding a `timestamp` field to the `Message` model
- **Files**: `Message.java`, `ChatAdapter.java`, `item_ai_message.xml`, `item_user_message.xml`

---

## Phase 3 — AI & Model Improvements

### 3.1 Configurable Max Tokens
- [x] Currently, hardcoded to `256` in `AIManager.java` line 112
- [x] Add a slider or segmented control in Settings (e.g. 128 / 256 / 512 / 1024)
- [x] Store in `PreferenceManager` and pass to `runInferenceNative()`
- **Files**: `AIManager.java`, `PreferenceManager.java`, `SettingsActivity.java`, `activity_settings.xml`

### 3.2 Configurable Temperature
- [x] Expose temperature parameter in native inference (if not already)
- [x] Add a slider in Settings (0.1 – 1.5, default 0.7)
- [x] Lower = more focused/deterministic, higher = more creative
- **Files**: `native-lib.cpp`, `AIManager.java`, `PreferenceManager.java`, `SettingsActivity.java`

### 3.3 Context Window Management
- [ ] Currently uses a fixed sliding window of 12 messages (`MAX_HISTORY` in `AIManager.java`)
- [ ] Make this configurable or auto-calculate based on model's context length
- [ ] Show a subtle indicator when old context is being dropped
- **Files**: `AIManager.java`, `PreferenceManager.java`

### 3.4 Multi-Model Support
- [ ] Currently the model selector UI exists but both "Nex Fast" and "Nex Pro" point to the same model
- [ ] Support downloading and switching between different GGUF models
- [ ] Store multiple model paths in `ModelManager`
- [ ] Show model size, quantization level, and capability summary in the selector
- **Files**: `ModelManager.java`, `MainActivity.java`, `bottom_sheet_model_selection.xml`

---

## Phase 4 — Data & Persistence

### 4.1 Migrate to Room Database
- [x] Current storage uses `SharedPreferences` with JSON strings — doesn't scale and risks data loss with large histories
- [x] Create Room entities: `ChatSessionEntity`, `MessageEntity`, `MemoryEntity`
- [x] Benefits: SQL queries, pagination, full-text search, proper data integrity
- [x] Migrate existing SharedPreferences data on first launch
- **Files**: New `database/` package, `HistoryManager.java`, `MemoryManager.java`

### 4.2 Search Conversations
- [ ] Add a search bar at the top of the drawer's recent chats list
- [ ] Filter conversations by title or message content
- [ ] With Room (4.1), this becomes a simple `LIKE` query
- **Files**: `DrawerActivity.java`, `activity_drawer.xml`, `HistoryManager.java`

### 4.3 Export Chat
- [ ] Export a conversation as a `.txt` or `.md` file
- [ ] Add "Export" option to the chat options bottom sheet
- [ ] Use Android's `Storage Access Framework` to let the user pick save location
- **Files**: `DrawerActivity.java`, `bottom_sheet_chat_options.xml`

### 4.4 Unlimited Session History
- [ ] Currently capped at 10 sessions (`HistoryManager.java` line 24)
- [ ] With Room (4.1), remove the cap and add pagination in the drawer
- **Files**: `HistoryManager.java`, `DrawerActivity.java`

### 4.5 Crash-Safe Streaming
- [ ] If the app is killed mid-generation, the partial AI response is lost
- [ ] Periodically save partial response to history (e.g. every 20 tokens)
- [ ] On reload, show the partial response as-is
- **Files**: `MainActivity.java`, `HistoryManager.java`

---

## Phase 5 — UI/UX Polish

### 5.1 Onboarding Flow
- [ ] First-launch walkthrough explaining Nex's offline capability
- [ ] Guide the user through model download before they try chatting
- [ ] Skip button for returning users

### 5.2 Empty State Improvements
- [ ] Better empty states in Memory, Account, and Chat History screens
- [ ] Illustrated placeholders instead of blank screens

### 5.3 Dark/Light Theme Toggle
- [ ] Currently hardcoded to dark theme
- [ ] Add a theme toggle in Settings (Dark / Light / System)
- [ ] Define light color variants in `colors.xml` and a `themes.xml` day/night split

### 5.4 Swipe Actions on Chat History
- [ ] Swipe-left to delete a conversation in the drawer
- [ ] Swipe-right to pin/archive (future)
- [ ] Use `ItemTouchHelper` on the RecyclerView
- **Files**: `DrawerActivity.java`, `RecentChatAdapter.java`

### 5.5 Input Enhancements
- [ ] Show character/token count near the input field
- [ ] Auto-expand input field smoothly as user types multi-line messages
- [ ] Keyboard "Send" action support (IME action)

---

## Phase 6 — Advanced Features (Long-Term)

### 6.1 Image Input (Multimodal)
- [ ] Support attaching images to prompts (requires a multimodal GGUF model like LLaVA)
- [ ] Camera and gallery picker
- [ ] Image is processed through native layer alongside text prompt

### 6.2 Widgets
- [ ] Home screen widget for quick prompts
- [ ] Shows last AI response or allows typing directly

### 6.3 Notification for Background Generation
- [ ] If the user backgrounds the app while AI is generating, show a notification when complete
- [ ] Tapping the notification opens the chat with the response

### 6.4 Voice Input
- [ ] Microphone button in the input bar
- [ ] Use Android's `SpeechRecognizer` API for speech-to-text
- [ ] Transcribed text fills the input field for review before sending

### 6.5 Auto-Title Generation
- [x] Currently uses the first 30 chars of the user's first message as the session title
- [x] After the first AI response, use the model to generate a short descriptive title
- **Files**: `MainActivity.java`, `AIManager.java`

---

## Completed ✅

| Feature | Notes |
|---|---|
| Basic chat UI | User/AI message bubbles with RecyclerView |
| Token streaming | Real-time token-by-token display via JNI callback |
| Smart auto-scroll | Scrolls to bottom during streaming, respects user reading position |
| Efficient streaming updates | Payload-based partial ViewHolder bind, stutter-free manual scrolling |
| Copy to clipboard | Long-press on any message |
| Chat history | Save, load, rename, delete conversations (SharedPreferences/JSON) |
| Navigation drawer | Side menu with recent chats, settings, memory, account |
| Custom bottom sheets | Modern rounded sheets for options, rename, model selection |
| Model download | DownloadManager with real-time progress polling |
| Memory system | Persistent memories with pin/unpin, injected into AI system prompt |
| Custom persona | User-configurable system prompt via Settings |
| Model selector UI | Nex Fast / Nex Pro toggle (UI-only, same backend model) |
| Theme | OLED-black dark theme with Material 3 components |
| Room Database Migration | Migrated history storage from JSON-in-SharedPreferences to Room Database with automatic legacy data migration on launch |
| Stop Generation | Exposes JNI cancellation hook and toggles UI send button to stop active inference |
| Max Tokens & Temperature | Controls in Settings to dynamically customize inference sampler parameters |
| Edge-to-edge | Proper system bar and keyboard inset handling |
| Regenerate Response | Tap graphical "Regenerate" button under response, supporting older messages with history branch truncation |
| Model Integrity Verification | Background SHA-256 validation of the downloaded GGUF model with SharedPreferences cache |
| Auto-Title Generation | Asynchronous background llama.cpp inference after first message exchange to set descriptive session title |
| Visible Copy Button | Graphic copy button below AI messages to copy response to clipboard |
| Share Message | Graphic share button below AI messages to export response text |
| Delete Message & Pin | Options menu (three dots) under AI messages to delete bubbles or pin to memory |
