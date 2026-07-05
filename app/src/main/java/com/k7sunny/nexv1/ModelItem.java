package com.k7sunny.nexv1;

public class ModelItem {
    private final String key;
    private final String name;
    private final String size;
    private final String description;
    private final int iconRes;

    public ModelItem(String key, String name, String size, String description, int iconRes) {
        this.key = key;
        this.name = name;
        this.size = size;
        this.description = description;
        this.iconRes = iconRes;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getSize() {
        return size;
    }

    public String getDescription() {
        return description;
    }

    public int getIconRes() {
        return iconRes;
    }
}
