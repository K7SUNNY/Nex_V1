package com.k7sunny.nexv1;

public class Message {
    public static final int TYPE_USER = 0;
    public static final int TYPE_AI = 1;
    public static final int TYPE_TYPING = 2;

    private String text;
    private int type;
    private boolean isActionsVisible = false;
    private String memoryTag = null;
    private String imageUri = null;

    public Message(String text, int type) {
        this.text = text;
        this.type = type;
    }

    public Message(String text, int type, String imageUri) {
        this.text = text;
        this.type = type;
        this.imageUri = imageUri;
    }

    public Message(Message other) {
        this.text = other.text;
        this.type = other.type;
        this.isActionsVisible = other.isActionsVisible;
        this.memoryTag = other.memoryTag;
        this.imageUri = other.imageUri;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public boolean isActionsVisible() {
        return isActionsVisible;
    }

    public void setActionsVisible(boolean visible) {
        this.isActionsVisible = visible;
    }

    public String getMemoryTag() {
        return memoryTag;
    }

    public void setMemoryTag(String memoryTag) {
        this.memoryTag = memoryTag;
    }

    public String getImageUri() {
        return imageUri;
    }

    public void setImageUri(String imageUri) {
        this.imageUri = imageUri;
    }
}