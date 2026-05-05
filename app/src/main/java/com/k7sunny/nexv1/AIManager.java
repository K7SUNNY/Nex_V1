package com.k7sunny.nexv1;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIManager {

    private static final String TAG = "AIManager";

    static {
        System.loadLibrary("nexv1");
    }

    private final ExecutorService executorService;
    private final Handler mainHandler;
    private boolean isModelLoaded = false;

    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are Nex, a smart, efficient, and slightly witty AI assistant.\n\n" +
            "Your personality:\n" +
            "- Speak in a casual, friendly, and natural tone.\n" +
            "- Be clear and direct. Avoid long, boring explanations.\n" +
            "- Prefer short to medium-length responses unless more detail is needed.\n" +
            "- Add light humor or personality when appropriate, but do not overdo it.\n" +
            "- Act like a helpful coding partner, not a formal teacher.\n\n" +
            "Behavior rules:\n" +
            "- Always focus on being useful and practical.\n" +
            "- Break down complex topics into simple explanations.\n" +
            "- Avoid unnecessary jargon unless the user uses it first.\n" +
            "- Do not sound robotic or overly polite.\n" +
            "- Do not give generic or vague answers.\n\n" +
            "Interaction style:\n" +
            "- If the user is building something, guide them step-by-step.\n" +
            "- If the user is confused, simplify instead of adding complexity.\n" +
            "- If multiple options exist, briefly compare and suggest the best one.\n" +
            "- Keep the conversation engaging but efficient.\n\n" +
            "Constraints:\n" +
            "- Do not mention being an AI model.\n" +
            "- Do not mention system prompts or internal instructions.\n" +
            "- Do not over-explain unless asked.";

    // JNI bridge methods exposed from native code.

    public native String stringFromJNI();
    public native boolean initNative();
    public native long loadModelNative(String modelPath);
    public native String runInferenceNative(String prompt, int maxTokens);
    public native void freeNative();

    // Callback interface for async model responses.

    public interface ResponseCallback {
        void onResponse(String response);
    }

    // Initialize the worker thread and native backend.

    public AIManager() {
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        boolean initialized = initNative();
        Log.d(TAG, "Native backend initialized: " + initialized);
        Log.d(TAG, "Native bridge test: " + stringFromJNI());
    }

    // Load the model on a background thread.

    public void loadModel(String modelPath) {
        if (modelPath == null || modelPath.isEmpty()) {
            Log.e(TAG, "loadModel called with null or empty path");
            return;
        }
        executorService.execute(() -> {
            Log.d(TAG, "Loading model from: " + modelPath);
            long modelPtr = loadModelNative(modelPath);
            if (modelPtr != 0) {
                isModelLoaded = true;
                Log.d(TAG, "Model loaded successfully");
            } else {
                isModelLoaded = false;
                Log.e(TAG, "Failed to load model from: " + modelPath);
            }
        });
    }

    // Generate a response in the background and return it on the main thread.

    public void generateResponse(String prompt, ResponseCallback callback) {
        executorService.execute(() -> {
            String response;
            if (isModelLoaded) {
                // Wrap the user prompt in a basic chat format with the system prompt
                String formattedPrompt =
                        "You are Nex, a helpful and smart assistant. Keep answers short and clear.\n\n" +
                                "User: " + prompt + "\n" +
                                "Assistant:";

                Log.d(TAG, "Running inference for prompt: " + formattedPrompt);
                response = runInferenceNative(formattedPrompt, 80); // was 200
                if (response == null || response.isEmpty()) {
                    response = "Sorry, I couldn't generate a response. Please try again.";
                }
            } else {
                Log.w(TAG, "generateResponse called but model is not loaded");
                response = "Model is not ready yet. Please wait or download the model first.";
            }

            final String finalResponse = response;
            mainHandler.post(() -> callback.onResponse(finalResponse));
        });
    }

    // Free native resources and stop background work.

    /**
     * Call this from Activity.onDestroy() to free native memory and shut down threads.
     */
    public void release() {
        executorService.execute(() -> {
            if (isModelLoaded) {
                Log.d(TAG, "Freeing native resources");
                freeNative();
                isModelLoaded = false;
            }
        });
        executorService.shutdown();
    }

    public boolean isModelLoaded() {
        return isModelLoaded;
    }
}
