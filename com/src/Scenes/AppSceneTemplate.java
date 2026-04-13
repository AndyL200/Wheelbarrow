package Scenes;

public class AppSceneTemplate extends javafx.scene.Scene implements AppScene {
    private static int BASE_WIDTH = 800;
    private static int BASE_HEIGHT = 600;

    public AppSceneTemplate() {
        super(new javafx.scene.Group(), BASE_WIDTH, BASE_HEIGHT);
    }
    
}
