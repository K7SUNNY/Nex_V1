package com.k7sunny.nexv1;

import android.content.Context;
import android.util.Log;
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

        // Each transcript line is capped so 10 messages can never come close
        // to the native n_ctx (2048 tokens) even with a verbose conversation.
        public static final int TRANSCRIPT_MSG_CHAR_LIMIT = 300;

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

    /**
     * Renders the (windowed) conversation as a plain-text transcript.
     *
     * FIX (root cause of bad titles): title/drift used to replay the whole
     * conversation as multi-turn chat history with the instruction hidden in
     * the system prompt. The chat template then ends with a fresh assistant
     * turn, so a small model (Qwen 0.5B/1.5B) simply CONTINUES the
     * conversation — it answers the user's last message instead of emitting
     * a title or a YES/NO verdict. Embedding the transcript inside a single
     * user message, with the instruction as the LAST thing the model reads,
     * makes it behave like a classifier instead of a chat participant.
     */
    private String buildTranscript(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message m : windowMessages(messages)) {
            String speaker;
            if (m.getType() == Message.TYPE_USER) {
                speaker = "User";
            } else if (m.getType() == Message.TYPE_AI) {
                speaker = "Assistant";
            } else {
                continue;
            }
            String text = (m.getText() == null) ? "" : m.getText().trim();
            if (text.isEmpty()) continue;
            if (text.length() > Config.TRANSCRIPT_MSG_CHAR_LIMIT) {
                text = text.substring(0, Config.TRANSCRIPT_MSG_CHAR_LIMIT) + "...";
            }
            sb.append(speaker).append(": ").append(text).append("\n");
        }
        return sb.toString().trim();
    }

    // --- TitleGenerator (Section 10) ---
    public void generateTitle(List<Message> messages, TitleCallback callback) {
        if (!aiManager.isModelLoaded()) {
            Log.w(TAG, "Model not loaded — cannot generate title");
            callback.onTitleGenerated(null);
            return;
        }

        String transcript = buildTranscript(messages);
        if (transcript.isEmpty()) {
            callback.onTitleGenerated(null);
            return;
        }

        String titleSystemPrompt = "You write short, clean titles for chat conversations.";

        String instruction =
                "Here is a conversation:\n\n" +
                        transcript + "\n\n" +
                        "Write a short title (2-6 words) describing the main topic of this conversation.\n" +
                        "Reply with ONLY the title text. No quotes, no emojis, no explanation.";

        aiManager.runShortInference(
                titleSystemPrompt,
                new String[]{"user"},
                new String[]{instruction},
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

        // FIX: small models often add an explanation on a second line
        // ("Planning Your Day\n\nThis title reflects..."). Previously any
        // newline caused validateTitle() to reject the WHOLE response and no
        // title was ever set. Keep the first non-empty line instead.
        for (String line : clean.split("\\r?\\n")) {
            String candidate = line.trim();
            if (!candidate.isEmpty()) {
                clean = candidate;
                break;
            }
        }

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

        // FIX: overlong output used to be rejected outright by validateTitle()
        // (with MAX_GENERATION_TOKENS=24 the model regularly overruns 40
        // chars), so many conversations never got a title at all. Truncate at
        // a word boundary instead of discarding.
        if (clean.length() > Config.MAX_TITLE_LENGTH) {
            String cut = clean.substring(0, Config.MAX_TITLE_LENGTH);
            int lastSpace = cut.lastIndexOf(' ');
            clean = (lastSpace > 10) ? cut.substring(0, lastSpace) : cut;
            clean = clean.replaceAll("[\\p{Punct}\\s]+$", "");
        }

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

        String transcript = buildTranscript(messages);
        if (transcript.isEmpty()) {
            callback.onDriftDetected(false);
            return;
        }

        // Same single-user-message structure as generateTitle — see
        // buildTranscript() for why the instruction must come last.
        String driftSystemPrompt = "You are a strict classifier. You reply with only YES or NO.";

        String instruction =
                "Current chat title: \"" + currentTitle + "\"\n\n" +
                        "Conversation:\n" + transcript + "\n\n" +
                        "Has the conversation moved on to a completely different subject than the title?\n" +
                        "Reply with ONLY 'YES' or 'NO'.";

        aiManager.runShortInference(
                driftSystemPrompt,
                new String[]{"user"},
                new String[]{instruction},
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