package Components;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class VideoCallComp extends StackPane {
    private GridPane callUsers;
    private HBox controls;
    private VBox container;
    private Runnable onExit;
    
    public VideoCallComp() {
        this.getStyleClass().add("video-call-comp");
        this.setMaxHeight(Double.MAX_VALUE);
        this.setMaxWidth(Double.MAX_VALUE);
        
        // Container for the call
        this.container = new VBox();
        this.container.setSpacing(10);
        this.container.setPadding(new javafx.geometry.Insets(20));
        this.container.getStyleClass().add("video-call-container");
        
        // Grid of users
        this.callUsers = new GridPane();
        this.callUsers.setHgap(10);
        this.callUsers.setVgap(10);
        this.callUsers.getStyleClass().add("video-call-users");
        this.callUsers.setAlignment(Pos.CENTER);
        
        // Add placeholder users for demo
        for (int i = 0; i < 4; i++) {
            Label userPlaceholder = new Label("User " + (i + 1));
            userPlaceholder.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 12px;");
            userPlaceholder.getStyleClass().add("video-user-placeholder");
            userPlaceholder.setMinSize(150, 150);
            userPlaceholder.setAlignment(Pos.CENTER);
            this.callUsers.add(userPlaceholder, i % 2, i / 2);
        }
        
        VBox.setVgrow(this.callUsers, Priority.ALWAYS);
        
        // Controls HBox
        this.controls = new HBox();
        this.controls.setSpacing(15);
        this.controls.setAlignment(Pos.CENTER);
        this.controls.getStyleClass().add("video-call-controls");
        this.controls.setPrefHeight(80);
        
        // Mute button
        Button muteBtn = new Button("🔊 Mute");
        muteBtn.getStyleClass().add("video-control-btn");
        muteBtn.setOnAction(e -> System.out.println("Mute toggled"));
        
        // Camera button
        Button cameraBtn = new Button("📷 Camera");
        cameraBtn.getStyleClass().add("video-control-btn");
        cameraBtn.setOnAction(e -> System.out.println("Camera toggled"));
        
        // End call button
        Button endCallBtn = new Button("End Call");
        endCallBtn.getStyleClass().add("video-end-call-btn");
        endCallBtn.setOnAction(e -> System.out.println("Call ended"));
        
        // Exit button
        Button exitBtn = new Button("✕ Exit");
        exitBtn.getStyleClass().add("video-exit-btn");
        exitBtn.setOnAction(e -> {
            if (onExit != null) onExit.run();
        });
        
        this.controls.getChildren().addAll(muteBtn, cameraBtn, endCallBtn, exitBtn);
        
        this.container.getChildren().addAll(this.callUsers, this.controls);
        this.getChildren().add(this.container);
    }
    
    public GridPane getCallUsersGrid() {
        return this.callUsers;
    }
    
    public HBox getControls() {
        return this.controls;
    }
    
    public void setOnExit(Runnable onExit) {
        this.onExit = onExit;
    }
}
