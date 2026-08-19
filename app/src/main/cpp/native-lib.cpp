#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include <mutex>
#include <stdexcept>
#include <android/log.h>
#include "llama.h"
#include "common.h"
#include "sampling.h"
#include "mtmd.h"
#include "mtmd-helper.h"

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
static mtmd_context* g_ctx_vision = nullptr;
static std::vector<llama_token> g_last_tokens;
static std::mutex g_mutex;
static std::atomic<bool> g_cancel_inference{false};

// RAII wrapper for llama_batch to ensure automatic freeing
struct llama_batch_raii {
    llama_batch batch;
    bool active;
    llama_batch_raii(int32_t n_tokens, int32_t embd, int32_t n_seq_max) {
        batch = llama_batch_init(n_tokens, embd, n_seq_max);
        active = true;
    }
    ~llama_batch_raii() {
        if (active) {
            llama_batch_free(batch);
        }
    }
    // Disable copy/move
    llama_batch_raii(const llama_batch_raii&) = delete;
    llama_batch_raii& operator=(const llama_batch_raii&) = delete;
};

// RAII wrapper for common_sampler to ensure automatic freeing
struct common_sampler_raii {
    common_sampler* sampler;
    common_sampler_raii(llama_model* model, common_params_sampling& sparams) {
        sampler = common_sampler_init(model, sparams);
    }
    ~common_sampler_raii() {
        if (sampler) {
            common_sampler_free(sampler);
        }
    }
    // Disable copy/move
    common_sampler_raii(const common_sampler_raii&) = delete;
    common_sampler_raii& operator=(const common_sampler_raii&) = delete;
};

// Helper: throw Java RuntimeException
static void throw_java_exception(JNIEnv* env, const char* message) {
    jclass clazz = env->FindClass("java/lang/RuntimeException");
    if (clazz) {
        env->ThrowNew(clazz, message);
        env->DeleteLocalRef(clazz);
    }
}

