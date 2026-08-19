package com.k7sunny.nexv1;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private static final String PREF_NAME = "nex_prefs";
    private static final String KEY_SYSTEM_PERSONA = "system_persona";
    private static final String DEFAULT_PERSONA_FAST = "You are Nex, a professional offline AI assistant created by K7SUNNY.";

    private static final String DEFAULT_PERSONA_PRO = "You are Nex, a professional offline AI assistant created by K7SUNNY.\n"
            +
            "You help with programming, writing, and analytical tasks.\n" +
            "Keep responses concise and accurate.";

    private static final String DEFAULT_PERSONA_ULTRA = "You are Nex, a highly advanced offline AI assistant created by K7SUNNY.\n"
            +
            "You think deeply, formulate structured plans, write robust code, and analyze complex logical queries.\n" +
            "Format your output beautifully and keep it accurate.";

    private static final String DEFAULT_PERSONA_VISION = "You are Nex Vision, an advanced offline multimodal AI assistant powered by Qwen2.5-VL.\n"
            +
            "You analyze images, read screenshots and UI elements, perform OCR, answer visual questions, and provide detailed observations locally.";

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String getSelectedModel() {
        return prefs.getString("selected_model", "fast");
    }

    public void setSelectedModel(String model) {
        prefs.edit().putString("selected_model", model).apply();
    }

    private String getDefaultPersonaForModel(String model) {
        if ("pro".equals(model)) {
            return DEFAULT_PERSONA_PRO;
        } else if ("ultra".equals(model)) {
            return DEFAULT_PERSONA_ULTRA;
        } else if ("vision".equals(model)) {
            return DEFAULT_PERSONA_VISION;
        } else {
            return DEFAULT_PERSONA_FAST;
        }
    }

    public void setSystemPersona(String persona) {
        String model = getSelectedModel();
        prefs.edit().putString(KEY_SYSTEM_PERSONA + "_" + model, persona).apply();
    }

    public void resetSystemPersona() {
        String model = getSelectedModel();
        prefs.edit().remove(KEY_SYSTEM_PERSONA + "_" + model).apply();
    }

    public boolean isCustomPersonaSet() {
        String model = getSelectedModel();
        return prefs.contains(KEY_SYSTEM_PERSONA + "_" + model);
    }

    public String getSystemPersona() {
        String model = getSelectedModel();
        return prefs.getString(KEY_SYSTEM_PERSONA + "_" + model, getDefaultPersonaForModel(model));
    }

    public int getMaxTokens() {
        return prefs.getInt("max_tokens", 256);
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

    /**
     * FIX: per-session preference keys (currently just the manual-title flag)
     * were never cleaned up when a session was deleted, leaking entries in
     * SharedPreferences indefinitely. Call this whenever a chat session is
     * permanently deleted.
     */
    public void clearSessionData(String sessionId) {
        prefs.edit()
                .remove("manual_title_" + sessionId)
                .apply();
    }
}