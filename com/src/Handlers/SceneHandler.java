package Handlers;

import javafx.scene.Scene;
import javafx.stage.Stage;
import Scenes.AppScene;
import Scenes.AppSceneTemplate;

public class SceneHandler {
    AppSceneTemplate currentScene;
    Stage stage;

    public SceneHandler(Stage stage, Scene scene) {
        this.stage = stage;
        this.currentScene = (AppSceneTemplate) scene;
    }   

    public SceneHandler(Stage stage, AppSceneTemplate scene)  {
        this.stage = stage;
        this.currentScene = scene;
    }


    private void setScene(Scene scene) {
        this.stage.setScene(scene);
    }

    private void handleChat() {

    }
}