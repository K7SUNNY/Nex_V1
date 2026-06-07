package com.k7sunny.nexv1.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public abstract class ChatHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void upsertSession(ChatSessionEntity session);

    @Insert
    protected abstract void insertMessages(List<ChatMessageEntity> messages);

    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId")
    protected abstract void deleteMessagesForSession(String sessionId);

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    protected abstract void deleteSessionRow(String sessionId);

    @Transaction
    public void replaceSession(ChatSessionEntity session, List<ChatMessageEntity> messages) {
        deleteMessagesForSession(session.id);
        upsertSession(session);
        insertMessages(messages);
    }

    @Transaction
    public void deleteSession(String sessionId) {
        deleteMessagesForSession(sessionId);
        deleteSessionRow(sessionId);
    }

    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    public abstract List<ChatSessionEntity> getSessions();

    @Query("SELECT DISTINCT s.* FROM chat_sessions s LEFT JOIN chat_messages m ON s.id = m.session_id WHERE s.title LIKE :query OR m.text LIKE :query ORDER BY s.timestamp DESC")
    public abstract List<ChatSessionEntity> searchSessions(String query);

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY position ASC, id ASC")
    public abstract List<ChatMessageEntity> getMessages(String sessionId);

    @Query("SELECT COUNT(*) FROM chat_sessions")
    public abstract int getSessionCount();

    @Query("UPDATE chat_sessions SET title = :newTitle WHERE id = :sessionId")
    public abstract int renameSession(String sessionId, String newTitle);
}
