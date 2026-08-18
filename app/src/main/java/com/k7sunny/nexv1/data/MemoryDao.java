package com.k7sunny.nexv1.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import java.util.List;

@Dao
public abstract class MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insertMemoriesList(List<MemoryEntity> memories);

    @Update
    protected abstract void updateMemories(List<MemoryEntity> memories);

    @Delete
    protected abstract void deleteMemory(MemoryEntity memory);

    @Transaction
    public void syncMemories(List<MemoryEntity> newMemories) {
        List<MemoryEntity> existing = getAllMemories();
        
        // Map existing memories by lowercase trimmed content for fast lookup
        java.util.Map<String, MemoryEntity> existingMap = new java.util.HashMap<>();
        for (MemoryEntity m : existing) {
            existingMap.put(m.content.toLowerCase().trim(), m);
        }

        java.util.Set<Long> idsToKeep = new java.util.HashSet<>();
        java.util.List<MemoryEntity> inserts = new java.util.ArrayList<>();
        java.util.List<MemoryEntity> updates = new java.util.ArrayList<>();

        if (newMemories != null) {
            for (MemoryEntity newMem : newMemories) {
                String key = newMem.content.toLowerCase().trim();
                MemoryEntity match = existingMap.get(key);
                if (match != null) {
                    // Row matches existing. Update fields if they changed.
                    // Keep the existing primary key id to avoid deleting/re-inserting!
                    newMem.id = match.id;
                    idsToKeep.add(match.id);
                    
                    if (!match.title.equals(newMem.title) || 
                        match.isPinned != newMem.isPinned || 
                        match.position != newMem.position) {
                        updates.add(newMem);
                    }
                } else {
                    // New row, insert it
                    inserts.add(newMem);
                }
            }
        }

        // 1. Delete rows that are not in the new list
        for (MemoryEntity match : existing) {
            if (!idsToKeep.contains(match.id)) {
                deleteMemory(match);
            }
        }

        // 2. Perform updates
        if (!updates.isEmpty()) {
            updateMemories(updates);
        }

        // 3. Perform inserts
        if (!inserts.isEmpty()) {
            insertMemoriesList(inserts);
        }
    }

    @Query("SELECT * FROM memories ORDER BY position ASC, id ASC")
    public abstract List<MemoryEntity> getAllMemories();

    @Query("SELECT title || ' | ' || content FROM memories WHERE is_pinned = 1 ORDER BY position ASC, id ASC")
    public abstract List<String> getPinnedMemoryStrings();

    @Query("SELECT title || ' | ' || content FROM memories ORDER BY is_pinned DESC, position ASC, id ASC")
    public abstract List<String> getAllMemoryStrings();

    @Query("SELECT COUNT(*) FROM memories")
    public abstract int getMemoryCount();
}
