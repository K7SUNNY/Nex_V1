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
    private final java.util.List<Message> chatHistory = new java.util.ArrayList<>();
    private static final int MAX_HISTORY = 12; // Keep last 6 rounds of chat
    private String systemPrompt =
        "You are Nex, a helpful assistant. Give short, direct answers in one or two sentences.";
    private final java.util.List<String> pinnedMemories = new java.util.ArrayList<>();

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
                chatHistory.add(new Message(cleanPrompt, Message.TYPE_USER));

                // 2. Build the full prompt using Zephyr template (TinyLlama-1.1B-Chat-v1.0)
                //    Format: <|system|>\n{msg}</s>\n<|user|>\n{msg}</s>\n<|assistant|>\n
                StringBuilder fullPrompt = new StringBuilder();

                // System turn — content directly followed by </s>, no extra newline
                fullPrompt.append("<|system|>\n");
                fullPrompt.append(systemPrompt);
                for (String memory : pinnedMemories) {
                    fullPrompt.append(" ").append(memory).append(".");
                }
                fullPrompt.append("</s>\n");

                // Conversation history
                for (Message m : chatHistory) {
                    if (m.getType() == Message.TYPE_USER) {
                        fullPrompt.append("<|user|>\n").append(m.getText()).append("</s>\n");
                    } else if (m.getType() == Message.TYPE_AI) {
                        fullPrompt.append("<|assistant|>\n").append(m.getText()).append("</s>\n");
                    }
                }

                // Start the assistant turn (model generates from here)
                fullPrompt.append("<|assistant|>\n");

                Log.d(TAG, "Full Prompt: " + fullPrompt);

                // Use the new signature with callback for streaming
                response = runInferenceNative(fullPrompt.toString(), 128, new ResponseCallback() {
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
                    chatHistory.add(new Message(response.trim(), Message.TYPE_AI));
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
            for (Message m : messages) {
                if (m.getType() == Message.TYPE_USER || m.getType() == Message.TYPE_AI) {
                    chatHistory.add(m);
                }
            }
            // Keep lean
            while (chatHistory.size() > MAX_HISTORY) {
                chatHistory.remove(0);
            }
            Log.d(TAG, "Chat history synchronized, size: " + chatHistory.size());
        });
    }

    public void setSystemPrompt(String prompt) {
        this.systemPrompt = prompt;
    }

    public void setMemories(java.util.List<String> memories) {
        this.pinnedMemories.clear();
        this.pinnedMemories.addAll(memories);
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