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
    private final java.util.List<String> chatHistory = new java.util.ArrayList<>();
    private static final int MAX_HISTORY = 6; // Keep last 3 rounds of chat

    // JNI bridge methods

    public native String stringFromJNI();
    public native boolean initNative();
    public native long loadModelNative(String modelPath);
    public native String runInferenceNative(String prompt, int maxTokens, ResponseCallback callback);
    public native void freeNative();

    public interface ResponseCallback {
        void onResponse(String response);
        default void onToken(String token) {}
    }

    public AIManager() {
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        boolean initialized = initNative();
        Log.d(TAG, "Native backend initialized: " + initialized);
        Log.d(TAG, "Native bridge test: " + stringFromJNI());
    }

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
                Log.e(TAG, "Failed to load model");
            }
        });
    }

    public void generateResponse(String prompt, ResponseCallback callback) {
        executorService.execute(() -> {
            String response;

            if (isModelLoaded) {
                String cleanPrompt = prompt.trim();

                // 1. Add User message to history
                chatHistory.add("<|user|>\n" + cleanPrompt);

                // 2. Build the full prompt with history
                // Need to master the system prompt handling and maybe this <|system|> <|user|> <|assistant|> method is not understandable for the model of quantized GGUFs
                // The model likely treated those as plain text, not special chat tokens.

                StringBuilder fullPrompt = new StringBuilder("<|system|>\nYou are a helpful AI assistant.");
                for (String entry : chatHistory) {
                    fullPrompt.append("\n").append(entry);
                }
                fullPrompt.append("\n<|assistant|>\n");

                Log.d(TAG, "Running inference with history.");

                // Use the new signature with callback for streaming
                response = runInferenceNative(fullPrompt.toString(), 256, new ResponseCallback() {
                    @Override
                    public void onResponse(String response) {
                        // Not used directly in native, but kept for interface
                    }

                    @Override
                    public void onToken(String token) {
                        mainHandler.post(() -> callback.onToken(token));
                    }
                });

                if (response == null || response.trim().isEmpty()) {
                    response = "No response generated.";
                } else {
                    // 3. Add AI response to history
                    chatHistory.add("<|assistant|>\n" + response.trim());
                }

                // 4. Keep history lean (sliding window of 3 rounds)
                if (chatHistory.size() > MAX_HISTORY) {
                    chatHistory.remove(0); // Remove oldest user msg
                    chatHistory.remove(0); // Remove oldest AI resp
                }

            } else {
                Log.w(TAG, "Model not loaded");
                response = "Model is not ready yet.";
            }

            String finalResponse = response;
            mainHandler.post(() -> callback.onResponse(finalResponse));
        });
    }

    public void setHistory(java.util.List<Message> messages) {
        executorService.execute(() -> {
            chatHistory.clear();
            // Convert Message objects back to JNI prompt format
            for (Message m : messages) {
                if (m.getType() == Message.TYPE_USER) {
                    chatHistory.add("<|user|>\n" + m.getText());
                } else if (m.getType() == Message.TYPE_AI) {
                    chatHistory.add("<|assistant|>\n" + m.getText());
                }
            }
            // Keep lean
            while (chatHistory.size() > MAX_HISTORY) {
                chatHistory.remove(0);
            }
            Log.d(TAG, "Chat history synchronized, size: " + chatHistory.size());
        });
    }

    public void clearHistory() {
        executorService.execute(() -> {
            chatHistory.clear();
            Log.d(TAG, "Chat history cleared");
        });
    }

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