package Components;

import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

import Components.ComponentMacros.MessageType;
import Components.Helper.CallConfig;
import Components.Helper.VideoCallConfig;
import Network.AudioCallClient;
import Network.AudioCallServer;
import Network.User;
import javafx.application.Platform;
import javafx.geometry.Pos;
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
    private CallComp callComp;

    public ChatComp(User user) {
        setMaxHeight(Double.MAX_VALUE);
        setMaxWidth(Double.MAX_VALUE);
        setMinWidth(40);
        setMinHeight(80);

        this.getStyleClass().add("chat-comp");

        //User section
        this.user = user;
        user.setOnMessageReceived(m -> {
            System.out.println("OnMessageReceived in ChatComp");
            Platform.runLater(() -> handleMessage(m));
        });



        this.core = new VBox();
        this.core.setSpacing(0);

        this.chatNav = new ChatNav(user.getInfo());
        
        

        this.chatNav.setOnCall(this::toggleAudioCall);
        this.chatNav.setInCallSup(this::isInCall);
        ChatBox chatBox = new ChatBox();
        chatBox.setKeyConsume(this::outtyping);
        chatBox.setOnSend((message) -> {
            Message msg = new Message(user.getName(), message, MessageType.MESSAGE.getValue());
            sendMessage(msg);
        });

        
        this.core.setFillWidth(true);
        VBox.setVgrow(this.core, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(this.core, javafx.scene.layout.Priority.ALWAYS);
        this.scrollChat = new ScrollPane(chatBox);
        this.scrollChat.setFitToWidth(true);
        this.scrollChat.setFitToHeight(true);
        VBox.setVgrow(this.scrollChat, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(this.scrollChat, javafx.scene.layout.Priority.ALWAYS);
        

        this.core.getChildren().addAll(this.chatNav, this.scrollChat);
        this.getChildren().add(this.core);
        //this.setOnMouseClicked((e) -> this.setStyle("-fx-border-color: #00ff00; -fx-border-width: 3;"));

        HBox.setHgrow(this, javafx.scene.layout.Priority.ALWAYS);
    }
    
    public void handleMessage(Message message) {
            if ((message.type & (MessageType.TYPING.getValue() | MessageType.MESSAGE.getValue())) > 0) {
                addMessage(message);
                return;
            }

            if ((message.type & MessageType.AUDIO.getValue()) > 0) {
                CallConfig config = CallConfig.fromBytes(message.messageData);
                if (message.sender.equals(user.getName()) || config.HOSTNAME.equals(user.getName())) { return; }
                chatNav.addAvailableCall(config);
                return;
            }
    }
    public void addMessage(Message message) {
        if ((message.type & MessageType.TYPING.getValue()) > 0) {
            //handle typing
            ChatBox box = (ChatBox) this.scrollChat.getContent();
            box.addTypingToDisplay(message);
        }
        else if ((message.type & MessageType.MESSAGE.getValue()) > 0) {
            ChatBox box = (ChatBox) this.scrollChat.getContent();
            box.addToDisplay(message);
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
    //handling incoming typing later
    private void outtyping(KeyEvent e) {
        if (user == null) return;
        //on outtyping, the chatBox doesn't need to change but a typing signal must still be broadcast to all users
        Message msg = new Message(user.getName(), "", MessageType.TYPING.getValue());
        sendMessage(msg);
    }

    private String ceasarCipher(String input, int shift) {
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isUpperCase(c)) {
                char shifted = (char) (((c - 'A' + shift) % 26) + 'A');
                result.append(shifted);
            } else if (Character.isLowerCase(c)) {
                char shifted = (char) (((c - 'a' + shift) % 26) + 'a');
                result.append(shifted);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String multiplicativeCipher(String input, int key) {
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isUpperCase(c)) {
                char shifted = (char) (((c - 'A') * key) % 26 + 'A');
                result.append(shifted);
            } else if (Character.isLowerCase(c)) {
                char shifted = (char) (((c - 'a') * key) % 26 + 'a');
                result.append(shifted);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    //as bytes or as Message object?
    private void sendMessage(Message message) {
        if (user != null) {
            System.out.println("Sending message");
            user.send(message);
            //Just a test
            // message.messageData = ceasarCipher(new String(message.messageData), 3).getBytes();
            // user.send(message);
            // message.messageData = multiplicativeCipher(new String(message.messageData), 5).getBytes();
            // user.send(message);
        }
    }


    //start with an audio call, video is optional
    private void toggleAudioCall(CallConfig config) {
        if (this.callComp == null) {
            //Came from own machine
            if (config == null) {
                AudioCallServer server = new AudioCallServer();
                this.callComp = new CallComp(server);
                byte[] serverConfig = CallConfig.toBytes(server.getAddress(), server.getPort(), user.getName());
                if (serverConfig.length == 0) {
                    System.out.println("Failed to get server config, cannot start audio call");
                }
                else {
                    sendMessage(new Message(user.getName(), serverConfig, MessageType.AUDIO.getValue()));
                }
            }
            else {
                AudioCallClient client = new AudioCallClient(config.HOST, config.PORT);
                this.callComp = new CallComp(client);
            }

            this.callComp.setOnEnd(() -> {
                this.getChildren().remove(this.callComp);
                this.callComp.endCall();
                this.callComp = null;
            });
            this.callComp.setOnExit(() -> {
                this.getChildren().remove(this.callComp);
            });
            this.getChildren().add(this.callComp);
        }
        else {
            if (this.getChildren().contains(this.callComp)) {
                this.getChildren().remove(this.callComp);
            }
            else {
                this.getChildren().add(this.callComp);
            }
        }
    }

    public boolean isInCall() {
        return this.callComp != null;
    }
}



class ChatBox extends VBox {
    public VBox chatDisplay;
    public TextField msgBox;
    public HBox sendBox;
    private Consumer<KeyEvent> keyConsume;
    private Consumer<String> onSend;
    private String typingString = "";
    private int typingIdx = -1;
    private boolean isTyping = false;
    private HashSet<String> currentlyTyping = new HashSet<>();
    public ChatBox() {
        this.getStyleClass().add("chat-box");
        this.msgBox = new TextField();
        this.sendBox = new HBox();
        this.setMaxHeight(Double.MAX_VALUE);
        this.setMaxWidth(Double.MAX_VALUE);


        msgBox.setPromptText("Type a message");
        msgBox.getStyleClass().add("msg-input");
        msgBox.setOnKeyPressed(e -> {
            if (e.getCode().toString().equals("ENTER")) {
                onSend.accept(getMessage());
                addToDisplayAndClear();
                return;
            }

            keyConsume.accept(e);
        });
        HBox.setHgrow(msgBox, Priority.ALWAYS);
        
        Button sendBtn = new Button("Send");
        sendBtn.getStyleClass().add("send-btn");
        sendBtn.setOnAction(e -> {
            onSend.accept(getMessage());
            addToDisplayAndClear();
            
        });
        this.sendBox.getChildren().addAll(msgBox, sendBtn);
        this.sendBox.setAlignment(Pos.BOTTOM_CENTER);
        this.sendBox.setSpacing(10);
        this.sendBox.getStyleClass().add("send-box");
        this.chatDisplay = new VBox();
        this.chatDisplay.setSpacing(4); // slight gap between message bubbles for readability
        this.getChildren().add(this.chatDisplay);
        this.getChildren().addAll(this.sendBox);
        
        // Set 8:2 ratio: chatDisplay takes 80%, sendBox takes 20%
        VBox.setVgrow(this.chatDisplay, Priority.ALWAYS);
        VBox.setVgrow(this.sendBox, Priority.ALWAYS);
        HBox.setHgrow(this.chatDisplay, Priority.ALWAYS);
        HBox.setHgrow(this.sendBox, Priority.ALWAYS);

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

    public void addToDisplayAndClear() {
        Label label = new Label(getMessage());
        label.getStyleClass().add("message-label");
        chatDisplay.getChildren().add(label);
        msgBox.clear();
    }

    public void addToDisplay(Message message) {
        if (isTyping) {
            isTyping = false;
            chatDisplay.getChildren().remove(typingIdx);
            typingString = "";
            currentlyTyping.clear();
        }
        
        Label label = new Label(message.sender + ": " + new String(message.messageData));
        label.getStyleClass().add("message-label");
        chatDisplay.getChildren().add(label);
    }

    public void addTypingToDisplay(Message message) {
        //add "sender is typing..." to display
        if (currentlyTyping.contains(message.sender)) {
                return;
        }
        if (!isTyping) {
            typingString = message.sender + " is typing...";
            Label label = new Label(typingString);
            label.getStyleClass().add("message-label");
            chatDisplay.getChildren().add(label);
            typingIdx = chatDisplay.getChildren().size() - 1;
            isTyping = true;
            currentlyTyping.add(message.sender);
        }
        else if (currentlyTyping.size() > 3) {
            
            typingString = currentlyTyping.size() + " people are typing...";
            chatDisplay.getChildren().remove(typingIdx);
            Label label = new Label(typingString);
            label.getStyleClass().add("message-label");
            chatDisplay.getChildren().add(label);
            typingIdx = chatDisplay.getChildren().size() - 1;
            currentlyTyping.add(message.sender);
        }
        else {
            typingString = message.sender + " and  " + typingString;
            chatDisplay.getChildren().remove(typingIdx);
            Label label = new Label(typingString);
            label.getStyleClass().add("message-label");
            chatDisplay.getChildren().add(label);
            typingIdx = chatDisplay.getChildren().size() - 1;
        }
        
    }
}
