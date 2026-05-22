# Nex V1

Nex V1 is a local-first AI chatbot Android app designed to run completely offline.  
It focuses on privacy, performance, and simplicity by running a lightweight language model directly on the device.

---

## Core Idea

Nex V1 is built as a fully offline personal AI system where:
- All inference happens on-device
- No internet is required after setup
- No user data leaves the device

The goal is to create a fast, private, and controllable AI assistant.

---

## Features

- Offline AI (no internet required)
- Chat-based conversational interface
- Fully local data storage (no cloud dependency)
- Lightweight model optimized for mobile devices
- Clean and minimal UI using Material Design
- Local memory system (planned)
- Persistent chat history (planned)

---

## AI Engine and Model

### Engine
- **llama.cpp**
    - Runs the model using native C++ for performance
    - Integrated via Android NDK and JNI bridge
    - Optimized for CPU-based inference on mobile devices

### Model
- **Base Model:** Qwen2.5-0.5B-Instruct
- **Format:** GGUF
- **Quantization:** Q4_K_M (balanced performance vs memory)

### Why this setup

- GGUF allows efficient loading and inference on low-resource devices
- Q4 quantization reduces RAM usage significantly
- llama.cpp provides a proven, lightweight inference backend

---

## Expected App Behavior

Nex V1 is designed to behave as follows:

### 1. Fully Offline Operation
- No API calls
- No cloud dependency
- Works without internet once model is available

### 2. Real-time Chat Interaction
- User sends message
- App processes it locally
- AI responds with minimal delay (depending on device)

### 3. Asynchronous Processing
- AI runs on background thread
- UI remains smooth and responsive
- Typing/processing indicator shown during generation

### 4. Context Handling (Planned)
- Maintains recent conversation history
- Builds prompt dynamically
- Keeps responses relevant

### 5. Local Memory System (Planned)
- Stores important user data locally
- Used to personalize responses
- No external storage or syncing

### 6. Persistent Chat History (Planned)
- Chats saved in local database
- Accessible from sidebar/drawer
- Supports multiple conversations

---

## Architecture Overview
UI (Activity / RecyclerView)
↓
ChatAdapter
↓
AIManager (Java Layer)
↓
JNI Bridge
↓
llama.cpp (C++)
↓
GGUF Model (Qwen2.5-0.5B)

---

## Tech Stack

- Platform: Android (Native)
- Language: Java
- UI: Material Components
- AI Engine: llama.cpp
- Model Format: GGUF
- Native Layer: C++ (NDK + JNI)
- Storage: Local (Room / SQLite - planned)

---

## Current Status

- UI layout: Completed
- Chat system (RecyclerView + Adapter): Completed
- AI abstraction layer (AIManager): In progress
- Native integration (NDK + llama.cpp): Pending
- Model execution: Pending
- Memory system: Planned

---

## Project Vision

Nex is designed as a personal AI assistant that:
- Runs entirely offline
- Keeps all user data private
- Feels fast and responsive
- Can be customized at system level (prompt, behavior, memory)

This is not just a chatbot, but a foundation for a fully local AI system.

---

## Setup

1. Clone the repository
2. Open in Android Studio
3. Build and run on a physical device (recommended)

### Note
Model integration requires:
- NDK setup
- llama.cpp build
- GGUF model file added manually

Detailed setup instructions will be added later.

---

## Roadmap

- Complete AIManager integration
- Add typing/processing state
- Integrate llama.cpp with JNI
- Load and run GGUF model locally
- Implement memory system
- Add chat history system
- Optimize performance for low-end devices
- Improve UI/UX polish

---

## Limitations

- Performance depends on device CPU and RAM
- Initial response time may be slower than cloud AI
- Model capability limited compared to large cloud models

---

## Disclaimer

This project is experimental and intended for learning and personal use.  
It is not production-ready and may have performance limitations on lower-end devices.

---

## Author

Sunny

---

## Inspiration

Built as the next evolution after the Spark AI project, focusing on full offline capability and system-level control.