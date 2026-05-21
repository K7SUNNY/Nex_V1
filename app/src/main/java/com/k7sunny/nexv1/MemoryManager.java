package com.k7sunny.nexv1;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MemoryManager {
    private static final String PREF_NAME = "nex_memories";
    private static final String KEY_MEMORIES = "memories";
    private final SharedPreferences prefs;

    public MemoryManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveMemories(List<Memory> memories) {
        try {
            JSONArray array = new JSONArray();
            for (Memory m : memories) {
                JSONObject obj = new JSONObject();
                obj.put("title", m.getTitle());
                obj.put("content", m.getContent());
                obj.put("isPinned", m.isPinned());
                array.put(obj);
            }
            prefs.edit().putString(KEY_MEMORIES, array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Memory> getAllMemories() {
        List<Memory> list = new ArrayList<>();
        String json = prefs.getString(KEY_MEMORIES, null);
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    list.add(new Memory(
                        obj.getString("title"),
                        obj.getString("content"),
                        obj.getBoolean("isPinned")
                    ));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    public List<String> getPinnedMemoryStrings() {
        List<String> pinned = new ArrayList<>();
        for (Memory m : getAllMemories()) {
            if (m.isPinned()) {
                pinned.add(m.getContent());
            }
        }
        return pinned;
    }
}
