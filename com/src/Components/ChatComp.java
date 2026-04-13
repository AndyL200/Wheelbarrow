package Components;

import Network.User;
import javafx.scene.layout.StackPane;

public class ChatComp extends StackPane{
    //navbar on top
    //text box below

    //need user to send messages to server and receive messages from server
    private User user;


    ChatComp(User user) {
        this.user = user;
    }

    private void setUser(User user) {
        this.user = user;
    }
}
