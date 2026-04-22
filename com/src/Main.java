import Handlers.SceneHandler;
import javafx.application.Application;
import javafx.stage.Stage;
import Scenes.ChatScene;


public class Main extends Application {
    SceneHandler controller;
    public static void main(String[] args) {
        
        //grab server cache from disk
        Application.launch(args);
    }

    @Override
    public void start(Stage stage) {
        
        stage.setTitle("OnlineCom");
        this.controller = new SceneHandler(stage, new ChatScene(800, 600));
        

        stage.show();
    }
}