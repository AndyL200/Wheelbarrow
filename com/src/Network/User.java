package Network;
import Components.Message;

public interface User {
    public void send(byte[] message);
    public void send(Message message);
    public byte[] receive();
    public String getName();
}
