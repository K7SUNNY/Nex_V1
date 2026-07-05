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
        // Bumped from 16 -> 24: 16 tokens was too tight for a small model to
        // reliably land on a clean word boundary, which caused truncated
        // titles to fail validateTitle() and get silently discarded.
        public static final int MAX_GENERATION_TOKENS = 24;

        // FIX: title/drift generation used to send the ENTIRE conversation
        // history to the model with no windowing (unlike normal chat, which
        // respects PreferenceManager's context window, and memory extraction,
        // which caps itself to the last 4 messages). On long conversations
        // this could exceed the native n_ctx (2048 tokens) and cause
        // llama_decode to fail, silently returning "Error" from native code.
        // We now cap how many recent messages are sent for these lightweight
        // classification-style calls.
        public static final int MAX_CONTEXT_MESSAGES = 10;

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

    /**
     * Returns the most recent messages, capped at Config.MAX_CONTEXT_MESSAGES,
     * so title/drift requests can't blow past the model's context window on
     * long conversations.
     */
    private List<Message> windowMessages(List<Message> messages) {
        if (messages.size() <= Config.MAX_CONTEXT_MESSAGES) {
            return messages;
        }
        int start = messages.size() - Config.MAX_CONTEXT_MESSAGES;
        return messages.subList(start, messages.size());
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

        List<Message> windowed = windowMessages(messages);

        // Build list of message roles and contents
        List<String> rolesList = new ArrayList<>();
        List<String> contentsList = new ArrayList<>();

        for (Message m : windowed) {
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

                        // FIX: distinguish a genuine native inference failure from
                        // an ordinary "the model produced something we couldn't
                        // validate" case, instead of silently swallowing both.
                        if (isNativeErrorResponse(rawResponse)) {
                            Log.w(TAG, "Native inference error during title generation: " + rawResponse);
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

        // FIX: the old check rejected ANY title containing a single "*" or
        // "_" anywhere (e.g. "Fixing user_id Bug" or "C++ Tips*" would be
        // rejected outright). That's overly aggressive — plain words with an
        // underscore/asterisk are legitimate. We now only reject genuine
        // markdown artifacts: bold/italic markers and inline code fences.
        if (title.contains("**") || title.contains("__") || title.contains("`")) return false;
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

        List<Message> windowed = windowMessages(messages);

        List<String> rolesList = new ArrayList<>();
        List<String> contentsList = new ArrayList<>();

        for (Message m : windowed) {
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

                        // FIX: previously a native inference failure (the native
                        // layer returns the literal string "Error" on decode
                        // failure) was silently treated as "no drift detected",
                        // masking a real failure as a legitimate negative result.
                        if (isNativeErrorResponse(response)) {
                            Log.w(TAG, "Native inference error during drift detection: " + response);
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

    /**
     * The native layer (native-lib.cpp) returns plain strings like "Error",
     * "Error: chat template failed" or "Error: chat template failed" on
     * failure paths, instead of throwing. Detect those here so callers can
     * log/handle them distinctly from a normal (if unvalidated) response.
     */
    private boolean isNativeErrorResponse(String response) {
        String trimmed = response.trim();
        return trimmed.equalsIgnoreCase("Error") || trimmed.regionMatches(true, 0, "Error:", 0, 6)
                || trimmed.equalsIgnoreCase("Error: Model not loaded");
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