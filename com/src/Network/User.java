package Network;
import java.net.InetAddress;
import java.util.function.Consumer;

import Components.Message;

public interface User {
    public void send(byte[] message);
    public void send(Message message);
    public byte[] receive();
    public String getName();
    public InetAddress getAddress();
    public void setOnMessageReceived(Consumer<Message> onMessageReceived);
    public ServerInfo getInfo();
}
