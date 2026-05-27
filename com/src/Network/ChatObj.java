package Network;

import java.net.InetAddress;
import java.util.function.Consumer;

import Components.Message;

public abstract class ChatObj implements Chat {

    public void send(byte[] message) {};
    public void send(Message message) {};
    public byte[] receive() { return new byte[0]; };
    public String getName() { return "Unspecified"; };
    public InetAddress getAddress() { return null; };
    public void setOnMessageReceived(Consumer<Message> onMessageReceived) {};
    public ServerInfo getInfo() { return null; };
    public void stop() {};
    public void start() {};
}
