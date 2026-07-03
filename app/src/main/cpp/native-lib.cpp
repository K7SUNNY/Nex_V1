#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include <android/log.h>
#include "llama.h"
#include "common.h"
#include "sampling.h"

#define TAG "NexNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Dedicated log tags for easier filtering
#define TAG_MODEL   "NexModel"
#define TAG_PROMPT  "NexPrompt"
#define TAG_INFER   "NexInfer"
#define TAG_CACHE   "NexCache"
#define LOG_MODEL(...) __android_log_print(ANDROID_LOG_DEBUG, TAG_MODEL, __VA_ARGS__)
#define LOG_PROMPT(...) __android_log_print(ANDROID_LOG_DEBUG, TAG_PROMPT, __VA_ARGS__)
#define LOG_INFER(...) __android_log_print(ANDROID_LOG_DEBUG, TAG_INFER, __VA_ARGS__)
#define LOG_CACHE(...) __android_log_print(ANDROID_LOG_DEBUG, TAG_CACHE, __VA_ARGS__)

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static std::string g_last_prompt = "";
static int g_last_token_count = 0;
static std::atomic<bool> g_cancel_inference{false};

// Helper: replace all occurrences of `from` with `to` in a string
static void replace_all(std::string& str, const std::string& from, const std::string& to) {
    size_t pos = 0;
    while ((pos = str.find(from, pos)) != std::string::npos) {
        str.replace(pos, from.length(), to);
        pos += to.length();
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_k7sunny_nexv1_AIManager_stringFromJNI(
        JNIEnv* env,
        jobject) {
    std::string hello = "Hello from C++ (NDK) with llama.cpp";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_k7sunny_nexv1_AIManager_initNative(JNIEnv*, jobject) {
    llama_backend_init();
    LOG_MODEL("Backend initialized");
    return JNI_TRUE;
}

static void free_resources() {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_last_prompt = "";
    g_last_token_count = 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_k7sunny_nexv1_AIManager_loadModelNative(JNIEnv* env, jobject, jstring model_path) {
    // Free existing resources first to prevent memory leaks when switching models
    free_resources();

    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOG_MODEL("Loading model from: %s", path);

    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;
    model_params.n_gpu_layers = 0; // CPU only for now to ensure stability

    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (!g_model) {
        LOGE("Model load failed");
        return 0;
    }

    // Log the chat template embedded in the GGUF
    const char* model_tmpl = llama_model_chat_template(g_model, nullptr);
    LOG_MODEL("GGUF chat template: %s", model_tmpl ? model_tmpl : "NONE");

    // Log EOS token info
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    llama_token eos_id = llama_vocab_eos(vocab);
    LOG_MODEL("EOS token id: %d", eos_id);

    llama_context_params ctx_params = llama_context_default_params();

    // Optimize for big.LITTLE mobile CPUs.
    int num_threads = (int)std::thread::hardware_concurrency();
    if (num_threads > 4) num_threads = 4;
    if (num_threads < 1) num_threads = 1;

    LOG_MODEL("Initializing context with %d threads", num_threads);

    ctx_params.n_ctx = 2048;
    ctx_params.n_threads = num_threads;
    ctx_params.n_threads_batch = num_threads;
    ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED; // Speed up prompt evaluation and reduce memory bandwidth usage

    g_ctx = llama_init_from_model(g_model, ctx_params);

    if (!g_ctx) {
        LOGE("Context creation failed");
        return 0;
    }

    LOG_MODEL("Model + Context ready");
    return reinterpret_cast<jlong>(g_model);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_k7sunny_nexv1_AIManager_runInferenceNative(
    JNIEnv* env, jobject,
    jstring jsystemPrompt,
    jobjectArray jroles,
    jobjectArray jcontents,
    jint max_tokens,
    jfloat temperature,
    jobject jcallback) {

    g_cancel_inference.store(false);

    if (!g_model || !g_ctx) {
        return env->NewStringUTF("Error: Model not loaded");
    }

    // Get the callback class and method ID
    jclass callbackClass = env->GetObjectClass(jcallback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");

    // ---- Build structured chat messages ----

    const char* sys_cstr = env->GetStringUTFChars(jsystemPrompt, nullptr);
    std::string system_prompt(sys_cstr);
    env->ReleaseStringUTFChars(jsystemPrompt, sys_cstr);

    int msg_count = env->GetArrayLength(jroles);

    // Strings must stay alive while llama_chat_message holds pointers to them
    std::vector<std::string> role_store;
    std::vector<std::string> content_store;
    role_store.reserve(msg_count + 1);
    content_store.reserve(msg_count + 1);

    // System message first
    role_store.push_back("system");
    content_store.push_back(system_prompt);

    // Chat history
    for (int i = 0; i < msg_count; i++) {
        jstring jrole = (jstring)env->GetObjectArrayElement(jroles, i);
        jstring jcontent = (jstring)env->GetObjectArrayElement(jcontents, i);

        const char* role_cstr = env->GetStringUTFChars(jrole, nullptr);
        const char* content_cstr = env->GetStringUTFChars(jcontent, nullptr);

        role_store.push_back(std::string(role_cstr));
        content_store.push_back(std::string(content_cstr));

        env->ReleaseStringUTFChars(jrole, role_cstr);
        env->ReleaseStringUTFChars(jcontent, content_cstr);
    }

    // Build llama_chat_message array
    std::vector<llama_chat_message> messages(role_store.size());
    for (size_t i = 0; i < role_store.size(); i++) {
        messages[i].role    = role_store[i].c_str();
        messages[i].content = content_store[i].c_str();
    }

    // ---- Apply chat template ----
    // Use the model's built-in chat template (auto-detection).
    // llama.cpp supports Qwen's ChatML template natively.
    const char* tmpl_to_use = nullptr;

    // First call: get required buffer size
    int32_t prompt_len = llama_chat_apply_template(
        tmpl_to_use, messages.data(), messages.size(), true, nullptr, 0);

    if (prompt_len < 0) {
        LOGE("llama_chat_apply_template failed, code: %d", prompt_len);
        return env->NewStringUTF("Error: chat template failed");
    }

    // Second call: fill the buffer
    std::vector<char> buf(prompt_len + 1, 0);
    llama_chat_apply_template(
        tmpl_to_use, messages.data(), messages.size(), true, buf.data(), buf.size());

    std::string prompt(buf.data(), prompt_len);

    LOG_PROMPT("Formatted prompt (%d chars):\n%s", (int)prompt.size(), prompt.c_str());

    // ---- SMART KV CACHE REUSE ----

    int n_past = 0;
    if (!g_last_prompt.empty() && prompt.find(g_last_prompt) == 0) {
        n_past = g_last_token_count;
        LOG_CACHE("Reusing KV cache: %d tokens", n_past);
    } else {
        llama_memory_clear(llama_get_memory(g_ctx), true);
        LOG_CACHE("Cache cleared — prompt changed");
        n_past = 0;
        g_last_prompt = "";
        g_last_token_count = 0;
    }

    std::vector<llama_token> all_tokens = common_tokenize(g_ctx, prompt, true, true);
    if (all_tokens.empty()) return env->NewStringUTF("");

    // Tokens we actually need to decode (the ones not in cache)
    std::vector<llama_token> new_tokens;
    if (n_past < (int)all_tokens.size()) {
        new_tokens.assign(all_tokens.begin() + n_past, all_tokens.end());
    }

    LOG_INFER("Total tokens: %zu, New to decode: %zu", all_tokens.size(), new_tokens.size());

    if (!new_tokens.empty()) {
        llama_batch batch = llama_batch_init(new_tokens.size(), 0, 1);
        for (size_t i = 0; i < new_tokens.size(); i++) {
            common_batch_add(batch, new_tokens[i], n_past + i, {0}, i == new_tokens.size() - 1);
        }

        int64_t start_eval = ggml_time_us();
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("Decode failed");
            llama_batch_free(batch);
            return env->NewStringUTF("Error");
        }
        LOG_INFER("Prompt decode took %lld ms", (long long)((ggml_time_us() - start_eval) / 1000));
        llama_batch_free(batch);
    }

    common_params_sampling sparams;
    sparams.temp           = temperature;
    sparams.top_k          = 40;
    sparams.top_p          = 0.90f;
    sparams.min_p          = 0.05f;
    sparams.penalty_repeat = 1.15f;
    sparams.penalty_last_n = 64;

    common_sampler* sampler = common_sampler_init(g_model, sparams);

    std::string response;
    llama_token token = common_sampler_sample(sampler, g_ctx, -1);
    common_sampler_accept(sampler, token, true);

    int n_predict = 0;
    int n_cur = all_tokens.size();

    llama_batch run_batch = llama_batch_init(1, 0, 1);

    while (n_predict < max_tokens) {
        if (g_cancel_inference.load()) {
            LOG_INFER("Inference cancelled by user");
            break;
        }

        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), token)) break;

        std::string piece = common_token_to_piece(g_ctx, token);
        response += piece;

        // Stream the token back to Java
        jstring jpiece = env->NewStringUTF(piece.c_str());
        env->CallVoidMethod(jcallback, onTokenMethod, jpiece);
        env->DeleteLocalRef(jpiece);

        // Check for role-leak / stop patterns — trim at earliest match and stop
        {
            std::vector<std::string> stops = {
                "<|user|>", "<|assistant|>", "<|system|>",
                "<|endoftext|>", "</s>", "<|end|>",
                "<|im_start|>", "<|im_end|>",
                "User:", "Instruction:"
            };
            size_t earliest = std::string::npos;
            for (const auto& s : stops) {
                size_t pos = response.find(s);
                if (pos != std::string::npos && pos < earliest) {
                    earliest = pos;
                }
            }
            if (earliest != std::string::npos) {
                response.erase(earliest);
                LOG_INFER("Stop pattern hit, trimmed response at pos %zu", earliest);
                break;
            }
        }

        common_batch_clear(run_batch);
        common_batch_add(run_batch, token, n_cur++, {0}, true);

        if (llama_decode(g_ctx, run_batch) != 0) break;

        token = common_sampler_sample(sampler, g_ctx, -1);
        common_sampler_accept(sampler, token, true);
        n_predict++;
    }

    // Update state for next call
    g_last_prompt = prompt + response;
    g_last_token_count = n_cur;

    LOG_INFER("Generated %d tokens, response length: %zu chars", n_predict, response.length());

    common_sampler_free(sampler);
    llama_batch_free(run_batch);

    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_k7sunny_nexv1_AIManager_freeNative(JNIEnv*, jobject) {
    free_resources();
    llama_backend_free();
    LOG_MODEL("Freed all resources");
}

extern "C" JNIEXPORT void JNICALL
Java_com_k7sunny_nexv1_AIManager_cancelInferenceNative(JNIEnv*, jobject) {
    g_cancel_inference.store(true);
    LOG_INFER("cancelInferenceNative called");
}