package com.k7sunny.nexv1.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "memories")
public class MemoryEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String title;

    @NonNull
    public String content;

    @ColumnInfo(name = "is_pinned")
    public boolean isPinned;

    public int position;

    public MemoryEntity(@NonNull String title, @NonNull String content, boolean isPinned, int position) {
        this.title = title;
        this.content = content;
        this.isPinned = isPinned;
        this.position = position;
    }
}
