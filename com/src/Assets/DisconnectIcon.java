package Assets;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class DisconnectIcon extends ImageView {
    public DisconnectIcon() {
        super();
        // Set the image for the disconnect icon
        // You can replace "disconnect_icon.png" with the actual path to your icon image
        this.setImage(new Image(getClass().getResourceAsStream("./unlink.png")));
        this.setFitWidth(20); // Set desired width
        this.setFitHeight(20); // Set desired height
        this.getStyleClass().clear();
        this.setStyle("");
    }
}
