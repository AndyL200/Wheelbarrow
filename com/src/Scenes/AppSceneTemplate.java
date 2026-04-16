package Scenes;

public class AppSceneTemplate extends javafx.scene.Scene implements AppScene {
    private static int BASE_WIDTH = 800;
    private static int BASE_HEIGHT = 600;

    public AppSceneTemplate() {
        super(new javafx.scene.Group(), BASE_WIDTH, BASE_HEIGHT);
    }

    public AppSceneTemplate(int width, int height) {
        super(new javafx.scene.Group(), width, height);
    }
    
    public AppSceneTemplate(javafx.scene.Parent root, int width, int height) {
        super(root, width, height);
    }
}
