package com.wheelbarrow.Components.Helper;

import javafx.scene.Scene;

public enum SceneType {
    CHAT (1),
    LOGIN (1 << 1),
    SETTINGS (1 << 2),
    CALL (1 << 3),
    DARK (1 << 4),
    LIGHT (1 << 5);

    private final int value;
    SceneType(int value) {
        this.value = value;
    }
    public int getValue() {
        return value;
    }
}
