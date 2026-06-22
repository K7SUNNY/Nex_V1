package com.k7sunny.nexv1;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private static final String PREF_NAME = "nex_prefs";
    private static final String KEY_SYSTEM_PERSONA = "system_persona";
    private static final String DEFAULT_PERSONA_FAST = 
        "You are the AI assistant named Nex. Sunny created Nex.\n" +
        "The other person is the User. You are NOT the User.\n" +
        "If asked who you are: \"I am Nex, a private AI created by Sunny.\"\n" +
        "If asked who the user is: \"You are the User.\" Do NOT say you are the user.\n" +
        "Keep answers under two sentences.";

    private static final String DEFAULT_PERSONA_PRO = 
        "You are Nex, a professional offline AI assistant created by Sunny.\n" +
        "You help with programming, writing, and analytical tasks.\n" +
        "Keep responses concise and accurate.";

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

    public void setSystemPersona(String persona) {
        boolean isPro = "pro".equals(getSelectedModel());
        if (isPro) {
            prefs.edit().putString(KEY_SYSTEM_PERSONA + "_pro", persona).apply();
        } else {
            prefs.edit().putString(KEY_SYSTEM_PERSONA + "_fast", persona).apply();
        }
    }

    public void resetSystemPersona() {
        boolean isPro = "pro".equals(getSelectedModel());
        if (isPro) {
            prefs.edit().remove(KEY_SYSTEM_PERSONA + "_pro").apply();
        } else {
            prefs.edit().remove(KEY_SYSTEM_PERSONA + "_fast").apply();
        }
    }

    public boolean isCustomPersonaSet() {
        boolean isPro = "pro".equals(getSelectedModel());
        if (isPro) {
            return prefs.contains(KEY_SYSTEM_PERSONA + "_pro");
        } else {
            return prefs.contains(KEY_SYSTEM_PERSONA + "_fast");
        }
    }

    public String getSystemPersona() {
        boolean isPro = "pro".equals(getSelectedModel());
        if (isPro) {
            return prefs.getString(KEY_SYSTEM_PERSONA + "_pro", DEFAULT_PERSONA_PRO);
        } else {
            return prefs.getString(KEY_SYSTEM_PERSONA + "_fast", DEFAULT_PERSONA_FAST);
        }
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
}
