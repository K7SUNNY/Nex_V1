package com.k7sunny.nexv1;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private static final String PREF_NAME = "nex_prefs";
    private static final String KEY_SYSTEM_PERSONA = "system_persona";
    private static final String KEY_SELECTED_PERSONA_PRESET = "selected_persona_preset";

    public static final String PERSONA_GENERAL = "general";
    public static final String PERSONA_CODE = "code";
    public static final String PERSONA_WRITER = "writer";
    public static final String PERSONA_TUTOR = "tutor";
    public static final String PERSONA_SUMMARIZER = "summarizer";
    public static final String PERSONA_CUSTOM = "custom";

    public static final String PROMPT_GENERAL =
            "You are Nex, an offline AI assistant. "
            + "Be direct and concise. Answer the user's questions helpfully. "
            + "If the user shares personal information, remember it for context.";

    public static final String PROMPT_CODE =
            "You are Nex, a software engineering assistant. "
            + "Write clean, correct code. Be concise. No unnecessary explanation.";

    public static final String PROMPT_WRITER =
            "You are Nex, a creative writing assistant. "
            + "Write expressively and imaginatively. Match the user's tone and genre.";

    public static final String PROMPT_TUTOR =
            "You are Nex, a patient tutor. "
            + "Explain concepts step by step using simple analogies. Ask questions to check understanding.";

    public static final String PROMPT_SUMMARIZER =
            "You are Nex, a summarization assistant. "
            + "Respond only with bullet points and key takeaways. Be extremely concise.";

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String getSelectedModel() {
        String model = prefs.getString("selected_model", "fast");
        if ("vision".equals(model)) {
            model = "fast";
            setSelectedModel(model);
        }
        return model;
    }

    public void setSelectedModel(String model) {
        prefs.edit().putString("selected_model", model).apply();
    }

    public String getSelectedPersonaKey() {
        return prefs.getString(KEY_SELECTED_PERSONA_PRESET, PERSONA_GENERAL);
    }

    public void setSelectedPersonaKey(String key) {
        prefs.edit().putString(KEY_SELECTED_PERSONA_PRESET, key).apply();
    }

    public String getPersonaName(String key) {
        switch (key) {
            case PERSONA_CODE: return "Code & Architecture Expert";
            case PERSONA_WRITER: return "Creative Writer";
            case PERSONA_TUTOR: return "Socratic Tutor";
            case PERSONA_SUMMARIZER: return "Concise Summarizer";
            case PERSONA_CUSTOM: return "Custom Persona";
            case PERSONA_GENERAL:
            default: return "General Assistant";
        }
    }

    public String getPersonaDescription(String key) {
        switch (key) {
            case PERSONA_CODE: return "Writes clean code, reviews architecture & debugs.";
            case PERSONA_WRITER: return "Storytelling, expressive prose and creative ideation.";
            case PERSONA_TUTOR: return "Step-by-step guidance, intuitive analogies and explanations.";
            case PERSONA_SUMMARIZER: return "Dense bullet points, key takeaways and no fluff.";
            case PERSONA_CUSTOM: return "Your own tailored instructions and prompt.";
            case PERSONA_GENERAL:
            default: return "Balanced, helpful and direct offline companion.";
        }
    }

    public String getPresetPrompt(String key) {
        switch (key) {
            case PERSONA_CODE: return PROMPT_CODE;
            case PERSONA_WRITER: return PROMPT_WRITER;
            case PERSONA_TUTOR: return PROMPT_TUTOR;
            case PERSONA_SUMMARIZER: return PROMPT_SUMMARIZER;
            case PERSONA_GENERAL: return PROMPT_GENERAL;
            case PERSONA_CUSTOM:
            default: return getCustomPersona();
        }
    }

    public String getCustomPersona() {
        String model = getSelectedModel();
        String persona = prefs.getString(KEY_SYSTEM_PERSONA + "_" + model, PROMPT_GENERAL);
        // Automatic migration if existing preference holds the legacy verbose prompt
        if (persona != null && persona.contains("helpful and professional offline AI assistant created by K7SUNNY")) {
            persona = PROMPT_GENERAL;
            prefs.edit().putString(KEY_SYSTEM_PERSONA + "_" + model, persona).apply();
        }
        return persona;
    }

    public void setCustomPersona(String persona) {
        String model = getSelectedModel();
        prefs.edit()
             .putString(KEY_SYSTEM_PERSONA + "_" + model, persona)
             .putString(KEY_SELECTED_PERSONA_PRESET, PERSONA_CUSTOM)
             .apply();
    }

    public void setSystemPersona(String persona) {
        setCustomPersona(persona);
    }

    public void resetSystemPersona() {
        String model = getSelectedModel();
        prefs.edit()
             .remove(KEY_SYSTEM_PERSONA + "_" + model)
             .putString(KEY_SELECTED_PERSONA_PRESET, PERSONA_GENERAL)
             .apply();
    }

    public boolean isCustomPersonaSet() {
        String model = getSelectedModel();
        return prefs.contains(KEY_SYSTEM_PERSONA + "_" + model);
    }

    public String getSystemPersona() {
        String key = getSelectedPersonaKey();
        if (PERSONA_CUSTOM.equals(key)) {
            return getCustomPersona();
        }
        return getPresetPrompt(key);
    }

    public int getMaxTokens() {
        int val = prefs.getInt("max_tokens", 2048);
        if (val < 512) {
            val = 2048;
            setMaxTokens(val);
        }
        return val;
    }

    public void setMaxTokens(int maxTokens) {
        prefs.edit().putInt("max_tokens", maxTokens).apply();
    }

    public float getTemperature() {
        return prefs.getFloat("temperature", 0.7f);
    }

    public void setTemperature(float temperature) {
        prefs.edit().putFloat("temperature", temperature).apply();
    }

    public boolean isHapticFeedbackEnabled() {
        return prefs.getBoolean("haptic_feedback", true);
    }

    public void setHapticFeedbackEnabled(boolean enabled) {
        prefs.edit().putBoolean("haptic_feedback", enabled).apply();
    }

    public int getContextWindow() {
        return prefs.getInt("context_window", 12);
    }

    public void setContextWindow(int size) {
        prefs.edit().putInt("context_window", size).apply();
    }

    public boolean isMemoryInitialized() {
        return prefs.getBoolean("memory_initialized", false);
    }

    public void setMemoryInitialized(boolean initialized) {
        prefs.edit().putBoolean("memory_initialized", initialized).apply();
    }

    public long getActiveDownloadId() {
        return prefs.getLong("active_download_id", -1);
    }

    public void setActiveDownloadId(long id) {
        prefs.edit().putLong("active_download_id", id).apply();
    }

    public boolean isSessionTitleManual(String sessionId) {
        return prefs.getBoolean("manual_title_" + sessionId, false);
    }

    public void setSessionTitleManual(String sessionId, boolean manual) {
        prefs.edit().putBoolean("manual_title_" + sessionId, manual).apply();
    }

    public void clearSessionData(String sessionId) {
        prefs.edit()
                .remove("manual_title_" + sessionId)
                .apply();
    }
}