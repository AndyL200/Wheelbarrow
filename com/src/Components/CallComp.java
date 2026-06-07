package Components;

import javax.sound.sampled.Mixer;

import Handlers.AppObserver;
import Network.AudioCall;
import Network.Call;
import Network.CallObj;
import Network.VideoCall;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class CallComp extends StackPane {
    private GridPane callUsers;
    private HBox controls;
    private VBox container;
    private Button muteBtn;
    private Button endCallBtn;
    private Button exitBtn;
    private Button streamBtn;
    
    //signals
    private Runnable onExit;
    private Runnable onMute;
    private Runnable onEnd;

    //Modes
    private boolean isAudioActive = false;
    private boolean isVideoActive = false;

    public CallComp() {
        this.getStyleClass().add("audio-call-comp");
        this.setMaxHeight(Double.MAX_VALUE);
        this.setMaxWidth(Double.MAX_VALUE);
        
        // Container for the call
        this.container = new VBox();
        this.container.setSpacing(10);
        this.container.setPadding(new javafx.geometry.Insets(20));
        this.container.getStyleClass().add("audio-call-container");
        
        // Grid of users
        this.callUsers = new GridPane();
        this.callUsers.setHgap(10);
        this.callUsers.setVgap(10);
        this.callUsers.getStyleClass().add("audio-call-users");
        this.callUsers.setAlignment(Pos.CENTER);
        
        // Add placeholder users for demo
        for (int i = 0; i < 4; i++) {
            //(TODO) use video components here instead
            
        }
        
        VBox.setVgrow(this.callUsers, Priority.ALWAYS);
        
        // Controls HBox
        this.controls = new HBox();
        this.controls.setSpacing(15);
        this.controls.setAlignment(Pos.CENTER);
        this.controls.getStyleClass().add("audio-call-controls");
        this.controls.setPrefHeight(80);
        
        // Mute button
        this.muteBtn = new Button("🔊 Mute");
        this.muteBtn.getStyleClass().add("audio-control-btn");
        this.muteBtn.setOnAction(e -> {
            System.out.println("Mute toggled");
            if (onMute != null) onMute.run();
        });
        
        // End call button
        this.endCallBtn = new Button("End Call");
        this.endCallBtn.getStyleClass().add("audio-end-call-btn");
        this.endCallBtn.setOnAction(e -> {
            System.out.println("End call clicked");
            if (onEnd != null) onEnd.run();
        });

        
        // Exit button
        this.exitBtn = new Button("✕ Exit");
        this.exitBtn.getStyleClass().add("audio-exit-btn");
        this.exitBtn.setOnAction(e -> {
            if (onExit != null) onExit.run();
        });
        
        this.streamBtn = new Button("📹 Stream");
        this.streamBtn.getStyleClass().add("video-stream-btn");
        this.streamBtn.setOnAction(e -> {
            System.out.println("Stream toggled");
            // For demo, just print. In real app, would toggle video stream.
        });

        ComboBox<Mixer.Info> micPicker = new ComboBox<>();
        micPicker.getItems().addAll(AudioCall.getAvailableMics());

        // Display the mixer name nicely
        micPicker.setCellFactory(lv -> new ListCell<>() {
            protected void updateItem(Mixer.Info item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        micPicker.setOnAction(e -> {
            CallObj callObj = AppObserver.getInstance().getCurrentCall();
            if (callObj != null && callObj.getAudio() != null) {
                callObj.getAudio().setMic(micPicker.getValue());
            }
        });

        ComboBox<Mixer.Info> speakerPicker = new ComboBox<>();
        speakerPicker.getItems().addAll(AudioCall.getAvailableSpeakers());
        
        speakerPicker.setCellFactory(lv -> new ListCell<>() {
            protected void updateItem(Mixer.Info item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        speakerPicker.setOnAction(e -> {
            CallObj callObj = AppObserver.getInstance().getCurrentCall();
            if (callObj != null && callObj.getAudio() != null) {
                callObj.getAudio().setSpeaker(speakerPicker.getValue());
            }
        });
        
        this.controls.getChildren().addAll(muteBtn, endCallBtn, exitBtn, micPicker, speakerPicker);
        
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

     public void setOnMute(Runnable onMute) {
        this.onMute = onMute;
    }
    
    public void setOnEnd(Runnable onEnd) {
        this.onEnd = onEnd;
    }

    public void endCall() {
        AppObserver.getInstance().stopCurrentCall();
    }

}
