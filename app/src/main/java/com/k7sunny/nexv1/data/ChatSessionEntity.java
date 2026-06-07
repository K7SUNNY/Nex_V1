package com.k7sunny.nexv1.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_sessions")
public class ChatSessionEntity {
    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String title;

    public long timestamp;

    public ChatSessionEntity(@NonNull String id, @NonNull String title, long timestamp) {
        this.id = id;
        this.title = title;
        this.timestamp = timestamp;
    }
}
