package com.k7sunny.nexv1;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIManager {

    private static final String TAG = "NexAI";
    private static final String TAG_CHAT = "NexChat";
    private static final String TAG_MODEL = "NexModel";

    static {
        System.loadLibrary("nexv1");
    }

    private final ExecutorService executorService;
    private final Handler mainHandler;
    private boolean isModelLoaded = false;
    private final java.util.List<Message> chatHistory = new java.util.ArrayList<>();
    private static final int MAX_HISTORY = 12; // Keep last 6 rounds of chat
    private String systemPrompt =
        "You are Nex, a helpful AI assistant. Always reply directly in the first person. Keep your answers brief, under two sentences.";
    private final java.util.List<String> pinnedMemories = new java.util.ArrayList<>();

    // JNI bridge methods

    public native String stringFromJNI();
    public native boolean initNative();
    public native long loadModelNative(String modelPath);
    public native String runInferenceNative(String systemPrompt, String[] roles, String[] contents, int maxTokens, ResponseCallback callback);
    public native void freeNative();

    public interface ResponseCallback {
        void onResponse(String response);
        default void onToken(String token) {}
    }

    public AIManager() {
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        boolean initialized = initNative();
        Log.d(TAG_MODEL, "Native backend initialized: " + initialized);
        Log.d(TAG_MODEL, "Native bridge test: " + stringFromJNI());
    }

    public void loadModel(String modelPath) {
        if (modelPath == null || modelPath.isEmpty()) {
            Log.e(TAG_MODEL, "loadModel called with null or empty path");
            return;
        }

        executorService.execute(() -> {
            Log.d(TAG_MODEL, "Loading model from: " + modelPath);
            long modelPtr = loadModelNative(modelPath);

            if (modelPtr != 0) {
                isModelLoaded = true;
                Log.d(TAG_MODEL, "Model loaded successfully");
            } else {
                isModelLoaded = false;
                Log.e(TAG_MODEL, "Failed to load model");
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

                // 2. Build message arrays for native template formatting
                java.util.List<String> roles = new java.util.ArrayList<>();
                java.util.List<String> contents = new java.util.ArrayList<>();

                for (Message m : chatHistory) {
                    if (m.getType() == Message.TYPE_USER) {
                        roles.add("user");
                        contents.add(m.getText());
                    } else if (m.getType() == Message.TYPE_AI) {
                        roles.add("assistant");
                        contents.add(m.getText());
                    }
                }

                // Merge pinned memories into the system prompt
                String systemWithMemories = systemPrompt;
                if (!pinnedMemories.isEmpty()) {
                    StringBuilder sb = new StringBuilder(systemPrompt);
                    for (String memory : pinnedMemories) {
                        sb.append(" ").append(memory).append(".");
                    }
                    systemWithMemories = sb.toString();
                }

                Log.d(TAG_CHAT, "Sending " + roles.size() + " messages to native | system: " + systemWithMemories);

                // Let native C++ apply the model's chat template via llama_chat_apply_template
                response = runInferenceNative(
                    systemWithMemories,
                    roles.toArray(new String[0]),
                    contents.toArray(new String[0]),
                    256,
                    new ResponseCallback() {
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
                Log.w(TAG_MODEL, "Model not loaded — cannot generate");
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
            Log.d(TAG_CHAT, "History synced, size: " + chatHistory.size());
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
            Log.d(TAG_CHAT, "Chat history cleared");
        });
    }

    public void release() {
        executorService.execute(() -> {
            if (isModelLoaded) {
                Log.d(TAG_MODEL, "Freeing native resources");
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