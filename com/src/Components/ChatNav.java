package Components;



import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class ChatNav extends HBox{

    public ChatNav() {
        this.getStyleClass().add("navbar");
        this.setMaxHeight(40);
        this.setSpacing(0);
        
        // Spacer (3 parts)
        Region spacerGrow = new Region();
        HBox.setHgrow(spacerGrow, Priority.ALWAYS);
        spacerGrow.setMinWidth(0);
        spacerGrow.setPrefWidth(3);
        
        // User label (4 parts)
        VBox userContainer = new VBox();
        HBox.setHgrow(userContainer, Priority.ALWAYS);
        userContainer.setMinWidth(0);
        userContainer.setPrefWidth(4);
        userContainer.setAlignment(Pos.CENTER);
        Label userLabel = new Label("User");
        userLabel.setStyle("-fx-text-fill: #ffffff;");
        userContainer.getChildren().add(userLabel);
        
        // Profile link (5 parts)
        VBox profileContainer = new VBox();
        HBox.setHgrow(profileContainer, Priority.ALWAYS);
        profileContainer.setMinWidth(0);
        profileContainer.setPrefWidth(5);
        profileContainer.setAlignment(Pos.CENTER);
        Label profileLink = new Label("Profile");
        profileLink.setStyle("-fx-text-fill: #0066cc; -fx-cursor: hand;");
        profileLink.setOnMouseEntered(e -> profileLink.setStyle("-fx-text-fill: #0052a3; -fx-cursor: hand; -fx-underline: true;"));
        profileLink.setOnMouseExited(e -> profileLink.setStyle("-fx-text-fill: #0066cc; -fx-cursor: hand;"));
        profileLink.setOnMouseClicked(e -> onProfileSelected());
        profileContainer.getChildren().add(profileLink);
        
        // Logout link (3 parts)
        VBox logoutContainer = new VBox();
        HBox.setHgrow(logoutContainer, Priority.ALWAYS);
        logoutContainer.setMinWidth(0);
        logoutContainer.setPrefWidth(3);
        logoutContainer.setAlignment(Pos.CENTER);
        Label logoutLink = new Label("Logout");
        logoutLink.setStyle("-fx-text-fill: #0066cc; -fx-cursor: hand;");
        logoutLink.setOnMouseEntered(e -> logoutLink.setStyle("-fx-text-fill: #0052a3; -fx-cursor: hand; -fx-underline: true;"));
        logoutLink.setOnMouseExited(e -> logoutLink.setStyle("-fx-text-fill: #0066cc; -fx-cursor: hand;"));
        logoutLink.setOnMouseClicked(e -> onLogoutSelected());
        logoutContainer.getChildren().add(logoutLink);
        
        this.getChildren().addAll(spacerGrow, userContainer, profileContainer, logoutContainer);
    }
    
    private void onProfileSelected() {
        System.out.println("Profile selected");
        // TODO: Handle profile selection
    }
    
    private void onLogoutSelected() {
        System.out.println("Logout selected");
        // TODO: Handle logout selection
    }
}
