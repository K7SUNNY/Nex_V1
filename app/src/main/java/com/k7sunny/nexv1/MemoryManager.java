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
                boolean migrated = false;
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String title = obj.getString("title");
                    String content = obj.getString("content");
                    boolean isPinned = obj.getBoolean("isPinned");

                    // Migrate old third-person default memory to first-person
                    if ("Nex prefers concise code examples and OLED dark mode themes.".equals(content)) {
                        content = "I prefer concise code examples and OLED dark mode themes.";
                        migrated = true;
                    }

                    list.add(new Memory(title, content, isPinned));
                }
                if (migrated) {
                    saveMemories(list);
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
