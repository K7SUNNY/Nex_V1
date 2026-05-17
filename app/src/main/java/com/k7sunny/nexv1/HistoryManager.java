package com.k7sunny.nexv1;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class HistoryManager {
    private static final String PREF_NAME = "chat_history";
    private static final String KEY_SESSIONS = "sessions";
    private SharedPreferences prefs;

    public HistoryManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(ChatSession session, List<Message> messages) {
        List<ChatSession> sessions = getSessions();
        sessions.removeIf(s -> s.getId().equals(session.getId()));
        sessions.add(0, session);
        
        if (sessions.size() > 10) {
            sessions = sessions.subList(0, 10);
        }
        
        saveSessions(sessions);
        saveMessages(session.getId(), messages);
    }

    private void saveMessages(String sessionId, List<Message> messages) {
        try {
            JSONArray array = new JSONArray();
            for (Message m : messages) {
                JSONObject obj = new JSONObject();
                obj.put("text", m.getText());
                obj.put("type", m.getType());
                array.put(obj);
            }
            prefs.edit().putString("msgs_" + sessionId, array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Message> getMessages(String sessionId) {
        List<Message> list = new ArrayList<>();
        String json = prefs.getString("msgs_" + sessionId, null);
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    list.add(new Message(
                        obj.getString("text"),
                        obj.getInt("type")
                    ));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    public List<ChatSession> getSessions() {
        List<ChatSession> list = new ArrayList<>();
        String json = prefs.getString(KEY_SESSIONS, null);
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    list.add(new ChatSession(
                        obj.getString("id"),
                        obj.getString("title"),
                        obj.getLong("timestamp")
                    ));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    private void saveSessions(List<ChatSession> sessions) {
        try {
            JSONArray array = new JSONArray();
            for (ChatSession s : sessions) {
                JSONObject obj = new JSONObject();
                obj.put("id", s.getId());
                obj.put("title", s.getTitle());
                obj.put("timestamp", s.getTimestamp());
                array.put(obj);
            }
            prefs.edit().putString(KEY_SESSIONS, array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
