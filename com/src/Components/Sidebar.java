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
    public Sidebar() {
        this.getStyleClass().add("sidebar-style");
        setMaxHeight(Double.MAX_VALUE);
        setMaxWidth(Double.MAX_VALUE);
        this.core = new VBox();
        this.core.setSpacing(15);

        
        // Create plus button
        StackPane plusButton = createPlusButton();
        plusButton.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(this.core, Priority.ALWAYS);
        HBox.setHgrow(this.core, Priority.ALWAYS);
        this.core.getChildren().add(plusButton);
        this.core.setAlignment(Pos.TOP_CENTER);
        this.setContent(this.core);

    }
    
    public StackPane createPlusButton() {
        // Create circle
        Circle circle = new Circle(25);
        circle.setStyle("-fx-fill: #555555; -fx-stroke: #888888; -fx-stroke-width: 2;");
        circle.setCursor(javafx.scene.Cursor.HAND);
        
        // Create plus label
        Label plusLabel = new Label("+");
        plusLabel.setStyle("-fx-font-size: 28px; -fx-text-fill: #ffffff;");
        plusLabel.setCursor(javafx.scene.Cursor.HAND);
        
        // Stack them together
        StackPane stackPane = new StackPane(circle, plusLabel);
        stackPane.setPrefSize(60, 60);
        
        // Add click functionality
        stackPane.setOnMouseClicked(e -> onAddServer.run());
        
        // Add hover effect
        stackPane.setOnMouseEntered(e -> circle.setStyle("-fx-fill: #666666; -fx-stroke: #999999; -fx-stroke-width: 2;"));
        stackPane.setOnMouseExited(e -> circle.setStyle("-fx-fill: #555555; -fx-stroke: #888888; -fx-stroke-width: 2;"));
        
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
        });
        this.core.getChildren().add(entry);
    }

    
    public void setOnAddServer(Runnable onAddServer) {
        this.onAddServer = onAddServer;
    }

    public void setOnServerSelect(Consumer<ServerInfo> onServerSelect) {
        this.onServerSelect = onServerSelect;
    }

}
