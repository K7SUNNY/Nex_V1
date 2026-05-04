#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"
#include "common.h"
#include "sampling.h"

#define TAG "NexNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model* g_model = nullptr;

extern "C" JNIEXPORT jstring JNICALL
Java_com_k7sunny_nexv1_AIManager_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++ (NDK) with llama.cpp";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_k7sunny_nexv1_AIManager_initNative(JNIEnv* env, jobject /* this */) {
    llama_backend_init();
    LOGD("llama_backend_init called");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_k7sunny_nexv1_AIManager_loadModelNative(JNIEnv* env, jobject /* this */, jstring model_path) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGD("Loading model from: %s", path);

    llama_model_params model_params = llama_model_default_params();
    // Use mmap to improve load performance and memory behavior.
    model_params.use_mmap = true;

    g_model = llama_model_load_from_file(path, model_params);

    env->ReleaseStringUTFChars(model_path, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return 0;
    }

    LOGD("Model loaded successfully");
    return reinterpret_cast<jlong>(g_model);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_k7sunny_nexv1_AIManager_runInferenceNative(JNIEnv* env, jobject /* this */, jstring jprompt, jint max_tokens) {
    if (!g_model) {
        return env->NewStringUTF("Error: Model not loaded");
    }

    const char* prompt = env->GetStringUTFChars(jprompt, nullptr);
    LOGD("Processing prompt: %s", prompt);

    // Configure inference context parameters.
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048; // Good context size for this mobile setup.
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;

    llama_context* ctx = llama_init_from_model(g_model, ctx_params);
    if (!ctx) {
        env->ReleaseStringUTFChars(jprompt, prompt);
        LOGE("Failed to create llama_context");
        return env->NewStringUTF("Error: Failed to create context");
    }

    // Tokenize the input prompt.
    std::vector<llama_token> tokens = common_tokenize(ctx, prompt, true, true);
    env->ReleaseStringUTFChars(jprompt, prompt);

    if (tokens.empty()) {
        llama_free(ctx);
        return env->NewStringUTF("");
    }

    // Initialize a batch for prompt and generated tokens.
    llama_batch batch = llama_batch_init(tokens.size() + max_tokens, 0, 1);

    // Add prompt tokens to the initial batch.
    for (size_t i = 0; i < tokens.size(); ++i) {
        common_batch_add(batch, tokens[i], i, {0}, i == tokens.size() - 1);
    }

    // Run an initial decode pass on the prompt.
    if (llama_decode(ctx, batch) != 0) {
        LOGE("llama_decode failed for prompt");
        llama_batch_free(batch);
        llama_free(ctx);
        return env->NewStringUTF("Error: llama_decode failed");
    }

    // Configure sampling parameters.
    common_params_sampling sparams;
    sparams.temp = 0.7f;
    sparams.top_k = 40;
    sparams.top_p = 0.95f;

    common_sampler* sampler = common_sampler_init(g_model, sparams);

    std::string response = "";
    llama_token curr_token = common_sampler_sample(sampler, ctx, -1);
    common_sampler_accept(sampler, curr_token, true);

    int n_curr = tokens.size();
    int n_predict = 0;

    // Generate tokens until EOS or max token limit.
    while (n_predict < max_tokens) {
        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), curr_token)) {
            LOGD("EOS reached");
            break;
        }

        std::string piece = common_token_to_piece(ctx, curr_token);
        response += piece;

        // Prepare the next decode step with the sampled token.
        common_batch_clear(batch);
        common_batch_add(batch, curr_token, n_curr, {0}, true);

        if (llama_decode(ctx, batch) != 0) {
            LOGE("llama_decode failed during generation");
            break;
        }

        n_curr++;
        n_predict++;

        curr_token = common_sampler_sample(sampler, ctx, -1);
        common_sampler_accept(sampler, curr_token, true);
    }

    LOGD("Inference complete. Response length: %zu", response.length());

    // Release context and sampler resources.
    common_sampler_free(sampler);
    llama_batch_free(batch);
    llama_free(ctx);

    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_k7sunny_nexv1_AIManager_freeNative(JNIEnv* env, jobject /* this */) {
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
    LOGD("llama_backend_free called");
}
