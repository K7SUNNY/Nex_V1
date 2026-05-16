#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <android/log.h>
#include "llama.h"
#include "common.h"
#include "sampling.h"

#define TAG "NexNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static std::string g_last_prompt = "";
static int g_last_token_count = 0;

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
    LOGD("Backend initialized");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_k7sunny_nexv1_AIManager_loadModelNative(JNIEnv* env, jobject, jstring model_path) {

    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGD("Loading model from: %s", path);

    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;
    model_params.n_gpu_layers = 0; // CPU only for now to ensure stability

    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (!g_model) {
        LOGE("Model load failed");
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();

    // Optimize for big.LITTLE mobile CPUs.
    // Most Android phones have 4 big cores and 4 small cores.
    // Using 4 threads is often much faster than using 6 or 8 because it avoids the small cores.
    int num_threads = (int)std::thread::hardware_concurrency();
    if (num_threads > 4) num_threads = 4;
    if (num_threads < 1) num_threads = 1;

    LOGD("Initializing context with %d threads", num_threads);

    ctx_params.n_ctx = 2048; // Increased context size
    ctx_params.n_threads = num_threads;
    ctx_params.n_threads_batch = num_threads;

    g_ctx = llama_init_from_model(g_model, ctx_params);

    if (!g_ctx) {
        LOGE("Context creation failed");
        return 0;
    }

    LOGD("Model + Context ready");
    return reinterpret_cast<jlong>(g_model);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_k7sunny_nexv1_AIManager_runInferenceNative(JNIEnv* env, jobject, jstring jprompt, jint max_tokens, jobject jcallback) {

    if (!g_model || !g_ctx) {
        return env->NewStringUTF("Error: Model not loaded");
    }

    // Get the callback class and method ID
    jclass callbackClass = env->GetObjectClass(jcallback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");

    // SMART KV CACHE REUSE
    const char* prompt_cstr = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_cstr);
    env->ReleaseStringUTFChars(jprompt, prompt_cstr);

    int n_past = 0;
    if (!g_last_prompt.empty() && prompt.find(g_last_prompt) == 0) {
        n_past = g_last_token_count;
        LOGD("Reusing KV cache: %d tokens", n_past);
    } else {
        llama_memory_clear(llama_get_memory(g_ctx), true);
        LOGD("Prompt changed significantly, cleared KV cache");
        n_past = 0;
    }

    std::vector<llama_token> all_tokens = common_tokenize(g_ctx, prompt, true, true);
    if (all_tokens.empty()) return env->NewStringUTF("");

    // Tokens we actually need to decode (the ones not in cache)
    std::vector<llama_token> new_tokens;
    if (n_past < all_tokens.size()) {
        new_tokens.assign(all_tokens.begin() + n_past, all_tokens.end());
    }

    LOGD("Total tokens: %zu, New to decode: %zu", all_tokens.size(), new_tokens.size());

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
        LOGD("Initial prompt decode took %lld ms", (ggml_time_us() - start_eval) / 1000);
        llama_batch_free(batch);
    }

    common_params_sampling sparams;
    sparams.temp = 0.2f;
    sparams.top_k = 40;
    sparams.top_p = 0.9f;
    sparams.penalty_repeat = 1.15f;

    common_sampler* sampler = common_sampler_init(g_model, sparams);

    std::string response;
    llama_token token = common_sampler_sample(sampler, g_ctx, -1);
    common_sampler_accept(sampler, token, true);

    int n_predict = 0;
    int n_cur = all_tokens.size();

    llama_batch run_batch = llama_batch_init(1, 0, 1);

    while (n_predict < max_tokens) {
        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), token)) break;

        std::string piece = common_token_to_piece(g_ctx, token);
        response += piece;

        // Stream the token back to Java
        jstring jpiece = env->NewStringUTF(piece.c_str());
        env->CallVoidMethod(jcallback, onTokenMethod, jpiece);
        env->DeleteLocalRef(jpiece);

        if (response.find("<|user|>") != std::string::npos ||
            response.find("<|assistant|>") != std::string::npos ||
            response.find("<|system|>") != std::string::npos) {
            size_t pos;
            if ((pos = response.find("<|user|>")) != std::string::npos) response.erase(pos);
            if ((pos = response.find("<|assistant|>")) != std::string::npos) response.erase(pos);
            if ((pos = response.find("<|system|>")) != std::string::npos) response.erase(pos);
            break;
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

    LOGD("Response length: %zu", response.length());

    common_sampler_free(sampler);
    llama_batch_free(run_batch);

    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_k7sunny_nexv1_AIManager_freeNative(JNIEnv*, jobject) {

    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    llama_backend_free();
    LOGD("Freed all resources");
}