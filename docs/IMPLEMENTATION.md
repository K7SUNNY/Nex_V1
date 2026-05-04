# Nex V1 – Implementation Plan

This document defines the step-by-step implementation plan for integrating an offline AI model into the Nex V1 Android app. Each phase is ordered intentionally to reduce complexity, isolate problems, and ensure stability.

---

## Phase 1: Stabilize UI and Message Flow

### Goal
Ensure the chat system behaves like a real messaging app before adding AI.

### Tasks
- Finalize RecyclerView + Adapter
- Support two message types (User, AI)
- Ensure smooth scrolling
- Hide welcome screen on first message
- Handle long text properly

### Why
AI integration becomes difficult if the UI layer is unstable. This phase guarantees that messages can be added, updated, and displayed reliably.

---

## Phase 2: Introduce AI Manager (Abstraction Layer)

### Goal
Create a separation between UI and AI logic.

### Tasks
- Create `AIManager` class
- Add method:
    - `generateResponse(String prompt)`
- Route all AI responses through this class

### Why
Directly calling the model from the UI creates tight coupling and makes debugging difficult. The AIManager acts as a controlled interface and allows swapping implementations later (mock → real model).

---

## Phase 3: Replace Mock Logic with Structured Flow

### Goal
Simulate real AI behavior without using an actual model yet.

### Tasks
- Replace delayed fake response with AIManager call
- Run AIManager in a background thread
- Update UI on main thread
- Add temporary response logic inside AIManager

### Why
This step validates the full pipeline:
UI → AIManager → UI

It ensures threading, message flow, and UI updates are working before introducing heavy computation.

---

## Phase 4: Add Typing / Processing State

### Goal
Improve user experience and prepare for real model latency.

### Tasks
- Insert temporary "typing" message when user sends input
- Replace or update it when response is ready

### Why
Real AI models take time to respond. This prevents the app from feeling unresponsive and prepares the UI for asynchronous behavior.

---

## Phase 5: Prepare Native Integration (NDK Setup)

### Goal
Enable the app to run a local AI model using native code.

### Tasks
- Install Android NDK
- Configure CMake
- Verify native build pipeline works

### Why
llama.cpp is written in C++. It cannot run directly in Java. The NDK is required to compile and run native code on Android.

---

## Phase 6: Integrate llama.cpp

### Goal
Bring the AI engine into the app.

### Tasks
- Add llama.cpp source to project
- Compile it into a shared library (.so)
- Load library using `System.loadLibrary()`

### Why
This step provides the actual inference engine required to run the GGUF model locally.

---

## Phase 7: Create JNI Bridge

### Goal
Connect Java code to native C++ model execution.

### Tasks
- Define native method in Java:
    - `public native String runModel(String prompt);`
- Implement JNI function in C++
- Pass prompt from Java to C++
- Return generated response back to Java

### Why
JNI acts as the bridge between Android (Java) and llama.cpp (C++). Without this, the model cannot be invoked.

---

## Phase 8: Load and Run GGUF Model

### Goal
Enable real AI responses.

### Tasks
- Place GGUF model in app storage
- Initialize model at app start or first use
- Run inference using prompt
- Return output to AIManager

### Why
This is the core functionality. The model generates responses based on user input.

---

## Phase 9: Optimize Performance

### Goal
Ensure smooth operation on mobile devices.

### Tasks
- Use quantized model (e.g., Q4_K_M)
- Limit context size
- Manage memory usage
- Run inference on background thread

### Why
Mobile devices have limited RAM and CPU. Optimization prevents crashes and improves response speed.

---

## Phase 10: Add Memory System (Optional)

### Goal
Enable contextual conversations.

### Tasks
- Store previous messages locally (Room/SQLite)
- Build prompt with conversation history
- Limit history size

### Why
Without memory, the AI behaves statelessly. This step allows more natural conversations.

---

## Final Architecture
UI (Activity)
    -
ChatAdapter
    -
AIManager
    -
JNI Bridge
    -
llama.cpp (C++)
    -
GGUF Model

---

## Key Principles

- Always separate UI and logic
- Never run heavy tasks on the main thread
- Build in layers (UI → abstraction → native)
- Validate each phase before moving forward

---

## Current Status (Update as you progress)

- UI: Completed
- Adapter: Completed
- AIManager: Pending
- Native Integration: Pending
- Model Execution: Pending

---
