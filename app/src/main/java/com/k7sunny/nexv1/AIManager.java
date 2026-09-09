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
    private volatile boolean isVisionModel = false;
    private volatile String currentLoadedModelPath = null;
    private volatile String currentLoadedMmprojPath = null;
    private volatile boolean currentLoadedUseGpu = true;
    private final java.util.List<Message> chatHistory = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private static final int MAX_HISTORY = 12; // Keep last 6 rounds of chat
    private volatile String systemPrompt = "";
    private final java.util.List<String> pinnedMemories = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private volatile int maxTokens = 256;
    private volatile float temperature = 0.7f;
    private volatile int contextWindowSize = 12;
    private volatile boolean isCancelled = false;

    // JNI bridge methods

    public native String stringFromJNI();
    public native boolean initNative();
    public native boolean isGpuSupportedNative();
    public native long loadModelNative(String modelPath, boolean useGpu);
    public native long loadVisionModelNative(String modelPath, String mmprojPath, boolean useGpu);
    public native String runInferenceNative(String systemPrompt, String[] roles, String[] contents, int maxTokens, float temperature, ResponseCallback callback);
    public native String runVisionInferenceNative(String systemPrompt, String[] roles, String[] contents, String imagePath, int maxTokens, float temperature, ResponseCallback callback);
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
        loadModel(modelPath, true);
    }

    public void loadModel(String modelPath, boolean useGpu) {
        if (modelPath == null || modelPath.isEmpty()) {
            Log.e(TAG_MODEL, "loadModel called with null or empty path");
            return;
        }

        if (isModelLoaded && !isVisionModel && modelPath.equals(currentLoadedModelPath) && currentLoadedUseGpu == useGpu) {
            Log.d(TAG_MODEL, "Text model is already loaded and active (GPU=" + useGpu + "): " + modelPath + ", skipping reload.");
            return;
        }

        executorService.execute(() -> {
            if (isModelLoaded && !isVisionModel && modelPath.equals(currentLoadedModelPath) && currentLoadedUseGpu == useGpu) {
                return;
            }
            Log.d(TAG_MODEL, "Loading text model from: " + modelPath + " (GPU=" + useGpu + ")");
            isVisionModel = false;
            long modelPtr = 0;
            try {
                modelPtr = loadModelNative(modelPath, useGpu);
            } catch (RuntimeException e) {
                Log.e(TAG_MODEL, "Native model load threw exception", e);
            }

            if (modelPtr != 0) {
                isModelLoaded = true;
                currentLoadedModelPath = modelPath;
                currentLoadedMmprojPath = null;
                currentLoadedUseGpu = useGpu;
                Log.d(TAG_MODEL, "Model loaded successfully (GPU=" + useGpu + ")");
            } else {
                isModelLoaded = false;
                currentLoadedModelPath = null;
                Log.e(TAG_MODEL, "Failed to load model");
            }
        });
    }

    public void loadVisionModel(String modelPath, String mmprojPath) {
        loadVisionModel(modelPath, mmprojPath, true);
    }

    public void loadVisionModel(String modelPath, String mmprojPath, boolean useGpu) {
        if (modelPath == null || modelPath.isEmpty() || mmprojPath == null || mmprojPath.isEmpty()) {
            Log.e(TAG_MODEL, "loadVisionModel called with missing model or mmproj path");
            return;
        }

        if (isModelLoaded && isVisionModel && modelPath.equals(currentLoadedModelPath) && mmprojPath.equals(currentLoadedMmprojPath) && currentLoadedUseGpu == useGpu) {
            Log.d(TAG_MODEL, "Vision model is already loaded and active (GPU=" + useGpu + "), skipping reload.");
            return;
        }

        executorService.execute(() -> {
            if (isModelLoaded && isVisionModel && modelPath.equals(currentLoadedModelPath) && mmprojPath.equals(currentLoadedMmprojPath) && currentLoadedUseGpu == useGpu) {
                return;
            }
            Log.d(TAG_MODEL, "Loading vision model from: " + modelPath + ", mmproj: " + mmprojPath + " (GPU=" + useGpu + ")");
            isVisionModel = true;
            long modelPtr = 0;
            try {
                modelPtr = loadVisionModelNative(modelPath, mmprojPath, useGpu);
            } catch (RuntimeException e) {
                Log.e(TAG_MODEL, "Native vision model load threw exception", e);
            }

            if (modelPtr != 0) {
                isModelLoaded = true;
                currentLoadedModelPath = modelPath;
                currentLoadedMmprojPath = mmprojPath;
                currentLoadedUseGpu = useGpu;
                Log.d(TAG_MODEL, "Nex Vision model loaded successfully (GPU=" + useGpu + ")");
            } else {
                isModelLoaded = false;
                currentLoadedModelPath = null;
                currentLoadedMmprojPath = null;
                Log.e(TAG_MODEL, "Failed to load Nex Vision model");
            }
        });
    }

    public void generateResponse(String prompt, ResponseCallback callback) {
        generateResponse(prompt, null, callback);
    }

    public void generateResponse(String prompt, String imagePath, ResponseCallback callback) {
        executorService.execute(() -> {
            isCancelled = false;
            String response;

            if (isModelLoaded) {
                String cleanPrompt = prompt != null ? prompt.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "").trim() : "";

                // 1. Add User message to history
                // 2. Build message arrays for native template formatting
                java.util.List<String> roles = new java.util.ArrayList<>();
                java.util.List<String> contents = new java.util.ArrayList<>();

                synchronized (chatHistory) {
                    chatHistory.add(new Message(cleanPrompt, Message.TYPE_USER, imagePath));
                    int historySize = chatHistory.size();
                    for (int i = 0; i < historySize; i++) {
                        Message m = chatHistory.get(i);
                        boolean isCurrentTurn = (i == historySize - 1);
                        if (m.getType() == Message.TYPE_USER) {
                            roles.add("user");
                            String content = m.getText();
                            // If this is a historical turn (not the latest message being processed),
                            // condense the raw document stream to avoid compounding token bloat in KV cache!
                            if (!isCurrentTurn && content != null && content.startsWith("[Document:")) {
                                int docEnd = content.indexOf("```\n\n");
                                if (docEnd != -1 && docEnd + 5 < content.length()) {
                                    int docHeaderEnd = content.indexOf("]\n```");
                                    String docHeader = (docHeaderEnd != -1) ? content.substring(0, docHeaderEnd + 1) : "[Attached Document]";
                                    content = docHeader + " " + content.substring(docEnd + 5);
                                }
                            }
                            contents.add(content != null ? content : "");
                        } else if (m.getType() == Message.TYPE_AI) {
                            roles.add("assistant");
                            contents.add(m.getText() != null ? m.getText() : "");
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
                        sb.append("Background facts about the person you are chatting with (referred to below as \"User\"):\n");
                        for (String memory : pinnedMemories) {
                            sb.append("- ").append(memory).append("\n");
                        }
                        systemWithMemories = sb.toString().trim();
                    }
                }

                Log.d(TAG_CHAT, "Sending " + roles.size() + " messages to native (image=" + imagePath + ", isVision=" + isVisionModel + ") | system: " + systemWithMemories);

                // Let native C++ apply the model's chat template and run inference
                try {
                    ResponseCallback nativeTokenCallback = new ResponseCallback() {
                        @Override
                        public void onResponse(String response) {}

                        @Override
                        public void onToken(String token) {
                            mainHandler.post(() -> callback.onToken(token));
                        }
                    };

                    if (isVisionModel || (imagePath != null && !imagePath.isEmpty())) {
                        response = runVisionInferenceNative(
                            systemWithMemories,
                            roles.toArray(new String[0]),
                            contents.toArray(new String[0]),
                            imagePath,
                            maxTokens,
                            temperature,
                            nativeTokenCallback
                        );
                    } else {
                        response = runInferenceNative(
                            systemWithMemories,
                            roles.toArray(new String[0]),
                            contents.toArray(new String[0]),
                            maxTokens,
                            temperature,
                            nativeTokenCallback
                        );
                    }
                } catch (RuntimeException e) {
                    Log.e(TAG_CHAT, "Native inference threw exception", e);
                    response = "Error: Native inference failed.";
                }

                if (isCancelled || response == null || response.trim().isEmpty() || response.startsWith("Error:")) {
                    // Rollback trailing user turn to prevent consecutive [User, User] prompt structure
                    synchronized (chatHistory) {
                        if (!chatHistory.isEmpty() && chatHistory.get(chatHistory.size() - 1).getType() == Message.TYPE_USER) {
                            chatHistory.remove(chatHistory.size() - 1);
                            Log.d(TAG_CHAT, "Rolled back un-answered user message from chatHistory (cancelled=" + isCancelled + ")");
                        }
                    }
                    if (response == null || response.trim().isEmpty()) {
                        response = isCancelled ? "Generation stopped." : "No response generated.";
                    }
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

            int start = Math.max(0, size - 4);
            for (int i = start; i < size; i++) {
                Message msg = chatHistory.get(i);
                String speaker = (msg.getType() == Message.TYPE_USER) ? "User" : "Assistant";
                String text = msg.getText();
                if (text == null || text.trim().isEmpty()) continue;
                // If message starts with attached document blocks, strip or truncate for memory extraction
                if (text.startsWith("[Document:")) {
                    int docEnd = text.indexOf("```\n\n");
                    if (docEnd != -1 && docEnd + 5 < text.length()) {
                        text = text.substring(docEnd + 5);
                    }
                }
                if (text.length() > 250) {
                    text = text.substring(0, 250) + "...";
                }
                transcriptBuilder.append(speaker).append(": ").append(text).append("\n");
            }
        }

        String transcript = transcriptBuilder.toString().trim();
        if (transcript.isEmpty()) {
            callback.onMemoryExtracted(null, null);
            return;
        }

        String memorySystemPrompt = "You are a concise, accurate memory extractor.";

        String instruction =
            "Here is the recent chat conversation:\n\n" +
            transcript + "\n\n" +
            "Task: Extract any personal facts, plans, preferences, family/life events, work, or details shared by the HUMAN USER (labeled as \"User\").\n" +
            "Rules:\n" +
            "1. ONLY extract information stated by the \"User\". If the user says \"remember this\" or \"update memory\", extract the underlying fact, event, or plan they shared.\n" +
            "2. DO NOT extract any statements, claims, opinions, or responses made by the \"Assistant\".\n" +
            "3. Format the memory output strictly as: \"[Topic] | User [fact/plan]\" (e.g. \"Family | User is meeting their family next week.\").\n" +
            "4. Refer to the user as \"User\" in the fact content. Keep it concise (1 short sentence).\n" +
            "5. If the conversation contains NO personal facts, plans, or user details at all (e.g. only greetings or general questions like 'what is photosynthesis?'), reply with ONLY the word \"NONE\".\n" +
            "6. DO NOT make up or hallucinate any facts.\n\n" +
            "Examples:\n" +
            "- User: \"Hey i am going to meet my family next week!\" -> Family | User is meeting their family next week.\n" +
            "- User: \"I love playing guitar\" -> Hobbies | User plays guitar.\n" +
            "- User: \"I work as a software engineer in Delhi\" -> Occupation | User works as a software engineer in Delhi.\n" +
            "- User: \"What is the capital of France?\" -> NONE\n\n" +
            "Memory:";

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
        
        // Handle possessive replacements first
        String normalized = text.replaceAll("(?i)\\bYour\\b", "User's");
        
        // Handle normal replacements
        normalized = normalized.replaceAll("(?i)\\bYou\\b", "User");
        
        // Ensure starting reference is properly capitalized if it starts with "user"
        if (normalized.startsWith("user ")) {
            normalized = "User " + normalized.substring(5);
        } else if (normalized.equals("user")) {
            normalized = "User";
        }
        
        return normalized;
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
        isCancelled = true;
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
                currentLoadedModelPath = null;
                currentLoadedMmprojPath = null;
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

    public boolean isVisionModel() {
        return isVisionModel;
    }

    public boolean isGpuAccelerationActive() {
        return currentLoadedUseGpu && isModelLoaded;
    }
}