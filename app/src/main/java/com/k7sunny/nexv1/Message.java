package com.k7sunny.nexv1;

public class Message {
    public static final int TYPE_USER = 0;
    public static final int TYPE_AI = 1;
    public static final int TYPE_TYPING = 2;

    private String text;
    private int type;
    private boolean isActionsVisible = false;

    public Message(String text, int type) {
        this.text = text;
        this.type = type;
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
}