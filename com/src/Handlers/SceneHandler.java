package Handlers;

import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import Scenes.AppSceneTemplate;
import Scenes.ChatScene;

public class SceneHandler {
    private AppSceneTemplate currentScene;
    private Stage stage;
    private static final String SERVER_LIST_PATH;
    static {
        Path p = Paths.get("").toAbsolutePath();
        Path filePath = p.resolve("com").resolve("temp_server_list").resolve("server_list.json");
        SERVER_LIST_PATH = filePath.toString();
    }

    public SceneHandler(Stage stage, Scene scene) {
        this.stage = stage;
        this.currentScene = (AppSceneTemplate) scene;
        this.stage.setScene(scene);
    }   

    public SceneHandler(Stage stage, AppSceneTemplate scene)  {
        this.stage = stage;
        this.currentScene = scene;
        this.stage.setScene(scene);
        if (scene instanceof ChatScene) {
            handleChat();
        }
    }

    public void switchScene(AppSceneTemplate newScene) {
        this.currentScene = newScene;
        this.stage.setScene(newScene);
        switchScene(newScene);
        if (newScene instanceof ChatScene) {
            handleChat();
        }
    }


    private void switchScene(Scene scene) {
        this.stage.setScene(scene);
    }

    private void handleChat() {
        if (!(currentScene instanceof ChatScene)) {
            //must be a ChatScene
            return;
        }

        ChatScene chat = (ChatScene) currentScene;
        try {
            String json = Files.readString(Paths.get(SERVER_LIST_PATH));
            chat.initServerList(json);
        }
        catch (IOException e) {
            System.err.println("Error reading server list: " + e.getMessage());
            e.printStackTrace();
        }

        
    }
}