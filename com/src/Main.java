import Handlers.SceneHandler;
import Scenes.ChatScene;
import Scenes.LoginScene;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;


public class Main extends Application {
    SceneHandler controller;
    public static void main(String[] args) {
        Application.launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("OnlineCom");

        LoginScene loginScene = new LoginScene(800, 600);
        loginScene.setOnLogin(username -> Platform.runLater(() -> {
            ChatScene chatScene = new ChatScene(800, 600, username);
            this.controller = new SceneHandler(stage, chatScene);
        }));

        stage.setScene(loginScene);
        stage.show();
    }
}