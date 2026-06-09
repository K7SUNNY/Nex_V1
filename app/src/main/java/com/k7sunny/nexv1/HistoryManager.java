package com.k7sunny.nexv1;

import android.content.Context;
import com.k7sunny.nexv1.data.ChatHistoryDao;
import com.k7sunny.nexv1.data.ChatMessageEntity;
import com.k7sunny.nexv1.data.ChatSessionEntity;
import com.k7sunny.nexv1.data.NexDatabase;
import java.util.ArrayList;
import java.util.List;

public class HistoryManager {
    private final ChatHistoryDao chatHistoryDao;

    public HistoryManager(Context context) {
        NexDatabase db = NexDatabase.getDatabase(context);
        this.chatHistoryDao = db.chatHistoryDao();

        // Migrate legacy SharedPreferences data if present
        migrateLegacyData(context);
    }

    private void migrateLegacyData(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("chat_history", Context.MODE_PRIVATE);
        if (prefs.contains("sessions") && chatHistoryDao.getSessionCount() == 0) {
            try {
                String sessionsJson = prefs.getString("sessions", null);
                if (sessionsJson != null) {
                    org.json.JSONArray array = new org.json.JSONArray(sessionsJson);
                    for (int i = 0; i < array.length(); i++) {
                        org.json.JSONObject obj = array.getJSONObject(i);
                        String sessionId = obj.getString("id");
                        String title = obj.getString("title");
                        long timestamp = obj.getLong("timestamp");

                        ChatSessionEntity sessionEntity = new ChatSessionEntity(sessionId, title, timestamp);

                        List<ChatMessageEntity> messageEntities = new ArrayList<>();
                        String msgsJson = prefs.getString("msgs_" + sessionId, null);
                        if (msgsJson != null) {
                            org.json.JSONArray msgsArray = new org.json.JSONArray(msgsJson);
                            for (int j = 0; j < msgsArray.length(); j++) {
                                org.json.JSONObject msgObj = msgsArray.getJSONObject(j);
                                messageEntities.add(new ChatMessageEntity(
                                    sessionId,
                                    j,
                                    msgObj.getString("text"),
                                    msgObj.getInt("type")
                                ));
                            }
                        }

                        chatHistoryDao.replaceSession(sessionEntity, messageEntities);
                    }
                }
                // Clear the preferences after successful migration
                prefs.edit().clear().apply();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void saveSession(ChatSession session, List<Message> messages) {
        ChatSessionEntity sessionEntity = new ChatSessionEntity(
            session.getId(),
            session.getTitle(),
            session.getTimestamp()
        );

        List<ChatMessageEntity> messageEntities = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            ChatMessageEntity entity = new ChatMessageEntity(
                session.getId(),
                i,
                msg.getText(),
                msg.getType()
            );
            entity.memoryTag = msg.getMemoryTag();
            messageEntities.add(entity);
        }

        chatHistoryDao.replaceSession(sessionEntity, messageEntities);
    }

    public List<Message> getMessages(String sessionId) {
        List<ChatMessageEntity> entities = chatHistoryDao.getMessages(sessionId);
        List<Message> list = new ArrayList<>();
        for (ChatMessageEntity entity : entities) {
            Message msg = new Message(entity.text, entity.type);
            msg.setMemoryTag(entity.memoryTag);
            list.add(msg);
        }
        return list;
    }

    public List<ChatSession> getSessions() {
        List<ChatSessionEntity> entities = chatHistoryDao.getSessions();
        List<ChatSession> list = new ArrayList<>();
        for (ChatSessionEntity entity : entities) {
            list.add(new ChatSession(entity.id, entity.title, entity.timestamp));
        }
        return list;
    }

    public List<ChatSession> searchSessions(String query) {
        String likeQuery = "%" + query + "%";
        List<ChatSessionEntity> entities = chatHistoryDao.searchSessions(likeQuery);
        List<ChatSession> list = new ArrayList<>();
        for (ChatSessionEntity entity : entities) {
            list.add(new ChatSession(entity.id, entity.title, entity.timestamp));
        }
        return list;
    }

    public void deleteSession(String sessionId) {
        chatHistoryDao.deleteSession(sessionId);
    }

    public void renameSession(String sessionId, String newTitle) {
        chatHistoryDao.renameSession(sessionId, newTitle);
    }
}
