package Components;

import java.util.function.Consumer;
import java.util.function.Supplier;

import Components.ComponentMacros.MessageType;
import Network.User;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class ChatComp extends StackPane{
    //navbar on top
    //text box below

    //need user to send messages to server and receive messages from server
    private User user;
    private ChatNav chatNav;
    private ScrollPane scrollChat;
    private VBox core;

    public ChatComp() {
        this.user = null;
        setMaxHeight(Double.MAX_VALUE);
        setMaxWidth(Double.MAX_VALUE);

        this.getStyleClass().add("chat-comp");

        this.core = new VBox();
        this.core.setSpacing(0);

        this.chatNav = new ChatNav();
        ChatBox chatBox = new ChatBox();
        chatBox.setKeyConsume(this::outtyping);
        chatBox.setOnSend((message) -> {
            Message msg = new Message(user.getName(), message, MessageType.MESSAGE);
            sendMessage(msg);
        });
        VBox.setVgrow(this.core, javafx.scene.layout.Priority.ALWAYS);
        this.scrollChat = new ScrollPane(chatBox);

        this.core.getChildren().addAll(this.chatNav, this.scrollChat);
        this.getChildren().add(this.core);
    }

    public ChatComp(User user) {
        this();
        this.user = user;
    }

    public void addMessage(Message message) {
        if (message.type == MessageType.MESSAGE) {
        ChatBox box = (ChatBox) this.scrollChat.getContent();
        box.chatDisplay.getChildren().add(new Label(new String(message.messageData)));
        }
    }

    public void fillMessageBox(Message[] messages) {
        for (Message msg : messages) {
                addMessage(msg);
        }
    }

    public User getUser() {
        return this.user;
    }

    private void setUser(User user) {
        this.user = user;
    }
    //handling incoming typing later
    private void outtyping(KeyEvent e) {
        //on outtyping, the chatBox doesn't need to change but a typing signal must still be broadcast to all users
        Message msg = new Message(user.getName(), "", MessageType.TYPING);
        sendMessage(msg);
    }

    //as bytes or as Message object?
    private void sendMessage(Message message) {
        if (user != null) {
            user.send(message);
        }
    }
}


class ChatBox extends VBox {
    public VBox chatDisplay;
    public TextField msgBox;
    public HBox sendBox;
    private Consumer<KeyEvent> keyConsume;
    private Consumer<String> onSend;
    public ChatBox() {
        this.getStyleClass().add("chat-box");
        this.msgBox = new TextField();
        this.sendBox = new HBox();


        msgBox.setPromptText("Type a message");
        msgBox.setOnKeyPressed(e -> {
            if (e.getCode().toString().equals("ENTER")) {
                onSend.accept(getMessage());
                return;
            }

            keyConsume.accept(e);
        });
        HBox.setHgrow(msgBox, Priority.ALWAYS);
        
        Button sendBtn = new Button("Send");
        sendBtn.setOnAction(e -> {
            onSend.accept(getMessage());
        });
        this.sendBox.getChildren().addAll(msgBox, sendBtn);
        this.chatDisplay = new VBox();
        this.chatDisplay.setSpacing(1);
        this.getChildren().add(this.chatDisplay);
        this.getChildren().addAll(this.sendBox);
        
        // Set 8:2 ratio: chatDisplay takes 80%, sendBox takes 20%
        VBox.setVgrow(this.chatDisplay, Priority.ALWAYS);
        VBox.setVgrow(this.sendBox, Priority.ALWAYS);
        this.chatDisplay.setPrefHeight(400);
        this.sendBox.setPrefHeight(100);
    }

    public void setKeyConsume(Consumer<KeyEvent> keyConsume) {
        this.keyConsume = keyConsume;
    }

    public void setOnSend(Consumer<String> onSend) {
        this.onSend = onSend;
    }

    public final String getMessage() {
        return this.msgBox.getText();
    }
}
