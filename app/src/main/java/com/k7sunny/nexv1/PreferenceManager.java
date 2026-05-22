package com.k7sunny.nexv1;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private static final String PREF_NAME = "nex_prefs";
    private static final String KEY_SYSTEM_PERSONA = "system_persona";
    private static final String DEFAULT_PERSONA = 
        "You are Nex, a helpful AI assistant. Always reply directly in the first person. Keep your answers brief, under two sentences.";

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setSystemPersona(String persona) {
        prefs.edit().putString(KEY_SYSTEM_PERSONA, persona).apply();
    }

    public String getSystemPersona() {
        return prefs.getString(KEY_SYSTEM_PERSONA, DEFAULT_PERSONA);
    }
}
