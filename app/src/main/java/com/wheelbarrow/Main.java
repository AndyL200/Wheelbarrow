package com.wheelbarrow;

import java.util.prefs.Preferences;

import com.wheelbarrow.Handlers.AppObserver;
import com.wheelbarrow.Handlers.SceneHandler;
import com.wheelbarrow.Handlers.ThemeManager;
import javafx.application.Application;
import javafx.stage.Stage;
import com.wheelbarrow.Scenes.ChatScene;
import com.wheelbarrow.Scenes.LoginScene;


public class Main extends Application {
    public static void main(String[] args) {
        
        //grab server cache from disk
        Preferences pref = Preferences.userRoot().node("wheelbarrow/debug");
        pref.putBoolean("mode",true);
        pref.putBoolean("concurrency", false);
        AppObserver.getInstance(); // initialize the app observer singleton
        ThemeManager.getInstance(); // initialize the theme manager singleton
        Application.launch(args);
    }

    @Override
    public void start(Stage stage) {
        
        stage.setTitle("OnlineCom");
        LoginScene login = new LoginScene(800, 600);
        SceneHandler.init(stage, login);
        login.setOnLogin((l) -> SceneHandler.get().switchScene(new ChatScene()));
        

        stage.show();
    }
}