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
    // Written on the inference thread (loadModel/release) and read from the
    // UI thread (title/drift/memory triggers) — must be volatile or a stale
    // `false` silently skips title generation after the model has loaded.
    private volatile boolean isModelLoaded = false;
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
        this.executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(() -> {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
                runnable.run();
            }, "nex-inference-thread");
            thread.setPriority(Thread.MAX_PRIORITY);
            return thread;
        });
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

    public void runShortInference(
        String systemPrompt,
        String[] roles,
        String[] contents,
        int maxTokens,
        float temperature,
        ResponseCallback callback
    ) {
        if (!isModelLoaded) {
            Log.w(TAG_MODEL, "Model not loaded — cannot run short inference");
            mainHandler.post(() -> callback.onResponse(null));
            return;
        }

        executorService.execute(() -> {
            String response = runInferenceNative(
                systemPrompt,
                roles,
                contents,
                maxTokens,
                temperature,
                new ResponseCallback() {
                    @Override
                    public void onResponse(String r) {}
                    @Override
                    public void onToken(String token) {}
                }
            );
            String finalResponse = (response != null) ? response.trim() : null;
            mainHandler.post(() -> callback.onResponse(finalResponse));
        });
    }

    public void extractMemory(MemoryCallback callback) {
        if (!isModelLoaded) {
            Log.w(TAG_MODEL, "Model not loaded — cannot extract memory");
            callback.onMemoryExtracted(null, null);
            return;
        }

        executorService.execute(() -> {
            String memorySystemPrompt = 
                "You are a strict memory processor. Analyze the conversation.\n" +
                "Only extract a memory if the user EXPLICITLY states a personal fact, preference, hobby, or detail about themselves.\n" +
                "Format: \"[Topic] | User [fact/preference]\"\n" +
                "Topic: A short 1-2 word category.\n" +
                "Example 1: \"Coding | User prefers Kotlin over Java.\"\n" +
                "Example 2: \"Location | User lives in Tokyo.\"\n" +
                "If the user is just saying hi, asking a general question, or if NO personal user info is present, you MUST reply with ONLY the word \"NONE\".\n" +
                "DO NOT make up facts. DO NOT hallucinate. If you are unsure, reply with \"NONE\".\n" +
                "Negative Example: User says \"Hey!\" -> Output: \"NONE\"\n" +
                "Negative Example: User says \"What is the weather?\" -> Output: \"NONE\"";

            java.util.List<Message> contextMsgs = new java.util.ArrayList<>();
            synchronized (chatHistory) {
                int size = chatHistory.size();
                
                // EXTRA STRICT: If only 1-2 messages exist and they are just greetings, skip.
                if (size <= 2) {
                    boolean allGreetings = true;
                    for (Message m : chatHistory) {
                        String t = m.getText().toLowerCase().replaceAll("[^a-z]", "");
                        if (!t.equals("hi") && !t.equals("hello") && !t.equals("hey") && !t.equals("heynex")) {
                            allGreetings = false;
                            break;
                        }
                    }
                    if (allGreetings) {
                        mainHandler.post(() -> callback.onMemoryExtracted(null, null));
                        return;
                    }
                }

                int start = Math.max(0, size - 10);
                for (int i = start; i < size; i++) {
                    contextMsgs.add(chatHistory.get(i));
                }
            }

            if (contextMsgs.isEmpty()) {
                mainHandler.post(() -> callback.onMemoryExtracted(null, null));
                return;
            }

            String[] roles = new String[contextMsgs.size()];
            String[] contents = new String[contextMsgs.size()];
            for (int i = 0; i < contextMsgs.size(); i++) {
                Message msg = contextMsgs.get(i);
                roles[i] = (msg.getType() == Message.TYPE_USER) ? "user" : "assistant";
                contents[i] = msg.getText();
            }

            String response = runInferenceNative(
                memorySystemPrompt,
                roles,
                contents,
                64, // Increased maxTokens for more stable extraction
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
                
                // Improved parsing for variations (Pipe, Colon, Dash)
                String title = "Personal Detail";
                String content = clean;
                
                int pipeIndex = clean.indexOf('|');
                int colonIndex = clean.indexOf(':');
                int dashIndex = clean.indexOf(" - ");
                
                int splitIndex = -1;
                int splitLen = 1;
                
                if (pipeIndex != -1) {
                    splitIndex = pipeIndex;
                } else if (colonIndex != -1) {
                    splitIndex = colonIndex;
                } else if (dashIndex != -1) {
                    splitIndex = dashIndex;
                    splitLen = 3;
                }
                
                if (splitIndex != -1) {
                    title = clean.substring(0, splitIndex).trim();
                    content = clean.substring(splitIndex + splitLen).trim();
                }
                
                if (title.length() > 30) title = title.substring(0, 27) + "...";
                
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