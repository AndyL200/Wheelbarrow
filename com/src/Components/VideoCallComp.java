package Components;

import java.io.ByteArrayInputStream;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class VideoCallComp extends StackPane {
    private GridPane callUsers;
    private HBox controls;
    private VBox container;
    private Runnable onExit;
    private Runnable onMute;
    private Runnable onCameraToggle;
    private Runnable onEnd;
    VideoCapture videoCapture;
    AnimationTimer timer;
    Canvas videoCanvas;
    GraphicsContext gc;
    
    public static Image mat2Image(Mat mat) {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".png", mat, buffer);
        return new Image(new ByteArrayInputStream(buffer.toArray()));
    }


    public VideoCallComp() {
        initOpenCV();

        videoCanvas = new Canvas();
        gc = videoCanvas.getGraphicsContext2D();
        gc.setStroke(Color.GREEN);

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                Mat mat = new Mat();
                if (videoCapture != null && videoCapture.isOpened()) {
                    videoCapture.read(mat);

                    Image img = mat2Image(mat);
                    gc.drawImage(img, 0, 0);
                }
            }
        };

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
        
        // Add user components - each can hold a canvas
        for (int i = 0; i < 4; i++) {
            StackPane userContainer = new StackPane();
            userContainer.setMinSize(150, 150);
            userContainer.getStyleClass().add("video-user-placeholder");
            
            if (i == 0) {
                // First user gets the video canvas
                userContainer.getChildren().add(videoCanvas);
            } else {
                // Other users get placeholder labels
                Label userPlaceholder = new Label("User " + (i + 1));
                userPlaceholder.getStyleClass().add("video-user-label");
                userPlaceholder.setAlignment(Pos.CENTER);
                userContainer.getChildren().add(userPlaceholder);
            }
            
            this.callUsers.add(userContainer, i % 2, i / 2);
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
        muteBtn.setOnAction(e -> {
            System.out.println("Mute toggled");
            if (onMute != null) onMute.run();
        });
        
        // Camera button
        Button cameraBtn = new Button("📷 Camera");
        cameraBtn.getStyleClass().add("video-control-btn");
        cameraBtn.setOnAction(e -> {
            System.out.println("Camera toggled");
            if (onCameraToggle != null) onCameraToggle.run();
        });
        
        // End call button
        Button endCallBtn = new Button("End Call");
        endCallBtn.getStyleClass().add("video-end-call-btn");
        endCallBtn.setOnAction(e -> {
            System.out.println("Call ended");
            if (onEnd != null) onEnd.run();
        });
        
        // Exit button
        Button exitBtn = new Button("✕ Exit");
        exitBtn.getStyleClass().add("video-exit-btn");
        exitBtn.setOnAction(e -> {
            if (onExit != null) onExit.run();
        });
        
        this.controls.getChildren().addAll(muteBtn, cameraBtn, endCallBtn, exitBtn);
        
        this.container.getChildren().addAll(this.callUsers, this.controls);
        this.getChildren().add(this.container);
        timer.start();
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
    
    public void setOnMute(Runnable onMute) {
        this.onMute = onMute;
    }
    
    public void setOnCameraToggle(Runnable onCameraToggle) {
        this.onCameraToggle = onCameraToggle;
    }
    
    public void setOnEnd(Runnable onEnd) {
        this.onEnd = onEnd;
    }


    private void initOpenCV() {
        // Load OpenCV native library
        try {
            System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
            System.out.println("OpenCV loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load OpenCV: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
