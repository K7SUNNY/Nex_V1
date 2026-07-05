package com.k7sunny.nexv1;

import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class ConversationAnalyzer {
    private static final String TAG = "ConversationAnalyzer";

    // Section 11: Configurable parameters
    public static class Config {
        public static final int MAX_TITLE_LENGTH = 40;
        public static final int MIN_WORDS = 2;
        public static final int FALLBACK_MAX_LENGTH = 30;
        
        // AI Title parameters
        public static final float AI_TEMPERATURE = 0.3f;
        public static final int MAX_GENERATION_TOKENS = 16;
        
        // Delayed title triggers
        public static final int TRIGGER_MIN_USER_MESSAGES = 2; // Generate title after 2 user messages
        public static final int TRIGGER_CHAR_COUNT = 500;      // Or if total characters exceed 500
        
        // Topic drift parameters
        public static final int TOPIC_DRIFT_MESSAGE_INTERVAL = 5; // Re-evaluate every 5 user messages
        public static final float DRIFT_TEMPERATURE = 0.1f;
        public static final int DRIFT_MAX_TOKENS = 4;
    }

    public interface TitleCallback {
        void onTitleGenerated(String title);
    }

    public interface TopicDriftCallback {
        void onDriftDetected(boolean drifted);
    }

    private final AIManager aiManager;
    private final PreferenceManager preferenceManager;

    public ConversationAnalyzer(Context context, AIManager aiManager) {
        this.aiManager = aiManager;
        this.preferenceManager = new PreferenceManager(context);
    }

    // --- TitleGenerator (Section 10) ---
    public void generateTitle(List<Message> messages, TitleCallback callback) {
        if (!aiManager.isModelLoaded()) {
            Log.w(TAG, "Model not loaded — cannot generate title");
            callback.onTitleGenerated(null);
            return;
        }

        // Section 3: Stricter and more deterministic prompt
        String titleSystemPrompt = 
            "Generate a concise conversation title.\n\n" +
            "Requirements:\n" +
            "- 2–6 words\n" +
            "- Use Title Case\n" +
            "- No quotation marks\n" +
            "- No emojis\n" +
            "- No introductory text (do NOT output 'Title:', 'Topic:', etc.)\n" +
            "- No punctuation unless required\n" +
            "- Focus on the primary discussion topic, not just the first message.\n" +
            "- Return only the title.";

        // Build list of message roles and contents
        List<String> rolesList = new ArrayList<>();
        List<String> contentsList = new ArrayList<>();

        for (Message m : messages) {
            if (m.getType() == Message.TYPE_USER) {
                rolesList.add("user");
                contentsList.add(m.getText());
            } else if (m.getType() == Message.TYPE_AI) {
                rolesList.add("assistant");
                contentsList.add(m.getText());
            }
        }

        if (rolesList.isEmpty()) {
            callback.onTitleGenerated(null);
            return;
        }

        aiManager.runShortInference(
            titleSystemPrompt,
            rolesList.toArray(new String[0]),
            contentsList.toArray(new String[0]),
            Config.MAX_GENERATION_TOKENS,
            Config.AI_TEMPERATURE,
            new AIManager.ResponseCallback() {
                @Override
                public void onResponse(String rawResponse) {
                    if (rawResponse == null) {
                        callback.onTitleGenerated(null);
                        return;
                    }
                    
                    // Section 5: Expand the sanitization pipeline
                    String sanitized = sanitizeTitle(rawResponse);
                    
                    // Section 4: Strengthen output validation
                    if (validateTitle(sanitized)) {
                        callback.onTitleGenerated(sanitized);
                    } else {
                        Log.w(TAG, "Title validation failed for: " + sanitized);
                        callback.onTitleGenerated(null);
                    }
                }

                @Override
                public void onToken(String token) {}
            }
        );
    }

    // Section 5: Sanitization pipeline
    public String sanitizeTitle(String title) {
        if (title == null) return "";
        
        String clean = title.trim();
        
        // Remove surrounding quotes
        if (clean.startsWith("\"") && clean.endsWith("\"")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        if (clean.startsWith("'") && clean.endsWith("'")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        
        // Case insensitive removal of prefixes
        String[] prefixes = {"title:", "topic:", "conversation:", "summary:"};
        String lower = clean.toLowerCase();
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                clean = clean.substring(prefix.length()).trim();
                lower = clean.toLowerCase();
            }
        }
        
        // Remove leading/trailing punctuation and trailing periods
        clean = clean.replaceAll("^[\\p{Punct}\\s]+", "");
        clean = clean.replaceAll("[\\p{Punct}\\s]+$", "");
        
        // Collapse multiple spaces into one
        clean = clean.replaceAll("\\s+", " ");
        
        return clean.trim();
    }

    // Section 4: Output validation
    public boolean validateTitle(String title) {
        if (title == null || title.isEmpty()) return false;
        if (title.length() > Config.MAX_TITLE_LENGTH) return false;
        
        // Reject titles with less than MIN_WORDS
        String[] words = title.split("\\s+");
        if (words.length < Config.MIN_WORDS) return false;
        
        // Reject titles containing markdown, code blocks, URLs, or newlines
        if (title.contains("**") || title.contains("*") || title.contains("`") || title.contains("_")) return false;
        if (title.contains("http://") || title.contains("https://") || title.contains("www.")) return false;
        if (title.contains("\n") || title.contains("\r")) return false;
        
        // Reject prefixes
        String lower = title.toLowerCase();
        if (lower.startsWith("title:") || lower.startsWith("topic:") || lower.startsWith("summary:") || lower.startsWith("conversation:")) {
            return false;
        }
        
        return true;
    }

    // --- TopicDetector (Section 2 & 10) ---
    public void detectTopicDrift(List<Message> messages, String currentTitle, TopicDriftCallback callback) {
        if (!aiManager.isModelLoaded() || currentTitle == null || currentTitle.isEmpty()) {
            callback.onDriftDetected(false);
            return;
        }

        String driftPrompt = 
            "Analyze the conversation history. Has the primary subject or topic of discussion changed significantly from the current title: \"" + currentTitle + "\"?\n\n" +
            "Reply with YES if the topic has shifted to a completely different subject, or NO if the conversation is still generally on the same topic.\n" +
            "Output ONLY 'YES' or 'NO'.";

        List<String> rolesList = new ArrayList<>();
        List<String> contentsList = new ArrayList<>();

        for (Message m : messages) {
            if (m.getType() == Message.TYPE_USER) {
                rolesList.add("user");
                contentsList.add(m.getText());
            } else if (m.getType() == Message.TYPE_AI) {
                rolesList.add("assistant");
                contentsList.add(m.getText());
            }
        }

        aiManager.runShortInference(
            driftPrompt,
            rolesList.toArray(new String[0]),
            contentsList.toArray(new String[0]),
            Config.DRIFT_MAX_TOKENS,
            Config.DRIFT_TEMPERATURE,
            new AIManager.ResponseCallback() {
                @Override
                public void onResponse(String response) {
                    if (response == null) {
                        callback.onDriftDetected(false);
                        return;
                    }
                    String clean = response.trim().toUpperCase();
                    callback.onDriftDetected(clean.contains("YES"));
                }

                @Override
                public void onToken(String token) {}
            }
        );
    }

    // --- FallbackTitleGenerator (Section 6 & 10) ---
    public String generateFallbackTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.isEmpty()) {
            return "New Chat";
        }
        
        String text = firstMessage.trim();
        if (text.length() <= Config.FALLBACK_MAX_LENGTH) {
            return text;
        }
        
        // Find nearest word boundary within FALLBACK_MAX_LENGTH
        String sub = text.substring(0, Config.FALLBACK_MAX_LENGTH);
        int lastSpace = sub.lastIndexOf(' ');
        if (lastSpace > 10) { // Only cut at space if it leaves a reasonable string length
            return sub.substring(0, lastSpace).trim() + "...";
        }
        
        // Fallback to absolute cut if no space boundary is reasonable
        return text.substring(0, Config.FALLBACK_MAX_LENGTH - 3).trim() + "...";
    }

    // --- Eligibility Checks (Section 1 & 8) ---
    public boolean isEligibleForInitialTitle(List<Message> messages) {
        int userMsgCount = 0;
        int totalCharCount = 0;
        for (Message m : messages) {
            if (m.getType() == Message.TYPE_USER) {
                userMsgCount++;
                totalCharCount += m.getText().length();
            }
        }
        
        // Eligible if user message count is >= threshold OR total characters exceed trigger threshold
        return (userMsgCount >= Config.TRIGGER_MIN_USER_MESSAGES) || (totalCharCount >= Config.TRIGGER_CHAR_COUNT);
    }

    public boolean isEligibleForDriftCheck(List<Message> messages) {
        int userMsgCount = 0;
        for (Message m : messages) {
            if (m.getType() == Message.TYPE_USER) {
                userMsgCount++;
            }
        }
        // Check periodic drift only after intervals
        return userMsgCount > 0 && (userMsgCount % Config.TOPIC_DRIFT_MESSAGE_INTERVAL == 0);
    }
}
