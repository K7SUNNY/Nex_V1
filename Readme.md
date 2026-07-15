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
- **Intelligent Memory System:** Automatically extracts and stores personal facts and preferences to personalize future interactions.
- **Smart Context Management:** Optimized KV cache reuse for lightning-fast multi-turn conversations.
- **Auto-Title Generation:** Automatically generates descriptive titles for chat sessions based on content.
- **Topic Drift Detection:** Detects when a conversation has moved to a new subject and suggests updating the title.
- **Persistent History:** Full chat history stored locally using Room Database.
- **Clean Material UI:** Minimalist and responsive design with dark mode support.

---

## AI Engine and Model

### Engine: llama.cpp (Native C++)
- Integrated via **Android NDK and JNI bridge**.
- **Token-based KV Cache Reuse:** Drastically reduces processing time for long conversations by only decoding new tokens.
- **Thread-Safe Implementation:** Native layer uses mutex synchronization for stability during concurrent operations.
- **Optimized for Mobile:** Tuned for big.LITTLE CPU architectures to balance speed and battery life.

### Model
- **Base Model:** Qwen2.5-0.5B-Instruct (or Llama 3 / Gemma variants).
- **Format:** GGUF.
- **Quantization:** Q4_K_M (Balanced performance vs. memory footprint).

---

## The Memory System

Nex V1 features a sophisticated, local "Personal Knowledge Base":

- **Auto-Extraction:** Uses the AI model to scan conversations for facts (hobbies, pets, locations, coding styles) after every user turn.
- **Identity Protection:** Features a robust sanitization pipeline that prevents "Role Reversal" hallucinations. It ensures the AI always refers to itself as "I" and you as "You," avoiding identity confusion common in small models.
- **Atomic Persistence:** Uses Room Database with unique constraints to prevent duplicate memories.
- **Dynamic Context Injection:** Memories are injected into the system prompt with de-ambiguated headers so the AI knows exactly who the information belongs to.

---

## Architecture Overview

**UI (Activity / RecyclerView)**  
↓  
**ChatAdapter / ConversationAnalyzer** (Title & Drift Logic)  
↓  
**AIManager** (Java Orchestration + Memory Extraction)  
↓  
**MemoryManager / HistoryManager** (Room Persistence)  
↓  
**JNI Bridge**  
↓  
**llama.cpp (C++)**  
↓  
**GGUF Model** (Qwen/Llama)

---

## Tech Stack

- **Platform:** Android (Native)
- **Language:** Java & C++
- **UI:** Material Components 3
- **AI Engine:** llama.cpp (GGUF)
- **Native Layer:** C++ (NDK + JNI)
- **Database:** Room / SQLite
- **Concurrency:** Java ExecutorService & C++ Pthreads

---

## Current Status

- **UI & Chat System:** Completed and Polished.
- **Native Integration:** llama.cpp successfully integrated with JNI.
- **Model Execution:** Fully functional on-device inference.
- **Memory System:** Active fact extraction and sanitization implemented.
- **Chat History:** Persistent storage and session management completed.
- **Optimization:** KV cache reuse and thread safety implemented.

---

## Setup

1. Clone the repository.
2. Open in Android Studio (Ladybug or newer recommended).
3. Ensure **NDK (Side-by-side)** is installed in SDK Manager.
4. Add your `.gguf` model file to the device (the app will prompt for the path).
5. Build and run on a physical device for best performance.

---

## Roadmap

- [x] Integrate llama.cpp with JNI.
- [x] Implement token-based KV cache reuse.
- [x] Add persistent chat history.
- [x] Create auto-extracting memory system.
- [x] Implement role-sanitization to prevent identity hallucinations.
- [ ] Add support for Multimodal models (Vision).
- [ ] Implement local RAG (Retrieval Augmented Generation) for larger documents.
- [ ] Add voice interaction (STT/TTS).
- [ ] Further optimize for entry-level devices.

---

## Author

**Sunny** - Creator of Nex AI

---

## Inspiration

Built as the next evolution after the **Spark AI** project, Nex V1 pushes the boundaries of what's possible with local-only AI on mobile hardware.

*This app is fully functional and offline.*
