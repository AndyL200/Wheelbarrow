package Components;




import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import Assets.AudioCallIcon;
import Assets.DisconnectIcon;
import Assets.VideoCallIcon;
import Components.Helper.CallConfig;
import Network.ServerInfo;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class ChatNav extends HBox{
    ServerInfo info;
    private Button audioCallBtn;
    private Button videoCallBtn;

    private Consumer<CallConfig> onCallSignal;
    private Runnable onDisconnectSignal;
    private Supplier<Boolean> isCallActiveSupplier;

    private HashSet<CallConfig> availableCalls = new HashSet<>();

    public ChatNav(ServerInfo info) {
        this.info = info;
        this.getStyleClass().add("navbar");
        this.setMaxHeight(40);
        this.setMaxWidth(Double.MAX_VALUE);
        this.setSpacing(40);  // Add spacing between components
        HBox.setHgrow(this, Priority.ALWAYS);
        
        // User label (3 parts)
        Label titleLabel;
        if (info == null) {
            titleLabel = new Label("Unknown Server");
        }
        else {
            titleLabel = new Label(info.SERVER_NAME.get());
            titleLabel.textProperty().bind(info.SERVER_NAME);
        }
        titleLabel.getStyleClass().add("server-title-label");
        //titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMinWidth(titleLabel.getText().length());
        titleLabel.setPrefWidth(Region.USE_COMPUTED_SIZE);
        
        // Spacer to push buttons to the right
        Region spacer = new Region();
        spacer.prefWidthProperty().bind(this.widthProperty().multiply(0.5));
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Button for audio call (5 parts)
        //TODO: make a ICON object for this
        audioCallBtn = new Button();
        double buttonSize = 10;
        audioCallBtn.setMinSize(buttonSize, buttonSize);
        audioCallBtn.setPrefSize(buttonSize, buttonSize);
        audioCallBtn.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        // Bind height to width to maintain square aspect ratio
        audioCallBtn.prefHeightProperty().bind(audioCallBtn.widthProperty());
        audioCallBtn.setGraphic(new AudioCallIcon());
        
        audioCallBtn.getStyleClass().add("audio-call-btn");
        audioCallBtn.setOnMouseEntered(e -> audioCallBtn.getStyleClass().add("audio-call-btn-hover"));
        audioCallBtn.setOnMouseExited(e -> audioCallBtn.getStyleClass().remove("audio-call-btn-hover"));
        audioCallBtn.setOnMouseClicked(e -> {
            if (isCallActiveSupplier != null && isCallActiveSupplier.get()) {
                onCallSignal.accept(null);
                return;
            }
            showCallDropdown(audioCallBtn);
        });
        //audioCallBtn.setAlignment(Pos.CENTER);
        
        // Logout link (5 parts)
        videoCallBtn = new Button();
        videoCallBtn.setMinSize(buttonSize, buttonSize);
        videoCallBtn.setPrefSize(buttonSize, buttonSize);
        videoCallBtn.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        // Bind height to width to maintain square aspect ratio
        videoCallBtn.prefHeightProperty().bind(videoCallBtn.widthProperty());
        videoCallBtn.setGraphic(new VideoCallIcon());
        videoCallBtn.getStyleClass().add("video-call-btn");
        videoCallBtn.setOnMouseEntered(e -> videoCallBtn.getStyleClass().add("video-call-btn-hover"));
        videoCallBtn.setOnMouseExited(e -> videoCallBtn.getStyleClass().remove("video-call-btn-hover"));
        videoCallBtn.setOnMouseClicked(e -> {
            if (isCallActiveSupplier != null && isCallActiveSupplier.get()) {
                onCallSignal.accept(null);
                return;
            }
            showCallDropdown(videoCallBtn);
        });
        //videoCallBtn.setAlignment(Pos.CENTER);
        
        // Disconnect Button
        Button disconnectBtn = new Button();
        disconnectBtn.setMinSize(buttonSize, buttonSize);
        disconnectBtn.setPrefSize(buttonSize, buttonSize);
        disconnectBtn.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        // Bind height to width to maintain square aspect ratio
        disconnectBtn.prefHeightProperty().bind(disconnectBtn.widthProperty());
        disconnectBtn.setGraphic(new DisconnectIcon());
        disconnectBtn.getStyleClass().add("disconnect-btn");
        disconnectBtn.setOnMouseEntered(e -> disconnectBtn.getStyleClass().add("disconnect-btn-hover"));
        disconnectBtn.setOnMouseExited(e -> disconnectBtn.getStyleClass().remove("disconnect-btn-hover"));
        disconnectBtn.setOnMouseClicked(e -> onDisconnect());
        //disconnectBtn.setAlignment(Pos.CENTER);
        
        this.getChildren().addAll(titleLabel, spacer, audioCallBtn, videoCallBtn, disconnectBtn);
    }
    
    public void updateCallBtns() {
        if (isCallActiveSupplier != null && isCallActiveSupplier.get()) {
            audioCallBtn.getStyleClass().add("audio-call-btn-active");
            videoCallBtn.getStyleClass().add("video-call-btn-active");
        } else {
            audioCallBtn.getStyleClass().remove("audio-call-btn-active");
            videoCallBtn.getStyleClass().remove("video-call-btn-active");
        }
    }

    public void setInCallSup(Supplier<Boolean> isCallActiveSupplier) {
        this.isCallActiveSupplier = isCallActiveSupplier;
    }

    public void setOnCall(Consumer<CallConfig> onCall) {
        this.onCallSignal = onCall;
    }
    
    
    public void setAvailableCalls(HashSet<CallConfig> calls) {
        this.availableCalls.clear();
        this.availableCalls.addAll(calls);
    }

    public void addAvailableCall(CallConfig config) {
        if (config == null || config.HOST == null || config.PORT == -1) {
            System.out.println("Invalid audio call config, cannot add to available calls");
            return;
        }
        this.availableCalls.add(config);
    }
    
    
    private void showCallDropdown(Button anchor) {
        
        ContextMenu contextMenu = new ContextMenu();
        MenuItem newCallItem = new MenuItem("Start New Call");
        newCallItem.setOnAction(e -> {
            if (onCallSignal != null) {
                onCallSignal.accept(null);
            }
        });
        contextMenu.getItems().add(newCallItem);
        
        for (CallConfig callConfig : availableCalls) {
            MenuItem menuItem = new MenuItem(callConfig.toString());
            menuItem.setOnAction(e -> {
                if (onCallSignal != null) {
                    onCallSignal.accept(callConfig);
                }
            });
            contextMenu.getItems().add(menuItem);
        }
        
        // Show the context menu at the button's location
        contextMenu.show(anchor, Side.BOTTOM, 0, 0);
    }
    

    private void onDisconnect() {
        System.out.println("Disconnecting from server");
        //Cleanup
        onDisconnectSignal.run();
    }

}
