package Components;

import Network.Server;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

public class Sidebar extends VBox {
    Runnable onAddServer = () -> {System.out.println("You must add functionality to this button!");};
    public Sidebar() {
        this.getStyleClass().add("sidebar-style");
        this.setAlignment(Pos.TOP_CENTER);
        this.setSpacing(15);
        
        // Create plus button
        StackPane plusButton = createPlusButton();
        this.getChildren().add(plusButton);
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

    public ServerEntry createServerEntry(Image icon) {
        if (icon == null) {
            System.out.println("Warning: No icon provided for server entry");
            return new ServerEntry();
        }
        return new ServerEntry(icon);
    }

    public ServerEntry createServerEntry() {
        return new ServerEntry();
    }
    
    public void setOnAddServer(Runnable onAddServer) {
        this.onAddServer = onAddServer;
    }

}
