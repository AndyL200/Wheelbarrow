package Components;

import javax.sound.sampled.Mixer;

import Network.AudioCall;
import Network.Call;
import Network.VideoCall;
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
    
    private AudioCall audioObj;
    private VideoCall videoObj;



    //signals
    private Runnable onExit;
    private Runnable onMute;
    private Runnable onEnd;

    public CallComp(Call call) {
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
            Label userPlaceholder = new Label("User " + (i + 1));
            userPlaceholder.getStyleClass().addAll("audio-user-placeholder", "audio-user-label");
            userPlaceholder.setMinSize(100, 100);
            userPlaceholder.setAlignment(Pos.CENTER);
            this.callUsers.add(userPlaceholder, i % 2, i / 2);
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

        
        // Exit button
        this.exitBtn = new Button("✕ Exit");
        this.exitBtn.getStyleClass().add("audio-exit-btn");
        this.exitBtn.setOnAction(e -> {
            if (onExit != null) onExit.run();
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
            this.audioObj.setMic(micPicker.getValue());
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
            this.audioObj.setSpeaker(speakerPicker.getValue());
        });
        
        this.controls.getChildren().addAll(muteBtn, endCallBtn, exitBtn, micPicker, speakerPicker);
        
        this.container.getChildren().addAll(this.callUsers, this.controls);
        this.getChildren().add(this.container);

        this.audioObj.start();
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
    
    public void setOnEnd(Runnable toEnd) {
        this.onEnd = toEnd;
    }

    public void endCall() {
        this.audioObj.stop();
        System.out.println("Audio call scheduled for termination.");
    }

    public AudioCall getAudioObj() {
        return this.audioObj;
    }


    public void setAudioObj(AudioCall audioObj) {
        this.audioObj = audioObj;
        this.endCallBtn.setOnAction(e -> {
            System.out.println("Call ended");
            if (onEnd != null) onEnd.run();
            if (this.audioObj != null) {
                this.audioObj.stop();
            }
            if (this.videoObj != null) {
                this.videoObj.stop();
            }
        });
    }

    public void setVideoObj(VideoCall videoObj) {
        this.videoObj = videoObj;
        this.endCallBtn.setOnAction(e -> {
            System.out.println("Call ended");
            if (onEnd != null) onEnd.run();
            if (this.audioObj != null) {
                this.audioObj.stop();
            }
            if (this.videoObj != null) {
                this.videoObj.stop();
            }
        });
    }

}
