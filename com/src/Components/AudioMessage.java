package Components;

public class AudioMessage {
    String sender;
    byte[] audioData;
    AudioMessage(String sender, byte[] audioData) {
        this.sender = sender;
        this.audioData = audioData;
    }
    
}
