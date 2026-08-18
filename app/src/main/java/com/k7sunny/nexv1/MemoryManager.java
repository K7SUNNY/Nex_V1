package com.k7sunny.nexv1;

import android.content.Context;
import com.k7sunny.nexv1.data.MemoryDao;
import com.k7sunny.nexv1.data.MemoryEntity;
import com.k7sunny.nexv1.data.NexDatabase;
import java.util.ArrayList;
import java.util.List;

public class MemoryManager {
    private final MemoryDao memoryDao;

    public MemoryManager(Context context) {
        NexDatabase db = NexDatabase.getDatabase(context);
        this.memoryDao = db.memoryDao();

        // Migrate legacy SharedPreferences data if present
        migrateLegacyData(context);
    }

    private void migrateLegacyData(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("nex_memories", Context.MODE_PRIVATE);
        if (prefs.contains("memories") && memoryDao.getMemoryCount() == 0) {
            try {
                String memoriesJson = prefs.getString("memories", null);
                if (memoriesJson != null) {
                    org.json.JSONArray array = new org.json.JSONArray(memoriesJson);
                    List<MemoryEntity> entities = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        org.json.JSONObject obj = array.getJSONObject(i);
                        String title = obj.getString("title");
                        String content = obj.getString("content");
                        boolean isPinned = obj.getBoolean("isPinned");

                        content = normalizePersonReference(content);

                        entities.add(new MemoryEntity(title, content, isPinned, i));
                    }
                    if (!entities.isEmpty()) {
                        memoryDao.syncMemories(entities);
                    }
                }
                // Clear the preferences after successful migration
                prefs.edit().clear().apply();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void saveMemories(List<Memory> memories) {
        List<MemoryEntity> entities = new ArrayList<>();
        for (int i = 0; i < memories.size(); i++) {
            Memory m = memories.get(i);
            entities.add(new MemoryEntity(m.getTitle(), m.getContent(), m.isPinned(), i));
        }
        memoryDao.syncMemories(entities);
    }

    public List<Memory> getAllMemories() {
        List<MemoryEntity> entities = memoryDao.getAllMemories();
        List<Memory> list = new ArrayList<>();
        boolean migrated = false;
        for (MemoryEntity entity : entities) {
            String originalContent = entity.content;
            String normalizedContent = normalizePersonReference(originalContent);
            
            if (!originalContent.equals(normalizedContent)) {
                migrated = true;
            }
            list.add(new Memory(entity.title, normalizedContent, entity.isPinned));
        }
        if (migrated) {
            saveMemories(list);
        }
        return list;
    }

    private String normalizePersonReference(String content) {
        if (content == null) return null;
        
        String normalized = content;
        if (normalized.startsWith("Nex ")) {
            normalized = "I " + normalized.substring(4);
        } else if (normalized.equals("Nex")) {
            normalized = "I";
        }
        
        normalized = normalized.replaceAll("(?i)\\bUser's\\b", "Your");
        normalized = normalized.replaceAll("(?i)\\bUser\\b", "You");
        
        if (normalized.startsWith("you ")) {
            normalized = "You " + normalized.substring(4);
        } else if (normalized.equals("you")) {
            normalized = "You";
        }
        
        return normalized;
    }

    public List<String> getPinnedMemoryStrings() {
        return memoryDao.getPinnedMemoryStrings();
    }

    public List<String> getAllMemoryStrings() {
        return memoryDao.getAllMemoryStrings();
    }
}
