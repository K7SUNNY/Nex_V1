# Nex V1

Nex V1 is a local-first AI chatbot Android app designed to run completely offline.  
It focuses on privacy, performance, and simplicity by running a lightweight language model directly on the device.

---

## Core Idea

Nex V1 is built as a fully offline personal AI system where:
- All inference happens on-device
- No internet is required after setup
- No user data leaves the device

The goal is to create a fast, private, and controllable AI assistant that actually *remembers* you.

---

## Features

- **Fully Offline AI:** No internet required, no API costs, total privacy.
- **Multimodal Nex Vision:** Offline image reasoning, screenshot analysis, OCR, and visual Q&A powered by Qwen2.5-VL 3B and `mtmd`.
- **4 Model Choices:**
  - **Nex Fast:** Qwen2.5-0.5B (ultra-fast, instant streaming).
  - **Nex Pro:** Qwen2.5-1.5B (balanced reasoning and speed).
  - **Nex Ultra:** Qwen2.5-3B (deep reasoning and complex planning).
  - **Nex Vision:** Qwen2.5-VL-3B (multimodal text + vision).
- **Intelligent Memory System:** Automatically extracts, sanitizes, and stores personal facts and plans to personalize future interactions.
- **Smart Context Management:** Optimized token-based KV cache reuse for lightning-fast multi-turn conversations.
- **Auto-Title Generation & Drift Detection:** Automatically generates descriptive session titles and detects topic shifts.
- **Persistent History:** Full chat history and image attachments stored locally using Room Database.
- **Clean Material UI:** Minimalist and responsive design with dark mode, code block highlighting, and attachment preview.

---

## AI Engine and Model Architecture

### Engine: llama.cpp + mtmd (Native C++)
- Integrated via **Android NDK and JNI bridge**.
- **Multimodal Subsystem (`mtmd`):** Uses llama.cpp's `mtmd` engine for vision tokenization, image patch embedding, and spatial merging.
- **Visual Token Clamping & Patch Alignment:** Dynamically limits vision tokens to 256 and pre-scales images to patch-aligned 392px to ensure 3–5s vision inference on mobile CPUs without memory thrashing.
- **Token-based KV Cache Reuse:** Drastically reduces processing time for long multi-turn conversations by only decoding new tokens.
- **Compiler Optimizations:** Built with Release mode `-O3` and ARM NEON SIMD vectorization.
- **Thread-Safe Architecture:** Mutex synchronization across JNI operations prevents race conditions during concurrent inference and cancellations.

### Models
- **Text Models:** Qwen2.5-0.5B, 1.5B, 3B Instruct (GGUF, Q4_K_M).
- **Vision Model:** Qwen2.5-VL-3B-Instruct (GGUF, Q4_K_M) paired with `mmproj-Qwen2.5-VL-3B-Instruct-f16.gguf` projector.

---

## The Memory System

Nex V1 features a sophisticated, local "Personal Knowledge Base":

- **Auto-Extraction:** Uses the AI model to scan conversations for facts, plans, and preferences after every user turn with few-shot guidance.
- **Third-Person Normalization:** Formats facts cleanly as `[Topic] | User [fact/plan]` (e.g. `Family | User is meeting their family next week.`).
- **Identity Protection:** Enforces strict validation filters to prevent AI self-referencing (`I am an AI...`) or prompt instruction leakage.
- **Atomic Persistence:** Uses Room Database with unique constraints to prevent duplicate memories.
- **Dynamic Context Injection:** Pinned memories are injected into the system prompt across conversations.

---

## Architecture Overview

**UI (Activity / RecyclerView / Image Picker)**  
↓  
**ChatAdapter / ConversationAnalyzer** (Title & Drift Logic)  
↓  
**AIManager** (Java Orchestration + Memory Extraction + Multimodal Dispatch)  
↓  
**MemoryManager / HistoryManager** (Room Persistence with Image URIs)  
↓  
**JNI Bridge (`native-lib.cpp`)**  
↓  
**llama.cpp & mtmd (C++)**  
↓  
**GGUF Model + mmproj Projector** (Qwen2.5 / Qwen2.5-VL)

---

## Tech Stack

- **Platform:** Android (Native)
- **Language:** Java & C++ (NDK 26+)
- **UI:** Material Components 3 & Markwon (Markdown rendering)
- **AI Engine:** llama.cpp & mtmd (GGUF)
- **Database:** Room / SQLite (Schema v4)
- **Concurrency:** Java ExecutorService & C++ Pthreads / OpenMP

---

## Current Status

- **UI & Chat System:** Completed and Polished with image attachment and thumbnail support.
- **Native Integration:** llama.cpp & mtmd successfully integrated with JNI.
- **Model Execution:** Fully functional text and multimodal on-device inference.
- **Memory System:** Active fact/plan extraction, third-person normalization, and Room persistence.
- **Chat History:** Persistent storage and session management with attached image support.
- **Optimization:** KV cache reuse, visual token clamping, and `-O3` Release compilation.

---

## Setup

1. Clone the repository.
2. Open in Android Studio (Ladybug or newer recommended).
3. Ensure **NDK (Side-by-side)** and **CMake 3.22.1+** are installed in SDK Manager.
4. Download or place `.gguf` model files in device storage (the app handles automatic in-app downloads from HuggingFace).
5. Build and run on a physical device for best performance.

---

## Roadmap

- [x] Integrate llama.cpp with JNI.
- [x] Implement token-based KV cache reuse.
- [x] Add persistent chat history (Room DB v4).
- [x] Create auto-extracting memory system with few-shot normalization.
- [x] Add support for Multimodal Vision models (Qwen2.5-VL 3B + mmproj).
- [ ] Implement local RAG (Retrieval Augmented Generation) for user documents.
- [ ] Add voice interaction (STT/TTS with Whisper.cpp).
- [ ] Further optimize NPU/GPU acceleration (Vulkan/NNAPI/OpenCL).

---

## Author

**Sunny** - Creator of Nex AI

---

## Inspiration

Built as the next evolution after the **Spark AI** project, Nex V1 pushes the boundaries of what's possible with local-only AI on mobile hardware.

*This app is fully functional and offline.*
