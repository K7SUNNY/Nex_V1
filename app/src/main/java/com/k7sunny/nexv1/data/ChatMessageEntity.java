package com.k7sunny.nexv1.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "chat_messages",
    foreignKeys = @ForeignKey(
        entity = ChatSessionEntity.class,
        parentColumns = "id",
        childColumns = "session_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {
        @Index(value = "session_id"),
        @Index(value = {"session_id", "position"}, unique = true)
    }
)
public class ChatMessageEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId;

    public int position;

    @NonNull
    public String text;

    public int type;

    public String memoryTag;

    @ColumnInfo(name = "image_uri")
    public String imageUri;

    public ChatMessageEntity(@NonNull String sessionId, int position, @NonNull String text, int type) {
        this.sessionId = sessionId;
        this.position = position;
        this.text = text;
        this.type = type;
    }
}
