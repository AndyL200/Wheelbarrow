package Components;

import java.util.function.Consumer;

import Network.Server;
import Network.ServerInfo;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

public class Sidebar extends ScrollPane {
    VBox core;
    Runnable onAddServer = () -> {System.out.println("You must add functionality to this button! (Add Server)");};
    Consumer<ServerInfo> onServerSelect = (serverInfo) -> {System.out.println("You must add functionality to this button! (Server selected)");};
    int hightlightedIdx = -1;
    public Sidebar() {
        this.getStyleClass().add("sidebar-style");
        setMaxHeight(Double.MAX_VALUE);
        setMaxWidth(Double.MAX_VALUE);
        setMinWidth(20);
        setMinHeight(80);


        //Core VBox (where server entries are added)
        this.core = new VBox();
        this.core.setSpacing(10);
        this.core.getStyleClass().add("sidebar-core");
        this.core.setMaxHeight(Double.MAX_VALUE);
        this.core.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(this.core, Priority.ALWAYS);
        HBox.setHgrow(this.core, Priority.ALWAYS);

        // Create plus button
        StackPane plusButton = createPlusButton();
        plusButton.setAlignment(Pos.TOP_CENTER);
        
        this.core.getChildren().add(plusButton);
        this.core.setAlignment(Pos.TOP_CENTER);
        //this.setOnMouseClicked((e) -> this.setStyle("-fx-border-color: #00ff00; -fx-border-width: 3;"));
        this.setContent(this.core);
        this.setFitToHeight(true);
        this.setFitToWidth(true);
        this.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        this.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

    }
    
    public StackPane createPlusButton() {
        // Create circle
        Circle circle = new Circle(25);
        circle.getStyleClass().add("sidebar-add-btn-circle");
        circle.setCursor(javafx.scene.Cursor.HAND);
        
        // Create plus label
        Label plusLabel = new Label("+");
        plusLabel.getStyleClass().add("sidebar-add-btn-label");
        plusLabel.setCursor(javafx.scene.Cursor.HAND);
        
        // Stack them together
        StackPane stackPane = new StackPane(circle, plusLabel);
        stackPane.setPrefSize(60, 60);
        
        // Add click functionality
        stackPane.setOnMouseClicked(e -> onAddServer.run());
        
        // Add hover effect via CSS classes
        stackPane.setOnMouseEntered(e -> {
            circle.getStyleClass().remove("sidebar-add-btn-circle");
            circle.getStyleClass().add("sidebar-add-btn-circle-hover");
        });
        stackPane.setOnMouseExited(e -> {
            circle.getStyleClass().remove("sidebar-add-btn-circle-hover");
            circle.getStyleClass().add("sidebar-add-btn-circle");
        });
        
        return stackPane;
    }

    //the creation of a server entry should be handled by the sidebar
    public static ServerEntry createServerEntry(ServerInfo info, Image icon) {
        if (icon == null) {
            System.out.println("Warning: No icon provided for server entry");
            return new ServerEntry(info);
        }
        return new ServerEntry(info, icon);
    }

    public static ServerEntry createServerEntry(ServerInfo info) {
        return new ServerEntry(info);
    }

    public void addServerEntry(ServerEntry entry) {
        entry.setOnMouseClicked((e) -> {
            onServerSelect.accept(entry.getServerInfo());
            highlightEntry(this.core.getChildren().size()-1);
        });
        this.core.getChildren().add(entry);
    }

    
    public void setOnAddServer(Runnable onAddServer) {
        this.onAddServer = onAddServer;
    }

    public void setOnServerSelect(Consumer<ServerInfo> onServerSelect) {
        this.onServerSelect = onServerSelect;
    }

    public void highlightEntry(int idx) {
        if (hightlightedIdx != -1) {
            this.core.getChildren().get(hightlightedIdx).getStyleClass().remove("server-entry-selected");
        }
        this.core.getChildren().get(idx).getStyleClass().add("server-entry-selected");
        this.hightlightedIdx = idx;
    }
}
