package com.k7sunny.nexv1;

public class ChatSession {
    private String id;
    private String title;
    private long timestamp;

    public ChatSession(String id, String title, long timestamp) {
        this.id = id;
        this.title = title;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public long getTimestamp() { return timestamp; }
}
