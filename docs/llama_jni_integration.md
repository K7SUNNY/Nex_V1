# Native Inference & llama.cpp / mtmd JNI Integration

Nex V1 utilizes a C++ native layer compiled using CMake/NDK. It links to `llama.cpp` and `mtmd` (multimodal engine) to perform on-device inference on text and vision GGUF models. This document describes the JNI bridge structure, multimodal vision pipeline, KV cache recycling mechanisms, thread tuning, and response token parsing.

---

## 1. JNI Bridge API Specification

The JNI entry points are defined in `native-lib.cpp` and exposed via `AIManager.java`:

| Java Native Method | C++ Implementation Signature | Purpose |
| :--- | :--- | :--- |
| `boolean initNative()` | `Java_com_k7sunny_nexv1_AIManager_initNative` | Initializes the global `llama.cpp` backend via `llama_backend_init()`. |
| `long loadModelNative(String path)` | `Java_com_k7sunny_nexv1_AIManager_loadModelNative` | Unloads any existing model/context, loads a text `.gguf` file, configures context parameters, and initializes `g_ctx`. |
| `long loadVisionModelNative(String modelPath, String mmprojPath)` | `Java_com_k7sunny_nexv1_AIManager_loadVisionModelNative` | Loads both the base language model (`Qwen2.5-VL`) and the vision projector (`mmproj-*.gguf`) into `g_ctx_vision` with `image_max_tokens = 256`. |
| `String runInferenceNative(...)` | `Java_com_k7sunny_nexv1_AIManager_runInferenceNative` | Formats chat input with templates, performs prefix KV cache lookup, decodes new tokens, runs the sampler loop, and streams results. |
| `String runVisionInferenceNative(...)` | `Java_com_k7sunny_nexv1_AIManager_runVisionInferenceNative` | Decodes image using `stb_image` / `mtmd_helper`, tokenizes multimodal input via `mtmd_tokenize`, evaluates visual chunks with `mtmd_helper_eval_chunks`, and streams generated response tokens. |
| `void cancelInferenceNative()` | `Java_com_k7sunny_nexv1_AIManager_cancelInferenceNative` | Signals the native sampler loop via an atomic boolean flag (`g_cancel_inference`) to halt execution. |
| `void freeNative()` | `Java_com_k7sunny_nexv1_AIManager_freeNative` | Releases model, context, and vision projector pointers and tears down backend memory. |

---

## 2. Multimodal Vision Pipeline (`mtmd`)

Nex Vision uses llama.cpp's `mtmd` subsystem to run Vision Transformers (ViT) locally on mobile CPUs:

1. **Image Loading**: `mtmd_helper_bitmap_init_from_file` decodes the cached downscaled image (JPEG/PNG).
2. **Multimodal Tokenization**:
   ```cpp
   mtmd_input_text text_in;
   text_in.text = prompt.c_str();
   text_in.text_len = prompt.size();
   text_in.add_special = false;
   text_in.parse_special = true;

   mtmd_tokenize(g_ctx_vision, chunks, &text_in, bitmaps, 1);
   ```
3. **Visual Token Clamping**:
   - `mparams.image_min_tokens = 64;`
   - `mparams.image_max_tokens = 256;`
   - Setting `image_max_tokens = 256` ensures ViT attention matrices remain compact, keeping CPU vision evaluation under 3–5 seconds and eliminating memory page allocation failures.
4. **Chunk Evaluation**: `mtmd_helper_eval_chunks` processes image embeddings into `llama_context`, after which autoregressive text generation proceeds normally.

---

## 3. Smart KV Cache Reuse (Token-Based)

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

## 4. Threading and CPU Optimization

Mobile chipsets operate on heterogeneous big.LITTLE CPU configurations. Spawning too many threads triggers thermal throttling and decreases performance. 
- The native layer fetches the hardware concurrency limit via `std::thread::hardware_concurrency()`.
- It clamps the thread count between `1` and `4` (optimal for mobile performance cores):
  ```cpp
  int num_threads = (int)std::thread::hardware_concurrency();
  if (num_threads > 4) num_threads = 4;
  if (num_threads < 1) num_threads = 1;
  ```
- Flash Attention is enabled for speed gains: `ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;`.
- CMake is configured with `-O3 -fno-finite-math-only` and `-DCMAKE_BUILD_TYPE=Release` for ARM NEON SIMD vectorization.

---

## 5. Sampling Parameters

Tokens are sampled dynamically using a unified sampler stack configured as follows:
- **Temperature**: `0.7f` (Default chat), `0.2f` (Memory extraction), `0.3f` (Auto-Title)
- **Top-K**: `40`
- **Top-P**: `0.90f`
- **Min-P**: `0.05f`
- **Repeat Penalty**: `1.15f` (applied to the last `64` tokens)

---

## 6. Stop Sequences and Role-Leak Protection

Small models (e.g. Qwen 0.5B) often hallucinate dialogue boundaries, trying to speak on behalf of the user. To prevent this, the native sampling loop checks the accumulated response in real-time. 

If any of the following stop patterns are matched, the generation is trimmed and halted:
- `<|user|>` / `<|assistant|>` / `<|system|>`
- `<|endoftext|>` / `</s>` / `<|end|>`
- `<|im_start|>` / `<|im_end|>` / `<|eot_id|>`
- `User:` / `Assistant:` / `Instruction:`

If a stop pattern triggers, `cache_valid` is set to `false`, causing the KV cache to be cleared on the next turn to prevent the malformed context from contaminating subsequent generation rounds.
