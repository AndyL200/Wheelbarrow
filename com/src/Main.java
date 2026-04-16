import java.io.IOException;
import java.util.*;

import Handlers.SceneHandler;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import Network.Server;
import Network.ServerCache;
import Scenes.ChatScene;

import java.lang.Throwable;
import Handlers.SceneHandler;

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