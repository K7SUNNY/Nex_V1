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
    private String systemPrompt = "";
    private final java.util.List<String> pinnedMemories = new java.util.ArrayList<>();
    private int maxTokens = 256;
    private float temperature = 0.7f;
    private int contextWindowSize = 12;

    // JNI bridge methods

    public native String stringFromJNI();
    public native boolean initNative();
    public native long loadModelNative(String modelPath);
    public native String runInferenceNative(String systemPrompt, String[] roles, String[] contents, int maxTokens, float temperature, ResponseCallback callback);
    public native void cancelInferenceNative();
    public native void freeNative();

    public interface ResponseCallback {
        void onResponse(String response);
        default void onToken(String token) {}
        default void onContextDropped() {}
    }

    public interface TitleCallback {
        void onTitleGenerated(String title);
    }

    public interface MemoryCallback {
        void onMemoryExtracted(String title, String content);
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
                    if (sb.length() > 0) {
                        sb.append("\n\n");
                    }
                    sb.append("User facts and memories for reference:\n");
                    for (String memory : pinnedMemories) {
                        sb.append("- ").append(memory).append("\n");
                    }
                    systemWithMemories = sb.toString().trim();
                }

                Log.d(TAG_CHAT, "Sending " + roles.size() + " messages to native | system: " + systemWithMemories);

                // Let native C++ apply the model's chat template via llama_chat_apply_template
                response = runInferenceNative(
                    systemWithMemories,
                    roles.toArray(new String[0]),
                    contents.toArray(new String[0]),
                    maxTokens,
                    temperature,
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

                // 4. Keep history lean (sliding window based on user preference)
                boolean dropped = false;
                while (chatHistory.size() > contextWindowSize) {
                    chatHistory.remove(0);
                    dropped = true;
                }
                if (dropped) {
                    mainHandler.post(() -> callback.onContextDropped());
                }

            } else {
                Log.w(TAG_MODEL, "Model not loaded — cannot generate");
                response = "Model is not ready yet.";
            }

            String finalResponse = response;
            mainHandler.post(() -> callback.onResponse(finalResponse));
        });
    }

    public void generateTitle(String userPrompt, String aiResponse, TitleCallback callback) {
        if (!isModelLoaded) {
            Log.w(TAG_MODEL, "Model not loaded — cannot generate title");
            callback.onTitleGenerated(null);
            return;
        }

        executorService.execute(() -> {
            String titleSystemPrompt = "You are a title generator. Write a 3-5 word descriptive title for this conversation based on the exchange. Do not use quotes, punctuation, or any introductory phrases like 'Title:'. Output ONLY the title itself.";
            String[] roles = new String[]{"user", "assistant"};
            String[] contents = new String[]{userPrompt, aiResponse};

            String response = runInferenceNative(
                titleSystemPrompt,
                roles,
                contents,
                16, // maxTokens for title
                0.3f, // lower temperature for title stability
                new ResponseCallback() {
                    @Override
                    public void onResponse(String response) {
                    }
                    @Override
                    public void onToken(String token) {
                    }
                }
            );

            String finalResponse = (response != null) ? response.trim() : null;
            mainHandler.post(() -> callback.onTitleGenerated(finalResponse));
        });
    }

    public void extractMemory(String userPrompt, String aiResponse, MemoryCallback callback) {
        if (!isModelLoaded) {
            Log.w(TAG_MODEL, "Model not loaded — cannot extract memory");
            callback.onMemoryExtracted(null, null);
            return;
        }

        executorService.execute(() -> {
            String memorySystemPrompt = 
                "You are a memory processor. Analyze the conversation exchange.\n" +
                "If the user shares personal details, preferences, interests, or facts about themselves, extract a single memory in the format: \"Category | User [fact/preference]\"\n" +
                "Example: \"Coding Style | User prefers Kotlin over Java.\"\n" +
                "Example: \"Pets | User has a dog named Rex.\"\n" +
                "If there are no personal details to remember, output ONLY \"NONE\". Output no other text, explanation, or punctuation.";
            String[] roles = new String[]{"user", "assistant"};
            String[] contents = new String[]{userPrompt, aiResponse};

            String response = runInferenceNative(
                memorySystemPrompt,
                roles,
                contents,
                32, // maxTokens for memory
                0.2f, // lower temperature for stability
                new ResponseCallback() {
                    @Override
                    public void onResponse(String response) {}
                    @Override
                    public void onToken(String token) {}
                }
            );

            if (response != null && !response.trim().isEmpty() && !response.trim().equalsIgnoreCase("NONE")) {
                String clean = response.trim();
                int pipeIndex = clean.indexOf('|');
                String title;
                String content;
                if (pipeIndex != -1) {
                    title = clean.substring(0, pipeIndex).trim();
                    content = clean.substring(pipeIndex + 1).trim();
                } else {
                    title = "Personal Detail";
                    content = clean;
                }
                
                if (title.length() > 20) title = title.substring(0, 17) + "...";
                
                String finalTitle = title;
                String finalContent = content;
                mainHandler.post(() -> callback.onMemoryExtracted(finalTitle, finalContent));
            } else {
                mainHandler.post(() -> callback.onMemoryExtracted(null, null));
            }
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
            while (chatHistory.size() > contextWindowSize) {
                chatHistory.remove(0);
            }
            Log.d(TAG_CHAT, "History synced, size: " + chatHistory.size());
        });
    }

    public void setContextWindow(int size) {
        this.contextWindowSize = size;
    }

    public void setSystemPrompt(String prompt) {
        this.systemPrompt = prompt;
    }

    public void setMemories(java.util.List<String> memories) {
        this.pinnedMemories.clear();
        this.pinnedMemories.addAll(memories);
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public void clearHistory() {
        executorService.execute(() -> {
            chatHistory.clear();
            Log.d(TAG_CHAT, "Chat history cleared");
        });
    }

    public void cancelInference() {
        cancelInferenceNative();
    }

    public void release() {
        cancelInference();
        executorService.execute(() -> {
            if (isModelLoaded) {
                Log.d(TAG_MODEL, "Freeing native resources");
                freeNative();
                isModelLoaded = false;
            }
        });
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public boolean isModelLoaded() {
        return isModelLoaded;
    }
}