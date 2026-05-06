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

    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (!g_model) {
        LOGE("Model load failed");
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();

    // FIX: better performance tuning
    ctx_params.n_ctx = 512;
    int threads = std::thread::hardware_concurrency();
    ctx_params.n_threads = threads > 0 ? threads : 4;
    ctx_params.n_threads_batch = ctx_params.n_threads;

    g_ctx = llama_init_from_model(g_model, ctx_params);

    if (!g_ctx) {
        LOGE("Context creation failed");
        return 0;
    }

    LOGD("Model + Context ready");
    return reinterpret_cast<jlong>(g_model);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_k7sunny_nexv1_AIManager_runInferenceNative(JNIEnv* env, jobject, jstring jprompt, jint max_tokens) {

    if (!g_model || !g_ctx) {
        return env->NewStringUTF("Error: Model not loaded");
    }

    const char* prompt = env->GetStringUTFChars(jprompt, nullptr);
    LOGD("Prompt: %s", prompt);

    std::vector<llama_token> tokens = common_tokenize(g_ctx, prompt, true, true);
    env->ReleaseStringUTFChars(jprompt, prompt);

    if (tokens.empty()) return env->NewStringUTF("");

    llama_batch batch = llama_batch_init(tokens.size() + max_tokens, 0, 1);

    for (size_t i = 0; i < tokens.size(); i++) {
        common_batch_add(batch, tokens[i], i, {0}, i == tokens.size() - 1);
    }

    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("Decode failed");
        llama_batch_free(batch);
        return env->NewStringUTF("Error");
    }

    common_params_sampling sparams;
    sparams.temp = 0.6f;
    sparams.top_k = 30;
    sparams.top_p = 0.9f;

    common_sampler* sampler = common_sampler_init(g_model, sparams);

    std::string response;
    llama_token token = common_sampler_sample(sampler, g_ctx, -1);
    common_sampler_accept(sampler, token, true);

    int n_predict = 0;
    int n_cur = tokens.size();

    while (n_predict < max_tokens) {

        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), token)) break;

        std::string piece = common_token_to_piece(g_ctx, token);

        // ✅ FIX: stop BEFORE appending bad tokens
        if (piece.find("User:") != std::string::npos ||
            piece.find("Assistant:") != std::string::npos) {
            break;
        }

        response += piece;

        common_batch_clear(batch);
        common_batch_add(batch, token, n_cur++, {0}, true);

        if (llama_decode(g_ctx, batch) != 0) break;

        token = common_sampler_sample(sampler, g_ctx, -1);
        common_sampler_accept(sampler, token, true);

        n_predict++;
    }

    LOGD("Response length: %zu", response.length());

    common_sampler_free(sampler);
    llama_batch_free(batch);

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