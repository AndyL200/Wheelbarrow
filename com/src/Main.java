import Handlers.SceneHandler;
import javafx.application.Application;
import javafx.stage.Stage;
import Scenes.ChatScene;
import Scenes.LoginScene;


public class Main extends Application {
    SceneHandler controller;
    public static void main(String[] args) {
        
        //grab server cache from disk
        Application.launch(args);
    }

    @Override
    public void start(Stage stage) {
        
        stage.setTitle("OnlineCom");
        LoginScene login = new LoginScene(800, 600);
        this.controller = new SceneHandler(stage, login);
        login.setOnLogin((l) -> this.controller.switchScene(new ChatScene()));
        

        stage.show();
    }
}