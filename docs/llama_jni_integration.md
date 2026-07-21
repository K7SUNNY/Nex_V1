# Native Inference & llama.cpp JNI Integration

Nex V1 utilizes a C++ native layer compiled using CMake/NDK. It links to the `llama.cpp` library to perform on-device inference on GGUF models. This document describes the JNI bridge structure, KV cache recycling mechanisms, thread tuning, and response token parsing.

---

## 1. JNI Bridge API Specification

The JNI entry points are defined in `native-lib.cpp` and exposed via `AIManager.java`:

| Java Native Method | C++ Implementation Signature | Purpose |
| :--- | :--- | :--- |
| `boolean initNative()` | `Java_com_k7sunny_nexv1_AIManager_initNative` | Initializes the global `llama.cpp` backend via `llama_backend_init()`. |
| `long loadModelNative(String path)` | `Java_com_k7sunny_nexv1_AIManager_loadModelNative` | Unloads any existing model/context, loads a new `.gguf` file, configures context parameters, and returns the model pointer. |
| `String runInferenceNative(...)` | `Java_com_k7sunny_nexv1_AIManager_runInferenceNative` | Formats chat input with templates, performs prefix KV cache lookup, decodes new tokens, runs the sampler loop, and streams results. |
| `void cancelInferenceNative()` | `Java_com_k7sunny_nexv1_AIManager_cancelInferenceNative` | Signals the native sampler loop via an atomic boolean flag (`g_cancel_inference`) to halt execution. |
| `void freeNative()` | `Java_com_k7sunny_nexv1_AIManager_freeNative` | Releases model/context pointers and tears down the backend memory. |

---

## 2. Smart KV Cache Reuse (Token-Based)

To avoid evaluating the entire chat prompt from scratch for every turn, the native bridge implements a token-level key-value (KV) cache alignment algorithm. 

### Alignment Strategy
1. The JNI bridge tokenizes the incoming formatted prompt into `all_tokens`.
2. It compares `all_tokens` token-by-token with the token history from the previous turn (`g_last_tokens`).
3. It finds the matching common prefix of length `n_past`.
4. If `n_past > 0`, it preserves the prefix in the KV cache and discards trailing tokens via:
   ```cpp
   llama_memory_seq_rm(llama_get_memory(g_ctx), 0, n_past, -1);
   ```
5. If there is no common prefix (`n_past == 0`), the KV cache is completely cleared:
   ```cpp
   llama_memory_clear(llama_get_memory(g_ctx), true);
   ```
6. The bridge evaluates only the remainder tokens (`new_tokens = all_tokens[n_past ... end]`). This drops evaluation time from seconds to milliseconds for multi-turn history.

---

## 3. Threading and CPU Optimization

Mobile chipsets operate on heterogeneous big.LITTLE CPU configurations. Spawning too many threads triggers thermal throttling and decreases performance. 
- The native layer fetches the hardware concurrency limit via `std::thread::hardware_concurrency()`.
- It clamps the thread count between `1` and `4` (optimal for mobile cores):
  ```cpp
  int num_threads = (int)std::thread::hardware_concurrency();
  if (num_threads > 4) num_threads = 4;
  if (num_threads < 1) num_threads = 1;
  ```
- Flash Attention is enabled for speed gains: `ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;`.

---

## 4. Sampling Parameters

Tokens are sampled dynamically using a unified sampler stack configured as follows:
- **Temperature**: `0.7f` (Default chat), `0.2f` (Memory extraction), `0.3f` (Auto-Title)
- **Top-K**: `40`
- **Top-P**: `0.90f`
- **Min-P**: `0.05f`
- **Repeat Penalty**: `1.15f` (applied to the last `64` tokens)

---

## 5. Stop Sequences and Role-Leak Protection

Small models (e.g. Qwen 0.5B) often hallucinate dialogue boundaries, trying to speak on behalf of the user. To prevent this, the native sampling loop checks the accumulated response in real-time. 

If any of the following stop patterns are matched, the generation is trimmed and halted:
- `<|user|>` / `<|assistant|>` / `<|system|>`
- `<|endoftext|>` / `</s>` / `<|end|>`
- `<|im_start|>` / `<|im_end|>` / `<|eot_id|>`
- `User:` / `Assistant:` / `Instruction:`

If a stop pattern triggers, `cache_valid` is set to `false`, causing the KV cache to be cleared on the next turn to prevent the malformed context from contaminating subsequent generation rounds.
