package com.wheelbarrow.Scenes;

import com.wheelbarrow.Components.Helper.SceneType;
import javafx.scene.Scene;

public abstract class AppScene extends Scene {
    public abstract SceneType getSceneType();
    private static int BASE_WIDTH = 800;
    private static int BASE_HEIGHT = 600;
    
    public AppScene() {
        super(new javafx.scene.Group(), BASE_WIDTH, BASE_HEIGHT);
    }

    public AppScene(int width, int height) {
        super(new javafx.scene.Group(), width, height);
    }
    
    public AppScene(javafx.scene.Parent root, int width, int height) {
        super(root, width, height);
    }
}
