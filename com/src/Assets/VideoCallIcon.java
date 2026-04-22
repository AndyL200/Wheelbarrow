package Assets;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class VideoCallIcon extends ImageView {
    public VideoCallIcon() {
        super();
        // Set the image for the video call icon
        // You can replace "video_call_icon.png" with the actual path to your icon image
        this.setImage(new Image(getClass().getResourceAsStream("./video-camera.png")));
        this.setFitWidth(20); // Set desired width
        this.setFitHeight(20); // Set desired height
        this.getStyleClass().clear();
        this.setStyle("");
    }
}
