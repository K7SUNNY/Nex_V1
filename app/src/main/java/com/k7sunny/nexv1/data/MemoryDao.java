package com.k7sunny.nexv1.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public abstract class MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insertMemories(List<MemoryEntity> memories);

    @Query("DELETE FROM memories")
    protected abstract void deleteAllRows();

    @Transaction
    public void replaceMemories(List<MemoryEntity> memories) {
        deleteAllRows();
        insertMemories(memories);
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