// Helper: check for stop patterns
static bool check_stop_patterns(std::string& response) {
    static const std::vector<std::string> stops = {
        "<|user|>", "<|assistant|>", "<|system|>",
        "<|endoftext|>", "</s>", "<|end|>",
        "<|im_start|>", "<|im_end|>", "<|eot_id|>",
        "<|vision_start|>", "<|vision_end|>",
        "User:", "Assistant:", "Instruction:"
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
        return true;
    }
    return false;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_k7sunny_nexv1_AIManager_stringFromJNI(
        JNIEnv* env,
        jclass) {
    try {
        std::string hello = "Hello from C++ (NDK) with llama.cpp and mtmd (Vision)";
        return env->NewStringUTF(hello.c_str());
    } catch (const std::exception& e) {
        LOGE("Exception in stringFromJNI: %s", e.what());
        throw_java_exception(env, e.what());
        return nullptr;
    } catch (...) {
        LOGE("Unknown exception in stringFromJNI");
        throw_java_exception(env, "Unknown exception in stringFromJNI");
        return nullptr;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_k7sunny_nexv1_AIManager_initNative(JNIEnv* env, jclass) {
    try {
        llama_backend_init();
        LOG_MODEL("Backend initialized");
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("Exception in initNative: %s", e.what());
        throw_java_exception(env, (std::string("Backend init error: ") + e.what()).c_str());
        return JNI_FALSE;
    } catch (...) {
        LOGE("Unknown exception in initNative");
        throw_java_exception(env, "Unknown native exception in initNative");
        return JNI_FALSE;
    }
}

static void free_resources() {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx_vision) {
        mtmd_free(g_ctx_vision);
        g_ctx_vision = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_last_tokens.clear();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_k7sunny_nexv1_AIManager_loadModelNative(JNIEnv* env, jclass, jstring model_path) {
    const char* path = nullptr;
    try {
        // Free existing resources first to prevent memory leaks when switching models
        free_resources();

        if (!model_path) {
            throw std::runtime_error("model_path parameter is null");
        }

        path = env->GetStringUTFChars(model_path, nullptr);
        if (!path) {
            throw std::runtime_error("Failed to get model path UTF string");
        }
        LOG_MODEL("Loading model from: %s", path);

        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = 0; // CPU only for stability

        std::lock_guard<std::mutex> lock(g_mutex);
        g_model = llama_model_load_from_file(path, model_params);
        env->ReleaseStringUTFChars(model_path, path);
        path = nullptr;

        if (!g_model) {
            throw std::runtime_error("llama_model_load_from_file failed (returned nullptr)");
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
        ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;

        g_ctx = llama_init_from_model(g_model, ctx_params);

        if (!g_ctx) {
            throw std::runtime_error("llama_init_from_model failed (returned nullptr)");
        }

        LOG_MODEL("Model + Context ready");
        return reinterpret_cast<jlong>(g_model);
    } catch (const std::exception& e) {
        LOGE("Exception in loadModelNative: %s", e.what());
        if (path && model_path) {
            env->ReleaseStringUTFChars(model_path, path);
        }
        free_resources();
        throw_java_exception(env, (std::string("Native error in loadModelNative: ") + e.what()).c_str());
        return 0;
    } catch (...) {
        LOGE("Unknown exception in loadModelNative");
        if (path && model_path) {
            env->ReleaseStringUTFChars(model_path, path);
        }
        free_resources();
        throw_java_exception(env, "Unknown native exception in loadModelNative");
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_k7sunny_nexv1_AIManager_loadVisionModelNative(
        JNIEnv* env, jclass,
        jstring model_path,
        jstring mmproj_path) {
    const char* m_path = nullptr;
    const char* p_path = nullptr;
    try {
        free_resources();

        if (!model_path) throw std::runtime_error("model_path parameter is null");
        if (!mmproj_path) throw std::runtime_error("mmproj_path parameter is null");

        m_path = env->GetStringUTFChars(model_path, nullptr);
        p_path = env->GetStringUTFChars(mmproj_path, nullptr);

        if (!m_path || !p_path) {
            throw std::runtime_error("Failed to get UTF strings for vision model paths");
        }

        LOG_MODEL("Loading vision model from: %s, mmproj from: %s", m_path, p_path);

        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = 0;

        std::lock_guard<std::mutex> lock(g_mutex);
        g_model = llama_model_load_from_file(m_path, model_params);
        if (!g_model) {
            throw std::runtime_error("llama_model_load_from_file failed for vision model");
        }

        int num_threads = (int)std::thread::hardware_concurrency();
        if (num_threads > 4) num_threads = 4;
        if (num_threads < 1) num_threads = 1;

        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = 4096; // 4096 tokens to accommodate vision tokens + conversation
        ctx_params.n_batch = 512;
        ctx_params.n_ubatch = 512;
        ctx_params.n_threads = num_threads;
        ctx_params.n_threads_batch = num_threads;
        ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;

        g_ctx = llama_init_from_model(g_model, ctx_params);
        if (!g_ctx) {
            throw std::runtime_error("llama_init_from_model failed for vision model context");
        }

        mtmd_context_params mparams = mtmd_context_params_default();
        mparams.use_gpu = false;
        mparams.n_threads = num_threads;
        mparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
        mparams.warmup = false;
        mparams.image_min_tokens = 64;
        mparams.image_max_tokens = 256; // Capped to 256 visual tokens for fast mobile ViT processing and low RAM

        g_ctx_vision = mtmd_init_from_file(p_path, g_model, mparams);
        if (!g_ctx_vision) {
            throw std::runtime_error("mtmd_init_from_file failed to load vision projector (mmproj)");
        }

        env->ReleaseStringUTFChars(model_path, m_path);
        env->ReleaseStringUTFChars(mmproj_path, p_path);
        m_path = nullptr;
        p_path = nullptr;

        LOG_MODEL("Nex Vision (Qwen2.5-VL + mmproj) ready");
        return reinterpret_cast<jlong>(g_model);
    } catch (const std::exception& e) {
        LOGE("Exception in loadVisionModelNative: %s", e.what());
        if (m_path && model_path) env->ReleaseStringUTFChars(model_path, m_path);
        if (p_path && mmproj_path) env->ReleaseStringUTFChars(mmproj_path, p_path);
        free_resources();
        throw_java_exception(env, (std::string("Native error in loadVisionModelNative: ") + e.what()).c_str());
        return 0;
    } catch (...) {
        LOGE("Unknown exception in loadVisionModelNative");
        if (m_path && model_path) env->ReleaseStringUTFChars(model_path, m_path);
        if (p_path && mmproj_path) env->ReleaseStringUTFChars(mmproj_path, p_path);
        free_resources();
        throw_java_exception(env, "Unknown native exception in loadVisionModelNative");
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_k7sunny_nexv1_AIManager_runInferenceNative(
    JNIEnv* env, jclass,
    jstring jsystemPrompt,
    jobjectArray jroles,
    jobjectArray jcontents,
    jint max_tokens,
    jfloat temperature,
    jobject jcallback) {

    jclass callbackClass = nullptr;
    const char* sys_cstr = nullptr;
    std::vector<std::pair<jstring, const char*>> acquired_role_strings;
    std::vector<std::pair<jstring, const char*>> acquired_content_strings;

    try {
        g_cancel_inference.store(false);

        std::unique_lock<std::mutex> lock(g_mutex);
        if (!g_model || !g_ctx) {
            return env->NewStringUTF("Error: Model not loaded");
        }

        // Get the callback class and method ID
        if (!jcallback) {
            throw std::runtime_error("jcallback parameter is null");
        }
        callbackClass = env->GetObjectClass(jcallback);
        if (!callbackClass) {
            throw std::runtime_error("Failed to get callback class");
        }
        jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
        if (!onTokenMethod) {
            throw std::runtime_error("Failed to find callback onToken method ID");
        }

        // ---- Build structured chat messages ----

        if (!jsystemPrompt) {
            throw std::runtime_error("jsystemPrompt is null");
        }
        sys_cstr = env->GetStringUTFChars(jsystemPrompt, nullptr);
        if (!sys_cstr) {
            throw std::runtime_error("Failed to get system prompt UTF chars");
        }
        std::string system_prompt(sys_cstr);
        env->ReleaseStringUTFChars(jsystemPrompt, sys_cstr);
        sys_cstr = nullptr;

        if (!jroles || !jcontents) {
            throw std::runtime_error("jroles or jcontents array is null");
        }

        int msg_count = env->GetArrayLength(jroles);
        if (msg_count != env->GetArrayLength(jcontents)) {
            throw std::runtime_error("jroles and jcontents array length mismatch");
        }

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
            if (!jrole || !jcontent) {
                throw std::runtime_error("Null message role or content in input array");
            }

            const char* role_cstr = env->GetStringUTFChars(jrole, nullptr);
            const char* content_cstr = env->GetStringUTFChars(jcontent, nullptr);

            if (!role_cstr || !content_cstr) {
                if (role_cstr) env->ReleaseStringUTFChars(jrole, role_cstr);
                if (content_cstr) env->ReleaseStringUTFChars(jcontent, content_cstr);
                env->DeleteLocalRef(jrole);
                env->DeleteLocalRef(jcontent);
                throw std::runtime_error("Failed to get message role or content UTF chars");
            }

            acquired_role_strings.push_back({jrole, role_cstr});
            acquired_content_strings.push_back({jcontent, content_cstr});

            role_store.push_back(std::string(role_cstr));
            content_store.push_back(std::string(content_cstr));
        }

        // Release the UTF chars and local references immediately
        for (auto& pair : acquired_role_strings) {
            env->ReleaseStringUTFChars(pair.first, pair.second);
            env->DeleteLocalRef(pair.first);
        }
        acquired_role_strings.clear();

        for (auto& pair : acquired_content_strings) {
            env->ReleaseStringUTFChars(pair.first, pair.second);
            env->DeleteLocalRef(pair.first);
        }
        acquired_content_strings.clear();

        // Build llama_chat_message array
        std::vector<llama_chat_message> messages(role_store.size());
        for (size_t i = 0; i < role_store.size(); i++) {
            messages[i].role    = role_store[i].c_str();
            messages[i].content = content_store[i].c_str();
        }

        // ---- Apply chat template ----
        const char* tmpl_to_use = nullptr;

        int32_t prompt_len = llama_chat_apply_template(
            tmpl_to_use, messages.data(), messages.size(), true, nullptr, 0);

        if (prompt_len < 0) {
            LOGE("llama_chat_apply_template failed, code: %d", prompt_len);
            throw std::runtime_error("llama_chat_apply_template failed");
        }

        std::vector<char> buf(prompt_len + 1, 0);
        llama_chat_apply_template(
            tmpl_to_use, messages.data(), messages.size(), true, buf.data(), buf.size());

        std::string prompt(buf.data(), prompt_len);

        LOG_PROMPT("Formatted prompt (%d chars):\n%s", (int)prompt.size(), prompt.c_str());

        // Tokenize full prompt
        std::vector<llama_token> all_tokens = common_tokenize(g_ctx, prompt, true, true);
        if (all_tokens.empty()) {
            if (callbackClass) env->DeleteLocalRef(callbackClass);
            return env->NewStringUTF("");
        }

        // ---- SMART KV CACHE REUSE (Token-based) ----

        int n_past = 0;
        // Compare token by token to find common prefix
        size_t max_reuse = std::min(all_tokens.size(), g_last_tokens.size());
        while (n_past < max_reuse && all_tokens[n_past] == g_last_tokens[n_past]) {
            n_past++;
        }

        if (n_past > 0) {
            LOG_CACHE("Reusing KV cache: %d tokens", n_past);
            llama_memory_seq_rm(llama_get_memory(g_ctx), 0, n_past, -1);
        } else {
            llama_memory_clear(llama_get_memory(g_ctx), true);
            LOG_CACHE("Cache cleared — no common prefix");
        }

        // Tokens we actually need to decode
        std::vector<llama_token> new_tokens(all_tokens.begin() + n_past, all_tokens.end());

        LOG_INFER("Total tokens: %zu, New to decode: %zu", all_tokens.size(), new_tokens.size());

        if (!new_tokens.empty()) {
            llama_batch_raii batch_wrapper(new_tokens.size(), 0, 1);
            for (size_t i = 0; i < new_tokens.size(); i++) {
                common_batch_add(batch_wrapper.batch, new_tokens[i], n_past + i, {0}, i == new_tokens.size() - 1);
            }

            int64_t start_eval = ggml_time_us();
            if (llama_decode(g_ctx, batch_wrapper.batch) != 0) {
                LOGE("Decode failed");
                llama_memory_clear(llama_get_memory(g_ctx), true);
                g_last_tokens.clear();
                throw std::runtime_error("llama_decode failed during prompt evaluation");
            }
            LOG_INFER("Prompt decode took %lld ms", (long long)((ggml_time_us() - start_eval) / 1000));
        }

        common_params_sampling sparams;
        sparams.temp           = temperature;
        sparams.top_k          = 40;
        sparams.top_p          = 0.90f;
        sparams.min_p          = 0.05f;
        sparams.penalty_repeat = 1.15f;
        sparams.penalty_last_n = 64;

        common_sampler_raii sampler_wrapper(g_model, sparams);

        std::string response;
        llama_token token = common_sampler_sample(sampler_wrapper.sampler, g_ctx, -1);
        common_sampler_accept(sampler_wrapper.sampler, token, true);

        int n_predict = 0;
        int n_cur = all_tokens.size();

        // Track tokens for next call
        std::vector<llama_token> current_tokens = all_tokens;
        bool cache_valid = true;

        llama_batch_raii run_batch_wrapper(1, 0, 1);

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
            if (jpiece) {
                env->CallVoidMethod(jcallback, onTokenMethod, jpiece);
                env->DeleteLocalRef(jpiece);
            }

            // Check for stop patterns
            if (check_stop_patterns(response)) {
                LOG_INFER("Stop pattern hit, trimmed response");
                cache_valid = false;
                break;
            }

            common_batch_clear(run_batch_wrapper.batch);
            common_batch_add(run_batch_wrapper.batch, token, n_cur++, {0}, true);
            current_tokens.push_back(token);

            if (llama_decode(g_ctx, run_batch_wrapper.batch) != 0) {
                cache_valid = false;
                break;
            }

            token = common_sampler_sample(sampler_wrapper.sampler, g_ctx, -1);
            common_sampler_accept(sampler_wrapper.sampler, token, true);
            n_predict++;
        }

        if (cache_valid) {
            g_last_tokens = current_tokens;
        } else {
            g_last_tokens.clear();
            llama_memory_clear(llama_get_memory(g_ctx), true);
        }

        LOG_INFER("Generated %d tokens, response length: %zu chars", n_predict, response.length());

        if (callbackClass) env->DeleteLocalRef(callbackClass);
        return env->NewStringUTF(response.c_str());
    } catch (const std::exception& e) {
        LOGE("Exception in runInferenceNative: %s", e.what());
        if (sys_cstr && jsystemPrompt) {
            env->ReleaseStringUTFChars(jsystemPrompt, sys_cstr);
        }
        for (auto& pair : acquired_role_strings) {
            env->ReleaseStringUTFChars(pair.first, pair.second);
            env->DeleteLocalRef(pair.first);
        }
        for (auto& pair : acquired_content_strings) {
            env->ReleaseStringUTFChars(pair.first, pair.second);
            env->DeleteLocalRef(pair.first);
        }
        if (callbackClass) env->DeleteLocalRef(callbackClass);
        throw_java_exception(env, (std::string("Native error in runInferenceNative: ") + e.what()).c_str());
        return env->NewStringUTF("Error: Native exception occurred");
    } catch (...) {
        LOGE("Unknown exception in runInferenceNative");
        if (sys_cstr && jsystemPrompt) {
            env->ReleaseStringUTFChars(jsystemPrompt, sys_cstr);
        }
        for (auto& pair : acquired_role_strings) {
            env->ReleaseStringUTFChars(pair.first, pair.second);
            env->DeleteLocalRef(pair.first);
        }
        for (auto& pair : acquired_content_strings) {
            env->ReleaseStringUTFChars(pair.first, pair.second);
            env->DeleteLocalRef(pair.first);
        }
        if (callbackClass) env->DeleteLocalRef(callbackClass);
        throw_java_exception(env, "Unknown native exception in runInferenceNative");
        return env->NewStringUTF("Error: Unknown native exception occurred");
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_k7sunny_nexv1_AIManager_runVisionInferenceNative(
    JNIEnv* env, jclass clazz,
    jstring jsystemPrompt,
    jobjectArray jroles,
    jobjectArray jcontents,
    jstring jimagePath,
    jint max_tokens,
    jfloat temperature,
    jobject jcallback) {

    // If no image path provided, delegate directly to text inference
    if (!jimagePath) {
        return Java_com_k7sunny_nexv1_AIManager_runInferenceNative(
            env, clazz, jsystemPrompt, jroles, jcontents, max_tokens, temperature, jcallback);
    }

    const char* img_cstr = env->GetStringUTFChars(jimagePath, nullptr);
    if (!img_cstr || std::string(img_cstr).empty()) {
        if (img_cstr) env->ReleaseStringUTFChars(jimagePath, img_cstr);
        return Java_com_k7sunny_nexv1_AIManager_runInferenceNative(
            env, clazz, jsystemPrompt, jroles, jcontents, max_tokens, temperature, jcallback);
    }

    std::string image_path(img_cstr);
    env->ReleaseStringUTFChars(jimagePath, img_cstr);
    img_cstr = nullptr;

    jclass callbackClass = nullptr;
    const char* sys_cstr = nullptr;
    std::vector<std::pair<jstring, const char*>> acquired_role_strings;
    std::vector<std::pair<jstring, const char*>> acquired_content_strings;

    try {
        g_cancel_inference.store(false);

        std::unique_lock<std::mutex> lock(g_mutex);
        if (!g_model || !g_ctx || !g_ctx_vision) {
            return env->NewStringUTF("Error: Vision model or mmproj projector not loaded");
        }

        if (!jcallback) {
            throw std::runtime_error("jcallback parameter is null");
        }
        callbackClass = env->GetObjectClass(jcallback);
        if (!callbackClass) {
            throw std::runtime_error("Failed to get callback class");
        }
        jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
        if (!onTokenMethod) {
            throw std::runtime_error("Failed to find callback onToken method ID");
        }

        // Build messages
        if (!jsystemPrompt) {
            throw std::runtime_error("jsystemPrompt is null");
        }
        sys_cstr = env->GetStringUTFChars(jsystemPrompt, nullptr);
        if (!sys_cstr) {
            throw std::runtime_error("Failed to get system prompt UTF chars");
        }
        std::string system_prompt(sys_cstr);
        env->ReleaseStringUTFChars(jsystemPrompt, sys_cstr);
        sys_cstr = nullptr;

        if (!jroles || !jcontents) {
            throw std::runtime_error("jroles or jcontents array is null");
        }

        int msg_count = env->GetArrayLength(jroles);
        if (msg_count != env->GetArrayLength(jcontents)) {
            throw std::runtime_error("jroles and jcontents array length mismatch");
        }

        std::vector<std::string> role_store;
        std::vector<std::string> content_store;
        role_store.reserve(msg_count + 1);
        content_store.reserve(msg_count + 1);

        // System message first
        role_store.push_back("system");
        content_store.push_back(system_prompt);

        const char* media_marker = mtmd_default_marker(); // "<__media__>"

        for (int i = 0; i < msg_count; i++) {
            jstring jrole = (jstring)env->GetObjectArrayElement(jroles, i);
            jstring jcontent = (jstring)env->GetObjectArrayElement(jcontents, i);
            if (!jrole || !jcontent) {
                throw std::runtime_error("Null message role or content in input array");
            }

            const char* role_cstr = env->GetStringUTFChars(jrole, nullptr);
            const char* content_cstr = env->GetStringUTFChars(jcontent, nullptr);

            if (!role_cstr || !content_cstr) {
                if (role_cstr) env->ReleaseStringUTFChars(jrole, role_cstr);
                if (content_cstr) env->ReleaseStringUTFChars(jcontent, content_cstr);
                env->DeleteLocalRef(jrole);
                env->DeleteLocalRef(jcontent);
                throw std::runtime_error("Failed to get message role or content UTF chars");
            }

            acquired_role_strings.push_back({jrole, role_cstr});
            acquired_content_strings.push_back({jcontent, content_cstr});

            std::string content(content_cstr);
            // If this is the last user message, attach the media marker if not present
            if (i == msg_count - 1 && std::string(role_cstr) == "user") {
                if (content.find(media_marker) == std::string::npos) {
                    content = std::string(media_marker) + "\n" + content;
                }
            }

            role_store.push_back(std::string(role_cstr));
            content_store.push_back(content);
        }

        for (auto& pair : acquired_role_strings) {
            env->ReleaseStringUTFChars(pair.first, pair.second);
            env->DeleteLocalRef(pair.first);
        }
        acquired_role_strings.clear();

        for (auto& pair : acquired_content_strings) {
            env->ReleaseStringUTFChars(pair.first, pair.second);
            env->DeleteLocalRef(pair.first);
        }
        acquired_content_strings.clear();

        std::vector<llama_chat_message> messages(role_store.size());
        for (size_t i = 0; i < role_store.size(); i++) {
            messages[i].role    = role_store[i].c_str();
            messages[i].content = content_store[i].c_str();
        }

        // Apply template
        const char* tmpl_to_use = nullptr;
        int32_t prompt_len = llama_chat_apply_template(
            tmpl_to_use, messages.data(), messages.size(), true, nullptr, 0);

        if (prompt_len < 0) {
            LOGE("llama_chat_apply_template failed for vision prompt, code: %d", prompt_len);
            throw std::runtime_error("llama_chat_apply_template failed for vision prompt");
        }

        std::vector<char> buf(prompt_len + 1, 0);
        llama_chat_apply_template(
            tmpl_to_use, messages.data(), messages.size(), true, buf.data(), buf.size());

        std::string prompt(buf.data(), prompt_len);
        LOG_PROMPT("Vision formatted prompt (%d chars):\n%s", (int)prompt.size(), prompt.c_str());

        // Load media bitmap using mtmd helper
        LOG_INFER("Loading image from file: %s", image_path.c_str());
        auto bitmap_wrapper = mtmd_helper_bitmap_init_from_file(g_ctx_vision, image_path.c_str(), false);
        if (!bitmap_wrapper.bitmap) {
            throw std::runtime_error("Failed to decode image from path: " + image_path);
        }

        uint32_t img_w = mtmd_bitmap_get_nx(bitmap_wrapper.bitmap);
        uint32_t img_h = mtmd_bitmap_get_ny(bitmap_wrapper.bitmap);
        LOG_INFER("Image loaded successfully, dimensions: %u x %u", img_w, img_h);

        // Tokenize prompt with image
        mtmd_input_text text_in;
        text_in.text          = prompt.c_str();
        text_in.text_len      = prompt.size();
        text_in.add_special   = false;
        text_in.parse_special = true;

        mtmd_input_chunks* chunks = mtmd_input_chunks_init();
        const mtmd_bitmap* bitmaps[1] = { bitmap_wrapper.bitmap };

        int64_t t_tok_start = ggml_time_ms();
        int32_t tok_res = mtmd_tokenize(g_ctx_vision, chunks, &text_in, bitmaps, 1);

        if (tok_res != 0) {
            mtmd_bitmap_free(bitmap_wrapper.bitmap);
            mtmd_input_chunks_free(chunks);
            LOGE("mtmd_tokenize failed with code %d (prompt_len=%zu, img_w=%u, img_h=%u)",
                 tok_res, prompt.size(), img_w, img_h);
            throw std::runtime_error("mtmd_tokenize failed to process vision prompt");
        }
        LOG_INFER("Vision prompt tokenized into %zu chunks in %lld ms",
                 mtmd_input_chunks_size(chunks), (long long)(ggml_time_ms() - t_tok_start));

        // Clear KV cache for clean multimodal evaluation
        llama_memory_clear(llama_get_memory(g_ctx), true);
        g_last_tokens.clear();

        // Evaluate chunks
        LOG_INFER("Evaluating vision chunks...");
        int64_t t_eval_start = ggml_time_ms();
        llama_pos n_past = 0;
        int32_t eval_res = mtmd_helper_eval_chunks(
            g_ctx_vision, g_ctx, chunks, 0, 0, 512, true, &n_past);

        mtmd_bitmap_free(bitmap_wrapper.bitmap);
        mtmd_input_chunks_free(chunks);

        if (eval_res != 0) {
            LOGE("mtmd_helper_eval_chunks failed with code %d", eval_res);
            throw std::runtime_error("mtmd_helper_eval_chunks failed during vision evaluation");
        }

        LOG_INFER("Vision prompt evaluated in %lld ms. Generating response from pos %d...",
                 (long long)(ggml_time_ms() - t_eval_start), (int)n_past);

        LOG_INFER("Vision prompt evaluated successfully. Starting generation from pos %d", (int)n_past);

        // Sampler setup
        common_params_sampling sparams;
        sparams.temp           = temperature;
        sparams.top_k          = 40;
        sparams.top_p          = 0.90f;
        sparams.min_p          = 0.05f;
        sparams.penalty_repeat = 1.15f;
        sparams.penalty_last_n = 64;

        common_sampler_raii sampler_wrapper(g_model, sparams);

        std::string response;
        llama_token token = common_sampler_sample(sampler_wrapper.sampler, g_ctx, -1);
        common_sampler_accept(sampler_wrapper.sampler, token, true);

        int n_predict = 0;
        llama_pos cur_pos = n_past;
        llama_batch_raii run_batch_wrapper(1, 0, 1);

        while (n_predict < max_tokens) {
            if (g_cancel_inference.load()) {
                LOG_INFER("Vision inference cancelled by user");
                break;
            }

            if (llama_vocab_is_eog(llama_model_get_vocab(g_model), token)) break;

            std::string piece = common_token_to_piece(g_ctx, token);
            response += piece;

            jstring jpiece = env->NewStringUTF(piece.c_str());
            if (jpiece) {
                env->CallVoidMethod(jcallback, onTokenMethod, jpiece);
                env->DeleteLocalRef(jpiece);
            }

            if (check_stop_patterns(response)) {
                LOG_INFER("Stop pattern hit in vision response");
                break;
            }

            common_batch_clear(run_batch_wrapper.batch);
            common_batch_add(run_batch_wrapper.batch, token, cur_pos++, {0}, true);

            if (llama_decode(g_ctx, run_batch_wrapper.batch) != 0) {
                LOGE("Decode failed during vision token generation");
                break;
            }

            token = common_sampler_sample(sampler_wrapper.sampler, g_ctx, -1);
            common_sampler_accept(sampler_wrapper.sampler, token, true);
            n_predict++;
        }

        LOG_INFER("Vision generated %d tokens, response: %zu chars", n_predict, response.length());

        if (callbackClass) env->DeleteLocalRef(callbackClass);
        return env->NewStringUTF(response.c_str());
    } catch (const std::exception& e) {
        LOGE("Exception in runVisionInferenceNative: %s", e.what());
        if (sys_cstr && jsystemPrompt) env->ReleaseStringUTFChars(jsystemPrompt, sys_cstr);
        for (auto& pair : acquired_role_strings) {
            env->ReleaseStringUTFChars(pair.first, pair.second);
            env->DeleteLocalRef(pair.first);
        }
        for (auto& pair : acquired_content_strings) {
            env->ReleaseStringUTFChars(pair.first, pair.second);
            env->DeleteLocalRef(pair.first);
        }
        if (callbackClass) env->DeleteLocalRef(callbackClass);
        throw_java_exception(env, (std::string("Native error in runVisionInferenceNative: ") + e.what()).c_str());
        return env->NewStringUTF("Error: Native vision inference failed");
    } catch (...) {
        LOGE("Unknown exception in runVisionInferenceNative");
        if (sys_cstr && jsystemPrompt) env->ReleaseStringUTFChars(jsystemPrompt, sys_cstr);
        for (auto& pair : acquired_role_strings) {
            env->ReleaseStringUTFChars(pair.first, pair.second);
            env->DeleteLocalRef(pair.first);
        }
        for (auto& pair : acquired_content_strings) {
            env->ReleaseStringUTFChars(pair.first, pair.second);
            env->DeleteLocalRef(pair.first);
        }
        if (callbackClass) env->DeleteLocalRef(callbackClass);
        throw_java_exception(env, "Unknown native exception in runVisionInferenceNative");
        return env->NewStringUTF("Error: Unknown native vision exception occurred");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_k7sunny_nexv1_AIManager_freeNative(JNIEnv* env, jclass) {
    try {
        free_resources();
        llama_backend_free();
        LOG_MODEL("Freed all resources");
    } catch (const std::exception& e) {
        LOGE("Exception in freeNative: %s", e.what());
        throw_java_exception(env, (std::string("Native error in freeNative: ") + e.what()).c_str());
    } catch (...) {
        LOGE("Unknown exception in freeNative");
        throw_java_exception(env, "Unknown native exception in freeNative");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_k7sunny_nexv1_AIManager_cancelInferenceNative(JNIEnv* env, jclass) {
    try {
        g_cancel_inference.store(true);
        LOG_INFER("cancelInferenceNative called");
    } catch (const std::exception& e) {
        LOGE("Exception in cancelInferenceNative: %s", e.what());
        throw_java_exception(env, (std::string("Native error in cancelInferenceNative: ") + e.what()).c_str());
    } catch (...) {
        LOGE("Unknown exception in cancelInferenceNative");
        throw_java_exception(env, "Unknown native exception in cancelInferenceNative");
    }
}
