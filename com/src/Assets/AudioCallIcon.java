package Assets;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class AudioCallIcon extends ImageView {
    public AudioCallIcon() {
        super();
        // Set the image for the audio call icon
        // You can replace "audio_call_icon.png" with the actual path to your icon image
        this.setImage(new Image(getClass().getResourceAsStream("./volume.png")));
        this.setFitWidth(20); // Set desired width
        this.setFitHeight(20); // Set desired height
        this.getStyleClass().clear();
        this.setStyle("");
    }
}
