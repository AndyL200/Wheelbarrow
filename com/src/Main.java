import java.util.prefs.Preferences;

import Handlers.AppObserver;
import Handlers.SceneHandler;
import Handlers.ThemeManager;
import javafx.application.Application;
import javafx.stage.Stage;
import Scenes.ChatScene;
import Scenes.LoginScene;


public class Main extends Application {
    SceneHandler controller;
    public static void main(String[] args) {
        
        //grab server cache from disk
        Preferences pref = Preferences.userRoot().node("wheelbarrow");
        pref.putBoolean("mode",true);
        pref.putBoolean("concurrency", true);
        AppObserver.getInstance(); // initialize the app observer singleton
        ThemeManager.getInstance(); // initialize the theme manager singleton
        Application.launch(args);
    }

    @Override
    public void start(Stage stage) {
        
        stage.setTitle("OnlineCom");
        LoginScene login = new LoginScene(800, 600);
        SceneHandler.init(stage, login);
        this.controller = SceneHandler.get();
        login.setOnLogin((l) -> this.controller.switchScene(new ChatScene()));
        

        stage.show();
    }
}