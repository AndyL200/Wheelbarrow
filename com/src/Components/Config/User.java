package Components.Config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Base64;

import org.json.JSONObject;

import javafx.scene.image.Image;

public class User {
    //TODO(impose naming restrictions)
    String username;
    Image img;
    public User(String username, Image img) {
        this.username = username;
        this.img = img;
    }
    public User(String username) {
        this(username, null);
     }
    public String getUsername() {
        return username;
    }
    public Image getImg() {
        return img;
    }
    public String getImgUrl() {
        if (img == null) {
            return "";
        }
        return img.getUrl();
    }

    public static User fromJSON(String key, JSONObject json) {
        //b64 encoded string for now
        String imgUrl = json.optString("b64");
        InputStream imgStream = (imgUrl != null && !imgUrl.isEmpty()) ? User.class.getResourceAsStream(imgUrl) : null;
        Image img = (imgStream != null) ? new Image(imgStream) : null;
        return new User(key, img);
    }
}
