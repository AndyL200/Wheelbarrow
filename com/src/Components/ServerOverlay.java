package Components;


import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Network.ServerCache;
import Network.ServerInfo;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Pair;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class ServerOverlay extends StackPane {
    private Runnable onHostServer;
    private Consumer<Pair<InetAddress, Integer>> onServerFound;
    private Runnable onClose;
    private StackPane exitButton;
    private VBox form;
    
    public ServerOverlay() {
        // Semi-transparent background
        this.getStyleClass().add("add-server-overlay");
        Pane darkBackground = new Pane();
        darkBackground.getStyleClass().add("add-server-dark-bg");
        
        // Content pane with BorderPane layout
        BorderPane contentPane = new BorderPane();
        contentPane.getStyleClass().add("add-server-content");
        contentPane.setPrefWidth(400);
        contentPane.setPrefHeight(300);
        
        // Top bar with exit button
        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_RIGHT);
        topBar.setPadding(new javafx.geometry.Insets(10, 10, 0, 0));
        topBar.setStyle("-fx-background-color: transparent;");
        
        StackPane exitButton = new StackPane();
        Circle exitCircle = new Circle(15);
        exitCircle.getStyleClass().add("add-server-exit-circle");
        Label exitLabel = new Label("X");
        exitLabel.getStyleClass().add("add-server-exit-label");
        exitButton.getChildren().addAll(exitCircle, exitLabel);
        exitButton.setPrefSize(35, 35);
        exitButton.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
        exitCircle.setOnMouseClicked(e -> onClose.run());
        this.exitButton = exitButton;
        
        topBar.getChildren().add(exitButton);
        contentPane.setTop(topBar);
        
        // Form container (center)
        VBox form = new VBox(10);
        form.setStyle("-fx-padding: 15;");
        form.setAlignment(Pos.TOP_CENTER);
        form.setSpacing(10);
        
        // Title
        Label title = new Label("Add Server");
        title.getStyleClass().add("add-server-title");
        
        // Server name field
        Label nameLabel = new Label("Server URL:");
        nameLabel.getStyleClass().add("add-server-label");
        TextField nameField = new TextField();
        nameField.setPromptText("e.g., myserver.com");


        
        // Submit button
        Button submitBtn = new Button("Add Server");
        submitBtn.getStyleClass().add("add-server-submit-btn");
        submitBtn.setOnAction(e -> {
            //TODO
            String url = nameField.getText();
            int port = 50000; //default port
            if (Pattern.matches(url, "/[0-9]+/")) { //check if url contains port number; 
                Pattern p = Pattern.compile("/[0-9]+/");
                Matcher m = p.matcher(url);

                //Tiny issue, matcher should match from the end of the string
                if (m.find()) {
                    port = Integer.parseInt(url.substring(m.start() + 1, m.end() - 1));
                }
            }
            try {
                InetAddress address = InetAddress.getByName(url);
                onServerFound.accept(new Pair<>(address, port));
            }
            catch (UnknownHostException h) {
                System.err.println("Unknown host: " + url);
            }
            onClose.run();
        });

        Button createServerBtn = new Button("Create Server");
        createServerBtn.getStyleClass().add("add-server-create-btn");
        createServerBtn.setOnAction(e -> {
            //TODO
            System.out.println("onHostServer entry");
            onHostServer.run();
            onClose.run();
        });

        
        form.getChildren().addAll(title, nameLabel, nameField, submitBtn, createServerBtn);
        contentPane.setCenter(form);
        this.form = form;
        
        // Stack overlay background and content
        this.getChildren().addAll(darkBackground, contentPane);
        this.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane.setAlignment(contentPane, Pos.CENTER);
    }

    public void setOnHostServer(Runnable onHostServer) {
        this.onHostServer = onHostServer;
    }

    public void setOnServerFound(Consumer<Pair<InetAddress, Integer>> onServerFound) {
        this.onServerFound = onServerFound;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }
}
