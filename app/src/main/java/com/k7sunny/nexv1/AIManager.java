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
    private final java.util.List<Message> chatHistory = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private static final int MAX_HISTORY = 12; // Keep last 6 rounds of chat
    private volatile String systemPrompt = "";
    private final java.util.List<String> pinnedMemories = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private volatile int maxTokens = 256;
    private volatile float temperature = 0.7f;
    private volatile int contextWindowSize = 12;

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

        boolean initialized = false;
        String bridgeTest = "failed";
        try {
            initialized = initNative();
            bridgeTest = stringFromJNI();
        } catch (RuntimeException e) {
            Log.e(TAG_MODEL, "Native bridge initialization threw exception", e);
        }
        Log.d(TAG_MODEL, "Native backend initialized: " + initialized);
        Log.d(TAG_MODEL, "Native bridge test: " + bridgeTest);
    }

    public void loadModel(String modelPath) {
        if (modelPath == null || modelPath.isEmpty()) {
            Log.e(TAG_MODEL, "loadModel called with null or empty path");
            return;
        }

        executorService.execute(() -> {
            Log.d(TAG_MODEL, "Loading model from: " + modelPath);
            long modelPtr = 0;
            try {
                modelPtr = loadModelNative(modelPath);
            } catch (RuntimeException e) {
                Log.e(TAG_MODEL, "Native model load threw exception", e);
            }

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
                // 2. Build message arrays for native template formatting
                java.util.List<String> roles = new java.util.ArrayList<>();
                java.util.List<String> contents = new java.util.ArrayList<>();

                synchronized (chatHistory) {
                    chatHistory.add(new Message(cleanPrompt, Message.TYPE_USER));
                    for (Message m : chatHistory) {
                        if (m.getType() == Message.TYPE_USER) {
                            roles.add("user");
                            contents.add(m.getText());
                        } else if (m.getType() == Message.TYPE_AI) {
                            roles.add("assistant");
                            contents.add(m.getText());
                        }
                    }
                }

                // Merge pinned memories into the system prompt
                String systemWithMemories = systemPrompt;
                synchronized (pinnedMemories) {
                    if (!pinnedMemories.isEmpty()) {
                        StringBuilder sb = new StringBuilder(systemPrompt);
                        if (sb.length() > 0) {
                            sb.append("\n\n");
                        }
                        sb.append("Background facts about the person you are chatting with (referred to below as \"you\" — this is not your own name or identity):\n");
                        for (String memory : pinnedMemories) {
                            sb.append("- ").append(memory).append("\n");
                        }
                        systemWithMemories = sb.toString().trim();
                    }
                }

                Log.d(TAG_CHAT, "Sending " + roles.size() + " messages to native | system: " + systemWithMemories);

                // Let native C++ apply the model's chat template via llama_chat_apply_template
                try {
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
                } catch (RuntimeException e) {
                    Log.e(TAG_CHAT, "Native inference threw exception", e);
                    response = "Error: Native inference failed.";
                }

                if (response == null || response.trim().isEmpty()) {
                    response = "No response generated.";
                } else {
                    // 3. Add AI response to history
                    synchronized (chatHistory) {
                        chatHistory.add(new Message(response.trim(), Message.TYPE_AI));
                    }
                }

                // 4. Keep history lean (sliding window based on user preference)
                boolean dropped = false;
                synchronized (chatHistory) {
                    while (chatHistory.size() > contextWindowSize) {
                        chatHistory.remove(0);
                        dropped = true;
                    }
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
            String response = null;
            try {
                response = runInferenceNative(
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
            } catch (RuntimeException e) {
                Log.e(TAG_CHAT, "Native short inference threw exception", e);
            }
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

        StringBuilder transcriptBuilder = new StringBuilder();
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
                    callback.onMemoryExtracted(null, null);
                    return;
                }
            }

            int start = Math.max(0, size - 10);
            for (int i = start; i < size; i++) {
                Message msg = chatHistory.get(i);
                String speaker = (msg.getType() == Message.TYPE_USER) ? "User" : "Assistant";
                transcriptBuilder.append(speaker).append(": ").append(msg.getText()).append("\n");
            }
        }

        String transcript = transcriptBuilder.toString().trim();
        if (transcript.isEmpty()) {
            callback.onMemoryExtracted(null, null);
            return;
        }

        String memorySystemPrompt = "You are a strict memory processor.";

        String instruction =
            "Here is the recent chat conversation:\n\n" +
            transcript + "\n\n" +
            "Task: Extract any personal facts, preferences, hobbies, or details that the HUMAN USER (labeled as \"User\") explicitly states about themselves.\n" +
            "Rules:\n" +
            "1. ONLY extract information stated by the \"User\".\n" +
            "2. DO NOT extract any statements, claims, opinions, or responses made by the \"Assistant\".\n" +
            "3. Format the memory output strictly as: \"[Topic] | You [fact]\" (e.g. \"Coding | You prefer Kotlin over Java.\").\n" +
            "4. Refer to the user as \"You\". Never refer to the user as \"User\" or \"Sunny\".\n" +
            "5. If the User has not shared any new personal facts (e.g. they only asked a question, made a general statement, or greeted you), reply with ONLY the word \"NONE\".\n" +
            "6. DO NOT make up or hallucinate any facts.";

        runShortInference(
            memorySystemPrompt,
            new String[]{"user"},
            new String[]{instruction},
            64, // Increased maxTokens for more stable extraction
            0.2f, // lower temperature for stability
            new ResponseCallback() {
                @Override
                public void onResponse(String response) {
                    if (response == null || response.trim().isEmpty() || response.trim().equalsIgnoreCase("NONE")) {
                        callback.onMemoryExtracted(null, null);
                        return;
                    }

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
                    
                    String finalTitle = normalizePersonReference(title);
                    String finalContent = normalizePersonReference(content);
                    callback.onMemoryExtracted(finalTitle, finalContent);
                }

                @Override
                public void onToken(String token) {}
            }
        );
    }

    private String normalizePersonReference(String text) {
        if (text == null) return text;
        if (text.startsWith("User ")) {
            return "You " + text.substring(5);
        } else if (text.equals("User")) {
            return "You";
        }
        // catch mid-sentence leaks too
        return text.replaceAll("\\bUser\\b", "you");
    }

    public void setHistory(java.util.List<Message> messages) {
        executorService.execute(() -> {
            synchronized (chatHistory) {
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
        synchronized (pinnedMemories) {
            this.pinnedMemories.clear();
            this.pinnedMemories.addAll(memories);
        }
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public void clearHistory() {
        executorService.execute(() -> {
            synchronized (chatHistory) {
                chatHistory.clear();
            }
            Log.d(TAG_CHAT, "Chat history cleared");
        });
    }

    public void cancelInference() {
        try {
            cancelInferenceNative();
        } catch (RuntimeException e) {
            Log.e(TAG_MODEL, "cancelInferenceNative threw exception", e);
        }
    }

    public void release() {
        cancelInference();
        executorService.execute(() -> {
            if (isModelLoaded) {
                Log.d(TAG_MODEL, "Freeing native resources");
                try {
                    freeNative();
                } catch (RuntimeException e) {
                    Log.e(TAG_MODEL, "freeNative threw exception", e);
                }
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