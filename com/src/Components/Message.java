package Components;

public class Message {
    
    public byte[] messageData;
    public String sender;

    public Message(String sender, byte[] messageData) {
        this.sender = sender;
        this.messageData = messageData;
    }

    public Message(String sender, String message) {
        this.sender = sender;
        this.messageData = message.getBytes();
    }
}
