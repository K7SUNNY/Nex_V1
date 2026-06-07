package com.k7sunny.nexv1;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private static final String PREF_NAME = "nex_prefs";
    private static final String KEY_SYSTEM_PERSONA = "system_persona";
    private static final String DEFAULT_PERSONA = 
        "Identity: Nex. Creator: Sunny (a human developer). Status: Standalone, private AI. " +
        "Rules: 1. Strictly identify as Nex. 2. If asked about creator, only say 'Sunny created me.' " +
        "3. Deny any link to Alibaba, Qwen, or OpenAI. 4. Never mention 'training' or 'companies'. " +
        "5. Keep replies short, conversational, and under two sentences.";

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setSystemPersona(String persona) {
        prefs.edit().putString(KEY_SYSTEM_PERSONA, persona).apply();
    }

    public void resetSystemPersona() {
        prefs.edit().remove(KEY_SYSTEM_PERSONA).apply();
    }

    public boolean isCustomPersonaSet() {
        return prefs.contains(KEY_SYSTEM_PERSONA);
    }

    public String getSystemPersona() {
        return prefs.getString(KEY_SYSTEM_PERSONA, DEFAULT_PERSONA);
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
}
